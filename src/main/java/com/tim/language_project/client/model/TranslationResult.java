package com.tim.language_project.client.model;

import java.util.List;

/**
 * 一次輸入的翻譯結果。
 * 只查一個詞時 words 就只有一個元素，所以呼叫端不需要為「單詞」和「句子」寫兩套邏輯。
 * token 用量一併帶回來，讓呼叫端可以記錄費用。
 */
public record TranslationResult(
        String thaiText,
        String romanization,
        List<TranslationWord> words,
        String modelName,
        long inputTokens,
        long outputTokens) {
}
