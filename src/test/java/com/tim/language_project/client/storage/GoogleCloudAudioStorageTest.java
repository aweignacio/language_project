package com.tim.language_project.client.storage;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  防「雲端版的路徑格式跟本機版分家」，以及「上傳失敗時炸出例外」。
 *
 * ── 路徑格式為什麼要緊 ─────────────────────────────────────────────────
 *
 *  save 回傳的字串會被寫進 audio_asset.file_path，而那張表是本機與雲端
 *  共用同一種語意。如果本機回傳 "th/x.wav"、雲端回傳 "audio/th/x.wav"
 *  或是一整串完整網址，同一張表就會混進兩種格式，之後誰也讀不對，
 *  而且要搬家時全部對不上。
 *
 * ── 上傳失敗為什麼不能拋例外 ───────────────────────────────────────────
 *
 *  呼叫端（GoogleSpeechClient）是靠 Optional 是不是空的來判斷成敗，
 *  然後決定要不要記一筆 FILE_SAVE_FAILED。如果這裡拋例外出去，
 *  整個翻譯查詢會一起失敗 —— 但設計上「音檔存不起來」只該讓語音消失，
 *  翻譯結果仍然要正常顯示給使用者。
 *
 * ── 假的東西 ───────────────────────────────────────────────────────────
 *
 *  整個 Google Storage 用 Mockito 換成假的。
 *
 *  ★ 為什麼不打真的 Cloud Storage：
 *    那要網路、要金鑰、要花錢，而且會在正式的 bucket 裡留下垃圾檔案。
 *    這裡要驗的是「我們有沒有用正確的參數去呼叫它」「它出錯時我們怎麼反應」，
 *    不是「Google 會不會壞」。
 *
 *  ★ 注意這裡跟 LocalDiskAudioStorageTest 的取捨不同：
 *    本機那支用「真的」實作（配 @TempDir），因為寫檔到硬碟是免費且可驗證的；
 *    這支不行，所以只能退而求其次驗參數。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  1. 路徑格式與本機一致   → 防兩個實作分家
 *  2. 上傳到正確的 bucket   → 防檔案被丟到別人的桶子或錯的名稱
 *  3. 副檔名照呼叫端指定    → OpenAI 那條路存的是 mp3，不可寫死成 wav
 *  4. 上傳失敗回空值        → 防整個查詢跟著失敗
 *  5. 檔案不存在回空值      → AudioFileController 靠這個回 404
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.enums.SpeechLanguageEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleCloudAudioStorageTest {

    private static final String BUCKET = "thailan-audio-test";

    private Storage storage;

    private GoogleCloudAudioStorage googleCloudAudioStorage;

    @BeforeEach
    void setUp() {
        storage = mock(Storage.class);

        AudioStorageProperties properties = new AudioStorageProperties();
        properties.setBucket(BUCKET);

        googleCloudAudioStorage = new GoogleCloudAudioStorage(storage, properties);
    }

    @Test
    @DisplayName("回傳的路徑格式應與本機實作一致：語言資料夾/檔名.副檔名")
    void shouldReturnSamePathFormatAsLocal() {
        Optional<String> filePath =
                googleCloudAudioStorage.save(SpeechLanguageEnum.TH, new byte[]{1}, "wav");

        assertThat(filePath).isPresent();
        assertThat(filePath.get()).startsWith("th/");
        assertThat(filePath.get()).endsWith(".wav");
    }

    @Test
    @DisplayName("應上傳到設定的 bucket，且物件名稱等於回傳的路徑")
    void shouldUploadToConfiguredBucket() {
        Optional<String> filePath =
                googleCloudAudioStorage.save(SpeechLanguageEnum.ZH, new byte[]{1, 2}, "wav");

        ArgumentCaptor<BlobInfo> captor = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage).create(captor.capture(), any(byte[].class));

        assertThat(captor.getValue().getBucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().getName()).isEqualTo(filePath.orElseThrow());
        assertThat(captor.getValue().getName()).startsWith("zh/");
    }

    @Test
    @DisplayName("副檔名應照呼叫端指定的來，OpenAI 那條路存的是 mp3")
    void shouldHonourRequestedExtension() {
        Optional<String> filePath =
                googleCloudAudioStorage.save(SpeechLanguageEnum.TH, new byte[]{1}, "mp3");

        assertThat(filePath.orElseThrow()).endsWith(".mp3");

        ArgumentCaptor<BlobInfo> captor = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage).create(captor.capture(), any(byte[].class));

        assertThat(captor.getValue().getContentType()).isEqualTo("audio/mpeg");
    }

    @Test
    @DisplayName("上傳失敗應回傳空值，不可拋出例外，否則整個翻譯查詢會跟著失敗")
    void shouldReturnEmptyWhenUploadFails() {
        when(storage.create(any(BlobInfo.class), any(byte[].class)))
                .thenThrow(new StorageException(500, "boom"));

        assertThat(googleCloudAudioStorage.save(SpeechLanguageEnum.TH, new byte[]{1}, "wav"))
                .isEmpty();
    }

    @Test
    @DisplayName("檔案不存在時應回傳空值，AudioFileController 靠它回 404")
    void shouldReturnEmptyWhenBlobMissing() {
        when(storage.get(any(BlobId.class))).thenReturn(null);

        assertThat(googleCloudAudioStorage.openStream("th/notexist.wav")).isEmpty();
    }

    @Test
    @DisplayName("檔案存在時應讀得到內容")
    void shouldReadBackExistingBlob() throws Exception {
        byte[] content = {9, 8, 7};
        Blob blob = mock(Blob.class);
        when(blob.getContent()).thenReturn(content);
        when(storage.get(any(BlobId.class))).thenReturn(blob);

        assertThat(googleCloudAudioStorage.openStream("th/exists.wav").orElseThrow()
                .readAllBytes()).isEqualTo(content);
    }
}
