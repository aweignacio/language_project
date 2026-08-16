package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  TranslationPersistenceService 負責把翻譯的結果寫進資料庫。
 *
 *  ★ 2026-08-16 起它有三個入口，對應使用者的三個動作：
 *
 *        你按下查詢        → persist              → translation_query
 *        你點「逐詞拆解」  → persistSegmentation  → translation_segment ＋ vocabulary
 *        你點「各種說法」  → persistVariants      → vocabulary
 *
 *    這裡測的是後兩個 —— persist 只是把幾個欄位塞進一個實體再存起來，
 *    沒有任何判斷可測。
 *
 *  它有一個很容易被忽略的責任：★ 補齊欄位 ★
 *
 *  情境是這樣的：
 *
 *      第 1 天  你查「我想喝酒」
 *               → 逐詞的「我 → ฉัน」被沉澱進單字庫，
 *                 但翻句子時模型沒被要求給性別與禮貌，那三欄是空的
 *
 *      第 3 天  你單獨查「我」
 *               → 模型這次給了完整的三種說法，含性別與禮貌
 *               → ★ ฉัน 那一列已經存在了，如果只是「已存在就跳過」，
 *                   它的三個欄位會永遠是空的
 *
 *  所以規則是「已存在但欄位是空的 → 補上；已經有值 → 不覆蓋」。
 *
 * ── 哪些東西被換成假的 ──────────────────────────────────────────────────
 *
 *  兩個 Repository 全部換成假的。這裡要測的是「寫入時的判斷邏輯」，
 *  不是資料庫本身 —— 資料庫的行為（唯一鍵）在 Repository 測試裡測過了。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  測試一  各種說法要各寫一列，性別與禮貌要跟著存
 *  測試二  ★已存在但欄位為空的說法，要被補齊（漏了這條，欄位永遠是空的）
 *  測試三  已存在且欄位有值的說法，不可被覆蓋（那是歷史，不該改寫）
 *  測試四  ★逐詞拆解要「同時」寫 segment 和單字庫
 *          （拆方法時最容易漏掉後者，而且畫面上完全看不出來）
 */

import com.tim.language_project.client.model.TranslationVariant;
import com.tim.language_project.client.model.TranslationWord;
import com.tim.language_project.entity.TranslationSegment;
import com.tim.language_project.entity.Vocabulary;
import com.tim.language_project.enums.GenderUsageEnum;
import com.tim.language_project.enums.PolitenessEnum;
import com.tim.language_project.enums.VocabularySourceTypeEnum;
import com.tim.language_project.repository.TranslationSegmentRepository;
import com.tim.language_project.repository.VocabularyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationPersistenceServiceTest {

    @Mock
    private TranslationSegmentRepository translationSegmentRepository;

    @Mock
    private VocabularyRepository vocabularyRepository;

    @InjectMocks
    private TranslationPersistenceService translationPersistenceService;

    @Test
    @DisplayName("單字的多個說法應各寫入一列並帶上標籤")
    void shouldPersistEachVariantAsItsOwnRow() {
        when(vocabularyRepository.findAllByChineseTextIn(anyCollection()))
                .thenReturn(List.of());

        translationPersistenceService.persistVariants("我", "我", variantsOfMe());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Vocabulary>> captor = ArgumentCaptor.forClass(List.class);
        verify(vocabularyRepository).saveAll(captor.capture());

        List<Vocabulary> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved).extracting(Vocabulary::getThaiText)
                .containsExactlyInAnyOrder("ผม", "ฉัน", "กู");
        assertThat(saved).allMatch(entry -> Objects.nonNull(entry.getGenderUsage()));
    }

    /*
     * ★ 這個測試防的是「沉澱過的詞永遠補不齊」。
     *   沒有它，你先查過句子的那些高頻詞（我、你、他）
     *   會永遠停在沒有性別標籤的狀態，而那正是這次改版最想解決的詞。
     */
    @Test
    @DisplayName("已存在但標籤為空的說法應被補齊")
    void shouldFillLabelsOnExistingRowWithNullLabels() {

        Vocabulary existing = new Vocabulary();
        existing.setChineseText("我");
        existing.setThaiText("ฉัน");
        existing.setRomanization("chǎn");
        existing.setSourceType(VocabularySourceTypeEnum.SEGMENT);

        when(vocabularyRepository.findAllByChineseTextIn(anyCollection()))
                .thenReturn(List.of(existing));

        translationPersistenceService.persistVariants("我", "我", variantsOfMe());

        assertThat(existing.getGenderUsage()).isEqualTo(GenderUsageEnum.FEMALE);
        assertThat(existing.getPoliteness()).isEqualTo(PolitenessEnum.FORMAL);
        assertThat(existing.getNote()).isEqualTo("女生自稱");

        // source_type 是歷史，不可被改寫
        assertThat(existing.getSourceType()).isEqualTo(VocabularySourceTypeEnum.SEGMENT);
    }

    @Test
    @DisplayName("已存在且標籤有值的說法不可被覆蓋")
    void shouldNotOverwriteExistingLabels() {

        Vocabulary existing = new Vocabulary();
        existing.setChineseText("我");
        existing.setThaiText("ฉัน");
        existing.setRomanization("chǎn");
        existing.setGenderUsage(GenderUsageEnum.BOTH);
        existing.setPoliteness(PolitenessEnum.CASUAL);
        existing.setNote("原本就有的說明");
        existing.setSourceType(VocabularySourceTypeEnum.DIRECT);

        when(vocabularyRepository.findAllByChineseTextIn(anyCollection()))
                .thenReturn(List.of(existing));

        translationPersistenceService.persistVariants("我", "我", variantsOfMe());

        assertThat(existing.getGenderUsage()).isEqualTo(GenderUsageEnum.BOTH);
        assertThat(existing.getNote()).isEqualTo("原本就有的說明");
    }

    /*
     * ★ 這個測試防的是「persist 拆成三個方法之後，逐詞那條路被漏掉」。
     *
     *   2026-08-16 把原本一個 persist 拆成 persist／persistSegmentation／
     *   persistVariants。拆的時候最容易出的錯，是逐詞只寫進 translation_segment
     *   卻忘了沉澱進單字庫 —— 症狀是畫面完全正常（拆解看得到），
     *   但單字庫永遠長不大，而使用者是拿單字庫來背單字的。
     */
    @Test
    @DisplayName("逐詞拆解應同時寫入 segment 與單字庫")
    void shouldPersistSegmentsAndVocabulary() {
        when(vocabularyRepository.findAllByChineseTextIn(anyCollection()))
                .thenReturn(List.of());

        translationPersistenceService.persistSegmentation(1L, "我想喝酒", List.of(
                new TranslationWord("我", "ผม", "pǒm"),
                new TranslationWord("想", "อยาก", "yàak")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TranslationSegment>> segmentCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(translationSegmentRepository).saveAll(segmentCaptor.capture());

        List<TranslationSegment> segments = segmentCaptor.getValue();
        assertThat(segments).hasSize(2);
        // seq_no 從 1 開始遞增，前端就是照這個順序排版的
        assertThat(segments).extracting(TranslationSegment::getSeqNo)
                .containsExactly(1, 2);
        assertThat(segments).allMatch(segment -> Objects.equals(segment.getQueryId(), 1L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Vocabulary>> vocabularyCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(vocabularyRepository).saveAll(vocabularyCaptor.capture());

        List<Vocabulary> vocabulary = vocabularyCaptor.getValue();
        assertThat(vocabulary).hasSize(2);
        // 這兩個詞是從句子拆出來的，不是使用者直接查的
        assertThat(vocabulary).allMatch(entry -> Objects.equals(
                entry.getSourceType(), VocabularySourceTypeEnum.SEGMENT));
    }

    /**
     * 「我」的三種說法。
     * 2026-08-16 起這些是使用者點了「各種說法」才拿到的獨立結果，
     * 不再跟整句翻譯綁在同一個回應裡。
     */
    private List<TranslationVariant> variantsOfMe() {
        return List.of(
                new TranslationVariant("ผม", "pǒm",
                        GenderUsageEnum.MALE, PolitenessEnum.FORMAL, "男生自稱"),
                new TranslationVariant("ฉัน", "chǎn",
                        GenderUsageEnum.FEMALE, PolitenessEnum.FORMAL, "女生自稱"),
                new TranslationVariant("กู", "guu",
                        GenderUsageEnum.BOTH, PolitenessEnum.RUDE, "很不客氣"));
    }
}
