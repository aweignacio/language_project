package com.tim.language_project.dto.response;

/**
 * Uniform error payload. The trace identifier lets a user report locate the
 * matching server log entry.
 */
public record ErrorResponseDto(
        String code,
        String message,
        String traceId) {
}
