package com.tim.language_project.config;

import com.tim.language_project.enums.SpeechLanguageEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 產生出來的音檔要存在本機哪個資料夾，從 application.yml 的 audio.storage 讀進來。
 * 預設是專案根目錄下的 audio/，該資料夾已列入 .gitignore。
 * 這個類別刻意不標 @Component，改由 WebMvcConfig 以 @EnableConfigurationProperties 註冊 ——
 * 理由見 WebMvcConfig 的說明。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "audio.storage")
public class AudioStorageProperties {

    /**
     * 音檔要存到哪裡。LOCAL / GCS。
     *
     * ★ 與 speech.provider 是同一種設計：兩份實作都在，切這一行就換人。
     *   LOCAL 走 LocalDiskAudioStorage（本機的 audio 資料夾）
     *   GCS   走 GoogleCloudAudioStorage（Cloud Storage）
     */
    private String provider = "LOCAL";

    /**
     * 本機模式的音檔資料夾，相對於程式的工作目錄。provider 為 LOCAL 時才會用到。
     */
    private String directory = "audio";

    /**
     * Cloud Storage 的 bucket 名稱。provider 為 GCS 時才會用到。
     *
     * bucket 是 Cloud Storage 裡「一個獨立的儲存空間」，名稱在全世界不可重複。
     * 雲端的值由 Cloud Run 的環境變數 GCS_BUCKET 帶進來。
     */
    private String bucket;

    /**
     * 查詢完成時要自動產生哪些語言的音檔（涵蓋整句與多重說法）。
     *
     * 這是總開關。實際上中文還有第二道規則 ——
     * 只有「使用者輸入泰文」時才會產生，見 TranslationService.autoGenerateAudio。
     */
    private List<SpeechLanguageEnum> autoGenerate =
            List.of(SpeechLanguageEnum.TH, SpeechLanguageEnum.ZH);
}
