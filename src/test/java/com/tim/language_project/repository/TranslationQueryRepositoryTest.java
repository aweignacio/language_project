package com.tim.language_project.repository;

import com.tim.language_project.dto.response.TranslationQueryDto;
import com.tim.language_project.entity.TranslationQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TranslationQueryRepositoryTest {

    @Autowired
    private TranslationQueryRepository translationQueryRepository;

    @Test
    @DisplayName("泰文與拼音寫入後讀回不應損毀")
    void shouldPreserveThaiTextAndRomanization() {
        TranslationQuery query = new TranslationQuery();
        query.setSourceText("我想喝酒");
        query.setThaiText("ฉันอยากดื่มเหล้า");
        query.setRomanization("chǎn yàak dùuem lâo");
        query.setAudioFile("a3f9c2.mp3");

        translationQueryRepository.saveAndFlush(query);

        Optional<TranslationQueryDto> found =
                translationQueryRepository.findBySourceText("我想喝酒");

        assertThat(found).isPresent();
        assertThat(found.get().thaiText()).isEqualTo("ฉันอยากดื่มเหล้า");
        assertThat(found.get().romanization()).isEqualTo("chǎn yàak dùuem lâo");
        assertThat(found.get().sourceText()).isEqualTo("我想喝酒");
    }

    @Test
    @DisplayName("音檔為 null 時仍可正常寫入與讀取")
    void shouldAllowNullAudioFile() {
        TranslationQuery query = new TranslationQuery();
        query.setSourceText("水");
        query.setThaiText("น้ำ");
        query.setRomanization("náam");
        query.setAudioFile(null);

        translationQueryRepository.saveAndFlush(query);

        Optional<TranslationQueryDto> found = translationQueryRepository.findBySourceText("水");

        assertThat(found).isPresent();
        assertThat(found.get().audioFile()).isNull();
    }
}
