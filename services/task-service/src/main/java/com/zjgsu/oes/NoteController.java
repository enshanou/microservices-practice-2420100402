package com.zjgsu.oes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/tasks/notes")
public class NoteController {
    private final NoteService service;
    private final NoteRepository notes;
    private final ModelNotes model;
    private final ObjectMapper json;

    public NoteController(NoteService service, NoteRepository notes, ModelNotes model, ObjectMapper json) {
        this.service = service;
        this.notes = notes;
        this.model = model;
        this.json = json;
    }

    record Configuration(boolean configured, String model, List<ModelProfiles.Choice> providers) {}
    record Create(@NotBlank @Pattern(regexp = "[a-f0-9-]{36}") String courseId, @Valid GenerationOptions options) {}
    record Question(@NotBlank @Size(max = 2400) String text, @NotBlank @Pattern(regexp = "rephrase|example") String mode) {}
    record Note(String fileName, String model, ModelNotes.Result content, MindMap mindMap, String transcript,
                List<TranscriptParagraphs.Paragraph> paragraphs, boolean reviewed, String videoTaskId, String sourceUrl, String version, String mapVersion, GenerationOptions options) {}
    record EditedSection(@NotBlank @Size(max = 200) String title,
                         @NotEmpty @Size(max = 25) List<@NotBlank @Size(max = 1200) String> points,
                         @Min(0) @Max(3600) Integer startSeconds) {}
    record Revision(@NotBlank String version, @NotBlank @Size(max = 60000) String summary,
                    @NotEmpty @Size(max = 600) List<@NotNull @Valid EditedSection> sections, NoteAppearance appearance) {
        Revision(String version, String summary, List<EditedSection> sections) {
            this(version, summary, sections, null);
        }
    }

    record MapRevision(@NotBlank String version,
                       @NotEmpty @Size(max = 600) List<@NotNull @Valid EditedSection> sections, NoteAppearance appearance) {}

    @GetMapping("/config")
    public Configuration config() {
        return new Configuration(model.configured(), model.configured() ? model.model() : "", model.choices());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@Valid @RequestBody Create request) {
        return service.enqueue(request.courseId(), request.options());
    }

    @GetMapping
    public List<NoteRepository.Info> list() {
        return notes.summaries();
    }

    @GetMapping("/{id}")
    public Note get(@PathVariable String id) {
        return view(service.ready(id));
    }

    @PatchMapping("/{id}")
    public Note revise(@PathVariable String id, @Valid @RequestBody Revision revision) {
        return view(service.revise(id, revision));
    }

    @PatchMapping("/{id}/mind-map")
    public Note reviseMap(@PathVariable String id, @Valid @RequestBody MapRevision revision) {
        return view(service.reviseMap(id, revision));
    }

    @PostMapping("/{id}/regenerate")
    @ResponseStatus(HttpStatus.CREATED)
    public Task regenerate(@PathVariable String id, @Valid @RequestBody(required = false) GenerationOptions options) {
        return service.regenerate(id, options);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.CREATED)
    public Task retry(@PathVariable String id) {
        return service.retry(id);
    }

    @PostMapping("/{id}/explain")
    public java.util.Map<String, String> explain(@PathVariable String id, @Valid @RequestBody Question question) {
        var note = service.ready(id);
        try {
            return java.util.Map.of("answer", model.explain(note.title, question.text(), question.mode(),
                service.options(note.generationOptions).provider()));
        } catch (IllegalStateException error) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_GATEWAY, error.getMessage());
        }
    }

    private Note view(NoteJob note) {
        var result = json.readValue(note.result(), ModelNotes.Result.class);
        boolean reviewed = result.paragraphs() != null;
        var video = service.sourceVideo(note);
        var map = MindMap.forResult(result);
        return new Note(note.fileName, note.model,
            new ModelNotes.Result(result.summary(), result.sections(), null, result.appearance(), null), map,
            note.transcript(), reviewed ? result.paragraphs() : TranscriptParagraphs.group(note.transcript()),
            reviewed, video == null ? null : video.taskId, video == null ? null : video.sourceUrl,
            service.version(result), service.mapVersion(map), service.options(note.generationOptions));
    }
}
