package com.tim.language_project.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 建立 Cloud Storage 的連線物件。
 *
 * ★ 只有 audio.storage.provider=GCS 時才建立。
 *   本機開發時這個 Bean 完全不存在，所以不需要任何 Google 憑證，
 *   也不會因為連不上而拖慢啟動 —— 這是刻意的，讓本機開發不依賴雲端。
 *
 * getDefaultInstance 會自動使用「目前這個程式的執行身分」，
 * 在 Cloud Run 上就是它綁定的服務帳號，不需要金鑰檔。
 */
@Configuration
@ConditionalOnProperty(name = "audio.storage.provider", havingValue = "GCS")
public class GoogleCloudStorageConfig {

    @Bean
    public Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}
