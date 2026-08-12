package com.tim.language_project.client.usage;

/*
 * ── 這個檔案為什麼存在？ ────────────────────────────────────────────────
 *
 *  它只有一個方法，內容只有一行 save。看起來完全多餘，但拿掉會出事。
 *
 *  原因是 Spring 的 @Transactional 的運作方式：
 *
 *    Spring 不會真的去改你的方法，而是「在外面包一層代理」。
 *    別人呼叫你的 Bean 時，其實是先呼叫到代理，
 *    代理開好交易 → 才呼叫你真正的方法 → 回來後提交交易。
 *
 *        呼叫端 →→ [代理：開交易] →→ 你的方法 →→ [代理：提交]
 *
 *  ★ 關鍵限制：同一個類別內部呼叫自己的方法（this.xxx()），
 *    是直接跳過代理的，交易完全不會啟動 —— 而且不會有任何錯誤訊息。
 *
 *  所以 ApiUsageRecorder 不能自己在內部呼叫一個 @Transactional 方法，
 *  必須交給「另一個 Bean」，這樣才會經過代理。
 *
 * ── 為什麼要 REQUIRES_NEW ───────────────────────────────────────────────
 *
 *  「我要自己開一個交易，不要跟呼叫端綁在一起。」
 *
 *  不這樣做的話會出兩個問題：
 *    (1) 記帳失敗 → 連帶把翻譯結果一起回滾掉
 *    (2) 翻譯的後續步驟失敗 → 連帶把這筆帳也消失，但錢已經付給 OpenAI 了
 *
 * ── 為什麼 try/catch 要寫在外面（ApiUsageRecorder）而不是這裡 ───────────
 *
 *  這是原本的寫法踩到的坑：try/catch 如果寫在交易方法「內部」，
 *
 *      save 失敗 → JPA 把交易標記為「只能回滾」
 *                → catch 把例外吃掉，方法正常結束
 *                → Spring 接著要提交，發現已被標記
 *                → 丟出 UnexpectedRollbackException
 *                → ★ 例外還是傳到呼叫端了，等於沒擋住
 *
 *  所以要把 try/catch 放到「交易邊界外面」—— 也就是 ApiUsageRecorder，
 *  讓它連同提交失敗一起接住。
 */

import com.tim.language_project.entity.ApiUsageLog;
import com.tim.language_project.repository.ApiUsageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用量紀錄的實際寫入者，跑在自己的獨立交易裡。
 * 刻意獨立成一個 Bean，@Transactional 才會經過代理真正生效。
 */
@Component
@RequiredArgsConstructor
public class ApiUsageLogWriter {

    private final ApiUsageLogRepository apiUsageLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(ApiUsageLog usageLog) {
        apiUsageLogRepository.save(usageLog);
    }
}
