package com.tim.language_project.client.model;

import com.tim.language_project.enums.GenderUsageEnum;
import com.tim.language_project.enums.PolitenessEnum;

/**
 * 一個中文詞在泰文的其中一種說法，例如「我」對應的 ผม。
 * 只有查單一個詞時才會有內容，查句子時是空的。
 */
public record TranslationVariant(
        String thaiText,
        String romanization,
        GenderUsageEnum genderUsage,
        PolitenessEnum politeness,
        String note) {
}
