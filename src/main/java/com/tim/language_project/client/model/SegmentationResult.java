package com.tim.language_project.client.model;

import java.util.List;

/**
 * 逐詞拆解的結果，以及那一次呼叫的用量。
 *
 * ★ 為什麼跟 TranslationResult 分開：
 *   2026-08-16 起，翻譯與逐詞拆解是兩次獨立的 AI 呼叫。
 *   翻譯是按下查詢就跑，拆解是使用者點了才跑 —— 兩者的用量要各自記帳，
 *   才看得出「拆解功能到底花了多少錢」。
 */
public record SegmentationResult(
        List<TranslationWord> words,
        String modelName,
        long inputTokens,
        long outputTokens) {
}
