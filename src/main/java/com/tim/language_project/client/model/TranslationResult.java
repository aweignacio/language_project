package com.tim.language_project.client.model;

import java.util.List;

/**
 * 一次輸入的翻譯結果。
 * 中文面與泰文面都帶回來，呼叫端不需要判斷方向就知道哪個是哪個。
 * variants 只有在「輸入本身就是一個詞」時才有內容，其餘情況是空清單。
 * token 用量一併帶回來，讓呼叫端可以記錄費用。
 */
public record TranslationResult(
        String chineseText,
        String thaiText,
        String romanization,
        List<TranslationWord> words,
        List<TranslationVariant> variants,
        String modelName,
        long inputTokens,
        long outputTokens,
        boolean translatable) {

    /**
     * 「這段輸入根本翻不出來」的結果，例如亂碼、無意義的字。
     * 由模型自己判斷並回報，因為我們沒有字典可以比對，判斷權本來就只在它手上。
     * 用量仍要帶進來 —— 那次呼叫確實發生過、也確實被收費了。
     */
    public static TranslationResult untranslatable(String modelName,
                                                   long inputTokens,
                                                   long outputTokens) {
        return new TranslationResult(null, null, null, List.of(), List.of(),
                modelName, inputTokens, outputTokens, false);
    }
}
