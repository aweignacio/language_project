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
import com.tim.language_project.dto.response.TranslationSummaryDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.service.QueryListService;
import com.tim.language_project.service.TranslationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    /** 最近與收藏兩份清單的 Service，同樣換成假的。 */
    @MockitoBean
    private QueryListService queryListService;

    /*
     * ═══ 測試一：快取命中的成功回應 ═════════════════════════════════════
     */
    @Test
    @DisplayName("查詢成功應回傳翻譯內容與 queryId")
    void shouldReturnTranslation() throws Exception {
        when(translationService.translate("我想喝酒", SpeakerGenderEnum.MALE))
                .thenReturn(new TranslationResponseDto(
                        137L,
                        "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                        "我想喝酒", "ผมอยากดื่มเหล้าครับ", "pǒm yàak dùuem lâo khráp",
                        "/audio/th/a3f9c2.mp3", null, true, false));

        mockMvc.perform(postTranslation("我想喝酒", "MALE"))
                // 快取命中 → 沒有產生新東西 → 200
                .andExpect(status().isOk())
                // 我主張：泰文原封不動傳出去，沒有變成亂碼
                .andExpect(jsonPath("$.thaiText").value("ผมอยากดื่มเหล้าครับ"))
                .andExpect(jsonPath("$.romanization").value("pǒm yàak dùuem lâo khráp"))
                // 我主張：方向與性別有跟著回去，前端要靠它們排版
                .andExpect(jsonPath("$.direction").value("ZH_TO_TH"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                // 我主張：音檔是「網址」不是檔名，前端才能直接放進 <audio src>
                .andExpect(jsonPath("$.thaiAudioUrl").value("/audio/th/a3f9c2.mp3"))
                .andExpect(jsonPath("$.fromCache").value(true))
                // ★ 我主張：queryId 有跟著出去。
                //   前端點「逐詞拆解」和「各種說法」時就是拿它打回來的，
                //   漏掉的話那兩顆按鈕永遠按不動，而且畫面上看不出任何異狀。
                .andExpect(jsonPath("$.queryId").value(137))
                // ★ 我主張：isWord 有跟著出去，而且「我想喝酒」是句子所以是 false。
                //   前端拿它決定「各種說法」那顆按鈕要不要出現 ——
                //   漏掉這個欄位的話前端會讀到 undefined，
                //   undefined !== false 成立，於是句子照樣長出那顆按鈕，
                //   按下去永遠是空的。畫面不會報錯，只會白按。
                .andExpect(jsonPath("$.isWord").value(false));
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
        when(translationService.translate("水", SpeakerGenderEnum.MALE))
                .thenReturn(new TranslationResponseDto(
                        138L,
                        "水", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                        "水", "น้ำ", "náam", null, null, false, true));

        mockMvc.perform(postTranslation("水", "MALE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromCache").value(false))
                // 語音失敗時 thaiAudioUrl 是 null，前端據此顯示成灰色的鍵
                .andExpect(jsonPath("$.thaiAudioUrl").doesNotExist());
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
        when(translationService.translate(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCodeEnum.INPUT_TOO_LONG));

        mockMvc.perform(postTranslation("字".repeat(101), "MALE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.INPUT_TOO_LONG.name()))
                .andExpect(jsonPath("$.message").value(ErrorCodeEnum.INPUT_TOO_LONG.getMessage()))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    /*
     * ═══ 測試四：性別要原樣傳給 Service ═════════════════════════════════
     *
     * gender 是 2026-08-14 新增的欄位。Controller 只做一件事：把它傳下去。
     *
     * ★ 傳丟了不會有任何錯誤訊息 —— Service 會收到 null，
     *   然後所有查詢都變成「沒有性別」的版本，男女看到的東西一模一樣。
     *   這種錯誤只有 verify 抓得到。
     */
    @Test
    @DisplayName("性別應原樣傳給 Service")
    void shouldPassGenderToService() throws Exception {
        when(translationService.translate("我", SpeakerGenderEnum.FEMALE))
                .thenReturn(new TranslationResponseDto(
                        139L,
                        "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.FEMALE,
                        "我", "ฉัน", "chǎn", null, null, false, true));

        mockMvc.perform(postTranslation("我", "FEMALE"))
                .andExpect(status().isCreated());

        verify(translationService).translate("我", SpeakerGenderEnum.FEMALE);
    }

    /*
     * ═══ 測試五：逐詞拆解端點要把 queryId 從網址取出來傳下去 ═════════════
     *
     * 這支端點是 2026-08-16 新開的。使用者在畫面上點「逐詞拆解」才會打過來。
     *
     * ★ 取錯 queryId 的症狀非常難查：畫面會長出「別句話」的逐詞拆解，
     *   而且看起來完全正常（有中文、有泰文、有拼音），只是內容跟你查的無關。
     *   所以這裡用 verify 釘住「傳下去的一定是 137」。
     */
    @Test
    @DisplayName("逐詞拆解端點應把網址上的 queryId 傳給 Service")
    void shouldResolveSegments() throws Exception {
        when(translationService.resolveSegments(137L))
                .thenReturn(List.of(new TranslationSegmentDto(
                        1, "我", "ผม", "pǒm", null, null)));

        mockMvc.perform(post("/api/v1/translations/137/segments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chineseText").value("我"))
                .andExpect(jsonPath("$[0].thaiText").value("ผม"));

        verify(translationService).resolveSegments(137L);
    }

    /*
     * ═══ 測試六：各種說法端點同理 ═══════════════════════════════════════
     *
     * ★ 另外釘住「空清單要回 200 加一個空陣列」，不是 404 也不是 204。
     *   查句子時本來就沒有其他說法，那是正常結果不是錯誤 ——
     *   回 404 的話前端會跳錯誤訊息，使用者以為系統壞了。
     */
    @Test
    @DisplayName("各種說法端點應把網址上的 queryId 傳給 Service，沒有說法時回空陣列")
    void shouldResolveVariants() throws Exception {
        when(translationService.resolveVariants(137L)).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/translations/137/variants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(translationService).resolveVariants(137L);
    }

    /*
     * ═══ 最近清單 ═══════════════════════════════════════════════════════
     *
     * ★ 這支同時在防一個很容易忽略的網址衝突：
     *
     *     GET /api/v1/translations/recent
     *     GET /api/v1/translations/{queryId}
     *
     *   兩條路徑的形狀一模一樣。Spring 會優先比對「寫死的字」而不是變數，
     *   所以 /recent 會正確地走到 recent()，不會被當成 queryId=recent 而回 400。
     *   這個測試就是在確認那件事真的成立 —— 哪天換了路徑比對的實作，
     *   壞掉的方式會是「最近分頁突然變成錯誤訊息」。
     */
    @Test
    @DisplayName("最近清單應回傳 200 與清單內容")
    void shouldReturnRecentList() throws Exception {
        when(queryListService.recent()).thenReturn(List.of(new TranslationSummaryDto(
                137L, "幫我叫計程車", "ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ",
                "chûai rîak tháek-sîi hâi pǒm nòi khráp",
                TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                "/audio/th/a3f9c2.mp3", true)));

        mockMvc.perform(get("/api/v1/translations/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].queryId").value(137))
                .andExpect(jsonPath("$[0].thaiText").value("ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ"))
                // 前端靠這個決定愛心是實心還是空心
                .andExpect(jsonPath("$[0].favorited").value(true))
                // 前端靠 gender 顯示那一列右上角的「男／女」標籤
                .andExpect(jsonPath("$[0].gender").value("MALE"));
    }

    /*
     * ═══ 收藏清單為空時回空陣列，不是 404 ══════════════════════════════
     *
     * ★ 回 404 的話前端會顯示錯誤訊息，但「一筆收藏都沒有」是完全正常的狀態，
     *   應該顯示的是「在查詢結果按愛心就會收進這裡」那句引導。
     */
    @Test
    @DisplayName("收藏清單為空應回傳 200 與空陣列")
    void shouldReturnEmptyFavorites() throws Exception {
        when(queryListService.favorites()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/translations/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    /*
     * ═══ 加入與取消收藏都回 204 ════════════════════════════════════════
     *
     * 不回內容，因為前端已經知道自己按了什麼，回傳整列只是多餘的傳輸。
     */
    @Test
    @DisplayName("加入與取消收藏應回傳 204")
    void shouldToggleFavorite() throws Exception {
        mockMvc.perform(put("/api/v1/translations/137/favorite"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/translations/137/favorite"))
                .andExpect(status().isNoContent());

        verify(queryListService).addFavorite(137L);
        verify(queryListService).removeFavorite(137L);
    }

    /*
     * ═══ 拖曳排序：把整份順序原樣交給 Service ═══════════════════════════
     *
     * 前端送來的是「排好的完整 id 陣列」，不是「把 A 移到第 3 位」：
     *
     *     PUT /api/v1/translations/favorites/order
     *     { "queryIds": [88, 137, 42] }
     *
     * ★ 這支驗的重點是「順序有沒有被保住」。JSON 的陣列是有序的，
     *   但接收端如果不小心宣告成 Set，順序就會在轉換的當下悄悄消失 ——
     *   而畫面上的症狀是「排好了，重新整理又跳回去」。
     *   下面那個 containsExactly 就是在守這件事（用 contains 會抓不到）。
     */
    @Test
    @DisplayName("重新排序應把整份 id 順序原樣傳給 Service")
    void shouldPassFavoriteOrderToService() throws Exception {
        mockMvc.perform(put("/api/v1/translations/favorites/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryIds\":[88,137,42]}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(queryListService).reorderFavorites(captor.capture());

        assertThat(captor.getValue()).containsExactly(88L, 137L, 42L);
    }

    /*
     * ═══ 還原一筆查詢 ═══════════════════════════════════════════════════
     */
    @Test
    @DisplayName("以 id 還原查詢應回傳 200 與完整結果")
    void shouldRestoreTranslationById() throws Exception {
        when(translationService.resolveById(137L)).thenReturn(new TranslationResponseDto(
                137L, "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                "我想喝酒", "ผมอยากดื่มเหล้าครับ", "pǒm yàak dùuem lâo khráp",
                "/audio/th/a3f9c2.mp3", null, true, false));

        mockMvc.perform(get("/api/v1/translations/137"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").value(137))
                .andExpect(jsonPath("$.thaiText").value("ผมอยากดื่มเหล้าครับ"))
                // 還原不會產生任何新東西，所以一定是 true
                .andExpect(jsonPath("$.fromCache").value(true));
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
    postTranslation(String sourceText, String gender) {
        String requestBody =
                "{\"sourceText\":\"" + sourceText + "\",\"gender\":\"" + gender + "\"}";

        return post("/api/v1/translations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.getBytes(StandardCharsets.UTF_8));
    }
}
