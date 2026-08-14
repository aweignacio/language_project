package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案在測什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測 TranslationService —— 整個查詢的「總指揮」。
 *
 *  前面幾支測試各測一個零件（翻譯、語音、記帳），這支測的是「順序與判斷」：
 *  什麼時候該查快取、什麼時候該花錢、什麼時候該擋下來、出事了怎麼辦。
 *
 *  ⚠ 不連資料庫、不連 OpenAI。四個依賴全部換成假的。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你打指令到看見 Tests run: 8
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在終端機打指令 ───────────────────────────────────────────
 *
 *        .\mvnw.cmd -B test "-Dtest=TranslationServiceTest"
 *
 * ── 第 2 步｜Mockito 把六個依賴全部換成假的 ─────────────────────────────
 *
 *        translationQueryRepository      假的 → 決定「快取有沒有命中」
 *        translationSegmentRepository    假的 → 快取命中時給逐詞資料
 *        vocabularyRepository            假的 → 決定「單字庫有沒有這個詞」
 *        translationClient               假的 → 不會真的呼叫 OpenAI
 *        speechClient                    假的 → 不會真的生音檔
 *        translationPersistenceService   假的 → 不會真的寫資料庫
 *
 *    ★ 全部是假的，所以每個測試可以「指定劇本」：
 *      「這次快取沒命中、翻譯回這個、語音失敗」—— 這種組合在真實環境
 *      很難重現，但這裡一行就設定好了。
 *
 * ── 第 3 步｜以測試一（快取命中）為例 ───────────────────────────────────
 *
 *  ● 布置劇本
 *
 *        when(translationQueryRepository.findBySourceText("我想喝酒"))
 *                .thenReturn(Optional.of(快取資料));
 *
 *  ● 執行
 *
 *        translationService.translate("我想喝酒");
 *
 *  ● 檢查
 *
 *        verify(translationClient, never()).translate(anyString());
 *        verify(speechClient, never()).synthesize(anyString(), any());
 *
 *    ★ 這裡驗的是「沒有做某件事」。
 *      快取命中卻還去呼叫 OpenAI，功能上看不出差別 —— 畫面一樣正確 ——
 *      但每查一次就重複付一次錢。這種錯誤只有測試抓得到。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  八個測試各自在防什麼
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
 *    七  單字庫已有這個詞    防：明明本地就有答案，還去付費問 OpenAI
 *    八  兩人同時查同一句    防：後到的那個撞唯一鍵爆掉，使用者看到 500
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 這張貼紙的作用見開頭第 2 步。
@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private TranslationQueryRepository translationQueryRepository;

    @Mock
    private TranslationSegmentRepository translationSegmentRepository;

    @Mock
    private VocabularyRepository vocabularyRepository;

    @Mock
    private TranslationClient translationClient;

    @Mock
    private SpeechClient speechClient;

    @Mock
    private TranslationPersistenceService translationPersistenceService;

    @InjectMocks
    private TranslationService translationService;

    /*
     * ═══ 測試一：查過的東西不可以再花一次錢 ═════════════════════════════
     */
    @Test
    @DisplayName("快取命中時不得呼叫外部服務")
    void shouldNotCallExternalServicesWhenCacheHits() {
        when(translationQueryRepository.findBySourceText("我想喝酒"))
                .thenReturn(Optional.of(new TranslationQueryDto(
                        1L, "我想喝酒", "ฉันอยากดื่มเหล้า", "chǎn yàak dùuem lâo", "a3f9c2.mp3")));
        when(translationSegmentRepository.findByQueryIdOrderBySeqNo(1L))
                .thenReturn(List.of(new TranslationSegmentDto(1, "我", "ฉัน", "chǎn")));

        TranslationResponseDto response = translationService.translate("我想喝酒");

        assertThat(response.fromCache()).isTrue();
        assertThat(response.thaiText()).isEqualTo("ฉันอยากดื่มเหล้า");

        // 檔名要組成前端可以直接用的網址
        assertThat(response.audioUrl()).isEqualTo("/audio/a3f9c2.mp3");

        // ★ 這兩行是這個測試的重點：一毛錢都不能花
        verify(translationClient, never()).translate(anyString());
        verify(speechClient, never()).synthesize(anyString(), any());
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
        when(translationQueryRepository.findBySourceText("我想喝酒"))
                .thenReturn(Optional.of(new TranslationQueryDto(
                        1L, "我想喝酒", "ฉันอยากดื่มเหล้า", "chǎn", null)));
        when(translationSegmentRepository.findByQueryIdOrderBySeqNo(1L))
                .thenReturn(List.of());

        TranslationResponseDto response = translationService.translate("  我想喝酒  ");

        assertThat(response.sourceText()).isEqualTo("我想喝酒");
    }

    /*
     * ═══ 測試三：空白輸入直接擋掉，不花錢 ═══════════════════════════════
     */
    @Test
    @DisplayName("空白輸入應拋出 INPUT_REQUIRED")
    void shouldRejectBlankInput() {
        assertThatThrownBy(() -> translationService.translate("   "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCodeEnum.INPUT_REQUIRED);

        // 連查資料庫都不必
        verify(translationQueryRepository, never()).findBySourceText(anyString());
    }

    /*
     * ═══ 測試四：超過欄位長度就擋，不要等到寫入才爆 ═════════════════════
     */
    @Test
    @DisplayName("超過 100 字應拋出 INPUT_TOO_LONG")
    void shouldRejectTooLongInput() {
        String tooLong = "字".repeat(101);

        assertThatThrownBy(() -> translationService.translate(tooLong))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCodeEnum.INPUT_TOO_LONG);

        verify(translationClient, never()).translate(anyString());
    }

    /*
     * ═══ 測試五：語音失敗，翻譯照樣要給使用者 ═══════════════════════════
     */
    @Test
    @DisplayName("語音失敗時仍應回傳翻譯結果，音檔為 null")
    void shouldReturnTranslationWhenSpeechFails() {
        when(translationQueryRepository.findBySourceText("水")).thenReturn(Optional.empty());
        when(vocabularyRepository.findByChineseText("水")).thenReturn(Optional.empty());
        when(translationClient.translate("水")).thenReturn(new TranslationResult(
                "น้ำ", "náam",
                List.of(new TranslationWord("水", "น้ำ", "náam")),
                "gpt-test", 10L, 5L, true));
        when(speechClient.synthesize("น้ำ", SpeechLanguageEnum.TH)).thenReturn(Optional.empty());

        TranslationResponseDto response = translationService.translate("水");

        assertThat(response.fromCache()).isFalse();
        assertThat(response.thaiText()).isEqualTo("น้ำ");

        // ★ 音檔是 null，但翻譯結果完整回傳了
        assertThat(response.audioUrl()).isNull();
        assertThat(response.segments()).hasSize(1);

        // 而且照樣存進資料庫 —— 沒有音檔不代表這次查詢不值得快取
        verify(translationPersistenceService).persist(any(), any(), any());
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
        when(translationQueryRepository.findBySourceText("嘎逼")).thenReturn(Optional.empty());
        when(vocabularyRepository.findByChineseText("嘎逼")).thenReturn(Optional.empty());
        when(translationClient.translate("嘎逼"))
                .thenReturn(TranslationResult.untranslatable("gpt-test", 95L, 8L));

        assertThatThrownBy(() -> translationService.translate("嘎逼"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCodeEnum.INPUT_UNSUPPORTED_CONTENT);

        // ★ 這三行是重點：什麼都不准留下來
        verify(translationPersistenceService, never()).persist(any(), any(), any());
        // 也不該為了一個翻不出來的東西去生語音
        verify(speechClient, never()).synthesize(anyString(), any());
    }

    /*
     * ═══ 測試八：兩個請求同時查同一句話 ═════════════════════════════════
     *
     * 情境：你連按兩次查詢，或兩個人同時查「水」。
     *
     *     請求 A 查快取 → 沒有 → 去翻譯（要好幾秒）
     *     請求 B 查快取 → 沒有 → 也去翻譯     ← 此時 A 還沒寫完
     *     請求 A 寫入 → 成功
     *     請求 B 寫入 → ★ 撞到 source_text 的唯一鍵 ★
     *
     * 修正前：B 會爆炸，使用者看到 500「系統發生非預期錯誤」。
     * 修正後：B 知道「有人比我快」，改去讀 A 寫好的那筆回傳。
     *         使用者完全不會發現發生過這件事。
     *
     * 這個測試用兩段式的假動作模擬時間差：
     *     第一次查快取 → 空的（那時 A 還沒寫完）
     *     第二次查快取 → 有了（A 已經寫完）
     */
    @Test
    @DisplayName("同時寫入撞唯一鍵時應改讀既有資料，不可讓使用者看到錯誤")
    void shouldFallBackToExistingRowOnConcurrentWrite() {
        // 第一次回空的、第二次回有資料 —— 模擬「另一個請求在這中間寫進去了」
        when(translationQueryRepository.findBySourceText("水"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new TranslationQueryDto(
                        88L, "水", "น้ำ", "náam", "abc123.mp3")));
        when(translationSegmentRepository.findByQueryIdOrderBySeqNo(88L))
                .thenReturn(List.of(new TranslationSegmentDto(1, "水", "น้ำ", "náam")));
        when(vocabularyRepository.findByChineseText("水")).thenReturn(Optional.empty());
        when(translationClient.translate("水")).thenReturn(new TranslationResult(
                "น้ำ", "náam",
                List.of(new TranslationWord("水", "น้ำ", "náam")),
                "gpt-test", 10L, 5L, true));
        when(speechClient.synthesize("น้ำ", SpeechLanguageEnum.TH)).thenReturn(Optional.of("xyz.mp3"));

        // 寫入時撞唯一鍵
        when(translationPersistenceService.persist(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        TranslationResponseDto response = translationService.translate("水");

        // ★ 沒有丟出例外，而且回的是別人寫好的那筆
        assertThat(response.thaiText()).isEqualTo("น้ำ");
        assertThat(response.audioUrl()).isEqualTo("/audio/abc123.mp3");
        assertThat(response.fromCache()).isTrue();
        assertThat(response.segments()).hasSize(1);
    }

    /*
     * ═══ 測試七：單字庫已經有的詞，不必再問 OpenAI ══════════════════════
     *
     * 使用者查「水」，而「水」之前已經從別的句子沉澱進單字庫了。
     * 這時本地就有答案，直接用，省下一次翻譯費用。
     *
     * 注意語音還是要生 —— 單字庫沒有存音檔（只有查詢快取有）。
     */
    @Test
    @DisplayName("單字庫已有該詞時應直接使用，不呼叫翻譯服務")
    void shouldUseVocabularyInsteadOfCallingTranslation() {
        when(translationQueryRepository.findBySourceText("水")).thenReturn(Optional.empty());
        when(vocabularyRepository.findByChineseText("水"))
                .thenReturn(Optional.of(new VocabularyDto(7L, "水", "น้ำ", "náam")));
        when(speechClient.synthesize("น้ำ", SpeechLanguageEnum.TH)).thenReturn(Optional.of("b1c2d3.mp3"));

        TranslationResponseDto response = translationService.translate("水");

        assertThat(response.thaiText()).isEqualTo("น้ำ");
        assertThat(response.audioUrl()).isEqualTo("/audio/b1c2d3.mp3");

        // ★ 重點：沒有花翻譯的錢
        verify(translationClient, never()).translate(anyString());
    }
}
