package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案在測什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測 TranslationService —— 整個查詢的「總指揮」。
 *
 *  前面幾支測試各測一個零件（翻譯、語音、記帳、寫入），這支測的是
 *  「順序與判斷」：什麼時候該查快取、什麼時候該花錢、什麼時候該擋下來、
 *  出事了怎麼辦。
 *
 *  ⚠ 不連資料庫、不連 OpenAI。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  哪些東西被換成假的，哪些是真的
 * ══════════════════════════════════════════════════════════════════════════
 *
 *    translationQueryRepository      假的 → 決定「快取有沒有命中」
 *    translationSegmentRepository    假的 → 快取命中時給逐詞資料
 *    vocabularyRepository            假的 → 快取命中時給多重說法
 *    translationClient               假的 → 不會真的呼叫 OpenAI
 *    audioAssetService               假的 → ★會花錢的那一個，絕不能真的跑
 *    translationPersistenceService   假的 → 不會真的寫資料庫
 *
 *    languageDetector          ★真的★ → 它只看字串本身，不連任何東西，
 *                                        用真的比較貼近實際行為（@Spy）
 *    audioStorageProperties    ★真的★ → 它就是一包設定值，預設 autoGenerate = [TH]
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  每個測試各自在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *    一  快取命中            防：★重複付費★，查過的東西又去問一次 OpenAI
 *    二  輸入前後有空白      防：「我想喝酒」和「 我想喝酒 」在資料庫變成兩筆，
 *                              等於同一句話付兩次錢
 *    三  空白輸入            防：空字串送去 OpenAI，白花錢
 *    四  超過 100 字         防：欄位只有 NVARCHAR(100)，寫入時才爆炸，
 *                              而錢已經先花掉了
 *    五  語音失敗            防：★聲音失敗把整個翻譯一起拖垮★
 *    六  AI 說翻不出來       防：★編造的詞被永久寫進快取與單字庫★
 *    七  兩人同時查同一句    防：後到的那個撞唯一鍵爆掉，使用者看到 500
 *    八  ★★單字庫捷徑不可回來★★  防：多重說法功能整個失效（見該測試說明）
 *    九  查快取要帶性別      防：切到女生看到男生的講法
 *    十  輸入泰文忽略性別    防：同一句泰文被翻譯兩次，白花一次錢
 *    十一 不自動生中文音檔   防：每次查詢都多等一兩秒去生沒人要聽的東西
 */

import com.tim.language_project.client.TranslationClient;
import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.client.model.TranslationVariant;
import com.tim.language_project.client.model.TranslationWord;
import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.dto.response.TranslationQueryDto;
import com.tim.language_project.dto.response.TranslationResponseDto;
import com.tim.language_project.dto.response.TranslationSegmentDto;
import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.enums.GenderUsageEnum;
import com.tim.language_project.enums.PolitenessEnum;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.repository.TranslationQueryRepository;
import com.tim.language_project.repository.TranslationSegmentRepository;
import com.tim.language_project.repository.VocabularyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * LENIENT 是因為「音檔網址」這類共用的假動作不是每個測試都會用到，
 * 嚴格模式會為此報「多餘的 stub」。這裡要測的是流程判斷，不是 stub 用得剛不剛好。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TranslationServiceTest {

    @Mock
    private TranslationQueryRepository translationQueryRepository;

    @Mock
    private TranslationSegmentRepository translationSegmentRepository;

    @Mock
    private VocabularyRepository vocabularyRepository;

    @Mock
    private TranslationClient translationClient;

    /** ★ 會花錢的那一個。換成假的才能用 verify 檢查「這次有沒有花錢」。 */
    @Mock
    private AudioAssetService audioAssetService;

    @Mock
    private TranslationPersistenceService translationPersistenceService;

    /** 真的。它只看字串本身，不連資料庫也不連網路。 */
    @Spy
    private LanguageDetector languageDetector = new LanguageDetector();

    /** 真的。就是一包設定值，預設 autoGenerate 只有 TH。 */
    @Spy
    private AudioStorageProperties audioStorageProperties = new AudioStorageProperties();

    @InjectMocks
    private TranslationService translationService;

    /*
     * ═══ 測試一：查過的東西不可以再花一次錢 ═════════════════════════════
     */
    @Test
    @DisplayName("快取命中時不得呼叫外部服務")
    void shouldNotCallExternalServicesWhenCacheHits() {
        when(translationQueryRepository.findByKey(
                "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(Optional.of(new TranslationQueryDto(
                        1L, "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                        "我想喝酒", "ผมอยากดื่มเหล้าครับ", "pǒm yàak dùuem lâo khráp")));
        when(translationSegmentRepository.findByQueryIdOrderBySeqNo(1L))
                .thenReturn(List.of(new TranslationSegmentDto(
                        1, "我", "ผม", "pǒm", null, null)));
        when(audioAssetService.findExistingAudioUrl(anyString(), any()))
                .thenReturn(Optional.of("/audio/th/a3f9c2.mp3"));

        TranslationResponseDto response =
                translationService.translate("我想喝酒", SpeakerGenderEnum.MALE);

        assertThat(response.fromCache()).isTrue();
        assertThat(response.thaiText()).isEqualTo("ผมอยากดื่มเหล้าครับ");
        assertThat(response.thaiAudioUrl()).isEqualTo("/audio/th/a3f9c2.mp3");

        // ★ 這兩行是這個測試的重點：一毛錢都不能花
        verify(translationClient, never()).translate(anyString(), any(), any());
        verify(audioAssetService, never()).resolveAudioUrl(anyString(), any());
    }

    /*
     * ═══ 測試二：前後空白要先去掉再查快取 ═══════════════════════════════
     *
     * 不去掉的話，「我想喝酒」和「 我想喝酒 」會被當成兩句不同的話，
     * 各自存一筆快取、各自付一次翻譯和語音的錢。
     */
    @Test
    @DisplayName("輸入前後空白應去除後再查快取")
    void shouldTrimInputBeforeLookup() {
        when(translationQueryRepository.findByKey(
                "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(Optional.of(new TranslationQueryDto(
                        1L, "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                        "我想喝酒", "ผมอยากดื่มเหล้าครับ", "pǒm")));
        when(translationSegmentRepository.findByQueryIdOrderBySeqNo(1L))
                .thenReturn(List.of());
        when(vocabularyRepository.findByChineseText("我想喝酒")).thenReturn(List.of());

        TranslationResponseDto response =
                translationService.translate("  我想喝酒  ", SpeakerGenderEnum.MALE);

        assertThat(response.sourceText()).isEqualTo("我想喝酒");
    }

    /*
     * ═══ 測試三：空白輸入直接擋掉，不花錢 ═══════════════════════════════
     */
    @Test
    @DisplayName("空白輸入應拋出 INPUT_REQUIRED")
    void shouldRejectBlankInput() {
        assertThatThrownBy(() -> translationService.translate("   ", SpeakerGenderEnum.MALE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCodeEnum.INPUT_REQUIRED);

        // 連查資料庫都不必
        verify(translationQueryRepository, never()).findByKey(anyString(), any(), any());
    }

    /*
     * ═══ 測試四：超過欄位長度就擋，不要等到寫入才爆 ═════════════════════
     */
    @Test
    @DisplayName("超過 100 字應拋出 INPUT_TOO_LONG")
    void shouldRejectTooLongInput() {
        String tooLong = "字".repeat(101);

        assertThatThrownBy(() -> translationService.translate(tooLong, SpeakerGenderEnum.MALE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCodeEnum.INPUT_TOO_LONG);

        verify(translationClient, never()).translate(anyString(), any(), any());
    }

    /*
     * ═══ 測試五：語音失敗，翻譯照樣要給使用者 ═══════════════════════════
     */
    @Test
    @DisplayName("語音失敗時仍應回傳翻譯結果，音檔為 null")
    void shouldReturnTranslationWhenSpeechFails() {
        when(translationQueryRepository.findByKey(
                "水", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(Optional.empty());
        when(translationClient.translate(
                "水", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(singleWordResult());
        when(audioAssetService.resolveAudioUrl(anyString(), any())).thenReturn(Optional.empty());
        when(audioAssetService.findExistingAudioUrl(anyString(), any()))
                .thenReturn(Optional.empty());

        TranslationResponseDto response =
                translationService.translate("水", SpeakerGenderEnum.MALE);

        assertThat(response.fromCache()).isFalse();
        assertThat(response.thaiText()).isEqualTo("น้ำ");

        // ★ 音檔是 null，但翻譯結果完整回傳了
        assertThat(response.thaiAudioUrl()).isNull();
        assertThat(response.segments()).hasSize(1);

        // 而且照樣存進資料庫 —— 沒有音檔不代表這次查詢不值得快取
        verify(translationPersistenceService).persist(any(), any(), any(), any());
    }

    /*
     * ═══ 測試六：AI 說翻不出來 —— 絕對不可以存進資料庫 ══════════════════
     *
     * 情境：使用者輸入「嘎逼」這種不存在的詞。
     *
     * 這是整個專案最重要的一道防線。如果讓它存進去：
     *   - 快取會永久記住那個編造的答案，之後每次查都回同一個錯的東西
     *   - 單字庫會被垃圾污染，而使用者是拿它來背單字的
     */
    @Test
    @DisplayName("模型回報無法翻譯時應拋出錯誤，且不得寫入資料庫")
    void shouldRejectUntranslatableInputAndNotPersist() {
        when(translationQueryRepository.findByKey(
                "嘎逼", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(Optional.empty());
        when(translationClient.translate(
                "嘎逼", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(TranslationResult.untranslatable("gpt-test", 95L, 8L));

        assertThatThrownBy(() -> translationService.translate("嘎逼", SpeakerGenderEnum.MALE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCodeEnum.INPUT_UNSUPPORTED_CONTENT);

        // ★ 什麼都不准留下來
        verify(translationPersistenceService, never()).persist(any(), any(), any(), any());
        // 也不該為了一個翻不出來的東西去生語音
        verify(audioAssetService, never()).resolveAudioUrl(anyString(), any());
    }

    /*
     * ═══ 測試七：兩個請求同時查同一句話 ═════════════════════════════════
     *
     * 情境：你連按兩次查詢，或兩個人同時查「水」。
     *
     *     請求 A 查快取 → 沒有 → 去翻譯（要好幾秒）
     *     請求 B 查快取 → 沒有 → 也去翻譯     ← 此時 A 還沒寫完
     *     請求 A 寫入 → 成功
     *     請求 B 寫入 → ★ 撞到唯一鍵 ★
     *
     * 修正前：B 會爆炸，使用者看到 500「系統發生非預期錯誤」。
     * 修正後：B 知道「有人比我快」，改去讀 A 寫好的那筆回傳。
     */
    @Test
    @DisplayName("同時寫入撞唯一鍵時應改讀既有資料，不可讓使用者看到錯誤")
    void shouldFallBackToExistingRowOnConcurrentWrite() {
        // 第一次回空的、第二次回有資料 —— 模擬「另一個請求在這中間寫進去了」
        when(translationQueryRepository.findByKey(
                "水", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new TranslationQueryDto(
                        88L, "水", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                        "水", "น้ำ", "náam")));
        when(translationSegmentRepository.findByQueryIdOrderBySeqNo(88L))
                .thenReturn(List.of(new TranslationSegmentDto(
                        1, "水", "น้ำ", "náam", null, null)));
        when(vocabularyRepository.findByChineseText("水")).thenReturn(List.of());
        when(translationClient.translate(
                "水", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(singleWordResult());
        when(audioAssetService.resolveAudioUrl(anyString(), any()))
                .thenReturn(Optional.of("/audio/th/xyz.mp3"));
        when(audioAssetService.findExistingAudioUrl(anyString(), any()))
                .thenReturn(Optional.of("/audio/th/xyz.mp3"));

        // 寫入時撞唯一鍵
        when(translationPersistenceService.persist(any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        TranslationResponseDto response =
                translationService.translate("水", SpeakerGenderEnum.MALE);

        // ★ 沒有丟出例外，而且回的是別人寫好的那筆
        assertThat(response.thaiText()).isEqualTo("น้ำ");
        assertThat(response.fromCache()).isTrue();
        assertThat(response.segments()).hasSize(1);
    }

    /*
     * ═══ 測試八 ★★ 這個測試是整個改版的命脈 ★★ ═══════════════════════
     *
     * 舊的程式碼有一段「省錢捷徑」：整段輸入剛好是單字庫裡有的詞時，
     * 直接拿單字庫的答案回傳，不呼叫 AI。
     *
     * 那段捷徑會讓多重說法功能完全失效：
     *
     *   第 1 天  你查「我想喝酒」→「我 → ฉัน」被沉澱進單字庫
     *   第 3 天  你單獨查「我」  → 捷徑看到單字庫有「我」，直接回 ฉัน
     *                            → 永遠不會去問 AI 要 ผม 和 กู
     *
     * 這個測試把那條捷徑釘死：單字庫裡明明有「我」，仍然必須呼叫 AI。
     *
     * 如果有人日後為了「省錢」把捷徑加回來，這裡會亮紅燈。
     */
    @Test
    @DisplayName("單字庫已有該詞時仍必須呼叫翻譯服務")
    void shouldStillCallTranslationClientWhenVocabularyAlreadyHasTheWord() {
        when(translationQueryRepository.findByKey(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(Optional.empty());
        // 單字庫裡明明已經有「我」了
        when(vocabularyRepository.findByChineseText("我"))
                .thenReturn(List.of(new VocabularyDto(
                        7L, "我", "ฉัน", "chǎn", null, null, null)));
        when(translationClient.translate(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(singleWordResultWithVariants());
        when(audioAssetService.resolveAudioUrl(anyString(), any()))
                .thenReturn(Optional.of("/audio/th/a1b2c3.mp3"));
        when(audioAssetService.findExistingAudioUrl(anyString(), any()))
                .thenReturn(Optional.empty());

        TranslationResponseDto response =
                translationService.translate("我", SpeakerGenderEnum.MALE);

        // ★ 重點：AI 一定要被呼叫，不可以被單字庫擋下來
        verify(translationClient).translate(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE);
        assertThat(response.variants()).hasSize(3);
    }

    /*
     * ═══ 測試九：查快取時性別必須一起帶入 ═══════════════════════════════
     *
     * ★ 決策 7：同一句話的男版與女版要分開查快取。
     *   共用的話，你切到女生會看到男生的講法。
     */
    @Test
    @DisplayName("查快取時性別必須一起帶入")
    void shouldIncludeGenderWhenLookingUpCache() {
        when(translationQueryRepository.findByKey(
                "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.FEMALE))
                .thenReturn(Optional.of(new TranslationQueryDto(
                        1L, "我想喝酒", TranslationDirectionEnum.ZH_TO_TH,
                        SpeakerGenderEnum.FEMALE, "我想喝酒",
                        "ฉันอยากดื่มเหล้าค่ะ", "chǎn yàak dùuem lâo khâ")));
        when(translationSegmentRepository.findByQueryIdOrderBySeqNo(1L))
                .thenReturn(List.of());
        when(vocabularyRepository.findByChineseText("我想喝酒")).thenReturn(List.of());
        when(audioAssetService.findExistingAudioUrl(anyString(), any()))
                .thenReturn(Optional.of("/audio/th/a1b2c3.mp3"));

        TranslationResponseDto response =
                translationService.translate("我想喝酒", SpeakerGenderEnum.FEMALE);

        assertThat(response.fromCache()).isTrue();
        verify(translationClient, never()).translate(anyString(), any(), any());
    }

    /*
     * ═══ 測試十：輸入泰文時性別要被忽略 ═════════════════════════════════
     *
     * 泰翻中不分性別。就算前端照樣送了性別過來，也要存成 null，
     * 否則同一句泰文會因為性別不同被翻譯兩次，白花一次錢。
     */
    @Test
    @DisplayName("輸入泰文時性別應被忽略並存為 null")
    void shouldIgnoreGenderWhenInputIsThai() {
        when(translationQueryRepository.findByKey(
                "ผมอยากดื่มเหล้า", TranslationDirectionEnum.TH_TO_ZH, null))
                .thenReturn(Optional.empty());
        when(translationClient.translate(
                "ผมอยากดื่มเหล้า", TranslationDirectionEnum.TH_TO_ZH, null))
                .thenReturn(thaiToChineseResult());
        when(audioAssetService.resolveAudioUrl(anyString(), any()))
                .thenReturn(Optional.of("/audio/th/a1b2c3.mp3"));
        when(audioAssetService.findExistingAudioUrl(anyString(), any()))
                .thenReturn(Optional.empty());

        translationService.translate("ผมอยากดื่มเหล้า", SpeakerGenderEnum.MALE);

        verify(translationClient).translate(
                "ผมอยากดื่มเหล้า", TranslationDirectionEnum.TH_TO_ZH, null);
        verify(translationPersistenceService).persist(
                eq("ผมอยากดื่มเหล้า"), eq(TranslationDirectionEnum.TH_TO_ZH),
                eq(null), any(TranslationResult.class));
    }

    /*
     * ═══ 測試十一：輸入中文時，不得自動產生中文音檔 ═════════════════════
     *
     * 你自己打「我」進來，中文那面就是你剛打的字 ——
     * 唸給你聽沒有任何價值，卻要多打一次 OpenAI、每次查詢多等一兩秒。
     *
     * ★ 注意這一條只擋「中翻泰」。反方向要生，見測試十二。
     */
    @Test
    @DisplayName("輸入中文時不得自動產生中文音檔")
    void shouldNotAutoGenerateChineseAudio() {
        when(translationQueryRepository.findByKey(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(Optional.empty());
        when(translationClient.translate(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(singleWordResultWithVariants());
        when(audioAssetService.resolveAudioUrl(anyString(), any()))
                .thenReturn(Optional.of("/audio/th/a1b2c3.mp3"));
        when(audioAssetService.findExistingAudioUrl(anyString(), any()))
                .thenReturn(Optional.empty());

        TranslationResponseDto response =
                translationService.translate("我", SpeakerGenderEnum.MALE);

        assertThat(response.chineseAudioUrl()).isNull();
        verify(audioAssetService, never())
                .resolveAudioUrl(anyString(), eq(SpeechLanguageEnum.ZH));
    }

    /*
     * ═══ 測試十二：輸入泰文時，中文音檔要自動產生 ═══════════════════════
     *
     * ★ 這一條是測試十一的反面，兩個合起來才是完整的規則。
     *
     *   你貼一段看不懂的泰文進來，中文那面是「翻譯結果」——
     *   你需要聽它確認自己有沒有理解對，所以要生。
     *
     *   漏掉這條的話，泰翻中的中文永遠是灰色的鍵，
     *   而且不會有任何錯誤訊息，只會讓人以為「這個功能壞了」。
     */
    @Test
    @DisplayName("輸入泰文時應自動產生中文音檔")
    void shouldAutoGenerateChineseAudioWhenInputIsThai() {
        when(translationQueryRepository.findByKey(
                "ผมอยากดื่มเหล้า", TranslationDirectionEnum.TH_TO_ZH, null))
                .thenReturn(Optional.empty());
        when(translationClient.translate(
                "ผมอยากดื่มเหล้า", TranslationDirectionEnum.TH_TO_ZH, null))
                .thenReturn(thaiToChineseResult());
        when(audioAssetService.resolveAudioUrl("ผมอยากดื่มเหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.of("/audio/th/a1b2c3.mp3"));
        when(audioAssetService.resolveAudioUrl("我想喝酒", SpeechLanguageEnum.ZH))
                .thenReturn(Optional.of("/audio/zh/d4e5f6.mp3"));
        when(audioAssetService.findExistingAudioUrl(anyString(), any()))
                .thenReturn(Optional.empty());

        TranslationResponseDto response =
                translationService.translate("ผมอยากดื่มเหล้า", SpeakerGenderEnum.MALE);

        // ★ 重點：中文那面真的去合成了，而且網址落在 zh/ 資料夾
        assertThat(response.chineseAudioUrl()).isEqualTo("/audio/zh/d4e5f6.mp3");
        verify(audioAssetService).resolveAudioUrl("我想喝酒", SpeechLanguageEnum.ZH);

        // 泰文那面照樣要生 —— 那才是你要學的發音
        assertThat(response.thaiAudioUrl()).isEqualTo("/audio/th/a1b2c3.mp3");
    }

    /** 一個沒有多重說法的單字結果（模擬「大部分的詞就只有一種說法」）。 */
    private TranslationResult singleWordResult() {
        return new TranslationResult(
                "水", "น้ำ", "náam",
                List.of(new TranslationWord("水", "น้ำ", "náam")),
                List.of(),
                "gpt-test", 10L, 5L, true);
    }

    /** 「我」的三種說法。 */
    private TranslationResult singleWordResultWithVariants() {
        return new TranslationResult(
                "我", "ผม", "pǒm",
                List.of(new TranslationWord("我", "ผม", "pǒm")),
                List.of(
                        new TranslationVariant("ผม", "pǒm",
                                GenderUsageEnum.MALE, PolitenessEnum.FORMAL, "男生自稱"),
                        new TranslationVariant("ฉัน", "chǎn",
                                GenderUsageEnum.FEMALE, PolitenessEnum.FORMAL, "女生自稱"),
                        new TranslationVariant("กู", "guu",
                                GenderUsageEnum.BOTH, PolitenessEnum.RUDE, "很不客氣")),
                "gpt-test", 100L, 50L, true);
    }

    /** 泰翻中的結果：chineseText 是翻出來的，thaiText 是使用者的輸入。 */
    private TranslationResult thaiToChineseResult() {
        return new TranslationResult(
                "我想喝酒", "ผมอยากดื่มเหล้า", "pǒm yàak dùuem lâo",
                List.of(new TranslationWord("我", "ผม", "pǒm")),
                List.of(),
                "gpt-test", 80L, 40L, true);
    }
}
