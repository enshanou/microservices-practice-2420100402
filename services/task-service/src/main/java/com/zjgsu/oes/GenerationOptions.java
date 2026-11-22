package com.zjgsu.oes;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GenerationOptions(
        @Pattern(regexp = "brief|balanced|detailed") String detail,
        @Pattern(regexp = "default|deepseek|gpt|claude|kimi") String provider,
        @Size(max = 1000) String glossary,
        Boolean personalize) {
    static final GenerationOptions DEFAULT = new GenerationOptions("balanced", "default", "", true);

    GenerationOptions normalized() {
        return new GenerationOptions(detail == null ? "balanced" : detail, provider == null ? "default" : provider,
            glossary == null ? "" : glossary.strip(), personalize == null || personalize);
    }

    String instruction() {
        return switch (detail) {
            case "brief" -> "简约：保留最关键的结论，每个主题用 1 到 2 条短句。";
            case "detailed" -> "详细：解释概念的原因和步骤，保留课中例子、条件和易错点，使用完整句子。";
            default -> "适中：每个主题总结核心概念和必要解释。";
        };
    }
}
