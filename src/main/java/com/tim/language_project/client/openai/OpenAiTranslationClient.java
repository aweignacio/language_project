package com.tim.language_project.client.openai;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  收到一句中文，回傳泰文＋拼音＋逐詞對照。實際去問 OpenAI 的就是這裡。
 *
 *  下面用「我想喝酒」走一遍完整流程，每一步標明是誰、哪個方法、
 *  以及那一刻資料長什麼樣子。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你輸入「我想喝酒」到畫面出現泰文
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在網頁輸入「我想喝酒」，按下查詢 ─────────────────────────
 *
 *    瀏覽器把這四個字送到後端的 TranslationController（Task 9 才會做）。
 *
 * ── 第 2 步｜TranslationService 先問資料庫（Task 8 才會做）──────────────
 *
 *    「這句話以前有人查過嗎？」
 *       查過了 → 直接把舊結果回傳，這個檔案完全不會被執行，不花半毛錢
 *       沒查過 → 才往下走，呼叫：
 *
 *                    translationClient.translate("我想喝酒");
 *                          ↑
 *                    注意 Service 呼叫的是 TranslationClient 這個「介面」，
 *                    它不知道背後是 OpenAI 還是別家。
 *
 * ── 第 3 步｜進到這個檔案的 translate 方法 ──────────────────────────────
 *
 *    Spring 在啟動時就把這個類別（唯一的實作）接上了那個介面，
 *    所以第 2 步那一行，實際跑的就是下面的 translate 方法。
 *
 *        public TranslationResult translate(String sourceText)
 *                                                 ↑
 *                                          值是 "我想喝酒"
 *
 *    到這裡為止，它就只是一個普通的 Java 字串。還沒有任何 JSON。
 *
 * ── 第 4 步｜交棒給 Spring AI ───────────────────────────────────────────
 *
 *        chatClient.prompt()
 *                  .user(sourceText)                        ← "我想喝酒"
 *                  .call()
 *                  .responseEntity(TranslationPayload.class)
 *
 *    這一行是分界點：往下就不是我們的程式了，是 Spring AI 這套函式庫在做事。
 *
 *    ChatClient 準備三樣東西：
 *
 *      (1) 系統提示詞 ── 就是下面那段 SYSTEM_PROMPT，
 *                       建構子裡用 .defaultSystem(...) 設定的，每次都會自動帶上
 *
 *      (2) 使用者說的話 ── "我想喝酒"
 *
 *      (3) 回覆格式規範 ── ★這個是自動產生的★
 *                       它去看 TranslationPayload（本檔案最下面那個 record）
 *                       有哪些欄位，自動寫出一段「你必須照這個格式回答」的規範。
 *                       我們一個字都沒寫。
 *
 * ── 第 5 步｜ChatModel 組出 JSON，用 HTTP 送給 OpenAI ───────────────────
 *
 *    ★★ 這裡回答「JSON 到底哪裡來的」★★
 *
 *    JSON 不是你寫的，也不是我寫的 —— 是這一步「自動組出來」的。
 *
 *    為什麼需要它：OpenAI 的伺服器在美國、是別人的機器，不是 Java 程式。
 *    兩邊要溝通，得先講好一種雙方都看得懂的文字格式，那個格式就叫 JSON。
 *    Java 物件沒辦法直接從網路線傳過去，必須先「翻譯成文字」。
 *
 *    ChatModel 把第 4 步那三樣東西翻譯成這樣一段文字：
 *
 *        {
 *          "model": "gpt-4o-mini",
 *          "messages": [
 *            { "role": "system", "content": "你是中文轉泰文的翻譯助理..." },
 *            { "role": "user",   "content": "我想喝酒" }
 *          ]
 *        }
 *
 *    然後把它送到這個網址（等同於瀏覽器打開一個網頁，只是送的是資料）：
 *
 *        https://api.openai.com/v1/chat/completions
 *
 *    金鑰（sk-...）也是在這一步從 application-local.yml 讀出來，
 *    放進請求的標頭，OpenAI 才知道這筆錢要跟誰收。
 *
 *    這整段我們都沒寫程式 —— 是 pom.xml 裡的 spring-ai-starter-model-openai
 *    在專案啟動時，讀了 application.yml 的設定自動準備好的。
 *
 * ── 第 6 步｜OpenAI 回一段 JSON ─────────────────────────────────────────
 *
 *        {
 *          "choices": [
 *            { "message": { "content": "{\"thaiText\":\"ฉันอยากดื่มเหล้า\", ...}" } }
 *          ],
 *          "usage": { "prompt_tokens": 120, "completion_tokens": 45 }
 *        }
 *
 *    ⚠ 最容易搞混的地方：content 裡面「又是一段 JSON」。
 *
 *      外層那包  = 「OpenAI 這次回應」的固定格式，永遠長這樣
 *      content   = 模型真正講的話。因為第 4 步要求它照格式回答，
 *                  所以它講的話本身又是一段 JSON
 *
 *      模型本質上只會「講話」，只會吐文字。它沒辦法直接給你 Java 物件，
 *      所以我們要求它「用 JSON 的格式講話」，這樣程式才好解析。
 *
 *    usage 那一段就是計費用的實際用量，記帳要用的就是這兩個數字。
 *
 * ── 第 7 步｜Spring AI 拆成兩個物件還給我們 ─────────────────────────────
 *
 *      ChatModel  → 把整包回應變成 ChatResponse 物件（usage 在它身上）
 *      ChatClient → 把 content 那串字，轉成 TranslationPayload 物件
 *
 *    這兩個東西一起裝在 ResponseEntity 這個「雙層盒子」裡回來：
 *
 *        response.entity()    → TranslationPayload（翻譯內容）
 *        response.response()  → ChatResponse（含 usage）
 *
 *    註：這個 ResponseEntity 跟 Controller 回傳的那個 ResponseEntity
 *        是完全不同的類別，只是剛好同名。
 *
 * ── 第 8 步｜回到我們自己的程式，做四件事 ───────────────────────────────
 *
 *      ① 取出真實 token 數（120 / 45）
 *      ② 檢查回來的東西有沒有殘缺（泰文是空的就不能給使用者）
 *      ③ 記帳：呼叫 apiUsageRecorder.record(...) 寫進 api_usage_log 表
 *      ④ 把 TranslationPayload 轉成 TranslationResult 回傳給 Service
 *
 *    第 ④ 步為什麼要換一個物件？因為 TranslationPayload 是「OpenAI 的格式」，
 *    綁著這家服務商。換成 TranslationResult 之後，Service 拿到的東西
 *    跟哪一家服務商無關，日後換服務商 Service 一行都不用改。
 *
 * ── 第 9 步｜之後（Task 8）──────────────────────────────────────────────
 *
 *    Service 拿到 TranslationResult → 存進資料庫當快取
 *                                   → 把逐詞的四個字沉澱進單字庫
 *                                   → 回給前端顯示
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  補充：一次翻譯總共出現三段 JSON
 * ══════════════════════════════════════════════════════════════════════════
 *
 *    第 5 步  送出去的那包        我們的問題         ChatModel 自動組的
 *    第 6 步  回來的那包          OpenAI 的回應格式   OpenAI 組的
 *    第 6 步  content 裡面那段    模型講的話本身      模型照我們的要求寫的
 *
 *  三段都不是手寫的，我們從頭到尾只給了一個字串「我想喝酒」，
 *  拿回一個 Java 物件。中間的翻譯工作都是 Spring AI 做的。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  補充：測試為什麼不會花錢
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  看第 5 步 —— 整條路上只有 ChatModel 那一層真的會連網路。
 *  測試時把它換成假的，叫它「有人問就直接回第 6 步那段 JSON」，
 *  第 4、7、8 步全都是真的程式在跑，只是永遠連不到 OpenAI。
 *
 *  測試檔：src/test/java/com/tim/language_project/client/openai/
 *          OpenAiTranslationClientTest.java
 *
 *  測得到：JSON 有沒有正確轉成物件、用量有沒有記對、殘缺回應有沒有丟對錯誤碼
 *  測不到：OpenAI 真的會不會照這個格式回 —— 那要填真實金鑰後手動驗證
 */

import com.tim.language_project.client.TranslationClient;
import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.client.model.TranslationWord;
import com.tim.language_project.client.usage.ApiUsageRecorder;
import com.tim.language_project.config.AiPricingProperties;
import com.tim.language_project.enums.AiProviderEnum;
import com.tim.language_project.enums.AiServiceTypeEnum;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.enums.UsageUnitTypeEnum;
import com.tim.language_project.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 以 OpenAI 對話模型實作翻譯，使用結構化輸出直接取得物件。
 */
@Slf4j
@Component
public class OpenAiTranslationClient implements TranslationClient {

    private static final String SYSTEM_PROMPT = """
            你是中文轉泰文的翻譯助理，服務對象是正在學泰文的中文使用者。

            收到一段中文後，請回傳：
            1. 整段對應的泰文
            2. 「第 1 點那段泰文」的羅馬拼音，需標註聲調符號（例如 chǎn、dùuem、lâo）
            3. 逐詞對照：把輸入依照語意切成詞，每個詞給出中文、泰文、羅馬拼音

            ★ romanization 欄位最容易搞錯，請特別注意：

            它是「泰文怎麼唸」，不是「中文怎麼唸」。
            絕對不可以填中文的漢語拼音。

              輸入「我想喝酒」→ 泰文是 ฉันอยากดื่มเหล้า
                 正確：chǎn yàak dùuem lâo    （這是泰文的唸法）
                 錯誤：wǒ xiǎng hē jiǔ         （這是中文的唸法，不要這樣寫）

            自我檢查：romanization 應該等於 words 裡每個詞的 romanization
            依序串起來的結果。對不起來就是寫錯了，請重寫。

            逐詞對照的規則：
            - 輸入若只有一個詞，words 就只有一個元素
            - 詞的順序必須與泰文語序一致
            - 每個詞的泰文必須是該詞單獨使用時的寫法

            translatable 欄位的規則（很重要，不要猜）：
            - 輸入是有意義、翻得出來的內容（包含數字，例如「5」就是「ห้า」）→ 設為 true
            - 輸入是亂碼、無意義的字串、或你無法確定它是什麼意思 → 設為 false，
              並且 thaiText、romanization 留空、words 給空陣列
            - 寧可誠實回報 false，也不要硬湊一個看起來合理的答案。
              使用者是學習者，一個編造出來的詞會被他背起來。
            """;

    /** 用來偵測泰文欄位裡有沒有混進中文字（CJK 統一表意文字的範圍）。 */
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");

    private final ChatClient chatClient;

    private final ApiUsageRecorder apiUsageRecorder;

    private final AiPricingProperties pricingProperties;

    private final String modelName;

    public OpenAiTranslationClient(ChatModel chatModel,
                                   ApiUsageRecorder apiUsageRecorder,
                                   AiPricingProperties pricingProperties,
                                   @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
                                   String modelName) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.apiUsageRecorder = apiUsageRecorder;
        this.pricingProperties = pricingProperties;
        this.modelName = modelName;
    }

    @Override
    public TranslationResult translate(String sourceText) {
        try {
            // 用 responseEntity 而不是 entity，是為了在拿到轉好的物件之外，
            // 同時拿到完整的 ChatResponse —— 真實的 token 用量只在它身上。
            ResponseEntity<ChatResponse, TranslationPayload> response = chatClient.prompt()
                    .user(sourceText)
                    .call()
                    .responseEntity(TranslationPayload.class);

            TranslationPayload payload = response.entity();
            Usage usage = usageOf(response.response());

            long inputTokens = toTokenCount(Objects.isNull(usage) ? null : usage.getPromptTokens());
            long outputTokens = toTokenCount(Objects.isNull(usage) ? null : usage.getCompletionTokens());

            if (Objects.isNull(payload)) {
                // 連物件都沒轉出來，這次呼叫仍然被收費了，用量照記但標記失敗。
                recordUsage(inputTokens, outputTokens, false);
                throw new BusinessException(ErrorCodeEnum.TRANSLATION_RESPONSE_INVALID);
            }

            // 模型自己說「這翻不出來」。這是正常的回答不是錯誤，
            // 呼叫也確實成功了，所以記成功，由呼叫端決定要怎麼回應使用者。
            //
            // 只有「明確是 false」才擋。欄位沒給（null）時當作可以翻 ——
            // 寧可讓一個可疑的結果通過，也不要把正常的翻譯誤擋下來，
            // 因為誤擋的症狀是「明明是正常的詞卻說無法翻譯」，很難查。
            if (Objects.equals(payload.translatable(), Boolean.FALSE)) {
                recordUsage(inputTokens, outputTokens, true);
                return TranslationResult.untranslatable(modelName, inputTokens, outputTokens);
            }

            if (ObjectUtils.isEmpty(payload.thaiText())
                    || ObjectUtils.isEmpty(payload.romanization())
                    || ObjectUtils.isEmpty(payload.words())) {
                // 說翻得出來卻沒給內容，這是格式錯誤。
                recordUsage(inputTokens, outputTokens, false);
                throw new BusinessException(ErrorCodeEnum.TRANSLATION_RESPONSE_INVALID);
            }

            if (containsChinese(payload)) {
                // 模型翻到一半沒翻完，泰文欄位裡混著中文字。
                // 這種半成品絕對不能存進快取與單字庫 —— 它會被使用者背起來。
                recordUsage(inputTokens, outputTokens, false);
                log.error("openai returned thai text containing chinese characters: {}",
                        payload.thaiText());
                throw new BusinessException(ErrorCodeEnum.TRANSLATION_RESPONSE_INVALID);
            }

            recordUsage(inputTokens, outputTokens, true);

            List<TranslationWord> words = payload.words().stream()
                    .map(word -> new TranslationWord(
                            word.chineseText(), word.thaiText(), word.romanization()))
                    .toList();

            return new TranslationResult(
                    payload.thaiText(), payload.romanization(), words,
                    modelName, inputTokens, outputTokens, true);

        } catch (BusinessException businessException) {
            // 已經是我們自己的錯誤碼，原封不動往外拋，不要被下面那個 catch 蓋成別的碼。
            throw businessException;
        } catch (Exception exception) {
            // 連線就失敗，沒有任何用量可言，記 0 留下「這次呼叫失敗過」的痕跡。
            recordUsage(0L, 0L, false);
            // 只記輸入長度不記內容，避免把使用者輸入寫進日誌。
            log.error("translation call failed for input length {}", sourceText.length(), exception);
            throw new BusinessException(ErrorCodeEnum.TRANSLATION_SERVICE_UNAVAILABLE, exception);
        }
    }

    /**
     * 檢查泰文欄位裡有沒有混進中文字。
     * 模型能力不足時會翻一半停手，把沒翻出來的中文原字直接貼在泰文裡，
     * 例如「ฉันอยาก吃ข้าว」。這種結果看起來很像成功，
     * 但存進去就會永久污染快取與單字庫，所以在這裡當掉。
     */
    private boolean containsChinese(TranslationPayload payload) {
        if (CHINESE_PATTERN.matcher(payload.thaiText()).find()) {
            return true;
        }

        return payload.words().stream()
                .anyMatch(word -> CHINESE_PATTERN.matcher(word.thaiText()).find());
    }

    private Usage usageOf(ChatResponse chatResponse) {
        if (Objects.isNull(chatResponse) || Objects.isNull(chatResponse.getMetadata())) {
            return null;
        }

        return chatResponse.getMetadata().getUsage();
    }

    /**
     * 把 token 數轉成記帳用的數字。取不到時回傳 0 並留下警告 ——
     * 寧可記 0 讓帳面看得出不對勁，也不要用字數估一個看起來很合理的假數字。
     */
    private long toTokenCount(Integer tokens) {
        if (Objects.isNull(tokens)) {
            log.warn("openai response carried no token usage, recording zero for this call");
            return 0L;
        }

        return tokens.longValue();
    }

    private void recordUsage(long inputTokens, long outputTokens, boolean success) {
        apiUsageRecorder.record(
                AiProviderEnum.OPENAI,
                AiServiceTypeEnum.TRANSLATION,
                modelName,
                UsageUnitTypeEnum.TOKEN,
                inputTokens,
                outputTokens,
                pricingProperties.getTranslationInputPrice(),
                pricingProperties.getTranslationOutputPrice(),
                success);
    }

    /**
     * 要求模型回傳的結構化格式。
     * 必須是一個「容器物件」—— OpenAI 的結構化輸出不接受最外層是陣列。
     */
    private record TranslationPayload(
            String thaiText,
            String romanization,
            List<WordPayload> words,
            /*
             * ★ 這裡是大寫的 Boolean，不是小寫的 boolean，這個差別很重要。
             *
             *   小寫 boolean 只有 true / false 兩種可能。
             *   模型如果漏了這個欄位沒寫，Java 會自動補 false，
             *   我們就會把一個「翻得好好的」結果誤判成「翻不出來」而擋掉。
             *
             *   大寫 Boolean 多了第三種可能：null（代表「它沒說」）。
             *   分得開之後就能這樣處理：
             *       null  → 它沒表示意見 → 當作可以翻，照常往下走
             *       false → 它明確說不行 → 才擋掉
             */
            Boolean translatable) {
    }

    private record WordPayload(
            String chineseText,
            String thaiText,
            String romanization) {
    }
}
