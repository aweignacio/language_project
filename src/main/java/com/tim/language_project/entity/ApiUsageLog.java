package com.tim.language_project.entity;

import com.tim.language_project.enums.AiProviderEnum;
import com.tim.language_project.enums.AiServiceTypeEnum;
import com.tim.language_project.enums.UsageUnitTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一次外部服務呼叫的用量與費用。屬於稽核資料 ——
 * 就算對應的查詢被刪掉也要留著，所以不設外鍵限制。
 */
@Entity
@Table(name = "api_usage_log")
@Getter
@Setter
@NoArgsConstructor
public class ApiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 這次呼叫是為了哪筆查詢，只是鬆散參照。不確定時為 null。 */
    @Column(name = "query_id")
    private Long queryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20, nullable = false)
    private AiProviderEnum provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", length = 20, nullable = false)
    private AiServiceTypeEnum serviceType;

    @Column(name = "model_name", length = 100, nullable = false)
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", length = 20, nullable = false)
    private UsageUnitTypeEnum unitType;

    @Column(name = "input_units", nullable = false)
    private Long inputUnits;

    /** 語音合成一律是 0。 */
    @Column(name = "output_units", nullable = false)
    private Long outputUnits;

    /** 呼叫當下的單價。之後服務商調價，舊資料的費用仍然對得起來。 */
    @Column(name = "input_unit_price", precision = 12, scale = 8, nullable = false)
    private BigDecimal inputUnitPrice;

    @Column(name = "output_unit_price", precision = 12, scale = 8, nullable = false)
    private BigDecimal outputUnitPrice;

    @Column(name = "cost_amount", precision = 12, scale = 6, nullable = false)
    private BigDecimal costAmount;

    @Column(name = "currency", columnDefinition = "CHAR(3)", insertable = false, updatable = false)
    private String currency;

    @Column(name = "is_success", nullable = false)
    private Boolean success;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
