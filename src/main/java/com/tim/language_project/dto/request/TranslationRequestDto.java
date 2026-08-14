package com.tim.language_project.dto.request;

import com.tim.language_project.enums.SpeakerGenderEnum;

/**
 * 查詢請求：{ "sourceText": "我", "gender": "MALE" }。
 * 輸入泰文時 gender 會被後端忽略，因為泰翻中沒有性別概念。
 */
public record TranslationRequestDto(String sourceText, SpeakerGenderEnum gender) {
}
