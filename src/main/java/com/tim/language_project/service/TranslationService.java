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
 * ── 第 1 步｜Controller 收到請求後呼叫這裡（Task 9 才做）────────────────
 *
 *        translationService.translate("  我想喝酒  ");
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
 * ── 第 3 步｜第一道省錢關卡：查詢快取 ───────────────────────────────────
 *
 *        translationQueryRepository.findBySourceText("我想喝酒")
 *
 *        有 → 直接組回應回傳，fromCache = true，★一毛錢都不花★
 *        沒有 → 往下走
 *
 * ── 第 4 步｜第二道省錢關卡：單字庫（resolveTranslation）────────────────
 *
 *        使用者查的如果剛好是「一個已經沉澱過的詞」（例如「水」），
 *        本地就有答案了，不必再問 OpenAI。
 *
 *        vocabularyRepository.findByChineseText("水")
 *          有 → 自己組一個 TranslationResult，★省下翻譯費用★
 *          沒有 → 才真的呼叫 translationClient.translate(...)
 *
 * ── 第 5 步｜第三道關卡：模型說「這翻不出來」───────────────────────────
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
 * ── 第 6 步｜生語音 ─────────────────────────────────────────────────────
 *
 *        String audioFile = speechClient.synthesize(result.thaiText()).orElse(null);
 *                                                    ↑ 送泰文進去，不是中文
 *
 *    ★ 這一行就是「翻譯」和「語音」兩個零件的接點：
 *      左邊是第 4 步的產物，右邊是語音的輸入。
 *
 *    失敗回傳空的 Optional → audioFile 是 null → 前端不顯示播放鍵，
 *    但翻譯結果照樣給使用者。聲音失敗不該讓整個查詢失敗。
 *
 * ── 第 7 步｜寫進資料庫 ─────────────────────────────────────────────────
 *
 *        translationPersistenceService.persist(sourceText, result, audioFile);
 *
 *    寫入快取、逐詞、單字庫三張表，綁在同一個交易裡（見該檔說明）。
 *
 * ── 第 8 步｜組回應給前端 ───────────────────────────────────────────────
 *
 *        {
 *          "sourceText": "我想喝酒",
 *          "thaiText": "ฉันอยากดื่มเหล้า",
 *          "romanization": "chǎn yàak dùuem lâo",
 *          "audioUrl": "/audio/a3f9c2b81e47.mp3",
 *          "fromCache": false,
 *          "segments": [ {"seqNo":1,"chineseText":"我",...}, ... ]
 *        }
 *
 *    檔名要在這裡加上 "/audio/" 前綴變成網址，前端才能直接放進 <audio src>。
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

import com.tim.language_project.client.SpeechClient;
import com.tim.language_project.client.TranslationClient;
import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.client.model.TranslationWord;
import com.tim.language_project.dto.response.TranslationQueryDto;
import com.tim.language_project.dto.response.TranslationResponseDto;
import com.tim.language_project.dto.response.TranslationSegmentDto;
import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.enums.SpeechLanguageEnum;
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

    private static final String AUDIO_URL_PREFIX = "/audio/";

    /** 單字庫命中時填入的模型名稱，用來標示「這次沒有真的呼叫外部服務」。 */
    private static final String VOCABULARY_SOURCE = "vocabulary-cache";

    private final TranslationQueryRepository translationQueryRepository;

    private final TranslationSegmentRepository translationSegmentRepository;

    private final VocabularyRepository vocabularyRepository;

    private final TranslationClient translationClient;

    private final SpeechClient speechClient;

    private final TranslationPersistenceService translationPersistenceService;

    /**
     * 查一句中文，回傳泰文、拼音、逐詞對照與音檔網址。
     * 外部呼叫刻意跑在交易之外，只有最後的寫入才進交易 —— 理由見檔案開頭。
     */
    public TranslationResponseDto translate(String rawInput) {
        String sourceText = validateAndNormalize(rawInput);

        Optional<TranslationQueryDto> cached =
                translationQueryRepository.findBySourceText(sourceText);

        if (cached.isPresent()) {
            return buildCachedResponse(cached.get());
        }

        TranslationResult result = resolveTranslation(sourceText);

        if (!result.translatable()) {
            // 模型自己說翻不出來。當場停手，不生語音也不寫資料庫，
            // 免得一個編造的詞被永久留在快取與單字庫裡。
            log.warn("model reported untranslatable input, length={}", sourceText.length());
            throw new BusinessException(ErrorCodeEnum.INPUT_UNSUPPORTED_CONTENT);
        }

        // 暫時補上語言參數讓專案編譯得過，整段流程在 Task 13 會全面改寫。
        String audioFile = speechClient
                .synthesize(result.thaiText(), SpeechLanguageEnum.TH)
                .orElse(null);

        try {
            translationPersistenceService.persist(sourceText, result, audioFile);
        } catch (DataIntegrityViolationException exception) {
            // 撞到唯一鍵，代表在我們翻譯的這幾秒內，另一個請求已經把同一句寫進去了。
            // 這不是錯誤，是「有人比我們快」。改讀他寫好的那筆回傳，
            // 使用者完全不會發現發生過這件事。
            log.warn("concurrent write detected for the same input, falling back to the cached row");

            return translationQueryRepository.findBySourceText(sourceText)
                    .map(this::buildCachedResponse)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCodeEnum.DATA_PERSIST_FAILED, exception));
        }

        return new TranslationResponseDto(
                sourceText,
                result.thaiText(),
                result.romanization(),
                toAudioUrl(audioFile),
                false,
                toSegmentDtos(result.words()));
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

        return new TranslationResponseDto(
                cached.sourceText(),
                cached.thaiText(),
                cached.romanization(),
                toAudioUrl(cached.audioFile()),
                true,
                segments);
    }

    /**
     * 整句輸入剛好是一個已知單字時，直接用單字庫的答案，省下一次付費呼叫。
     */
    private TranslationResult resolveTranslation(String sourceText) {
        Optional<VocabularyDto> knownWord = vocabularyRepository.findByChineseText(sourceText);

        if (knownWord.isPresent()) {
            VocabularyDto word = knownWord.get();

            return new TranslationResult(
                    word.thaiText(),
                    word.romanization(),
                    List.of(new TranslationWord(
                            word.chineseText(), word.thaiText(), word.romanization())),
                    VOCABULARY_SOURCE, 0L, 0L, true);
        }

        return translationClient.translate(sourceText);
    }

    private List<TranslationSegmentDto> toSegmentDtos(List<TranslationWord> words) {
        List<TranslationSegmentDto> segments = new ArrayList<>();
        int seqNo = 1;

        for (TranslationWord word : words) {
            segments.add(new TranslationSegmentDto(
                    seqNo++, word.chineseText(), word.thaiText(), word.romanization()));
        }

        return segments;
    }

    private String toAudioUrl(String audioFile) {
        return ObjectUtils.isEmpty(audioFile) ? null : AUDIO_URL_PREFIX + audioFile;
    }
}
