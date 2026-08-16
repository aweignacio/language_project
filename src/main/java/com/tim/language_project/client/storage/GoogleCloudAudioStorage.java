package com.tim.language_project.client.storage;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  把音檔存到 Google Cloud Storage，雲端執行時用的就是它。
 *
 * ── 為什麼雲端不能像本機一樣存資料夾 ───────────────────────────────────
 *
 *  Cloud Run 的容器是「用完就丟的紙杯」。每次重新部署、平台維護、
 *  或容器自動重啟，都會換一個新的容器，舊容器裡的檔案全部消失。
 *
 *  ★ 後果不只是「檔案不見了」，而是「程式會發現 audio_asset 有紀錄
 *    但檔案讀不到，於是重新合成一次」—— 每次重啟都重付一次錢，
 *    而且畫面上完全看不出異常。
 *
 *  Cloud Storage 是獨立於容器之外的儲存空間，容器換幾次都不受影響。
 *
 * ── 流程：合成好的音檔怎麼上雲 ─────────────────────────────────────────
 *
 *  第 1 步｜GoogleSpeechClient 把整理好的位元組交過來
 *
 *      audioStorage.save(SpeechLanguageEnum.TH, wavBytes, "wav");
 *
 *    ★ 副檔名由呼叫端指定，不是這裡猜的。
 *      Google 那條路走 LINEAR16 經 WavAudio.tidy 之後是 wav，
 *      OpenAI 那條路依設定直接回 mp3，兩家不一樣。
 *
 *  第 2 步｜產生與本機版完全相同格式的路徑
 *
 *      "th/a1b2c3d4e5f6.wav"
 *
 *    ★ 這個格式必須跟 LocalDiskAudioStorage 一模一樣，因為它會被寫進
 *      audio_asset.file_path，而那張表兩種環境共用同一種語意。
 *      本機存的紀錄搬到雲端要能直接讀，反過來也一樣。
 *
 *  第 3 步｜上傳
 *
 *      BlobId   = (bucket 名稱, "th/a1b2c3d4e5f6.wav")
 *      storage.create(blobInfo, content)
 *
 *    名詞解釋：在 Cloud Storage 的術語裡，一個檔案叫一個 blob，
 *    bucket 則是裝 blob 的桶子（相當於一個獨立的儲存空間，名稱全球唯一）。
 *
 *    ★ 名稱裡的斜線只是名字的一部分，Cloud Storage 其實沒有真正的資料夾。
 *      但後台介面會依斜線顯示成資料夾，看起來跟本機一樣。
 *
 *  第 4 步｜回傳 "th/a1b2c3d4e5f6.wav"，由 AudioAssetService 寫進資料庫
 *
 * ── 出錯了怎麼辦 ───────────────────────────────────────────────────────
 *
 *  一律回傳 Optional.empty()，★ 絕對不可以把例外拋出去。
 *
 *  呼叫端是靠「Optional 是不是空的」決定要不要記一筆 FILE_SAVE_FAILED。
 *  拋例外出去的話，整個翻譯查詢會一起失敗 —— 但設計上「音檔存不起來」
 *  只該讓聲音消失，翻譯結果仍然要正常顯示給使用者。
 *
 * ── 身分怎麼來（這裡沒有金鑰）───────────────────────────────────────────
 *
 *  StorageOptions.getDefaultInstance() 會自動抓「這個程式正在用誰的身分
 *  執行」—— 在 Cloud Run 上就是它綁定的服務帳號。
 *
 *  ★ 這是雲端內部服務互相呼叫的標準做法，比帶著金鑰檔安全得多，
 *    因為根本沒有金鑰可以外流。前提是那個服務帳號要有 bucket 的權限，
 *    那一步在部署時設定。
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.enums.SpeechLanguageEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "audio.storage.provider", havingValue = "GCS")
public class GoogleCloudAudioStorage implements AudioStorage {

    private final Storage storage;

    private final AudioStorageProperties audioStorageProperties;

    @Override
    public Optional<String> save(SpeechLanguageEnum language, byte[] content, String extension) {
        String filePath = language.getFolderName() + "/" + newFileName(extension);

        try {
            BlobId blobId = BlobId.of(audioStorageProperties.getBucket(), filePath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(AudioContentType.of(extension))
                    .build();

            storage.create(blobInfo, content);

            return Optional.of(filePath);
        } catch (Exception exception) {
            log.error("音檔上傳 Cloud Storage 失敗，路徑 {}", filePath, exception);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Resource> load(String filePath) {
        try {
            Blob blob = storage.get(
                    BlobId.of(audioStorageProperties.getBucket(), filePath));

            if (Objects.isNull(blob)) {
                return Optional.empty();
            }

            // getContent 會把整個 blob 讀進記憶體。對這個專案是可接受的：
            // 單一音檔最大不過幾百 KB。
            //
            // ★ 用 ByteArrayResource 而不是 InputStream，是為了讓 Spring
            //   能處理 iOS 播放音訊時必送的 Range 請求 —— 它需要一個
            //   「知道長度、可重複讀取」的資源才切得出部分內容。
            //   （見 AudioStorage.load 的說明。）
            //
            // ★ 若日後音檔改成長篇朗讀（例如整段課文），這裡要改成
            //   blob.reader() 搭配支援隨機存取的 Resource，否則同時多人播放
            //   會把記憶體吃光。
            return Optional.of(new ByteArrayResource(blob.getContent()));
        } catch (Exception exception) {
            log.error("音檔自 Cloud Storage 讀取失敗，路徑 {}", filePath, exception);
            return Optional.empty();
        }
    }

    /** 隨機十二碼加副檔名。★ 產生方式必須與 LocalDiskAudioStorage 完全一致。 */
    private String newFileName(String extension) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                + "." + extension;
    }
}
