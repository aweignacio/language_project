package com.tim.language_project.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Google Cloud Text-to-Speech 的設定，從 application.yml 的 google.speech 讀進來。
 * apiKey 放在 application-local.yml，不進版本控制。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "google.speech")
public class GoogleSpeechProperties {

    private String apiKey = "";

    /** 泰語聲音。th-TH 的男聲全部都是 Chirp3-HD 系列。 */
    private String thaiVoice = "th-TH-Chirp3-HD-Charon";

    /** 中文聲音。cmn-TW 是台灣華語。 */
    private String chineseVoice = "cmn-TW-Wavenet-B";

    /** 每一個字元的美金價格。 */
    private BigDecimal pricePerCharacter = BigDecimal.ZERO;
}
