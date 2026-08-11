package com.tim.language_project.exception;

import com.tim.language_project.dto.response.ErrorResponseDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Translates exceptions into the uniform error payload.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessException(BusinessException exception) {
        ErrorCodeEnum errorCode = exception.getErrorCode();
        String traceId = newTraceId();

        log.warn("[{}] business error: {}", traceId, errorCode.name(), exception);

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorResponseDto(errorCode.name(), errorCode.getMessage(), traceId));
    }

    /**
     * Last-resort handler. The original exception message is never returned to the
     * caller — it may contain connection strings, file paths, or credential fragments.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(Exception exception) {
        ErrorCodeEnum errorCode = ErrorCodeEnum.INTERNAL_ERROR;
        String traceId = newTraceId();

        log.error("[{}] unexpected error", traceId, exception);

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorResponseDto(errorCode.name(), errorCode.getMessage(), traceId));
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
