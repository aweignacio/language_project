package com.tim.language_project.exception;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案是什麼？
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  全專案的「錯誤總機」。任何請求處理到一半出錯，最後都會來到這裡，
 *  由它決定要回什麼狀態碼、什麼訊息給前端。
 *
 *  沒有它的話，例外會變成 Spring 預設的錯誤頁面，格式不一、
 *  而且可能把內部訊息（連線字串、檔案路徑）直接印給使用者看。
 *
 * ── @RestControllerAdvice 是什麼？ ──────────────────────────────────────
 *
 *  「這個類別負責處理所有 Controller 丟出來的例外。」
 *  不用在每個 Controller 寫 try/catch，Spring 會自動把例外送過來。
 *
 * ── Spring 怎麼決定由誰處理？ ───────────────────────────────────────────
 *
 *  例外發生後，Spring 拿著它依序問一串處理器：
 *
 *      1. ExceptionHandlerExceptionResolver  ← 這個檔案掛在這裡（最優先）
 *      2. ResponseStatusExceptionResolver
 *      3. DefaultHandlerExceptionResolver    ← Spring 內建（404、405 由它翻譯）
 *
 *  排前面的接走了，後面的就沒機會。這件事造成過一個 bug，見下方。
 *
 *  同一個檔案裡有兩個 @ExceptionHandler 時，Java 挑「型別最貼近」的那個：
 *  BusinessException 兩個都符合，但它比 Exception 貼近，所以走第一個。
 *
 * ── 這裡修過的一個 bug（重要）─────────────────────────────────────────
 *
 *  原本兜底處理器寫成「任何例外都回 500」。但它排在第 1 順位，
 *  於是連 Spring 內建要回 404 的「網址不存在」也被它接走，變成 500 ——
 *  等於把「使用者打錯字」講成「伺服器爆炸」。
 *
 *  修法是在兜底處理器裡先問一句「你身上有沒有帶狀態碼」
 *  （instanceof ErrorResponse），有的話就沿用它說的。
 *
 * ── 何時執行 ────────────────────────────────────────────────────────────
 *
 *   Service throw BusinessException  →  handleBusinessException
 *                                       → 照 ErrorCodeEnum 的定義回應
 *
 *   網址打錯、方法用錯（Spring 丟的）→  handleUnexpectedException
 *                                       → 沿用 Spring 判定的 404 / 405
 *
 *   NullPointerException 之類         →  handleUnexpectedException
 *                                       → 500，訊息換成罐頭訊息，內情只進日誌
 *
 *  測試檔：src/test/java/.../exception/GlobalExceptionHandlerTest.java
 *  相關：ErrorCodeEnum、BusinessException、ErrorResponseDto。
 */

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
