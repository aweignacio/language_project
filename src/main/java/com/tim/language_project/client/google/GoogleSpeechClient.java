package com.tim.language_project.client.google;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  用 Google 的語音服務把文字唸出來，存成音檔。
 *
 *  ★ 為什麼要多這一家，OpenAI 不是已經會唸了嗎？
 *
 *    因為 OpenAI 的聲音「全部都是拿英文訓練的」，它沒有泰語的發音模型。
 *    丟泰文給它，它是用英文的嘴去唸泰文字母 ——
 *    ครับ 唸出來會像英文的 "krab"，而泰國人不是那樣唸的。
 *
 *    Google 有 th-TH 的原生泰語聲音（男聲全部是 Chirp3-HD 系列），
 *    那是拿泰國人的聲音做出來的，腔調才對。
 *
 *    翻譯仍然走 OpenAI 的 gpt-5.5，只有「唸出來」這件事換到 Google。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從一段泰文到一個可以播的檔案
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜AudioAssetService 決定「這段文字沒合成過，去合成吧」──────────
 *
 *        googleSpeechClient.synthesize("ขับรถ", SpeechLanguageEnum.TH);
 *
 *    ★ 呼叫的一定是 AudioAssetService，不會有別人。
 *      那一層負責「先查資料庫，沒有才花錢」；進到這裡就代表錢一定會付出去。
 *
 * ── 第 2 步｜組請求送去 Google ──────────────────────────────────────────
 *
 *        POST https://texttospeech.googleapis.com/v1/text:synthesize?key=金鑰
 *
 *        {
 *          "input":       { "text": "ขับรถ" },
 *          "voice":       { "languageCode": "th-TH",
 *                           "name": "th-TH-Chirp3-HD-Charon" },
 *          "audioConfig": { "audioEncoding": "LINEAR16" }
 *        }
 *
 *    ★ 為什麼要 LINEAR16（也就是 WAV）而不是 mp3？
 *
 *      因為下一步要剪靜音、調音量，而那需要看得到「每一瞬間的振幅」。
 *      mp3 是壓縮過的，Java 沒有內建解碼器，拿到手只是一堆看不懂的位元組。
 *      WAV 沒有壓縮，裡面就是一連串數字，處理起來只是簡單的數學。
 *
 * ── 第 3 步｜Google 回來的是 base64 字串，不是檔案本身 ──────────────────
 *
 *        { "audioContent": "UklGRiQAAABXQVZFZm10IBAAAAABAAEA..." }
 *
 *    base64 是「把二進位資料用純文字表示」的編碼方式 ——
 *    因為 JSON 裡面塞不進原始的二進位。解回來才是真正的 WAV。
 *
 * ── 第 4 步｜★ 整理音檔（這一步是這個檔案存在的關鍵）★ ──────────────────
 *
 *        WavAudio.tidy(wavBytes)
 *
 *    Chirp3-HD 是生成式的，每次回來的前後留白都不一樣。實測同一個聲音、
 *    同一個詞「ขับรถ」，一次前置靜音 0.82 秒、另一次 0.32 秒 ——
 *    不剪的話你點下去要等快一秒才出聲，而且每次等的長度還不同。
 *
 *    順便把音量正規化，並擋掉「整段都是靜音」的壞檔案。細節見 WavAudio。
 *
 * ── 第 5 步｜寫進硬碟，依語言分資料夾 ───────────────────────────────────
 *
 *        泰文 → audio\th\a3f9c2b81e47.wav   回傳 "th/a3f9c2b81e47.wav"
 *        中文 → audio\zh\b7e1d4a95c22.wav
 *
 *    ★ 副檔名是 .wav 不是 .mp3。瀏覽器兩種都能直接播，
 *      前端拿到什麼網址就播什麼，不需要知道格式。
 *
 * ── 第 6 步｜記帳 ───────────────────────────────────────────────────────
 *
 *        用量 = 送出去的字元數（Google 也是按字元計價）
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  ★ 失敗一律回空的 Optional，不丟例外
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  跟 OpenAiSpeechClient 同一條原則：語音出問題絕對不能連帶讓翻譯失敗。
 *  使用者要的是「這句泰文怎麼寫」，聲音沒生出來只是少一個播放鍵而已。
 *
 *  測試檔：src/test/java/com/tim/language_project/client/google/GoogleSpeechClientTest.java
 *          （音檔整理那段的測試在 WavAudioTest）
 */

import com.tim.language_project.client.SpeechClient;
import com.tim.language_project.client.storage.AudioStorage;
import com.tim.language_project.client.usage.ApiUsageRecorder;
import com.tim.language_project.config.GoogleSpeechProperties;
import com.tim.language_project.enums.AiProviderEnum;
import com.tim.language_project.enums.AiServiceTypeEnum;
import com.tim.language_project.enums.SpeechFailureReasonEnum;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.enums.UsageUnitTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 以 Google Cloud Text-to-Speech 合成語音，存成剪過靜音、音量一致的 WAV。
 * 只有設定 speech.provider=GOOGLE 時才會被建立出來。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "speech.provider", havingValue = "GOOGLE")
public class GoogleSpeechClient implements SpeechClient {

    private static final String ENDPOINT =
            "https://texttospeech.googleapis.com/v1/text:synthesize";

    /** 語言代碼。cmn-TW 是台灣華語，不是 zh-TW。 */
    private static final String THAI_LANGUAGE_CODE = "th-TH";

    private static final String CHINESE_LANGUAGE_CODE = "cmn-TW";

    private final RestClient restClient;

    private final ApiUsageRecorder apiUsageRecorder;

    private final GoogleSpeechProperties googleSpeechProperties;

    private final AudioStorage audioStorage;

    public GoogleSpeechClient(RestClient.Builder restClientBuilder,
                              ApiUsageRecorder apiUsageRecorder,
                              GoogleSpeechProperties googleSpeechProperties,
                              AudioStorage audioStorage) {
        this.restClient = restClientBuilder.build();
        this.apiUsageRecorder = apiUsageRecorder;
        this.googleSpeechProperties = googleSpeechProperties;
        this.audioStorage = audioStorage;
    }

    @Override
    public Optional<String> synthesize(String speechText, SpeechLanguageEnum language) {
        if (ObjectUtils.isEmpty(speechText)) {
            return Optional.empty();
        }

        String voiceName = voiceOf(language);
        byte[] wavBytes;

        try {
            wavBytes = callGoogle(speechText, language, voiceName);
        } catch (Exception exception) {
            // 沒接通就沒有費用，記 0 只是為了留下「這時候失敗過」的痕跡。
            recordFailure(SpeechFailureReasonEnum.CONNECTION_FAILED, voiceName, 0L, exception);
            return Optional.empty();
        }

        if (ObjectUtils.isEmpty(wavBytes)) {
            // 接通也回應了，只是內容是空的 —— 這一次已經被收費。
            recordFailure(SpeechFailureReasonEnum.UNKNOWN, voiceName,
                    speechText.length(), null);
            return Optional.empty();
        }

        Optional<byte[]> tidied = WavAudio.tidy(wavBytes);

        if (tidied.isEmpty()) {
            // 整段都是靜音。這一次確實被收費了，但檔案不能留 ——
            // 留下來會被永久快取，那個詞就再也沒有聲音（2026-08-14 的教訓）。
            recordFailure(SpeechFailureReasonEnum.UNKNOWN, voiceName,
                    speechText.length(), null);
            return Optional.empty();
        }

        // 存到哪裡由 AudioStorage 決定 —— 本機是 audio 資料夾，雲端是 Cloud Storage。
        // 這裡不需要知道是哪一種，只在乎「有沒有存成功」。
        //
        // ★ 副檔名是 wav 不是 mp3：Google 回的是 LINEAR16，
        //   而且 WavAudio.tidy 處理完仍然是 WAV，中間沒有任何轉檔。
        Optional<String> filePath = audioStorage.save(language, tidied.get(), "wav");

        if (filePath.isEmpty()) {
            // 聲音已經拿到了，錢也付了，是我們自己沒存下來。
            recordFailure(SpeechFailureReasonEnum.FILE_SAVE_FAILED, voiceName,
                    speechText.length(), null);
            return Optional.empty();
        }

        recordUsage(voiceName, speechText.length(), true);

        return filePath;
    }

    /**
     * 送出請求並把 base64 的 audioContent 解回真正的 WAV 位元組。
     */
    private byte[] callGoogle(String speechText, SpeechLanguageEnum language, String voiceName) {
        Map<String, Object> request = Map.of(
                "input", Map.of("text", speechText),
                "voice", Map.of(
                        "languageCode", languageCodeOf(language),
                        "name", voiceName),
                "audioConfig", Map.of("audioEncoding", "LINEAR16"));

        SynthesisResponse response = restClient.post()
                .uri(ENDPOINT + "?key={key}", googleSpeechProperties.getApiKey())
                .body(request)
                .retrieve()
                .body(SynthesisResponse.class);

        if (Objects.isNull(response) || ObjectUtils.isEmpty(response.audioContent())) {
            return new byte[0];
        }

        return Base64.getDecoder().decode(response.audioContent());
    }

    private String voiceOf(SpeechLanguageEnum language) {
        return Objects.equals(language, SpeechLanguageEnum.ZH)
                ? googleSpeechProperties.getChineseVoice()
                : googleSpeechProperties.getThaiVoice();
    }

    private String languageCodeOf(SpeechLanguageEnum language) {
        return Objects.equals(language, SpeechLanguageEnum.ZH)
                ? CHINESE_LANGUAGE_CODE
                : THAI_LANGUAGE_CODE;
    }

    /**
     * 記錄失敗。失敗原因只寫日誌不外拋，使用者只會發現這次沒有播放鍵。
     */
    private void recordFailure(SpeechFailureReasonEnum reason, String voiceName,
                               long characterCount, Exception exception) {
        log.error("google speech synthesis failed, reason={} ({})",
                reason.name(), reason.getDescription(), exception);

        recordUsage(voiceName, characterCount, false);
    }

    /**
     * 語音以字元計價，沒有輸出用量的概念，所以輸出量與輸出單價固定為 0。
     * 模型名稱記的是聲音的名稱，日後查帳才知道那筆錢是哪個聲音花的。
     */
    private void recordUsage(String voiceName, long characterCount, boolean success) {
        apiUsageRecorder.record(
                AiProviderEnum.GOOGLE,
                AiServiceTypeEnum.SPEECH,
                voiceName,
                UsageUnitTypeEnum.CHARACTER,
                characterCount,
                0L,
                googleSpeechProperties.getPricePerCharacter(),
                BigDecimal.ZERO,
                success);
    }

    /** Google 的回應格式。audioContent 是 base64 編碼過的音檔內容。 */
    private record SynthesisResponse(String audioContent) {
    }
}
