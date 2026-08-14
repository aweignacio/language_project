package com.tim.language_project.controller;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  POST /api/v1/audio 是「你在畫面上點了逐詞的播放鍵」時打的那支 API。
 *
 *  ★ 這支 API 會花錢。★
 *
 *  這是它跟其他 API 最大的不同 —— 別的端點打錯了只是拿到錯誤訊息，
 *  這支打進來就是一次 OpenAI 的付費呼叫。
 *
 *  所以它有一道守門的檢查：只准合成「我們系統自己產生過的文字」。
 *  沒有這道檢查的話，任何人寫個迴圈送隨機字串進來，
 *  就能把帳戶餘額燒光，而且每一筆看起來都是正常請求。
 *
 * ── 哪些東西被換成假的 ──────────────────────────────────────────────────
 *
 *  @WebMvcTest 只啟動「網頁」那一層（Controller、例外處理器），
 *  不啟動 Service 和資料庫。所以：
 *
 *      SpeechTextGuard    換成假的 —— 我們自己指定「這段文字算不算已知」
 *      AudioAssetService  換成假的 —— 它會花錢，絕對不能讓它真的跑
 *
 *  MockMvc 是「假的瀏覽器」，可以送出 HTTP 請求而不用真的開伺服器。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  測試一  已知的文字 → 回 200，帶著音檔網址
 *  測試二  ★未知的文字 → 回 400，而且絕對不可以呼叫 AudioAssetService★
 *          （這一題就是防止帳戶被燒的那道關卡，壞了不會有任何徵兆）
 *  測試三  合成失敗   → 回 404，不要假裝成功給前端一個壞掉的網址
 */

import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.service.AudioAssetService;
import com.tim.language_project.service.SpeechTextGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AudioController.class)
class AudioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpeechTextGuard speechTextGuard;

    @MockitoBean
    private AudioAssetService audioAssetService;

    @Test
    @DisplayName("已知的文字應回傳音檔網址")
    void shouldReturnAudioUrlForKnownText() throws Exception {
        when(speechTextGuard.isKnown("เหล้า", SpeechLanguageEnum.TH)).thenReturn(true);
        when(audioAssetService.resolveAudioUrl("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.of("/audio/th/a1b2c3.mp3"));

        mockMvc.perform(audioRequest("เหล้า", "TH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audioUrl").value("/audio/th/a1b2c3.mp3"));
    }

    /*
     * ★ 這個測試守著帳戶餘額。
     *   最後那一行 verify(..., never()) 是重點：不只要回 400，
     *   更重要的是「那次付費呼叫根本沒有發生」。
     */
    @Test
    @DisplayName("未知的文字應被擋下且不得呼叫語音服務")
    void shouldRejectUnknownTextWithoutSynthesizing() throws Exception {
        when(speechTextGuard.isKnown("任意輸入的字", SpeechLanguageEnum.ZH)).thenReturn(false);

        mockMvc.perform(audioRequest("任意輸入的字", "ZH"))
                .andExpect(status().isBadRequest());

        // ★ 一毛錢都不能花
        verify(audioAssetService, never()).resolveAudioUrl(anyString(), any());
    }

    @Test
    @DisplayName("合成失敗時應回傳 404 而非假裝成功")
    void shouldReturnNotFoundWhenSynthesisFails() throws Exception {
        when(speechTextGuard.isKnown("เหล้า", SpeechLanguageEnum.TH)).thenReturn(true);
        when(audioAssetService.resolveAudioUrl("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.empty());

        mockMvc.perform(audioRequest("เหล้า", "TH"))
                .andExpect(status().isNotFound());
    }

    /**
     * 組出一個合成音檔的請求。
     * ★ 內容要先轉成 UTF-8 的位元組再送，直接送字串的話泰文與中文會變成問號，
     *   守門檢查就永遠比對不到，測試會以看不出原因的方式失敗。
     */
    private MockHttpServletRequestBuilder audioRequest(String speechText, String language) {
        String requestBody = """
                { "speechText": "%s", "language": "%s" }
                """.formatted(speechText, language);

        return post("/api/v1/audio")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody.getBytes(StandardCharsets.UTF_8));
    }
}
