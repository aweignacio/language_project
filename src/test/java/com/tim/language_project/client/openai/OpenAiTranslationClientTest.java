package com.tim.language_project.client.openai;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案在測什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測 OpenAiTranslationClient —— 也就是「拿到 OpenAI 的回應之後，
 *  我們自己那段處理對不對」。
 *
 *  ⚠ 這支測試不會真的呼叫 OpenAI。不花錢、不需要金鑰、拔掉網路線也能跑。
 *
 *  下面一樣一步一步走。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你打指令到看見 Tests run: 4
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在終端機打指令 ───────────────────────────────────────────
 *
 *        .\mvnw.cmd -B test "-Dtest=OpenAiTranslationClientTest"
 *
 *    Maven 掃 src/test/java，找到名字以 Test 結尾的類別。
 *    （IntelliJ 2023.3 的綠色箭頭在這個專案跑不動 JUnit 6，只能用 Maven）
 *
 * ── 第 2 步｜看到 @ExtendWith(MockitoExtension.class) ───────────────────
 *
 *    這張貼紙的意思是「這個檔案要用 Mockito，先幫我準備好」。
 *    Mockito 是「做假物件」的工具，沒有它，下面的 @Mock 就只是空的變數。
 *
 * ── 第 3 步｜對「每一個」@Test 方法，完整重複這五步 ─────────────────────
 *
 *        ① 建立全新的測試類別實例
 *        ② 建立全新的假物件（所有 @Mock 欄位）
 *        ③ 執行 @BeforeEach setUp()
 *        ④ 執行 @Test 方法本體
 *        ⑤ Mockito 收尾檢查
 *
 *    ★ ①② 每個測試都重來一次，所以測試之間不會互相污染 ——
 *      上一個測試教假模型講的話，不會殘留到下一個。
 *      四個 @Test 就是整套跑四遍。
 *
 * ── 第 4 步｜setUp() 裡，「換掉底層」真正發生的那一行 ───────────────────
 *
 *        openAiTranslationClient = new OpenAiTranslationClient(
 *                chatModel,          ← 假的（不會連線）
 *                apiUsageRecorder,   ← 假的（不會寫資料庫）
 *                pricingProperties,  ← 真的
 *                "gpt-4o-mini");
 *
 *    ★ 這就是全部的訣竅。
 *
 *      OpenAiTranslationClient 沒有自己去 new 一個連線物件，
 *      而是「在建構子把 ChatModel 要進來」。
 *      正式環境 Spring 塞真的給它，測試時我們塞假的給它，
 *      被測的類別完全不知道差別，照樣跑它自己的邏輯。
 *
 *      這就是正式程式要寫成建構子注入的原因 ——
 *      不是為了好看，是寫成這樣才測得動。
 *
 * ── 第 5 步｜以測試二為例，@Test 方法本體做三件事 ───────────────────────
 *
 *  ● 布置場景
 *
 *        givenModelReplies("""
 *                { "thaiText": "น้ำ", "romanization": "náam", "words": [...] }
 *                """, 120, 45);
 *
 *    白話：「等一下有人呼叫模型，你就回這段 JSON，
 *           並宣稱用掉了 120 個輸入 token、45 個輸出 token。」
 *
 *    這段 JSON 是手寫的，內容照抄 OpenAI 真實回應裡 content 那一段的格式。
 *
 *  ● 執行
 *
 *        openAiTranslationClient.translate("水");
 *
 *  ● 檢查
 *
 *        verify(apiUsageRecorder).record(..., eq(120L), eq(45L), ...);
 *
 * ── 第 6 步｜第 5 步那一行 translate 按下去，內部真正跑了什麼 ───────────
 *
 *    translate("水")                          ← 真的（我們寫的）
 *      │
 *      ├→ chatClient.prompt().user("水").call()   ← 真的（Spring AI 的）
 *      │     ├─ 組 Prompt：系統提示詞 ＋ "水" ＋ 自動產生的格式規範
 *      │     ├─ 問 chatModel.getOptions()   →→ 【假的】回空選項
 *      │     └─ 呼叫 chatModel.call(prompt) →→ 【假的】回第 5 步寫死的那包
 *      │
 *      │        ★ 正式環境下，這一行會組出 JSON 用 HTTP 送去美國。
 *      │          現在它只是回傳我們剛才手寫的東西，所以連不到網路，也不花錢。
 *      │
 *      ├→ .responseEntity(TranslationPayload.class)
 *      │     └─ 真的 ChatClient 把那段 JSON 字串轉成 TranslationPayload 物件
 *      │
 *      └→ 回到我們的程式：取 usage(120/45) → 檢查殘缺 → 記帳 → 組 TranslationResult
 *
 *    ★ 只有兩個箭頭指向「假的」，其餘全部是真的程式在跑。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  assertThat 和 verify 差在哪
 * ══════════════════════════════════════════════════════════════════════════
 *
 *      assertThat  檢查「回傳值」   → 這個方法吐出來的東西對不對
 *      verify      檢查「做了什麼」 → 它有沒有去呼叫別人、用什麼參數呼叫
 *
 *  ★ 下面的測試二整段沒有一個 assertThat，回傳值直接丟掉不管。
 *
 *    因為「有沒有記對帳」不在回傳值裡。記帳是一個「副作用」——
 *    方法轉頭去呼叫了別人（apiUsageRecorder）。
 *    這種事只能用 verify 檢查：問那個假物件「剛才有人拿什麼參數來呼叫你？」
 *
 *    eq(120L) 的意思是「這個位置的參數必須剛好等於 120」。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  四個測試各自在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *    測試一  正常 JSON              防：JSON 沒被正確轉成物件、逐詞順序亂掉
 *    測試二  正常 JSON ＋ 120/45     防：有人把記帳改回「用字數猜」
 *    測試三  泰文是空字串的 JSON     防：殘缺結果被當成功回給使用者；
 *                                      或漏記一次已經付費的呼叫
 *    測試四  叫模型直接爆炸          防：使用者看到一串 Java 例外，
 *                                      而不是「翻譯服務暫時無法使用」
 *
 *    ★ 測試二的輸入故意用「水」一個字，卻搭配 120 token。
 *      如果程式改回用字數估算，這裡會拿到 1，eq(120L) 立刻失敗。
 *      這個測試存在的唯一目的，就是防止有人把它改回去用猜的。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  測得到什麼、測不到什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *    測得到：JSON 有沒有正確轉成物件、用量有沒有記對、殘缺回應有沒有丟對錯誤碼
 *    測不到：OpenAI 真的會不會照這個格式回 —— 那要填真實金鑰後手動驗證
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
                  ],
                  "translatable": true
                }
                """, 120, 45);

        TranslationResult result = openAiTranslationClient.translate("我想喝酒");

        assertThat(result.translatable()).isTrue();
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
                  "words": [{"chineseText": "水", "thaiText": "น้ำ", "romanization": "náam"}],
                  "translatable": true
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
                  "words": [],
                  "translatable": true
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
     * ═══ 測試五：模型說「這翻不出來」時，不可硬湊一個答案 ═══════════════
     *
     * 情境：使用者輸入「嘎逼」這種不存在的詞。
     *
     * 這是整個專案最重要的一道防線。語言模型的本性是「盡量給出一個像樣的答案」，
     * 它會拼一個發音接近的泰文給你，而且講得跟真的一樣。
     * 那個編造出來的詞會被永久寫進快取、沉澱進單字庫，然後被使用者背起來。
     *
     * 所以我們在提示詞裡正式跟模型要一個判斷（translatable 欄位），
     * 它說 false 時，這裡主張：
     *   - 回傳的結果標記為「翻不出來」，由 Service 決定要回什麼錯誤給使用者
     *   - 但用量要記成「成功」—— 模型確實正常回答了，那是一次有效的呼叫，
     *     只是答案是「我翻不出來」。錢照樣要付。
     */
    @Test
    @DisplayName("模型回報無法翻譯時應標記為不可翻譯，且用量記為成功")
    void shouldMarkResultAsUntranslatableWhenModelSaysSo() {
        givenModelReplies("""
                {
                  "thaiText": "",
                  "romanization": "",
                  "words": [],
                  "translatable": false
                }
                """, 95, 8);

        TranslationResult result = openAiTranslationClient.translate("嘎逼");

        // 我主張：沒有丟例外，而是回一個標記為「翻不出來」的結果
        assertThat(result.translatable()).isFalse();
        assertThat(result.thaiText()).isNull();
        assertThat(result.words()).isEmpty();

        // 我主張：這次呼叫算成功（模型正常回答了），用量照實記
        verify(apiUsageRecorder).record(
                any(), any(), anyString(), any(),
                eq(95L), eq(8L),
                any(), any(),
                eq(true));
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
