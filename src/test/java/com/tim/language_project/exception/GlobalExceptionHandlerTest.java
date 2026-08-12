package com.tim.language_project.exception;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案在測什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測 GlobalExceptionHandler ——「程式出錯時，回給前端的東西對不對」。
 *
 *  每次都要一起看兩樣：
 *      (1) HTTP 狀態碼   前端用這個判斷「錯在誰」
 *      (2) 回傳的 JSON   前端用這個顯示訊息給使用者
 *
 *  狀態碼分兩大類，這個分別是整個檔案的重點：
 *      4xx ＝「你（使用者）弄錯了」  例：404 網址不存在、405 用錯方式
 *      5xx ＝「我（伺服器）壞了」    例：500 程式爆炸
 *
 *  ★ 把 4xx 回報成 5xx，等於把「使用者打錯字」講成「伺服器爆炸」。
 *    測試三、測試四就是在防這件事。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你打指令到看見 Tests run: 4
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在終端機打指令 ───────────────────────────────────────────
 *
 *        .\mvnw.cmd -B test "-Dtest=GlobalExceptionHandlerTest"
 *
 * ── 第 2 步｜@WebMvcTest 只啟動「網頁請求」那一塊 ───────────────────────
 *
 *        @DataJpaTest  只啟動「資料庫」那一塊
 *        @WebMvcTest   只啟動「網頁請求」那一塊  ← 這個檔案
 *
 *    兩個都叫「切片測試」——只啟動 Spring 的一部分就停，快得多。
 *    所以這支測試不連資料庫、不需要 Docker、不需要 OpenAI 金鑰。
 *
 *    GlobalExceptionHandler 標了 @RestControllerAdvice，屬於網頁那一塊，
 *    會被自動載入，不用手動指定。
 *
 * ── 第 3 步｜為什麼要自己造一個假的 Controller ──────────────────────────
 *
 *        @Import(GlobalExceptionHandlerTest.TestController.class)
 *
 *    例外處理器要有東西「丟例外」給它接，才測得到。
 *    但這個專案現在還沒有任何真的 Controller（Task 9 才會做）。
 *
 *    所以檔案最下面自己造了一個 TestController，
 *    它唯一的用途就是「按照要求丟出指定的例外」。
 *    它在 src/test/java 底下，永遠不會被打包上線。
 *
 * ── 第 4 步｜測試一實際做了什麼 ─────────────────────────────────────────
 *
 *        mockMvc.perform(get("/test/business-error"))
 *
 *    MockMvc 是「假的瀏覽器」。
 *    沒有它的話，要測一個網址回什麼，得先真的把網站啟動起來、佔一個 port、
 *    再用工具去打它。MockMvc 讓你直接在記憶體裡問 Spring：
 *    「如果有人打這個網址，你會回什麼？」
 *
 * ── 第 5 步｜這一行按下去，內部真正跑了什麼 ─────────────────────────────
 *
 *    mockMvc.perform(get("/test/business-error"))
 *      │
 *      ├→ Spring 找到 TestController 的 throwBusinessException 方法
 *      │     └─ throw new BusinessException(ErrorCodeEnum.VOCABULARY_NOT_FOUND);
 *      │
 *      ├→ 例外往上冒，Spring 依序問處理器「誰要接？」
 *      │     └─ GlobalExceptionHandler 排第一順位，它的
 *      │        handleBusinessException 接走（型別最貼近）
 *      │
 *      ├→ 該方法從例外拿出錯誤碼、產生 traceId、寫日誌、組回應
 *      │
 *      └→ MockMvc 收到：
 *             HTTP 404
 *             { "code": "VOCABULARY_NOT_FOUND",
 *               "message": "找不到指定的單字",
 *               "traceId": "8c3aa942" }
 *
 * ── 第 6 步｜檢查 ───────────────────────────────────────────────────────
 *
 *        .andExpect(status().isNotFound())                    ← 狀態碼是 404
 *        .andExpect(jsonPath("$.code").value("VOCABULARY_NOT_FOUND"))
 *        .andExpect(jsonPath("$.traceId").isNotEmpty())
 *
 *    jsonPath("$.code") 的意思是「回傳的 JSON 裡，最外層那個 code 欄位」。
 *    $ 代表最外層，點號往裡面鑽。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  四個測試各自在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *    測試一  丟 BusinessException     防：錯誤碼指定的狀態與訊息沒被照著回
 *
 *    測試二  丟含連線字串的例外        防：★把資料庫密碼回給前端★
 *                                        假 Controller 丟的例外訊息裡故意藏了
 *                                        "jdbc:sqlserver://...password=..."，
 *                                        這支測試主張它不會出現在回應裡
 *
 *    測試三  打一個不存在的網址        防：404 被兜底處理器蓋成 500
 *    測試四  用錯 HTTP 方法（POST）    防：405 被兜底處理器蓋成 500
 *
 *    ★ 測試三、四是「回歸測試」—— 這兩件事真的發生過。
 *      修好之前它們是紅的（Status expected:<404> but was:<500>），
 *      現在它們守著那個修正不被改回去。
 *      詳細經過見 GlobalExceptionHandler 開頭的「情境三」。
 */

import com.tim.language_project.enums.ErrorCodeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 這兩張貼紙的作用見開頭第 2、3 步。
@WebMvcTest
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    /** 假的瀏覽器，不用真的啟動網站就能問「有人打這個網址，你會回什麼」。 */
    @Autowired
    private MockMvc mockMvc;

    /*
     * ═══ 測試一：自己定義的 BusinessException ═══════════════════════════
     *
     * 這是「正常」的情況，用來確認基本功能是好的。
     * 丟出帶 VOCABULARY_NOT_FOUND 的例外，就該照那個錯誤碼的定義回 404。
     */
    @Test
    @DisplayName("BusinessException 應回傳錯誤碼指定的狀態與訊息")
    void shouldMapBusinessExceptionToItsErrorCode() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                // 我主張：狀態碼是 404（因為 VOCABULARY_NOT_FOUND 定義成 NOT_FOUND）
                .andExpect(status().isNotFound())
                // 我主張：JSON 裡的 code 欄位是 VOCABULARY_NOT_FOUND
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.VOCABULARY_NOT_FOUND.name()))
                .andExpect(jsonPath("$.message").value(ErrorCodeEnum.VOCABULARY_NOT_FOUND.getMessage()))
                // 我主張：traceId 有值，出事時才能拿它去 log 裡對照
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    /*
     * ═══ 測試二：沒預料到的例外，不可以把內情講出去 ═════════════════════
     *
     * 假 Controller 丟的例外訊息裡故意藏了一段假的資料庫連線字串。
     * 這種訊息絕對不能回給前端 —— 瀏覽器上看得到，等於把伺服器的內部
     * 資訊送給任何一個路人。
     *
     * 所以這裡除了驗「回 500」，更重要的是驗「訊息被換成罐頭訊息了」。
     */
    @Test
    @DisplayName("未預期的例外應回 500，且不得外洩原始例外訊息")
    void shouldHideInternalDetailsOnUnexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.INTERNAL_ERROR.name()))
                // 我主張：回傳的是罐頭訊息「系統發生非預期錯誤」
                .andExpect(jsonPath("$.message").value(ErrorCodeEnum.INTERNAL_ERROR.getMessage()))
                // 我主張：連線字串沒有出現在回應裡
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("jdbc:sqlserver"))));
    }

    /*
     * ═══ 測試三：網址打錯，該回 404 不該回 500 ══════════════════════════
     *
     * 這就是這次要修的問題。
     *
     * /test/does-not-exist 這個網址不存在，Spring 本來會回 404。
     * 但 GlobalExceptionHandler 裡的 @ExceptionHandler(Exception.class)
     * 意思是「任何例外我都接」，而它比 Spring 內建的處理器優先，
     * 於是連「網址不存在」也被它接走，一律回報成 500。
     *
     * 這支測試現在會失敗，訊息會是：
     *     Status expected:<404> but was:<500>
     * 修好之後才會變綠。
     */
    @Test
    @DisplayName("網址不存在應回 404，不可被兜底處理器蓋成 500")
    void shouldReturnNotFoundForUnknownPath() throws Exception {
        mockMvc.perform(get("/test/does-not-exist"))
                .andExpect(status().isNotFound())
                // 我主張：狀態碼對了以外，格式也要跟其他錯誤一致，
                //         前端才不用為這種情況寫特例。
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    /*
     * ═══ 測試四：HTTP 方式用錯，該回 405 不該回 500 ═════════════════════
     *
     * 跟測試三是同一個病，換一個症狀。
     *
     * /test/business-error 只接受 GET，這裡故意用 POST 去打。
     * Spring 本來會回 405（方法不被允許），同樣會被兜底處理器蓋成 500。
     */
    @Test
    @DisplayName("HTTP 方法不支援應回 405，不可被兜底處理器蓋成 500")
    void shouldReturnMethodNotAllowedForWrongHttpMethod() throws Exception {
        mockMvc.perform(post("/test/business-error"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").isNotEmpty());
    }

    /*
     * ═══ 這個 Controller 是假的，只為測試而存在 ═════════════════════════
     *
     * 真正的 Controller 要 Task 9 才會做。
     * 這裡只需要有東西能「按照要求丟例外」，好讓例外處理器有事可做。
     *
     * static 是因為它寫在別的類別裡面；沒有 static 的話，
     * Spring 沒辦法單獨把它生出來（它會被綁在外層的測試物件上）。
     */
    @RestController
    static class TestController {

        @GetMapping("/test/business-error")
        String throwBusinessException() {
            throw new BusinessException(ErrorCodeEnum.VOCABULARY_NOT_FOUND);
        }

        @GetMapping("/test/unexpected-error")
        String throwUnexpectedException() {
            // 訊息裡故意放連線字串，測試二要驗證它不會被回傳出去。
            throw new IllegalStateException(
                    "jdbc:sqlserver://localhost:1433;user=sa;password=Sqlserver123456");
        }
    }
}
