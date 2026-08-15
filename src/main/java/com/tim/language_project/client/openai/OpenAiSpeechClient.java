package com.tim.language_project.client.openai;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  把一段文字唸出來，存成 mp3 檔，回傳它相對於 audio 資料夾的路徑。
 *
 *  ★ 2026-08-14 起，唸的不一定是泰文了。
 *
 *    以前只唸泰文（使用者要學的是泰文發音），現在中文也會唸 ——
 *    逐詞對照的每一列都有中泰兩個播放鍵。所以參數從 thaiText 改名成
 *    speechText，並多收一個「這是什麼語言」。
 *
 *    語言決定檔案存去哪個子資料夾，也決定回傳值長什麼樣：
 *
 *        synthesize("เหล้า", TH)  →  Optional("th/a3f9c2b81e47.mp3")
 *        synthesize("酒",    ZH)  →  Optional("zh/b7e1d4a95c22.mp3")
 *
 *  ★ 最重要的一條原則：這裡失敗，絕對不能讓翻譯跟著失敗。
 *
 *    使用者要的是「我想喝酒的泰文怎麼寫、怎麼唸」。
 *    翻譯已經拿到了，只是聲音沒生出來 —— 那就把泰文照樣顯示給他，
 *    單純不顯示播放鍵。為了聲音失敗而讓整個查詢報錯，是本末倒置。
 *
 *    所以這個檔案的每一條失敗路徑都是「回傳空的 Optional」，不丟例外。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：接續「我想喝酒」翻譯成功之後
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜AudioAssetService 決定「這段文字沒合成過，去合成吧」──────────
 *
 *        Optional<String> filePath =
 *                speechClient.synthesize("ฉันอยากดื่มเหล้า", SpeechLanguageEnum.TH);
 *
 *    ★ 呼叫的一定是 AudioAssetService，不會有別人。
 *      那一層負責「先查資料庫，沒有才花錢」；這一層只負責「真的去花錢」，
 *      進到這個方法就代表錢一定會付出去。繞過那一層直接呼叫這裡，
 *      等於把省錢機制整個跳過。
 *
 * ── 第 2 步｜先擋掉空字串 ───────────────────────────────────────────────
 *
 *        if (ObjectUtils.isEmpty(speechText)) return Optional.empty();
 *
 *    空字串送去 OpenAI 只會浪費一次呼叫。
 *
 * ── 第 3 步｜呼叫 OpenAI，拿回一串位元組 ────────────────────────────────
 *
 *        byte[] audioBytes = textToSpeechModel.call(thaiText);
 *
 *    ★ byte[] 是什麼？
 *
 *      就是「一個檔案的內容本身」。mp3 不是文字，沒辦法用 String 裝，
 *      所以用一串數字（位元組）表示。這時它還只在記憶體裡，
 *      要自己寫進硬碟才會變成一個檔案。
 *
 *    這一行做的事跟翻譯那邊一樣：組請求、附上金鑰、用 HTTP 送去 OpenAI，
 *    只是回來的不是 JSON 文字而是音訊資料。這些都由 Spring AI 處理，
 *    textToSpeechModel 這個 Bean 是 spring-ai-starter-model-openai 自動生的。
 *
 * ── 第 4 步｜寫進硬碟，依語言分資料夾 ───────────────────────────────────
 *
 *        檔名   = UUID 隨機 12 碼 + ".mp3"     例如 a3f9c2b81e47.mp3
 *        資料夾 = audio.storage.directory（預設 audio/）＋ 語言的子資料夾
 *
 *        泰文 → audio\th\a3f9c2b81e47.mp3    回傳 "th/a3f9c2b81e47.mp3"
 *        中文 → audio\zh\b7e1d4a95c22.mp3    回傳 "zh/b7e1d4a95c22.mp3"
 *
 *    ★ 為什麼要分資料夾？
 *
 *      檔名是隨機亂碼，中泰音檔混在一起的話，日後要清理或搬移
 *      完全分不出哪個是哪個。分了之後光看路徑就知道。
 *
 *    ★ 為什麼檔名要隨機，不用「我想喝酒.mp3」？
 *
 *      使用者的輸入什麼都有可能 —— 空白、斜線、表情符號、超長句子，
 *      這些拿去當檔名會直接出問題（斜線在檔名裡代表資料夾）。
 *      隨機檔名一律安全，而「哪個檔案對應哪段文字」記在資料庫的
 *      audio_asset 那張表裡。
 *
 * ── 第 5 步｜記帳 ───────────────────────────────────────────────────────
 *
 *        用量 = 泰文的字元數（不是 token！）
 *
 *    ★ 語音跟翻譯的計價單位不一樣：
 *
 *        翻譯  按 token 算  → UsageUnitTypeEnum.TOKEN
 *        語音  按字元算     → UsageUnitTypeEnum.CHARACTER
 *
 *      而且語音沒有「輸出用量」的概念（你給多少字、它就唸多少），
 *      所以輸出用量固定傳 0、輸出單價傳 0。
 *
 * ── 第 6 步｜回傳路徑給 AudioAssetService ───────────────────────────────
 *
 *        return Optional.of("th/a3f9c2b81e47.mp3");
 *
 *    ★ 回傳的是「路徑」不是「檔名」—— 前面多了資料夾。
 *      2026-08-14 以前只回檔名，接手改這裡時要注意這個差別。
 *
 *    AudioAssetService 會把這個路徑寫進 audio_asset，
 *    前端就能用 <audio src="/audio/th/a3f9c2b81e47.mp3"> 播放它。
 *    那個網址怎麼對應到硬碟資料夾，見 WebMvcConfig
 *    （它已支援子路徑，不需要為了這次改版異動）。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  三條失敗路徑，以及各自要不要記帳
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  判斷標準只有一個：★ 這次 OpenAI 到底有沒有真的替我們做事、有沒有收錢 ★
 *
 *    CONNECTION_FAILED  連線就失敗、逾時、金鑰錯 → 根本沒接通
 *                       → 記 0 用量。記了才知道「這個時間點有呼叫失敗過」，
 *                         但不能記字元數，否則帳面會多出一筆沒發生的費用。
 *
 *    UNKNOWN            接通了、也回應了，但回來的是空的
 *                       → 記字元數。它已經處理過我們的請求了。
 *
 *    FILE_SAVE_FAILED   聲音成功拿到了，是我們自己寫檔失敗（硬碟滿了、沒權限）
 *                       → 記字元數。這筆錢確實付了，只是我們自己沒接住。
 *
 *  三種都回傳 Optional.empty()，使用者只會發現「這次沒有播放鍵」。
 *  失敗原因用 SpeechFailureReasonEnum 寫進日誌，那個 enum 只給我們看，
 *  不會外流到前端 —— 使用者不需要知道是硬碟滿了還是金鑰過期。
 *
 *  測試檔：src/test/java/com/tim/language_project/client/openai/
 *          OpenAiSpeechClientTest.java
 */

import com.tim.language_project.client.SpeechClient;
import com.tim.language_project.client.usage.ApiUsageRecorder;
import com.tim.language_project.config.AiPricingProperties;
import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.enums.AiProviderEnum;
import com.tim.language_project.enums.AiServiceTypeEnum;
import com.tim.language_project.enums.SpeechFailureReasonEnum;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.enums.UsageUnitTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

/**
 * 以 OpenAI 的語音模型把一段文字轉成 mp3，依語言存進 audio 底下的子資料夾。
 * 任何失敗都吞下來回傳空結果，語音問題絕不影響外層的翻譯。
 */
/*
 * ★ 2026-08-15 起這個實作預設「不會」被建立出來。
 *
 *   泰語發音改用 Google（見 GoogleSpeechClient 的說明：OpenAI 的聲音
 *   全是英文訓練的，唸泰文會有英文腔）。這份保留著當備援 ——
 *   Google 出問題時，把 speech.provider 改成 OPENAI 就能立刻切回來。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "speech.provider", havingValue = "OPENAI")
public class OpenAiSpeechClient implements SpeechClient {

    /** 已經算是「講完一句話」的結尾字元，遇到這些就不再補句點。 */
    private static final String SENTENCE_ENDINGS = ".!?。！？…～~";

    private final TextToSpeechModel textToSpeechModel;

    private final ApiUsageRecorder apiUsageRecorder;

    private final AiPricingProperties pricingProperties;

    private final AudioStorageProperties audioStorageProperties;

    private final String modelName;

    public OpenAiSpeechClient(TextToSpeechModel textToSpeechModel,
                              ApiUsageRecorder apiUsageRecorder,
                              AiPricingProperties pricingProperties,
                              AudioStorageProperties audioStorageProperties,
                              @Value("${spring.ai.openai.audio.speech.model:gpt-4o-mini-tts}")
                              String modelName) {
        this.textToSpeechModel = textToSpeechModel;
        this.apiUsageRecorder = apiUsageRecorder;
        this.pricingProperties = pricingProperties;
        this.audioStorageProperties = audioStorageProperties;
        this.modelName = modelName;
    }

    @Override
    public Optional<String> synthesize(String speechText, SpeechLanguageEnum language) {
        if (ObjectUtils.isEmpty(speechText)) {
            return Optional.empty();
        }

        // ★ 送出去的是補過句點的版本，資料庫仍然以乾淨的原文當鍵。理由見★三。
        String spokenText = withTrailingStop(speechText);

        byte[] audioBytes;

        try {
            audioBytes = textToSpeechModel.call(spokenText);
        } catch (Exception exception) {
            // 沒接通就沒有費用，記 0 只是為了留下「這時候失敗過」的痕跡。
            recordFailure(SpeechFailureReasonEnum.CONNECTION_FAILED, 0L, exception);
            return Optional.empty();
        }

        if (ObjectUtils.isEmpty(audioBytes)) {
            // 接通也回應了，只是內容是空的 —— 這一次已經被收費。
            recordFailure(SpeechFailureReasonEnum.UNKNOWN, spokenText.length(), null);
            return Optional.empty();
        }

        try {
            // 相對路徑，例如 th/a1b2c3d4e5f6.mp3。
            // 前端把它接在 /audio/ 後面就是可以直接播放的網址。
            String filePath = language.getFolderName() + "/" + newFileName();
            Path directory = Paths.get(audioStorageProperties.getDirectory())
                    .resolve(language.getFolderName());

            Files.createDirectories(directory);
            Files.write(Paths.get(audioStorageProperties.getDirectory()).resolve(filePath),
                    audioBytes);

            recordUsage(spokenText.length(), true);

            return Optional.of(filePath);
        } catch (Exception exception) {
            // 聲音已經拿到了，錢也付了，是我們自己沒存下來。
            recordFailure(SpeechFailureReasonEnum.FILE_SAVE_FAILED,
                    spokenText.length(), exception);
            return Optional.empty();
        }
    }

    /**
     * 在尾端補一個句點再送去合成。
     *
     * ★ 為什麼要補：tts-1 會把「沒有結尾標點的短句」的最後一個音節吃掉。
     *
     *   2026-08-14 實測「ขับรถ」（khàp rót，兩個音節）：
     *
     *       送 "ขับรถ"    → 0.50 秒，聲音只佔前 125ms，後面全是靜音，
     *                       實際只唸得出 khàp
     *       送 "ขับรถ."   → 0.53 秒，兩段聲音，khàp rót 都在
     *
     *   有結尾標點時模型才把它當成「一句講完的話」。尾端加空白沒有用，
     *   那會被吃掉。
     *
     * ★ 補的只有「送出去的那一份」。資料庫與快取仍然以乾淨的原文當鍵 ——
     *   把句點也存進去的話，下次拿原文查就會查不到，同一個詞每次都重新合成。
     *
     * ★ 用量記的是「補過之後的長度」，因為 OpenAI 是按實際送出的字元收費。
     */
    private String withTrailingStop(String speechText) {
        String trimmed = speechText.strip();

        if (SENTENCE_ENDINGS.indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0) {
            return trimmed;
        }

        return trimmed + ".";
    }

    /**
     * 記錄失敗。失敗原因只寫日誌不外拋，使用者只會發現這次沒有播放鍵。
     */
    private void recordFailure(SpeechFailureReasonEnum reason, long characterCount, Exception exception) {
        log.error("speech synthesis failed, reason={} ({})",
                reason.name(), reason.getDescription(), exception);

        recordUsage(characterCount, false);
    }

    /**
     * 語音以字元計價，沒有輸出用量的概念，所以輸出量與輸出單價固定為 0。
     */
    private void recordUsage(long characterCount, boolean success) {
        apiUsageRecorder.record(
                AiProviderEnum.OPENAI,
                AiServiceTypeEnum.SPEECH,
                modelName,
                UsageUnitTypeEnum.CHARACTER,
                characterCount,
                0L,
                pricingProperties.getSpeechPrice(),
                BigDecimal.ZERO,
                success);
    }

    /**
     * 隨機檔名，避免使用者輸入的內容（空白、斜線、表情符號）跑進檔名。
     * 不含資料夾 —— 要放進 th/ 還是 zh/ 由 language 決定。
     */
    private String newFileName() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ".mp3";
    }
}
