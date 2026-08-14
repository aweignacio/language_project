package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  回答一個問題：「這段文字，是我們系統自己產生過的嗎？」
 *
 *  為什麼需要它：合成語音的那支 API 會花錢。
 *  沒有這道檢查，任何人寫三行程式送隨機字串進來，就能把 OpenAI 的餘額燒光，
 *  而且伺服器日誌上每一筆都長得像正常請求。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你點下播放鍵到通過檢查
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在逐詞對照看到「เหล้า」旁邊有個灰色的播放鍵，點下去 ──────
 *
 *        POST /api/v1/audio  { "speechText": "เหล้า", "language": "TH" }
 *
 * ── 第 2 步｜AudioController 先問這裡 ───────────────────────────────────
 *
 *        speechTextGuard.isKnown("เหล้า", SpeechLanguageEnum.TH)
 *
 * ── 第 3 步｜依語言決定要比對哪一欄，去三張表找 ─────────────────────────
 *
 *    TH → 比對三張表的 thai_text 欄位
 *    ZH → 比對三張表的 chinese_text 欄位
 *
 *        translation_segment  逐詞拆解的結果（最常命中的就是這張）
 *        translation_query    整句翻譯的結果
 *        vocabulary           單字庫
 *
 *    只要任何一張找得到，就是「我們產生過的」，回 true。
 *
 *    ★ 用短路運算（||）串起來，找到就不再查後面兩張，
 *      所以最常命中的 translation_segment 放第一個。
 *
 * ── 第 4 步｜回傳結果 ───────────────────────────────────────────────────
 *
 *    true  → AudioController 繼續，交給 AudioAssetService
 *    false → 丟 SPEECH_TEXT_UNKNOWN，回 400，★沒有花任何錢★
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  ★ 這不是效能考量，是安全考量
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  看起來像是「先查一下比較快」，其實不是 —— 這三次查詢反而讓請求變慢。
 *  它存在的唯一理由是擋住花錢。不要因為「想讓 API 快一點」就把它拿掉。
 *
 *  測試檔：合成 API 的守門行為在
 *          src/test/java/com/tim/language_project/controller/AudioControllerTest.java
 */

import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.repository.TranslationQueryRepository;
import com.tim.language_project.repository.TranslationSegmentRepository;
import com.tim.language_project.repository.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Objects;

/**
 * 判斷一段文字是否為系統產生過的內容，用來擋掉會花錢的任意合成請求。
 */
@Component
@RequiredArgsConstructor
public class SpeechTextGuard {

    private final TranslationSegmentRepository translationSegmentRepository;

    private final TranslationQueryRepository translationQueryRepository;

    private final VocabularyRepository vocabularyRepository;

    /**
     * 這段文字是否出現在逐詞、查詢快取或單字庫裡。
     */
    public boolean isKnown(String speechText, SpeechLanguageEnum language) {
        if (ObjectUtils.isEmpty(speechText) || Objects.isNull(language)) {
            return false;
        }

        if (Objects.equals(language, SpeechLanguageEnum.TH)) {
            return translationSegmentRepository.existsByThaiText(speechText)
                    || translationQueryRepository.existsByThaiText(speechText)
                    || vocabularyRepository.existsByThaiText(speechText);
        }

        return translationSegmentRepository.existsByChineseText(speechText)
                || translationQueryRepository.existsByChineseText(speechText)
                || vocabularyRepository.existsByChineseText(speechText);
    }
}
