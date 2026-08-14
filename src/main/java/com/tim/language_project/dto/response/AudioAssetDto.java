package com.tim.language_project.dto.response;

import com.tim.language_project.enums.SpeechLanguageEnum;

/**
 * 音檔資產的投影，也就是 JPQL 建構子表達式要組出來的型別。
 */
public record AudioAssetDto(
        Long id,
        String speechText,
        SpeechLanguageEnum language,
        String filePath) {
}
