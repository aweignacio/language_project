package com.tim.language_project.exception;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  全專案的「錯誤總機」。任何請求處理到一半出錯，最後都會來到這裡，
 *  由它決定回什麼狀態碼、什麼訊息給前端。
 *
 *  下面用三個實際情境各走一遍。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  情境一：使用者查一個不存在的單字
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在網頁輸入「嘎逼」，按下查詢 ─────────────────────────────
 *
 *    瀏覽器送出：GET /api/vocabulary?word=嘎逼
 *
 * ── 第 2 步｜Service 查資料庫，查不到 ───────────────────────────────────
 *
 *        Optional<VocabularyDto> found = vocabularyRepository.findByChineseText("嘎逼");
 *        // found 是空的
 *
 *        throw new BusinessException(ErrorCodeEnum.VOCABULARY_NOT_FOUND);
 *
 *    注意它只丟了一個「錯誤碼」，沒有決定要回幾號、也沒有寫訊息。
 *    那些是這個檔案的工作。
 *
 * ── 第 3 步｜例外一路往上冒 ─────────────────────────────────────────────
 *
 *        Service → Controller → Spring
 *
 *    中間沒有任何人 try/catch，所以它一路衝到 Spring 手上。
 *
 * ── 第 4 步｜Spring 找人處理這個例外 ────────────────────────────────────
 *
 *    Spring 拿著這個例外，依序問三個處理器「這個誰要接？」：
 *
 *        1. ExceptionHandlerExceptionResolver ← 這個檔案掛在這裡（最優先）
 *        2. ResponseStatusExceptionResolver
 *        3. DefaultHandlerExceptionResolver   ← Spring 內建（404、405 由它翻譯）
 *
 *    ★ 排前面的接走了，後面的就完全沒機會。這件事造成過一個 bug，見情境三。
 *
 *    這個檔案標了 @RestControllerAdvice，意思是
 *    「所有 Controller 丟出來的例外都送來我這」。
 *
 * ── 第 5 步｜挑哪一個方法接？ ───────────────────────────────────────────
 *
 *    這個檔案裡有兩個 @ExceptionHandler：
 *
 *        handleBusinessException(BusinessException)  ← 專接我們自己的
 *        handleUnexpectedException(Exception)        ← 兜底，什麼都接
 *
 *    BusinessException 兩個都符合（它也是 Exception 的子孫），
 *    Java 的規則是「挑型別最貼近的」，所以走第一個。
 *
 * ── 第 6 步｜handleBusinessException 做四件事 ───────────────────────────
 *
 *        ① 從例外身上拿出錯誤碼   → ErrorCodeEnum.VOCABULARY_NOT_FOUND
 *        ② 產生一個 8 碼隨機 traceId → 例如 "8c3aa942"
 *        ③ 寫日誌（含完整例外堆疊，只有伺服器看得到）
 *        ④ 照錯誤碼的定義組出回應
 *
 *    ErrorCodeEnum 裡那一條長這樣：
 *
 *        VOCABULARY_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到指定的單字")
 *                                     ↑ 狀態碼      ↑ 給使用者看的訊息
 *
 * ── 第 7 步｜前端實際收到 ───────────────────────────────────────────────
 *
 *        HTTP 404
 *        {
 *          "code": "VOCABULARY_NOT_FOUND",
 *          "message": "找不到指定的單字",
 *          "traceId": "8c3aa942"
 *        }
 *
 *    前端看 code 決定怎麼處理，把 message 直接顯示給使用者。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  情境二：程式真的爆炸了（例如資料庫斷線）
 * ══════════════════════════════════════════════════════════════════════════
 *
 *    丟出來的是 NullPointerException 之類，不是我們的 BusinessException，
 *    所以第 5 步會挑到兜底的 handleUnexpectedException，回 500。
 *
 *    ★ 這裡最重要的一條規則：原始例外訊息絕對不能回給前端。
 *
 *      原始訊息可能長這樣（真的會出現在例外裡）：
 *          "jdbc:sqlserver://localhost:1433;user=sa;password=Sqlserver123456"
 *
 *      這種東西回到瀏覽器，等於把資料庫密碼送給任何一個路人。
 *      所以回應的 message 永遠換成罐頭訊息「系統發生非預期錯誤」，
 *      真正的內容只寫進伺服器日誌。
 *
 *      使用者回報問題時，把畫面上的 traceId 唸給你，
 *      你就能在日誌裡搜到那一筆，看到完整堆疊 —— 這就是 traceId 的用途。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  情境三：網址打錯（這裡修過一個 bug，別改回去）
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 原本的錯誤行為 ──────────────────────────────────────────────────────
 *
 *    使用者的瀏覽器去要一個不存在的音檔：
 *
 *        GET /audio/abc123.mp3      （這個檔案不存在）
 *
 *    Spring 丟出 NoResourceFoundException，本來該由第 4 步的第 3 順位
 *    翻譯成 404。但兜底處理器寫的是 @ExceptionHandler(Exception.class) ——
 *    「任何例外我都接」，而它排在第 1 順位，於是連這個也被它接走，回了 500。
 *
 *    結果：使用者打錯字，被講成伺服器爆炸。
 *    而且 ErrorCodeEnum 裡的 AUDIO_FILE_NOT_FOUND(404) 永遠不會出現。
 *
 * ── 現在的修法 ──────────────────────────────────────────────────────────
 *
 *    在兜底處理器的第一行先問一句「你身上有沒有自己帶狀態碼？」
 *
 *        if (exception instanceof ErrorResponse errorResponse) {
 *            → 有 → 沿用它說的（404 / 405 / 400）
 *        }
 *        → 沒有 → 才是真的沒人料到的錯，回 500
 *
 *    ErrorResponse 是 Spring 那些「自己知道該回幾號」的例外共同的身分證。
 *    網址打錯、HTTP 方法用錯、請求內容讀不懂，全都有這張身分證。
 *
 *    ★ 關鍵觀念：狀態碼是「最後處理它的人 return 什麼」決定的，
 *      不是例外自己帶著就會生效。原本的寫法沒去問例外，直接寫死 500。
 *
 *    順帶把日誌也分開了：使用者打錯（4xx）只記 warn 且不印堆疊，
 *    否則有人網址打錯或掃描機器人亂打，日誌就被一整篇 ERROR 洗版。
 *
 *  測試檔：src/test/java/com/tim/language_project/exception/
 *          GlobalExceptionHandlerTest.java
 *          （其中兩個測試就是專門守著情境三不要被改回去）
 */

import com.tim.language_project.dto.response.ErrorResponseDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
     * 請求內容讀不懂時（JSON 少一個括號、多一個逗號、根本不是 JSON）。
     * 這是「送的人弄錯了」，要回 400，不是伺服器的錯。
     * 必須單獨接住，因為 HttpMessageNotReadableException 沒有實作 ErrorResponse，
     * 下面那個兜底處理器問不出它的狀態碼，會誤判成 500。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleUnreadableRequest(
            HttpMessageNotReadableException exception) {
        ErrorCodeEnum errorCode = ErrorCodeEnum.REQUEST_INVALID;
        String traceId = newTraceId();

        // 不印堆疊：這是對方送錯，不是我們的程式有問題。
        log.warn("[{}] unreadable request body: {}", traceId, exception.getMessage());

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
