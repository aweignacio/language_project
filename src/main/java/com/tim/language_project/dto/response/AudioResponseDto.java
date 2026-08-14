package com.tim.language_project.dto.response;

/**
 * 合成音檔的回應，網址可直接放進前端的 audio 標籤。
 */
public record AudioResponseDto(String audioUrl) {
}
