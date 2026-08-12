package com.tim.language_project.client.openai;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案在測什麼？
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測 OpenAiTranslationClient —— 拿到 OpenAI 的回應之後，我們自己那段處理對不對。
 *
 *  ⚠ 這支測試「不會」真的呼叫 OpenAI，不花錢、不需要金鑰、離線也能跑。
 *    做法是把 ChatModel（真正去連線的那一層）換成假的，
 *    叫它「有人問你就回這串固定的 JSON」。
 *
 *    所以測的是我們自己的程式：
 *      - 有沒有正確把 JSON 轉成 TranslationResult
 *      - 有沒有記錄「真實的」token 用量（而不是拿字數去猜）
 *      - 回應殘缺、或連線爆炸時，有沒有丟出正確的錯誤碼
 *
 *    測不到的是 OpenAI 會不會真的回這種格式 —— 那要靠手動驗證。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  執行流程：按下 mvnw test 之後發生什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *   掃 src/test/java，找到名字以 Test 結尾的類別
 *     ↓
 *   看到 @ExtendWith(MockitoExtension.class) → 這個檔案交給 Mockito 接管
 *     ↓
 *   對「每一個」@Test 方法，完整重複這五步：
 *     ① 建立全新的測試類別實例
 *     ② 建立全新的假物件（所有 @Mock 欄位）
 *     ③ 執行 @BeforeEach setUp()
 *     ④ 執行 @Test 方法本體
 *     ⑤ Mockito 收尾檢查（有沒有設定了卻沒用到的假動作）
 *
 *   第 ①② 步每個測試都重來一次，所以測試之間不會互相污染 ——
 *   上一個測試教假模型講的話，不會殘留到下一個。
 *
 * ── 「換掉底層」是在哪一行完成的？ ──────────────────────────────────────
 *
 *   在 setUp() 的最後一段：
 *
 *       openAiTranslationClient = new OpenAiTranslationClient(
 *               chatModel,          ← 假的（不會連線）
 *               apiUsageRecorder,   ← 假的（不會寫資料庫）
 *               pricingProperties,  ← 真的
 *               "gpt-4o-mini");
 *
 *   OpenAiTranslationClient 沒有自己去 new 一個連線物件，
 *   而是「在建構子把 ChatModel 要進來」。
 *   正式環境 Spring 塞真的給它，測試時我們塞假的給它，
 *   被測的類別完全不知道差別，照樣跑它自己的邏輯。
 *
 *   這就是正式程式要寫成建構子注入的原因 ——
 *   不是為了好看，是寫成這樣才測得動。
 *
 * ── translate() 按下去之後，內部真正跑了什麼 ────────────────────────────
 *
 *   translate("我想喝酒")                     ← 真的（我們寫的）
 *     │
 *     ├→ chatClient.prompt().user(...).call() ← 真的（Spring AI 的）
 *     │     ├─ 組 Prompt：系統提示詞 ＋ 使用者輸入 ＋ 自動產生的 JSON 格式規範
 *     │     ├─ 問 chatModel.getOptions()   →→ 【假的】回空選項（setUp 教的）
 *     │     └─ 呼叫 chatModel.call(prompt) →→ 【假的】回寫死的 ChatResponse
 *     │                                          （givenModelReplies 教的）
 *     │        ⚠ 網路連線就是在這一步被攔掉的，整支測試唯一的假動作在此
 *     │
 *     ├→ .responseEntity(TranslationPayload.class)
 *     │     └─ 真的 ChatClient 把 JSON 字串解析成 TranslationPayload
 *     │
 *     └→ 回到我們的程式：取 usage → 檢查殘缺 → 記帳 → 組 TranslationResult
 *
 *   只有兩個箭頭指向「假的」，其餘全部是真的程式在跑。
 *
 * ── assertThat 和 verify 差在哪？ ───────────────────────────────────────
 *
 *       assertThat  檢查「回傳值」   → 這個方法吐出來的東西對不對
 *       verify      檢查「做了什麼」 → 它有沒有去呼叫別人、用什麼參數呼叫
 *
 *   下面的測試二整段沒有一個 assertThat，因為「有沒有記對帳」不在回傳值裡。
 *   記帳是一個「副作用」—— 方法轉頭去呼叫了別人。
 *   這種事只能用 verify 檢查：問那個假物件「剛才有人拿什麼參數來呼叫你？」
 */

import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.client.usage.ApiUsageRecorder;
import com.tim.language_project.config.AiPricingProperties;
import com.tim.language_project.enums.AiProviderEnum;
import com.tim.language_project.enums.AiServiceTypeEnum;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.enums.UsageUnitTypeEnum;
import com.tim.language_project.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OpenAiTranslationClientTest {

    /** 真正會連上 OpenAI 的那一層，整支測試就是靠換掉它來斷網。 */
    @Mock
    private ChatModel chatModel;

    @Mock
    private ApiUsageRecorder apiUsageRecorder;

    private OpenAiTranslationClient openAiTranslationClient;

    @BeforeEach
    void setUp() {
        // ChatClient 每次送出請求前，都會去問模型「你的預設選項是什麼」，
        // 拿到 null 會當場 NPE。假模型不會自己回答，所以這裡給它一份空的。
        given(chatModel.getOptions()).willReturn(ChatOptions.builder().build());

        AiPricingProperties pricingProperties = new AiPricingProperties();
        pricingProperties.setTranslationInputPrice(new BigDecimal("0.00000500"));
        pricingProperties.setTranslationOutputPrice(new BigDecimal("0.00001500"));

        openAiTranslationClient = new OpenAiTranslationClient(
                chatModel, apiUsageRecorder, pricingProperties, "gpt-4o-mini");
    }

    /*
     * ═══ 測試一：正常回應要正確轉成 TranslationResult ═══════════════════
     */
    @Test
    @DisplayName("正常回應應轉成翻譯結果，並帶回逐詞對照")
    void shouldConvertResponseIntoTranslationResult() {
        givenModelReplies("""
                {
                  "thaiText": "ฉันอยากดื่มเหล้า",
                  "romanization": "chǎn yàak dùuem lâo",
                  "words": [
                    {"chineseText": "我", "thaiText": "ฉัน", "romanization": "chǎn"},
                    {"chineseText": "想", "thaiText": "อยาก", "romanization": "yàak"},
                    {"chineseText": "喝", "thaiText": "ดื่ม", "romanization": "dùuem"},
                    {"chineseText": "酒", "thaiText": "เหล้า", "romanization": "lâo"}
                  ]
                }
                """, 120, 45);

        TranslationResult result = openAiTranslationClient.translate("我想喝酒");

        assertThat(result.thaiText()).isEqualTo("ฉันอยากดื่มเหล้า");
        assertThat(result.romanization()).isEqualTo("chǎn yàak dùuem lâo");
        assertThat(result.modelName()).isEqualTo("gpt-4o-mini");

        // 逐詞對照的順序必須保持原樣，前端是照這個順序排版的。
        assertThat(result.words()).hasSize(4);
        assertThat(result.words().get(0).chineseText()).isEqualTo("我");
        assertThat(result.words().get(3).thaiText()).isEqualTo("เหล้า");
        assertThat(result.words().get(3).romanization()).isEqualTo("lâo");
    }

    /*
     * ═══ 測試二：記錄的必須是「真實」token 數 ═══════════════════════════
     *
     * 這支測試是刻意加的。計畫原本的寫法是拿「字數」當 token 數，
     * 那是猜的，帳會對不起來。這裡主張記到的必須是 OpenAI 回報的 120 / 45。
     *
     * 用輸入「水」（一個字）搭配 120 個 token，就是為了讓「用字數猜」的寫法
     * 一定會被抓出來 —— 字數是 1，真實 token 是 120，差很遠。
     */
    @Test
    @DisplayName("應記錄 OpenAI 回報的真實 token 用量，而非以字數估算")
    void shouldRecordActualTokenUsage() {
        givenModelReplies("""
                {
                  "thaiText": "น้ำ",
                  "romanization": "náam",
                  "words": [{"chineseText": "水", "thaiText": "น้ำ", "romanization": "náam"}]
                }
                """, 120, 45);

        openAiTranslationClient.translate("水");

        verify(apiUsageRecorder).record(
                eq(AiProviderEnum.OPENAI),
                eq(AiServiceTypeEnum.TRANSLATION),
                eq("gpt-4o-mini"),
                eq(UsageUnitTypeEnum.TOKEN),
                eq(120L),
                eq(45L),
                eq(new BigDecimal("0.00000500")),
                eq(new BigDecimal("0.00001500")),
                eq(true));
    }

    /*
     * ═══ 測試三：回應殘缺要丟出格式錯誤，且用量照記 ═════════════════════
     *
     * 模型回了東西，但泰文是空的 —— 這種結果不能拿去給使用者。
     *
     * 重點在後半段：這次呼叫「確實發生過、確實被 OpenAI 收費了」，
     * 所以帳還是要記，只是標記為失敗。漏記等於帳面短少。
     */
    @Test
    @DisplayName("回應缺少泰文時應丟出格式錯誤，但用量仍要記錄")
    void shouldRejectIncompleteResponseAndStillRecordUsage() {
        givenModelReplies("""
                {
                  "thaiText": "",
                  "romanization": "",
                  "words": []
                }
                """, 80, 10);

        assertThatThrownBy(() -> openAiTranslationClient.translate("我想喝酒"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCodeEnum.TRANSLATION_RESPONSE_INVALID);

        verify(apiUsageRecorder).record(
                any(), any(), anyString(), any(),
                eq(80L), eq(10L),
                any(), any(),
                eq(false));
    }

    /*
     * ═══ 測試四：連線失敗要轉成服務不可用 ═══════════════════════════════
     *
     * 網路斷了、OpenAI 掛了，這時使用者該看到「翻譯服務暫時無法使用」，
     * 而不是一串 Java 例外。
     *
     * 這次沒有任何用量可言（根本沒接通），所以記 0 —— 但還是要留一筆，
     * 帳面上才看得出「這個時間點有呼叫失敗過」。
     */
    @Test
    @DisplayName("呼叫失敗時應轉成翻譯服務不可用，並記錄一筆失敗紀錄")
    void shouldTranslateConnectionFailureIntoServiceUnavailable() {
        given(chatModel.call(any(Prompt.class)))
                .willThrow(new RuntimeException("connection reset"));

        assertThatThrownBy(() -> openAiTranslationClient.translate("我想喝酒"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCodeEnum.TRANSLATION_SERVICE_UNAVAILABLE);

        verify(apiUsageRecorder).record(
                any(), any(), anyString(), any(),
                eq(0L), eq(0L),
                any(), any(),
                eq(false));
    }

    /*
     * ═══ 這個方法不是測試，是用來設定假模型的回應 ═══════════════════════
     *
     * 白話：「等一下有人呼叫模型時，你就回這段 JSON，
     *        並且宣稱用掉了 promptTokens 個輸入 token、completionTokens 個輸出 token。」
     */
    private void givenModelReplies(String json, int promptTokens, int completionTokens) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(promptTokens, completionTokens,
                        promptTokens + completionTokens))
                .build();

        ChatResponse chatResponse = new ChatResponse(
                java.util.List.of(new Generation(new AssistantMessage(json))), metadata);

        given(chatModel.call(any(Prompt.class))).willReturn(chatResponse);
    }
}
