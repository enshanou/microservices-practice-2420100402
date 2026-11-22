"""Download a supported public video into the worker temporary directory."""
import math
import re
import sys
from pathlib import Path
import urllib.error
import urllib.request
from urllib.parse import parse_qs, urljoin, urlsplit


local_dependencies = Path(__file__).resolve().parent / ".video-python"
if local_dependencies.is_dir():
    sys.path.insert(0, str(local_dependencies))


class LinkVideoError(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def video_url(url):
    try:
        parts = urlsplit(url)
        if parts.scheme != 'https' or parts.username is not None or parts.port is not None:
            raise ValueError
    except ValueError:
        raise LinkVideoError('请输入有效的 B 站视频链接') from None
    if parts.hostname == 'b23.tv' and re.fullmatch(r'/[a-zA-Z0-9]{1,32}/?', parts.path):
        return 'https://b23.tv' + parts.path
    if parts.hostname not in {'www.bilibili.com', 'bilibili.com', 'm.bilibili.com'} or not re.fullmatch(
            r'/video/(?:BV[a-zA-Z0-9]{10}|av[0-9]{1,16})/?', parts.path):
        raise LinkVideoError('分享链接没有指向 B 站公开单视频')
    page = parse_qs(parts.query).get('p', ['1'])[0]
    if not re.fullmatch(r'[1-9][0-9]{0,4}', page):
        page = '1'
    return f'https://www.bilibili.com{parts.path}?p={page}'


def resolve_short_link(url):
    url = video_url(url)
    if not url.startswith('https://b23.tv/'):
        return url
    opener = urllib.request.build_opener(NoRedirect())
    for _ in range(4):
        request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        try:
            with opener.open(request, timeout=10):
                break
        except urllib.error.HTTPError as error:
            with error:
                target = error.headers.get('Location')
                if error.code not in {301, 302, 303, 307, 308} or not target:
                    break
            url = video_url(urljoin(url, target))
            if not url.startswith('https://b23.tv/'):
                return url
        except OSError:
            break
    raise LinkVideoError('无法打开 B 站短链，请复制浏览器地址栏里的视频链接')


def source_for_download(url):
    if re.fullmatch(r'https://www\.youtube\.com/watch\?v=[a-zA-Z0-9_-]{11}', url):
        return url, 'Youtube'
    vimeo = re.fullmatch(r'https://(?:vimeo\.com/|player\.vimeo\.com/video/)([0-9]{1,14})', url)
    if vimeo:
        return 'https://player.vimeo.com/video/' + vimeo.group(1), 'Vimeo'
    return resolve_short_link(url), 'BiliBili'


def download_video(url, output, ffmpeg, max_bytes, max_seconds):
    try:
        from yt_dlp import YoutubeDL
        from yt_dlp.utils import DownloadError
    except ImportError:
        raise LinkVideoError('本机转写环境缺少 yt-dlp') from None
    url, extractor = source_for_download(url)
    amounts = {}

    def check_duration(info, *, incomplete):
        duration = info.get('duration')
        if duration is not None and (not math.isfinite(duration) or duration > max_seconds):
            raise LinkVideoError('请选择不超过 60 分钟的公开视频')

    def check_size(status):
        if status.get('filename') and status.get('downloaded_bytes'):
            amounts[status['filename']] = status['downloaded_bytes']
            if sum(amounts.values()) > max_bytes:
                raise LinkVideoError('链接视频超过 1 GB，请换一条视频')

    options = {
        'allowed_extractors': [extractor],
        'noplaylist': True,
        'playlist_items': '1',
        'format': 'bv[height<=720][ext=mp4][vcodec^=avc]+ba[ext=m4a]/b[height<=720][ext=mp4][vcodec^=avc]',
        'merge_output_format': 'mp4',
        'outtmpl': str(output.with_suffix('.%(ext)s')),
        'ffmpeg_location': ffmpeg,
        'match_filter': check_duration,
        'progress_hooks': [check_size],
        'max_filesize': max_bytes,
        'socket_timeout': 15,
        'retries': 2,
        'extractor_retries': 1,
        'quiet': True,
        'no_warnings': True,
        'noprogress': True,
    }
    if extractor == 'Youtube':
        options['js_runtimes'] = {'deno': {}, 'node': {}}
    elif extractor == 'Vimeo':
        options['format'] += ('/bv[height<=720][ext=mp4][vcodec^=avc]+ba[ext=mp4]'
                              '/b[height<=?720][ext=mp4][vcodec^=?avc]')
    try:
        with YoutubeDL(options) as client:
            client.extract_info(url, download=True)
    except DownloadError as error:
        text = str(error).lower()
        if 'drm' in text:
            raise LinkVideoError('该视频受 DRM 保护，请选择可公开下载的视频') from None
        if extractor == 'Vimeo' and any(code in text for code in ('http error 401', 'http error 403', 'logged-in')):
            raise LinkVideoError('Vimeo 限制了此视频的访问，请选择允许公开嵌入的视频') from None
        if extractor == 'BiliBili' and any(flag in text for flag in ('412', '-352', 'captcha')):
            raise LinkVideoError('B 站暂时限制了链接读取，请稍后重试或上传本地文件') from None
        raise LinkVideoError('无法获取该链接的视频，请确认视频公开可播放，或上传本地文件') from None
    if not output.is_file() or not 0 < output.stat().st_size <= max_bytes:
        raise LinkVideoError('未获得可转写的 MP4，或视频超过 1 GB')
