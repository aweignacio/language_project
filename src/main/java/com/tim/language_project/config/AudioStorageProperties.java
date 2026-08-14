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

    private String directory = "audio";

    /**
     * 查詢完成時要自動產生哪些語言的音檔（涵蓋整句與多重說法）。
     *
     * 這是總開關。實際上中文還有第二道規則 ——
     * 只有「使用者輸入泰文」時才會產生，見 TranslationService.autoGenerateAudio。
     */
    private List<SpeechLanguageEnum> autoGenerate =
            List.of(SpeechLanguageEnum.TH, SpeechLanguageEnum.ZH);
}
