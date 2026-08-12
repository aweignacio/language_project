package com.tim.language_project.controller;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案在測什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測 TranslationController —— 也就是「對外那道門」。
 *
 *  它測的不是翻譯正不正確（那是 Service 和 Client 的測試在管），
 *  而是「HTTP 這一層有沒有接對」：
 *      網址對不對、收到的 JSON 有沒有正確變成 Java 物件、
 *      回去的 JSON 欄位名對不對、狀態碼對不對。
 *
 *  ⚠ 不連資料庫、不連 OpenAI。Service 整個換成假的。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你打指令到看見 Tests run: 3
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在終端機打指令 ───────────────────────────────────────────
 *
 *        .\mvnw.cmd -B test "-Dtest=TranslationControllerTest"
 *
 * ── 第 2 步｜@WebMvcTest(TranslationController.class) ────────────────────
 *
 *    只啟動網頁那一塊，而且只載入括號裡指定的這一個 Controller。
 *    不寫括號的話會把所有 Controller 都載入，那就變成在測別人的東西了。
 *
 * ── 第 3 步｜@MockitoBean 把 Service 換成假的 ───────────────────────────
 *
 *        @MockitoBean TranslationService translationService;
 *
 *    ★ 跟前面測試用的 @Mock 有什麼不同？
 *
 *        @Mock        單純做一個假物件，自己 new 出來用（不涉及 Spring）
 *        @MockitoBean 做一個假物件，並且「塞進 Spring 容器裡」，
 *                     取代原本那個真的 Bean
 *
 *      因為這裡 Controller 是由 Spring 生出來的，我們沒辦法自己 new，
 *      所以要用後者去替換它的依賴。
 *
 * ── 第 4 步｜以測試一為例，假的瀏覽器送出請求 ───────────────────────────
 *
 *        mockMvc.perform(post("/api/v1/translations")
 *                        .contentType(MediaType.APPLICATION_JSON)
 *                        .content("{ \\"sourceText\\": \\"我想喝酒\\" }"))
 *
 *    ★ 為什麼用 POST 不用 GET？
 *
 *      兩個理由：
 *        (1) 這個呼叫會「產生東西」—— 寫資料庫、生一個 mp3 檔案。
 *            GET 的慣例是「只讀取、不改變任何東西」。
 *        (2) 輸入是中文，放在網址裡要做編碼（我想喝酒 會變成
 *            %E6%88%91%E6%83%B3...），又醜又容易出錯。放在請求內容裡就沒這問題。
 *
 * ── 第 5 步｜這一行按下去，內部真正跑了什麼 ─────────────────────────────
 *
 *    mockMvc.perform(post(...))
 *      │
 *      ├→ Spring 依網址找到 TranslationController 的 translate 方法
 *      │
 *      ├→ 把請求內容那段 JSON 轉成 TranslationRequestDto 物件
 *      │     { "sourceText": "我想喝酒" }  →  new TranslationRequestDto("我想喝酒")
 *      │     這件事由 @RequestBody 觸發，Jackson 這套函式庫負責轉換
 *      │
 *      ├→ 呼叫 translationService.translate("我想喝酒")
 *      │     └─ 【假的】回傳我們在測試裡寫死的結果
 *      │
 *      ├→ 把回傳的 TranslationResponseDto 轉回 JSON
 *      │
 *      └→ MockMvc 收到 HTTP 200 與那段 JSON
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  三個測試各自在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *    一  快取命中的成功回應   防：欄位名稱拼錯、逐詞資料沒帶出去、
 *                               中文與泰文在傳輸過程中變成亂碼
 *    二  新查詢回 201         防：分不出「這次有沒有花錢」
 *    三  Service 丟出錯誤     防：★錯誤沒有被轉成統一格式★
 *                               這一條同時驗證了 Controller 和
 *                               GlobalExceptionHandler 有正確接在一起
 */

import com.tim.language_project.dto.response.TranslationResponseDto;
import com.tim.language_project.dto.response.TranslationSegmentDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.service.TranslationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 這兩張貼紙的作用見開頭第 2、3 步。
@WebMvcTest(TranslationController.class)
class TranslationControllerTest {

    /** 假的瀏覽器。 */
    @Autowired
    private MockMvc mockMvc;

    /** 假的 Service，並且已經替換掉 Spring 容器裡真的那個。 */
    @MockitoBean
    private TranslationService translationService;

    /*
     * ═══ 測試一：快取命中的成功回應 ═════════════════════════════════════
     */
    @Test
    @DisplayName("查詢成功應回傳翻譯內容與逐詞對照")
    void shouldReturnTranslation() throws Exception {
        when(translationService.translate("我想喝酒")).thenReturn(new TranslationResponseDto(
                "我想喝酒", "ฉันอยากดื่มเหล้า", "chǎn yàak dùuem lâo",
                "/audio/a3f9c2.mp3", true,
                List.of(new TranslationSegmentDto(1, "我", "ฉัน", "chǎn"))));

        mockMvc.perform(postTranslation("我想喝酒"))
                // 快取命中 → 沒有產生新東西 → 200
                .andExpect(status().isOk())
                // 我主張：泰文原封不動傳出去，沒有變成亂碼
                .andExpect(jsonPath("$.thaiText").value("ฉันอยากดื่มเหล้า"))
                .andExpect(jsonPath("$.romanization").value("chǎn yàak dùuem lâo"))
                // 我主張：音檔是「網址」不是檔名，前端才能直接放進 <audio src>
                .andExpect(jsonPath("$.audioUrl").value("/audio/a3f9c2.mp3"))
                .andExpect(jsonPath("$.fromCache").value(true))
                // 我主張：逐詞對照有跟著出去，而且欄位名稱正確
                .andExpect(jsonPath("$.segments[0].chineseText").value("我"))
                .andExpect(jsonPath("$.segments[0].thaiText").value("ฉัน"));
    }

    /*
     * ═══ 測試二：新查詢要回 201，跟快取命中分得出來 ═════════════════════
     *
     * 200 = 這次沒做什麼新東西（讀快取）
     * 201 = 這次真的建立了新資源（呼叫了 OpenAI、寫了資料庫、生了音檔）
     *
     * 前端兩種都當成功處理，但這個差別讓我們光看伺服器日誌就知道
     * 「哪些請求真的花了錢」。
     */
    @Test
    @DisplayName("新建立的查詢應回傳 201")
    void shouldReturnCreatedForNewTranslation() throws Exception {
        when(translationService.translate("水")).thenReturn(new TranslationResponseDto(
                "水", "น้ำ", "náam", null, false,
                List.of(new TranslationSegmentDto(1, "水", "น้ำ", "náam"))));

        mockMvc.perform(postTranslation("水"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromCache").value(false))
                // 語音失敗時 audioUrl 是 null，前端據此不顯示播放鍵
                .andExpect(jsonPath("$.audioUrl").doesNotExist());
    }

    /*
     * ═══ 測試三：Service 丟出的錯誤要變成統一格式 ═══════════════════════
     *
     * 這個測試橫跨兩個檔案：Controller 沒有寫任何 try/catch，
     * 錯誤是一路往上冒、由 GlobalExceptionHandler 接住的。
     *
     * 所以它同時驗證了「兩者有正確接在一起」—— 這種接線問題
     * 各自的單元測試都測不到。
     */
    @Test
    @DisplayName("Service 拋出錯誤時應回傳統一的錯誤格式")
    void shouldReturnUniformErrorPayload() throws Exception {
        when(translationService.translate(anyString()))
                .thenThrow(new BusinessException(ErrorCodeEnum.INPUT_TOO_LONG));

        mockMvc.perform(postTranslation("字".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.INPUT_TOO_LONG.name()))
                .andExpect(jsonPath("$.message").value(ErrorCodeEnum.INPUT_TOO_LONG.getMessage()))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    /*
     * ═══ 這個方法不是測試，只是少打字 ═══════════════════════════════════
     *
     * 三個測試都要送同樣格式的請求，包成一個方法。
     *
     * ★ 為什麼內容要轉成 byte 再送？
     *   中文和泰文是非 ASCII 字元，直接送字串可能被用錯的編碼解讀而變成亂碼。
     *   明確指定 UTF-8 轉成位元組，就不會有這個問題。
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    postTranslation(String sourceText) {
        String requestBody = "{\"sourceText\":\"" + sourceText + "\"}";

        return post("/api/v1/translations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.getBytes(StandardCharsets.UTF_8));
    }
}
