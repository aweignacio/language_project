# 中泰語言學習網站 — 設計規格

- **文件日期**：2026-08-11
- **專案路徑**：`C:\Tim\language_project`
- **狀態**：已確認，待排實作計畫

---

## 1. 專案目標

一個給中文使用者學泰文的網站。使用者輸入中文（單字或句子），系統回傳對應的泰文、羅馬拼音、逐詞對照，並提供整句發音音檔。

核心價值在於三件 Google 翻譯做不到的事：

1. **羅馬拼音** — 泰文對中文使用者最難的是發音，`สวัสดี` 要配上 `sà-wàt-dii` 才學得起來
2. **逐詞對照** — 泰文書寫時詞之間沒有空格，需要 AI 判斷詞邊界，才能看出哪個泰文對應哪個中文
3. **發音音檔** — 可實際聽到正確發音

---

## 2. 範圍

### 第一版要做

- 中文輸入 → 泰文、拼音、逐詞拆解、整句發音
- 查詢結果永久快取，相同輸入不重複呼叫外部服務
- 拆解出的單詞沉澱成單字表
- API 用量與費用紀錄
- 簡單的查詢網頁

### 第一版不做

| 項目 | 原因 |
|---|---|
| 會員登入 / 收藏 / 學習紀錄 | 確定之後要做，但分層設計已預留，屆時不需改動現有結構 |
| 逐詞的個別發音 | 只提供整句音檔。單詞音檔拼接會有停頓與語調斷裂，泰文為聲調語言更明顯 |
| Redis 快取 | 快取是永久寫入 MySQL，非短期暫存。MySQL 索引查詢已足夠 |
| 獨立前端專案 | 後端從第一天就是純 REST API，未來要換 React/Vue 不需改後端 |

---

## 3. 技術選型

| 項目 | 選擇 | 說明 |
|---|---|---|
| 框架 | Spring Boot 4.1.0 / Java 21 | 既有骨架 |
| 資料庫 | MySQL | `compose.yaml` 已有 |
| 翻譯 | OpenAI Chat API | pom 需將 `spring-ai-starter-model-anthropic` 換為 `spring-ai-starter-model-openai` |
| 語音 | OpenAI TTS | 包成介面，可抽換為 Google / Azure |
| 前端 | 靜態 HTML + JavaScript | 不使用模板引擎，直接呼叫 REST API |
| 音檔儲存 | 伺服器本機資料夾 | DB 只存檔名，不存二進位內容 |
| 測試資料庫 | H2 | 已在 pom |

### 成本結構

翻譯與語音各自為獨立的付費 API 呼叫，皆按用量計費。**費用只在「首次遇到該輸入」時產生**，之後從資料庫與本機音檔提供，成本為零。

| 情境 | AI 呼叫 | TTS 呼叫 |
|---|---|---|
| 首次查詢新輸入 | 1 次 | 1 次 |
| 重複查詢相同輸入 | 0 | 0 |
| 單獨查詢曾在句子中出現過的詞 | **0**（單字表命中） | 1 次 |

單字表的省費效果僅發生在「單獨查詢一個詞」時；查詢新句子仍必須呼叫 AI。其主要價值是累積成可再利用的字典資產。

---

## 4. 架構

```
[瀏覽器]  靜態 HTML + JavaScript
    │  HTTP / REST
    ▼
[Spring Boot]
    ├─ Controller     接收請求、參數驗證、回應包裝
    ├─ Service        商業邏輯、快取判斷、DTO 組裝
    ├─ Repository     資料存取
    └─ Client         外部服務呼叫（翻譯、語音）+ 用量記錄
    │
    ▼
[MySQL] 四張資料表          [檔案系統] 音檔資料夾
```

### 查詢流程

輸入「我想喝酒」：

```
1. 去除前後空白，驗證輸入
2. 以 source_text 查 translation_query
     命中 → 撈出 segment → 回傳（0 元，數十毫秒）
     未命中 → 往下
3. 以完整輸入查 vocabulary
     命中（代表輸入是單一個已知詞）→ 跳過步驟 4，
       直接以該筆 vocabulary 資料組成「泰文 = 該詞泰文、拼音 = 該詞拼音、
       拆解陣列長度為 1」的結果
     未命中 → 執行步驟 4
4. 呼叫翻譯 API，一次取得：整句泰文、整句拼音、逐詞拆解陣列
5. 呼叫語音 API，產生整句音檔並存檔
     失敗 → 不中斷，audio_file 留 null
6. 開啟交易，一次寫入 translation_query + translation_segment + vocabulary
7. 回傳結果
```

**外部 API 呼叫必須在資料庫交易之外執行**，避免長時間佔用連線。交易只包住步驟 6 的寫入。

---

## 5. 資料模型

### 5.1 `translation_query` — 查詢結果快取

Key 為使用者輸入的原始字串，不區分單字或句子。**只有此表持有音檔。**

| 欄位 | 型別 | 說明 |
|---|---|---|
| `id` | BIGINT | 主鍵（自增） |
| `source_text` | VARCHAR(100) | 使用者輸入原文，**UNIQUE 索引** |
| `thai_text` | VARCHAR | 整句泰文 |
| `romanization` | VARCHAR | 整句羅馬拼音 |
| `audio_file` | VARCHAR **(可為 null)** | 音檔檔名，TTS 失敗時為 null |
| `created_at` / `updated_at` | DATETIME | |

**為何使用代理主鍵而非 `source_text`**：有子表需外鍵參考，中文字串當外鍵佔用空間大（約 15 倍）且 join 較慢；InnoDB 聚簇索引會使所有二級索引夾帶主鍵值；未來若要正規化輸入（去空白、全形轉半形），修改自然主鍵將牽動所有外鍵。

### 5.2 `translation_segment` — 逐詞拆解

**複合主鍵：`query_id` + `seq_no`**，無獨立 `id` 欄位。

| 欄位 | 型別 | 說明 |
|---|---|---|
| `query_id` | BIGINT | 主鍵之一，對應 `translation_query.id` |
| `seq_no` | INT | 主鍵之一，顯示順序 |
| `chinese_text` | VARCHAR | 中文詞 |
| `thai_text` | VARCHAR | 泰文詞 |
| `romanization` | VARCHAR | 拼音 |

不使用 JSON 欄位儲存拆解結果，以便日後統計與查詢。同一個詞可在不同句子的 segment 中重複出現，這是正確的 — segment 記錄的是「該句話如何拆解」。

### 5.3 `vocabulary` — 單字表

純文字字典，**無音檔**。

| 欄位 | 型別 | 說明 |
|---|---|---|
| `id` | BIGINT | 主鍵 |
| `chinese_text` | VARCHAR | 中文詞，**UNIQUE 索引** |
| `thai_text` | VARCHAR | 泰文 |
| `romanization` | VARCHAR | 拼音 |
| `source_type` | VARCHAR | `VocabularySourceTypeEnum`：`SEGMENT` / `DIRECT`，判定規則見下 |
| `created_at` / `updated_at` | DATETIME | |

**`source_type` 判定規則：**

- 該詞是「使用者本次輸入的完整內容」（拆解結果長度為 1）→ `DIRECT`
- 該詞是由多詞句子拆解而來 → `SEGMENT`
- **已存在的詞不更新 `source_type`**，以首次寫入的值為準。此欄位用於了解資料來源，非精確統計，維持單純即可

**單字可同時存在於 `translation_query` 與 `vocabulary`**，這是刻意的：前者的 key 是「使用者輸入了什麼」（可能是詞或句），後者的 key 是「一個中文詞」，兩者概念不同。

### 5.4 `api_usage_log` — API 用量與費用

事件紀錄表，無自然鍵，使用流水號主鍵。屬營運監控資料，刪除不影響業務功能。

| 欄位 | 型別 | 說明 |
|---|---|---|
| `id` | BIGINT | 主鍵 |
| `query_id` | BIGINT (可為 null) | 對應的查詢，可追溯 |
| `provider` | VARCHAR | `AiProviderEnum`：`OPENAI` / `ANTHROPIC` / `GOOGLE` / `AZURE` |
| `service_type` | VARCHAR | `AiServiceTypeEnum`：`TRANSLATION` / `SPEECH` |
| `model_name` | VARCHAR | 實際使用的模型名稱 |
| `unit_type` | VARCHAR | `UsageUnitTypeEnum`：`TOKEN` / `CHARACTER` |
| `input_units` | BIGINT | 輸入用量 |
| `output_units` | BIGINT | 輸出用量（TTS 為 0） |
| `input_unit_price` | DECIMAL(12,8) | 呼叫當下的輸入單價 |
| `output_unit_price` | DECIMAL(12,8) | 呼叫當下的輸出單價 |
| `cost_amount` | DECIMAL(12,6) | 本次費用 |
| `currency` | CHAR(3) | 固定 `USD`，**不存台幣**（匯率浮動，統計時再換算） |
| `is_success` | BOOLEAN | 呼叫是否成功（失敗仍可能計費，且可觀察失敗率） |
| `created_at` | DATETIME | **需索引**，供期間統計 |

**金額欄位一律使用 `DECIMAL`，Java 端對應 `BigDecimal`，禁止使用 `double` / `float`。**

**需儲存呼叫當下的單價**，價格調整後歷史紀錄仍可驗算。單價本身設定於 `application.properties`，不另建價格表：

```properties
ai.openai.translation.input-price=0.000005
ai.openai.translation.output-price=0.000015
ai.openai.speech.price=0.000015
```

此表存放的 `token` 為 **AI 用量單位**，與認證用的 token 無關。API Key 不得寫入資料庫。

---

## 6. API 設計

| 方法 | 路徑 | 說明 |
|---|---|---|
| `POST` | `/api/v1/translations` | 查詢翻譯。回 `200`（快取命中）或 `201`（新建立） |
| `GET` | `/api/v1/vocabularies` | 瀏覽單字表（分頁） |
| `GET` | `/api/v1/vocabularies/{id}` | 查詢單一單字 |
| `GET` | `/audio/{fileName}` | 取得音檔（靜態資源） |

查詢使用 `POST` 而非 `GET`，因為該動作會寫入資料表並產生檔案，具有副作用；同時避免中文字置於 URL 的編碼問題。

### 請求

```json
{ "sourceText": "我想喝酒" }
```

### 回應

```json
{
  "sourceText": "我想喝酒",
  "thaiText": "ฉันอยากดื่มเหล้า",
  "romanization": "chǎn yàak dùuem lâo",
  "audioUrl": "/audio/a3f9c2.mp3",
  "fromCache": true,
  "segments": [
    { "seqNo": 1, "chineseText": "我", "thaiText": "ฉัน",  "romanization": "chǎn" },
    { "seqNo": 2, "chineseText": "想", "thaiText": "อยาก", "romanization": "yàak" },
    { "seqNo": 3, "chineseText": "喝", "thaiText": "ดื่ม",  "romanization": "dùuem" },
    { "seqNo": 4, "chineseText": "酒", "thaiText": "เหล้า", "romanization": "lâo" }
  ]
}
```

`audioUrl` 於 TTS 失敗時為 null，前端不顯示播放按鈕。
`fromCache` 供開發階段判斷本次查詢是否產生費用。

---

## 7. 分層與套件結構

```
com.tim.language_project
├── controller          接 HTTP、參數驗證、回應包裝（不含商業邏輯，不碰 Entity）
├── service             商業邏輯、Entity 轉 DTO（Entity 不外流）
├── repository          資料存取，查詢回傳 DTO class
├── entity              JPA 物件
├── dto
│   ├── request
│   └── response
├── client              外部服務呼叫 + 用量記錄
├── enums
├── config
└── exception
```

### 外部服務介面

```java
/**
 * Translates Chinese text into Thai with romanization and word segmentation.
 */
public interface TranslationClient {
    TranslationResult translate(String sourceText);
}

/**
 * Converts Thai text into an audio file and returns the stored file name.
 */
public interface SpeechClient {
    String synthesize(String thaiText);
}
```

第一版實作 `OpenAiTranslationClient` 與 `OpenAiSpeechClient`。更換服務商時只需新增實作類別並調整設定，Service 層不受影響。

`api_usage_log` 的寫入統一在 Client 層完成，確保不會遺漏。

---

## 8. 程式碼規範

除全域 CLAUDE.md 既有規範外，本專案另有以下約定：

### 8.1 命名

| 類型 | 規則 | 範例 |
|---|---|---|
| Entity | **不加 `Entity` 後綴**，依字意命名 | `TranslationQuery`、`TranslationSegment`、`Vocabulary`、`ApiUsageLog` |
| 複合主鍵類別 | 加 `Id` | `TranslationSegmentId` |
| DTO | 加 `Dto` | `TranslationResponseDto`、`VocabularyDto` |
| Enum | 加 `Enum` | `VocabularySourceTypeEnum`、`ErrorCodeEnum` |

### 8.2 Entity 規則

**全部 Entity 一律禁止使用 `@ManyToOne`、`@OneToMany`、`@JoinColumn` 等關聯註解。** 外鍵以單純欄位型別表示（如 `Long queryId`），關聯由 Service 層自行查詢組裝。

代價是取關聯資料需多下一次查詢；換得的是不會有 lazy loading 例外、不會有非預期的 N+1、序列化行為單純，且每次資料庫存取都是明確寫出來的。

複合主鍵採用 **`@IdClass`**（非 `@EmbeddedId`），Entity 欄位保持扁平：

```java
/**
 * Composite identity for the translation segment table.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class TranslationSegmentId implements Serializable {

    private Long queryId;

    private Integer seqNo;
}
```

```java
@Entity
@Table(name = "translation_segment")
@IdClass(TranslationSegmentId.class)
@Getter
@Setter
public class TranslationSegment {

    @Id
    @Column(name = "query_id")
    private Long queryId;

    @Id
    @Column(name = "seq_no")
    private Integer seqNo;

    @Column(name = "chinese_text")
    private String chineseText;

    @Column(name = "thai_text")
    private String thaiText;

    @Column(name = "romanization")
    private String romanization;
}
```

### 8.3 查詢寫法

一律使用 **DTO class 投影**（建構子表達式），**禁止使用介面投影**。JPQL 使用 Java Text Block，各關鍵字子句之間空一行：

```java
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
VocabularyDto findByChineseText(@Param("chineseText") String chineseText);
```

### 8.4 開發節奏

**採增量開發。** 每完成一個可獨立驗證的小單位即停下，由 Awei 確認無誤後才 commit。不可一次產生大量程式碼後才提交。

---

## 9. 錯誤處理

### 9.1 輸入驗證

| 檢查 | 處理 |
|---|---|
| 空字串或僅空白 | `INPUT_REQUIRED` |
| 超過 100 字 | `INPUT_TOO_LONG` |
| 純數字或純符號 | `INPUT_UNSUPPORTED_CONTENT` |
| 前後空白 | 自動去除後再查快取 |

### 9.2 `ErrorCodeEnum`

| 錯誤碼 | HTTP | 訊息 | 觸發時機 |
|---|---|---|---|
| `INPUT_REQUIRED` | 400 | 輸入內容不可為空 | 空字串、僅空白 |
| `INPUT_TOO_LONG` | 400 | 輸入內容不可超過 100 字 | 超過長度上限 |
| `INPUT_UNSUPPORTED_CONTENT` | 400 | 輸入內容無法翻譯 | 純數字、純符號 |
| `TRANSLATION_SERVICE_UNAVAILABLE` | 503 | 翻譯服務暫時無法使用 | 網路連線失敗、對方服務中斷 |
| `TRANSLATION_SERVICE_TIMEOUT` | 504 | 翻譯服務回應逾時 | 超過設定逾時秒數 |
| `TRANSLATION_RESPONSE_INVALID` | 502 | 翻譯服務回傳資料格式錯誤 | 回傳 JSON 解析失敗、缺少欄位 |
| `TRANSLATION_QUOTA_EXCEEDED` | 503 | 翻譯服務額度不足 | API 餘額用盡 |
| `TRANSLATION_RATE_LIMITED` | 429 | 請求過於頻繁，請稍後再試 | 被對方限流 |
| `VOCABULARY_NOT_FOUND` | 404 | 找不到指定的單字 | 查詢不存在的單字 |
| `AUDIO_FILE_NOT_FOUND` | 404 | 找不到音檔 | 音檔遺失 |
| `DATA_PERSIST_FAILED` | 500 | 資料儲存失敗 | 寫入交易失敗 |
| `INTERNAL_ERROR` | 500 | 系統發生非預期錯誤 | 兜底 |

`TRANSLATION_QUOTA_EXCEEDED` 刻意與 `TRANSLATION_SERVICE_UNAVAILABLE` 分開 — 餘額用盡需儲值，服務中斷需等待，處理方式完全不同。

### 9.3 語音失敗不拋出例外

TTS 失敗時翻譯結果照常寫入與回傳，`audio_file` 為 null。失敗僅記錄於 `api_usage_log`（`is_success = false`）與系統 log，並以內部的 `SpeechFailureReasonEnum`（`CONNECTION_FAILED` / `TIMEOUT` / `QUOTA_EXCEEDED` / `FILE_SAVE_FAILED`）標記原因，供日後補產生音檔時查詢。

理由：AI 呼叫費用已產生，捨棄可惜；且「有翻譯但暫時無聲音」的體驗優於整筆查詢失敗。

### 9.4 例外與全域處理

單一自訂例外攜帶錯誤碼：

```java
throw new BusinessException(ErrorCodeEnum.INPUT_TOO_LONG);
```

全域處理器依 `ErrorCodeEnum` 決定 HTTP 狀態與訊息：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDto> handleBusinessException(BusinessException exception) {
        ErrorCodeEnum errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(new ErrorResponseDto(errorCode.name(), errorCode.getMessage()));
    }
}
```

### 9.5 錯誤回應格式

```json
{
  "code": "INPUT_TOO_LONG",
  "message": "輸入內容不可超過 100 字",
  "traceId": "8f3a2c1e"
}
```

`traceId` 供使用者回報問題時精準定位 log。

### 9.6 安全規則

**兜底的 `Exception` 處理器不得將原始例外訊息回傳前端。** 資料庫錯誤可能含連線字串或帳號，外部 API 錯誤可能夾帶 API Key 片段。一律回傳固定的 `INTERNAL_ERROR` 訊息，詳細內容僅寫入伺服器 log。

### 9.7 AI 回傳格式異常

解析失敗時**不得寫入資料庫**，記錄於 `api_usage_log`（`is_success = false`）後拋出 `TRANSLATION_RESPONSE_INVALID`。快取一經寫入即為永久，錯誤資料會持續影響後續所有查詢。

---

## 10. 測試策略

| 層 | 方式 |
|---|---|
| Service | 單元測試，mock `TranslationClient` 與 `SpeechClient` |
| Repository | `@DataJpaTest` + H2 記憶體資料庫 |
| Controller | `MockMvc`，驗證輸入檢查與回應格式 |

**自動化測試中禁止實際呼叫外部 AI / TTS 服務。** 原因：每次執行皆產生費用；且測試會因網路或外部服務不穩而隨機失敗，失去可信度。真實呼叫僅於手動驗證時執行。

外部呼叫抽成介面的可測試性，正是該設計的附帶效益。

---

## 11. 未來規劃：會員功能

確定會做，但不在第一版。屆時新增：

| 新增表 | 用途 |
|---|---|
| `user` | 帳號、密碼雜湊、Email、狀態 |
| `user_query_history` | 使用者查詢紀錄（指向 `translation_query`） |
| `user_favorite` | 生字本（**指向 `vocabulary`，不複製資料**） |

現有四張表結構不需變更，僅 `translation_query` 可能新增 nullable 的 `created_by`，屆時 `ALTER TABLE` 即可。

程式面只需新增 `UserController` / `UserService` / `UserRepository`，並在現有服務外層加上權限檢查，`TranslationService` 本身不需修改。

**「預留」透過乾淨的分層達成，現階段不撰寫任何用不到的程式碼、不增加用不到的欄位。**

---

## 12. 待辦與待確認事項

| 項目 | 說明 |
|---|---|
| 泰語 TTS 品質試聽 | OpenAI 語音以英文為主，Google / Azure 有專屬 `th-TH` 語音。建議於 `openai.fm`、Google Cloud TTS 產品頁、Azure Speech Studio 分別試聽同一句泰文後再定案。因語音服務已抽成介面，日後更換不影響其他程式 |
| 外部服務單價查證 | 實作前需查證 OpenAI 官方最新定價，填入 `application.properties` |
| pom.xml 調整 | 將 `spring-ai-starter-model-anthropic` 替換為 `spring-ai-starter-model-openai` |
| 版本控制 | 專案目前尚未初始化 git，需先 `git init` 才能依規範進行分支與提交 |
