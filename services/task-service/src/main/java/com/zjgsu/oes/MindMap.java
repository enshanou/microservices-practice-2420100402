package com.zjgsu.oes;

import java.util.List;

record MindMap(List<ModelNotes.Section> sections, NoteAppearance appearance) {
    static MindMap forResult(ModelNotes.Result result) {
        if (result.mindMap() != null) {
            return result.mindMap();
        }
        var legacy = result.appearance();
        var colors = legacy == null ? null : new NoteAppearance(java.util.Map.of(), legacy.branchColors());
        return new MindMap(compact(result.sections()), colors);
    }

    static List<ModelNotes.Section> compact(List<ModelNotes.Section> sections) {
        return sections.stream().map(section -> new ModelNotes.Section(
            shorten(section.title(), 26),
            section.points().stream().limit(2).map(MindMap::keyPhrase).distinct().toList(),
            section.startSeconds()
        )).toList();
    }

    private static String keyPhrase(String text) {
        String phrase = text.split("[，。；！？;!?\\n]", 2)[0].strip();
        return shorten(phrase.length() >= 3 ? phrase : text.strip(), 30);
    }

    private static String shorten(String text, int length) {
        return text.codePointCount(0, text.length()) <= length ? text
            : text.substring(0, text.offsetByCodePoints(0, length - 1)).stripTrailing() + "…";
    }
}
