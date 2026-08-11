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
 * 把各種例外轉換成統一的錯誤回應格式。
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
     * 兜底處理器，接住上面沒人認領的例外。
     * 原始的例外訊息絕對不回傳給前端 ——
     * 裡面可能含有連線字串、檔案路徑或金鑰片段。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(Exception exception) {
        String traceId = newTraceId();

        // Spring 自己為「請求有問題」丟出的例外 —— 網址不存在、HTTP 方法不支援、
        // 請求內容讀不懂 —— 都實作了 ErrorResponse，身上已經帶著該回的狀態碼。
        // 這裡沿用它說的狀態碼，才不會把「使用者打錯」講成「伺服器壞掉」。
        // 這種情況只記 warn 且不印堆疊，網址打錯不值得留一整篇錯誤紀錄。
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
     * 把 Spring 已經決定好的狀態碼，對應到本專案自己的錯誤碼。
     * 這樣不管例外是誰丟的，前端收到的格式都一樣。
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
