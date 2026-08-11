package com.tim.language_project.client.usage;

import com.tim.language_project.entity.ApiUsageLog;
import com.tim.language_project.enums.AiProviderEnum;
import com.tim.language_project.enums.AiServiceTypeEnum;
import com.tim.language_project.enums.UsageUnitTypeEnum;
import com.tim.language_project.repository.ApiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 記錄每次呼叫外部服務的用量與費用。
 * 使用獨立交易（REQUIRES_NEW），這樣記帳失敗不會把呼叫端一起回滾，
 * 呼叫端失敗時這筆紀錄也仍然留得下來。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiUsageRecorder {

    private final ApiUsageLogRepository apiUsageLogRepository;

    /**
     * 記錄一次呼叫。
     * 費用 = 輸入單價 × 輸入用量 ＋ 輸出單價 × 輸出用量。
     * 單價由呼叫端傳入而非在這裡查表，紀錄才會反映「呼叫當下」的價格。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AiProviderEnum provider,
                       AiServiceTypeEnum serviceType,
                       String modelName,
                       UsageUnitTypeEnum unitType,
                       long inputUnits,
                       long outputUnits,
                       BigDecimal inputUnitPrice,
                       BigDecimal outputUnitPrice,
                       boolean success) {
        try {
            BigDecimal cost = inputUnitPrice.multiply(BigDecimal.valueOf(inputUnits))
                    .add(outputUnitPrice.multiply(BigDecimal.valueOf(outputUnits)));

            ApiUsageLog usageLog = new ApiUsageLog();
            usageLog.setProvider(provider);
            usageLog.setServiceType(serviceType);
            usageLog.setModelName(modelName);
            usageLog.setUnitType(unitType);
            usageLog.setInputUnits(inputUnits);
            usageLog.setOutputUnits(outputUnits);
            usageLog.setInputUnitPrice(inputUnitPrice);
            usageLog.setOutputUnitPrice(outputUnitPrice);
            usageLog.setCostAmount(cost);
            usageLog.setSuccess(success);

            apiUsageLogRepository.save(usageLog);
        } catch (Exception exception) {
            // 記帳只是給我們自己看的，不構成讓使用者請求失敗的理由。
            log.error("failed to record api usage for {} {}", provider, serviceType, exception);
        }
    }
}
