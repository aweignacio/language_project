package com.tim.language_project.client.usage;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  記帳。每呼叫一次 OpenAI，就在 api_usage_log 表寫一筆
 *  「這次花了多少錢」，月底才對得起 OpenAI 寄來的帳單。
 *
 *  ★ 這是全專案唯一算錢的地方。算錯不會有任何錯誤訊息，
 *    只會靜靜存進一個錯的數字，所以這個檔案有專屬測試。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：接續「我想喝酒」翻譯成功之後
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜OpenAI 剛回完話 ────────────────────────────────────────────
 *
 *    OpenAiTranslationClient 拿到回應，裡面的 usage 說：
 *
 *        輸入用了 120 個 token，輸出用了 45 個 token
 *
 * ── 第 2 步｜OpenAiTranslationClient 呼叫這個檔案 ───────────────────────
 *
 *        apiUsageRecorder.record(
 *                AiProviderEnum.OPENAI,            打給誰
 *                AiServiceTypeEnum.TRANSLATION,    做什麼（翻譯，不是語音）
 *                "gpt-4o-mini",                    哪個模型
 *                UsageUnitTypeEnum.TOKEN,          按什麼單位算
 *                120L,                             輸入用量
 *                45L,                              輸出用量
 *                new BigDecimal("0.00000500"),     輸入單價（每個 token）
 *                new BigDecimal("0.00001500"),     輸出單價
 *                true);                            這次成功了
 *
 *    ★ 單價為什麼由呼叫端傳進來，而不是這裡自己去查設定檔？
 *
 *      因為 OpenAI 會調價。單價要記錄「呼叫當下」的價格，
 *      三個月後回頭看這筆帳，才算得出跟當時一樣的數字。
 *      如果每次都去查最新設定，舊資料的費用就會跟著變動，帳就爛了。
 *
 * ── 第 3 步｜進到 record 方法，算錢 ─────────────────────────────────────
 *
 *        費用 = 輸入單價 × 輸入用量 ＋ 輸出單價 × 輸出用量
 *
 *        輸入：0.00000500 × 120 = 0.000600
 *        輸出：0.00001500 ×  45 = 0.000675
 *        ─────────────────────────────────
 *        合計                    = 0.001275 美金
 *
 *    ★ 為什麼用 BigDecimal 而不是 double？
 *
 *      double 算小數會有誤差（0.1 + 0.2 在電腦裡不等於 0.3）。
 *      單價小數點後有八位，誤差累積起來帳就對不上。
 *      凡是金額一律 BigDecimal，這是本專案的硬性規定。
 *
 *      也因為這樣，乘法不能寫 a * b，要寫 a.multiply(b)。
 *
 * ── 第 4 步｜組成一列資料存進去 ─────────────────────────────────────────
 *
 *        ApiUsageLog usageLog = new ApiUsageLog();   ← 一個空白的「列」
 *        usageLog.setProvider(...);                  ← 一格一格填
 *        ...
 *        apiUsageLogRepository.save(usageLog);       ← 交給 Repository 寫進資料庫
 *
 * ── 第 5 步｜資料庫的 api_usage_log 表多出一列 ──────────────────────────
 *
 *        id  provider  service_type  model_name    input_units  cost_amount  is_success
 *        ──  ────────  ────────────  ───────────   ───────────  ───────────  ──────────
 *        17  OPENAI    TRANSLATION   gpt-4o-mini   120          0.001275     1
 *
 * ── 第 6 步｜使用者完全不知道這件事發生過 ──────────────────────────────
 *
 *    他只看到泰文出現在畫面上。記帳是我們自己要看的帳，
 *    整個過程對使用者是隱形的 —— 這一點決定了下面兩個設計。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  設計一：為什麼要 @Transactional(REQUIRES_NEW)
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  先解釋「交易」：一組資料庫動作，要嘛全部成功、要嘛全部當作沒發生。
 *  例如「存翻譯結果 ＋ 存四個單字」應該綁在一起，
 *  不能只存到一半就斷掉，留下一筆沒有單字的翻譯。
 *
 *  預設情況下，這個 record 方法會被「拉進呼叫端的那個交易」裡一起算。
 *  那會出兩個問題：
 *
 *      (1) 記帳失敗 → 連帶把翻譯結果也一起回滾掉
 *      (2) 翻譯的後續步驟失敗 → 連帶把這筆帳也一起消失，
 *          但錢明明已經付給 OpenAI 了
 *
 *  REQUIRES_NEW 的意思是「我要自己開一個交易，不要跟呼叫端綁在一起」。
 *  兩邊的成敗互不影響。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  設計二：為什麼整段包在 try/catch 裡
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  情境：翻譯已經成功了，正要記帳，結果資料庫掛掉。
 *
 *  這時使用者該不該看到錯誤？不該。翻譯結果已經拿到了，
 *  為了「記不成帳」而讓使用者的查詢失敗，是本末倒置。
 *  所以這裡把例外吃掉，只寫進日誌。
 *
 *  ⚠ 已知缺陷（尚未修，見計畫文件已知偏離第 8 條）
 *
 *    這個 try/catch 保護得不完整。因為它寫在交易方法「內部」：
 *
 *        save 失敗 → JPA 把這個交易標記為「只能回滾」
 *                  → catch 把例外吃掉，方法正常結束
 *                  → Spring 接著要提交交易，發現已被標記
 *                  → 丟出 UnexpectedRollbackException
 *                  → ★ 這個例外還是傳到呼叫端了
 *
 *    正規寫法是拆成兩層：外層不帶交易、負責 try/catch，
 *    內層帶 REQUIRES_NEW 負責寫入。Task 8 串接 Service 時一併處理。
 *
 *  測試檔：src/test/java/com/tim/language_project/client/usage/
 *          ApiUsageRecorderTest.java
 *          （注意：那支測試沒有啟動 Spring，所以 @Transactional 不生效，
 *            它只驗得到 try/catch 有沒有作用，驗不到交易行為）
 */

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
