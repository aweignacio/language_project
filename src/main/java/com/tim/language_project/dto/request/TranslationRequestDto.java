package com.tim.language_project.dto.request;

/**
 * 查詢請求的內容，對應前端送來的 JSON：{ "sourceText": "我想喝酒" }。
 */
public record TranslationRequestDto(String sourceText) {
}
