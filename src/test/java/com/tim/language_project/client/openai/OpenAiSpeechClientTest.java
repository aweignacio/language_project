package com.tim.language_project.client.openai;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案在測什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測 OpenAiSpeechClient —— 「拿到聲音之後，我們自己那段處理對不對」。
 *
 *  ⚠ 不會真的呼叫 OpenAI，不花錢、不需要金鑰、離線也能跑。
 *  ⚠ 也不會寫到你專案的 audio/ 資料夾（用的是臨時資料夾，測完自動刪）。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你打指令到看見 Tests run: 5
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在終端機打指令 ───────────────────────────────────────────
 *
 *        .\mvnw.cmd -B test "-Dtest=OpenAiSpeechClientTest"
 *
 * ── 第 2 步｜@TempDir 幫每個測試準備一個空資料夾 ────────────────────────
 *
 *        @TempDir Path tempDirectory;
 *
 *    JUnit 會在測試開始前生一個臨時資料夾（例如 C:\Users\...\Temp\junit123\），
 *    測完自動整個刪掉。
 *
 *    ★ 為什麼不直接用專案的 audio/ 資料夾？
 *
 *      那樣每跑一次測試就會在你的專案裡留下垃圾 mp3，
 *      而且測試會受「上次跑剩下的檔案」影響。臨時資料夾每次都是全新的空的。
 *
 * ── 第 3 步｜setUp() 把兩個假的塞進去 ───────────────────────────────────
 *
 *        new OpenAiSpeechClient(
 *                textToSpeechModel,      ← 假的（不會連 OpenAI）
 *                apiUsageRecorder,       ← 假的（不會寫資料庫）
 *                pricingProperties,      ← 真的
 *                audioStorageProperties, ← 真的，但資料夾指向臨時資料夾
 *                "gpt-4o-mini-tts");
 *
 * ── 第 4 步｜以測試一為例 ───────────────────────────────────────────────
 *
 *  ● 布置：叫假模型回三個位元組（假裝那是一個 mp3 的內容）
 *
 *        given(textToSpeechModel.call("ฉันอยากดื่มเหล้า"))
 *                .willReturn(new byte[]{1, 2, 3});
 *
 *    真實環境回來的是幾十 KB 的音訊資料，但我們要測的是
 *    「有沒有把拿到的東西正確寫成檔案」，內容是什麼不重要，三個位元組就夠。
 *
 *  ● 執行
 *
 *        Optional<String> fileName = openAiSpeechClient.synthesize("ฉันอยากดื่มเหล้า");
 *
 *  ● 檢查
 *
 *        檔名有回傳、以 .mp3 結尾
 *        臨時資料夾裡真的多出那個檔案
 *        檔案內容就是那三個位元組
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  ★ 2026-08-14 起：音檔分資料夾存，回傳值也跟著變
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  synthesize 多了第二個參數「這段文字是什麼語言」，回傳值也從「檔名」
 *  變成「相對於 audio 資料夾的路徑」：
 *
 *        以前  synthesize("เหล้า")       →  Optional("a3f9c2b81e47.mp3")
 *        現在  synthesize("เหล้า", TH)   →  Optional("th/a3f9c2b81e47.mp3")
 *                                                    ↑ 多了資料夾
 *
 *  為什麼要分資料夾：這個系統現在中文泰文都會唸。兩種音檔混在同一個資料夾、
 *  檔名又都是隨機亂碼，日後要清理或搬移完全分不出哪個是哪個。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  七個測試各自在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *    測試一  正常              防：檔案沒寫出來、或寫出來的內容不對
 *
 *    測試二  空字串輸入        防：空字串也送去 OpenAI，白花一次錢
 *
 *    測試三  連線爆炸          防：★語音失敗把整個翻譯一起拖垮★
 *                                並主張「沒接通就記 0 用量」——
 *                                記字元數會讓帳面多出一筆沒發生的費用
 *
 *    測試四  回傳空的音訊      防：把空檔案寫進硬碟當成成功
 *                                並主張「接通了就要照實記字元數」
 *
 *    測試五  寫檔失敗          防：漏記一次已經付費的呼叫
 *                                （做法是把資料夾路徑指向一個「已存在的檔案」，
 *                                  這樣 createDirectories 一定會失敗）
 *
 *    ★ 測試三、四、五合起來守的是同一條規則：
 *
 *        OpenAI 有沒有真的替我們做事 → 有就記字元數，沒有就記 0
 *
 *      這條規則寫錯不會有任何錯誤訊息，只會讓帳對不起來。
 *
 *    測試六  泰文的資料夾      防：泰文音檔沒進 th/，或回傳值漏掉資料夾
 *    測試七  中文的資料夾      防：中文音檔沒進 zh/
 */

import com.tim.language_project.client.usage.ApiUsageRecorder;
import com.tim.language_project.config.AiPricingProperties;
import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.enums.AiProviderEnum;
import com.tim.language_project.enums.AiServiceTypeEnum;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.enums.UsageUnitTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.audio.tts.TextToSpeechModel;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 這張貼紙的作用見開頭第 3 步。
@ExtendWith(MockitoExtension.class)
class OpenAiSpeechClientTest {

    private static final String THAI_TEXT = "ฉันอยากดื่มเหล้า";

    /** 真正會連上 OpenAI 的那一層，整支測試靠換掉它來斷網。 */
    @Mock
    private TextToSpeechModel textToSpeechModel;

    @Mock
    private ApiUsageRecorder apiUsageRecorder;

    /** JUnit 自動準備的臨時資料夾，測完自動刪。見開頭第 2 步。 */
    @TempDir
    private Path tempDirectory;

    private OpenAiSpeechClient openAiSpeechClient;

    @BeforeEach
    void setUp() {
        AiPricingProperties pricingProperties = new AiPricingProperties();
        pricingProperties.setSpeechPrice(new BigDecimal("0.00001500"));

        AudioStorageProperties audioStorageProperties = new AudioStorageProperties();
        audioStorageProperties.setDirectory(tempDirectory.toString());

        openAiSpeechClient = new OpenAiSpeechClient(
                textToSpeechModel, apiUsageRecorder,
                pricingProperties, audioStorageProperties, "gpt-4o-mini-tts");
    }

    /*
     * ═══ 測試一：正常情況，檔案要真的出現在硬碟上 ═══════════════════════
     */
    @Test
    @DisplayName("語音成功時應寫出 mp3 檔並回傳檔名")
    void shouldWriteAudioFileAndReturnFileName() throws IOException {
        byte[] audioBytes = {1, 2, 3};
        given(textToSpeechModel.call(THAI_TEXT)).willReturn(audioBytes);

        Optional<String> filePath =
                openAiSpeechClient.synthesize(THAI_TEXT, SpeechLanguageEnum.TH);

        // 我主張：有回傳路徑，而且是 mp3
        assertThat(filePath).isPresent();
        assertThat(filePath.get()).endsWith(".mp3");

        // 我主張：那個檔案真的存在，而且內容就是拿到的位元組
        Path writtenFile = tempDirectory.resolve(filePath.get());
        assertThat(writtenFile).exists();
        assertThat(Files.readAllBytes(writtenFile)).isEqualTo(audioBytes);
    }

    /*
     * ═══ 測試二：空字串不該花錢 ═════════════════════════════════════════
     */
    @Test
    @DisplayName("輸入為空時不應呼叫語音服務")
    void shouldNotCallServiceForEmptyText() {
        Optional<String> filePath =
                openAiSpeechClient.synthesize("", SpeechLanguageEnum.TH);

        assertThat(filePath).isEmpty();

        // 我主張：完全沒有去呼叫模型，也沒有記任何一筆帳
        verify(textToSpeechModel, never()).call(anyString());
        verify(apiUsageRecorder, never()).record(
                any(), any(), anyString(), any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    /*
     * ═══ 測試三：連線失敗 —— 不可拖垮翻譯，且用量記 0 ═══════════════════
     */
    @Test
    @DisplayName("連線失敗時應回傳空結果，且用量記 0")
    void shouldReturnEmptyAndRecordZeroUsageWhenCallFails() {
        given(textToSpeechModel.call(THAI_TEXT))
                .willThrow(new RuntimeException("connection reset"));

        Optional<String> filePath =
                openAiSpeechClient.synthesize(THAI_TEXT, SpeechLanguageEnum.TH);

        // 我主張：沒有丟出例外（丟了的話這一行根本執行不到），只是回傳空的
        assertThat(filePath).isEmpty();

        // 我主張：記了一筆失敗紀錄，但用量是 0 —— 根本沒接通，不該有費用
        verify(apiUsageRecorder).record(
                eq(AiProviderEnum.OPENAI),
                eq(AiServiceTypeEnum.SPEECH),
                eq("gpt-4o-mini-tts"),
                eq(UsageUnitTypeEnum.CHARACTER),
                eq(0L),
                eq(0L),
                any(), any(),
                eq(false));
    }

    /*
     * ═══ 測試四：回傳空音訊 —— 已經被收費了，用量照實記 ═════════════════
     */
    @Test
    @DisplayName("回傳空音訊時應記錄實際字元數，因為該次呼叫已被收費")
    void shouldRecordCharacterCountWhenAudioIsEmpty() {
        given(textToSpeechModel.call(THAI_TEXT)).willReturn(new byte[0]);

        Optional<String> filePath =
                openAiSpeechClient.synthesize(THAI_TEXT, SpeechLanguageEnum.TH);

        assertThat(filePath).isEmpty();

        verify(apiUsageRecorder).record(
                any(), any(), anyString(), any(),
                eq((long) THAI_TEXT.length()),
                eq(0L),
                any(), any(),
                eq(false));
    }

    /*
     * ═══ 測試五：寫檔失敗 —— 錢已經付了，不可漏記 ══════════════════════
     *
     * 製造失敗的方法：把「資料夾」指向一個已經存在的「檔案」。
     * 這樣 Files.createDirectories 會失敗（同名的檔案已存在，建不出資料夾）。
     */
    @Test
    @DisplayName("寫檔失敗時應回傳空結果，但用量仍要記錄")
    void shouldRecordUsageWhenFileSaveFails() throws IOException {
        Path blockingFile = tempDirectory.resolve("blocked");
        Files.createFile(blockingFile);

        AiPricingProperties pricingProperties = new AiPricingProperties();
        pricingProperties.setSpeechPrice(new BigDecimal("0.00001500"));

        AudioStorageProperties audioStorageProperties = new AudioStorageProperties();
        audioStorageProperties.setDirectory(blockingFile.toString());

        OpenAiSpeechClient blockedClient = new OpenAiSpeechClient(
                textToSpeechModel, apiUsageRecorder,
                pricingProperties, audioStorageProperties, "gpt-4o-mini-tts");

        given(textToSpeechModel.call(THAI_TEXT)).willReturn(new byte[]{1, 2, 3});

        Optional<String> filePath =
                blockedClient.synthesize(THAI_TEXT, SpeechLanguageEnum.TH);

        assertThat(filePath).isEmpty();

        // 我主張：聲音已經拿到、錢已經付了，字元數要照實記，只是標記失敗
        verify(apiUsageRecorder).record(
                any(), any(), anyString(), any(),
                eq((long) THAI_TEXT.length()),
                eq(0L),
                any(), any(),
                eq(false));
    }

    /*
     * ═══ 測試六：泰文要存進 th/ 子資料夾 ═══════════════════════════════
     *
     * 為什麼要分資料夾：中文音檔和泰文音檔混在同一個資料夾裡，
     * 檔名又都是隨機亂碼，日後要清理或搬移完全分不出哪個是哪個。
     */
    @Test
    @DisplayName("泰文音檔應存入 th 子資料夾，回傳路徑含資料夾")
    void shouldStoreThaiAudioInThaiFolder() {
        given(textToSpeechModel.call("เหล้า")).willReturn(new byte[]{1, 2, 3});

        Optional<String> filePath =
                openAiSpeechClient.synthesize("เหล้า", SpeechLanguageEnum.TH);

        assertThat(filePath).isPresent();
        assertThat(filePath.get()).startsWith("th/");
        assertThat(filePath.get()).endsWith(".mp3");
        assertThat(tempDirectory.resolve(filePath.get())).exists();
    }

    /*
     * ═══ 測試七：中文要存進 zh/ 子資料夾 ═══════════════════════════════
     */
    @Test
    @DisplayName("中文音檔應存入 zh 子資料夾")
    void shouldStoreChineseAudioInChineseFolder() {
        given(textToSpeechModel.call("酒")).willReturn(new byte[]{1, 2, 3});

        Optional<String> filePath =
                openAiSpeechClient.synthesize("酒", SpeechLanguageEnum.ZH);

        assertThat(filePath).isPresent();
        assertThat(filePath.get()).startsWith("zh/");
        assertThat(tempDirectory.resolve(filePath.get())).exists();
    }
}
