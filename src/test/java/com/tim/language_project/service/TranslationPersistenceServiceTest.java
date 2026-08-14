package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  TranslationPersistenceService 負責把一次翻譯的結果寫進三張表。
 *  這次改版讓它多了一個很容易被忽略的責任：★ 補齊欄位 ★
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
 *  三個 Repository 全部換成假的。這裡要測的是「寫入時的判斷邏輯」，
 *  不是資料庫本身 —— 資料庫的行為（唯一鍵）在 Repository 測試裡測過了。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  測試一  單字查詢的多個說法要各寫一列，性別與禮貌要跟著存
 *  測試二  ★已存在但欄位為空的說法，要被補齊（漏了這條，欄位永遠是空的）
 *  測試三  已存在且欄位有值的說法，不可被覆蓋（那是歷史，不該改寫）
 */

import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.client.model.TranslationVariant;
import com.tim.language_project.client.model.TranslationWord;
import com.tim.language_project.entity.TranslationQuery;
import com.tim.language_project.entity.Vocabulary;
import com.tim.language_project.enums.GenderUsageEnum;
import com.tim.language_project.enums.PolitenessEnum;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import com.tim.language_project.enums.VocabularySourceTypeEnum;
import com.tim.language_project.repository.TranslationQueryRepository;
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
    private TranslationQueryRepository translationQueryRepository;

    @Mock
    private TranslationSegmentRepository translationSegmentRepository;

    @Mock
    private VocabularyRepository vocabularyRepository;

    @InjectMocks
    private TranslationPersistenceService translationPersistenceService;

    @Test
    @DisplayName("單字的多個說法應各寫入一列並帶上標籤")
    void shouldPersistEachVariantAsItsOwnRow() {
        givenSavedQuery();
        when(vocabularyRepository.findAllByChineseTextIn(anyCollection()))
                .thenReturn(List.of());

        translationPersistenceService.persist(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                singleWordResultWithVariants());

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
        givenSavedQuery();

        Vocabulary existing = new Vocabulary();
        existing.setChineseText("我");
        existing.setThaiText("ฉัน");
        existing.setRomanization("chǎn");
        existing.setSourceType(VocabularySourceTypeEnum.SEGMENT);

        when(vocabularyRepository.findAllByChineseTextIn(anyCollection()))
                .thenReturn(List.of(existing));

        translationPersistenceService.persist(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                singleWordResultWithVariants());

        assertThat(existing.getGenderUsage()).isEqualTo(GenderUsageEnum.FEMALE);
        assertThat(existing.getPoliteness()).isEqualTo(PolitenessEnum.FORMAL);
        assertThat(existing.getNote()).isEqualTo("女生自稱");

        // source_type 是歷史，不可被改寫
        assertThat(existing.getSourceType()).isEqualTo(VocabularySourceTypeEnum.SEGMENT);
    }

    @Test
    @DisplayName("已存在且標籤有值的說法不可被覆蓋")
    void shouldNotOverwriteExistingLabels() {
        givenSavedQuery();

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

        translationPersistenceService.persist(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                singleWordResultWithVariants());

        assertThat(existing.getGenderUsage()).isEqualTo(GenderUsageEnum.BOTH);
        assertThat(existing.getNote()).isEqualTo("原本就有的說明");
    }

    private void givenSavedQuery() {
        TranslationQuery savedQuery = new TranslationQuery();
        savedQuery.setId(1L);
        when(translationQueryRepository.saveAndFlush(any(TranslationQuery.class)))
                .thenReturn(savedQuery);
    }

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
                "gpt-5.5", 100L, 50L, true);
    }
}
