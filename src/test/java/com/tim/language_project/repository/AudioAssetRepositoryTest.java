package com.tim.language_project.repository;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  audio_asset 這張表只有一個任務：★ 同一段文字全站只合成一次 ★
 *
 *  這件事完全靠資料庫的唯一鍵 UQ_audio_asset_text_language 撐著。
 *  那條唯一鍵如果哪天被誰拿掉，程式「不會壞」—— 它會照常運作，
 *  只是每次查詢都重新合成一次語音，安靜地一直花錢。
 *  這種問題不會有人發現，所以要用測試把它釘住。
 *
 * ── 為什麼連真的 SQL Server 而不用 H2 ───────────────────────────────────
 *
 *  @AutoConfigureTestDatabase(replace = NONE) 是在擋掉 @DataJpaTest
 *  「偷偷換成 H2」的預設行為。因為這個資料庫的 collation 存不了非 ASCII 字元，
 *  泰文一定要用 NVARCHAR 才不會變成問號，而 H2 沒有這個區別 ——
 *  用 H2 測等於這個專案最危險的問題永遠測不到。
 *
 *  代價是跑測試前要先啟動 Docker 容器。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  測試一  泰文存進去再撈出來還是泰文（防 NVARCHAR 被改成 VARCHAR）
 *  測試二  同一段文字加同一個語言，第二次寫入必須失敗（防唯一鍵被拿掉）
 *  測試三  同一段文字但不同語言可以各存一筆（中泰同形的字不該互相擋）
 */

import com.tim.language_project.dto.response.AudioAssetDto;
import com.tim.language_project.entity.AudioAsset;
import com.tim.language_project.enums.SpeechLanguageEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AudioAssetRepositoryTest {

    @Autowired
    private AudioAssetRepository audioAssetRepository;

    @Test
    @DisplayName("泰文存入後應原樣取回")
    void shouldKeepThaiCharacters() {
        audioAssetRepository.saveAndFlush(
                newAudioAsset("เหล้า", SpeechLanguageEnum.TH, "th/a1b2c3.mp3"));

        Optional<AudioAssetDto> found = audioAssetRepository
                .findBySpeechTextAndLanguage("เหล้า", SpeechLanguageEnum.TH);

        assertThat(found).isPresent();
        assertThat(found.get().speechText()).isEqualTo("เหล้า");
        assertThat(found.get().filePath()).isEqualTo("th/a1b2c3.mp3");
    }

    /*
     * ★ 這個測試守著整個「用越久越省錢」的機制。
     *   唯一鍵一旦消失，這裡會變成綠燈，而正式環境會開始重複付語音費用。
     */
    @Test
    @DisplayName("同一段文字加同一語言不可重複寫入")
    void shouldRejectDuplicateTextAndLanguage() {
        audioAssetRepository.saveAndFlush(
                newAudioAsset("ขอบคุณ", SpeechLanguageEnum.TH, "th/d4e5f6.mp3"));

        assertThatThrownBy(() -> audioAssetRepository.saveAndFlush(
                newAudioAsset("ขอบคุณ", SpeechLanguageEnum.TH, "th/g7h8i9.mp3")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("同一段文字不同語言可各存一筆")
    void shouldAllowSameTextInDifferentLanguages() {
        audioAssetRepository.saveAndFlush(
                newAudioAsset("OK", SpeechLanguageEnum.TH, "th/j1k2l3.mp3"));
        audioAssetRepository.saveAndFlush(
                newAudioAsset("OK", SpeechLanguageEnum.ZH, "zh/m4n5o6.mp3"));

        assertThat(audioAssetRepository
                .findBySpeechTextAndLanguage("OK", SpeechLanguageEnum.ZH))
                .isPresent();
    }

    private AudioAsset newAudioAsset(String speechText,
                                     SpeechLanguageEnum language,
                                     String filePath) {
        AudioAsset audioAsset = new AudioAsset();
        audioAsset.setSpeechText(speechText);
        audioAsset.setLanguage(language);
        audioAsset.setFilePath(filePath);

        return audioAsset;
    }
}
