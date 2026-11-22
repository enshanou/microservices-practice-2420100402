package com.zjgsu.oes;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class PublicVideoLink {
    private static final Pattern URL = Pattern.compile("https://[^\\s<>\\\"【】）)。，；！？、]+");

    static BilibiliLink.Shared parse(String text) {
        var external = external(text);
        return external == null ? BilibiliLink.parse(text) : external;
    }

    static BilibiliLink.Shared external(String text) {
        var match = URL.matcher(text.strip());
        if (!match.find()) return null;
        URI uri;
        try {
            uri = URI.create(match.group());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        String path = uri.getRawPath();
        String source;
        String platform;
        if (Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be").contains(host)) {
            String video = "";
            if ("youtu.be".equals(host) && path.matches("/[A-Za-z0-9_-]{11}/?")) {
                video = path.substring(1, 12);
            } else if (!"youtu.be".equals(host) && path.matches("/shorts/[A-Za-z0-9_-]{11}/?")) {
                video = path.substring(8, 19);
            } else if (!"youtu.be".equals(host) && "/watch".equals(path) && uri.getRawQuery() != null) {
                for (String field : uri.getRawQuery().split("&")) {
                    if (field.matches("v=[A-Za-z0-9_-]{11}")) { video = field.substring(2); break; }
                }
            }
            if (video.isEmpty()) throw invalid();
            source = "https://www.youtube.com/watch?v=" + video;
            platform = "YouTube";
        } else if (Set.of("vimeo.com", "www.vimeo.com", "player.vimeo.com").contains(host)) {
            if (!path.matches("/(?:video/)?[0-9]{1,14}/?")) throw invalid();
            source = "https://player.vimeo.com/video/" + path.replaceAll("[^0-9]", "");
            platform = "Vimeo";
        } else {
            return null;
        }
        if (uri.getRawUserInfo() != null || uri.getPort() != -1) throw invalid();
        String title = text.strip().substring(0, match.start()).strip();
        if (title.startsWith("【") && title.endsWith("】")) title = title.substring(1, title.length() - 1).strip();
        return new BilibiliLink.Shared(source, title.isBlank() ? platform + " 视频" : BilibiliLink.title(title, 200));
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入 YouTube 或 Vimeo 的公开视频单集链接");
    }
}
