package com.tim.language_project.repository;

import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.entity.Vocabulary;
import com.tim.language_project.enums.VocabularySourceTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VocabularyRepositoryTest {

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Test
    @DisplayName("依中文詞查詢應回傳對應泰文")
    void shouldFindByChineseText() {
        vocabularyRepository.saveAndFlush(
                buildVocabulary("酒", "เหล้า", "lâo", VocabularySourceTypeEnum.SEGMENT));

        Optional<VocabularyDto> found = vocabularyRepository.findByChineseText("酒");

        assertThat(found).isPresent();
        assertThat(found.get().thaiText()).isEqualTo("เหล้า");
        assertThat(found.get().romanization()).isEqualTo("lâo");
    }

    @Test
    @DisplayName("批次查詢已存在的中文詞，供沉澱單字時過濾重複")
    void shouldFindExistingChineseTexts() {
        vocabularyRepository.saveAndFlush(
                buildVocabulary("我", "ฉัน", "chǎn", VocabularySourceTypeEnum.SEGMENT));
        vocabularyRepository.saveAndFlush(
                buildVocabulary("水", "น้ำ", "náam", VocabularySourceTypeEnum.SEGMENT));

        List<String> existing =
                vocabularyRepository.findExistingChineseTexts(List.of("我", "水", "沒有這個詞"));

        assertThat(existing).containsExactlyInAnyOrder("我", "水");
    }

    private Vocabulary buildVocabulary(String chineseText, String thaiText,
                                       String romanization, VocabularySourceTypeEnum sourceType) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setChineseText(chineseText);
        vocabulary.setThaiText(thaiText);
        vocabulary.setRomanization(romanization);
        vocabulary.setSourceType(sourceType);
        return vocabulary;
    }
}
