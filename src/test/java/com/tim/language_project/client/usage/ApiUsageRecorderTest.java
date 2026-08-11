package com.tim.language_project.client.usage;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案在測什麼？
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測 ApiUsageRecorder ——「每次呼叫 OpenAI 之後，記下來的費用對不對」。
 *
 *  這是專案裡唯一算錢的地方，算錯會直接反映在你對帳的數字上，
 *  而且錯了不會有任何錯誤訊息，只會靜靜地存進一個錯的數字。
 *  所以這裡值得測。
 *
 * ── 跟前面兩種測試的差別 ──────────────────────────────────────────────
 *
 *      @DataJpaTest  → 啟動 Spring + 連真的資料庫（慢，但驗得到 NVARCHAR）
 *      @WebMvcTest   → 啟動 Spring 的網頁那一塊（中等）
 *      這個檔案      → 完全不啟動 Spring（最快，毫秒等級）
 *
 *  差別在於「要不要真的存進資料庫」。
 *  這裡要驗的是「算式對不對」，不是「存不存得進去」，
 *  所以資料庫用假的就好，這樣測試跑起來不用等，也不用開 Docker。
 */

import com.tim.language_project.entity.ApiUsageLog;
import com.tim.language_project.enums.AiProviderEnum;
import com.tim.language_project.enums.AiServiceTypeEnum;
import com.tim.language_project.enums.UsageUnitTypeEnum;
import com.tim.language_project.repository.ApiUsageLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/*
 * ── 貼紙：@ExtendWith(MockitoExtension.class) ────────────────────────────
 *
 *  跟 JUnit 說：「這個檔案要用 Mockito，麻煩你先把它準備好。」
 *  Mockito 是「做假物件」的工具，下面兩張貼紙才會生效。
 */
@ExtendWith(MockitoExtension.class)
class ApiUsageRecorderTest {

    /*
     * @Mock = 「做一個假的 ApiUsageLogRepository 給我。」
     *
     * 這個假貨長得跟真的一模一樣（方法都在），但裡面完全是空的：
     * 呼叫 save() 不會連資料庫、不會存任何東西，預設就回傳 null。
     *
     * 好處是，我們可以事後問它：「剛才有人拿什麼東西來叫你存？」
     * ——那個「什麼東西」就是我們要檢查的算錢結果。
     */
    @Mock
    private ApiUsageLogRepository apiUsageLogRepository;

    /*
     * @InjectMocks = 「做一個真的 ApiUsageRecorder，
     *                  但把它需要的 Repository 換成上面那個假貨。」
     *
     * 被測的是真的 ApiUsageRecorder，執行的是它真正的程式碼，
     * 只有「存資料庫」這一步被換掉了。
     */
    @InjectMocks
    private ApiUsageRecorder apiUsageRecorder;

    /*
     * ═══ 測試一：費用要等於 輸入單價×輸入量 ＋ 輸出單價×輸出量 ═══════════
     *
     * 用的數字是刻意挑的，方便你心算對照：
     *
     *     輸入：0.000005 × 1200 = 0.006
     *     輸出：0.000015 ×  300 = 0.0045
     *     ─────────────────────────────
     *     合計                  = 0.0105 美金
     */
    @Test
    @DisplayName("費用應等於輸入與輸出各自的單價乘以用量後相加")
    void shouldComputeCostFromUnitPricesAndUnits() {
        // ── 第一段：執行要測的動作 ──
        apiUsageRecorder.record(
                AiProviderEnum.OPENAI,
                AiServiceTypeEnum.TRANSLATION,
                "gpt-4o-mini",
                UsageUnitTypeEnum.TOKEN,
                1200L,
                300L,
                new BigDecimal("0.00000500"),
                new BigDecimal("0.00001500"),
                true);

        /*
         * ── 第二段：把「被拿去存的那個物件」攔下來 ──
         *
         * ArgumentCaptor 是「攔截器」：
         * 它會記住剛才有人呼叫 save() 時，塞進去的參數是什麼。
         *
         * 因為 Repository 是假的，資料沒有真的進資料庫，
         * 所以只能用這個方式檢查「本來要存進去的內容」。
         */
        ArgumentCaptor<ApiUsageLog> savedLog = ArgumentCaptor.forClass(ApiUsageLog.class);
        org.mockito.Mockito.verify(apiUsageLogRepository).save(savedLog.capture());

        ApiUsageLog usageLog = savedLog.getValue();

        /*
         * ── 第三段：檢查結果 ──
         *
         * 為什麼用 isEqualByComparingTo 而不是 isEqualTo？
         *
         *   BigDecimal 的 equals 連「小數位數」都要一樣才算相等：
         *       0.0105 和 0.01050 → equals 是 false（位數不同）
         *                        → compareTo 是 0（數值相同）
         *
         *   我們在意的是金額本身，不是它被記成幾位小數，
         *   所以要用 compareTo 這一系列的方法。
         *   用錯會得到一個看起來莫名其妙的失敗訊息。
         */
        assertThat(usageLog.getCostAmount()).isEqualByComparingTo("0.0105");

        // 單價也要原封不動存下來，日後對帳才能重算驗證。
        assertThat(usageLog.getInputUnitPrice()).isEqualByComparingTo("0.00000500");
        assertThat(usageLog.getOutputUnitPrice()).isEqualByComparingTo("0.00001500");

        // 其餘欄位照傳入的值存好，沒有漏掉或放錯位置。
        assertThat(usageLog.getProvider()).isEqualTo(AiProviderEnum.OPENAI);
        assertThat(usageLog.getServiceType()).isEqualTo(AiServiceTypeEnum.TRANSLATION);
        assertThat(usageLog.getUnitType()).isEqualTo(UsageUnitTypeEnum.TOKEN);
        assertThat(usageLog.getModelName()).isEqualTo("gpt-4o-mini");
        assertThat(usageLog.getInputUnits()).isEqualTo(1200L);
        assertThat(usageLog.getOutputUnits()).isEqualTo(300L);
        assertThat(usageLog.getSuccess()).isTrue();
    }

    /*
     * ═══ 測試二：語音合成沒有輸出用量，費用只算輸入那一邊 ═══════════════
     *
     * 語音是「給多少字元、收多少錢」，沒有「輸出」這個概念，
     * 所以輸出用量傳 0。這時費用不可以因為乘到 0 而算錯或爆掉。
     *
     *     0.000015 × 500 = 0.0075
     */
    @Test
    @DisplayName("輸出用量為 0 時，費用只計算輸入部分")
    void shouldComputeCostWithoutOutputUnits() {
        apiUsageRecorder.record(
                AiProviderEnum.OPENAI,
                AiServiceTypeEnum.SPEECH,
                "gpt-4o-mini-tts",
                UsageUnitTypeEnum.CHARACTER,
                500L,
                0L,
                new BigDecimal("0.00001500"),
                BigDecimal.ZERO,
                true);

        ArgumentCaptor<ApiUsageLog> savedLog = ArgumentCaptor.forClass(ApiUsageLog.class);
        org.mockito.Mockito.verify(apiUsageLogRepository).save(savedLog.capture());

        assertThat(savedLog.getValue().getCostAmount()).isEqualByComparingTo("0.0075");
    }

    /*
     * ═══ 測試三：記帳失敗不可以拖垮主流程 ═══════════════════════════════
     *
     * 情境：翻譯已經成功了，正要記錄這次花了多少錢，結果資料庫掛掉。
     *
     * 這時候「使用者該不該看到錯誤」？不該。
     * 翻譯結果已經拿到了，記帳只是我們自己要看的帳，
     * 為了記不成帳而讓使用者的查詢失敗，是本末倒置。
     *
     * 所以這支測試主張：就算 save 爆炸，record 也不可以把例外往外丟。
     *
     * ⚠ 這支測試只驗得到 ApiUsageRecorder 裡的 try/catch 有沒有作用。
     *   它沒有啟動 Spring，所以 @Transactional(REQUIRES_NEW) 那張貼紙
     *   在這個測試裡是完全沒有生效的，交易行為驗不到。
     */
    @Test
    @DisplayName("寫入用量紀錄失敗時不可將例外往外拋")
    void shouldNotPropagateExceptionWhenSaveFails() {
        // 叫假的 Repository：「等一下有人呼叫你的 save，你就給我爆炸。」
        given(apiUsageLogRepository.save(any(ApiUsageLog.class)))
                .willThrow(new RuntimeException("資料庫連線中斷"));

        // 我主張：即使如此，這一段執行完不會有任何例外冒出來。
        assertThatCode(() -> apiUsageRecorder.record(
                AiProviderEnum.OPENAI,
                AiServiceTypeEnum.TRANSLATION,
                "gpt-4o-mini",
                UsageUnitTypeEnum.TOKEN,
                100L,
                50L,
                new BigDecimal("0.00000500"),
                new BigDecimal("0.00001500"),
                false))
                .doesNotThrowAnyException();
    }
}
