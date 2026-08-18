package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  全站唯一一個可以叫 OpenAI 合成語音的地方。
 *
 *  為什麼要有這一層：合成語音要付錢。如果每個需要聲音的地方都各自去呼叫，
 *  同一個泰文詞會被合成好幾次 —— 你查「酒」合成一次、
 *  查「我想喝酒」逐詞再合成一次、查「他喝酒了」又合成一次。
 *  三個一模一樣的 mp3，付了三次錢。
 *
 *  這個檔案的規則只有一句：★ 同一段文字，全站只合成一次 ★
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從需要一段聲音到拿到網址
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜有人需要「เหล้า」的發音 ────────────────────────────────────
 *
 *    可能是 TranslationService（查詢完成要附上音檔），
 *    也可能是 AudioController（你在畫面上點了逐詞的播放鍵）。
 *    兩邊都呼叫同一個方法：
 *
 *        audioAssetService.resolveAudioUrl("เหล้า", SpeechLanguageEnum.TH);
 *
 * ── 第 2 步｜先查資料庫 ─────────────────────────────────────────────────
 *
 *        audioAssetRepository.findBySpeechTextAndLanguage("เหล้า", TH)
 *
 *    查到了 → 拿出 file_path，例如 "th/a1b2c3.mp3"
 *             加上前綴變成 "/audio/th/a1b2c3.mp3" 回傳
 *             ★ 到此結束，一毛錢都沒花 ★
 *
 *    沒查到 → 往下
 *
 * ── 第 3 步｜真的去合成（這一步會花錢）─────────────────────────────────
 *
 *        speechClient.synthesize("เหล้า", TH)  →  Optional("th/d4e5f6.mp3")
 *
 *    tts-1 按字元計價，「เหล้า」是 5 個字元，約 0.000075 美金。
 *    很便宜，但重複一萬次就不便宜了 —— 那正是第 2 步在擋的事。
 *
 * ── 第 4 步｜把結果記下來，下次就不用再花錢 ─────────────────────────────
 *
 *        audio_asset 新增一筆：("เหล้า", TH, "th/d4e5f6.mp3")
 *
 *    ★ 合成失敗（第 3 步回傳空的）時「絕對不可以」寫入。
 *      寫進去的話，那筆紀錄之後每次都會命中，使用者再怎麼點都不會重試，
 *      這個詞就永遠沒有聲音了。
 *
 * ── 第 5 步｜回傳網址 ───────────────────────────────────────────────────
 *
 *        "/audio/th/d4e5f6.mp3"
 *
 *    前端直接放進 <audio src> 就能播。網址怎麼對應到硬碟上的檔案，
 *    是 WebMvcConfig 在管的，這裡不需要知道。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  ★ 為什麼回傳 Optional 而不是丟例外
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  合成失敗是「這個詞暫時沒有聲音」，不是「這次查詢失敗」。
 *  丟例外的話，語音服務一出問題，整個翻譯功能就跟著不能用了。
 *  回傳空的 Optional，呼叫端就只是少顯示一個播放鍵而已。
 *
 *  測試檔：src/test/java/com/tim/language_project/service/AudioAssetServiceTest.java
 */

import com.tim.language_project.client.SpeechClient;
import com.tim.language_project.dto.response.AudioAssetDto;
import com.tim.language_project.entity.AudioAsset;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.repository.AudioAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 全站唯一的語音合成入口，先查資料庫再決定要不要花錢。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioAssetService {

    private static final String AUDIO_URL_PREFIX = "/audio/";

    private final AudioAssetRepository audioAssetRepository;

    private final SpeechClient speechClient;

    /**
     * 取得一段文字的音檔網址。已經合成過就直接回傳舊的，沒有才合成。
     * 合成失敗時回傳空的 Optional，呼叫端當作「這段文字暫時沒有聲音」處理。
     */
    public Optional<String> resolveAudioUrl(String speechText, SpeechLanguageEnum language) {
        if (ObjectUtils.isEmpty(speechText)) {
            return Optional.empty();
        }

        Optional<AudioAssetDto> existing =
                audioAssetRepository.findBySpeechTextAndLanguage(speechText, language);

        if (existing.isPresent()) {
            return Optional.of(toAudioUrl(existing.get().filePath()));
        }

        Optional<String> synthesizedPath = speechClient.synthesize(speechText, language);

        if (synthesizedPath.isEmpty()) {
            // 合成失敗。刻意不寫入資料庫 —— 寫了之後這段文字會永遠命中那筆假紀錄，
            // 使用者再怎麼點都不會重試。
            return Optional.empty();
        }

        persist(speechText, language, synthesizedPath.get());

        return Optional.of(toAudioUrl(synthesizedPath.get()));
    }

    /**
     * 只查現成的音檔，查不到就回空的，★絕對不會觸發合成★。
     * 讀取快取時用這個 —— 快取命中的意義就是「這次不花錢」，
     * 若在那條路上呼叫 resolveAudioUrl，音檔缺失時會偷偷變成一次付費呼叫，
     * 而回應還標著 fromCache: true，帳目會對不起來。
     */
    public Optional<String> findExistingAudioUrl(String speechText,
                                                 SpeechLanguageEnum language) {
        if (ObjectUtils.isEmpty(speechText)) {
            return Optional.empty();
        }

        return audioAssetRepository.findBySpeechTextAndLanguage(speechText, language)
                .map(audioAsset -> toAudioUrl(audioAsset.filePath()));
    }

    /**
     * 一次查一整批文字的現成音檔，★絕對不會觸發合成★。
     *
     * 回傳的 Map 以文字為鍵、網址為值；沒有音檔的文字「不會出現在 Map 裡」，
     * 呼叫端用 get 拿到 null 就知道那一列的播放鍵要顯示成灰的。
     *
     * ★ 清單畫面一定要用這支，不可以在迴圈裡呼叫 findExistingAudioUrl ——
     *   那是 N+1，收藏一百筆就是一百趟資料庫往返，而且資料少時看不出來。
     */
    public Map<String, String> findExistingAudioUrls(Collection<String> speechTexts,
                                                     SpeechLanguageEnum language) {
        if (ObjectUtils.isEmpty(speechTexts)) {
            return Map.of();
        }

        return audioAssetRepository.findBySpeechTextInAndLanguage(speechTexts, language)
                .stream()
                .collect(Collectors.toMap(
                        AudioAssetDto::speechText,
                        audioAsset -> toAudioUrl(audioAsset.filePath()),
                        // 同一段文字同一語言在資料庫有唯一鍵，理論上撞不到。
                        // 真的撞到時取先出現的那一個 —— 兩個檔案內容一樣，播哪個都對。
                        (existing, duplicate) -> existing));
    }

    /**
     * 寫入音檔紀錄。撞到唯一鍵代表另一個請求在我們合成的這幾秒內先寫進去了，
     * 這不是錯誤，忽略即可 —— 我們手上這個檔案照樣能播，只是多存了一份在硬碟上。
     */
    private void persist(String speechText, SpeechLanguageEnum language, String filePath) {
        AudioAsset audioAsset = new AudioAsset();
        audioAsset.setSpeechText(speechText);
        audioAsset.setLanguage(language);
        audioAsset.setFilePath(filePath);

        try {
            audioAssetRepository.saveAndFlush(audioAsset);
        } catch (DataIntegrityViolationException exception) {
            log.warn("concurrent audio synthesis detected, keeping the file just created");
        }
    }

    private String toAudioUrl(String filePath) {
        return AUDIO_URL_PREFIX + filePath;
    }
}
