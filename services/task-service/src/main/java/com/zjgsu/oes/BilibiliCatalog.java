package com.zjgsu.oes;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class BilibiliCatalog {
    private static final int MAX_EPISODES = 500;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final String api;
    private final ObjectMapper json;

    BilibiliCatalog(@Value("${BILIBILI_CATALOG_API:https://api.bilibili.com}") String api, ObjectMapper json) {
        this.api = api;
        this.json = json;
    }

    record Episode(int index, String title, int seconds, String url) {}
    record Preview(String title, String url, String collectionTitle, int selectedIndex, List<Episode> episodes) {}

    Preview preview(String text) {
        var shared = BilibiliLink.parse(text);
        URI source = resolve(URI.create(shared.url()));
        String id = source.getPath().split("/")[2];
        int page = source.getQuery() == null ? 1 : Integer.parseInt(source.getQuery().substring(2));
        var query = id.startsWith("BV") ? "bvid=" + id : "aid=" + id.substring(2);
        var response = read(URI.create(api + "/x/web-interface/view?" + query), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw unavailable();
        }
        JsonNode body;
        try {
            body = json.readTree(response.body());
        } catch (RuntimeException e) {
            throw unavailable();
        }
        var data = body.path("data");
        String bvid = data.path("bvid").asText();
        if (body.path("code").asInt(-1) != 0 || !bvid.matches("BV[A-Za-z0-9]{10}")) {
            throw unavailable();
        }
        String title = BilibiliLink.title(data.path("title").asText("B 站视频"), 200);
        String selected = episodeUrl(bvid, page);
        var episodes = new ArrayList<Episode>();
        for (var part : data.path("pages")) {
            int p = part.path("page").asInt(0);
            if (p > 0) {
                add(episodes, part.path("part").asText(title), part.path("duration").asInt(0), episodeUrl(bvid, p));
            }
        }
        if (episodes.isEmpty() && page == 1) {
            add(episodes, title, data.path("duration").asInt(0), selected);
        }
        String collectionTitle = title;
        var season = data.path("ugc_season");
        var seasonParts = seasonEpisodes(season);
        if (seasonParts.size() > 1 && seasonParts.stream().anyMatch(part -> selected.equals(part.url()))) {
            episodes = seasonParts;
            collectionTitle = BilibiliLink.title(season.path("title").asText(title), 200);
        }
        int selectedIndex = episodes.stream().filter(part -> selected.equals(part.url())).mapToInt(Episode::index)
            .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "这个分集在 B 站目录中不存在"));
        if (episodes.size() > MAX_EPISODES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "合集超过 500 集，请选择较小的合集");
        }
        return new Preview(shared.title().isBlank() ? title : shared.title(), selected, collectionTitle, selectedIndex, episodes);
    }

    private ArrayList<Episode> seasonEpisodes(JsonNode season) {
        var unique = new LinkedHashMap<String, JsonNode>();
        for (var group : season.path("sections")) {
            for (var part : group.path("episodes")) {
                String bvid = part.path("bvid").asText();
                int page = part.path("page").path("page").asInt(1);
                if (page > 0 && bvid.matches("BV[A-Za-z0-9]{10}")) {
                    unique.putIfAbsent(episodeUrl(bvid, page), part);
                }
            }
        }
        var episodes = new ArrayList<Episode>();
        if (season.path("ep_count").asInt(0) <= unique.size()) {
            unique.forEach((url, part) -> add(episodes, part.path("title").asText("分集"),
                part.path("page").path("duration").asInt(part.path("arc").path("duration").asInt(0)), url));
        }
        return episodes;
    }

    private void add(List<Episode> episodes, String title, int seconds, String url) {
        episodes.add(new Episode(episodes.size() + 1, BilibiliLink.title(title, 200), Math.max(0, seconds), url));
    }

    private String episodeUrl(String bvid, int page) {
        return "https://www.bilibili.com/video/" + bvid + "/?p=" + page;
    }

    private URI resolve(URI url) {
        for (int hops = 0; hops < 3 && "b23.tv".equals(url.getHost()); hops++) {
            var response = read(url, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 3) {
                throw unavailable();
            }
            String destination = response.headers().firstValue("location").orElseThrow(BilibiliCatalog::unavailable);
            url = URI.create(BilibiliLink.normalize(url.resolve(destination).toString()));
        }
        if ("b23.tv".equals(url.getHost())) {
            throw unavailable();
        }
        return url;
    }

    private <T> HttpResponse<T> read(URI url, HttpResponse.BodyHandler<T> handler) {
        var request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(6))
            .header("User-Agent", "Mozilla/5.0")
            .header("Referer", "https://www.bilibili.com/").GET().build();
        try {
            return client.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException e) {
            throw unavailable();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "暂时无法读取 B 站视频目录，请确认链接可公开访问后重试");
    }
}
