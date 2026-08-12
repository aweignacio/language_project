# 中泰語言學習網站 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立一個網站，使用者輸入中文後回傳對應泰文、羅馬拼音、逐詞對照與整句發音音檔，並將結果永久快取避免重複付費呼叫外部服務。

**Architecture:** Controller → Service → Repository 三層。外部服務（翻譯、語音）抽成介面隔離於 `client` 層，可抽換服務商。查詢先讀 SQL Server 快取，未命中才呼叫 OpenAI，結果寫回資料庫與本機音檔資料夾。

**Tech Stack:** Spring Boot 4.1.0 / Java 21 / Spring Data JPA / SQL Server 2022（Docker）/ Spring AI 2.0.0（OpenAI）/ Lombok / 靜態 HTML + JavaScript

**規格文件：** `docs/superpowers/specs/2026-08-11-thai-learning-design.md`

---

## 執行前必讀

### 已完成，不需重做

| 項目 | 狀態 |
|---|---|
| Git 版控 + GitHub remote (`origin`) | 完成，主分支 `main` |
| SQL Server 容器 | `C:\Tim\docker\compose.yaml`，群組 `shared-db`，容器名 `sqlserver`，`localhost:1433` |
| 資料庫 `language_project` | 已建立 |
| 四張資料表 | 已由 `db/schema.sql` 建立並驗證 |
| `pom.xml` | 依賴已整理，Spring AI 為 OpenAI |
| `application.yml` / `application-local.yml` | 已建立，機密不進版控 |
| **Task 1：建立 Enum** | 完成，分支 `feat/enums`（commit `8ff54f9`） |
| **Task 2：建立 Entity** | 完成，分支 `feat/entities`（commit `9f2d7ae`） |
| **Task 3：DTO 與 Repository** | 完成，分支 `feat/repositories`（commit `e37003f`），測試 5 項全過 |
| **Task 4：錯誤處理骨架** | 完成，分支 `feat/error_handle`（commit `784b34b`、`72542d9`），測試 9 項全過 |
| **Task 5：外部服務介面與用量記錄** | 完成，分支 `feat/client-contracts`（commit `043b4c6`、`1f5af44`），測試 12 項全過 |
| **Task 6：OpenAI 翻譯實作** | 程式完成、測試 16 項全過，**尚未 commit**，等 Awei 確認 |

**下一個要做的是 Task 7。**

**分支現況：** 逐層疊加，皆未推上 origin、未合併回 main。
`main` → `feat/enums` → `feat/entities` → `feat/repositories` → `feat/error_handle`
→ `feat/client-contracts` → `feat/openai-translation`
（Task 4 分支名為 `feat/error_handle`，與本文件寫的 `feat/error-handling` 不同，以實際分支為準。）

### 執行過程中發現的偏離（本文件其餘部分尚未修正，實作時以此處為準）

1. **所有測試的 import 路徑要改。** 本文件寫的是 Spring Boot 3.x 路徑，在 4.1 完全不存在，
   第一次執行會出現整批 `package does not exist`。正確路徑：

   | 註解 | 本文件寫的（錯） | 4.1 正確路徑 |
   |---|---|---|
   | `@DataJpaTest` | `...boot.test.autoconfigure.orm.jpa` | `org.springframework.boot.data.jpa.test.autoconfigure` |
   | `@AutoConfigureTestDatabase` | `...boot.test.autoconfigure.jdbc` | `org.springframework.boot.jdbc.test.autoconfigure` |
   | `@WebMvcTest`（Task 9 會用到） | `...boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |

   `@MockitoBean` 不受影響，本文件寫的是對的。

2. **Task 7 Step 4 的第一個方案是錯的。** `spring.web.resources.static-locations` 加 `file:audio/`
   是掛在 `/**` 底下，`/audio/x.mp3` 會被解析成 `audio/audio/x.mp3`，取不到檔。
   直接使用該步驟的備案 `WebMvcConfig`，跳過該 yaml 設定。

3. **Task 10 的 `app.js` 有 XSS 破口。** 原始碼用 `innerHTML` 拼字串塞入 API 回傳值，
   使用者輸入會被當 HTML 執行。改用 `createElement` + `textContent`，畫面完全相同。

4. **`application-local.yml` 的 OpenAI api-key 目前是佔位字串。**
   Task 6 Step 4 與 Task 10 Step 4 的「手動驗證」在補上真實金鑰前無法執行，
   需由 Awei 自行完成。自動化測試本來就禁止呼叫真實 API，不受影響。

5. **Task 4 的 `GlobalExceptionHandler` 有缺陷，已於 commit `72542d9` 修正。**
   `@ExceptionHandler(Exception.class)` 會攔下 Spring 自己丟的例外，
   導致「網址不存在」「HTTP 方法不支援」被回報成 500。
   修法：先判斷 `exception instanceof ErrorResponse`，是的話沿用它身上的狀態碼，
   只有問不出狀態碼的例外才回 500。新增 `RESOURCE_NOT_FOUND`、`METHOD_NOT_ALLOWED`、
   `REQUEST_INVALID` 三個錯誤碼對應。
   **Task 9 撰寫 Controller 測試時，請沿用既有的 `GlobalExceptionHandlerTest`**
   （已含 4 個測試與一個測試用假 Controller），不要重寫。

6. **註解語言改為繁體中文**（Awei 於 2026-08-11 要求，全域 CLAUDE.md 已同步修改）。
   Task 1-4 既有程式的註解已全部改寫完成。
   本文件 Task 5 以後的程式碼範例仍是英文註解，實作時一律改寫成中文。

7. **`mvnw test` 出現 `Unresolved compilation problem` 時，改用 `mvnw clean test`。**
   IDE 會在背景把沒跑 Lombok 的壞 class 寫進 `target/`，Maven 判定「不用重編」而沿用，
   產生看似無法解釋的失敗。IDE 在 Lombok 相關程式上標的紅字同樣不是真錯誤，一律以 Maven 為準。

8. **`ApiUsageRecorder` 的 try/catch 保護不完整（尚未處理，待 Awei 決定）。**
   `try/catch` 寫在 `@Transactional(REQUIRES_NEW)` 方法「內部」。若 `save` 失敗，
   JPA 會把該交易標記為 rollback-only，方法正常返回後 Spring 提交時仍會丟出
   `UnexpectedRollbackException`，一樣會傳到呼叫端 —— 與「記帳絕不影響主流程」的原意不符。
   一般寫法是拆成兩個方法：外層不帶交易、負責 try/catch，內層帶 `REQUIRES_NEW` 負責寫入。
   Task 8 串接 Service 時可一併處理。

9. **Task 6 改用真實 token 用量，不採計畫的字數估算。**
   計畫原本以 `sourceText.length()` 當 token 數，是猜的，帳會對不起來。
   改用 `.call().responseEntity(TranslationPayload.class)`（而非 `.entity()`），
   可同時取得轉好的物件與完整 `ChatResponse`，真實用量在
   `chatResponse.getMetadata().getUsage()` 的 `getPromptTokens()` / `getCompletionTokens()`。
   取不到時記 0 並留 warn，不用估算值填充。
   另外，回應格式不合法時（`TRANSLATION_RESPONSE_INVALID`）用量照記，因為那次呼叫確實已被收費。

10. **Task 6 加了 `OpenAiTranslationClientTest`（4 項），未違反「禁止呼叫真實 API」。**
    做法是把 `ChatModel` 換成 Mockito 假物件，離線、免金鑰、不花錢。
    **測試必備**：要 stub `chatModel.getOptions()` 回傳 `ChatOptions.builder().build()`，
    否則 `DefaultChatClientUtils.toChatClientRequest` 會 NPE。
    注意是 `getOptions()`，不是已棄用的 `getDefaultOptions()`。

11. **【Task 8 待辦】輸入驗證的決策（Awei 於 2026-08-12 決定）。**

    現況：`INPUT_REQUIRED`、`INPUT_TOO_LONG`、`INPUT_UNSUPPORTED_CONTENT` 三個錯誤碼
    已定義但**沒有任何程式在用**，等於完全沒有輸入驗證。
    因此輸入「嘎逼」「asdfgh」時，AI 會掰一個泰文出來（模型不會說「我不知道」），
    而且會被**永久寫進快取、沉澱進單字庫**，之後每次查都回那個錯的答案。

    Awei 的決定：
    - **空字串要擋** → 丟 `INPUT_REQUIRED`，在 Service 進來就擋，不花錢呼叫 AI
    - **「翻不翻得出來」交給 AI 判斷** → 在結構化輸出的 record 加一個欄位
      （例如 `boolean translatable`），系統提示詞明確要求「輸入不是有意義的中文詞句時設 false」。
      收到 false 就丟 `INPUT_UNSUPPORTED_CONTENT`，**不寫快取、不沉澱單字**。
    - 這兩項都在 **Task 8** 做。

    `INPUT_TOO_LONG` 也建議一併補上 —— `source_text` 欄位只有 NVARCHAR(100)，
    超長輸入會在寫入時失敗，而錢已經先花掉了。

### 執行方式

Awei 要求：**每個 Task 完成後停下來回報，經他確認後才進行下一個 Task。**
不推 origin、不開 PR，分支繼續往下疊。

**⚠ 絕對不可自行 commit。** 各 Task 最後一步雖然寫著「Commit」，但實作者只做到
「改完檔案、測試跑過、回報結果」為止，**停在未提交狀態**，等 Awei 看過並明確說可以，才執行 commit。
分支可以先開，程式可以先改，就是不能自己提交。

**資料庫連線資訊**（已寫在 `src/main/resources/application-local.yml`）：
`localhost:1433` / `sa` / `Sqlserver123456` / 資料庫 `language_project`

### 硬性規範（違反即為錯誤）

1. **所有存放文字的欄位必須是 `NVARCHAR`。** Entity 一律標註 `columnDefinition = "NVARCHAR(n)"`。省略的話 Hibernate 對 SQL Server 產生 `VARCHAR`，中文與泰文會靜默變成 `?`。
2. **禁用 JPA 關聯註解**（`@ManyToOne`、`@OneToMany`、`@JoinColumn`）。外鍵是單純欄位（`Long queryId`），關聯由 Service 層自行查詢組裝。
3. **複合主鍵使用 `@IdClass`**，不用 `@EmbeddedId`。Entity 欄位保持扁平。
4. **查詢一律使用 DTO class 投影**（建構子表達式），**禁止介面投影**。
5. **JPQL 使用 Java Text Block，各關鍵字子句之間空一行。**
6. **Entity 類別名稱不加 `Entity` 後綴。** Enum 類別名稱必須以 `Enum` 結尾並標 `@Getter`。
7. **註解使用繁體中文 Javadoc，不使用 `<p>` 標籤。** 技術名詞（類別名、註解名、`null`、`token`、HTTP 狀態碼）保持原文不翻譯。本文件後續 Task 的程式碼範例仍是英文註解，實作時一律改寫成中文。
8. **null 判斷使用 `Objects.isNull` / `Objects.nonNull` 或 `ObjectUtils.isEmpty` / `isNotEmpty`；相等比較使用 `Objects.equals`。禁止 `== null`、`!= null`、直接 `.equals()`。**
9. **Lambda 參數使用有意義的名稱**，禁止單字母。
10. **金額一律 `BigDecimal`**，禁止 `float` / `double`。
11. **禁止 `--no-verify`。**
12. **自動化測試禁止實際呼叫 OpenAI。**

### 測試策略的一項刻意偏離

規格原訂 Repository 測試使用 H2。**本計畫改為對真實 SQL Server 測試**，理由：H2 不會重現 `NVARCHAR` 與 `VARCHAR` 的差異，用 H2 測不出本專案最關鍵的資料損毀風險。

作法：`@DataJpaTest` 搭配 `@AutoConfigureTestDatabase(replace = Replace.NONE)`。`@DataJpaTest` 預設每個測試方法都在交易中執行並於結束時回滾，不會留下測試資料。

**代價：執行測試前 SQL Server 容器必須是啟動狀態。**

### 執行測試的指令

```powershell
cd C:\Tim\language_project
.\mvnw.cmd -B test                              # 全部測試
.\mvnw.cmd -B test -Dtest=VocabularyRepositoryTest   # 單一測試類別
```

### 分支策略

**每個 Task 一支分支，一個 commit。** 完成後推送並開 PR 合併，流程：

```powershell
git switch main
git pull
git switch -c <分支名>
# ...實作、測試...
git add <檔案>
git commit -m "..."
git push -u origin <分支名>
# GitHub 開 PR → 審查 → Merge → Delete branch
git switch main
git pull
git branch -d <分支名>
```

---

## 檔案結構

```
src/main/java/com/tim/language_project/
├── LanguageProjectApplication.java              （既有）
├── enums/
│   ├── VocabularySourceTypeEnum.java            單字來源
│   ├── AiProviderEnum.java                      服務商
│   ├── AiServiceTypeEnum.java                   服務種類
│   ├── UsageUnitTypeEnum.java                   計費單位
│   ├── SpeechFailureReasonEnum.java             語音失敗原因（內部用，不外拋）
│   └── ErrorCodeEnum.java                       對外錯誤碼
├── entity/
│   ├── TranslationQuery.java
│   ├── TranslationSegment.java
│   ├── TranslationSegmentId.java                複合主鍵
│   ├── Vocabulary.java
│   └── ApiUsageLog.java
├── repository/
│   ├── TranslationQueryRepository.java
│   ├── TranslationSegmentRepository.java
│   ├── VocabularyRepository.java
│   └── ApiUsageLogRepository.java
├── dto/
│   ├── request/
│   │   └── TranslationRequestDto.java
│   └── response/
│       ├── TranslationResponseDto.java          API 回應主體
│       ├── TranslationSegmentDto.java           逐詞對照
│       ├── TranslationQueryDto.java             快取查詢投影
│       ├── VocabularyDto.java                   單字投影
│       └── ErrorResponseDto.java                錯誤回應
├── exception/
│   ├── BusinessException.java
│   └── GlobalExceptionHandler.java
├── client/
│   ├── TranslationClient.java                   翻譯介面
│   ├── SpeechClient.java                        語音介面
│   ├── model/
│   │   ├── TranslationResult.java               翻譯結果（含逐詞）
│   │   └── TranslationWord.java                 單一詞
│   ├── openai/
│   │   ├── OpenAiTranslationClient.java
│   │   └── OpenAiSpeechClient.java
│   └── usage/
│       └── ApiUsageRecorder.java                用量與費用記錄
├── config/
│   ├── AiPricingProperties.java                 單價設定
│   └── AudioStorageProperties.java              音檔資料夾設定
├── service/
│   └── TranslationService.java                  主流程
└── controller/
    └── TranslationController.java

src/main/resources/
├── application.yml                              （既有，本計畫會擴充）
├── application-local.yml                        （既有，不進版控）
└── static/
    ├── index.html                               查詢頁面
    ├── app.js
    └── style.css

src/test/java/com/tim/language_project/
├── repository/
│   ├── TranslationQueryRepositoryTest.java
│   ├── TranslationSegmentRepositoryTest.java
│   ├── VocabularyRepositoryTest.java
│   └── ApiUsageLogRepositoryTest.java
├── service/
│   └── TranslationServiceTest.java
└── controller/
    └── TranslationControllerTest.java
```

---

# Task 1：建立 Enum　✅ 已完成

**分支：** `feat/enums`

**Files:**
- Create: `src/main/java/com/tim/language_project/enums/VocabularySourceTypeEnum.java`
- Create: `src/main/java/com/tim/language_project/enums/AiProviderEnum.java`
- Create: `src/main/java/com/tim/language_project/enums/AiServiceTypeEnum.java`
- Create: `src/main/java/com/tim/language_project/enums/UsageUnitTypeEnum.java`
- Create: `src/main/java/com/tim/language_project/enums/SpeechFailureReasonEnum.java`

Enum 沒有邏輯，不需要單元測試；正確性由後續 Entity 測試間接驗證。

- [ ] **Step 1：建立 `VocabularySourceTypeEnum`**

```java
package com.tim.language_project.enums;

import lombok.Getter;

/**
 * Indicates how a vocabulary entry was collected.
 */
@Getter
public enum VocabularySourceTypeEnum {

    /** Extracted from the segmentation of a multi-word sentence. */
    SEGMENT("由句子拆解而來"),

    /** The user queried this exact word on its own. */
    DIRECT("使用者直接查詢");

    private final String description;

    VocabularySourceTypeEnum(String description) {
        this.description = description;
    }
}
```

- [ ] **Step 2：建立 `AiProviderEnum`**

```java
package com.tim.language_project.enums;

import lombok.Getter;

/**
 * External AI service providers used by this application.
 */
@Getter
public enum AiProviderEnum {

    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GOOGLE("Google Cloud"),
    AZURE("Microsoft Azure");

    private final String displayName;

    AiProviderEnum(String displayName) {
        this.displayName = displayName;
    }
}
```

- [ ] **Step 3：建立 `AiServiceTypeEnum`**

```java
package com.tim.language_project.enums;

import lombok.Getter;

/**
 * Types of external AI service calls that are billed separately.
 */
@Getter
public enum AiServiceTypeEnum {

    /** Chinese to Thai translation with romanization and segmentation. */
    TRANSLATION("翻譯"),

    /** Thai text to audio synthesis. */
    SPEECH("語音合成");

    private final String description;

    AiServiceTypeEnum(String description) {
        this.description = description;
    }
}
```

- [ ] **Step 4：建立 `UsageUnitTypeEnum`**

```java
package com.tim.language_project.enums;

import lombok.Getter;

/**
 * Billing unit used by an external service. Chat models bill per token,
 * speech synthesis bills per character.
 */
@Getter
public enum UsageUnitTypeEnum {

    TOKEN("Token"),
    CHARACTER("字元");

    private final String description;

    UsageUnitTypeEnum(String description) {
        this.description = description;
    }
}
```

- [ ] **Step 5：建立 `SpeechFailureReasonEnum`**

```java
package com.tim.language_project.enums;

import lombok.Getter;

/**
 * Reason a speech synthesis attempt failed. Recorded for diagnostics only —
 * speech failures never propagate to the caller, the translation result is
 * returned with a null audio file instead.
 */
@Getter
public enum SpeechFailureReasonEnum {

    CONNECTION_FAILED("無法連線至語音服務"),
    TIMEOUT("語音服務回應逾時"),
    QUOTA_EXCEEDED("語音服務額度不足"),
    FILE_SAVE_FAILED("音檔存檔失敗"),
    UNKNOWN("未知原因");

    private final String description;

    SpeechFailureReasonEnum(String description) {
        this.description = description;
    }
}
```

- [ ] **Step 6：編譯驗證**

Run: `.\mvnw.cmd -B -q compile`
Expected: 無錯誤輸出，BUILD SUCCESS

- [ ] **Step 7：Commit**

```bash
git add src/main/java/com/tim/language_project/enums/
git commit -m "$(cat <<'EOF'
新增資料層列舉

Feat:
- 新增 VocabularySourceTypeEnum、AiProviderEnum、AiServiceTypeEnum
- 新增 UsageUnitTypeEnum、SpeechFailureReasonEnum
EOF
)"
```

---

# Task 2：建立 Entity　✅ 已完成

**分支：** `feat/entities`

**Files:**
- Create: `src/main/java/com/tim/language_project/entity/TranslationQuery.java`
- Create: `src/main/java/com/tim/language_project/entity/TranslationSegmentId.java`
- Create: `src/main/java/com/tim/language_project/entity/TranslationSegment.java`
- Create: `src/main/java/com/tim/language_project/entity/Vocabulary.java`
- Create: `src/main/java/com/tim/language_project/entity/ApiUsageLog.java`

- [ ] **Step 1：建立 `TranslationQuery`**

```java
package com.tim.language_project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Cached translation result keyed by the raw text the user submitted.
 * This is the only table that owns an audio file.
 */
@Entity
@Table(name = "translation_query")
@Getter
@Setter
@NoArgsConstructor
public class TranslationQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Raw Chinese input, trimmed before persisting. Unique. */
    @Column(name = "source_text", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String sourceText;

    @Column(name = "thai_text", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String thaiText;

    @Column(name = "romanization", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String romanization;

    /**
     * Generated audio file name. Null when speech synthesis failed —
     * the translation is still returned, only the play button is hidden.
     */
    @Column(name = "audio_file", length = 100)
    private String audioFile;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2：建立複合主鍵類別 `TranslationSegmentId`**

```java
package com.tim.language_project.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite identity for {@link TranslationSegment}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TranslationSegmentId implements Serializable {

    private Long queryId;

    private Integer seqNo;
}
```

- [ ] **Step 3：建立 `TranslationSegment`**

```java
package com.tim.language_project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One word of the segmentation of a query. Records how a specific sentence was
 * split, so the same word may appear across many queries.
 * The parent is referenced by a plain identifier column, not a JPA association.
 */
@Entity
@Table(name = "translation_segment")
@IdClass(TranslationSegmentId.class)
@Getter
@Setter
@NoArgsConstructor
public class TranslationSegment {

    @Id
    @Column(name = "query_id")
    private Long queryId;

    /** Display order, starting from 1. */
    @Id
    @Column(name = "seq_no")
    private Integer seqNo;

    @Column(name = "chinese_text", columnDefinition = "NVARCHAR(50)", nullable = false)
    private String chineseText;

    @Column(name = "thai_text", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String thaiText;

    @Column(name = "romanization", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String romanization;
}
```

- [ ] **Step 4：建立 `Vocabulary`**

```java
package com.tim.language_project.entity;

import com.tim.language_project.enums.VocabularySourceTypeEnum;
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

import java.time.LocalDateTime;

/**
 * Chinese to Thai dictionary entry accumulated from query segmentation.
 * Holds no audio — only the query cache owns audio files.
 */
@Entity
@Table(name = "vocabulary")
@Getter
@Setter
@NoArgsConstructor
public class Vocabulary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "chinese_text", columnDefinition = "NVARCHAR(50)", nullable = false)
    private String chineseText;

    @Column(name = "thai_text", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String thaiText;

    @Column(name = "romanization", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String romanization;

    /** Kept as first written; never updated for an existing entry. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20, nullable = false)
    private VocabularySourceTypeEnum sourceType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 5：建立 `ApiUsageLog`**

```java
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
 * One external service call with its usage and cost. Audit data — kept even when
 * the referenced query is deleted, hence no foreign key constraint.
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

    /** Loose reference to the query this call served. Null when unknown. */
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

    /** Always zero for speech synthesis. */
    @Column(name = "output_units", nullable = false)
    private Long outputUnits;

    /** Unit price at the time of the call, so historical rows stay auditable. */
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
```

- [ ] **Step 6：編譯驗證**

Run: `.\mvnw.cmd -B -q compile`
Expected: 無錯誤輸出

- [ ] **Step 7：Commit**

```bash
git add src/main/java/com/tim/language_project/entity/
git commit -m "$(cat <<'EOF'
新增四張資料表對應的 Entity

Feat:
- 新增 TranslationQuery、TranslationSegment、Vocabulary、ApiUsageLog
- TranslationSegment 以 @IdClass 實作複合主鍵，不使用關聯註解
- 所有文字欄位標註 NVARCHAR，避免中文與泰文靜默損毀
EOF
)"
```

---

# Task 3：建立 DTO 與 Repository　✅ 已完成

**分支：** `feat/repositories`

> 測試檔另加了給 Awei 看的中文教學註解（commit `bbcbca5`），非計畫內容，可隨時移除。

**Files:**
- Create: `src/main/java/com/tim/language_project/dto/response/TranslationQueryDto.java`
- Create: `src/main/java/com/tim/language_project/dto/response/TranslationSegmentDto.java`
- Create: `src/main/java/com/tim/language_project/dto/response/VocabularyDto.java`
- Create: `src/main/java/com/tim/language_project/repository/TranslationQueryRepository.java`
- Create: `src/main/java/com/tim/language_project/repository/TranslationSegmentRepository.java`
- Create: `src/main/java/com/tim/language_project/repository/VocabularyRepository.java`
- Create: `src/main/java/com/tim/language_project/repository/ApiUsageLogRepository.java`
- Test: `src/test/java/com/tim/language_project/repository/TranslationQueryRepositoryTest.java`
- Test: `src/test/java/com/tim/language_project/repository/VocabularyRepositoryTest.java`

- [ ] **Step 1：建立三個查詢投影 DTO**

`TranslationQueryDto.java`：

```java
package com.tim.language_project.dto.response;

/**
 * Projection of a cached query row. Used as a JPQL constructor expression target.
 */
public record TranslationQueryDto(
        Long id,
        String sourceText,
        String thaiText,
        String romanization,
        String audioFile) {
}
```

`TranslationSegmentDto.java`：

```java
package com.tim.language_project.dto.response;

/**
 * One word of a segmentation, as returned to the caller.
 */
public record TranslationSegmentDto(
        Integer seqNo,
        String chineseText,
        String thaiText,
        String romanization) {
}
```

`VocabularyDto.java`：

```java
package com.tim.language_project.dto.response;

/**
 * Projection of a dictionary entry.
 */
public record VocabularyDto(
        Long id,
        String chineseText,
        String thaiText,
        String romanization) {
}
```

- [ ] **Step 2：先寫失敗的測試 — `TranslationQueryRepositoryTest`**

```java
package com.tim.language_project.repository;

import com.tim.language_project.dto.response.TranslationQueryDto;
import com.tim.language_project.entity.TranslationQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TranslationQueryRepositoryTest {

    @Autowired
    private TranslationQueryRepository translationQueryRepository;

    @Test
    @DisplayName("泰文與拼音寫入後讀回不應損毀")
    void shouldPreserveThaiTextAndRomanization() {
        TranslationQuery query = new TranslationQuery();
        query.setSourceText("我想喝酒");
        query.setThaiText("ฉันอยากดื่มเหล้า");
        query.setRomanization("chǎn yàak dùuem lâo");
        query.setAudioFile("a3f9c2.mp3");

        translationQueryRepository.saveAndFlush(query);

        Optional<TranslationQueryDto> found =
                translationQueryRepository.findBySourceText("我想喝酒");

        assertThat(found).isPresent();
        assertThat(found.get().thaiText()).isEqualTo("ฉันอยากดื่มเหล้า");
        assertThat(found.get().romanization()).isEqualTo("chǎn yàak dùuem lâo");
        assertThat(found.get().sourceText()).isEqualTo("我想喝酒");
    }

    @Test
    @DisplayName("音檔為 null 時仍可正常寫入與讀取")
    void shouldAllowNullAudioFile() {
        TranslationQuery query = new TranslationQuery();
        query.setSourceText("水");
        query.setThaiText("น้ำ");
        query.setRomanization("náam");
        query.setAudioFile(null);

        translationQueryRepository.saveAndFlush(query);

        Optional<TranslationQueryDto> found = translationQueryRepository.findBySourceText("水");

        assertThat(found).isPresent();
        assertThat(found.get().audioFile()).isNull();
    }
}
```

- [ ] **Step 3：執行測試確認失敗**

Run: `.\mvnw.cmd -B test -Dtest=TranslationQueryRepositoryTest`
Expected: 編譯失敗，`TranslationQueryRepository` 不存在

- [ ] **Step 4：建立 `TranslationQueryRepository`**

```java
package com.tim.language_project.repository;

import com.tim.language_project.dto.response.TranslationQueryDto;
import com.tim.language_project.entity.TranslationQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Data access for the query result cache.
 */
public interface TranslationQueryRepository extends JpaRepository<TranslationQuery, Long> {

    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationQueryDto(
                translationQuery.id,
                translationQuery.sourceText,
                translationQuery.thaiText,
                translationQuery.romanization,
                translationQuery.audioFile
            )

            FROM TranslationQuery translationQuery

            WHERE translationQuery.sourceText = :sourceText
            """)
    Optional<TranslationQueryDto> findBySourceText(@Param("sourceText") String sourceText);
}
```

- [ ] **Step 5：執行測試確認通過**

Run: `.\mvnw.cmd -B test -Dtest=TranslationQueryRepositoryTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6：建立 `TranslationSegmentRepository`**

```java
package com.tim.language_project.repository;

import com.tim.language_project.dto.response.TranslationSegmentDto;
import com.tim.language_project.entity.TranslationSegment;
import com.tim.language_project.entity.TranslationSegmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Data access for per-query word segmentation.
 */
public interface TranslationSegmentRepository
        extends JpaRepository<TranslationSegment, TranslationSegmentId> {

    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationSegmentDto(
                translationSegment.seqNo,
                translationSegment.chineseText,
                translationSegment.thaiText,
                translationSegment.romanization
            )

            FROM TranslationSegment translationSegment

            WHERE translationSegment.queryId = :queryId

            ORDER BY translationSegment.seqNo
            """)
    List<TranslationSegmentDto> findByQueryIdOrderBySeqNo(@Param("queryId") Long queryId);
}
```

- [ ] **Step 7：先寫失敗的測試 — `VocabularyRepositoryTest`**

```java
package com.tim.language_project.repository;

import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.entity.Vocabulary;
import com.tim.language_project.enums.VocabularySourceTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VocabularyRepositoryTest {

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Test
    @DisplayName("依中文詞查詢應回傳對應泰文")
    void shouldFindByChineseText() {
        vocabularyRepository.saveAndFlush(
                buildVocabulary("酒", "เหล้า", "lâo", VocabularySourceTypeEnum.SEGMENT));

        Optional<VocabularyDto> found = vocabularyRepository.findByChineseText("酒");

        assertThat(found).isPresent();
        assertThat(found.get().thaiText()).isEqualTo("เหล้า");
        assertThat(found.get().romanization()).isEqualTo("lâo");
    }

    @Test
    @DisplayName("批次查詢已存在的中文詞，供沉澱單字時過濾重複")
    void shouldFindExistingChineseTexts() {
        vocabularyRepository.saveAndFlush(
                buildVocabulary("我", "ฉัน", "chǎn", VocabularySourceTypeEnum.SEGMENT));
        vocabularyRepository.saveAndFlush(
                buildVocabulary("水", "น้ำ", "náam", VocabularySourceTypeEnum.SEGMENT));

        List<String> existing =
                vocabularyRepository.findExistingChineseTexts(List.of("我", "水", "沒有這個詞"));

        assertThat(existing).containsExactlyInAnyOrder("我", "水");
    }

    private Vocabulary buildVocabulary(String chineseText, String thaiText,
                                       String romanization, VocabularySourceTypeEnum sourceType) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setChineseText(chineseText);
        vocabulary.setThaiText(thaiText);
        vocabulary.setRomanization(romanization);
        vocabulary.setSourceType(sourceType);
        return vocabulary;
    }
}
```

- [ ] **Step 8：執行測試確認失敗**

Run: `.\mvnw.cmd -B test -Dtest=VocabularyRepositoryTest`
Expected: 編譯失敗，`VocabularyRepository` 不存在

- [ ] **Step 9：建立 `VocabularyRepository`**

```java
package com.tim.language_project.repository;

import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.entity.Vocabulary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the accumulated Chinese to Thai dictionary.
 */
public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {

    @Query("""
            SELECT new com.tim.language_project.dto.response.VocabularyDto(
                vocabulary.id,
                vocabulary.chineseText,
                vocabulary.thaiText,
                vocabulary.romanization
            )

            FROM Vocabulary vocabulary

            WHERE vocabulary.chineseText = :chineseText
            """)
    Optional<VocabularyDto> findByChineseText(@Param("chineseText") String chineseText);

    @Query("""
            SELECT vocabulary.chineseText

            FROM Vocabulary vocabulary

            WHERE vocabulary.chineseText IN :chineseTexts
            """)
    List<String> findExistingChineseTexts(
            @Param("chineseTexts") Collection<String> chineseTexts);

    @Query("""
            SELECT new com.tim.language_project.dto.response.VocabularyDto(
                vocabulary.id,
                vocabulary.chineseText,
                vocabulary.thaiText,
                vocabulary.romanization
            )

            FROM Vocabulary vocabulary

            ORDER BY vocabulary.id DESC
            """)
    Page<VocabularyDto> findAllOrderByIdDesc(Pageable pageable);
}
```

- [ ] **Step 10：建立 `ApiUsageLogRepository`**

```java
package com.tim.language_project.repository;

import com.tim.language_project.entity.ApiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for API usage and cost records.
 */
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {
}
```

- [ ] **Step 11：執行全部測試**

Run: `.\mvnw.cmd -B test`
Expected: 全部通過，`Failures: 0, Errors: 0`

- [ ] **Step 12：Commit**

```bash
git add src/main/java/com/tim/language_project/dto/ src/main/java/com/tim/language_project/repository/ src/test/java/com/tim/language_project/repository/
git commit -m "$(cat <<'EOF'
新增查詢投影 DTO 與 Repository

Feat:
- 新增 TranslationQueryDto、TranslationSegmentDto、VocabularyDto
- 新增四個 Repository，查詢一律使用 DTO class 投影
- 新增 Repository 測試，驗證泰文與拼音讀寫無損
EOF
)"
```

---

# Task 4：錯誤處理骨架　✅ 已完成

**分支：** `feat/error-handling`

**Files:**
- Create: `src/main/java/com/tim/language_project/enums/ErrorCodeEnum.java`
- Create: `src/main/java/com/tim/language_project/exception/BusinessException.java`
- Create: `src/main/java/com/tim/language_project/dto/response/ErrorResponseDto.java`
- Create: `src/main/java/com/tim/language_project/exception/GlobalExceptionHandler.java`

- [ ] **Step 1：建立 `ErrorCodeEnum`**

```java
package com.tim.language_project.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * All business error codes with their HTTP status and user-facing message.
 * The global exception handler maps these directly onto the response.
 */
@Getter
public enum ErrorCodeEnum {

    INPUT_REQUIRED(HttpStatus.BAD_REQUEST, "輸入內容不可為空"),
    INPUT_TOO_LONG(HttpStatus.BAD_REQUEST, "輸入內容不可超過 100 字"),
    INPUT_UNSUPPORTED_CONTENT(HttpStatus.BAD_REQUEST, "輸入內容無法翻譯"),

    TRANSLATION_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "翻譯服務暫時無法使用"),
    TRANSLATION_SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "翻譯服務回應逾時"),
    TRANSLATION_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "翻譯服務回傳資料格式錯誤"),
    TRANSLATION_QUOTA_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "翻譯服務額度不足"),
    TRANSLATION_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "請求過於頻繁，請稍後再試"),

    VOCABULARY_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到指定的單字"),
    AUDIO_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到音檔"),

    DATA_PERSIST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "資料儲存失敗"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系統發生非預期錯誤");

    private final HttpStatus httpStatus;

    private final String message;

    ErrorCodeEnum(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
```

- [ ] **Step 2：建立 `BusinessException`**

```java
package com.tim.language_project.exception;

import com.tim.language_project.enums.ErrorCodeEnum;
import lombok.Getter;

/**
 * Application exception carrying a predefined error code. The global handler
 * derives the HTTP status and message from the code.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCodeEnum errorCode;

    public BusinessException(ErrorCodeEnum errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCodeEnum errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
```

- [ ] **Step 3：建立 `ErrorResponseDto`**

```java
package com.tim.language_project.dto.response;

/**
 * Uniform error payload. The trace identifier lets a user report locate the
 * matching server log entry.
 */
public record ErrorResponseDto(
        String code,
        String message,
        String traceId) {
}
```

- [ ] **Step 4：建立 `GlobalExceptionHandler`**

```java
package com.tim.language_project.exception;

import com.tim.language_project.dto.response.ErrorResponseDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Translates exceptions into the uniform error payload.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessException(BusinessException exception) {
        ErrorCodeEnum errorCode = exception.getErrorCode();
        String traceId = newTraceId();

        log.warn("[{}] business error: {}", traceId, errorCode.name(), exception);

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorResponseDto(errorCode.name(), errorCode.getMessage(), traceId));
    }

    /**
     * Last-resort handler. The original exception message is never returned to the
     * caller — it may contain connection strings, file paths, or credential fragments.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpectedException(Exception exception) {
        ErrorCodeEnum errorCode = ErrorCodeEnum.INTERNAL_ERROR;
        String traceId = newTraceId();

        log.error("[{}] unexpected error", traceId, exception);

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorResponseDto(errorCode.name(), errorCode.getMessage(), traceId));
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
```

- [ ] **Step 5：編譯驗證**

Run: `.\mvnw.cmd -B -q compile`
Expected: 無錯誤輸出

- [ ] **Step 6：Commit**

```bash
git add src/main/java/com/tim/language_project/enums/ErrorCodeEnum.java src/main/java/com/tim/language_project/exception/ src/main/java/com/tim/language_project/dto/response/ErrorResponseDto.java
git commit -m "$(cat <<'EOF'
新增錯誤碼與全域例外處理

Feat:
- 新增 ErrorCodeEnum，集中定義錯誤碼、HTTP 狀態與訊息
- 新增 BusinessException 與 GlobalExceptionHandler
- 錯誤回應附帶 traceId，供對照伺服器日誌

Improve:
- 兜底處理器不回傳原始例外訊息，避免洩漏連線資訊與金鑰片段
EOF
)"
```

---

# Task 5：外部服務介面與用量記錄　✅ 已完成

> 計畫原本此 Task 無測試。實作時加了 `ApiUsageRecorderTest`（3 項），
> 因為費用計算是專案唯一算錢的地方，算錯不會有任何錯誤訊息。
> 另有一項未解決的疑慮，見「已知偏離」第 8 條。

**分支：** `feat/client-contracts`

**Files:**
- Create: `src/main/java/com/tim/language_project/client/model/TranslationWord.java`
- Create: `src/main/java/com/tim/language_project/client/model/TranslationResult.java`
- Create: `src/main/java/com/tim/language_project/client/TranslationClient.java`
- Create: `src/main/java/com/tim/language_project/client/SpeechClient.java`
- Create: `src/main/java/com/tim/language_project/config/AiPricingProperties.java`
- Create: `src/main/java/com/tim/language_project/client/usage/ApiUsageRecorder.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1：建立 `TranslationWord` 與 `TranslationResult`**

```java
package com.tim.language_project.client.model;

/**
 * One segmented word returned by the translation service.
 */
public record TranslationWord(
        String chineseText,
        String thaiText,
        String romanization) {
}
```

```java
package com.tim.language_project.client.model;

import java.util.List;

/**
 * Translation of one user input. A single-word query yields a words list of
 * length one, so callers need no special case for words versus sentences.
 * The token counts are carried here so the caller can record usage.
 */
public record TranslationResult(
        String thaiText,
        String romanization,
        List<TranslationWord> words,
        String modelName,
        long inputTokens,
        long outputTokens) {
}
```

- [ ] **Step 2：建立兩個 Client 介面**

```java
package com.tim.language_project.client;

import com.tim.language_project.client.model.TranslationResult;

/**
 * Translates Chinese text into Thai with romanization and word segmentation.
 * Implementations are responsible for recording their own usage.
 */
public interface TranslationClient {

    TranslationResult translate(String sourceText);
}
```

```java
package com.tim.language_project.client;

import java.util.Optional;

/**
 * Converts Thai text into a stored audio file.
 * Returns an empty result on failure — speech problems must never fail the
 * surrounding translation, the caller stores a null audio file instead.
 */
public interface SpeechClient {

    Optional<String> synthesize(String thaiText);
}
```

- [ ] **Step 3：建立 `AiPricingProperties`**

```java
package com.tim.language_project.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Unit prices of external services, captured into every usage record so that
 * historical costs remain auditable after a price change.
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
```

- [ ] **Step 4：在 `application.yml` 補上單價與音檔設定**

在 `application.yml` 檔案最後、`logging:` 區塊之前插入：

```yaml
ai:
  pricing:
    openai:
      # 實際數值需查證 OpenAI 官方定價後填入。單位為「每一 token / 每一字元」的美金價格。
      translation-input-price: 0.00000500
      translation-output-price: 0.00001500
      speech-price: 0.00001500

audio:
  storage:
    # 音檔存放資料夾。已列入 .gitignore，不進版本控制。
    directory: audio
```

- [ ] **Step 5：建立 `ApiUsageRecorder`**

```java
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
 * Writes usage and cost records for external service calls.
 * Runs in its own transaction so that a failure here never rolls back the
 * caller, and a record survives even when the surrounding work fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiUsageRecorder {

    private final ApiUsageLogRepository apiUsageLogRepository;

    /**
     * Records one call. Cost is computed as input units times input price plus
     * output units times output price, using the prices supplied by the caller
     * so the row reflects the price in effect at call time.
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
            // Usage recording is observability, never a reason to fail the request.
            log.error("failed to record api usage for {} {}", provider, serviceType, exception);
        }
    }
}
```

- [ ] **Step 6：編譯驗證**

Run: `.\mvnw.cmd -B -q compile`
Expected: 無錯誤輸出

- [ ] **Step 7：Commit**

```bash
git add src/main/java/com/tim/language_project/client/ src/main/java/com/tim/language_project/config/ src/main/resources/application.yml
git commit -m "$(cat <<'EOF'
新增外部服務介面與用量記錄機制

Feat:
- 新增 TranslationClient 與 SpeechClient 介面，隔離外部服務供應商
- 新增 ApiUsageRecorder，於獨立交易記錄用量與費用
- 新增 AiPricingProperties，單價由設定檔提供並寫入每筆紀錄

Modify:
- application.yml 補上單價與音檔資料夾設定
EOF
)"
```

---

# Task 6：OpenAI 翻譯實作

**分支：** `feat/openai-translation`

**Files:**
- Create: `src/main/java/com/tim/language_project/client/openai/OpenAiTranslationClient.java`

**注意：** Spring AI 2.0.0 的 OpenAI 結構化輸出**不接受頂層 JSON 陣列**。本設計回傳的是含 `words` 欄位的容器 record，符合此限制。

- [ ] **Step 1：建立 `OpenAiTranslationClient`**

```java
package com.tim.language_project.client.openai;

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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Objects;

/**
 * Translation backed by an OpenAI chat model with structured output.
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
            TranslationPayload payload = chatClient.prompt()
                    .user(sourceText)
                    .call()
                    .entity(TranslationPayload.class);

            if (Objects.isNull(payload)
                    || ObjectUtils.isEmpty(payload.thaiText())
                    || ObjectUtils.isEmpty(payload.words())) {
                recordUsage(0L, 0L, false);
                throw new BusinessException(ErrorCodeEnum.TRANSLATION_RESPONSE_INVALID);
            }

            // Token counts are not exposed by the entity() shortcut; they are
            // recorded as an estimate based on character count until the usage
            // metadata is wired through.
            long inputTokens = sourceText.length();
            long outputTokens = payload.thaiText().length() + payload.romanization().length();
            recordUsage(inputTokens, outputTokens, true);

            List<TranslationWord> words = payload.words().stream()
                    .map(word -> new TranslationWord(
                            word.chineseText(), word.thaiText(), word.romanization()))
                    .toList();

            return new TranslationResult(
                    payload.thaiText(), payload.romanization(), words,
                    modelName, inputTokens, outputTokens);

        } catch (BusinessException businessException) {
            throw businessException;
        } catch (Exception exception) {
            recordUsage(0L, 0L, false);
            log.error("translation call failed for input length {}", sourceText.length(), exception);
            throw new BusinessException(ErrorCodeEnum.TRANSLATION_SERVICE_UNAVAILABLE, exception);
        }
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
     * Structured output shape requested from the model. Must stay a container
     * object — OpenAI structured outputs reject a top-level array.
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
```

- [ ] **Step 2：在 `application.yml` 指定聊天模型**

在 `application.yml` 的 `ai:` 區塊上方，`spring:` 底下加入：

```yaml
  ai:
    openai:
      chat:
        options:
          model: gpt-4o-mini
```

- [ ] **Step 3：編譯驗證**

Run: `.\mvnw.cmd -B -q compile`
Expected: 無錯誤輸出

若 `ChatClient` 或 `entity()` 的簽章與此處不同，查閱 Spring AI 2.0.0 文件後修正：使用 context7 查詢 `/websites/spring_io_spring-ai_2_0_0`，query 為 `ChatClient entity structured output record mapping`。

- [ ] **Step 4：手動驗證（需真實 API Key，非自動化測試）**

先在 `application-local.yml` 填入實際的 `spring.ai.openai.api-key`，再啟動應用程式並觀察 log 是否成功建立 Bean。此步驟不寫成自動化測試 —— 自動化測試禁止呼叫真實 API。

- [ ] **Step 5：Commit**

```bash
git add src/main/java/com/tim/language_project/client/openai/OpenAiTranslationClient.java src/main/resources/application.yml
git commit -m "$(cat <<'EOF'
新增 OpenAI 翻譯實作

Feat:
- 新增 OpenAiTranslationClient，以結構化輸出取得泰文、拼音與逐詞對照
- 回傳格式採容器物件，符合 OpenAI 結構化輸出不接受頂層陣列的限制
- 呼叫成功與失敗皆寫入用量紀錄
EOF
)"
```

---

# Task 7：OpenAI 語音實作與音檔儲存

**分支：** `feat/openai-speech`

**Files:**
- Create: `src/main/java/com/tim/language_project/config/AudioStorageProperties.java`
- Create: `src/main/java/com/tim/language_project/client/openai/OpenAiSpeechClient.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1：建立 `AudioStorageProperties`**

```java
package com.tim.language_project.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Location of the generated audio files on the local filesystem.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "audio.storage")
public class AudioStorageProperties {

    private String directory = "audio";
}
```

- [ ] **Step 2：建立 `OpenAiSpeechClient`**

```java
package com.tim.language_project.client.openai;

import com.tim.language_project.client.SpeechClient;
import com.tim.language_project.client.usage.ApiUsageRecorder;
import com.tim.language_project.config.AiPricingProperties;
import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.enums.AiProviderEnum;
import com.tim.language_project.enums.AiServiceTypeEnum;
import com.tim.language_project.enums.SpeechFailureReasonEnum;
import com.tim.language_project.enums.UsageUnitTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

/**
 * Speech synthesis backed by the OpenAI text-to-speech model.
 * Failures are swallowed and reported as an empty result so that a speech
 * problem never fails the surrounding translation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiSpeechClient implements SpeechClient {

    private final TextToSpeechModel textToSpeechModel;

    private final ApiUsageRecorder apiUsageRecorder;

    private final AiPricingProperties pricingProperties;

    private final AudioStorageProperties audioStorageProperties;

    @Value("${spring.ai.openai.audio.speech.options.model:gpt-4o-mini-tts}")
    private String modelName;

    @Override
    public Optional<String> synthesize(String thaiText) {
        if (ObjectUtils.isEmpty(thaiText)) {
            return Optional.empty();
        }

        byte[] audioBytes;
        try {
            audioBytes = textToSpeechModel.call(thaiText);
        } catch (Exception exception) {
            recordFailure(thaiText, SpeechFailureReasonEnum.CONNECTION_FAILED, exception);
            return Optional.empty();
        }

        if (ObjectUtils.isEmpty(audioBytes)) {
            recordFailure(thaiText, SpeechFailureReasonEnum.UNKNOWN, null);
            return Optional.empty();
        }

        try {
            String fileName = UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ".mp3";
            Path directory = Paths.get(audioStorageProperties.getDirectory());
            Files.createDirectories(directory);
            Files.write(directory.resolve(fileName), audioBytes);

            recordUsage(thaiText.length(), true);
            return Optional.of(fileName);
        } catch (Exception exception) {
            recordFailure(thaiText, SpeechFailureReasonEnum.FILE_SAVE_FAILED, exception);
            return Optional.empty();
        }
    }

    private void recordFailure(String thaiText, SpeechFailureReasonEnum reason, Exception exception) {
        log.error("speech synthesis failed, reason={} ({})",
                reason.name(), reason.getDescription(), exception);
        recordUsage(thaiText.length(), false);
    }

    private void recordUsage(long characterCount, boolean success) {
        apiUsageRecorder.record(
                AiProviderEnum.OPENAI,
                AiServiceTypeEnum.SPEECH,
                modelName,
                UsageUnitTypeEnum.CHARACTER,
                characterCount,
                0L,
                pricingProperties.getSpeechPrice(),
                BigDecimal.ZERO,
                success);
    }
}
```

- [ ] **Step 3：在 `application.yml` 指定語音模型**

在 `spring.ai.openai` 底下、`chat` 之後加入：

```yaml
      audio:
        speech:
          options:
            model: gpt-4o-mini-tts
            voice: alloy
            response-format: mp3
```

- [ ] **Step 4：設定音檔對外提供路徑**

在 `application.yml` 的 `spring:` 底下加入靜態資源對應，讓 `/audio/**` 指向本機資料夾：

```yaml
  web:
    resources:
      static-locations:
        - classpath:/static/
        - file:audio/
```

**注意：** `file:audio/` 對應到 `/audio/**` 需要額外的 `WebMvcConfigurer`，若上述設定無法讓 `/audio/xxx.mp3` 取得檔案，改以下列 Bean 明確註冊（建立 `src/main/java/com/tim/language_project/config/WebMvcConfig.java`）：

```java
package com.tim.language_project.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Serves generated audio files from the local storage directory.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AudioStorageProperties audioStorageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(audioStorageProperties.getDirectory())
                .toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/audio/**").addResourceLocations(location);
    }
}
```

- [ ] **Step 5：編譯驗證**

Run: `.\mvnw.cmd -B -q compile`
Expected: 無錯誤輸出

若 `TextToSpeechModel` 的匯入路徑不同，以 context7 查詢 `/websites/spring_io_spring-ai_2_0_0`，query 為 `OpenAiAudioSpeechModel TextToSpeechModel autoconfiguration bean`。

- [ ] **Step 6：Commit**

```bash
git add src/main/java/com/tim/language_project/client/openai/OpenAiSpeechClient.java src/main/java/com/tim/language_project/config/ src/main/resources/application.yml
git commit -m "$(cat <<'EOF'
新增 OpenAI 語音實作與音檔儲存

Feat:
- 新增 OpenAiSpeechClient，產生整句泰文的 mp3 並存至本機資料夾
- 語音失敗不中斷流程，回傳空結果由呼叫端存入 null 音檔
- 新增 WebMvcConfig 將 /audio/** 對應至音檔資料夾
EOF
)"
```

---

# Task 8：Service 主流程

**分支：** `feat/translation-service`

**Files:**
- Create: `src/main/java/com/tim/language_project/dto/response/TranslationResponseDto.java`
- Create: `src/main/java/com/tim/language_project/service/TranslationPersistenceService.java`
- Create: `src/main/java/com/tim/language_project/service/TranslationService.java`
- Test: `src/test/java/com/tim/language_project/service/TranslationServiceTest.java`

> **為什麼寫入要獨立成 `TranslationPersistenceService`：**
> Spring 的 `@Transactional` 靠 AOP 代理實作。同一個類別內部直接呼叫自己的方法（`this.persist(...)`）**不會經過代理，交易不會啟動**。把寫入放到另一個 Bean 才能確保交易真的生效。

- [ ] **Step 1：建立 `TranslationResponseDto`**

```java
package com.tim.language_project.dto.response;

import java.util.List;

/**
 * API response for one translation query.
 */
public record TranslationResponseDto(
        String sourceText,
        String thaiText,
        String romanization,
        String audioUrl,
        boolean fromCache,
        List<TranslationSegmentDto> segments) {
}
```

- [ ] **Step 2：先寫失敗的測試 — `TranslationServiceTest`**

```java
package com.tim.language_project.service;

import com.tim.language_project.client.SpeechClient;
import com.tim.language_project.client.TranslationClient;
import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.client.model.TranslationWord;
import com.tim.language_project.dto.response.TranslationQueryDto;
import com.tim.language_project.dto.response.TranslationResponseDto;
import com.tim.language_project.dto.response.TranslationSegmentDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.repository.TranslationQueryRepository;
import com.tim.language_project.repository.TranslationSegmentRepository;
import com.tim.language_project.repository.VocabularyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationServiceTest {

    @Mock
    private TranslationQueryRepository translationQueryRepository;

    @Mock
    private TranslationSegmentRepository translationSegmentRepository;

    @Mock
    private VocabularyRepository vocabularyRepository;

    @Mock
    private TranslationClient translationClient;

    @Mock
    private SpeechClient speechClient;

    @Mock
    private TranslationPersistenceService translationPersistenceService;

    @InjectMocks
    private TranslationService translationService;

    @Test
    @DisplayName("快取命中時不得呼叫外部服務")
    void shouldNotCallExternalServicesWhenCacheHits() {
        when(translationQueryRepository.findBySourceText("我想喝酒"))
                .thenReturn(Optional.of(new TranslationQueryDto(
                        1L, "我想喝酒", "ฉันอยากดื่มเหล้า", "chǎn yàak dùuem lâo", "a3f9c2.mp3")));
        when(translationSegmentRepository.findByQueryIdOrderBySeqNo(1L))
                .thenReturn(List.of(new TranslationSegmentDto(1, "我", "ฉัน", "chǎn")));

        TranslationResponseDto response = translationService.translate("我想喝酒");

        assertThat(response.fromCache()).isTrue();
        assertThat(response.thaiText()).isEqualTo("ฉันอยากดื่มเหล้า");
        assertThat(response.audioUrl()).isEqualTo("/audio/a3f9c2.mp3");
        verify(translationClient, never()).translate(anyString());
        verify(speechClient, never()).synthesize(anyString());
    }

    @Test
    @DisplayName("輸入前後空白應去除後再查快取")
    void shouldTrimInputBeforeLookup() {
        when(translationQueryRepository.findBySourceText("我想喝酒"))
                .thenReturn(Optional.of(new TranslationQueryDto(
                        1L, "我想喝酒", "ฉันอยากดื่มเหล้า", "chǎn", null)));
        when(translationSegmentRepository.findByQueryIdOrderBySeqNo(1L))
                .thenReturn(List.of());

        TranslationResponseDto response = translationService.translate("  我想喝酒  ");

        assertThat(response.sourceText()).isEqualTo("我想喝酒");
    }

    @Test
    @DisplayName("空白輸入應拋出 INPUT_REQUIRED")
    void shouldRejectBlankInput() {
        assertThatThrownBy(() -> translationService.translate("   "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCodeEnum.INPUT_REQUIRED);
    }

    @Test
    @DisplayName("超過 100 字應拋出 INPUT_TOO_LONG")
    void shouldRejectTooLongInput() {
        String tooLong = "字".repeat(101);

        assertThatThrownBy(() -> translationService.translate(tooLong))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCodeEnum.INPUT_TOO_LONG);
    }

    @Test
    @DisplayName("語音失敗時仍應回傳翻譯結果，音檔為 null")
    void shouldReturnTranslationWhenSpeechFails() {
        when(translationQueryRepository.findBySourceText("水")).thenReturn(Optional.empty());
        when(vocabularyRepository.findByChineseText("水")).thenReturn(Optional.empty());
        when(translationClient.translate("水")).thenReturn(new TranslationResult(
                "น้ำ", "náam",
                List.of(new TranslationWord("水", "น้ำ", "náam")),
                "gpt-test", 10L, 5L));
        when(speechClient.synthesize("น้ำ")).thenReturn(Optional.empty());
        when(translationPersistenceService.persist(any(), any(), any())).thenReturn(99L);

        TranslationResponseDto response = translationService.translate("水");

        assertThat(response.fromCache()).isFalse();
        assertThat(response.thaiText()).isEqualTo("น้ำ");
        assertThat(response.audioUrl()).isNull();
        assertThat(response.segments()).hasSize(1);
    }
}
```

- [ ] **Step 3：執行測試確認失敗**

Run: `.\mvnw.cmd -B test -Dtest=TranslationServiceTest`
Expected: 編譯失敗，`TranslationService` 不存在

- [ ] **Step 4a：建立 `TranslationPersistenceService`**

```java
package com.tim.language_project.service;

import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.client.model.TranslationWord;
import com.tim.language_project.entity.TranslationQuery;
import com.tim.language_project.entity.TranslationSegment;
import com.tim.language_project.entity.Vocabulary;
import com.tim.language_project.enums.VocabularySourceTypeEnum;
import com.tim.language_project.repository.TranslationQueryRepository;
import com.tim.language_project.repository.TranslationSegmentRepository;
import com.tim.language_project.repository.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Writes one completed translation across the query cache, its segmentation, and
 * the dictionary in a single transaction. Kept separate from
 * {@link TranslationService} because a self-invoked method would bypass the
 * transactional proxy.
 */
@Service
@RequiredArgsConstructor
public class TranslationPersistenceService {

    private final TranslationQueryRepository translationQueryRepository;

    private final TranslationSegmentRepository translationSegmentRepository;

    private final VocabularyRepository vocabularyRepository;

    @Transactional
    public Long persist(String sourceText, TranslationResult result, String audioFile) {
        TranslationQuery query = new TranslationQuery();
        query.setSourceText(sourceText);
        query.setThaiText(result.thaiText());
        query.setRomanization(result.romanization());
        query.setAudioFile(audioFile);
        TranslationQuery savedQuery = translationQueryRepository.saveAndFlush(query);

        int seqNo = 1;
        List<TranslationSegment> segments = new ArrayList<>();
        for (TranslationWord word : result.words()) {
            TranslationSegment segment = new TranslationSegment();
            segment.setQueryId(savedQuery.getId());
            segment.setSeqNo(seqNo++);
            segment.setChineseText(word.chineseText());
            segment.setThaiText(word.thaiText());
            segment.setRomanization(word.romanization());
            segments.add(segment);
        }
        translationSegmentRepository.saveAll(segments);

        persistNewVocabulary(sourceText, result.words());

        return savedQuery.getId();
    }

    private void persistNewVocabulary(String sourceText, List<TranslationWord> words) {
        List<String> chineseTexts = words.stream()
                .map(TranslationWord::chineseText)
                .distinct()
                .toList();
        if (ObjectUtils.isEmpty(chineseTexts)) {
            return;
        }

        List<String> existing = vocabularyRepository.findExistingChineseTexts(chineseTexts);

        List<Vocabulary> newEntries = new ArrayList<>();
        for (String chineseText : chineseTexts) {
            if (existing.contains(chineseText)) {
                continue;
            }
            TranslationWord word = words.stream()
                    .filter(candidate -> Objects.equals(candidate.chineseText(), chineseText))
                    .findFirst()
                    .orElseThrow();

            Vocabulary vocabulary = new Vocabulary();
            vocabulary.setChineseText(word.chineseText());
            vocabulary.setThaiText(word.thaiText());
            vocabulary.setRomanization(word.romanization());
            vocabulary.setSourceType(Objects.equals(sourceText, word.chineseText())
                    ? VocabularySourceTypeEnum.DIRECT
                    : VocabularySourceTypeEnum.SEGMENT);
            newEntries.add(vocabulary);
        }

        if (!ObjectUtils.isEmpty(newEntries)) {
            vocabularyRepository.saveAll(newEntries);
        }
    }
}
```

- [ ] **Step 4b：建立 `TranslationService`**

```java
package com.tim.language_project.service;

import com.tim.language_project.client.SpeechClient;
import com.tim.language_project.client.TranslationClient;
import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.client.model.TranslationWord;
import com.tim.language_project.dto.response.TranslationQueryDto;
import com.tim.language_project.dto.response.TranslationResponseDto;
import com.tim.language_project.dto.response.TranslationSegmentDto;
import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.repository.TranslationQueryRepository;
import com.tim.language_project.repository.TranslationSegmentRepository;
import com.tim.language_project.repository.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Main query flow. Reads the cache first and only calls external services on a
 * miss, so a repeated query costs nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

    private static final int MAX_SOURCE_TEXT_LENGTH = 100;

    private static final String AUDIO_URL_PREFIX = "/audio/";

    private final TranslationQueryRepository translationQueryRepository;

    private final TranslationSegmentRepository translationSegmentRepository;

    private final VocabularyRepository vocabularyRepository;

    private final TranslationClient translationClient;

    private final SpeechClient speechClient;

    private final TranslationPersistenceService translationPersistenceService;

    /**
     * External service calls deliberately run outside any database transaction —
     * they can take seconds and must not hold a connection open. Only the final
     * write is transactional, and it lives in a separate bean so the
     * transactional proxy actually applies.
     */
    public TranslationResponseDto translate(String rawInput) {
        String sourceText = validateAndNormalize(rawInput);

        Optional<TranslationQueryDto> cached =
                translationQueryRepository.findBySourceText(sourceText);
        if (cached.isPresent()) {
            return buildCachedResponse(cached.get());
        }

        TranslationResult result = resolveTranslation(sourceText);
        String audioFile = speechClient.synthesize(result.thaiText()).orElse(null);

        translationPersistenceService.persist(sourceText, result, audioFile);

        List<TranslationSegmentDto> segments = new ArrayList<>();
        int seqNo = 1;
        for (TranslationWord word : result.words()) {
            segments.add(new TranslationSegmentDto(
                    seqNo++, word.chineseText(), word.thaiText(), word.romanization()));
        }

        return new TranslationResponseDto(
                sourceText,
                result.thaiText(),
                result.romanization(),
                toAudioUrl(audioFile),
                false,
                segments);
    }

    private String validateAndNormalize(String rawInput) {
        if (ObjectUtils.isEmpty(rawInput) || ObjectUtils.isEmpty(rawInput.trim())) {
            throw new BusinessException(ErrorCodeEnum.INPUT_REQUIRED);
        }
        String sourceText = rawInput.trim();
        if (sourceText.length() > MAX_SOURCE_TEXT_LENGTH) {
            throw new BusinessException(ErrorCodeEnum.INPUT_TOO_LONG);
        }
        if (!sourceText.matches(".*[\\p{IsHan}].*")) {
            throw new BusinessException(ErrorCodeEnum.INPUT_UNSUPPORTED_CONTENT);
        }
        return sourceText;
    }

    private TranslationResponseDto buildCachedResponse(TranslationQueryDto cached) {
        List<TranslationSegmentDto> segments =
                translationSegmentRepository.findByQueryIdOrderBySeqNo(cached.id());
        return new TranslationResponseDto(
                cached.sourceText(),
                cached.thaiText(),
                cached.romanization(),
                toAudioUrl(cached.audioFile()),
                true,
                segments);
    }

    /**
     * Uses the accumulated dictionary when the whole input is a single known
     * word, which skips the paid translation call entirely.
     */
    private TranslationResult resolveTranslation(String sourceText) {
        Optional<VocabularyDto> knownWord = vocabularyRepository.findByChineseText(sourceText);
        if (knownWord.isPresent()) {
            VocabularyDto word = knownWord.get();
            return new TranslationResult(
                    word.thaiText(),
                    word.romanization(),
                    List.of(new TranslationWord(
                            word.chineseText(), word.thaiText(), word.romanization())),
                    "vocabulary-cache", 0L, 0L);
        }
        return translationClient.translate(sourceText);
    }

    private String toAudioUrl(String audioFile) {
        return ObjectUtils.isEmpty(audioFile) ? null : AUDIO_URL_PREFIX + audioFile;
    }
}
```

- [ ] **Step 5：執行測試確認通過**

Run: `.\mvnw.cmd -B test -Dtest=TranslationServiceTest`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 6：執行全部測試**

Run: `.\mvnw.cmd -B test`
Expected: 全部通過

- [ ] **Step 7：Commit**

```bash
git add src/main/java/com/tim/language_project/service/ src/main/java/com/tim/language_project/dto/response/TranslationResponseDto.java src/test/java/com/tim/language_project/service/
git commit -m "$(cat <<'EOF'
新增查詢主流程

Feat:
- 新增 TranslationService，先讀快取再決定是否呼叫外部服務
- 單一已知詞可直接使用單字表，跳過付費的翻譯呼叫
- 拆解結果沉澱為單字，依輸入是否等同該詞判定 DIRECT 或 SEGMENT
- 外部呼叫置於交易之外，僅寫入動作使用交易

Improve:
- 語音失敗不影響翻譯結果，音檔以 null 儲存
EOF
)"
```

---

# Task 9：Controller

**分支：** `feat/translation-controller`

**Files:**
- Create: `src/main/java/com/tim/language_project/dto/request/TranslationRequestDto.java`
- Create: `src/main/java/com/tim/language_project/controller/TranslationController.java`
- Test: `src/test/java/com/tim/language_project/controller/TranslationControllerTest.java`

- [ ] **Step 1：建立 `TranslationRequestDto`**

```java
package com.tim.language_project.dto.request;

/**
 * Request body for a translation query.
 */
public record TranslationRequestDto(String sourceText) {
}
```

- [ ] **Step 2：先寫失敗的測試 — `TranslationControllerTest`**

```java
package com.tim.language_project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tim.language_project.dto.request.TranslationRequestDto;
import com.tim.language_project.dto.response.TranslationResponseDto;
import com.tim.language_project.dto.response.TranslationSegmentDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.service.TranslationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TranslationController.class)
class TranslationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TranslationService translationService;

    @Test
    @DisplayName("查詢成功應回傳翻譯內容與逐詞對照")
    void shouldReturnTranslation() throws Exception {
        when(translationService.translate("我想喝酒")).thenReturn(new TranslationResponseDto(
                "我想喝酒", "ฉันอยากดื่มเหล้า", "chǎn yàak dùuem lâo",
                "/audio/a3f9c2.mp3", true,
                List.of(new TranslationSegmentDto(1, "我", "ฉัน", "chǎn"))));

        mockMvc.perform(post("/api/v1/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TranslationRequestDto("我想喝酒"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thaiText").value("ฉันอยากดื่มเหล้า"))
                .andExpect(jsonPath("$.audioUrl").value("/audio/a3f9c2.mp3"))
                .andExpect(jsonPath("$.fromCache").value(true))
                .andExpect(jsonPath("$.segments[0].chineseText").value("我"));
    }

    @Test
    @DisplayName("輸入過長應回傳 400 與 INPUT_TOO_LONG")
    void shouldReturnBadRequestWhenInputTooLong() throws Exception {
        when(translationService.translate(anyString()))
                .thenThrow(new BusinessException(ErrorCodeEnum.INPUT_TOO_LONG));

        mockMvc.perform(post("/api/v1/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TranslationRequestDto("字".repeat(101)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INPUT_TOO_LONG"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
```

- [ ] **Step 3：執行測試確認失敗**

Run: `.\mvnw.cmd -B test -Dtest=TranslationControllerTest`
Expected: 編譯失敗，`TranslationController` 不存在

- [ ] **Step 4：建立 `TranslationController`**

```java
package com.tim.language_project.controller;

import com.tim.language_project.dto.request.TranslationRequestDto;
import com.tim.language_project.dto.response.TranslationResponseDto;
import com.tim.language_project.service.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Translation query endpoint. POST is used rather than GET because the call
 * writes rows and generates a file, and because the input is Chinese text that
 * would need URL encoding.
 */
@RestController
@RequestMapping("/api/v1/translations")
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationService translationService;

    @PostMapping
    public ResponseEntity<TranslationResponseDto> translate(
            @RequestBody TranslationRequestDto request) {
        TranslationResponseDto response = translationService.translate(request.sourceText());
        HttpStatus status = response.fromCache() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }
}
```

- [ ] **Step 5：執行測試確認通過**

Run: `.\mvnw.cmd -B test -Dtest=TranslationControllerTest`
Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6：建立 `VocabularyService` 與 `VocabularyController`**

規格的 API 設計包含單字瀏覽端點，補上。

`src/main/java/com/tim/language_project/service/VocabularyService.java`：

```java
package com.tim.language_project.service;

import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.repository.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Read access to the accumulated dictionary.
 */
@Service
@RequiredArgsConstructor
public class VocabularyService {

    private final VocabularyRepository vocabularyRepository;

    public Page<VocabularyDto> findAll(Pageable pageable) {
        return vocabularyRepository.findAllOrderByIdDesc(pageable);
    }

    public VocabularyDto findById(Long id) {
        return vocabularyRepository.findById(id)
                .map(vocabulary -> new VocabularyDto(
                        vocabulary.getId(),
                        vocabulary.getChineseText(),
                        vocabulary.getThaiText(),
                        vocabulary.getRomanization()))
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.VOCABULARY_NOT_FOUND));
    }
}
```

`src/main/java/com/tim/language_project/controller/VocabularyController.java`：

```java
package com.tim.language_project.controller;

import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.service.VocabularyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Browse endpoints for the accumulated dictionary.
 */
@RestController
@RequestMapping("/api/v1/vocabularies")
@RequiredArgsConstructor
public class VocabularyController {

    private final VocabularyService vocabularyService;

    @GetMapping
    public ResponseEntity<Page<VocabularyDto>> findAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(vocabularyService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VocabularyDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(vocabularyService.findById(id));
    }
}
```

- [ ] **Step 7：執行全部測試**

Run: `.\mvnw.cmd -B test`
Expected: 全部通過

- [ ] **Step 8：Commit**

```bash
git add src/main/java/com/tim/language_project/controller/ src/main/java/com/tim/language_project/service/VocabularyService.java src/main/java/com/tim/language_project/dto/request/ src/test/java/com/tim/language_project/controller/
git commit -m "$(cat <<'EOF'
新增查詢 API

Feat:
- 新增 TranslationController，提供 POST /api/v1/translations
- 快取命中回 200，新建立回 201
- 新增 VocabularyController 與 VocabularyService，提供單字瀏覽端點
- 新增 Controller 測試，涵蓋成功回應與錯誤碼回應格式
EOF
)"
```

---

# Task 10：前端查詢頁面

**分支：** `feat/web-page`

**Files:**
- Create: `src/main/resources/static/index.html`
- Create: `src/main/resources/static/style.css`
- Create: `src/main/resources/static/app.js`

- [ ] **Step 1：建立 `index.html`**

```html
<!DOCTYPE html>
<html lang="zh-Hant">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>中泰語言學習</title>
    <link rel="stylesheet" href="/style.css">
</head>
<body>
<main>
    <h1>中文轉泰文</h1>

    <form id="query-form">
        <input id="source-text" type="text" maxlength="100"
               placeholder="輸入中文，例如：我想喝酒" autocomplete="off">
        <button type="submit" id="submit-button">查詢</button>
    </form>

    <p id="status" class="status"></p>

    <section id="result" class="result hidden">
        <p class="thai" id="thai-text"></p>
        <p class="romanization" id="romanization"></p>
        <button type="button" id="play-button" class="hidden">▶ 播放發音</button>
        <audio id="audio-player"></audio>

        <h2>逐詞對照</h2>
        <table id="segment-table">
            <thead>
            <tr><th>中文</th><th>泰文</th><th>拼音</th></tr>
            </thead>
            <tbody id="segment-body"></tbody>
        </table>
    </section>
</main>
<script src="/app.js"></script>
</body>
</html>
```

- [ ] **Step 2：建立 `style.css`**

```css
* { box-sizing: border-box; }

body {
    margin: 0;
    padding: 2rem 1rem;
    font-family: "Noto Sans TC", "Microsoft JhengHei", sans-serif;
    background: #f7f5f2;
    color: #2b2b2b;
}

main { max-width: 640px; margin: 0 auto; }

h1 { font-size: 1.5rem; margin-bottom: 1.5rem; }

h2 { font-size: 1rem; margin-top: 2rem; color: #666; }

#query-form { display: flex; gap: 0.5rem; }

#source-text {
    flex: 1;
    padding: 0.75rem;
    font-size: 1rem;
    border: 1px solid #ccc;
    border-radius: 6px;
}

button {
    padding: 0.75rem 1.25rem;
    font-size: 1rem;
    border: none;
    border-radius: 6px;
    background: #b5543a;
    color: #fff;
    cursor: pointer;
}

button:disabled { background: #bbb; cursor: not-allowed; }

.status { min-height: 1.5rem; color: #b5543a; font-size: 0.9rem; }

.result { background: #fff; padding: 1.5rem; border-radius: 8px; }

.thai { font-size: 2rem; margin: 0 0 0.5rem; }

.romanization { font-size: 1.1rem; color: #666; margin: 0 0 1rem; }

table { width: 100%; border-collapse: collapse; margin-top: 0.5rem; }

th, td { padding: 0.5rem; text-align: left; border-bottom: 1px solid #eee; }

th { font-size: 0.85rem; color: #888; font-weight: normal; }

td:nth-child(2) { font-size: 1.15rem; }

.hidden { display: none; }
```

- [ ] **Step 3：建立 `app.js`**

```javascript
const form = document.getElementById('query-form');
const sourceInput = document.getElementById('source-text');
const submitButton = document.getElementById('submit-button');
const statusText = document.getElementById('status');
const resultSection = document.getElementById('result');
const thaiText = document.getElementById('thai-text');
const romanization = document.getElementById('romanization');
const playButton = document.getElementById('play-button');
const audioPlayer = document.getElementById('audio-player');
const segmentBody = document.getElementById('segment-body');

form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const input = sourceInput.value.trim();
    if (input === '') {
        statusText.textContent = '請輸入中文';
        return;
    }

    submitButton.disabled = true;
    statusText.textContent = '查詢中…';
    resultSection.classList.add('hidden');

    try {
        const response = await fetch('/api/v1/translations', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sourceText: input })
        });

        const payload = await response.json();

        if (!response.ok) {
            statusText.textContent = payload.message || '查詢失敗';
            return;
        }

        render(payload);
        statusText.textContent = payload.fromCache ? '（來自快取）' : '';
    } catch (error) {
        statusText.textContent = '無法連線至伺服器';
    } finally {
        submitButton.disabled = false;
    }
});

function render(payload) {
    thaiText.textContent = payload.thaiText;
    romanization.textContent = payload.romanization;

    if (payload.audioUrl) {
        audioPlayer.src = payload.audioUrl;
        playButton.classList.remove('hidden');
    } else {
        playButton.classList.add('hidden');
    }

    segmentBody.innerHTML = '';
    payload.segments.forEach((segment) => {
        const row = document.createElement('tr');
        row.innerHTML =
            `<td>${segment.chineseText}</td>` +
            `<td>${segment.thaiText}</td>` +
            `<td>${segment.romanization}</td>`;
        segmentBody.appendChild(row);
    });

    resultSection.classList.remove('hidden');
}

playButton.addEventListener('click', () => audioPlayer.play());
```

- [ ] **Step 4：手動驗證**

先確認 `application-local.yml` 已填入實際的 OpenAI API Key，再啟動應用程式：

Run: `.\mvnw.cmd -B spring-boot:run`

開啟瀏覽器至 `http://localhost:8080`，輸入「我想喝酒」，預期：
- 顯示泰文與拼音
- 顯示四列逐詞對照
- 出現播放按鈕，點擊可聽到發音
- 再查一次同樣內容，狀態列顯示「（來自快取）」且回應明顯變快

- [ ] **Step 5：Commit**

```bash
git add src/main/resources/static/
git commit -m "$(cat <<'EOF'
新增查詢頁面

Feat:
- 新增靜態查詢頁面，顯示泰文、拼音、逐詞對照與發音播放
- 音檔不存在時隱藏播放按鈕
- 顯示是否來自快取，便於開發階段確認費用
EOF
)"
```

---

## 完成後的待辦

| 項目 | 說明 |
|---|---|
| OpenAI API Key | 需申請並填入 `application-local.yml` |
| 單價查證 | 需查證 OpenAI 官方最新定價，更新 `application.yml` 的 `ai.pricing` |
| 泰語 TTS 品質 | 建議試聽比較 OpenAI 與 Google / Azure 的泰語語音；語音已抽成介面，更換只需新增實作類別 |
| Token 用量精準化 | Task 6 目前以字元數估算 token，待確認 Spring AI 2.0.0 取得 usage metadata 的方式後改為實際值 |
| 音檔資料夾 | `audio/` 已列入 `.gitignore`，正式部署時需確認寫入權限 |
| 會員功能 | 規格第 11 節已規劃，現有結構不需改動 |
