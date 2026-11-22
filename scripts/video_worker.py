"""Pull video jobs from task-service and transcribe them on this computer."""
from pathlib import Path
from importlib.util import find_spec
from bilibili_media import LinkVideoError, download_video
import json
import math
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import wave

ROOT = Path(__file__).resolve().parent.parent
API = 'http://127.0.0.1:18083/api/tasks/video'
HTTP = urllib.request.build_opener(urllib.request.ProxyHandler({}))
MAX_BYTES = 1024 * 1024 * 1024
MAX_SECONDS = 60 * 60
MAX_TRANSCRIPT_BYTES = 512 * 1024
AUDIO_PART_SECONDS = 5 * 60
TRANSCRIBE_TIMEOUT = 90 * 60
DOWNLOAD_TIMEOUT = 10 * 60


class VideoError(Exception):
    pass


def settings():
    values = {}
    for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
        name, separator, value = line.partition('=')
        if separator and name.strip().startswith('VIDEO_'):
            values[name.strip()] = value.strip().strip("'").strip('"')
    return values


def media_tools(config):
    ffmpeg = Path(config['VIDEO_FFMPEG'])
    ffprobe = ffmpeg.with_name('ffprobe' + ffmpeg.suffix)
    model = Path(config['VIDEO_WHISPER_MODEL'])
    if not ffmpeg.is_file() or not ffprobe.is_file():
        raise VideoError('本机缺少 FFmpeg 或 FFprobe')
    if not (model / 'model.bin').is_file() or not (model / 'tokenizer.json').is_file():
        raise VideoError('本机 Whisper 模型文件不完整')
    return str(ffmpeg), str(ffprobe), model


def run_media(args, timeout, error):
    try:
        return subprocess.run(args, capture_output=True, check=True, timeout=timeout).stdout
    except (subprocess.SubprocessError, OSError):
        raise VideoError(error) from None


def transcribe(video, config):
    ffmpeg, ffprobe, model_path = media_tools(config)
    allowed = ['-protocol_whitelist', 'file,pipe', '-format_whitelist', 'mov,matroska,webm']
    raw = run_media([
        ffprobe, '-v', 'error', *allowed, '-show_entries', 'format=duration:stream=codec_type',
        '-of', 'json', str(video)
    ], 20, '无法读取视频，请检查文件格式')
    try:
        info = json.loads(raw)
        duration = float(info['format']['duration'])
        streams = {stream.get('codec_type') for stream in info.get('streams', [])}
    except (KeyError, ValueError, TypeError):
        raise VideoError('无法读取视频时长') from None
    if not math.isfinite(duration) or not 0 < duration <= MAX_SECONDS:
        raise VideoError('请选择不超过 60 分钟的视频')
    if not {'audio', 'video'} <= streams:
        raise VideoError('视频需要包含画面和语音音轨')
    run_media([
        ffmpeg, '-hide_banner', '-loglevel', 'error', '-nostdin', *allowed, '-i', str(video),
        '-map', '0:a:0', '-vn', '-t', str(MAX_SECONDS + 1), '-ac', '1', '-ar', '16000',
        '-c:a', 'pcm_s16le', '-f', 'segment', '-segment_time', str(AUDIO_PART_SECONDS),
        '-reset_timestamps', '1', str(video.parent / 'audio-%03d.wav')
    ], 300, '提取音频失败或超时，请检查视频音轨')

    os.environ['HF_HUB_OFFLINE'] = '1'
    from faster_whisper import WhisperModel
    model = WhisperModel(str(model_path), device='cpu', compute_type='int8',
                         cpu_threads=4, local_files_only=True)
    lines = []
    elapsed = 0.0
    for audio in sorted(video.parent.glob('audio-*.wav')):
        segments, _ = model.transcribe(str(audio), beam_size=3, vad_filter=True)
        for segment in segments:
            text = segment.text.strip()
            if text:
                start = int(elapsed + segment.start)
                stamp = f'{start // 3600:02d}:{start // 60 % 60:02d}:{start % 60:02d}'
                lines.append(f'[{stamp}] {text}')
        with wave.open(str(audio), 'rb') as wav:
            elapsed += wav.getnframes() / wav.getframerate()
    transcript = '\n'.join(lines)
    if not transcript:
        raise VideoError('未识别到语音，请检查视频音轨')
    if len(transcript.encode('utf-8')) > MAX_TRANSCRIPT_BYTES:
        raise VideoError('转写正文超过 512 KB，请分成两个视频上传')
    return transcript


def request(path, token, *, method='GET', payload=None, lease=None, links=False):
    headers = {'X-Video-Worker-Token': token}
    if lease:
        headers['X-Video-Lease'] = lease
    if links:
        headers['X-Video-Link-Support'] = 'true'
    data = None
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        headers['Content-Type'] = 'application/json'
    return HTTP.open(urllib.request.Request(API + path, data=data, headers=headers, method=method), timeout=12)


def run_job_child(args, job_id, token, lease, timeout, action):
    deadline = time.monotonic() + timeout
    with subprocess.Popen([sys.executable, str(Path(__file__)), *args],
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE) as child:
        try:
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise VideoError(f'{action}超时，请尝试较短的视频')
                try:
                    stdout, _ = child.communicate(timeout=min(30, remaining))
                    if child.returncode:
                        raise VideoError(f'{action}失败，请检查视频后重试')
                    result = json.loads(stdout)
                    if 'error' in result:
                        raise VideoError(result['error'])
                    return result
                except subprocess.TimeoutExpired:
                    try:
                        with request(f'/{job_id}/heartbeat', token, method='POST', lease=lease):
                            pass
                    except urllib.error.HTTPError as error:
                        if 400 <= error.code < 500:
                            raise VideoError('视频任务已失效，请重新提交') from None
                    except OSError:
                        pass
        finally:
            if child.poll() is None:
                child.kill()
                child.communicate()


def store_link_video(video, job_id, token, lease):
    headers = {
        'X-Video-Worker-Token': token,
        'X-Video-Lease': lease,
        'Content-Type': 'application/octet-stream',
        'Content-Length': str(video.stat().st_size),
    }
    with video.open('rb') as media:
        upload = urllib.request.Request(API + f'/{job_id}/source-file', data=media, headers=headers, method='POST')
        try:
            with HTTP.open(upload, timeout=120):
                pass
        except OSError:
            raise VideoError('链接视频保存到工作台失败，请重试') from None


def process(claim, token):
    job_id, lease = claim['taskId'], claim['lease']
    outcome = '/transcript'
    try:
        suffix = Path(claim['fileName']).suffix.lower()
        if suffix not in {'.mp4', '.mov', '.webm', '.mkv'}:
            raise VideoError('不支持的视频格式')
        with tempfile.TemporaryDirectory(prefix='courselens-video-') as directory:
            video = Path(directory) / ('source' + suffix)
            url = claim.get('sourceUrl')
            if url:
                run_job_child(['--download', url, str(video)], job_id, token, lease,
                              DOWNLOAD_TIMEOUT, '读取视频链接')
            else:
                with request(f'/{job_id}/file', token, lease=lease) as response, video.open('wb') as output:
                    size = int(response.headers.get('Content-Length', 0))
                    if not 0 < size <= MAX_BYTES:
                        raise VideoError('视频大小超出限制')
                    shutil.copyfileobj(response, output)
            result = run_job_child(['--transcribe', str(video)], job_id, token, lease,
                                   TRANSCRIBE_TIMEOUT, '本机转写')
            if url:
                store_link_video(video, job_id, token, lease)
            payload = {'text': result['text']}
    except VideoError as error:
        outcome, payload = '/failure', {'reason': str(error)}
    except (subprocess.SubprocessError, OSError, ValueError, KeyError):
        outcome, payload = '/failure', {'reason': '本机视频转写失败或超时，请检查文件后重试'}

    for attempt in range(3):
        try:
            with request('/' + job_id + outcome, token, method='POST', payload=payload, lease=lease):
                print(job_id, 'transcript saved' if outcome == '/transcript' else payload['reason'], flush=True)
            return
        except urllib.error.HTTPError as error:
            if 400 <= error.code < 500:
                print(job_id, f'callback rejected ({error.code})', flush=True)
                return
        except (OSError, TimeoutError):
            pass
        time.sleep(2)
    print(job_id, 'callback unavailable; lease will expire', flush=True)


def main():
    config = settings()
    if len(sys.argv) == 3 and sys.argv[1] == '--transcribe':
        try:
            result = {'text': transcribe(Path(sys.argv[2]), config)}
        except VideoError as error:
            result = {'error': str(error)}
        except Exception:
            result = {'error': '本机 Whisper 转写失败，请检查模型和视频'}
        sys.stdout.buffer.write(json.dumps(result, ensure_ascii=False).encode('utf-8'))
        return
    if len(sys.argv) == 4 and sys.argv[1] == '--download':
        try:
            download_video(sys.argv[2], Path(sys.argv[3]), config['VIDEO_FFMPEG'], MAX_BYTES, MAX_SECONDS)
            result = {'ok': True}
        except LinkVideoError as error:
            result = {'error': str(error)}
        except Exception:
            result = {'error': '本机读取视频链接失败，请检查网络后重试'}
        sys.stdout.buffer.write(json.dumps(result, ensure_ascii=False).encode('utf-8'))
        return
    media_tools(config)
    supports_links = find_spec('yt_dlp') is not None
    token = config['VIDEO_WORKER_TOKEN']
    print('CourseLens local video worker ready', flush=True)
    while True:
        try:
            with request('/claim', token, method='POST', links=supports_links) as response:
                claim = json.load(response) if response.status == 200 else None
            if claim:
                print(claim['taskId'], 'transcribing', flush=True)
                process(claim, token)
                continue
        except urllib.error.HTTPError as error:
            if error.code == 403:
                print('Worker token rejected. Check local configuration.', flush=True)
                return
        except (OSError, TimeoutError, ValueError):
            pass
        time.sleep(2)


if __name__ == '__main__':
    main()
