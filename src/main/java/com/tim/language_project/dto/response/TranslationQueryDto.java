package com.tim.language_project.dto.response;

import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;

/**
 * 查詢快取的投影，也就是 JPQL 建構子表達式要組出來的型別。
 */
public record TranslationQueryDto(
        Long id,
        String sourceText,
        TranslationDirectionEnum direction,
        SpeakerGenderEnum gender,
        String chineseText,
        String thaiText,
        String romanization) {
}
