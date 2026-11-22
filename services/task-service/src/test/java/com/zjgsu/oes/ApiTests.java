package com.zjgsu.oes;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import java.net.*;
import java.net.http.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(ApiTests.DiscoveryTestConfiguration.class)
class ApiTests {
    static volatile boolean noInstances=false;
    @LocalServerPort int port;
    HttpResponse<String> request(String method,String path,String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
        return HttpClient.newHttpClient().send(b.method(method,HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }

    static volatile int upstreamStatus=200;
    static volatile boolean slow=false;
    static final com.sun.net.httpserver.HttpServer upstream;
    static final java.util.List<String> modelRequests = new java.util.concurrent.CopyOnWriteArrayList<>();
    static {
        try {
            upstream=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            upstream.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
            upstream.createContext("/api/", ex -> {
                try {
                    if(slow) Thread.sleep(600);
                    var bytes="{\"id\":\"11111111-1111-1111-1111-111111111111\",\"ownerId\":\"22222222-2222-2222-2222-222222222222\",\"title\":\"微服务\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    ex.getResponseHeaders().set("Content-Type", "application/json");
                    ex.getResponseHeaders().set("X-Course-Instance", "local-test");
                    ex.sendResponseHeaders(upstreamStatus,bytes.length); ex.getResponseBody().write(bytes);
                } catch(Exception ignored) { } finally { ex.close(); }
            });
            upstream.createContext("/chat/completions", ex -> {
                try (ex) {
                    var json = new tools.jackson.databind.ObjectMapper();
                    String prompt = json.readTree(ex.getRequestBody()).path("messages").path(1).path("content").asText();
                    modelRequests.add(prompt);
                    if (prompt.contains("\n选段：")) {
                        byte[] response = json.writeValueAsBytes(java.util.Map.of("choices", java.util.List.of(
                            java.util.Map.of("finish_reason", "stop", "message", java.util.Map.of("content", "例如：查询注册中心。")))));
                        ex.sendResponseHeaders(200, response.length);
                        ex.getResponseBody().write(response);
                        return;
                    }
                    var first = json.readTree(prompt.substring(prompt.indexOf("字幕段落：") + "字幕段落：".length()).strip()).get(0);
                    var correction = new ModelNotes.Correction(0, first.path("text").asText().replace('。', '！'), false);
                    String note = json.writeValueAsString(new ModelNotes.Generated("摘要",
                        java.util.List.of(new ModelNotes.Section("要点", java.util.List.of("示例"))), java.util.List.of(correction)));
                    byte[] response = json.writeValueAsBytes(java.util.Map.of("choices", java.util.List.of(
                        java.util.Map.of("finish_reason", "stop", "message", java.util.Map.of("content", note)))));
                    ex.sendResponseHeaders(200, response.length);
                    ex.getResponseBody().write(response);
                }
            });
            upstream.createContext("/x/web-interface/view", ex -> {
                try (ex) {
                    byte[] response = """
                        {"code":0,"data":{"bvid":"BV1iE411x7gS","title":"目录标题","pages":[
                          {"page":1,"part":"第一集","duration":120},{"page":2,"part":"超时长分集","duration":3700},
                          {"page":3,"part":"第三集","duration":90}]}}
                        """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    if (ex.getRequestURI().getQuery().contains("BV1vL411G7N7")) {
                        response = new String(response, java.nio.charset.StandardCharsets.UTF_8)
                            .replace("BV1iE411x7gS", "BV1vL411G7N7").replace("3700", "60")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }
                    ex.sendResponseHeaders(200, response.length);
                    ex.getResponseBody().write(response);
                }
            });
            upstream.start();
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class DiscoveryTestConfiguration {
        @org.springframework.context.annotation.Bean
        org.springframework.cloud.client.discovery.DiscoveryClient discoveryClientForTest() {
            return new org.springframework.cloud.client.discovery.DiscoveryClient() {
                public String description() { return "Local fake discovery"; }
                public java.util.List<String> getServices() {
                    return java.util.List.of("user-service", "course-service");
                }
                public java.util.List<org.springframework.cloud.client.ServiceInstance>
                        getInstances(String serviceId) {
                    if (noInstances || !"course-service".equals(serviceId)) return java.util.List.of();
                    return java.util.List.of(
                        new org.springframework.cloud.client.DefaultServiceInstance(
                            "local-test", serviceId, "127.0.0.1",
                            upstream.getAddress().getPort(), false));
                }
            };
        }
    }
    @AfterAll static void stop() { upstream.stop(0); }

    @org.springframework.test.context.DynamicPropertySource
    static void configureModel(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("NOTES_API_BASE_URL", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        registry.add("NOTES_API_KEY", () -> "test-key");
        registry.add("NOTES_MODEL", () -> "test-model");
        registry.add("VIDEO_WORKER_TOKEN", () -> "test-worker");
        registry.add("BILIBILI_CATALOG_API", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        registry.add("VIDEO_STORAGE_DIR", () -> "./target/video-test-storage");
    }

    @Autowired TaskRepository repository;
    @Autowired NoteRepository notes;
    @Autowired VideoRepository videos;
    @Autowired VideoCollectionRepository collections;
    @Autowired VideoCollectionService collectionService;
    @Autowired NoteService noteService;
    @Autowired VideoService videoService;
    String body="{\"courseId\":\"11111111-1111-1111-1111-111111111111\"}";
    @BeforeEach void clean() { collections.deleteAll(); notes.deleteAll(); videos.deleteAll(); repository.deleteAll(); upstreamStatus=200; slow=false; noInstances=false; }
    @Test void successUsesHttpAndPersists() throws Exception {
        assertEquals(201,request("POST","/api/tasks",body).statusCode());
        var item=repository.findAll().getFirst();
        assertEquals(200,request("GET","/api/tasks/"+item.id,"").statusCode());
        assertEquals(1,repository.count());
    }
    @Test void missingDependencyDoesNotWrite() throws Exception {
        upstreamStatus=404;
        assertEquals(404,request("POST","/api/tasks",body).statusCode());
        assertEquals(0,repository.count());
    }
    @Test void unavailableDependencyDoesNotWrite() throws Exception {
        upstreamStatus=500;
        var response = request("POST", "/api/tasks", body);
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("course-service unavailable or timed out"), response.body());
        assertEquals(0,repository.count());
    }
    @Test void invalidCourseResponseDoesNotWrite() throws Exception {
        var response = request("POST", "/api/tasks",
                body.replace("11111111-1111-1111-1111-111111111111", "33333333-3333-3333-3333-333333333333"));
        assertEquals(502, response.statusCode());
        assertTrue(response.body().contains("Invalid course-service response"), response.body());
        assertEquals(0, repository.count());
    }
    @Test void timeoutDoesNotWrite() throws Exception {
        slow=true;
        assertEquals(503,request("POST","/api/tasks",body).statusCode());
        assertEquals(0,repository.count());
    }
    @Test void invalidInputDoesNotWrite() throws Exception {
        assertEquals(400,request("POST","/api/tasks","{}").statusCode());
        assertEquals(0,repository.count());
    }
    @Test void longTranscriptKeepsAllPartsAndReadsOriginalNote() throws Exception {
        assertEquals(201, request("POST", "/api/tasks", body).statusCode());
        var task = repository.findAll().getFirst();
        String transcript = ("[00:00:01] " + "微服务课程。".repeat(50) + "\n").repeat(30)
            + "[00:20:00] " + "任务恢复。".repeat(50);
        modelRequests.clear();
        noteService.queue(task, new NoteService.Source("网课.mp4", "微服务", transcript), null);

        String path = "/api/tasks/notes/" + task.id;
        HttpResponse<String> result = request("GET", path, "");
        for (int i = 0; result.statusCode() == 404 && i < 50; i++) {
            Thread.sleep(200);
            result = request("GET", path, "");
        }
        assertEquals(200, result.statusCode(), result.body());
        var content = new tools.jackson.databind.ObjectMapper().readTree(result.body());
        assertEquals(transcript, content.path("transcript").asText());
        assertTrue(modelRequests.size() > 1);
        assertTrue(modelRequests.getLast().contains("1200"));
        assertEquals(modelRequests.size(), content.path("content").path("sections").size());
        assertTrue(content.path("reviewed").asBoolean());
        assertTrue(content.path("paragraphs").get(0).path("text").asText().contains("！"));
        assertFalse(content.path("paragraphs").get(0).path("needsReview").asBoolean());
        assertTrue(notes.summaries().getFirst().ready());

        var mapper = new tools.jackson.databind.ObjectMapper();
        var appearance = new NoteAppearance(java.util.Map.of("summary", java.util.List.of(
            new NoteAppearance.Mark(0, 2, "wave"), new NoteAppearance.Mark(0, 2, "yellow"))), java.util.List.of("sky"));
        String revision = mapper.writeValueAsString(new NoteController.Revision(content.path("version").asText(),
            "新摘要", java.util.List.of(new NoteController.EditedSection("已修改章节", java.util.List.of("新要点"), 1200)), appearance));
        String invalidRevision = revision.replace("\"end\":2", "\"end\":99");
        assertEquals(400, request("PATCH", path, invalidRevision).statusCode());
        assertEquals(200, request("PATCH", path, revision).statusCode());
        var edited = mapper.readTree(request("GET", path, "").body());
        assertEquals("新摘要", edited.path("content").path("summary").asText());
        assertEquals(1200, edited.path("content").path("sections").get(0).path("startSeconds").asInt());
        assertEquals(content.path("paragraphs"), edited.path("paragraphs"));
        assertEquals("sky", edited.path("content").path("appearance").path("branchColors").get(0).asText());
        assertEquals(2, edited.path("content").path("appearance").path("marks").path("summary").size());
        assertEquals(409, request("PATCH", path, revision).statusCode());
        assertEquals(content.path("mapVersion"), edited.path("mapVersion"));
        assertEquals(content.path("mindMap"), edited.path("mindMap"));
        var mapRevision = mapper.writeValueAsString(new NoteController.MapRevision(edited.path("mapVersion").asText(),
            java.util.List.of(new NoteController.EditedSection("导图独立分支", java.util.List.of("关键词"), 42)), null));
        assertEquals(200, request("PATCH", path + "/mind-map", mapRevision).statusCode());
        var mapped = mapper.readTree(request("GET", path, "").body());
        assertEquals("导图独立分支", mapped.path("mindMap").path("sections").get(0).path("title").asText());
        assertEquals(edited.path("content"), mapped.path("content"));
        assertEquals(edited.path("version"), mapped.path("version"));
        assertEquals(409, request("PATCH", path + "/mind-map", mapRevision).statusCode());

        var saved = notes.findById(task.id).orElseThrow();
        saved.subtitleText = "原有短字幕";
        saved.resultJson = """
            {"summary":"旧摘要","sections":[{"title":"旧章节","points":["旧要点"]}]}
            """;
        saved.longTranscript = null;
        saved.longResultJson = null;
        notes.save(saved);
        var original = new tools.jackson.databind.ObjectMapper().readTree(request("GET", path, "").body());
        assertEquals("原有短字幕", original.path("transcript").asText());
    }

    @Test void publicVideoLinkUsesWorkerLeaseAndStoresPlayableRange() throws Exception {
        String path = "/api/tasks/video/link";
        var mapper = new tools.jackson.databind.ObjectMapper();
        String courseId = "11111111-1111-1111-1111-111111111111";
        String untrusted = mapper.writeValueAsString(new VideoController.Link(courseId,
            "https://www.bilibili.com@127.0.0.1/video/BV1vL411G7N7"));
        assertEquals(400, request("POST", path, untrusted).statusCode());
        assertEquals(0, repository.count());

        var client = HttpClient.newHttpClient();
        String videoApi = "http://127.0.0.1:" + port + "/api/tasks/video";
        var claim = HttpRequest.newBuilder(URI.create(videoApi + "/claim"))
            .header("X-Video-Worker-Token", "test-worker").header("X-Video-Link-Support", "true")
            .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(204, client.send(claim, HttpResponse.BodyHandlers.discarding()).statusCode());
        String body = mapper.writeValueAsString(new VideoController.Link(courseId,
            "https://m.bilibili.com/video/BV1vL411G7N7/?p=2&tracking=ignored"));
        assertEquals(201, request("POST", path, body).statusCode());
        var claimed = client.send(claim, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, claimed.statusCode());
        var item = mapper.readValue(claimed.body(), VideoService.Claim.class);
        assertEquals("https://www.bilibili.com/video/BV1vL411G7N7/?p=2", item.sourceUrl());
        var source = HttpRequest.newBuilder(URI.create(videoApi + "/" + item.taskId() + "/source-file"))
            .header("X-Video-Worker-Token", "test-worker").header("X-Video-Lease", item.lease())
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofString("0123456789")).build();
        assertEquals(204, client.send(source, HttpResponse.BodyHandlers.discarding()).statusCode());
        var media = HttpRequest.newBuilder(URI.create(videoApi + "/" + item.taskId() + "/media"))
            .header("Range", "bytes=3-6").GET().build();
        var part = client.send(media, HttpResponse.BodyHandlers.ofString());
        assertEquals(206, part.statusCode());
        assertEquals("3456", part.body());
    }

    @Test void wholeCollectionAdvancesAfterFailedEpisodeAndKeepsOrder() throws Exception {
        var mapper = new tools.jackson.databind.ObjectMapper();
        String courseId = "11111111-1111-1111-1111-111111111111";
        String shared = "【分享的中文标题】 https://www.bilibili.com/video/BV1iE411x7gS/?p=3&share_source=copy_web";
        String api = "/api/tasks/video";
        var preview = request("POST", api + "/preview",
            mapper.writeValueAsString(new VideoCollectionController.PreviewRequest(shared)));
        assertEquals(200, preview.statusCode(), preview.body());
        var catalog = mapper.readTree(preview.body());
        assertEquals("分享的中文标题", catalog.path("title").asText());
        assertEquals(3, catalog.path("selectedIndex").asInt());
        assertEquals(3, catalog.path("episodes").size());

        var client = HttpClient.newHttpClient();
        String root = "http://127.0.0.1:" + port + api;
        var claim = HttpRequest.newBuilder(URI.create(root + "/claim"))
            .header("X-Video-Worker-Token", "test-worker").header("X-Video-Link-Support", "true")
            .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertEquals(204, client.send(claim, HttpResponse.BodyHandlers.discarding()).statusCode());
        var queued = request("POST", api + "/collections",
            mapper.writeValueAsString(new VideoCollectionController.CollectionRequest(courseId, shared, "合集标题", null)));
        assertEquals(201, queued.statusCode(), queued.body());
        var batch = mapper.readValue(queued.body(), VideoCollectionService.View.class);
        assertNotNull(batch.entries().get(0).taskId());
        assertEquals(batch.entries().get(0).taskId(), collections.findById(batch.id()).orElseThrow().currentTaskId);
        assertEquals("单集超过 60 分钟", batch.entries().get(1).problem());
        assertNull(batch.entries().get(2).taskId());
        assertEquals(409, request("POST", "/api/tasks", body).statusCode());

        var first = client.send(claim, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, first.statusCode());
        var item = mapper.readValue(first.body(), VideoService.Claim.class);
        assertTrue(item.sourceUrl().endsWith("?p=1"));
        var failed = HttpRequest.newBuilder(URI.create(root + "/" + item.taskId() + "/failure"))
            .header("X-Video-Worker-Token", "test-worker").header("X-Video-Lease", item.lease())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(new VideoController.Failure("下载失败")))).build();
        assertEquals(204, client.send(failed, HttpResponse.BodyHandlers.discarding()).statusCode());
        collectionService.process();
        var next = client.send(claim, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, next.statusCode());
        assertTrue(mapper.readValue(next.body(), VideoService.Claim.class).sourceUrl().endsWith("?p=3"));
        assertEquals("FAILED", repository.findById(item.taskId()).orElseThrow().status);
        var stopped = request("POST", api + "/collections/" + batch.id() + "/stop", "");
        assertEquals(200, stopped.statusCode());
        assertTrue(mapper.readTree(stopped.body()).path("stopped").asBoolean());
    }

    @Test void fullCollectionRecoversAndRetriesWithoutRepeatingCompletedEpisodes() throws Exception {
        String api = "/api/tasks/video";
        String courseId = "11111111-1111-1111-1111-111111111111";
        var mapper = new tools.jackson.databind.ObjectMapper();
        String source = "https://www.bilibili.com/video/BV1vL411G7N7/";
        var options = new GenerationOptions("detailed", "default", "纳科斯 → Nacos", true);
        assertNull(videoService.claim("test-worker", true));
        var queued = request("POST", api + "/collections",
            mapper.writeValueAsString(new VideoCollectionController.CollectionRequest(courseId, source, "完整验收", options)));
        assertEquals(201, queued.statusCode(), queued.body());
        var batch = mapper.readValue(queued.body(), VideoCollectionService.View.class);

        var abandoned = videoService.claim("test-worker", true);
        var stale = videos.findById(abandoned.taskId()).orElseThrow();
        stale.claimedAt = System.currentTimeMillis() - 400_000;
        videos.save(stale);
        videoService.expireClaims();
        var resumed = videoService.claim("test-worker", true);
        assertEquals(abandoned.taskId(), resumed.taskId());
        assertNotEquals(abandoned.lease(), resumed.lease());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> videoService.renew(abandoned.taskId(), "test-worker", abandoned.lease()));
        finishEpisode(resumed);
        awaitSuccess(resumed.taskId());
        var firstNote = notes.findById(resumed.taskId()).orElseThrow();
        assertTrue(firstNote.generationOptions.contains("detailed"));
        assertTrue(modelRequests.getLast().contains("Nacos"));

        collectionService.process();
        var failed = videoService.claim("test-worker", true);
        assertTrue(failed.sourceUrl().endsWith("?p=2"));
        videoService.fail(failed.taskId(), "test-worker", failed.lease(), "模拟下载失败");

        collectionService.process();
        var third = videoService.claim("test-worker", true);
        assertTrue(third.sourceUrl().endsWith("?p=3"));
        finishEpisode(third);
        awaitSuccess(third.taskId());
        collectionService.process();
        var retry = request("POST", api + "/collections/" + batch.id() + "/episodes/2/retry", "");
        assertEquals(200, retry.statusCode(), retry.body());
        var retried = videoService.claim("test-worker", true);
        assertTrue(retried.sourceUrl().endsWith("?p=2"));
        assertNotEquals(failed.taskId(), retried.taskId());
        finishEpisode(retried);
        awaitSuccess(retried.taskId());
        var complete = mapper.readValue(retry.body(), VideoCollectionService.View.class);
        assertFalse(complete.active());
        assertEquals(java.util.List.of(resumed.taskId(), retried.taskId(), third.taskId()),
            complete.entries().stream().map(VideoCollectionService.Entry::taskId).toList());
        assertNull(videoService.claim("test-worker", true));
    }

    private void finishEpisode(VideoService.Claim item) {
        byte[] bytes = {1, 2, 3, 4};
        videoService.storeLinkedMedia(item.taskId(), "test-worker", item.lease(), bytes.length,
            new java.io.ByteArrayInputStream(bytes));
        videoService.complete(item.taskId(), "test-worker", item.lease(), "[00:00:01] 纳科斯用于服务发现。");
    }

    private void awaitSuccess(String id) throws Exception {
        for (int i = 0; i < 50; i++) {
            String status = repository.findById(id).orElseThrow().status;
            if ("SUCCEEDED".equals(status)) return;
            assertNotEquals("FAILED", status, () -> repository.findById(id).orElseThrow().failureReason);
            Thread.sleep(150);
        }
        fail("Task did not finish: " + id);
    }

    @Test void uploadsVideoWithGenerationSettingsThroughMultipartEntry() throws Exception {
        assertNull(videoService.claim("test-worker", false));
        String boundary = "courselens-fixture";
        String multipart = """
            --courselens-fixture
            Content-Disposition: form-data; name="detail"

            brief
            --courselens-fixture
            Content-Disposition: form-data; name="glossary"

            Nacos
            --courselens-fixture
            Content-Disposition: form-data; name="personalize"

            false
            --courselens-fixture
            Content-Disposition: form-data; name="file"; filename="fixture.mp4"
            Content-Type: video/mp4

            simulated-video
            --courselens-fixture--
            """.replace("\n", "\r\n");
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + "/api/tasks/video/upload/11111111-1111-1111-1111-111111111111"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofString(multipart)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode(), response.body());
        var claim = videoService.claim("test-worker", false);
        videoService.complete(claim.taskId(), "test-worker", claim.lease(), "服务发现。");
        awaitSuccess(claim.taskId());
        var saved = notes.findById(claim.taskId()).orElseThrow();
        assertEquals(new GenerationOptions("brief", "default", "Nacos", false), noteService.options(saved.generationOptions));
    }

    @Test void savedRevisionsLearnStyleAndAssistantUsesSelectedText() throws Exception {
        var mapper = new tools.jackson.databind.ObjectMapper();
        assertEquals(201, request("POST", "/api/tasks", body).statusCode());
        var task = repository.findAll().getFirst();
        noteService.queue(task, new NoteService.Source("示例.srt", "微服务", "字幕内容。"), null);
        awaitSuccess(task.id);
        String path = "/api/tasks/notes/" + task.id;
        var note = mapper.readTree(request("GET", path, "").body());
        var revision = new NoteController.Revision(note.path("version").asText(), "服务发现简要说明",
            java.util.List.of(new NoteController.EditedSection("原理", java.util.List.of("因为服务地址会变化，所以需要服务发现"), null),
                new NoteController.EditedSection("例子", java.util.List.of("例如查询注册中心"), null)));
        assertEquals(200, request("PATCH", path, mapper.writeValueAsString(revision)).statusCode());
        var saved = notes.findById(task.id).orElseThrow();
        assertEquals("22222222-2222-2222-2222-222222222222", saved.styleOwner);
        assertTrue(NoteStyle.instruction(java.util.List.of(saved.styleSignal)).contains("优先保留课中例子"));
        assertTrue(notes.styleSamples("33333333-3333-3333-3333-333333333333",
            org.springframework.data.domain.PageRequest.of(0, 12)).isEmpty());

        var explanation = request("POST", path + "/explain", "{\"mode\":\"example\",\"text\":\"查询注册中心\"}");
        assertEquals(200, explanation.statusCode(), explanation.body());
        assertEquals("例如：查询注册中心。", mapper.readTree(explanation.body()).path("answer").asText());
        assertTrue(modelRequests.getLast().contains("选段：查询注册中心"));
        assertFalse(modelRequests.getLast().contains("字幕内容"));
    }

    @Test void canonicalizesNewPlatformsAndRejectsPlaylistOrCredentials() throws Exception {
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk",
            PublicVideoLink.parse("分享 https://youtu.be/abcdefghijk?si=discard").url());
        assertEquals("https://player.vimeo.com/video/987654321",
            PublicVideoLink.parse("https://player.vimeo.com/video/987654321").url());
        assertEquals(400, request("POST", "/api/tasks/video/preview",
            "{\"shared\":\"https://youtube.com/playlist?list=abc\"}").statusCode());
        assertEquals(400, request("POST", "/api/tasks/video/preview",
            "{\"shared\":\"https://example.com@vimeo.com/987654321\"}").statusCode());
    }

    @Test void manualCancellationReleasesCourse() throws Exception {
        assertEquals(201, request("POST", "/api/tasks", body).statusCode());
        var task = repository.findAll().getFirst();
        assertEquals(200, request("POST", "/api/tasks/" + task.id + "/cancel", "").statusCode());
        assertNull(repository.findById(task.id).orElseThrow().activeCourseId);
        assertEquals(201, request("POST", "/api/tasks", body).statusCode());
    }

    @Test void health() throws Exception { assertEquals(200,request("GET","/actuator/health","").statusCode()); }

    @Test void duplicateAndTransitions() throws Exception {
        assertEquals(201,request("POST","/api/tasks",body).statusCode());
        assertEquals(409,request("POST","/api/tasks",body).statusCode());
        var id=repository.findAll().getFirst().id;
        assertEquals(409,request("PATCH","/api/tasks/"+id+"/status","{\"status\":\"SUCCEEDED\"}").statusCode());
        assertEquals(200,request("PATCH","/api/tasks/"+id+"/status","{\"status\":\"RUNNING\"}").statusCode());
        assertEquals(400,request("PATCH","/api/tasks/"+id+"/status","{\"status\":\"FAILED\"}").statusCode());
        assertEquals(200,request("PATCH","/api/tasks/"+id+"/status","{\"status\":\"SUCCEEDED\"}").statusCode());
        assertEquals(201,request("POST","/api/tasks",body).statusCode());
    }
    @Test void failureReleasesActiveSlot() throws Exception {
        request("POST","/api/tasks",body);
        var id=repository.findAll().getFirst().id;
        request("PATCH","/api/tasks/"+id+"/status","{\"status\":\"RUNNING\"}");
        assertEquals(200,request("PATCH","/api/tasks/"+id+"/status","{\"status\":\"FAILED\",\"failureReason\":\"Bad subtitle\"}").statusCode());
        assertNull(repository.findById(id).orElseThrow().activeCourseId);
        assertEquals(201,request("POST","/api/tasks",body).statusCode());
    }


    @Test void noInstancesDoesNotWrite() throws Exception {
        noInstances=true;
        assertEquals(503,request("POST","/api/tasks",body).statusCode());
        assertEquals(0,repository.count());
    }
    @Test void discoveryIsReadAgainForNextOperation() throws Exception {
        assertEquals(201,request("POST","/api/tasks",body).statusCode());
        noInstances=true;
        assertEquals(503,request("POST","/api/tasks",body).statusCode());
        assertEquals(1,repository.count());
    }
}
