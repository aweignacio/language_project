package com.tim.language_project.client.openai;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  看這個檔案之前，先搞懂三層「Chat 什麼」
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  下面的程式碼會出現三個長得很像的名字，其實是三層，由下往上：
 *
 *    ① OpenAI Chat API  一個網址。送 JSON 過去、回 JSON 回來。OpenAI 提供。
 *    ② ChatModel        Java 介面，把①那包 HTTP 包成一個方法。Spring AI 提供。
 *    ③ ChatClient       好用的外殼，幫忙組 prompt、把回傳的 JSON 轉成 Java 物件。
 *
 *  這個檔案只碰③，底下兩層都是別人做好的。
 *
 * ── ChatModel 這個 Bean 是誰生的？ ──────────────────────────────────────
 *
 *  pom.xml 裡的 spring-ai-starter-model-openai。Spring Boot 啟動時看到它，
 *  就自動讀 application.yml 的 spring.ai.openai.* 設定生一個出來。
 *  所以下面的建構子可以直接跟 Spring 要 ChatModel，
 *  這個檔案裡沒有任何一行連線、金鑰或 JSON 處理的程式碼。
 *
 * ── 為什麼要包一層 ChatClient？ ──────────────────────────────────────────
 *
 *  它多做兩件事：
 *
 *    (1) 記住系統提示詞（下面的 SYSTEM_PROMPT），不必每次重打
 *
 *    (2) 「結構化輸出」—— 直接給你 Java 物件，不是一坨字串
 *
 *        模型本質上只會回文字。沒有這個功能的話，我們會拿到一整段字，
 *        要自己解析「哪一段是泰文、哪一段是拼音」，模型格式一變就爆。
 *
 *        寫 .responseEntity(TranslationPayload.class) 之後，Spring AI 會：
 *          a. 看 TranslationPayload 有哪些欄位，自動產生 JSON 格式規範
 *          b. 把規範附進請求，等於跟模型說「你必須照這個格式回」
 *          c. 收到回應後，自動把 JSON 轉成 TranslationPayload 物件
 *
 *        這也是 TranslationPayload 必須是「容器物件」的原因 ——
 *        OpenAI 的結構化輸出不接受最外層是陣列，所以包一個有 words 欄位的 record。
 *
 * ── 為什麼是 responseEntity 而不是 entity？ ─────────────────────────────
 *
 *      .entity(X.class)          只給你轉好的物件，usage 被丟掉
 *      .responseEntity(X.class)  物件 ＋ 完整的 ChatResponse
 *
 *  真實的 token 用量只存在於 ChatResponse 裡：
 *      chatResponse.getMetadata().getUsage().getPromptTokens()
 *
 *  這個專案要精確記帳，所以一定要用後者。
 *  （token 是計費單位，不是字數。輸入「水」一個字，實際輸入 token 可能是 120，
 *    因為上面那段長長的系統提示詞也被算進去了。用字數估算一定不準。）
 *
 *  另外提醒：這裡的 ResponseEntity 是「裝兩樣東西的盒子」，
 *  跟 Spring MVC 那個回傳 HTTP 回應的 ResponseEntity 是不同類別，只是剛好同名。
 *
 * ── 一次翻譯的完整路徑 ──────────────────────────────────────────────────
 *
 *      TranslationService（Task 8）
 *        ↓
 *      TranslationClient        ← 我們的介面，只寫「要能翻譯」
 *        ↓
 *      OpenAiTranslationClient  ← 就是這個檔案
 *        ↓
 *      ChatClient → ChatModel → 🌐 OpenAI
 *        ↓
 *      回程：ChatResponse → TranslationPayload → TranslationResult
 *
 *  Service 只認識 TranslationClient 這個介面。哪天要換成別家翻譯服務，
 *  多寫一個實作就好，Service 一行都不用改。
 *
 * ── 測試怎麼做到不花錢？ ────────────────────────────────────────────────
 *
 *  看上面的路徑，只有 ChatModel 那一層真的會連網路。
 *  測試把它換成假的（Mockito），叫它回一段固定的 JSON 就好；
 *  ChatClient 和這個檔案全部都是真的在跑。
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
            2. 整段泰文的羅馬拼音，需標註聲調符號（例如 chǎn、dùuem、lâo）
            3. 逐詞對照：把輸入依照語意切成詞，每個詞給出中文、泰文、羅馬拼音

            逐詞對照的規則：
            - 輸入若只有一個詞，words 就只有一個元素
            - 詞的順序必須與泰文語序一致
            - 每個詞的泰文必須是該詞單獨使用時的寫法
            """;

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

            if (Objects.isNull(payload)
                    || ObjectUtils.isEmpty(payload.thaiText())
                    || ObjectUtils.isEmpty(payload.romanization())
                    || ObjectUtils.isEmpty(payload.words())) {
                // 回應格式不對，但這次呼叫確實發生過、也確實被收費了，
                // 所以用量照記，只是標記為失敗。
                recordUsage(inputTokens, outputTokens, false);
                throw new BusinessException(ErrorCodeEnum.TRANSLATION_RESPONSE_INVALID);
            }

            recordUsage(inputTokens, outputTokens, true);

            List<TranslationWord> words = payload.words().stream()
                    .map(word -> new TranslationWord(
                            word.chineseText(), word.thaiText(), word.romanization()))
                    .toList();

            return new TranslationResult(
                    payload.thaiText(), payload.romanization(), words,
                    modelName, inputTokens, outputTokens);

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
            List<WordPayload> words) {
    }

    private record WordPayload(
            String chineseText,
            String thaiText,
            String romanization) {
    }
}
