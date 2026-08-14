package com.tim.language_project.dto.request;

import com.tim.language_project.enums.SpeechLanguageEnum;

/**
 * 合成音檔的請求：{ "speechText": "เหล้า", "language": "TH" }。
 */
public record AudioRequestDto(String speechText, SpeechLanguageEnum language) {
}
