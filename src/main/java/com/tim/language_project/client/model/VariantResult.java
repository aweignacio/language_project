package com.tim.language_project.client.model;

import java.util.List;

/**
 * 一個詞的各種說法，以及那一次呼叫的用量。
 *
 * ★ 為什麼跟 TranslationResult 分開：
 *   2026-08-16 起，多種說法是使用者點了「多種說法」才跑的獨立呼叫。
 *   用量各自記帳，才看得出這個功能實際花了多少錢，也才能判斷值不值得。
 */
public record VariantResult(
        List<TranslationVariant> variants,
        String modelName,
        long inputTokens,
        long outputTokens) {
}
