package com.tim.language_project.dto.response;

import com.tim.language_project.enums.GenderUsageEnum;
import com.tim.language_project.enums.PolitenessEnum;

/**
 * 一個中文詞在泰文的其中一種說法，要回傳給前端。
 * 前端依 genderUsage 排序（符合使用者性別的排前面）、依 politeness 上色。
 * thaiAudioUrl 為 null 代表音檔還沒產生。
 */
public record TranslationVariantDto(
        String thaiText,
        String romanization,
        GenderUsageEnum genderUsage,
        PolitenessEnum politeness,
        String note,
        String thaiAudioUrl) {
}
