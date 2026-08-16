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
 *        thaiAudioUrl    = autoGenerateAudio(result.thaiText(),    TH, direction);
 *        chineseAudioUrl = autoGenerateAudio(result.chineseText(), ZH, direction);
 *
 *    ★ 這裡不直接呼叫 SpeechClient，一律走 AudioAssetService ——
 *      那一層會先查 audio_asset，同一段文字全站只合成一次。
 *
 *    ★ 中文音檔「只有你輸入泰文時才生」：
 *
 *          你打「我想喝酒」（中翻泰）→ 只生泰文。
 *              中文是你自己剛打的字，唸給你聽沒有價值，
 *              為它多打一次 OpenAI、每次查詢多等一兩秒不划算。
 *
 *          你貼「ผม」（泰翻中）→ 泰文和中文都生。
 *              中文是「翻譯結果」，你需要聽它確認自己有沒有理解對。
 *
 *      沒生的那個回 null，前端顯示成灰色的鍵，點了才生。
 *
 *    ★ 逐詞的音檔「不在這裡生」，只查現成的（見 toSegmentDtos）。
 *      一句話拆成四五個詞，每個都生要多打四五次 OpenAI、多等好幾秒，
 *      而那些詞你未必想聽。改成點哪個生哪個（POST /api/v1/audio）。
 *
 *    失敗回傳空的 Optional → 網址是 null → 前端不顯示播放鍵，
 *    但翻譯結果照樣給使用者。聲音失敗不該讓整個查詢失敗。
 *
 * ── 第 7 步｜寫進資料庫 ─────────────────────────────────────────────────
 *
 *        translationPersistenceService.persist(sourceText, direction, gender, result);
 *
 *    ★ 只寫 translation_query 那一列，而且會把資料庫產生的 id 回傳給我們。
 *      逐詞與說法這次完全沒問，當然也沒有東西可以寫。
 *      音檔也不在裡面 —— 它在第 6 步就已經由 AudioAssetService 自己存好了。
 *
 * ── 第 8 步｜組回應給前端 ───────────────────────────────────────────────
 *
 *        {
 *          "queryId": 137,
 *          "sourceText": "我想喝酒",
 *          "direction": "ZH_TO_TH",
 *          "gender": "MALE",
 *          "chineseText": "我想喝酒",
 *          "thaiText": "ผมอยากดื่มเหล้าครับ",
 *          "romanization": "pǒm yàak dùuem lâo khráp",
 *          "thaiAudioUrl": "/audio/th/a3f9c2b81e47.mp3",
 *          "chineseAudioUrl": null,
 *          "fromCache": false
 *        }
 *
 *    ★ chineseText 和 thaiText 兩面都給，前端不需要自己判斷方向。
 *    ★ queryId 是給第 9 步用的。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  ★ 第 9 步｜逐詞拆解與各種說法：使用者「點了才跑」（2026-08-16 改的）
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  以前第 4 步那一次 OpenAI 呼叫要一口氣產出翻譯、拼音、逐詞拆解、各種說法。
 *  查 api_usage_log 量到平均輸出 867 個 token；而語言模型是一個 token 一個
 *  token 吐出來的，以每秒約 40 個計算，光是「產出」就要 22 秒 ——
 *  跟你實際等到不耐煩的時間完全吻合。
 *
 *  ★ 關鍵是：你按下查詢的那一刻，只想看到泰文和拼音。
 *    逐詞拆解和各種說法是「看完之後才會慢慢研究」的東西，
 *    而且大部分時候你根本不會看。
 *
 *  所以拆成三支，後兩支等你在畫面上點下去才跑：
 *
 *      畫面點「逐詞拆解」→ POST /api/v1/translations/137/segments
 *                        → resolveSegments(137)
 *                            ① translation_segment 有 137 的資料 → 直接回，不花錢
 *                            ② 沒有 → translationClient.segment(中文, 泰文)
 *                                    → 寫進 translation_segment ＋ 單字庫
 *
 *      畫面點「各種說法」→ POST /api/v1/translations/137/variants
 *                        → resolveVariants(137)
 *                            ① vocabulary 裡這個中文詞已經有兩種以上說法 → 直接回
 *                            ② 沒有 → translationClient.variants(中文, 泰文)
 *                                    → 寫進單字庫
 *
 *  ★ 最容易搞混：resolveSegments 傳的是「翻譯完成後的中泰兩面」，
 *    不是使用者當初打的字。這樣模型不必重新翻譯一次，只要專心切分，
 *    輸出量與出錯機會都小得多。
 *
 *  ★ 為什麼快取命中時也不順便把逐詞撈出來給前端？
 *    因為那樣「第一次查」和「第二次查」的畫面會不一樣 ——
 *    第二次一打開就長出拆解，看起來像壞掉。一律等你點。
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
import com.tim.language_project.client.model.SegmentationResult;
import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.client.model.TranslationVariant;
import com.tim.language_project.client.model.VariantResult;
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
     * 查一段文字，自動判斷方向後回傳兩面的文字、拼音與音檔網址。
     * 外部呼叫刻意跑在交易之外，只有最後的寫入才進交易 —— 理由見檔案開頭。
     *
     * ★ 這裡「只有整句」。逐詞拆解與各種說法要另外呼叫 resolveSegments／
     *   resolveVariants，而且是使用者在畫面上點了才跑。
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

        String thaiAudioUrl =
                autoGenerateAudio(result.thaiText(), SpeechLanguageEnum.TH, direction);
        String chineseAudioUrl =
                autoGenerateAudio(result.chineseText(), SpeechLanguageEnum.ZH, direction);

        Long queryId;

        try {
            queryId = translationPersistenceService.persist(
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
                queryId, sourceText, direction, effectiveGender,
                result.chineseText(), result.thaiText(), result.romanization(),
                thaiAudioUrl, chineseAudioUrl, false);
    }

    /**
     * 逐詞拆解：使用者在畫面上點了「逐詞拆解」才會走到這裡。
     *
     * 順序刻意是「先問資料庫，沒有才問模型」：
     *
     *   ① 資料庫裡已經有「這句泰文」的拆解 → 直接回，不花錢
     *   ② 沒有 → 呼叫 OpenAI 拆一次，寫進資料庫，之後任何人再點都走 ①
     *
     * ★ 第 ① 步找的是「泰文一樣」而不是「同一筆查詢」（2026-08-16 改的）。
     *
     *   「我想喝酒」和「我想要喝酒」是兩句不同的中文，會各自存一列快取，
     *   但翻出來的泰文常常一模一樣。用查詢 id 去找的話，第二句找不到自己的
     *   逐詞，於是又付一次錢把「同一句泰文」重新拆一次。
     *
     *   ★ 副作用要知道：第二句拿到的是第一句的逐詞，所以中文那欄會是
     *     「想」而不是「想要」。這是可以接受的 —— 逐詞對照本來就是在解釋
     *     「這個泰文詞是什麼意思」，อยาก 註成「想」或「想要」都對。
     *
     *   ★ 翻譯那一次呼叫省不掉：要先拿到泰文才知道泰文一樣，
     *     而拿到泰文就是付錢的那一刻。詳見 TranslationSegmentRepository。
     *
     * ★ 模型拆不出來時 segment 會回空的 list（見 OpenAiTranslationClient），
     *   這裡照樣回空的給前端，畫面顯示「沒有拆解結果」，不會擋住整句。
     */
    public List<TranslationSegmentDto> resolveSegments(Long queryId) {
        TranslationQueryDto query = translationQueryRepository.findDtoById(queryId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND));

        List<TranslationSegmentDto> cached =
                translationSegmentRepository.findByThaiTextOrderBySeqNo(query.thaiText());

        if (!ObjectUtils.isEmpty(cached)) {
            return withSegmentAudio(cached);
        }

        SegmentationResult result =
                translationClient.segment(query.chineseText(), query.thaiText());

        if (ObjectUtils.isEmpty(result.words())) {
            return List.of();
        }

        translationPersistenceService.persistSegmentation(
                queryId, query.sourceText(), result.words());

        return toSegmentDtos(result.words());
    }

    /**
     * 各種說法：使用者在畫面上點了「各種說法」才會走到這裡。
     *
     * 順序同樣是「先問資料庫，沒有才問模型」，只是家在單字庫：
     *
     *   ① vocabulary 裡這個中文詞已經有兩種以上的說法 → 直接回，不花錢
     *   ② 沒有 → 呼叫 OpenAI 列一次，寫進單字庫，之後任何人再點都走 ①
     *
     * ★ 只有一列的情況要當作「還沒問過」而不是「只有一種說法」。
     *   那一列通常是查整句時從逐詞沉澱下來的，本來就沒有其他說法可比。
     *
     * ★ 整句沒有「另一種說法」這種東西（換個講法那叫翻譯），模型會回空的，
     *   這裡就回空的，前端顯示「沒有其他說法」。
     *
     *   ★★ 但要知道「模型會回空的」這件事是「提示詞叫它這樣做」換來的，
     *      不是這裡檢查出來的。下面那段寫入完全不會過問傳進去的是詞還是句子。
     *      提示詞那條規則被拿掉的話，一整句話就會躺進單字庫，
     *      而且畫面完全正常、測試也全過。詳見 OpenAiTranslationClient
     *      的 VARIANT_PROMPT 上面那段。
     */
    public List<TranslationVariantDto> resolveVariants(Long queryId) {
        TranslationQueryDto query = translationQueryRepository.findDtoById(queryId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND));

        List<TranslationVariantDto> cached = cachedVariants(query.chineseText());

        if (!ObjectUtils.isEmpty(cached)) {
            return cached;
        }

        VariantResult result =
                translationClient.variants(query.chineseText(), query.thaiText());

        if (ObjectUtils.isEmpty(result.variants())) {
            return List.of();
        }

        translationPersistenceService.persistVariants(
                query.sourceText(), query.chineseText(), result.variants());

        return buildVariants(result.variants(), query.direction());
    }

    /**
     * 決定這次要不要自動產生某個語言的音檔，不產生就回 null（前端顯示成灰色的鍵，
     * 使用者點了才生）。
     *
     * 兩道關卡：
     *
     *   ① 總開關：設定 audio.storage.auto-generate 沒列到的語言一律不生
     *
     *   ② ★ 中文只在「使用者輸入泰文」時才生 ★
     *
     *      泰翻中時中文是「翻譯結果」—— 你貼一段看不懂的泰文進來，
     *      需要聽中文確認自己有沒有理解對，所以要生。
     *
     *      中翻泰時中文是「你自己剛打的字」，唸給你聽沒有任何價值，
     *      卻要多打一次 OpenAI、每次查詢多等一兩秒。所以不生。
     */
    private String autoGenerateAudio(String speechText,
                                     SpeechLanguageEnum language,
                                     TranslationDirectionEnum direction) {
        if (!audioStorageProperties.getAutoGenerate().contains(language)) {
            return null;
        }

        if (Objects.equals(language, SpeechLanguageEnum.ZH)
                && !Objects.equals(direction, TranslationDirectionEnum.TH_TO_ZH)) {
            return null;
        }

        return audioAssetService.resolveAudioUrl(speechText, language).orElse(null);
    }

    /**
     * 把每一種說法的音檔一併產生。
     * ★ 整句與第一個說法常常是同一段文字（查「我」時 thaiText 就是 ผม），
     *   靠 audio_asset 的唯一鍵自動共用，不會重複合成。
     */
    private List<TranslationVariantDto> buildVariants(List<TranslationVariant> sourceVariants,
                                                      TranslationDirectionEnum direction) {
        List<TranslationVariantDto> variants = new ArrayList<>();

        for (TranslationVariant variant : sourceVariants) {
            variants.add(new TranslationVariantDto(
                    variant.thaiText(),
                    variant.romanization(),
                    variant.genderUsage(),
                    variant.politeness(),
                    variant.note(),
                    autoGenerateAudio(variant.thaiText(),
                            SpeechLanguageEnum.TH, direction)));
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

    /**
     * ★ 快取命中時也不撈逐詞與說法。
     *   它們是「點了才長出來」的東西，就算資料庫已經有了也一樣等使用者點 ——
     *   不然同一句話「第一次查」和「第二次查」的畫面會不一樣，看起來像壞掉。
     */
    private TranslationResponseDto buildCachedResponse(TranslationQueryDto cached) {
        // 快取命中代表「這次不花錢」，所以只查現成的音檔，絕不合成。
        String thaiAudioUrl = audioAssetService
                .findExistingAudioUrl(cached.thaiText(), SpeechLanguageEnum.TH).orElse(null);
        String chineseAudioUrl = audioAssetService
                .findExistingAudioUrl(cached.chineseText(), SpeechLanguageEnum.ZH).orElse(null);

        return new TranslationResponseDto(
                cached.id(), cached.sourceText(), cached.direction(), cached.gender(),
                cached.chineseText(), cached.thaiText(), cached.romanization(),
                thaiAudioUrl, chineseAudioUrl, true);
    }

    /**
     * 幫快取撈出來的逐詞資料補上音檔網址。
     *
     * ★ 為什麼要補：音檔不在 translation_segment 那張表裡（全站的音檔由
     *   audio_asset 持有），JPQL 的建構子表達式又沒辦法跨表把它們一起撈出來，
     *   所以 findByThaiTextOrderBySeqNo 的兩個音檔欄位一律回 null。
     *
     * ★ 漏掉這一步的症狀很不明顯：畫面照常、點下去也會有聲音，
     *   只是「該亮的播放鍵一直是灰的」，看起來像音檔從來沒生出來過。
     *   2026-08-14 就是這樣被抓到的。
     *
     * ★ 用 findExistingAudioUrl（只查不生）—— 快取命中的意義就是「這次不花錢」。
     */
    private List<TranslationSegmentDto> withSegmentAudio(List<TranslationSegmentDto> segments) {
        List<TranslationSegmentDto> filled = new ArrayList<>();

        for (TranslationSegmentDto segment : segments) {
            filled.add(new TranslationSegmentDto(
                    segment.seqNo(),
                    segment.chineseText(),
                    segment.thaiText(),
                    segment.romanization(),
                    audioAssetService.findExistingAudioUrl(
                            segment.thaiText(), SpeechLanguageEnum.TH).orElse(null),
                    audioAssetService.findExistingAudioUrl(
                            segment.chineseText(), SpeechLanguageEnum.ZH).orElse(null)));
        }

        return filled;
    }

    /**
     * 說法從單字庫撈 —— 那裡就是它們的家。
     * 只有單字查詢才有說法，句子查詢撈出來會是空的（因為句子不是一個詞）。
     */
    private List<TranslationVariantDto> cachedVariants(String chineseText) {
        List<VocabularyDto> words = vocabularyRepository.findByChineseText(chineseText);

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
