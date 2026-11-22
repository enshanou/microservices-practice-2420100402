package com.zjgsu.oes;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class BilibiliLink {
    private static final Set<String> VIDEO_HOSTS = Set.of("www.bilibili.com", "bilibili.com", "m.bilibili.com");

    private static final Pattern SHARED_URL = Pattern.compile("https://[^\\s<>\\\"【】）)。，；！？、]+");

    record Shared(String url, String title) {}

    static Shared parse(String input) {
        var match = SHARED_URL.matcher(input.strip());
        if (!match.find()) {
            throw invalid();
        }
        String title = input.strip().substring(0, match.start()).strip();
        if (title.startsWith("【") && title.endsWith("】")) {
            title = title.substring(1, title.length() - 1);
        }
        return new Shared(normalizeUrl(match.group()), title(title, 200));
    }

    static String normalize(String input) {
        return parse(input).url();
    }

    static String title(String input, int limit) {
        String value = input.replaceAll("[\\p{Cntrl}]+", " ").strip();
        if (value.length() <= limit) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(limit - 1)) ? limit - 1 : limit;
        return value.substring(0, end).stripTrailing();
    }

    private static String normalizeUrl(String input) {
        URI url;
        try {
            url = URI.create(input.strip());
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        String host = url.getHost();
        if (host == null || url.getRawUserInfo() != null || url.getPort() != -1
                || !"https".equalsIgnoreCase(url.getScheme())) {
            throw invalid();
        }
        host = host.toLowerCase(Locale.ROOT);
        String path = url.getRawPath();
        if ("b23.tv".equals(host) && path.matches("/[A-Za-z0-9]{1,32}/?")) {
            return "https://b23.tv" + path;
        }
        if (!VIDEO_HOSTS.contains(host) || !path.matches("/video/(?:BV[A-Za-z0-9]{10}|av[0-9]{1,16})/?")) {
            throw invalid();
        }
        String canonical = "https://www.bilibili.com" + path;
        if (url.getRawQuery() != null) {
            for (String field : url.getRawQuery().split("&")) {
                if (field.startsWith("p=") && field.substring(2).matches("[1-9][0-9]{0,4}")) {
                    return canonical + "?" + field;
                }
            }
        }
        return canonical;
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入 B 站公开单视频链接或 b23.tv 分享链接");
    }
}
