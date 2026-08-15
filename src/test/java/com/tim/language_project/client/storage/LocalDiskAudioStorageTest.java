package com.tim.language_project.client.storage;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  防「本機存進去的音檔讀不回來」，以及「路徑格式跟雲端那份不一致」。
 *
 *  路徑格式為什麼要緊：save 回傳的字串會被寫進 audio_asset.file_path，
 *  而那張表是本機與雲端共用的語意。如果本機回傳 "audio/th/x.wav"、
 *  雲端回傳 "th/x.wav"，資料就沒辦法互通，將來要搬家也會全部對不上。
 *
 * ── 假的東西 ───────────────────────────────────────────────────────────
 *
 *  用 @TempDir 給一個測試專用的暫存資料夾，取代真正的 audio 資料夾。
 *  這樣測試不會弄髒專案，也不會受既有音檔影響。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  1. 存進去讀得回來       → 最基本的往返，壞了就整個功能不能用
 *  2. 路徑格式是 語言/檔名 → 防路徑格式跟雲端實作分家
 *  3. 子資料夾自動建立     → 第一次跑的機器上 audio/th 還不存在
 *  4. 讀不存在的檔回空值   → 防呼叫端拿到例外而不是空值
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.enums.SpeechLanguageEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDiskAudioStorageTest {

    @TempDir
    private Path tempDirectory;

    private LocalDiskAudioStorage localDiskAudioStorage;

    @BeforeEach
    void setUp() {
        AudioStorageProperties properties = new AudioStorageProperties();
        properties.setDirectory(tempDirectory.toString());
        localDiskAudioStorage = new LocalDiskAudioStorage(properties);
    }

    @Test
    @DisplayName("存進去的內容應原樣讀得回來")
    void shouldReadBackWhatWasSaved() throws Exception {
        byte[] content = {1, 2, 3, 4, 5};

        Optional<String> filePath =
                localDiskAudioStorage.save(SpeechLanguageEnum.TH, content, "wav");

        assertThat(filePath).isPresent();

        try (InputStream stream =
                     localDiskAudioStorage.openStream(filePath.get()).orElseThrow()) {
            assertThat(stream.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("回傳的路徑應為「語言資料夾/檔名.副檔名」")
    void shouldReturnPathWithLanguageFolder() {
        Optional<String> filePath =
                localDiskAudioStorage.save(SpeechLanguageEnum.TH, new byte[]{1}, "wav");

        assertThat(filePath).isPresent();
        assertThat(filePath.get()).startsWith("th/");
        assertThat(filePath.get()).endsWith(".wav");
    }

    @Test
    @DisplayName("副檔名應照呼叫端指定的來，OpenAI 那條路存的是 mp3")
    void shouldHonourRequestedExtension() {
        Optional<String> filePath =
                localDiskAudioStorage.save(SpeechLanguageEnum.TH, new byte[]{1}, "mp3");

        assertThat(filePath.orElseThrow()).endsWith(".mp3");
    }

    @Test
    @DisplayName("中文與泰文應存到不同的子資料夾")
    void shouldSeparateLanguagesIntoFolders() {
        Optional<String> thaiPath =
                localDiskAudioStorage.save(SpeechLanguageEnum.TH, new byte[]{1}, "wav");
        Optional<String> chinesePath =
                localDiskAudioStorage.save(SpeechLanguageEnum.ZH, new byte[]{1}, "wav");

        assertThat(thaiPath.orElseThrow()).startsWith("th/");
        assertThat(chinesePath.orElseThrow()).startsWith("zh/");
    }

    @Test
    @DisplayName("讀取不存在的檔案應回傳空值，而非拋出例外")
    void shouldReturnEmptyWhenFileMissing() {
        assertThat(localDiskAudioStorage.openStream("th/notexist.wav")).isEmpty();
    }
}
