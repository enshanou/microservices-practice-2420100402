package com.zjgsu.oes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/courses")
public class TranscriptController {
    private static final String TIME = "\\d{1,3}:\\d{2}(?::\\d{2})?[.,]\\d{3}";
    private static final Pattern TIMING = Pattern.compile("^(" + TIME + ")\\s+-->\\s+" + TIME + "(?:\\s+.*)?$");
    private final CourseService courses;
    private final TranscriptRepository transcripts;

    public TranscriptController(CourseService courses, TranscriptRepository transcripts) {
        this.courses = courses;
        this.transcripts = transcripts;
    }

    public record Upload(@NotBlank @Size(max = 200) String fileName,
                         @NotBlank @Size(max = 65536) String content) {}
    public record Source(String fileName, String title, String text) {}

    @GetMapping("/transcripts")
    public List<TranscriptRepository.FileInfo> list() {
        return transcripts.files();
    }

    @PostMapping("/{id}/transcript")
    public TranscriptRepository.FileInfo save(@PathVariable String id, @Valid @RequestBody Upload upload) {
        courses.get(id);
        String fileName = upload.fileName().strip();
        if (fileName.contains("/") || fileName.contains("\\")) {
            throw badRequest("请选择一个字幕文件");
        }
        if (bytes(upload.content()) > 65536) {
            throw badRequest("字幕文件不能超过 64 KB");
        }
        String text = normalize(fileName, upload.content());
        if (text.isBlank()) {
            throw badRequest("文件中没有可读取的字幕正文");
        }
        if (bytes(text) > 7000) {
            throw badRequest("清理后的正文超过 7 KB，请选取一小段课程字幕");
        }
        var transcript = new CourseTranscript();
        transcript.courseId = id;
        transcript.fileName = fileName;
        transcript.subtitleText = text;
        transcripts.saveAndFlush(transcript);
        return new TranscriptRepository.FileInfo(id, fileName);
    }

    @GetMapping("/{id}/transcript")
    public Source get(@PathVariable String id) {
        var course = courses.get(id);
        var transcript = transcripts.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "请先为这门课程上传字幕"));
        return new Source(transcript.fileName, course.title, transcript.subtitleText);
    }

    static String normalize(String fileName, String content) {
        String name = fileName.toLowerCase(Locale.ROOT);
        String text = content.replaceFirst("^\\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').strip();
        if (name.endsWith(".txt")) {
            return text;
        }
        if (!name.endsWith(".srt") && !name.endsWith(".vtt")) {
            throw badRequest("请选择 .srt、.vtt 或 .txt 文件");
        }
        var cues = new ArrayList<String>();
        for (String block : text.split("\n[\\t ]*\n")) {
            if (block.startsWith("NOTE") || block.startsWith("STYLE") || block.startsWith("REGION")) {
                continue;
            }
            String[] lines = block.strip().split("\n");
            for (int i = 0; i < lines.length - 1; i++) {
                var timing = TIMING.matcher(lines[i].strip());
                if (timing.matches()) {
                    String caption = String.join(" ", java.util.Arrays.copyOfRange(lines, i + 1, lines.length))
                        .replaceAll("<[^>]*>", "").strip();
                    if (!caption.isBlank()) {
                        cues.add("[" + timing.group(1) + "] " + caption);
                    }
                    break;
                }
            }
        }
        return String.join("\n", cues);
    }

    private static int bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
