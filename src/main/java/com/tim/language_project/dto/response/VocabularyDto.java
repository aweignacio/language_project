package com.tim.language_project.dto.response;

import com.tim.language_project.enums.GenderUsageEnum;
import com.tim.language_project.enums.PolitenessEnum;

/**
 * 單字庫裡的一個說法。
 */
public record VocabularyDto(
        Long id,
        String chineseText,
        String thaiText,
        String romanization,
        GenderUsageEnum genderUsage,
        PolitenessEnum politeness,
        String note) {
}
