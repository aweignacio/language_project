package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  整個查詢的「總指揮」。前面做的都是零件（翻譯、語音、記帳、寫入），
 *  這個檔案負責決定「什麼時候做什麼」，以及「什麼時候不要做」。
 *
 *  最重要的一件事：★ 能不花錢就不花錢 ★
 *  每呼叫一次 OpenAI 就是真的付一次錢，所以有三道關卡擋在前面。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你輸入「我想喝酒」到畫面出現泰文
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜Controller 收到請求後呼叫這裡 ──────────────────────────────
 *
 *        translationService.translate("  我想喝酒  ", SpeakerGenderEnum.MALE);
 *                                                     ↑ 前端每次都會送性別過來
 *
 * ── 第 2 步｜檢查輸入（validateAndNormalize）────────────────────────────
 *
 *        "  我想喝酒  "  →  去掉前後空白  →  "我想喝酒"
 *
 *    ★ 為什麼一定要去空白？
 *      不去的話「我想喝酒」和「 我想喝酒 」在資料庫是兩筆不同的紀錄，
 *      同一句話會被翻譯兩次、付兩次錢。
 *
 *    兩道擋下來就不花錢的檢查：
 *        空的            → INPUT_REQUIRED   （空字串送去問也沒意義）
 *        超過 100 字     → INPUT_TOO_LONG   （欄位是 NVARCHAR(100)，
 *                                            不擋的話會等到寫入才爆，
 *                                            而那時錢已經花掉了）
 *
 * ── 第 3 步｜判斷方向，並決定性別要不要留 ───────────────────────────────
 *
 *        languageDetector.detect("我想喝酒")  →  ZH_TO_TH
 *
 *    畫面上沒有「切換方向」的按鈕，你打什麼系統就自己知道。
 *    判斷依據是字元範圍，見 LanguageDetector。
 *
 *    ★ 泰翻中沒有性別概念，effectiveGender 直接歸零。
 *      不歸零的話，同一句泰文會因為性別不同被翻譯兩次，白花一次錢。
 *
 * ── 第 4 步｜唯一的省錢關卡：查詢快取 ───────────────────────────────────
 *
 *        translationQueryRepository.findByKey("我想喝酒", ZH_TO_TH, MALE)
 *
 *        有 → 直接組回應回傳，fromCache = true，★一毛錢都不花★
 *        沒有 → 往下走，真的呼叫 translationClient.translate(...)
 *
 *    ★★ 這裡以前還有第二道關卡，2026-08-14 拿掉了，不要加回來 ★★
 *
 *      那段捷徑是：「整段輸入剛好是單字庫裡有的詞 → 直接拿單字庫的答案」。
 *      看起來很划算，實際上會讓「多重說法」整個功能失效：
 *
 *          第 1 天  你查「我想喝酒」→「我 → ฉัน」被沉澱進單字庫
 *          第 3 天  你單獨查「我」  → 捷徑看到單字庫有「我」，直接回 ฉัน
 *                                    → 永遠不會去問 AI 要 ผม 和 กู
 *
 *      而它省下的是什麼？「每個詞一輩子只省一次呼叫」而已 ——
 *      因為第一次查完就會寫進 translation_query，之後本來就走第 4 步的快取。
 *      用整個功能換一次呼叫，不划算。
 *
 *      TranslationServiceTest 有一個測試專門把這條捷徑釘死，
 *      有人加回來就會亮紅燈。
 *
 * ── 第 5 步｜另一道關卡：模型說「這翻不出來」───────────────────────────
 *
 *        if (!result.translatable()) → 丟 INPUT_UNSUPPORTED_CONTENT
 *
 *    ★ 這一步是為了「嘎逼」「asdfgh」這種輸入。
 *
 *      語言模型的本性是「盡量給一個像樣的答案」，它會拼一個發音接近的泰文，
 *      而且講得跟真的一樣。如果讓它存進去：
 *        - 快取會永久記住那個編造的答案
 *        - 單字庫會被垃圾污染，而使用者是拿它來背單字的
 *
 *      所以我們在提示詞裡正式跟模型要一個判斷，它說不行就當場停手 ——
 *      不生語音、不寫資料庫。
 *
 *      （注意：這次呼叫的錢還是付了，用量已經由 client 記帳，那筆帳是真的。）
 *
 * ── 第 6 步｜生語音（哪些語言要生，由設定決定）─────────────────────────
 *
 *        thaiAudioUrl    = autoGenerateAudio(result.thaiText(),    TH);
 *        chineseAudioUrl = autoGenerateAudio(result.chineseText(), ZH);
 *
 *    ★ 這裡不直接呼叫 SpeechClient，一律走 AudioAssetService ——
 *      那一層會先查 audio_asset，同一段文字全站只合成一次。
 *
 *    ★ 「哪些語言要自動生」是設定值 audio.storage.auto-generate，預設只有 TH。
 *      中文音檔的機制已完整建置，只是預設不主動產生 ——
 *      目前的使用者是中文母語者，不需要聽中文，為它每次多等一兩秒不划算。
 *      沒生的那個回 null，前端顯示成灰色的鍵，點了才生。
 *
 *    ★ 逐詞的音檔「不在這裡生」，只查現成的（見 toSegmentDtos）。
 *      一句話拆成四五個詞，每個都生要多打四五次 OpenAI、多等好幾秒，
 *      而那些詞你未必想聽。改成點哪個生哪個（POST /api/v1/audio）。
 *
 *    失敗回傳空的 Optional → 網址是 null → 前端不顯示播放鍵，
 *    但翻譯結果照樣給使用者。聲音失敗不該讓整個查詢失敗。
 *
 * ── 第 7 步｜組多重說法（只有查單一個詞時才有）─────────────────────────
 *
 *        buildVariants(result)  →  ผม / ฉัน / กู 三筆，各自帶標籤與音檔
 *
 *    查句子時 result.variants() 是空的，這裡就回空清單。
 *
 * ── 第 8 步｜寫進資料庫 ─────────────────────────────────────────────────
 *
 *        translationPersistenceService.persist(sourceText, direction, gender, result);
 *
 *    寫入快取、逐詞、單字庫三張表，綁在同一個交易裡（見該檔說明）。
 *    音檔不在裡面 —— 它在第 6 步就已經由 AudioAssetService 自己存好了。
 *
 * ── 第 9 步｜組回應給前端 ───────────────────────────────────────────────
 *
 *        {
 *          "sourceText": "我想喝酒",
 *          "direction": "ZH_TO_TH",
 *          "gender": "MALE",
 *          "chineseText": "我想喝酒",
 *          "thaiText": "ผมอยากดื่มเหล้าครับ",
 *          "romanization": "pǒm yàak dùuem lâo khráp",
 *          "thaiAudioUrl": "/audio/th/a3f9c2b81e47.mp3",
 *          "chineseAudioUrl": null,
 *          "fromCache": false,
 *          "segments": [ {"seqNo":1,"chineseText":"我",...}, ... ],
 *          "variants": []
 *        }
 *
 *    ★ chineseText 和 thaiText 兩面都給，前端不需要自己判斷方向。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  一個刻意的設計：外部呼叫不放在交易裡
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  這個方法「沒有」標 @Transactional，只有第 7 步的寫入才有。
 *
 *  因為呼叫 OpenAI 要好幾秒。如果整段包在交易裡，那幾秒鐘會一直佔著
 *  一條資料庫連線，幾個人同時查就會把連線池吃光，整個網站卡住。
 *
 *  代價是「翻譯成功但寫入失敗」時，錢花了卻沒存下來。
 *  但那比「整個網站卡死」好得多，而且下次查詢會重新來過。
 *
 *  測試檔：src/test/java/com/tim/language_project/service/TranslationServiceTest.java
 */

import com.tim.language_project.client.TranslationClient;
import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.client.model.TranslationVariant;
import com.tim.language_project.client.model.TranslationWord;
import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.dto.response.TranslationQueryDto;
import com.tim.language_project.dto.response.TranslationResponseDto;
import com.tim.language_project.dto.response.TranslationSegmentDto;
import com.tim.language_project.dto.response.TranslationVariantDto;
import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.repository.TranslationQueryRepository;
import com.tim.language_project.repository.TranslationSegmentRepository;
import com.tim.language_project.repository.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 查詢主流程。先讀快取，未命中才呼叫外部服務，所以重複查詢不花錢。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    /** 對應 translation_query.source_text 的 NVARCHAR(100)。 */
    private static final int MAX_SOURCE_TEXT_LENGTH = 100;

    private final TranslationQueryRepository translationQueryRepository;

    private final TranslationSegmentRepository translationSegmentRepository;

    private final VocabularyRepository vocabularyRepository;

    private final TranslationClient translationClient;

    private final LanguageDetector languageDetector;

    private final AudioAssetService audioAssetService;

    private final AudioStorageProperties audioStorageProperties;

    private final TranslationPersistenceService translationPersistenceService;

    /**
     * 查一段文字，自動判斷方向後回傳兩面的文字、拼音、逐詞對照、多重說法與音檔網址。
     * 外部呼叫刻意跑在交易之外，只有最後的寫入才進交易 —— 理由見檔案開頭。
     */
    public TranslationResponseDto translate(String rawInput, SpeakerGenderEnum gender) {
        String sourceText = validateAndNormalize(rawInput);
        TranslationDirectionEnum direction = languageDetector.detect(sourceText);

        // ★ 泰翻中沒有性別概念。前端照樣會送性別過來，這裡直接歸零 ——
        //   不歸零的話，同一句泰文會因為性別不同被翻譯兩次，白花一次錢。
        SpeakerGenderEnum effectiveGender =
                Objects.equals(direction, TranslationDirectionEnum.TH_TO_ZH) ? null : gender;

        Optional<TranslationQueryDto> cached =
                translationQueryRepository.findByKey(sourceText, direction, effectiveGender);

        if (cached.isPresent()) {
            return buildCachedResponse(cached.get());
        }

        TranslationResult result =
                translationClient.translate(sourceText, direction, effectiveGender);

        if (!result.translatable()) {
            // 模型自己說翻不出來。當場停手，不生語音也不寫資料庫，
            // 免得一個編造的詞被永久留在快取與單字庫裡。
            log.warn("model reported untranslatable input, length={}", sourceText.length());
            throw new BusinessException(ErrorCodeEnum.INPUT_UNSUPPORTED_CONTENT);
        }

        String thaiAudioUrl = autoGenerateAudio(result.thaiText(), SpeechLanguageEnum.TH);
        String chineseAudioUrl = autoGenerateAudio(result.chineseText(), SpeechLanguageEnum.ZH);
        List<TranslationVariantDto> variants = buildVariants(result);

        try {
            translationPersistenceService.persist(
                    sourceText, direction, effectiveGender, result);
        } catch (DataIntegrityViolationException exception) {
            // 撞到唯一鍵，代表在我們翻譯的這幾秒內，另一個請求已經把同一句寫進去了。
            // 這不是錯誤，是「有人比我們快」。改讀他寫好的那筆回傳，
            // 使用者完全不會發現發生過這件事。
            log.warn("concurrent write detected for the same input, falling back to the cached row");

            return translationQueryRepository.findByKey(sourceText, direction, effectiveGender)
                    .map(this::buildCachedResponse)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCodeEnum.DATA_PERSIST_FAILED, exception));
        }

        return new TranslationResponseDto(
                sourceText, direction, effectiveGender,
                result.chineseText(), result.thaiText(), result.romanization(),
                thaiAudioUrl, chineseAudioUrl, false,
                toSegmentDtos(result.words()), variants);
    }

    /**
     * 設定裡有列到的語言才自動產生音檔，沒列到的一律回 null（改由使用者點擊產生）。
     */
    private String autoGenerateAudio(String speechText, SpeechLanguageEnum language) {
        if (!audioStorageProperties.getAutoGenerate().contains(language)) {
            return null;
        }

        return audioAssetService.resolveAudioUrl(speechText, language).orElse(null);
    }

    /**
     * 把每一種說法的音檔一併產生。
     * ★ 整句與第一個說法常常是同一段文字（查「我」時 thaiText 就是 ผม），
     *   靠 audio_asset 的唯一鍵自動共用，不會重複合成。
     */
    private List<TranslationVariantDto> buildVariants(TranslationResult result) {
        if (ObjectUtils.isEmpty(result.variants())) {
            return List.of();
        }

        List<TranslationVariantDto> variants = new ArrayList<>();

        for (TranslationVariant variant : result.variants()) {
            variants.add(new TranslationVariantDto(
                    variant.thaiText(),
                    variant.romanization(),
                    variant.genderUsage(),
                    variant.politeness(),
                    variant.note(),
                    autoGenerateAudio(variant.thaiText(), SpeechLanguageEnum.TH)));
        }

        return variants;
    }

    /**
     * 去掉前後空白並擋掉不值得花錢的輸入。
     * 去空白很重要 —— 不然「我想喝酒」和「 我想喝酒 」會變成兩筆快取，付兩次錢。
     */
    private String validateAndNormalize(String rawInput) {
        if (ObjectUtils.isEmpty(rawInput) || ObjectUtils.isEmpty(rawInput.trim())) {
            throw new BusinessException(ErrorCodeEnum.INPUT_REQUIRED);
        }

        String sourceText = rawInput.trim();

        if (sourceText.length() > MAX_SOURCE_TEXT_LENGTH) {
            throw new BusinessException(ErrorCodeEnum.INPUT_TOO_LONG);
        }

        return sourceText;
    }

    private TranslationResponseDto buildCachedResponse(TranslationQueryDto cached) {
        List<TranslationSegmentDto> segments =
                translationSegmentRepository.findByQueryIdOrderBySeqNo(cached.id());

        // 快取命中代表「這次不花錢」，所以只查現成的音檔，絕不合成。
        String thaiAudioUrl = audioAssetService
                .findExistingAudioUrl(cached.thaiText(), SpeechLanguageEnum.TH).orElse(null);
        String chineseAudioUrl = audioAssetService
                .findExistingAudioUrl(cached.chineseText(), SpeechLanguageEnum.ZH).orElse(null);

        return new TranslationResponseDto(
                cached.sourceText(), cached.direction(), cached.gender(),
                cached.chineseText(), cached.thaiText(), cached.romanization(),
                thaiAudioUrl, chineseAudioUrl, true,
                segments, cachedVariants(cached));
    }

    /**
     * 快取命中時，說法從單字庫撈 —— 那裡就是它們的家。
     * 只有單字查詢才有說法，句子查詢撈出來會是空的（因為句子不是一個詞）。
     */
    private List<TranslationVariantDto> cachedVariants(TranslationQueryDto cached) {
        List<VocabularyDto> words =
                vocabularyRepository.findByChineseText(cached.chineseText());

        if (words.size() <= 1) {
            return List.of();
        }

        List<TranslationVariantDto> variants = new ArrayList<>();

        for (VocabularyDto word : words) {
            variants.add(new TranslationVariantDto(
                    word.thaiText(), word.romanization(),
                    word.genderUsage(), word.politeness(), word.note(),
                    audioAssetService.findExistingAudioUrl(
                            word.thaiText(), SpeechLanguageEnum.TH).orElse(null)));
        }

        return variants;
    }

    private List<TranslationSegmentDto> toSegmentDtos(List<TranslationWord> words) {
        List<TranslationSegmentDto> segments = new ArrayList<>();
        int seqNo = 1;

        for (TranslationWord word : words) {
            // ★ 逐詞音檔是「點了才生」，所以這裡只查現成的，不合成。
            //   改成 resolveAudioUrl 的話，查一句話會多打好幾次 OpenAI，
            //   使用者要多等四到八秒。
            segments.add(new TranslationSegmentDto(
                    seqNo++, word.chineseText(), word.thaiText(), word.romanization(),
                    audioAssetService.findExistingAudioUrl(
                            word.thaiText(), SpeechLanguageEnum.TH).orElse(null),
                    audioAssetService.findExistingAudioUrl(
                            word.chineseText(), SpeechLanguageEnum.ZH).orElse(null)));
        }

        return segments;
    }
}
