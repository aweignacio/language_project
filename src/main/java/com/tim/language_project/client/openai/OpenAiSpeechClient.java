package com.tim.language_project.client.openai;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  把泰文唸出來，存成 mp3 檔，回傳檔名。
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
 * ── 第 1 步｜Service 拿到翻譯結果，接著要聲音（Task 8）──────────────────
 *
 *        Optional<String> audioFile = speechClient.synthesize("ฉันอยากดื่มเหล้า");
 *                                                              ↑ 整句泰文，不是中文
 *
 *    注意送進來的是「泰文」。要唸出來的是泰文，中文原文用不到。
 *
 * ── 第 2 步｜先擋掉空字串 ───────────────────────────────────────────────
 *
 *        if (ObjectUtils.isEmpty(thaiText)) return Optional.empty();
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
 * ── 第 4 步｜寫進硬碟 ───────────────────────────────────────────────────
 *
 *        檔名 = UUID 隨機 12 碼 + ".mp3"    例如 a3f9c2b81e47.mp3
 *        資料夾 = application.yml 的 audio.storage.directory（預設 audio/）
 *
 *        Files.createDirectories(directory);          資料夾不存在就建
 *        Files.write(directory.resolve(fileName), audioBytes);
 *
 *    ★ 為什麼檔名要隨機，不用「我想喝酒.mp3」？
 *
 *      使用者的輸入什麼都有可能 —— 空白、斜線、表情符號、超長句子，
 *      這些拿去當檔名會直接出問題（斜線在檔名裡代表資料夾）。
 *      隨機檔名一律安全，而「哪個檔名對應哪一句」記在資料庫的
 *      translation_query.audio_file 欄位裡。
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
 * ── 第 6 步｜回傳檔名給 Service ─────────────────────────────────────────
 *
 *        return Optional.of("a3f9c2b81e47.mp3");
 *
 *    Service 把這個檔名存進 translation_query.audio_file，
 *    前端就能用 <audio src="/audio/a3f9c2b81e47.mp3"> 播放它。
 *    那個網址怎麼對應到硬碟資料夾，見 WebMvcConfig。
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
import com.tim.language_project.enums.UsageUnitTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechModel;
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
 * 以 OpenAI 的語音模型把泰文轉成 mp3。
 * 任何失敗都吞下來回傳空結果，語音問題絕不影響外層的翻譯。
 */
@Slf4j
@Component
public class OpenAiSpeechClient implements SpeechClient {

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
    public Optional<String> synthesize(String thaiText) {
        if (ObjectUtils.isEmpty(thaiText)) {
            return Optional.empty();
        }

        byte[] audioBytes;

        try {
            audioBytes = textToSpeechModel.call(thaiText);
        } catch (Exception exception) {
            // 沒接通就沒有費用，記 0 只是為了留下「這時候失敗過」的痕跡。
            recordFailure(SpeechFailureReasonEnum.CONNECTION_FAILED, 0L, exception);
            return Optional.empty();
        }

        if (ObjectUtils.isEmpty(audioBytes)) {
            // 接通也回應了，只是內容是空的 —— 這一次已經被收費。
            recordFailure(SpeechFailureReasonEnum.UNKNOWN, thaiText.length(), null);
            return Optional.empty();
        }

        try {
            String fileName = newFileName();
            Path directory = Paths.get(audioStorageProperties.getDirectory());

            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName), audioBytes);

            recordUsage(thaiText.length(), true);

            return Optional.of(fileName);
        } catch (Exception exception) {
            // 聲音已經拿到了，錢也付了，是我們自己沒存下來。
            recordFailure(SpeechFailureReasonEnum.FILE_SAVE_FAILED, thaiText.length(), exception);
            return Optional.empty();
        }
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
     */
    private String newFileName() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ".mp3";
    }
}
