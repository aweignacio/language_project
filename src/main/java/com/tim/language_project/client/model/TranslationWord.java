package com.tim.language_project.client.model;

/**
 * 翻譯服務回傳的其中一個詞。
 */
public record TranslationWord(
        String chineseText,
        String thaiText,
        String romanization) {
}
