package com.zjgsu.oes;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

final class NoteStyle {
    private static final Pattern EXAMPLE = Pattern.compile("例如|比如|举例|假设|例子");
    private static final Pattern CAUSE = Pattern.compile("因为|因此|原因|所以|原理");

    static String observe(ModelNotes.Result before, ModelNotes.Result after) {
        var oldPoints = points(before);
        var newPoints = points(after);
        if (oldPoints.equals(newPoints) && before.summary().equals(after.summary())
                && before.sections().stream().map(ModelNotes.Section::title).toList()
                    .equals(after.sections().stream().map(ModelNotes.Section::title).toList())) return "";
        int structure = Integer.compare(after.sections().size(), before.sections().size());
        int detail = Integer.compare(newPoints.stream().mapToInt(String::length).sum() + after.summary().length(),
            oldPoints.stream().mapToInt(String::length).sum() + before.summary().length());
        int examples = change(EXAMPLE.asPredicate(), oldPoints, newPoints);
        int reasons = change(CAUSE.asPredicate(), oldPoints, newPoints);
        return structure + "," + detail + "," + examples + "," + reasons;
    }

    private static List<String> points(ModelNotes.Result result) {
        return result.sections().stream().flatMap(section -> section.points().stream()).toList();
    }

    private static int change(Predicate<String> matches, List<String> before, List<String> after) {
        return Long.compare(after.stream().filter(matches).count(), before.stream().filter(matches).count());
    }

    static String instruction(List<String> samples) {
        int[] votes = new int[4];
        for (String sample : samples) {
            String[] dimensions = sample.split(",");
            for (int i = 0; i < votes.length; i++) votes[i] += Integer.parseInt(dimensions[i]);
        }
        return (votes[0] > 0 ? "主题按小节拆分。" : votes[0] < 0 ? "合并重复主题。" : "")
            + (votes[1] > 0 ? "在所选详略范围内保留较多细节。" : votes[1] < 0 ? "在所选详略范围内尽量紧凑。" : "")
            + (votes[2] > 0 ? "优先保留课中例子。" : votes[2] < 0 ? "精简例子。" : "")
            + (votes[3] > 0 ? "优先解释原因和机制。" : votes[3] < 0 ? "简洁交代原因。" : "");
    }
}
