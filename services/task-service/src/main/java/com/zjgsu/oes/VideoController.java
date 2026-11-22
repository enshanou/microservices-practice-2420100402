package com.zjgsu.oes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/tasks/video")
public class VideoController {
    private static final String TOKEN = "X-Video-Worker-Token";
    private final VideoService service;
    private final VideoRepository videos;

    public VideoController(VideoService service, VideoRepository videos) {
        this.service = service;
        this.videos = videos;
    }

    record Transcript(@NotBlank @Size(max = VideoService.MAX_TRANSCRIPT_BYTES) String text) {}
    record Link(@NotBlank @Pattern(regexp = "[a-f0-9-]{36}") String courseId,
                @NotBlank @Size(max = 1200) String url, @Size(max = 200) String title, @Valid GenerationOptions options) {
        Link(String courseId, String url) { this(courseId, url, null, null); }
    }
    record Failure(@NotBlank @Size(max = 1000) String reason) {}

    @GetMapping("/config")
    public Map<String, Boolean> config() {
        return Map.of("available", service.available(), "linkAvailable", service.linkAvailable());
    }

    @GetMapping
    public List<VideoRepository.Info> list() {
        return videos.summaries();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@RequestParam String courseId, @RequestParam MultipartFile file) {
        return service.enqueue(courseId, file, null);
    }

    @PostMapping("/link")
    @ResponseStatus(HttpStatus.CREATED)
    public Task createLink(@Valid @RequestBody Link link) {
        return service.enqueueLink(link.courseId(), link.url(), link.title(), link.options());
    }

    @PostMapping(value = "/upload/{courseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Task upload(@PathVariable String courseId, @RequestParam MultipartFile file, @Valid @ModelAttribute GenerationOptions options) {
        return service.enqueue(courseId, file, options);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.CREATED)
    public Task retry(@PathVariable String id) {
        return service.retry(id);
    }

    @PostMapping("/claim")
    public ResponseEntity<VideoService.Claim> claim(@RequestHeader(value = TOKEN, required = false) String token,
            @RequestHeader(value = "X-Video-Link-Support", defaultValue = "false") boolean supportsLinks) {
        var claim = service.claim(token, supportsLinks);
        return claim == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(claim);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable String id,
            @RequestHeader(value = TOKEN, required = false) String token,
            @RequestHeader(value = "X-Video-Lease", required = false) String lease) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(service.file(id, token, lease));
    }

    @GetMapping("/{id}/media")
    public ResponseEntity<Resource> media(@PathVariable String id) {
        var type = MediaTypeFactory.getMediaType(service.mediaName(id)).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).body(service.media(id));
    }

    @PostMapping(value = "/{id}/source-file", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sourceFile(@PathVariable String id, @RequestHeader(value = TOKEN, required = false) String token,
            @RequestHeader(value = "X-Video-Lease", required = false) String lease, HttpServletRequest request) throws IOException {
        service.storeLinkedMedia(id, token, lease, request.getContentLengthLong(), request.getInputStream());
    }

    @PostMapping("/{id}/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void renew(@PathVariable String id, @RequestHeader(value = TOKEN, required = false) String token,
                      @RequestHeader(value = "X-Video-Lease", required = false) String lease) {
        service.renew(id, token, lease);
    }

    @PostMapping("/{id}/transcript")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable String id, @RequestHeader(value = TOKEN, required = false) String token,
                         @RequestHeader(value = "X-Video-Lease", required = false) String lease,
                         @Valid @RequestBody Transcript transcript) {
        service.complete(id, token, lease, transcript.text());
    }

    @PostMapping("/{id}/failure")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fail(@PathVariable String id, @RequestHeader(value = TOKEN, required = false) String token,
                     @RequestHeader(value = "X-Video-Lease", required = false) String lease,
                     @Valid @RequestBody Failure failure) {
        service.fail(id, token, lease, failure.reason());
    }
}
