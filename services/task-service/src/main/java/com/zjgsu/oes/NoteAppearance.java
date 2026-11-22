package com.zjgsu.oes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

record NoteAppearance(Map<String, List<Mark>> marks, List<String> branchColors) {
    private static final Set<String> INKS = Set.of("underline", "wave", "yellow", "mint", "sky", "rose");
    private static final Set<String> COLORS = Set.of("default", "mint", "sky", "yellow", "rose", "lavender");

    record Mark(int start, int end, String ink) {}

    void validate(String summary, List<ModelNotes.Section> sections) {
        if (marks == null || branchColors == null || branchColors.size() != sections.size()
                || branchColors.stream().anyMatch(color -> color == null || !COLORS.contains(color))) {
            throw invalid();
        }
        var fields = new HashMap<String, String>();
        fields.put("summary", summary);
        for (int i = 0; i < sections.size(); i++) {
            var section = sections.get(i);
            fields.put(i + ".title", section.title());
            for (int j = 0; j < section.points().size(); j++) {
                fields.put(i + ".point." + j, section.points().get(j));
            }
        }
        for (var entry : marks.entrySet()) {
            String text = fields.get(entry.getKey());
            var spans = entry.getValue();
            if (text == null || spans == null || spans.size() > 4800) {
                throw invalid();
            }
            for (var span : spans) {
                if (span == null || span.ink() == null || !INKS.contains(span.ink()) || span.start() < 0
                        || span.end() <= span.start() || span.end() > text.length()
                        || splitsPair(text, span.start()) || splitsPair(text, span.end())) {
                    throw invalid();
                }
            }
        }
    }

    private static boolean splitsPair(String text, int offset) {
        return offset > 0 && offset < text.length() && Character.isHighSurrogate(text.charAt(offset - 1))
            && Character.isLowSurrogate(text.charAt(offset));
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "笔记标注或导图颜色无效，请重新打开学习页后再修改");
    }
}
