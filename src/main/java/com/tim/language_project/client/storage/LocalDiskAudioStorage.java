package com.tim.language_project.client.storage;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  把音檔存在這台電腦的 audio 資料夾裡，本機開發時用的就是它。
 *
 * ── 流程：一段泰文合成完之後，檔案怎麼落地 ────────────────────────────
 *
 *  第 1 步｜GoogleSpeechClient 拿到整理好的 WAV 位元組，呼叫
 *
 *      audioStorage.save(SpeechLanguageEnum.TH, wavBytes, "wav");
 *
 *  第 2 步｜這裡產生一個隨機檔名，並組出相對路徑
 *
 *      "th" + "/" + "a1b2c3d4e5f6.wav"  →  "th/a1b2c3d4e5f6.wav"
 *
 *    ★ 檔名用隨機碼而不是那段泰文，理由有二：
 *      泰文含檔案系統不接受的字元，且同一段文字可能很長。
 *      「哪個檔案對應哪段文字」由 audio_asset 那張表負責記住。
 *
 *  第 3 步｜確保 audio/th 資料夾存在（第一次跑的機器上還沒有），寫檔
 *
 *      C:\Tim\language_project\audio\th\a1b2c3d4e5f6.wav
 *
 *  第 4 步｜回傳 "th/a1b2c3d4e5f6.wav"
 *
 *    ★ 回傳的是相對路徑，不含 audio 這一層，也不含磁碟機代號。
 *      GoogleCloudAudioStorage 回傳的格式必須與此完全相同 ——
 *      這個字串會被寫進 audio_asset.file_path，兩種環境共用同一張表。
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.enums.SpeechLanguageEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "audio.storage.provider", havingValue = "LOCAL", matchIfMissing = true)
public class LocalDiskAudioStorage implements AudioStorage {

    private final AudioStorageProperties audioStorageProperties;

    @Override
    public Optional<String> save(SpeechLanguageEnum language, byte[] content, String extension) {
        String filePath = language.getFolderName() + "/" + newFileName(extension);

        try {
            Path root = Paths.get(audioStorageProperties.getDirectory());
            Files.createDirectories(root.resolve(language.getFolderName()));
            Files.write(root.resolve(filePath), content);

            return Optional.of(filePath);
        } catch (Exception exception) {
            log.error("音檔寫入本機失敗，路徑 {}", filePath, exception);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Resource> load(String filePath) {
        try {
            Path target = Paths.get(audioStorageProperties.getDirectory()).resolve(filePath);

            if (!Files.exists(target)) {
                return Optional.empty();
            }

            // PathResource 直接指向磁碟上的檔案：知道長度、可以隨機存取，
            // 而且不會把內容載進記憶體。Spring 靠這兩個能力才處理得了
            // iOS 播放音訊時必送的 Range 請求（見 AudioStorage.load 的說明）。
            return Optional.of(new PathResource(target));
        } catch (Exception exception) {
            log.error("音檔讀取失敗，路徑 {}", filePath, exception);
            return Optional.empty();
        }
    }

    /** 隨機十二碼加副檔名。與原本兩個 SpeechClient 的產生方式一致。 */
    private String newFileName(String extension) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                + "." + extension;
    }
}
