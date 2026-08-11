package com.tim.language_project.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 外部服務的單價，從 application.yml 的 ai.pricing.openai 讀進來。
 * 每次呼叫都會把當下的單價寫進用量紀錄，之後服務商調價也不影響舊資料的稽核。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ai.pricing.openai")
public class AiPricingProperties {

    private BigDecimal translationInputPrice = BigDecimal.ZERO;

    private BigDecimal translationOutputPrice = BigDecimal.ZERO;

    private BigDecimal speechPrice = BigDecimal.ZERO;
}
