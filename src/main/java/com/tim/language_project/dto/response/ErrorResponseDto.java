package com.tim.language_project.dto.response;

/**
 * 統一的錯誤回應格式。
 * traceId 是給使用者回報問題時用的，拿它可以在伺服器日誌裡找到對應的那一筆。
 */
public record ErrorResponseDto(
        String code,
        String message,
        String traceId) {
}
