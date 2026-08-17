package com.tim.language_project.client.model;

/**
 * 一次輸入的翻譯結果：整句的中文面、泰文面與羅馬拼音。
 * 兩面都帶回來，呼叫端不需要判斷方向就知道哪個是哪個。
 * token 用量一併帶回來，讓呼叫端可以記錄費用。
 *
 * ── ★ 2026-08-16 起，這裡「只有整句」──────────────────────────────────
 *
 *  逐詞拆解（words）與多種說法（variants）都搬出去，各自成為獨立的呼叫，
 *  而且要使用者在畫面上點了才會跑。
 *
 *  理由：原本一次呼叫要產出這三樣，實測平均 867 個 token。
 *  大型語言模型是一個 token 一個 token 吐出來的，以每秒約 40 個計算，
 *  光是產出就要 22 秒 —— 跟實際量到的等待時間完全吻合。
 *
 *  而使用者按下查詢的那一刻，最想看到的就是泰文和拼音；
 *  拆解與各種說法是看完之後才會慢慢研究的東西。
 *
 *  拆開之後這次呼叫只剩約 60～100 個 token，2～3 秒就看得到結果，
 *  沒點開的部分則完全不花錢。
 */
public record TranslationResult(
        String chineseText,
        String thaiText,
        String romanization,
        String modelName,
        long inputTokens,
        long outputTokens,
        boolean translatable,
        /*
         * 使用者輸入的是「一個詞」還是「一句話」，由模型判斷。
         *
         * 只用來決定畫面上「各種說法」那顆按鈕要不要出現 ——
         * 句子沒有別種說法，讓人按一顆註定沒東西的按鈕沒有意義。
         *
         * ★ 是大寫 Boolean，容得下 null（代表「模型沒說」）。
         *   null 時按鈕照常顯示，理由見 OpenAiTranslationClient 的 TranslationPayload。
         */
        Boolean isWord) {

    /**
     * 「這段輸入根本翻不出來」的結果，例如亂碼、無意義的字。
     * 由模型自己判斷並回報，因為我們沒有字典可以比對，判斷權本來就只在它手上。
     * 用量仍要帶進來 —— 那次呼叫確實發生過、也確實被收費了。
     */
    public static TranslationResult untranslatable(String modelName,
                                                   long inputTokens,
                                                   long outputTokens) {
        return new TranslationResult(null, null, null,
                modelName, inputTokens, outputTokens, false, null);
    }
}
