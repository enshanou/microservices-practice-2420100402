package com.zjgsu.oes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks/video")
class VideoCollectionController {
    private final BilibiliCatalog catalog;
    private final VideoCollectionService collections;

    VideoCollectionController(BilibiliCatalog catalog, VideoCollectionService collections) {
        this.catalog = catalog;
        this.collections = collections;
    }

    record PreviewRequest(@NotBlank @Size(max = 1200) String shared) {}
    record CollectionRequest(@NotBlank @Pattern(regexp = "[a-f0-9-]{36}") String courseId,
                             @NotBlank @Size(max = 1200) String shared, @Size(max = 200) String title, @Valid GenerationOptions options) {}

    @PostMapping("/preview")
    BilibiliCatalog.Preview preview(@Valid @RequestBody PreviewRequest request) {
        var source = PublicVideoLink.external(request.shared());
        if (source != null) {
            return new BilibiliCatalog.Preview(source.title(), source.url(), source.title(), 1,
                List.of(new BilibiliCatalog.Episode(1, source.title(), 0, source.url())));
        }
        return catalog.preview(request.shared());
    }

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    VideoCollectionService.View create(@Valid @RequestBody CollectionRequest request) {
        return collections.start(request.courseId(), request.shared(), request.title(), request.options());
    }

    @GetMapping("/collections")
    List<VideoCollectionService.View> list() {
        return collections.list();
    }

    @PostMapping("/collections/{id}/episodes/{index}/retry")
    VideoCollectionService.View retry(@PathVariable String id, @PathVariable int index) {
        return collections.retry(id, index);
    }

    @PostMapping("/collections/{id}/stop")
    VideoCollectionService.View stop(@PathVariable String id) {
        return collections.stop(id);
    }
}
