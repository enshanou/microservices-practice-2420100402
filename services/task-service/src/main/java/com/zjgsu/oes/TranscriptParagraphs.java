package com.zjgsu.oes;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class TranscriptParagraphs {
    private static final Pattern TIMESTAMP = Pattern.compile("^\\[(?:(\\d{1,3}):)?(\\d{2}):(\\d{2})(?:[.,]\\d{3})?]\\s*");
    private static final int TARGET_LENGTH = 220;

    record Paragraph(Integer startSeconds, String text, boolean needsReview) {}

    static List<Paragraph> group(String raw) {
        var paragraphs = new ArrayList<Paragraph>();
        var text = new StringBuilder();
        Integer start = null;
        Integer previous = null;
        for (String line : raw.split("\\R")) {
            var stamp = TIMESTAMP.matcher(line);
            Integer seconds = stamp.find() ? seconds(stamp.group(1), stamp.group(2), stamp.group(3)) : null;
            String words = (seconds == null ? line : line.substring(stamp.end())).strip();
            boolean pause = previous != null && seconds != null && seconds - previous >= 7;
            boolean tooLong = text.length() + words.length() > TARGET_LENGTH;
            if (!text.isEmpty() && (words.isEmpty() || pause || tooLong)) {
                paragraphs.add(new Paragraph(start, text.toString(), true));
                text.setLength(0);
            }
            if (words.isEmpty()) {
                continue;
            }
            if (text.isEmpty()) {
                start = seconds;
            } else if (asciiWord(text.charAt(text.length() - 1)) || asciiWord(words.charAt(0))) {
                text.append(' ');
            }
            while (words.length() > 600) {
                int boundary = words.offsetByCodePoints(0, 300);
                paragraphs.add(new Paragraph(start, words.substring(0, boundary), true));
                words = words.substring(boundary);
            }
            text.append(words);
            previous = seconds;
        }
        if (!text.isEmpty()) {
            paragraphs.add(new Paragraph(start, text.toString(), true));
        }
        return paragraphs;
    }

    private static int seconds(String hours, String minutes, String seconds) {
        return (hours == null ? 0 : Integer.parseInt(hours) * 3600)
            + Integer.parseInt(minutes) * 60 + Integer.parseInt(seconds);
    }

    private static boolean asciiWord(char character) {
        return character < 128 && Character.isLetterOrDigit(character);
    }
}
