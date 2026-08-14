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
     * 預設只有泰文 —— 目前的使用者是中文母語者，中文音檔對他價值為零，
     * 為它每次多等一兩秒不划算。日後開放給泰國使用者時改成 TH, ZH 即可。
     */
    private List<SpeechLanguageEnum> autoGenerate = List.of(SpeechLanguageEnum.TH);
}
