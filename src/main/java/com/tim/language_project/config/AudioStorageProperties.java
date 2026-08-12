package com.tim.language_project.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
}
