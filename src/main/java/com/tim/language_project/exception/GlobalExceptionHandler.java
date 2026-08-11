package com.tim.language_project.exception;

import com.tim.language_project.dto.response.ErrorResponseDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
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

        return toResponse(errorCode.getHttpStatus(), errorCode, traceId);
    }

    /**
     * Last-resort handler. The original exception message is never returned to the
     * caller — it may contain connection strings, file paths, or credential fragments.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(Exception exception) {
        String traceId = newTraceId();

        // Exceptions Spring raises for a malformed request — unknown path, unsupported
        // method, unreadable body — all implement ErrorResponse and already carry the
        // status the caller should see. Keeping that status stops a caller mistake from
        // being reported as a server failure. Logged without a stack trace, since a
        // mistyped URL is not a defect worth an error-level entry.
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode statusCode = errorResponse.getStatusCode();
            ErrorCodeEnum requestErrorCode = resolveRequestErrorCode(statusCode);

            log.warn("[{}] request error: {} - {}",
                    traceId, requestErrorCode.name(), exception.getMessage());

            return toResponse(statusCode, requestErrorCode, traceId);
        }

        ErrorCodeEnum errorCode = ErrorCodeEnum.INTERNAL_ERROR;

        log.error("[{}] unexpected error", traceId, exception);

        return toResponse(errorCode.getHttpStatus(), errorCode, traceId);
    }

    /**
     * Maps the status Spring already decided onto this application's error code, so
     * the caller sees one uniform payload whoever raised the exception.
     */
    private ErrorCodeEnum resolveRequestErrorCode(HttpStatusCode statusCode) {
        if (statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return ErrorCodeEnum.RESOURCE_NOT_FOUND;
        }

        if (statusCode.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return ErrorCodeEnum.METHOD_NOT_ALLOWED;
        }

        if (statusCode.is4xxClientError()) {
            return ErrorCodeEnum.REQUEST_INVALID;
        }

        return ErrorCodeEnum.INTERNAL_ERROR;
    }

    private ResponseEntity<ErrorResponseDto> toResponse(HttpStatusCode statusCode,
                                                        ErrorCodeEnum errorCode,
                                                        String traceId) {
        return ResponseEntity.status(statusCode)
                .body(new ErrorResponseDto(errorCode.name(), errorCode.getMessage(), traceId));
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
