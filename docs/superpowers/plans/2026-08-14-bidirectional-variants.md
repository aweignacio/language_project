# 雙向翻譯與多重說法 實作計畫

> **給執行者：** 請搭配 superpowers:subagent-driven-development 或 superpowers:executing-plans 逐項執行。步驟使用 `- [ ]` 追蹤進度。

> ⚠ **這個專案禁止自行 commit。** 每個任務最後都有 commit 步驟，但依 `CLAUDE.md` 規定，**必須先向 Awei 回報做了什麼並取得明確同意才能執行**。程式照改、測試照跑，停在「已修改、未提交」的狀態。

**目標：** 讓查單字時列出泰文的所有說法（含性別與禮貌標籤），讓整句翻譯依使用者性別造句，並支援泰翻中與逐詞發音。

**架構：** 沿用現有分層（Controller → Service → Client / Repository）。新增 `audio_asset` 表讓「同一段文字全站只合成一次」，移除 `TranslationService` 的單字庫捷徑讓多重說法真的生效。翻譯與多重說法在同一次 AI 呼叫中取得，不增加費用。

**技術：** Spring Boot 4.1.0 / Java 21 / SQL Server 2022 / Spring AI（OpenAI gpt-5.5 + tts-1）/ Angular 前端 / JUnit 5 + Mockito + AssertJ

**規格書：** `docs/superpowers/specs/2026-08-14-bidirectional-variants-design.md`

---

## 執行前必讀

### 測試怎麼跑

```powershell
.\mvnw.cmd test                              # 全部
.\mvnw.cmd test -Dtest=LanguageDetectorTest  # 單一類別
```

- **不要用 IntelliJ 的綠色箭頭。** IntelliJ 2023.3 跑不動這個專案的 JUnit 版本，一定會失敗，那不是程式的錯。
- 出現 `Unresolved compilation problem` 之類的怪錯誤 → 先 `.\mvnw.cmd clean`，那是 IDE 殘留的舊 class。
- **Repository 測試（`@DataJpaTest`）連的是真的 SQL Server，不是 H2**，執行前必須先啟動 Docker 容器。這是刻意的設計——H2 沒有 `VARCHAR` / `NVARCHAR` 的區別，用 H2 測等於這個專案最危險的問題永遠測不到。

### 寫程式的規矩（違反會被退回）

| 規矩 | 說明 |
|---|---|
| 註解用繁體中文 | Javadoc 風格，**不可有 `<p>` `</p>`**。類別名、`null`、`token`、HTTP 狀態碼等技術名詞保持原文 |
| 有流程的檔案要加流程註解 | Service、Client、Controller、例外處理器、**所有測試檔**。放在 `package` 之後，用 `/* ... */`。風格見下方 |
| 純宣告的檔案不要加流程註解 | Repository、DTO、record、enum、Entity 只要一兩句 Javadoc |
| 判空與比較 | 用 `Objects.isNull/nonNull`、`ObjectUtils.isEmpty/isNotEmpty`、`Objects.equals`。**禁止 `== null`、`!= null`、直接 `.equals()`** |
| 固定值用 Enum | 類別名以 `Enum` 結尾，標 `@Getter`。禁止硬寫 `"MALE"` 這種字串 |
| Lambda 參數命名 | 禁止單字母。用 `variant`、`segment`、`word` 這種看得懂的名字 |
| commit 禁用 `--no-verify` | 沒有例外 |

**流程註解的風格是硬性規定：**
1. 由上往下，從使用者的實際動作開始（「你在網頁輸入『我』，按下查詢」），不要從架構分幾層開始
2. 分步驟編號，每步標明**誰**（哪個類別）、**哪個方法**、**當下資料實際長什麼樣**
3. 貼真實資料（實際的 JSON、字串值、數字），不要只寫「回傳一個物件」
4. 名詞第一次出現要解釋「為什麼需要它」
5. 用 ★ 標出最容易搞混的地方
6. 測試檔還要說明哪些東西被換成假的、每個測試各自在防什麼

---

## 檔案結構

### 新增

| 檔案 | 職責 | 流程註解 |
|---|---|---|
| `enums/TranslationDirectionEnum.java` | 翻譯方向 | 否 |
| `enums/SpeakerGenderEnum.java` | 使用者性別 | 否 |
| `enums/GenderUsageEnum.java` | 某個說法適合誰用 | 否 |
| `enums/PolitenessEnum.java` | 禮貌程度 | 否 |
| `enums/SpeechLanguageEnum.java` | 音檔語言，兼決定子資料夾 | 否 |
| `service/LanguageDetector.java` | 依字元範圍判斷翻譯方向 | **是** |
| `entity/AudioAsset.java` | `audio_asset` 對應 | 否 |
| `repository/AudioAssetRepository.java` | 音檔資產存取 | 否 |
| `service/AudioAssetService.java` | 「查表→命中就回→未命中才合成」的守門人 | **是** |
| `controller/AudioController.java` | `POST /api/v1/audio` | **是** |
| `dto/request/AudioRequestDto.java` | | 否 |
| `dto/response/AudioResponseDto.java` | | 否 |
| `dto/response/AudioAssetDto.java` | | 否 |
| `dto/response/TranslationVariantDto.java` | | 否 |
| `client/model/TranslationVariant.java` | | 否 |

### 修改

| 檔案 | 改什麼 |
|---|---|
| `db/schema.sql` | 依規格第 4 節重寫 |
| `enums/ErrorCodeEnum.java` | 新增 `SPEECH_TEXT_UNKNOWN` |
| `entity/TranslationQuery.java` | 加 `direction` / `gender` / `chineseText`，移除 `audioFile` |
| `entity/Vocabulary.java` | 加 `genderUsage` / `politeness` / `note` |
| `repository/TranslationQueryRepository.java` | 依 `(sourceText, direction, gender)` 查 |
| `repository/VocabularyRepository.java` | `findByChineseText` 改回傳 `List`；既有詞查詢改用中泰組合 |
| `client/SpeechClient.java` | `synthesize(String, SpeechLanguageEnum)` |
| `client/openai/OpenAiSpeechClient.java` | 依語言寫入 `th/` 或 `zh/` |
| `client/TranslationClient.java` | `translate(String, TranslationDirectionEnum, SpeakerGenderEnum)` |
| `client/openai/OpenAiTranslationClient.java` | 兩套提示詞、解析 `variants`、去重、上限、中文字檢查擴及 variants |
| `client/model/TranslationResult.java` | 加 `chineseText` 與 `variants` |
| `service/TranslationPersistenceService.java` | 寫入新欄位、多筆 vocabulary、合併規則 |
| `service/TranslationService.java` | 方向判斷、性別傳遞、**移除單字庫捷徑**、組裝 variants |
| `service/VocabularyService.java` | 註解更新：這張表不再是快取 |
| `config/AudioStorageProperties.java` | 新增 `autoGenerate` |
| `dto/request/TranslationRequestDto.java` | 加 `gender` |
| `dto/response/TranslationResponseDto.java` | 依規格第 9.1 節重寫 |
| `dto/response/TranslationQueryDto.java` | 依新欄位調整 |
| `dto/response/TranslationSegmentDto.java` | 加兩個音檔網址 |
| `dto/response/VocabularyDto.java` | 加三個新欄位 |
| `controller/TranslationController.java` | 傳遞 `gender` |
| `frontend/src/app/**` | 性別切換、說法列表、逐詞播放鍵 |

**`config/WebMvcConfig.java` 不需異動**（`/audio/**` 已支援子路徑）。

---

## Task 1：五個新 Enum 與新錯誤碼

**Files:**
- Create: `src/main/java/com/tim/language_project/enums/TranslationDirectionEnum.java`
- Create: `src/main/java/com/tim/language_project/enums/SpeakerGenderEnum.java`
- Create: `src/main/java/com/tim/language_project/enums/GenderUsageEnum.java`
- Create: `src/main/java/com/tim/language_project/enums/PolitenessEnum.java`
- Create: `src/main/java/com/tim/language_project/enums/SpeechLanguageEnum.java`
- Modify: `src/main/java/com/tim/language_project/enums/ErrorCodeEnum.java`

這一批是純宣告，沒有邏輯可測，所以不寫測試（專案既有的 enum 也都沒有測試）。後續每個任務的測試都會用到它們，編譯過不了就會立刻發現。

- [ ] **Step 1：建立 `TranslationDirectionEnum`**

```java
package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 翻譯方向。由 LanguageDetector 依輸入的字元範圍判斷，不由使用者選擇。
 */
@Getter
public enum TranslationDirectionEnum {

    ZH_TO_TH("中翻泰"),
    TH_TO_ZH("泰翻中");

    private final String description;

    TranslationDirectionEnum(String description) {
        this.description = description;
    }
}
```

- [ ] **Step 2：建立 `SpeakerGenderEnum`**

```java
package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 說話者的性別，由前端每次請求傳入，影響泰文造句的自稱與句尾助詞。
 * 泰翻中沒有性別概念，該方向一律存 null。
 * 注意與 GenderUsageEnum 的差別：這個描述「使用者是誰」，那個描述「某個說法適合誰」。
 */
@Getter
public enum SpeakerGenderEnum {

    MALE("男性"),
    FEMALE("女性");

    private final String description;

    SpeakerGenderEnum(String description) {
        this.description = description;
    }
}
```

- [ ] **Step 3：建立 `GenderUsageEnum`**

```java
package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 某一個泰文說法適合哪種性別使用。
 * 比 SpeakerGenderEnum 多一個 BOTH —— 使用者不可能「男女都是」，
 * 但一個詞可以是男女通用的（例如 กู）。
 */
@Getter
public enum GenderUsageEnum {

    MALE("男性使用"),
    FEMALE("女性使用"),
    BOTH("不分性別");

    private final String description;

    GenderUsageEnum(String description) {
        this.description = description;
    }
}
```

- [ ] **Step 4：建立 `PolitenessEnum`**

```java
package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 一個泰文說法的禮貌程度。
 * 前端要把 RUDE 用警示色標出來 —— 用錯場合的後果是冒犯到人，不是講得不夠好。
 */
@Getter
public enum PolitenessEnum {

    FORMAL("正式"),
    NEUTRAL("一般"),
    CASUAL("隨性"),
    RUDE("粗俗");

    private final String description;

    PolitenessEnum(String description) {
        this.description = description;
    }
}
```

- [ ] **Step 5：建立 `SpeechLanguageEnum`**

```java
package com.tim.language_project.enums;

import lombok.Getter;

/**
 * 音檔的語言。folderName 同時決定音檔存在 audio 底下的哪個子資料夾，
 * 例如 TH 的檔案會存成 audio/th/a1b2c3.mp3。
 */
@Getter
public enum SpeechLanguageEnum {

    TH("泰文", "th"),
    ZH("中文", "zh");

    private final String description;

    private final String folderName;

    SpeechLanguageEnum(String description, String folderName) {
        this.description = description;
        this.folderName = folderName;
    }
}
```

- [ ] **Step 6：在 `ErrorCodeEnum` 新增一個錯誤碼**

在 `AUDIO_FILE_NOT_FOUND` 那一行後面加上：

```java
    SPEECH_TEXT_UNKNOWN(HttpStatus.BAD_REQUEST, "無法為未知的文字產生語音"),
```

★ 只加這一個。不要加 `INPUT_LANGUAGE_UNSUPPORTED` —— 方向判斷不會失敗（見 Task 2）。

- [ ] **Step 7：確認編譯通過**

```powershell
.\mvnw.cmd clean compile
```

預期：`BUILD SUCCESS`

- [ ] **Step 8：回報並徵求同意後 commit**

```
新增翻譯方向與說法標籤的 Enum

Feat:
- 新增 TranslationDirectionEnum、SpeakerGenderEnum、GenderUsageEnum、PolitenessEnum、SpeechLanguageEnum
- ErrorCodeEnum 新增 SPEECH_TEXT_UNKNOWN
```

---

## Task 2：LanguageDetector（判斷翻譯方向）

**Files:**
- Create: `src/main/java/com/tim/language_project/service/LanguageDetector.java`
- Test: `src/test/java/com/tim/language_project/service/LanguageDetectorTest.java`

- [ ] **Step 1：先寫會失敗的測試**

建立 `src/test/java/com/tim/language_project/service/LanguageDetectorTest.java`：

```java
package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  LanguageDetector 決定「你打的這串字，要走中翻泰還是泰翻中」。
 *  判斷錯的後果不是報錯，是安靜地翻反方向 —— 你輸入泰文，
 *  系統以為你在講中文，把泰文原封不動再「翻」一次泰文給你。
 *
 *  這個檔案沒有換掉任何東西（沒有 mock）。LanguageDetector 只看字串本身，
 *  不連資料庫、不連網路，所以直接 new 一個出來測就好。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  測試一  純中文「我想喝酒」   → 要走中翻泰。這是最常見的用法，壞了就整個網站沒用
 *  測試二  純泰文「ผมอยากดื่มเหล้า」→ 要走泰翻中。新功能的入口
 *  測試三  中泰混合             → 要走泰翻中。使用者貼上一段有註解的泰文時會發生
 *  測試四  ★純數字「5」        → 要走中翻泰。
 *                                這一題最重要 —— 現有的提示詞明確支援數字輸入
 *                                （「5」會翻成「ห้า」）。如果有人把判斷邏輯改成
 *                                「不是中文也不是泰文就報錯」，這個現有功能會壞掉，
 *                                而且不會有人發現，因為沒人會特地去測數字。
 *  測試五  亂碼「asdfgh」       → 要走中翻泰，交給 AI 的 translatable 去擋，
 *                                不在這裡報錯（維持現有行為）
 */

import com.tim.language_project.enums.TranslationDirectionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageDetectorTest {

    private final LanguageDetector languageDetector = new LanguageDetector();

    @Test
    @DisplayName("純中文應判為中翻泰")
    void shouldDetectChineseAsZhToTh() {
        assertThat(languageDetector.detect("我想喝酒"))
                .isEqualTo(TranslationDirectionEnum.ZH_TO_TH);
    }

    @Test
    @DisplayName("純泰文應判為泰翻中")
    void shouldDetectThaiAsThToZh() {
        assertThat(languageDetector.detect("ผมอยากดื่มเหล้า"))
                .isEqualTo(TranslationDirectionEnum.TH_TO_ZH);
    }

    @Test
    @DisplayName("中泰混合時泰文優先，判為泰翻中")
    void shouldDetectMixedAsThToZh() {
        assertThat(languageDetector.detect("ผม（我）"))
                .isEqualTo(TranslationDirectionEnum.TH_TO_ZH);
    }

    /*
     * ★ 這個測試是為了保住一個現有功能。
     *   提示詞裡明確寫著「包含數字，例如『5』就是『ห้า』」，
     *   所以純數字必須能正常走完中翻泰的流程，不可以被判斷邏輯擋在門外。
     */
    @Test
    @DisplayName("純數字應判為中翻泰，不可被擋下")
    void shouldDetectDigitsAsZhToTh() {
        assertThat(languageDetector.detect("5"))
                .isEqualTo(TranslationDirectionEnum.ZH_TO_TH);
    }

    @Test
    @DisplayName("亂碼應判為中翻泰，由模型自行回報無法翻譯")
    void shouldDetectGibberishAsZhToTh() {
        assertThat(languageDetector.detect("asdfgh"))
                .isEqualTo(TranslationDirectionEnum.ZH_TO_TH);
    }
}
```

- [ ] **Step 2：跑測試，確認它失敗**

```powershell
.\mvnw.cmd test -Dtest=LanguageDetectorTest
```

預期：編譯失敗，`LanguageDetector` 這個類別不存在。

- [ ] **Step 3：寫出實作**

建立 `src/main/java/com/tim/language_project/service/LanguageDetector.java`：

```java
package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  看一眼你打進來的字，決定這次要「中翻泰」還是「泰翻中」。
 *
 *  為什麼需要它：這個網站兩個方向都支援，但畫面上「沒有」切換方向的按鈕。
 *  你打什麼，系統就自己知道你要什麼。這個檔案就是那個「自己知道」。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你打字到決定方向
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在網頁輸入「ผมอยากดื่มเหล้า」，按下查詢 ──────────────────
 *
 *    TranslationService 拿到這串字之後，第一件事就是問這裡：
 *
 *        languageDetector.detect("ผมอยากดื่มเหล้า");
 *
 * ── 第 2 步｜看這串字裡面有沒有泰文字 ───────────────────────────────────
 *
 *    每一個字在電腦裡都是一個編號（叫做 Unicode 碼位）。
 *    泰文字的編號全部落在 0E00 到 0E7F 這一段，中文則在 4E00 到 9FFF。
 *    這不是猜的，是查表 —— 所以判斷結果是確定的，不會有模糊地帶。
 *
 *        "ผมอยากดื่มเหล้า" 的第一個字 ผ 編號是 0E1C  → 落在泰文區間 → 有泰文
 *
 *    有泰文  → TH_TO_ZH（泰翻中）
 *    沒泰文  → ZH_TO_TH（中翻泰）
 *
 * ── 第 3 步｜回傳方向，後面的流程照這個方向走 ───────────────────────────
 *
 *    TranslationService 會用它決定：查快取時 direction 欄位填什麼、
 *    呼叫 AI 時要用哪一套提示詞、gender 要不要存。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  ★ 兩個最容易被改壞的地方
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  ★ 一：這個方法「永遠不會失敗」，不要幫它加錯誤處理。
 *
 *    看起來很自然的一個「改進」是：
 *
 *        既不是中文也不是泰文 → 丟一個「不支援的語言」錯誤
 *
 *    那是錯的，會弄壞一個現有功能。提示詞裡明確支援數字輸入
 *    （見 OpenAiTranslationClient 的 SYSTEM_PROMPT：「包含數字，例如『5』就是『ห้า』」）。
 *    你輸入「5」，字元既不是中文也不是泰文，加了那個判斷就會被擋在門外。
 *
 *    亂碼要怎麼辦？交給 AI。它會回 translatable = false，
 *    TranslationService 就會擋下來。那條路本來就存在，不需要在這裡再擋一次。
 *
 *  ★ 二：為什麼是「有泰文就算泰文」，而不是「哪種字多算哪種」？
 *
 *    因為使用者貼上泰文時，常常會連著中文註解一起貼，例如「ผม（我）」。
 *    這種情況他要的是「幫我看懂這段泰文」，不是「幫我把『我』翻成泰文」。
 *
 *  測試檔：src/test/java/com/tim/language_project/service/LanguageDetectorTest.java
 */

import com.tim.language_project.enums.TranslationDirectionEnum;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 依輸入的字元範圍判斷翻譯方向。
 * 這個判斷不會失敗，理由見檔案開頭的★一。
 */
@Component
public class LanguageDetector {

    /** 泰文的 Unicode 區間。 */
    private static final Pattern THAI_PATTERN = Pattern.compile("[\\u0E00-\\u0E7F]");

    /**
     * 判斷翻譯方向。含泰文字就是泰翻中，其餘一律中翻泰（包含純數字）。
     */
    public TranslationDirectionEnum detect(String sourceText) {
        if (THAI_PATTERN.matcher(sourceText).find()) {
            return TranslationDirectionEnum.TH_TO_ZH;
        }

        return TranslationDirectionEnum.ZH_TO_TH;
    }
}
```

- [ ] **Step 4：跑測試，確認五個都通過**

```powershell
.\mvnw.cmd test -Dtest=LanguageDetectorTest
```

預期：`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5：回報並徵求同意後 commit**

```
新增翻譯方向判斷

Feat:
- 新增 LanguageDetector，依字元範圍判斷中翻泰或泰翻中
- 純數字維持走中翻泰，避免既有的數字輸入功能失效
```

---

## Task 3：重建資料表

**Files:**
- Modify: `db/schema.sql`

> ⚠ **這個任務會刪掉所有既有資料。SQL 由 Awei 自己執行，執行者只負責改檔案。**
> 資料量僅 5 個音檔，重查成本約新台幣數元（規格決策 15）。

- [ ] **Step 1：在 `db/schema.sql` 最上方的說明區塊後、`USE` 之前，加入重建區塊**

```sql
/* ============================================================
 * 【重建】2026-08-14 雙向翻譯與多重說法改版
 *
 * 這次改版動到三張表的結構，且既有資料全部作廢：
 *   - 舊的句子是「沒有性別概念」翻出來的，算不上男版也算不上女版
 *   - 舊的單字沒有性別、禮貌、說明欄位，也只有單一說法
 *
 * 因為資料要清空，這裡直接 DROP 後重建，不使用 ALTER TABLE。
 *
 * ★ api_usage_log 不刪 —— 那是花錢的稽核紀錄，刪掉就查不回歷史費用。
 *
 * 執行這一段之後，記得手動清空 audio/ 資料夾底下的 mp3，
 * 並建立 audio/th/ 與 audio/zh/ 兩個子資料夾。
 * ============================================================ */
-- 刪除順序：有外鍵的先刪
DROP TABLE IF EXISTS dbo.translation_segment;
DROP TABLE IF EXISTS dbo.translation_query;
DROP TABLE IF EXISTS dbo.vocabulary;
DROP TABLE IF EXISTS dbo.audio_asset;
GO
```

- [ ] **Step 2：把 `translation_query` 的建表語句整個換掉**

```sql
IF OBJECT_ID('dbo.translation_query', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.translation_query
    (
        id            BIGINT          IDENTITY(1,1)   NOT NULL,

        -- 使用者實際輸入的原文，前後空白於寫入前去除。快取的鑰匙。
        source_text   NVARCHAR(100)                   NOT NULL,

        -- TranslationDirectionEnum：ZH_TO_TH / TH_TO_ZH
        direction     VARCHAR(20)                     NOT NULL,

        -- SpeakerGenderEnum：MALE / FEMALE。
        -- 泰翻中沒有性別概念，該方向一律為 NULL。
        gender        VARCHAR(10)                     NULL,

        -- 這句話的中文面與泰文面。
        --
        -- ★ source_text 必定與其中一面完全相同，這份重複是刻意的：
        --   source_text 專職當快取的鑰匙，另外兩欄專職表示「這句話的兩面」。
        --   混用的話，程式每次都要先判斷方向才知道哪個欄位裝什麼，很容易寫錯。
        chinese_text  NVARCHAR(500)                   NOT NULL,
        thai_text     NVARCHAR(500)                   NOT NULL,

        -- 泰文的羅馬拼音（含聲調符號，如 chǎn、dùuem）
        romanization  NVARCHAR(500)                   NOT NULL,

        created_at    DATETIME2                       NOT NULL
            CONSTRAINT DF_translation_query_created_at DEFAULT SYSDATETIME(),
        updated_at    DATETIME2                       NOT NULL
            CONSTRAINT DF_translation_query_updated_at DEFAULT SYSDATETIME(),

        CONSTRAINT PK_translation_query
            PRIMARY KEY (id),

        -- 快取命中的判斷依據。
        --
        -- ★ SQL Server 的 UNIQUE 把 NULL 當成一個值來比對，
        --   所以「同一句泰文（gender 為 NULL）只會有一筆」仍然成立，不需額外處理。
        CONSTRAINT UQ_translation_query_key
            UNIQUE (source_text, direction, gender),

        CONSTRAINT CK_translation_query_direction
            CHECK (direction IN ('ZH_TO_TH', 'TH_TO_ZH')),

        CONSTRAINT CK_translation_query_gender
            CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE'))
    );
END
GO
```

★ `audio_file` 欄位**不再存在**。音檔統一由 `audio_asset` 管理，留著會變成兩個地方都聲稱自己有音檔。

- [ ] **Step 3：把 `vocabulary` 的建表語句整個換掉**

```sql
IF OBJECT_ID('dbo.vocabulary', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vocabulary
    (
        id            BIGINT          IDENTITY(1,1)   NOT NULL,

        chinese_text  NVARCHAR(50)                    NOT NULL,
        thai_text     NVARCHAR(100)                   NOT NULL,
        romanization  NVARCHAR(100)                   NOT NULL,

        -- GenderUsageEnum：MALE / FEMALE / BOTH。這個「說法」適合誰用。
        --
        -- ★ 與 translation_query.gender 是不同的概念：
        --   那個是「使用者是誰」，這個是「這個說法適合誰」，
        --   而且只有這裡才有 BOTH（使用者不可能男女都是）。
        --
        -- 從句子拆解沉澱下來的詞沒有這項資訊，為 NULL，
        -- 日後單獨查詢該詞時才會補上（合併規則見 TranslationPersistenceService）。
        gender_usage  VARCHAR(10)                     NULL,

        -- PolitenessEnum：FORMAL / NEUTRAL / CASUAL / RUDE
        politeness    VARCHAR(10)                     NULL,

        -- 中文說明，例如「男生自稱，正式或對不熟的人使用」
        note          NVARCHAR(200)                   NULL,

        -- VocabularySourceTypeEnum：
        --   SEGMENT —— 由多詞句子拆解而來
        --   DIRECT  —— 使用者輸入的完整內容即為此詞
        -- 已存在的列不更新此欄位，以首次寫入的值為準。
        source_type   VARCHAR(20)                     NOT NULL,

        created_at    DATETIME2                       NOT NULL
            CONSTRAINT DF_vocabulary_created_at DEFAULT SYSDATETIME(),
        updated_at    DATETIME2                       NOT NULL
            CONSTRAINT DF_vocabulary_updated_at DEFAULT SYSDATETIME(),

        CONSTRAINT PK_vocabulary
            PRIMARY KEY (id),

        -- ★ 從「一個中文詞只能一列」改成「一個說法一列」。
        --   「我」會佔 ผม / ฉัน / กู 三列，這是預期行為，不是資料重複。
        CONSTRAINT UQ_vocabulary_chinese_thai
            UNIQUE (chinese_text, thai_text),

        CONSTRAINT CK_vocabulary_source_type
            CHECK (source_type IN ('SEGMENT', 'DIRECT')),

        CONSTRAINT CK_vocabulary_gender_usage
            CHECK (gender_usage IS NULL OR gender_usage IN ('MALE', 'FEMALE', 'BOTH')),

        CONSTRAINT CK_vocabulary_politeness
            CHECK (politeness IS NULL
                   OR politeness IN ('FORMAL', 'NEUTRAL', 'CASUAL', 'RUDE'))
    );
END
GO
```

- [ ] **Step 4：新增 `audio_asset` 建表語句（放在 `vocabulary` 之後、`api_usage_log` 之前）**

```sql
/* ============================================================
 * 4. audio_asset —— 音檔資產
 *
 * 規則只有一句話：★ 同一段文字，全站只會有一個 mp3 ★
 *
 * 不管這段泰文是「整句翻譯的結果」、「單字的某一種說法」、
 * 還是「別的句子裡剛好出現的同一個詞」，通通指向同一個檔案。
 *
 * 這張表是本專案「用越久越省錢」的核心：
 * 查過的東西永遠不會再付第二次語音費用。
 * ============================================================ */
IF OBJECT_ID('dbo.audio_asset', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.audio_asset
    (
        id          BIGINT      IDENTITY(1,1)   NOT NULL,

        -- 要唸出來的文字。可能短到一個詞（เหล้า），長到一整句。
        speech_text NVARCHAR(500)               NOT NULL,

        -- SpeechLanguageEnum：TH / ZH
        language    VARCHAR(10)                 NOT NULL,

        -- 相對於 audio 資料夾的路徑，例如 th/a1b2c3d4e5f6.mp3。
        -- 系統產生的 ASCII 字串，故用 VARCHAR。
        file_path   VARCHAR(100)                NOT NULL,

        created_at  DATETIME2                   NOT NULL
            CONSTRAINT DF_audio_asset_created_at DEFAULT SYSDATETIME(),

        CONSTRAINT PK_audio_asset
            PRIMARY KEY (id),

        -- ★ 這條唯一鍵就是「同一段文字只合成一次」的保證。
        --   拿掉它，程式仍然會跑，只是會安靜地一直重複付錢。
        CONSTRAINT UQ_audio_asset_text_language
            UNIQUE (speech_text, language),

        CONSTRAINT CK_audio_asset_language
            CHECK (language IN ('TH', 'ZH'))
    );
END
GO
```

- [ ] **Step 5：更新檔案開頭的說明**

原本寫著「四張表」的地方改成「五張表」。**並刪掉這句已經不成立的話：**

> 四張表中「只有這張」持有音檔。

改成：

```
 * 音檔一律由 audio_asset 持有，其他表只存文字。
 * （2026-08-14 之前只有 translation_query 存音檔，
 *   改版後改為以文字內容為鍵全站共用，見該表說明。）
```

同時更新檔案最下方「重新建立」段落的 DROP 順序，加入 `audio_asset`。

- [ ] **Step 6：交給 Awei 執行**

執行者**不要自己跑這段 SQL**。改完檔案後回報：

> `db/schema.sql` 已更新。請你自己執行重建，並清空 `audio/` 底下的 mp3、建立 `audio/th/` 與 `audio/zh/` 資料夾。

Awei 可用的指令（僅供參考，由他決定）：

```powershell
docker cp db\schema.sql sqlserver:/tmp/schema.sql
docker exec sqlserver /opt/mssql-tools18/bin/sqlcmd `
    -S localhost -U sa -P 'Sqlserver123456' -C -f 65001 -i /tmp/schema.sql
```

- [ ] **Step 7：等 Awei 確認資料表已重建後再繼續**

後面所有 Repository 測試都依賴新結構，沒重建會全部失敗。

- [ ] **Step 8：回報並徵求同意後 commit**

```
資料表改版支援雙向翻譯與多重說法

Modify:
- translation_query 新增 direction、gender、chinese_text，移除 audio_file
- vocabulary 唯一鍵改為中文＋泰文，新增 gender_usage、politeness、note
- 新增 audio_asset，以文字內容與語言為鍵，全站共用音檔
```

---

## Task 4：AudioAsset 實體與存取

**Files:**
- Create: `src/main/java/com/tim/language_project/entity/AudioAsset.java`
- Create: `src/main/java/com/tim/language_project/dto/response/AudioAssetDto.java`
- Create: `src/main/java/com/tim/language_project/repository/AudioAssetRepository.java`
- Test: `src/test/java/com/tim/language_project/repository/AudioAssetRepositoryTest.java`

> ⚠ 這個測試連的是真的 SQL Server，執行前先確認 Docker 容器已啟動。

- [ ] **Step 1：先寫會失敗的測試**

```java
package com.tim.language_project.repository;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  audio_asset 這張表只有一個任務：★ 同一段文字全站只合成一次 ★
 *
 *  這件事完全靠資料庫的唯一鍵 UQ_audio_asset_text_language 撐著。
 *  那條唯一鍵如果哪天被誰拿掉，程式「不會壞」—— 它會照常運作，
 *  只是每次查詢都重新合成一次語音，安靜地一直花錢。
 *  這種問題不會有人發現，所以要用測試把它釘住。
 *
 * ── 為什麼連真的 SQL Server 而不用 H2 ───────────────────────────────────
 *
 *  @AutoConfigureTestDatabase(replace = NONE) 是在擋掉 @DataJpaTest
 *  「偷偷換成 H2」的預設行為。因為這個資料庫的 collation 存不了非 ASCII 字元，
 *  泰文一定要用 NVARCHAR 才不會變成問號，而 H2 沒有這個區別 ——
 *  用 H2 測等於這個專案最危險的問題永遠測不到。
 *
 *  代價是跑測試前要先啟動 Docker 容器。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  測試一  泰文存進去再撈出來還是泰文（防 NVARCHAR 被改成 VARCHAR）
 *  測試二  同一段文字加同一個語言，第二次寫入必須失敗（防唯一鍵被拿掉）
 *  測試三  同一段文字但不同語言可以各存一筆（中泰同形的字不該互相擋）
 */

import com.tim.language_project.dto.response.AudioAssetDto;
import com.tim.language_project.entity.AudioAsset;
import com.tim.language_project.enums.SpeechLanguageEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AudioAssetRepositoryTest {

    @Autowired
    private AudioAssetRepository audioAssetRepository;

    @Test
    @DisplayName("泰文存入後應原樣取回")
    void shouldKeepThaiCharacters() {
        audioAssetRepository.saveAndFlush(
                newAudioAsset("เหล้า", SpeechLanguageEnum.TH, "th/a1b2c3.mp3"));

        Optional<AudioAssetDto> found = audioAssetRepository
                .findBySpeechTextAndLanguage("เหล้า", SpeechLanguageEnum.TH);

        assertThat(found).isPresent();
        assertThat(found.get().speechText()).isEqualTo("เหล้า");
        assertThat(found.get().filePath()).isEqualTo("th/a1b2c3.mp3");
    }

    /*
     * ★ 這個測試守著整個「用越久越省錢」的機制。
     *   唯一鍵一旦消失，這裡會變成綠燈，而正式環境會開始重複付語音費用。
     */
    @Test
    @DisplayName("同一段文字加同一語言不可重複寫入")
    void shouldRejectDuplicateTextAndLanguage() {
        audioAssetRepository.saveAndFlush(
                newAudioAsset("ขอบคุณ", SpeechLanguageEnum.TH, "th/d4e5f6.mp3"));

        assertThatThrownBy(() -> audioAssetRepository.saveAndFlush(
                newAudioAsset("ขอบคุณ", SpeechLanguageEnum.TH, "th/g7h8i9.mp3")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("同一段文字不同語言可各存一筆")
    void shouldAllowSameTextInDifferentLanguages() {
        audioAssetRepository.saveAndFlush(
                newAudioAsset("OK", SpeechLanguageEnum.TH, "th/j1k2l3.mp3"));
        audioAssetRepository.saveAndFlush(
                newAudioAsset("OK", SpeechLanguageEnum.ZH, "zh/m4n5o6.mp3"));

        assertThat(audioAssetRepository
                .findBySpeechTextAndLanguage("OK", SpeechLanguageEnum.ZH))
                .isPresent();
    }

    private AudioAsset newAudioAsset(String speechText,
                                     SpeechLanguageEnum language,
                                     String filePath) {
        AudioAsset audioAsset = new AudioAsset();
        audioAsset.setSpeechText(speechText);
        audioAsset.setLanguage(language);
        audioAsset.setFilePath(filePath);

        return audioAsset;
    }
}
```

- [ ] **Step 2：跑測試，確認它失敗**

```powershell
.\mvnw.cmd test -Dtest=AudioAssetRepositoryTest
```

預期：編譯失敗，`AudioAsset`、`AudioAssetDto`、`AudioAssetRepository` 都不存在。

- [ ] **Step 3：建立 `AudioAsset` 實體**

```java
package com.tim.language_project.entity;

import com.tim.language_project.enums.SpeechLanguageEnum;
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
 * 一段文字對應的音檔。整個專案的音檔都由這張表持有，其他表只存文字。
 * speechText 加 language 是唯一的，這保證同一段文字全站只會合成一次。
 */
@Entity
@Table(name = "audio_asset")
@Getter
@Setter
@NoArgsConstructor
public class AudioAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 要唸出來的文字，可能是一個詞也可能是一整句。 */
    @Column(name = "speech_text", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String speechText;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 10, nullable = false)
    private SpeechLanguageEnum language;

    /** 相對於 audio 資料夾的路徑，例如 th/a1b2c3.mp3。 */
    @Column(name = "file_path", length = 100, nullable = false)
    private String filePath;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4：建立 `AudioAssetDto`**

```java
package com.tim.language_project.dto.response;

import com.tim.language_project.enums.SpeechLanguageEnum;

/**
 * 音檔資產的投影，也就是 JPQL 建構子表達式要組出來的型別。
 */
public record AudioAssetDto(
        Long id,
        String speechText,
        SpeechLanguageEnum language,
        String filePath) {
}
```

- [ ] **Step 5：建立 `AudioAssetRepository`**

```java
package com.tim.language_project.repository;

import com.tim.language_project.dto.response.AudioAssetDto;
import com.tim.language_project.entity.AudioAsset;
import com.tim.language_project.enums.SpeechLanguageEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 音檔資產的資料存取。
 */
public interface AudioAssetRepository extends JpaRepository<AudioAsset, Long> {

    @Query("""
            SELECT new com.tim.language_project.dto.response.AudioAssetDto(
                audioAsset.id,
                audioAsset.speechText,
                audioAsset.language,
                audioAsset.filePath
            )

            FROM AudioAsset audioAsset

            WHERE audioAsset.speechText = :speechText
              AND audioAsset.language = :language
            """)
    Optional<AudioAssetDto> findBySpeechTextAndLanguage(
            @Param("speechText") String speechText,
            @Param("language") SpeechLanguageEnum language);
}
```

- [ ] **Step 6：跑測試，確認三個都通過**

```powershell
.\mvnw.cmd test -Dtest=AudioAssetRepositoryTest
```

預期：`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`

若出現「找不到資料表」→ Task 3 的 SQL 還沒執行。

- [ ] **Step 7：回報並徵求同意後 commit**

```
新增音檔資產的實體與存取

Feat:
- 新增 AudioAsset、AudioAssetDto、AudioAssetRepository
- 以文字內容加語言為唯一鍵，確保同一段文字只合成一次
```

---

## Task 5：語音客戶端支援語言與子資料夾

**Files:**
- Modify: `src/main/java/com/tim/language_project/client/SpeechClient.java`
- Modify: `src/main/java/com/tim/language_project/client/openai/OpenAiSpeechClient.java`
- Modify: `src/test/java/com/tim/language_project/client/openai/OpenAiSpeechClientTest.java`

- [ ] **Step 1：在測試檔補上兩個會失敗的測試**

在 `OpenAiSpeechClientTest` 既有測試之後加入。同時把檔案裡所有 `synthesize("...")` 的呼叫改成兩個參數的版本（第二個傳 `SpeechLanguageEnum.TH`），並在檔案開頭的流程註解補一段說明子資料夾這件事。

需要新增的 import：

```java
import com.tim.language_project.enums.SpeechLanguageEnum;
```

新增的測試：

```java
    /*
     * ═══ 泰文要存進 th/ 子資料夾 ═══════════════════════════════════════
     *
     * 為什麼要分資料夾：中文音檔和泰文音檔混在同一個資料夾裡，
     * 檔名又都是隨機亂碼，日後要清理或搬移完全分不出哪個是哪個。
     */
    @Test
    @DisplayName("泰文音檔應存入 th 子資料夾，回傳路徑含資料夾")
    void shouldStoreThaiAudioInThaiFolder() {
        when(textToSpeechModel.call("เหล้า")).thenReturn(new byte[]{1, 2, 3});

        Optional<String> filePath =
                openAiSpeechClient.synthesize("เหล้า", SpeechLanguageEnum.TH);

        assertThat(filePath).isPresent();
        assertThat(filePath.get()).startsWith("th/");
        assertThat(filePath.get()).endsWith(".mp3");
        assertThat(tempDirectory.resolve(filePath.get())).exists();
    }

    @Test
    @DisplayName("中文音檔應存入 zh 子資料夾")
    void shouldStoreChineseAudioInChineseFolder() {
        when(textToSpeechModel.call("酒")).thenReturn(new byte[]{1, 2, 3});

        Optional<String> filePath =
                openAiSpeechClient.synthesize("酒", SpeechLanguageEnum.ZH);

        assertThat(filePath).isPresent();
        assertThat(filePath.get()).startsWith("zh/");
        assertThat(tempDirectory.resolve(filePath.get())).exists();
    }
```

> 若既有測試沒有 `tempDirectory` 這個欄位，請照既有測試取得音檔資料夾的方式改寫這兩個斷言，重點是驗證「路徑前綴」與「檔案真的被寫出來」。

- [ ] **Step 2：跑測試，確認它失敗**

```powershell
.\mvnw.cmd test -Dtest=OpenAiSpeechClientTest
```

預期：編譯失敗，`synthesize` 沒有兩個參數的版本。

- [ ] **Step 3：改介面 `SpeechClient`**

```java
package com.tim.language_project.client;

import com.tim.language_project.enums.SpeechLanguageEnum;

import java.util.Optional;

/**
 * 把一段文字轉成音檔並存起來，回傳相對於 audio 資料夾的路徑（例如 th/a1b2c3.mp3）。
 * 失敗時回傳空的 Optional —— 語音出問題絕對不能連帶讓翻譯失敗，
 * 呼叫端當作「這段文字目前沒有音檔」處理即可。
 */
public interface SpeechClient {

    Optional<String> synthesize(String speechText, SpeechLanguageEnum language);
}
```

- [ ] **Step 4：改 `OpenAiSpeechClient`**

方法簽章與存檔那一段改成：

```java
    @Override
    public Optional<String> synthesize(String speechText, SpeechLanguageEnum language) {
        if (ObjectUtils.isEmpty(speechText)) {
            return Optional.empty();
        }

        byte[] audioBytes;

        try {
            audioBytes = textToSpeechModel.call(speechText);
        } catch (Exception exception) {
            // 沒接通就沒有費用，記 0 只是為了留下「這時候失敗過」的痕跡。
            recordFailure(SpeechFailureReasonEnum.CONNECTION_FAILED, 0L, exception);
            return Optional.empty();
        }

        if (ObjectUtils.isEmpty(audioBytes)) {
            // 接通也回應了，只是內容是空的 —— 這一次已經被收費。
            recordFailure(SpeechFailureReasonEnum.UNKNOWN, speechText.length(), null);
            return Optional.empty();
        }

        try {
            // 相對路徑，例如 th/a1b2c3d4e5f6.mp3。
            // 前端把它接在 /audio/ 後面就是可以直接播放的網址。
            String filePath = language.getFolderName() + "/" + newFileName();
            Path directory = Paths.get(audioStorageProperties.getDirectory())
                    .resolve(language.getFolderName());

            Files.createDirectories(directory);
            Files.write(Paths.get(audioStorageProperties.getDirectory()).resolve(filePath),
                    audioBytes);

            recordUsage(speechText.length(), true);

            return Optional.of(filePath);
        } catch (Exception exception) {
            // 聲音已經拿到了，錢也付了，是我們自己沒存下來。
            recordFailure(SpeechFailureReasonEnum.FILE_SAVE_FAILED,
                    speechText.length(), exception);
            return Optional.empty();
        }
    }
```

同時：
- 把 `newFileName()` 的 Javadoc 補一句「不含資料夾，資料夾由 language 決定」
- 檔案開頭的流程註解要加一段：音檔改存子資料夾、回傳值從「檔名」變成「相對路徑」
- 參數名從 `thaiText` 改成 `speechText`（現在也會收中文）

- [ ] **Step 5：跑測試，確認通過**

```powershell
.\mvnw.cmd test -Dtest=OpenAiSpeechClientTest
```

預期：全部 PASS。

此時 `TranslationService` 會編譯失敗（它還在呼叫單參數版本），這是預期的，Task 13 會修好。若要先讓專案編譯過，可暫時在 `TranslationService` 補上 `SpeechLanguageEnum.TH` 參數。

- [ ] **Step 6：回報並徵求同意後 commit**

```
語音客戶端支援多語言與子資料夾

Modify:
- SpeechClient 介面新增語言參數，回傳值改為相對路徑
- 泰文音檔存入 audio/th，中文音檔存入 audio/zh
```

---

## Task 6：AudioAssetService（省錢的守門人）

**Files:**
- Create: `src/main/java/com/tim/language_project/service/AudioAssetService.java`
- Test: `src/test/java/com/tim/language_project/service/AudioAssetServiceTest.java`

- [ ] **Step 1：先寫會失敗的測試**

```java
package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  AudioAssetService 是所有語音合成的唯一入口。它的工作只有一句話：
 *
 *      這段文字以前合成過嗎？合成過就把舊檔案給你，沒有才去花錢。
 *
 *  ★ 這是整個專案「用越久越省錢」的核心。
 *    如果它壞了，程式「不會出錯」—— 畫面照常、聲音照常，
 *    只是每一次都在重新付語音費用，而且沒有任何跡象。
 *    所以這個測試要盯得很緊。
 *
 * ── 哪些東西被換成假的 ──────────────────────────────────────────────────
 *
 *  AudioAssetRepository  換成假的。真的要連資料庫，太慢，而且這裡要測的是
 *                        「有沒有去查」「查到之後做什麼」，不是資料庫本身。
 *  SpeechClient          換成假的。★ 這個最重要 —— 它是真正會花錢的那一個。
 *                        換成假的之後，我們就能用 verify(...) 檢查
 *                        「它到底有沒有被呼叫」，也就是「這次有沒有花錢」。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  測試一  資料庫已經有了 → ★絕對不可以呼叫 SpeechClient★（省錢的命脈）
 *  測試二  資料庫沒有     → 要合成、要寫進資料庫、要回傳網址
 *  測試三  合成失敗       → 回傳空的 Optional，不可以寫進資料庫
 *                          （寫進去的話，那筆假紀錄會永遠擋住之後的重試）
 */

import com.tim.language_project.client.SpeechClient;
import com.tim.language_project.dto.response.AudioAssetDto;
import com.tim.language_project.entity.AudioAsset;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.repository.AudioAssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioAssetServiceTest {

    @Mock
    private AudioAssetRepository audioAssetRepository;

    @Mock
    private SpeechClient speechClient;

    @InjectMocks
    private AudioAssetService audioAssetService;

    /*
     * ★ 這個測試是整個省錢機制的命脈。
     *   最後那一行 verify(..., never()) 才是重點 —— 它在確認「這次沒有花錢」。
     */
    @Test
    @DisplayName("音檔已存在時不得再次合成")
    void shouldNotSynthesizeWhenAudioAlreadyExists() {
        when(audioAssetRepository.findBySpeechTextAndLanguage("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.of(new AudioAssetDto(
                        1L, "เหล้า", SpeechLanguageEnum.TH, "th/a1b2c3.mp3")));

        Optional<String> audioUrl =
                audioAssetService.resolveAudioUrl("เหล้า", SpeechLanguageEnum.TH);

        assertThat(audioUrl).contains("/audio/th/a1b2c3.mp3");

        // ★ 一毛錢都不能花
        verify(speechClient, never()).synthesize(anyString(), any());
        verify(audioAssetRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("音檔不存在時應合成並寫入資料庫")
    void shouldSynthesizeAndPersistWhenAudioMissing() {
        when(audioAssetRepository.findBySpeechTextAndLanguage("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.empty());
        when(speechClient.synthesize("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.of("th/d4e5f6.mp3"));

        Optional<String> audioUrl =
                audioAssetService.resolveAudioUrl("เหล้า", SpeechLanguageEnum.TH);

        assertThat(audioUrl).contains("/audio/th/d4e5f6.mp3");
        verify(audioAssetRepository).saveAndFlush(any(AudioAsset.class));
    }

    /*
     * 合成失敗時如果照樣寫一筆進資料庫，那筆紀錄會永遠命中，
     * 使用者之後再怎麼點都不會重試 —— 這個詞就永遠沒有聲音了。
     */
    @Test
    @DisplayName("合成失敗時不得寫入資料庫")
    void shouldNotPersistWhenSynthesisFails() {
        when(audioAssetRepository.findBySpeechTextAndLanguage("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.empty());
        when(speechClient.synthesize("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.empty());

        Optional<String> audioUrl =
                audioAssetService.resolveAudioUrl("เหล้า", SpeechLanguageEnum.TH);

        assertThat(audioUrl).isEmpty();
        verify(audioAssetRepository, never()).saveAndFlush(any());
    }
}
```

- [ ] **Step 2：跑測試，確認它失敗**

```powershell
.\mvnw.cmd test -Dtest=AudioAssetServiceTest
```

預期：編譯失敗，`AudioAssetService` 不存在。

- [ ] **Step 3：寫出實作**

```java
package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  全站唯一一個可以叫 OpenAI 合成語音的地方。
 *
 *  為什麼要有這一層：合成語音要付錢。如果每個需要聲音的地方都各自去呼叫，
 *  同一個泰文詞會被合成好幾次 —— 你查「酒」合成一次、
 *  查「我想喝酒」逐詞再合成一次、查「他喝酒了」又合成一次。
 *  三個一模一樣的 mp3，付了三次錢。
 *
 *  這個檔案的規則只有一句：★ 同一段文字，全站只合成一次 ★
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從需要一段聲音到拿到網址
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜有人需要「เหล้า」的發音 ────────────────────────────────────
 *
 *    可能是 TranslationService（查詢完成要附上音檔），
 *    也可能是 AudioController（你在畫面上點了逐詞的播放鍵）。
 *    兩邊都呼叫同一個方法：
 *
 *        audioAssetService.resolveAudioUrl("เหล้า", SpeechLanguageEnum.TH);
 *
 * ── 第 2 步｜先查資料庫 ─────────────────────────────────────────────────
 *
 *        audioAssetRepository.findBySpeechTextAndLanguage("เหล้า", TH)
 *
 *    查到了 → 拿出 file_path，例如 "th/a1b2c3.mp3"
 *             加上前綴變成 "/audio/th/a1b2c3.mp3" 回傳
 *             ★ 到此結束，一毛錢都沒花 ★
 *
 *    沒查到 → 往下
 *
 * ── 第 3 步｜真的去合成（這一步會花錢）─────────────────────────────────
 *
 *        speechClient.synthesize("เหล้า", TH)  →  Optional("th/d4e5f6.mp3")
 *
 *    tts-1 按字元計價，「เหล้า」是 5 個字元，約 0.000075 美金。
 *    很便宜，但重複一萬次就不便宜了 —— 那正是第 2 步在擋的事。
 *
 * ── 第 4 步｜把結果記下來，下次就不用再花錢 ─────────────────────────────
 *
 *        audio_asset 新增一筆：("เหล้า", TH, "th/d4e5f6.mp3")
 *
 *    ★ 合成失敗（第 3 步回傳空的）時「絕對不可以」寫入。
 *      寫進去的話，那筆紀錄之後每次都會命中，使用者再怎麼點都不會重試，
 *      這個詞就永遠沒有聲音了。
 *
 * ── 第 5 步｜回傳網址 ───────────────────────────────────────────────────
 *
 *        "/audio/th/d4e5f6.mp3"
 *
 *    前端直接放進 <audio src> 就能播。網址怎麼對應到硬碟上的檔案，
 *    是 WebMvcConfig 在管的，這裡不需要知道。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  ★ 為什麼回傳 Optional 而不是丟例外
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  合成失敗是「這個詞暫時沒有聲音」，不是「這次查詢失敗」。
 *  丟例外的話，語音服務一出問題，整個翻譯功能就跟著不能用了。
 *  回傳空的 Optional，呼叫端就只是少顯示一個播放鍵而已。
 *
 *  測試檔：src/test/java/com/tim/language_project/service/AudioAssetServiceTest.java
 */

import com.tim.language_project.client.SpeechClient;
import com.tim.language_project.dto.response.AudioAssetDto;
import com.tim.language_project.entity.AudioAsset;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.repository.AudioAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Optional;

/**
 * 全站唯一的語音合成入口，先查資料庫再決定要不要花錢。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudioAssetService {

    private static final String AUDIO_URL_PREFIX = "/audio/";

    private final AudioAssetRepository audioAssetRepository;

    private final SpeechClient speechClient;

    /**
     * 取得一段文字的音檔網址。已經合成過就直接回傳舊的，沒有才合成。
     * 合成失敗時回傳空的 Optional，呼叫端當作「這段文字暫時沒有聲音」處理。
     */
    public Optional<String> resolveAudioUrl(String speechText, SpeechLanguageEnum language) {
        if (ObjectUtils.isEmpty(speechText)) {
            return Optional.empty();
        }

        Optional<AudioAssetDto> existing =
                audioAssetRepository.findBySpeechTextAndLanguage(speechText, language);

        if (existing.isPresent()) {
            return Optional.of(toAudioUrl(existing.get().filePath()));
        }

        Optional<String> synthesizedPath = speechClient.synthesize(speechText, language);

        if (synthesizedPath.isEmpty()) {
            // 合成失敗。刻意不寫入資料庫 —— 寫了之後這段文字會永遠命中那筆假紀錄，
            // 使用者再怎麼點都不會重試。
            return Optional.empty();
        }

        persist(speechText, language, synthesizedPath.get());

        return Optional.of(toAudioUrl(synthesizedPath.get()));
    }

    /**
     * 寫入音檔紀錄。撞到唯一鍵代表另一個請求在我們合成的這幾秒內先寫進去了，
     * 這不是錯誤，忽略即可 —— 我們手上這個檔案照樣能播，只是多存了一份在硬碟上。
     */
    private void persist(String speechText, SpeechLanguageEnum language, String filePath) {
        AudioAsset audioAsset = new AudioAsset();
        audioAsset.setSpeechText(speechText);
        audioAsset.setLanguage(language);
        audioAsset.setFilePath(filePath);

        try {
            audioAssetRepository.saveAndFlush(audioAsset);
        } catch (DataIntegrityViolationException exception) {
            log.warn("concurrent audio synthesis detected, keeping the file just created");
        }
    }

    private String toAudioUrl(String filePath) {
        return AUDIO_URL_PREFIX + filePath;
    }
}
```

- [ ] **Step 4：跑測試，確認三個都通過**

```powershell
.\mvnw.cmd test -Dtest=AudioAssetServiceTest
```

預期：`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5：回報並徵求同意後 commit**

```
新增音檔資產服務

Feat:
- 新增 AudioAssetService，合成前先查資料庫，同一段文字全站只合成一次
- 合成失敗不寫入資料庫，保留日後重試的機會
```

---

## Task 7：合成音檔的 API

**Files:**
- Create: `src/main/java/com/tim/language_project/service/SpeechTextGuard.java`
- Create: `src/main/java/com/tim/language_project/dto/request/AudioRequestDto.java`
- Create: `src/main/java/com/tim/language_project/dto/response/AudioResponseDto.java`
- Create: `src/main/java/com/tim/language_project/controller/AudioController.java`
- Modify: `src/main/java/com/tim/language_project/repository/TranslationQueryRepository.java`
- Modify: `src/main/java/com/tim/language_project/repository/TranslationSegmentRepository.java`
- Modify: `src/main/java/com/tim/language_project/repository/VocabularyRepository.java`
- Test: `src/test/java/com/tim/language_project/controller/AudioControllerTest.java`

- [ ] **Step 1：先寫會失敗的測試**

```java
package com.tim.language_project.controller;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  POST /api/v1/audio 是「你在畫面上點了逐詞的播放鍵」時打的那支 API。
 *
 *  ★ 這支 API 會花錢。★
 *
 *  這是它跟其他 API 最大的不同 —— 別的端點打錯了只是拿到錯誤訊息，
 *  這支打進來就是一次 OpenAI 的付費呼叫。
 *
 *  所以它有一道守門的檢查：只准合成「我們系統自己產生過的文字」。
 *  沒有這道檢查的話，任何人寫個迴圈送隨機字串進來，
 *  就能把帳戶餘額燒光，而且每一筆看起來都是正常請求。
 *
 * ── 哪些東西被換成假的 ──────────────────────────────────────────────────
 *
 *  @WebMvcTest 只啟動「網頁」那一層（Controller、例外處理器），
 *  不啟動 Service 和資料庫。所以：
 *
 *      SpeechTextGuard    換成假的 —— 我們自己指定「這段文字算不算已知」
 *      AudioAssetService  換成假的 —— 它會花錢，絕對不能讓它真的跑
 *
 *  MockMvc 是「假的瀏覽器」，可以送出 HTTP 請求而不用真的開伺服器。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  測試一  已知的文字 → 回 200，帶著音檔網址
 *  測試二  ★未知的文字 → 回 400，而且絕對不可以呼叫 AudioAssetService★
 *          （這一題就是防止帳戶被燒的那道關卡，壞了不會有任何徵兆）
 *  測試三  合成失敗   → 回 404，不要假裝成功給前端一個壞掉的網址
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tim.language_project.dto.request.AudioRequestDto;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.service.AudioAssetService;
import com.tim.language_project.service.SpeechTextGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AudioController.class)
class AudioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SpeechTextGuard speechTextGuard;

    @MockitoBean
    private AudioAssetService audioAssetService;

    @Test
    @DisplayName("已知的文字應回傳音檔網址")
    void shouldReturnAudioUrlForKnownText() throws Exception {
        when(speechTextGuard.isKnown("เหล้า", SpeechLanguageEnum.TH)).thenReturn(true);
        when(audioAssetService.resolveAudioUrl("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.of("/audio/th/a1b2c3.mp3"));

        mockMvc.perform(post("/api/v1/audio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AudioRequestDto("เหล้า", SpeechLanguageEnum.TH))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audioUrl").value("/audio/th/a1b2c3.mp3"));
    }

    /*
     * ★ 這個測試守著帳戶餘額。
     *   最後那一行 verify(..., never()) 是重點：不只要回 400，
     *   更重要的是「那次付費呼叫根本沒有發生」。
     */
    @Test
    @DisplayName("未知的文字應被擋下且不得呼叫語音服務")
    void shouldRejectUnknownTextWithoutSynthesizing() throws Exception {
        when(speechTextGuard.isKnown("任意輸入的字", SpeechLanguageEnum.ZH)).thenReturn(false);

        mockMvc.perform(post("/api/v1/audio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AudioRequestDto("任意輸入的字", SpeechLanguageEnum.ZH))))
                .andExpect(status().isBadRequest());

        // ★ 一毛錢都不能花
        verify(audioAssetService, never()).resolveAudioUrl(anyString(), any());
    }

    @Test
    @DisplayName("合成失敗時應回傳 404 而非假裝成功")
    void shouldReturnNotFoundWhenSynthesisFails() throws Exception {
        when(speechTextGuard.isKnown("เหล้า", SpeechLanguageEnum.TH)).thenReturn(true);
        when(audioAssetService.resolveAudioUrl("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/audio")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AudioRequestDto("เหล้า", SpeechLanguageEnum.TH))))
                .andExpect(status().isNotFound());
    }
}
```

> 若 `@WebMvcTest` 或 `@MockitoBean` 的 import 路徑與專案既有的 `TranslationControllerTest` 不同，以既有檔案為準（Spring Boot 4.1 的測試切片套件路徑與 3.x 不同）。

- [ ] **Step 2：跑測試，確認它失敗**

```powershell
.\mvnw.cmd test -Dtest=AudioControllerTest
```

預期：編譯失敗，相關類別都不存在。

- [ ] **Step 3：三個 Repository 各補兩個查詢方法**

`TranslationQueryRepository`、`TranslationSegmentRepository`、`VocabularyRepository` 各加入：

```java
    boolean existsByThaiText(String thaiText);

    boolean existsByChineseText(String chineseText);
```

這兩個是 Spring Data 依方法名稱自動產生的查詢，不需要寫 `@Query`。

- [ ] **Step 4：建立 `SpeechTextGuard`**

```java
package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  回答一個問題：「這段文字，是我們系統自己產生過的嗎？」
 *
 *  為什麼需要它：合成語音的那支 API 會花錢。
 *  沒有這道檢查，任何人寫三行程式送隨機字串進來，就能把 OpenAI 的餘額燒光，
 *  而且伺服器日誌上每一筆都長得像正常請求。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你點下播放鍵到通過檢查
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在逐詞對照看到「เหล้า」旁邊有個灰色的播放鍵，點下去 ──────
 *
 *        POST /api/v1/audio  { "speechText": "เหล้า", "language": "TH" }
 *
 * ── 第 2 步｜AudioController 先問這裡 ───────────────────────────────────
 *
 *        speechTextGuard.isKnown("เหล้า", SpeechLanguageEnum.TH)
 *
 * ── 第 3 步｜依語言決定要比對哪一欄，去三張表找 ─────────────────────────
 *
 *    TH → 比對三張表的 thai_text 欄位
 *    ZH → 比對三張表的 chinese_text 欄位
 *
 *        translation_segment  逐詞拆解的結果（最常命中的就是這張）
 *        translation_query    整句翻譯的結果
 *        vocabulary           單字庫
 *
 *    只要任何一張找得到，就是「我們產生過的」，回 true。
 *
 *    ★ 用短路運算（||）串起來，找到就不再查後面兩張，
 *      所以最常命中的 translation_segment 放第一個。
 *
 * ── 第 4 步｜回傳結果 ───────────────────────────────────────────────────
 *
 *    true  → AudioController 繼續，交給 AudioAssetService
 *    false → 丟 SPEECH_TEXT_UNKNOWN，回 400，★沒有花任何錢★
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  ★ 這不是效能考量，是安全考量
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  看起來像是「先查一下比較快」，其實不是 —— 這三次查詢反而讓請求變慢。
 *  它存在的唯一理由是擋住花錢。不要因為「想讓 API 快一點」就把它拿掉。
 *
 *  測試檔：合成 API 的守門行為在
 *          src/test/java/com/tim/language_project/controller/AudioControllerTest.java
 */

import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.repository.TranslationQueryRepository;
import com.tim.language_project.repository.TranslationSegmentRepository;
import com.tim.language_project.repository.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.Objects;

/**
 * 判斷一段文字是否為系統產生過的內容，用來擋掉會花錢的任意合成請求。
 */
@Component
@RequiredArgsConstructor
public class SpeechTextGuard {

    private final TranslationSegmentRepository translationSegmentRepository;

    private final TranslationQueryRepository translationQueryRepository;

    private final VocabularyRepository vocabularyRepository;

    /**
     * 這段文字是否出現在逐詞、查詢快取或單字庫裡。
     */
    public boolean isKnown(String speechText, SpeechLanguageEnum language) {
        if (ObjectUtils.isEmpty(speechText) || Objects.isNull(language)) {
            return false;
        }

        if (Objects.equals(language, SpeechLanguageEnum.TH)) {
            return translationSegmentRepository.existsByThaiText(speechText)
                    || translationQueryRepository.existsByThaiText(speechText)
                    || vocabularyRepository.existsByThaiText(speechText);
        }

        return translationSegmentRepository.existsByChineseText(speechText)
                || translationQueryRepository.existsByChineseText(speechText)
                || vocabularyRepository.existsByChineseText(speechText);
    }
}
```

- [ ] **Step 5：建立兩個 DTO**

```java
package com.tim.language_project.dto.request;

import com.tim.language_project.enums.SpeechLanguageEnum;

/**
 * 合成音檔的請求：{ "speechText": "เหล้า", "language": "TH" }。
 */
public record AudioRequestDto(String speechText, SpeechLanguageEnum language) {
}
```

```java
package com.tim.language_project.dto.response;

/**
 * 合成音檔的回應，網址可直接放進前端的 audio 標籤。
 */
public record AudioResponseDto(String audioUrl) {
}
```

- [ ] **Step 6：建立 `AudioController`**

```java
package com.tim.language_project.controller;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  逐詞對照裡的播放鍵，點下去打的就是這支 API。
 *
 *  為什麼逐詞的音檔不在查詢時就先做好：一句話拆成 4、5 個詞，
 *  每個詞各合成一次要多打 4、5 次 OpenAI，每次 1 到 2 秒。
 *  這樣每次查句子都要多等好幾秒，而那些詞你未必想聽。
 *  改成「想聽哪個就點哪個」，第一次點等一兩秒，之後永久免費。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你點下播放鍵到聽見聲音
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你查了「我想喝酒」，逐詞區出現四個詞 ───────────────────────
 *
 *        我    ฉัน      chǎn      🔊（灰色，還沒有音檔）
 *        想    อยาก     yàak      🔊（灰色）
 *        喝    ดื่ม      dùuem     🔊（灰色）
 *        酒    เหล้า     lâo       🔊（亮的，你以前查過「酒」，現成的）
 *
 * ── 第 2 步｜你點了「喝」旁邊那個灰色的鍵 ───────────────────────────────
 *
 *        POST /api/v1/audio
 *        { "speechText": "ดื่ม", "language": "TH" }
 *
 * ── 第 3 步｜先過守門檢查 ───────────────────────────────────────────────
 *
 *        speechTextGuard.isKnown("ดื่ม", TH)
 *
 *        false → 丟 SPEECH_TEXT_UNKNOWN，回 400，★不花錢★
 *                （這道關卡防的是有人拿這支 API 燒我們的餘額）
 *        true  → 往下
 *
 * ── 第 4 步｜交給 AudioAssetService ─────────────────────────────────────
 *
 *        audioAssetService.resolveAudioUrl("ดื่ม", TH)
 *
 *        它會先查資料庫，沒有才真的合成。合成完會寫進 audio_asset，
 *        所以這個詞之後在「任何句子裡」出現都直接是亮的。
 *
 * ── 第 5 步｜回應 ───────────────────────────────────────────────────────
 *
 *        有拿到 → 200 { "audioUrl": "/audio/th/d4e5f6.mp3" }
 *        沒拿到 → 404 AUDIO_FILE_NOT_FOUND
 *
 *        ★ 為什麼失敗要回 404 而不是硬給一個網址？
 *          給了網址前端會顯示成「可以播」，使用者點下去卻沒聲音，
 *          會以為是自己的喇叭壞了。誠實回報找不到，前端才能維持灰色。
 *
 * ── 第 6 步｜前端把該行的播放鍵換成亮的並播放 ───────────────────────────
 *
 *  測試檔：src/test/java/com/tim/language_project/controller/AudioControllerTest.java
 */

import com.tim.language_project.dto.request.AudioRequestDto;
import com.tim.language_project.dto.response.AudioResponseDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.service.AudioAssetService;
import com.tim.language_project.service.SpeechTextGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 逐詞音檔的合成端點。
 * 使用 POST 是因為這個呼叫會產生檔案與資料，而且可能花錢。
 */
@RestController
@RequestMapping("/api/v1/audio")
@RequiredArgsConstructor
public class AudioController {

    private final SpeechTextGuard speechTextGuard;

    private final AudioAssetService audioAssetService;

    @PostMapping
    public ResponseEntity<AudioResponseDto> synthesize(@RequestBody AudioRequestDto request) {
        // ★ 這一行是防止帳戶被燒的關卡，不要為了「讓 API 快一點」把它拿掉。
        if (!speechTextGuard.isKnown(request.speechText(), request.language())) {
            throw new BusinessException(ErrorCodeEnum.SPEECH_TEXT_UNKNOWN);
        }

        Optional<String> audioUrl =
                audioAssetService.resolveAudioUrl(request.speechText(), request.language());

        return audioUrl
                .map(url -> ResponseEntity.ok(new AudioResponseDto(url)))
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.AUDIO_FILE_NOT_FOUND));
    }
}
```

- [ ] **Step 7：跑測試，確認三個都通過**

```powershell
.\mvnw.cmd test -Dtest=AudioControllerTest
```

預期：`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 8：回報並徵求同意後 commit**

```
新增逐詞音檔合成端點

Feat:
- 新增 POST /api/v1/audio，點擊時才合成逐詞音檔
- 新增 SpeechTextGuard，只允許合成系統產生過的文字，避免任意請求耗用額度
```

---

## Task 8：TranslationQuery 支援方向與性別

**Files:**
- Modify: `src/main/java/com/tim/language_project/entity/TranslationQuery.java`
- Modify: `src/main/java/com/tim/language_project/dto/response/TranslationQueryDto.java`
- Modify: `src/main/java/com/tim/language_project/repository/TranslationQueryRepository.java`
- Modify: `src/test/java/com/tim/language_project/repository/TranslationQueryRepositoryTest.java`

- [ ] **Step 1：在測試檔補上會失敗的測試**

既有測試裡所有建立 `TranslationQuery` 的地方都要補上新欄位。新增這三個測試：

```java
    /*
     * ★ 決策 7 的第一半：同一句話的男版與女版，泰文真的不一樣，必須各存一筆。
     *   這條唯一鍵如果只用 source_text，男版會把女版蓋掉（或反過來），
     *   使用者切換性別後看到的是另一個性別的講法。
     */
    @Test
    @DisplayName("同一句話的男版與女版可各存一筆")
    void shouldAllowSameSourceTextWithDifferentGender() {
        translationQueryRepository.saveAndFlush(newQuery(
                "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                "我想喝酒", "ผมอยากดื่มเหล้าครับ", "pǒm yàak dùuem lâo khráp"));

        translationQueryRepository.saveAndFlush(newQuery(
                "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.FEMALE,
                "我想喝酒", "ฉันอยากดื่มเหล้าค่ะ", "chǎn yàak dùuem lâo khâ"));

        assertThat(translationQueryRepository.findByKey(
                "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .isPresent();
        assertThat(translationQueryRepository.findByKey(
                "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.FEMALE))
                .isPresent();
    }

    @Test
    @DisplayName("同一句話同一性別不可重複寫入")
    void shouldRejectDuplicateKey() {
        translationQueryRepository.saveAndFlush(newQuery(
                "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                "我想喝酒", "ผมอยากดื่มเหล้าครับ", "pǒm yàak dùuem lâo khráp"));

        assertThatThrownBy(() -> translationQueryRepository.saveAndFlush(newQuery(
                "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                "我想喝酒", "ผมอยากทานเหล้าครับ", "pǒm yàak thaan lâo khráp")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /*
     * ★ 泰翻中的 gender 是 null。SQL Server 的 UNIQUE 把 null 當成一個值來比對，
     *   所以「同一句泰文只會有一筆」這件事仍然成立。
     *   這個測試就是在確認那個行為真的如我們所想。
     */
    @Test
    @DisplayName("泰翻中的性別為 null 時仍然唯一")
    void shouldEnforceUniquenessWhenGenderIsNull() {
        translationQueryRepository.saveAndFlush(newQuery(
                "ผมอยากดื่มเหล้า", TranslationDirectionEnum.TH_TO_ZH, null,
                "我想喝酒", "ผมอยากดื่มเหล้า", "pǒm yàak dùuem lâo"));

        assertThatThrownBy(() -> translationQueryRepository.saveAndFlush(newQuery(
                "ผมอยากดื่มเหล้า", TranslationDirectionEnum.TH_TO_ZH, null,
                "我要喝酒", "ผมอยากดื่มเหล้า", "pǒm yàak dùuem lâo")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private TranslationQuery newQuery(String sourceText,
                                      TranslationDirectionEnum direction,
                                      SpeakerGenderEnum gender,
                                      String chineseText,
                                      String thaiText,
                                      String romanization) {
        TranslationQuery query = new TranslationQuery();
        query.setSourceText(sourceText);
        query.setDirection(direction);
        query.setGender(gender);
        query.setChineseText(chineseText);
        query.setThaiText(thaiText);
        query.setRomanization(romanization);

        return query;
    }
```

需要新增的 import：

```java
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 2：跑測試，確認它失敗**

```powershell
.\mvnw.cmd test -Dtest=TranslationQueryRepositoryTest
```

預期：編譯失敗，`setDirection` 等方法不存在。

- [ ] **Step 3：改 `TranslationQuery` 實體**

把 `audioFile` 欄位整段刪掉，加入三個新欄位，並更新類別的 Javadoc：

```java
/**
 * 翻譯結果的快取。查詢的鍵是「輸入原文＋方向＋性別」三者的組合。
 * 音檔不在這裡 —— 全站的音檔一律由 audio_asset 持有。
 */
```

欄位部分：

```java
    /** 使用者輸入的原文，寫入前會先去掉頭尾空白。 */
    @Column(name = "source_text", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String sourceText;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 20, nullable = false)
    private TranslationDirectionEnum direction;

    /** 泰翻中沒有性別概念，該方向為 null。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private SpeakerGenderEnum gender;

    /** 這句話的中文面。sourceText 必定與這一欄或 thaiText 其中之一相同。 */
    @Column(name = "chinese_text", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String chineseText;

    @Column(name = "thai_text", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String thaiText;

    @Column(name = "romanization", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String romanization;
```

新增 import：`jakarta.persistence.EnumType`、`jakarta.persistence.Enumerated`、兩個 Enum。

- [ ] **Step 4：改 `TranslationQueryDto`**

```java
package com.tim.language_project.dto.response;

import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;

/**
 * 查詢快取的投影，也就是 JPQL 建構子表達式要組出來的型別。
 */
public record TranslationQueryDto(
        Long id,
        String sourceText,
        TranslationDirectionEnum direction,
        SpeakerGenderEnum gender,
        String chineseText,
        String thaiText,
        String romanization) {
}
```

- [ ] **Step 5：改 `TranslationQueryRepository`**

把 `findBySourceText` 換成 `findByKey`，並保留 Task 7 加的兩個 `existsBy` 方法：

```java
    /*
     * ★ gender 可能是 null（泰翻中）。JPQL 的 = 比不到 null，
     *   所以要寫成「(:gender IS NULL AND ... IS NULL) OR ... = :gender」的形式。
     *   直接寫 gender = :gender 的話，泰翻中的快取永遠不會命中，
     *   每次查同一句泰文都會重新付費 —— 而且不會有任何錯誤訊息。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationQueryDto(
                translationQuery.id,
                translationQuery.sourceText,
                translationQuery.direction,
                translationQuery.gender,
                translationQuery.chineseText,
                translationQuery.thaiText,
                translationQuery.romanization
            )

            FROM TranslationQuery translationQuery

            WHERE translationQuery.sourceText = :sourceText
              AND translationQuery.direction = :direction
              AND ((:gender IS NULL AND translationQuery.gender IS NULL)
                   OR translationQuery.gender = :gender)
            """)
    Optional<TranslationQueryDto> findByKey(
            @Param("sourceText") String sourceText,
            @Param("direction") TranslationDirectionEnum direction,
            @Param("gender") SpeakerGenderEnum gender);
```

- [ ] **Step 6：跑測試，確認通過**

```powershell
.\mvnw.cmd test -Dtest=TranslationQueryRepositoryTest
```

預期：全部 PASS。`TranslationService` 此時仍會編譯失敗，Task 13 會修好。

- [ ] **Step 7：回報並徵求同意後 commit**

```
查詢快取支援翻譯方向與說話者性別

Modify:
- TranslationQuery 新增 direction、gender、chineseText，移除 audioFile
- 查詢改以輸入原文、方向、性別三者為鍵
```

---

## Task 9：Vocabulary 支援多重說法

**Files:**
- Modify: `src/main/java/com/tim/language_project/entity/Vocabulary.java`
- Modify: `src/main/java/com/tim/language_project/dto/response/VocabularyDto.java`
- Modify: `src/main/java/com/tim/language_project/repository/VocabularyRepository.java`
- Modify: `src/test/java/com/tim/language_project/repository/VocabularyRepositoryTest.java`

- [ ] **Step 1：在測試檔補上會失敗的測試**

```java
    /*
     * ★ 這是整個「多重說法」功能的地基。
     *   舊的唯一鍵是「一個中文詞只能一列」，那條規則存在的話，
     *   「我」的第二種說法根本寫不進去。
     */
    @Test
    @DisplayName("同一個中文詞可以有多個泰文說法")
    void shouldAllowMultipleThaiVariantsForSameChineseText() {
        vocabularyRepository.saveAndFlush(newVocabulary(
                "我", "ผม", "pǒm", GenderUsageEnum.MALE, PolitenessEnum.FORMAL,
                "男生自稱，正式或對不熟的人使用"));
        vocabularyRepository.saveAndFlush(newVocabulary(
                "我", "ฉัน", "chǎn", GenderUsageEnum.FEMALE, PolitenessEnum.FORMAL,
                "女生自稱"));
        vocabularyRepository.saveAndFlush(newVocabulary(
                "我", "กู", "guu", GenderUsageEnum.BOTH, PolitenessEnum.RUDE,
                "很不客氣，只能對很熟的朋友使用"));

        List<VocabularyDto> variants = vocabularyRepository.findByChineseText("我");

        assertThat(variants).hasSize(3);
        assertThat(variants).extracting(VocabularyDto::thaiText)
                .containsExactlyInAnyOrder("ผม", "ฉัน", "กู");
    }

    @Test
    @DisplayName("同一組中文與泰文不可重複寫入")
    void shouldRejectDuplicateChineseAndThai() {
        vocabularyRepository.saveAndFlush(newVocabulary(
                "酒", "เหล้า", "lâo", null, null, null));

        assertThatThrownBy(() -> vocabularyRepository.saveAndFlush(newVocabulary(
                "酒", "เหล้า", "lao", null, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /*
     * 從句子拆解沉澱下來的詞沒有性別與禮貌資訊，那三欄必須允許 null。
     * 不允許的話，翻一句話就會整個寫入失敗。
     */
    @Test
    @DisplayName("性別、禮貌、說明三欄可以是 null")
    void shouldAllowNullLabels() {
        vocabularyRepository.saveAndFlush(newVocabulary(
                "水", "น้ำ", "náam", null, null, null));

        List<VocabularyDto> found = vocabularyRepository.findByChineseText("水");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).genderUsage()).isNull();
        assertThat(found.get(0).politeness()).isNull();
        assertThat(found.get(0).note()).isNull();
    }

    private Vocabulary newVocabulary(String chineseText,
                                     String thaiText,
                                     String romanization,
                                     GenderUsageEnum genderUsage,
                                     PolitenessEnum politeness,
                                     String note) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setChineseText(chineseText);
        vocabulary.setThaiText(thaiText);
        vocabulary.setRomanization(romanization);
        vocabulary.setGenderUsage(genderUsage);
        vocabulary.setPoliteness(politeness);
        vocabulary.setNote(note);
        vocabulary.setSourceType(VocabularySourceTypeEnum.DIRECT);

        return vocabulary;
    }
```

- [ ] **Step 2：跑測試，確認它失敗**

```powershell
.\mvnw.cmd test -Dtest=VocabularyRepositoryTest
```

- [ ] **Step 3：改 `Vocabulary` 實體**

更新類別 Javadoc：

```java
/**
 * 中泰對照的一個「說法」。同一個中文詞可以有多列 ——
 * 例如「我」會有 ผม、ฉัน、กู 三列，這是預期行為不是資料重複。
 * 這裡不存音檔，全站的音檔一律由 audio_asset 持有。
 */
```

在 `romanization` 之後加入三個欄位：

```java
    /**
     * 這個說法適合哪種性別使用。
     * 從句子拆解沉澱下來的詞沒有這項資訊，為 null，
     * 日後單獨查詢該詞時才會補上。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender_usage", length = 10)
    private GenderUsageEnum genderUsage;

    /** 禮貌程度。同樣可能為 null。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "politeness", length = 10)
    private PolitenessEnum politeness;

    /** 中文說明，例如「男生自稱，正式或對不熟的人使用」。 */
    @Column(name = "note", columnDefinition = "NVARCHAR(200)")
    private String note;
```

- [ ] **Step 4：改 `VocabularyDto`**

```java
package com.tim.language_project.dto.response;

import com.tim.language_project.enums.GenderUsageEnum;
import com.tim.language_project.enums.PolitenessEnum;

/**
 * 單字庫裡的一個說法。
 */
public record VocabularyDto(
        Long id,
        String chineseText,
        String thaiText,
        String romanization,
        GenderUsageEnum genderUsage,
        PolitenessEnum politeness,
        String note) {
}
```

- [ ] **Step 5：改 `VocabularyRepository`**

`findByChineseText` 改回傳 `List`，`findExistingChineseTexts` 改成用中泰組合判斷，兩個查詢的建構子表達式都要補上新欄位：

```java
    @Query("""
            SELECT new com.tim.language_project.dto.response.VocabularyDto(
                vocabulary.id,
                vocabulary.chineseText,
                vocabulary.thaiText,
                vocabulary.romanization,
                vocabulary.genderUsage,
                vocabulary.politeness,
                vocabulary.note
            )

            FROM Vocabulary vocabulary

            WHERE vocabulary.chineseText = :chineseText

            ORDER BY vocabulary.id
            """)
    List<VocabularyDto> findByChineseText(@Param("chineseText") String chineseText);

    /*
     * ★ 改成撈出「整個實體」而不是只撈中文字。
     *   因為寫入時要判斷的不只是「這個說法在不在」，
     *   還要在它已經存在、但性別／禮貌／說明是 null 時把那三欄補上
     *   （合併規則見 TranslationPersistenceService）。只撈字串就做不到這件事。
     */
    @Query("""
            SELECT vocabulary

            FROM Vocabulary vocabulary

            WHERE vocabulary.chineseText IN :chineseTexts
            """)
    List<Vocabulary> findAllByChineseTextIn(
            @Param("chineseTexts") Collection<String> chineseTexts);
```

`findAllOrderByIdDesc` 的建構子表達式也要補上三個新欄位。舊的 `findExistingChineseTexts` 刪除。

- [ ] **Step 6：跑測試，確認通過**

```powershell
.\mvnw.cmd test -Dtest=VocabularyRepositoryTest
```

- [ ] **Step 7：回報並徵求同意後 commit**

```
單字庫支援同一個詞的多種說法

Modify:
- vocabulary 唯一鍵改為中文加泰文，新增性別、禮貌、說明三個欄位
- findByChineseText 改回傳多筆
```

---

## Task 10：翻譯結果的模型與介面

**Files:**
- Create: `src/main/java/com/tim/language_project/client/model/TranslationVariant.java`
- Modify: `src/main/java/com/tim/language_project/client/model/TranslationResult.java`
- Modify: `src/main/java/com/tim/language_project/client/TranslationClient.java`

這一批是純宣告，沒有邏輯可測，Task 11 的測試會全面用到它們。

- [ ] **Step 1：建立 `TranslationVariant`**

```java
package com.tim.language_project.client.model;

import com.tim.language_project.enums.GenderUsageEnum;
import com.tim.language_project.enums.PolitenessEnum;

/**
 * 一個中文詞在泰文的其中一種說法，例如「我」對應的 ผม。
 * 只有查單一個詞時才會有內容，查句子時是空的。
 */
public record TranslationVariant(
        String thaiText,
        String romanization,
        GenderUsageEnum genderUsage,
        PolitenessEnum politeness,
        String note) {
}
```

- [ ] **Step 2：改 `TranslationResult`**

```java
package com.tim.language_project.client.model;

import java.util.List;

/**
 * 一次輸入的翻譯結果。
 * 中文面與泰文面都帶回來，呼叫端不需要判斷方向就知道哪個是哪個。
 * variants 只有在「輸入本身就是一個詞」時才有內容，其餘情況是空清單。
 * token 用量一併帶回來，讓呼叫端可以記錄費用。
 */
public record TranslationResult(
        String chineseText,
        String thaiText,
        String romanization,
        List<TranslationWord> words,
        List<TranslationVariant> variants,
        String modelName,
        long inputTokens,
        long outputTokens,
        boolean translatable) {

    /**
     * 「這段輸入根本翻不出來」的結果，例如亂碼、無意義的字。
     * 由模型自己判斷並回報，因為我們沒有字典可以比對，判斷權本來就只在它手上。
     * 用量仍要帶進來 —— 那次呼叫確實發生過、也確實被收費了。
     */
    public static TranslationResult untranslatable(String modelName,
                                                   long inputTokens,
                                                   long outputTokens) {
        return new TranslationResult(null, null, null, List.of(), List.of(),
                modelName, inputTokens, outputTokens, false);
    }
}
```

- [ ] **Step 3：改 `TranslationClient` 介面**

```java
package com.tim.language_project.client;

import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;

/**
 * 中泰互譯，附帶羅馬拼音、逐詞拆解，以及單字的多種說法。
 * 抽成介面是為了隔離服務商，日後要換掉 OpenAI 只需新增一個實作。
 * 實作類別要自己負責記錄用量。
 */
public interface TranslationClient {

    /**
     * @param gender 說話者性別，影響泰文造句的自稱與句尾助詞。
     *               泰翻中沒有性別概念，該方向傳 null。
     */
    TranslationResult translate(String sourceText,
                                TranslationDirectionEnum direction,
                                SpeakerGenderEnum gender);
}
```

- [ ] **Step 4：確認編譯狀態**

```powershell
.\mvnw.cmd clean compile
```

預期：`OpenAiTranslationClient` 與 `TranslationService` 會編譯失敗，這是預期的，Task 11 與 13 會修好。**這一步不 commit**，跟 Task 11 一起。

---

## Task 11：翻譯客戶端支援雙向與多重說法

**Files:**
- Modify: `src/main/java/com/tim/language_project/client/openai/OpenAiTranslationClient.java`
- Modify: `src/test/java/com/tim/language_project/client/openai/OpenAiTranslationClientTest.java`

- [ ] **Step 1：在測試檔補上會失敗的測試**

既有測試的 `translate("...")` 呼叫全部改成三個參數版本。新增：

```java
    /*
     * ═══ 多重說法要正確解析 ═══════════════════════════════════════════
     *
     * 這是整個改版的重點功能。模型回來的 variants 陣列要原樣變成物件，
     * 包含性別與禮貌兩個標籤 —— 前端的排序和警示色都靠它們。
     */
    @Test
    @DisplayName("單字查詢應解析出多個說法")
    void shouldParseVariantsForSingleWord() {
        givenChatResponse("""
                {
                  "chineseText": "我",
                  "thaiText": "ผม",
                  "romanization": "pǒm",
                  "words": [ { "chineseText": "我", "thaiText": "ผม", "romanization": "pǒm" } ],
                  "variants": [
                    { "thaiText": "ผม", "romanization": "pǒm",
                      "genderUsage": "MALE", "politeness": "FORMAL", "note": "男生自稱" },
                    { "thaiText": "ฉัน", "romanization": "chǎn",
                      "genderUsage": "FEMALE", "politeness": "FORMAL", "note": "女生自稱" },
                    { "thaiText": "กู", "romanization": "guu",
                      "genderUsage": "BOTH", "politeness": "RUDE", "note": "很不客氣" }
                  ],
                  "translatable": true
                }
                """);

        TranslationResult result = openAiTranslationClient.translate(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE);

        assertThat(result.variants()).hasSize(3);
        assertThat(result.variants().get(2).genderUsage()).isEqualTo(GenderUsageEnum.BOTH);
        assertThat(result.variants().get(2).politeness()).isEqualTo(PolitenessEnum.RUDE);
    }

    /*
     * ★ 模型有「盡量湊到你期待的數量」的本性。
     *   最常見的湊法就是把同一個泰文換個拼音寫法再交一次。
     *   不去重的話，畫面上會出現兩個看起來一樣的說法，
     *   使用者以為是兩個不同的詞，而且資料庫寫入會撞唯一鍵。
     */
    @Test
    @DisplayName("泰文重複的說法應去重")
    void shouldDeduplicateVariantsWithSameThaiText() {
        givenChatResponse("""
                {
                  "chineseText": "我",
                  "thaiText": "ผม",
                  "romanization": "pǒm",
                  "words": [ { "chineseText": "我", "thaiText": "ผม", "romanization": "pǒm" } ],
                  "variants": [
                    { "thaiText": "ผม", "romanization": "pǒm",
                      "genderUsage": "MALE", "politeness": "FORMAL", "note": "男生自稱" },
                    { "thaiText": "ผม", "romanization": "phom",
                      "genderUsage": "MALE", "politeness": "NEUTRAL", "note": "另一種拼法" }
                  ],
                  "translatable": true
                }
                """);

        TranslationResult result = openAiTranslationClient.translate(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE);

        assertThat(result.variants()).hasSize(1);
        assertThat(result.variants().get(0).romanization()).isEqualTo("pǒm");
    }

    @Test
    @DisplayName("說法超過五個時只保留前五個")
    void shouldCapVariantsAtFive() {
        givenChatResponse(sixVariantsJson());

        TranslationResult result = openAiTranslationClient.translate(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE);

        assertThat(result.variants()).hasSize(5);
    }

    /*
     * ★ 現有的中文字檢查只看 thaiText 和 words。
     *   variants 是這次新開的路徑，忘了擴充的話，
     *   「ผ我ม」這種半成品會從新路徑漏進單字庫，而且會被使用者背起來。
     */
    @Test
    @DisplayName("說法的泰文混有中文字時應整筆拒絕")
    void shouldRejectWhenVariantContainsChinese() {
        givenChatResponse("""
                {
                  "chineseText": "我",
                  "thaiText": "ผม",
                  "romanization": "pǒm",
                  "words": [ { "chineseText": "我", "thaiText": "ผม", "romanization": "pǒm" } ],
                  "variants": [
                    { "thaiText": "ผ我ม", "romanization": "pǒm",
                      "genderUsage": "MALE", "politeness": "FORMAL", "note": "壞掉的資料" }
                  ],
                  "translatable": true
                }
                """);

        assertThatThrownBy(() -> openAiTranslationClient.translate(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode",
                        ErrorCodeEnum.TRANSLATION_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("欄位殘缺的說法應被丟棄，其餘保留")
    void shouldDropIncompleteVariants() {
        givenChatResponse("""
                {
                  "chineseText": "我",
                  "thaiText": "ผม",
                  "romanization": "pǒm",
                  "words": [ { "chineseText": "我", "thaiText": "ผม", "romanization": "pǒm" } ],
                  "variants": [
                    { "thaiText": "ผม", "romanization": "pǒm",
                      "genderUsage": "MALE", "politeness": "FORMAL", "note": "男生自稱" },
                    { "thaiText": "", "romanization": "chǎn",
                      "genderUsage": "FEMALE", "politeness": "FORMAL", "note": "泰文是空的" }
                  ],
                  "translatable": true
                }
                """);

        TranslationResult result = openAiTranslationClient.translate(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE);

        assertThat(result.variants()).hasSize(1);
    }

    /*
     * 泰翻中不需要 variants，而且 chineseText 要是翻譯結果、
     * thaiText 要是使用者的輸入。方向弄反的話，畫面會顯示「泰文翻泰文」。
     */
    @Test
    @DisplayName("泰翻中應回傳中文結果且不含說法")
    void shouldTranslateThaiToChinese() {
        givenChatResponse("""
                {
                  "chineseText": "我想喝酒",
                  "thaiText": "ผมอยากดื่มเหล้าครับ",
                  "romanization": "pǒm yàak dùuem lâo khráp",
                  "words": [
                    { "chineseText": "我", "thaiText": "ผม", "romanization": "pǒm" },
                    { "chineseText": "（男性禮貌語助詞）", "thaiText": "ครับ",
                      "romanization": "khráp" }
                  ],
                  "variants": [],
                  "translatable": true
                }
                """);

        TranslationResult result = openAiTranslationClient.translate(
                "ผมอยากดื่มเหล้าครับ", TranslationDirectionEnum.TH_TO_ZH, null);

        assertThat(result.chineseText()).isEqualTo("我想喝酒");
        assertThat(result.thaiText()).isEqualTo("ผมอยากดื่มเหล้าครับ");
        assertThat(result.variants()).isEmpty();
        // 句尾助詞不可以被丟掉 —— 它是泰文最高頻的字之一
        assertThat(result.words()).hasSize(2);
    }
```

需要補的輔助方法（依既有測試取得 mock 回應的方式調整，重點是讓 `chatModel` 回傳指定的 JSON）：

```java
    private void givenChatResponse(String contentJson) {
        // 依既有測試的做法組出 ChatResponse，content 放入 contentJson
    }

    private String sixVariantsJson() {
        return """
                {
                  "chineseText": "我",
                  "thaiText": "ผม",
                  "romanization": "pǒm",
                  "words": [ { "chineseText": "我", "thaiText": "ผม", "romanization": "pǒm" } ],
                  "variants": [
                    { "thaiText": "ผม",   "romanization": "pǒm",   "genderUsage": "MALE",
                      "politeness": "FORMAL",  "note": "一" },
                    { "thaiText": "ฉัน",   "romanization": "chǎn",  "genderUsage": "FEMALE",
                      "politeness": "FORMAL",  "note": "二" },
                    { "thaiText": "ดิฉัน",  "romanization": "dì-chǎn", "genderUsage": "FEMALE",
                      "politeness": "FORMAL",  "note": "三" },
                    { "thaiText": "เรา",   "romanization": "rao",   "genderUsage": "BOTH",
                      "politeness": "CASUAL",  "note": "四" },
                    { "thaiText": "กู",    "romanization": "guu",   "genderUsage": "BOTH",
                      "politeness": "RUDE",    "note": "五" },
                    { "thaiText": "ข้า",    "romanization": "khâa",  "genderUsage": "BOTH",
                      "politeness": "CASUAL",  "note": "六" }
                  ],
                  "translatable": true
                }
                """;
    }
```

- [ ] **Step 2：跑測試，確認它失敗**

```powershell
.\mvnw.cmd test -Dtest=OpenAiTranslationClientTest
```

- [ ] **Step 3：改 `OpenAiTranslationClient` —— 提示詞**

把單一的 `SYSTEM_PROMPT` 換成兩套。**中翻泰那套要完整保留現有內容**（特別是 `romanization` 不可填漢語拼音、`translatable` 的規則、數字可翻），只在後面追加兩段：

```java
    /** 兩個方向共用的誠實原則，避免同一段話寫兩次。 */
    private static final String HONESTY_RULES = """
            translatable 欄位的規則（很重要，不要猜）：
            - 輸入是有意義、翻得出來的內容（包含數字，例如「5」就是「ห้า」）→ 設為 true
            - 輸入是亂碼、無意義的字串、或你無法確定它是什麼意思 → 設為 false，
              並且其餘欄位留空、words 與 variants 給空陣列
            - 寧可誠實回報 false，也不要硬湊一個看起來合理的答案。
              使用者是學習者，一個編造出來的詞會被他背起來。
            """;

    private static final String ZH_TO_TH_PROMPT = """
            你是中文轉泰文的翻譯助理，服務對象是正在學泰文的中文使用者。

            收到一段中文後，請回傳：
            1. chineseText：原封不動的輸入內容
            2. thaiText：整段對應的泰文
            3. romanization：「thaiText」的羅馬拼音，需標註聲調符號（例如 chǎn、dùuem、lâo）
            4. words：逐詞對照，把輸入依照語意切成詞，每個詞給出中文、泰文、羅馬拼音

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

            說話者的性別會在使用者訊息裡指明，造句時請遵守：
            - 男性：自稱用 ผม，句尾禮貌助詞用 ครับ
            - 女性：自稱用 ฉัน 或 ดิฉัน，句尾禮貌助詞用 ค่ะ

            variants —— 這個詞的各種說法（只有輸入是單一個詞時才要填）：

            如果 words 只有一個元素，請額外列出這個詞在泰文的各種說法，每個給出：
              thaiText      泰文
              romanization  羅馬拼音（含聲調符號）
              genderUsage   MALE / FEMALE / BOTH ——「哪種性別的人會這樣說」，
                            不分性別就填 BOTH
              politeness    FORMAL / NEUTRAL / CASUAL / RUDE
              note          一句中文說明，講清楚什麼場合用、對誰用會失禮

            variants 的規則（很重要）：
            - 最多 5 個
            - ★ 寧可只給一個，也不要為了看起來豐富而硬湊。
              大部分的詞就只有一種說法，這很正常，誠實回報即可。
            - 不同的說法泰文必須真的不同。
              不可以拿同一個泰文換個拼音寫法充數。
            - words 超過一個元素時，variants 給空陣列。

            """ + HONESTY_RULES;

    private static final String TH_TO_ZH_PROMPT = """
            你是泰文轉中文的翻譯助理，服務對象是正在學泰文的中文使用者。

            收到一段泰文後，請回傳：
            1. thaiText：原封不動的輸入內容
            2. chineseText：對應的繁體中文
            3. romanization：「thaiText」的羅馬拼音，需標註聲調符號
            4. words：逐詞對照，把泰文依語意切成詞，每個詞給出泰文、羅馬拼音、中文意思

            ★ 泰文書寫時詞與詞之間沒有空格，切詞是這項工作最重要的部分。

            句尾助詞的處理（不要省略）：
            - ครับ、ค่ะ、นะ、จ๊ะ 這類助詞沒有對應的中文詞，但一定要列進 words
            - 它們的 chineseText 請填一個括號標籤，例如「（男性禮貌語助詞）」
            - ★ 不可以因為「翻不出中文」就把它從 words 裡拿掉。
              這些是泰文最高頻的字，使用者正需要知道它們在做什麼。

            這個方向不需要 variants，一律回空陣列。

            """ + HONESTY_RULES;
```

- [ ] **Step 4：改 `OpenAiTranslationClient` —— 建構子與 translate**

建構子不再用 `defaultSystem`，改成每次呼叫時依方向帶入：

```java
    public OpenAiTranslationClient(ChatModel chatModel,
                                   ApiUsageRecorder apiUsageRecorder,
                                   AiPricingProperties pricingProperties,
                                   @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
                                   String modelName) {
        // ★ 不再用 defaultSystem —— 提示詞現在有兩套，要依方向在呼叫時決定。
        this.chatClient = ChatClient.builder(chatModel).build();
        this.apiUsageRecorder = apiUsageRecorder;
        this.pricingProperties = pricingProperties;
        this.modelName = modelName;
    }

    @Override
    public TranslationResult translate(String sourceText,
                                       TranslationDirectionEnum direction,
                                       SpeakerGenderEnum gender) {
        try {
            ResponseEntity<ChatResponse, TranslationPayload> response = chatClient.prompt()
                    .system(systemPromptOf(direction))
                    .user(userMessageOf(sourceText, direction, gender))
                    .call()
                    .responseEntity(TranslationPayload.class);

            // ...以下沿用既有的用量取得、null 檢查、translatable 檢查...
        }
        // ...
    }

    private String systemPromptOf(TranslationDirectionEnum direction) {
        return Objects.equals(direction, TranslationDirectionEnum.TH_TO_ZH)
                ? TH_TO_ZH_PROMPT
                : ZH_TO_TH_PROMPT;
    }

    /**
     * 中翻泰時把性別一起交代給模型，泰翻中不需要。
     */
    private String userMessageOf(String sourceText,
                                 TranslationDirectionEnum direction,
                                 SpeakerGenderEnum gender) {
        if (Objects.equals(direction, TranslationDirectionEnum.TH_TO_ZH)
                || Objects.isNull(gender)) {
            return sourceText;
        }

        return "說話者性別：" + gender.getDescription() + "\n輸入：" + sourceText;
    }
```

- [ ] **Step 5：改 `OpenAiTranslationClient` —— 驗證與轉換**

`containsChinese` 擴充到 variants，並新增 variants 的整理方法：

```java
    /** 一個詞最多列幾種說法。上限只是保險，真正防編造的是提示詞那句「寧可少給」。 */
    private static final int MAX_VARIANTS = 5;

    /**
     * 檢查泰文欄位裡有沒有混進中文字。
     * 模型能力不足時會翻一半停手，把沒翻出來的中文原字直接貼在泰文裡，
     * 例如「ฉันอยาก吃ข้าว」。這種結果看起來很像成功，
     * 但存進去就會永久污染快取與單字庫，所以在這裡當掉。
     *
     * ★ variants 是 2026-08-14 新開的路徑，一定要一起檢查 ——
     *   漏掉的話，污染會從新路徑繞過這道防線。
     */
    private boolean containsChinese(TranslationPayload payload) {
        if (CHINESE_PATTERN.matcher(payload.thaiText()).find()) {
            return true;
        }

        boolean wordsContainChinese = payload.words().stream()
                .anyMatch(word -> CHINESE_PATTERN.matcher(word.thaiText()).find());

        if (wordsContainChinese) {
            return true;
        }

        if (ObjectUtils.isEmpty(payload.variants())) {
            return false;
        }

        return payload.variants().stream()
                .filter(variant -> ObjectUtils.isNotEmpty(variant.thaiText()))
                .anyMatch(variant -> CHINESE_PATTERN.matcher(variant.thaiText()).find());
    }

    /**
     * 整理模型回來的說法：丟掉殘缺的、去掉泰文重複的、最多留 5 個。
     *
     * ★ 去重是必要的。模型有「盡量湊到期待數量」的本性，
     *   最常見的湊法就是把同一個泰文換個拼音寫法再交一次。
     *   留著的話畫面上會出現兩個看起來一樣的說法，寫入時還會撞唯一鍵。
     */
    private List<TranslationVariant> toVariants(List<VariantPayload> payloads) {
        if (ObjectUtils.isEmpty(payloads)) {
            return List.of();
        }

        Map<String, TranslationVariant> uniqueVariants = new LinkedHashMap<>();

        for (VariantPayload payload : payloads) {
            if (ObjectUtils.isEmpty(payload.thaiText())
                    || ObjectUtils.isEmpty(payload.romanization())) {
                // 殘缺的那一筆丟掉就好，不必整次翻譯失敗 ——
                // 少一種說法不影響使用，但殘缺的資料存進去會一直錯下去。
                log.warn("dropped an incomplete variant returned by the model");
                continue;
            }

            uniqueVariants.putIfAbsent(payload.thaiText(), new TranslationVariant(
                    payload.thaiText(),
                    payload.romanization(),
                    payload.genderUsage(),
                    payload.politeness(),
                    payload.note()));
        }

        return uniqueVariants.values().stream().limit(MAX_VARIANTS).toList();
    }
```

回傳值改成：

```java
            return new TranslationResult(
                    payload.chineseText(),
                    payload.thaiText(),
                    payload.romanization(),
                    words,
                    toVariants(payload.variants()),
                    modelName, inputTokens, outputTokens, true);
```

`TranslationPayload` 加兩個欄位、新增 `VariantPayload`：

```java
    private record TranslationPayload(
            String chineseText,
            String thaiText,
            String romanization,
            List<WordPayload> words,
            List<VariantPayload> variants,
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

    private record VariantPayload(
            String thaiText,
            String romanization,
            GenderUsageEnum genderUsage,
            PolitenessEnum politeness,
            String note) {
    }
```

檔案開頭的流程註解要更新：新增「兩套提示詞怎麼選」「variants 從哪來、為什麼要去重」兩段。

- [ ] **Step 6：跑測試，確認全部通過**

```powershell
.\mvnw.cmd test -Dtest=OpenAiTranslationClientTest
```

- [ ] **Step 7：回報並徵求同意後 commit**

```
翻譯客戶端支援雙向翻譯與多重說法

Feat:
- 新增泰翻中提示詞，句尾助詞照樣拆解並標註為無中文對應
- 中翻泰提示詞加入說話者性別與多重說法要求

Fix:
- 泰文混入中文字的檢查擴及 variants，避免污染從新路徑漏入單字庫

Improve:
- 說法去重、丟棄殘缺項目、上限 5 個
```

---

## Task 12：寫入服務支援多重說法與欄位補齊

**Files:**
- Modify: `src/main/java/com/tim/language_project/service/TranslationPersistenceService.java`
- Test: `src/test/java/com/tim/language_project/service/TranslationPersistenceServiceTest.java`（新建）

- [ ] **Step 1：先寫會失敗的測試**

```java
package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  TranslationPersistenceService 負責把一次翻譯的結果寫進三張表。
 *  這次改版讓它多了一個很容易被忽略的責任：★ 補齊欄位 ★
 *
 *  情境是這樣的：
 *
 *      第 1 天  你查「我想喝酒」
 *               → 逐詞的「我 → ฉัน」被沉澱進單字庫，
 *                 但翻句子時模型沒被要求給性別與禮貌，那三欄是空的
 *
 *      第 3 天  你單獨查「我」
 *               → 模型這次給了完整的三種說法，含性別與禮貌
 *               → ★ ฉัน 那一列已經存在了，如果只是「已存在就跳過」，
 *                   它的三個欄位會永遠是空的
 *
 *  所以規則是「已存在但欄位是空的 → 補上；已經有值 → 不覆蓋」。
 *
 * ── 哪些東西被換成假的 ──────────────────────────────────────────────────
 *
 *  三個 Repository 全部換成假的。這裡要測的是「寫入時的判斷邏輯」，
 *  不是資料庫本身 —— 資料庫的行為（唯一鍵）在 Repository 測試裡測過了。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  測試一  單字查詢的多個說法要各寫一列，性別與禮貌要跟著存
 *  測試二  ★已存在但欄位為空的說法，要被補齊（漏了這條，欄位永遠是空的）
 *  測試三  已存在且欄位有值的說法，不可被覆蓋（那是歷史，不該改寫）
 */

import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.client.model.TranslationVariant;
import com.tim.language_project.client.model.TranslationWord;
import com.tim.language_project.entity.TranslationQuery;
import com.tim.language_project.entity.Vocabulary;
import com.tim.language_project.enums.GenderUsageEnum;
import com.tim.language_project.enums.PolitenessEnum;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import com.tim.language_project.enums.VocabularySourceTypeEnum;
import com.tim.language_project.repository.TranslationQueryRepository;
import com.tim.language_project.repository.TranslationSegmentRepository;
import com.tim.language_project.repository.VocabularyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationPersistenceServiceTest {

    @Mock
    private TranslationQueryRepository translationQueryRepository;

    @Mock
    private TranslationSegmentRepository translationSegmentRepository;

    @Mock
    private VocabularyRepository vocabularyRepository;

    @InjectMocks
    private TranslationPersistenceService translationPersistenceService;

    @Test
    @DisplayName("單字的多個說法應各寫入一列並帶上標籤")
    void shouldPersistEachVariantAsItsOwnRow() {
        givenSavedQuery();
        when(vocabularyRepository.findAllByChineseTextIn(anyCollection()))
                .thenReturn(List.of());

        translationPersistenceService.persist(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                singleWordResultWithVariants());

        ArgumentCaptor<List<Vocabulary>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(vocabularyRepository).saveAll(captor.capture());

        List<Vocabulary> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved).extracting(Vocabulary::getThaiText)
                .containsExactlyInAnyOrder("ผม", "ฉัน", "กู");
        assertThat(saved).allMatch(entry ->
                java.util.Objects.nonNull(entry.getGenderUsage()));
    }

    /*
     * ★ 這個測試防的是「沉澱過的詞永遠補不齊」。
     *   沒有它，你先查過句子的那些高頻詞（我、你、他）
     *   會永遠停在沒有性別標籤的狀態，而那正是這次改版最想解決的詞。
     */
    @Test
    @DisplayName("已存在但標籤為空的說法應被補齊")
    void shouldFillLabelsOnExistingRowWithNullLabels() {
        givenSavedQuery();

        Vocabulary existing = new Vocabulary();
        existing.setChineseText("我");
        existing.setThaiText("ฉัน");
        existing.setRomanization("chǎn");
        existing.setSourceType(VocabularySourceTypeEnum.SEGMENT);

        when(vocabularyRepository.findAllByChineseTextIn(anyCollection()))
                .thenReturn(List.of(existing));

        translationPersistenceService.persist(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                singleWordResultWithVariants());

        assertThat(existing.getGenderUsage()).isEqualTo(GenderUsageEnum.FEMALE);
        assertThat(existing.getPoliteness()).isEqualTo(PolitenessEnum.FORMAL);
        assertThat(existing.getNote()).isEqualTo("女生自稱");
        // source_type 是歷史，不可被改寫
        assertThat(existing.getSourceType()).isEqualTo(VocabularySourceTypeEnum.SEGMENT);
    }

    @Test
    @DisplayName("已存在且標籤有值的說法不可被覆蓋")
    void shouldNotOverwriteExistingLabels() {
        givenSavedQuery();

        Vocabulary existing = new Vocabulary();
        existing.setChineseText("我");
        existing.setThaiText("ฉัน");
        existing.setRomanization("chǎn");
        existing.setGenderUsage(GenderUsageEnum.BOTH);
        existing.setPoliteness(PolitenessEnum.CASUAL);
        existing.setNote("原本就有的說明");
        existing.setSourceType(VocabularySourceTypeEnum.DIRECT);

        when(vocabularyRepository.findAllByChineseTextIn(anyCollection()))
                .thenReturn(List.of(existing));

        translationPersistenceService.persist(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                singleWordResultWithVariants());

        assertThat(existing.getGenderUsage()).isEqualTo(GenderUsageEnum.BOTH);
        assertThat(existing.getNote()).isEqualTo("原本就有的說明");
    }

    private void givenSavedQuery() {
        TranslationQuery savedQuery = new TranslationQuery();
        savedQuery.setId(1L);
        when(translationQueryRepository.saveAndFlush(any(TranslationQuery.class)))
                .thenReturn(savedQuery);
    }

    private TranslationResult singleWordResultWithVariants() {
        return new TranslationResult(
                "我", "ผม", "pǒm",
                List.of(new TranslationWord("我", "ผม", "pǒm")),
                List.of(
                        new TranslationVariant("ผม", "pǒm",
                                GenderUsageEnum.MALE, PolitenessEnum.FORMAL, "男生自稱"),
                        new TranslationVariant("ฉัน", "chǎn",
                                GenderUsageEnum.FEMALE, PolitenessEnum.FORMAL, "女生自稱"),
                        new TranslationVariant("กู", "guu",
                                GenderUsageEnum.BOTH, PolitenessEnum.RUDE, "很不客氣")),
                "gpt-5.5", 100L, 50L, true);
    }
}
```

- [ ] **Step 2：跑測試，確認它失敗**

```powershell
.\mvnw.cmd test -Dtest=TranslationPersistenceServiceTest
```

- [ ] **Step 3：改 `persist` 的簽章與快取寫入**

```java
    /**
     * 寫入一次完整的查詢結果，回傳快取那一筆的 id。
     * 音檔不在這裡處理 —— 全站的音檔由 AudioAssetService 統一管理。
     */
    @Transactional
    public Long persist(String sourceText,
                        TranslationDirectionEnum direction,
                        SpeakerGenderEnum gender,
                        TranslationResult result) {
        TranslationQuery query = new TranslationQuery();
        query.setSourceText(sourceText);
        query.setDirection(direction);
        // ★ 泰翻中沒有性別概念，這裡會是 null，資料表允許。
        query.setGender(gender);
        query.setChineseText(result.chineseText());
        query.setThaiText(result.thaiText());
        query.setRomanization(result.romanization());

        // saveAndFlush 是為了立刻拿到資料庫產生的 id，下一步當外鍵用。
        TranslationQuery savedQuery = translationQueryRepository.saveAndFlush(query);

        persistSegments(savedQuery.getId(), result.words());
        persistVocabulary(sourceText, result);

        return savedQuery.getId();
    }
```

- [ ] **Step 4：把 `persistNewVocabulary` 換成 `persistVocabulary`**

```java
    /**
     * 把這次的結果沉澱進單字庫。
     *
     * 兩種來源：
     *   variants 有內容 → 這是單字查詢，把每一種說法各寫一列
     *   variants 是空的 → 這是句子查詢，把逐詞各寫一列（沒有標籤）
     *
     * ★ 已經存在的說法不是單純「跳過」。
     *   如果它的性別／禮貌／說明是空的（代表它當初是從句子沉澱下來的），
     *   而這次拿到了有值的資料，就要補上去。漏掉這件事的話，
     *   「我」「你」「他」這些最常出現在句子裡的詞，
     *   會永遠停在沒有標籤的狀態 —— 而那正是這次改版最想解決的詞。
     */
    private void persistVocabulary(String sourceText, TranslationResult result) {
        List<Vocabulary> candidates = ObjectUtils.isEmpty(result.variants())
                ? fromWords(sourceText, result.words())
                : fromVariants(sourceText, result);

        if (ObjectUtils.isEmpty(candidates)) {
            return;
        }

        List<String> chineseTexts = candidates.stream()
                .map(Vocabulary::getChineseText)
                .distinct()
                .toList();

        List<Vocabulary> existingEntries =
                vocabularyRepository.findAllByChineseTextIn(chineseTexts);

        List<Vocabulary> newEntries = new ArrayList<>();

        for (Vocabulary candidate : candidates) {
            Vocabulary existing = findExisting(existingEntries, candidate);

            if (Objects.isNull(existing)) {
                if (!containsSameWord(newEntries, candidate)) {
                    newEntries.add(candidate);
                }
                continue;
            }

            fillMissingLabels(existing, candidate);
        }

        if (ObjectUtils.isNotEmpty(newEntries)) {
            vocabularyRepository.saveAll(newEntries);
        }
    }

    /**
     * 單字查詢：每一種說法各一列，帶著性別與禮貌標籤。
     */
    private List<Vocabulary> fromVariants(String sourceText, TranslationResult result) {
        List<Vocabulary> entries = new ArrayList<>();

        for (TranslationVariant variant : result.variants()) {
            Vocabulary vocabulary = new Vocabulary();
            vocabulary.setChineseText(result.chineseText());
            vocabulary.setThaiText(variant.thaiText());
            vocabulary.setRomanization(variant.romanization());
            vocabulary.setGenderUsage(variant.genderUsage());
            vocabulary.setPoliteness(variant.politeness());
            vocabulary.setNote(variant.note());
            vocabulary.setSourceType(Objects.equals(sourceText, result.chineseText())
                    ? VocabularySourceTypeEnum.DIRECT
                    : VocabularySourceTypeEnum.SEGMENT);
            entries.add(vocabulary);
        }

        return entries;
    }

    /**
     * 句子查詢：逐詞各一列，沒有標籤（翻句子時不會問模型要那些資訊）。
     */
    private List<Vocabulary> fromWords(String sourceText, List<TranslationWord> words) {
        if (ObjectUtils.isEmpty(words)) {
            return List.of();
        }

        List<Vocabulary> entries = new ArrayList<>();

        for (TranslationWord word : words) {
            Vocabulary vocabulary = new Vocabulary();
            vocabulary.setChineseText(word.chineseText());
            vocabulary.setThaiText(word.thaiText());
            vocabulary.setRomanization(word.romanization());
            vocabulary.setSourceType(Objects.equals(sourceText, word.chineseText())
                    ? VocabularySourceTypeEnum.DIRECT
                    : VocabularySourceTypeEnum.SEGMENT);
            entries.add(vocabulary);
        }

        return entries;
    }

    /**
     * 找出資料庫裡「同一個中文詞、同一個泰文」的那一列。
     * 唯一鍵是這兩欄的組合，所以比對也要用這兩欄。
     */
    private Vocabulary findExisting(List<Vocabulary> existingEntries, Vocabulary candidate) {
        return existingEntries.stream()
                .filter(entry -> Objects.equals(entry.getChineseText(),
                        candidate.getChineseText()))
                .filter(entry -> Objects.equals(entry.getThaiText(), candidate.getThaiText()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 只補空的欄位，已經有值的一律不動 ——
     * 那些是先前寫入的歷史，不該被後來的呼叫改寫。
     * source_type 也不更新，同樣的理由。
     */
    private void fillMissingLabels(Vocabulary existing, Vocabulary candidate) {
        if (Objects.isNull(existing.getGenderUsage())) {
            existing.setGenderUsage(candidate.getGenderUsage());
        }

        if (Objects.isNull(existing.getPoliteness())) {
            existing.setPoliteness(candidate.getPoliteness());
        }

        if (ObjectUtils.isEmpty(existing.getNote())) {
            existing.setNote(candidate.getNote());
        }
    }

    /**
     * 防止同一次寫入裡出現兩筆一樣的（例如句子裡重複的詞，或去重沒攔乾淨的說法）。
     */
    private boolean containsSameWord(List<Vocabulary> entries, Vocabulary candidate) {
        return entries.stream()
                .anyMatch(entry -> Objects.equals(entry.getChineseText(),
                        candidate.getChineseText())
                        && Objects.equals(entry.getThaiText(), candidate.getThaiText()));
    }
```

★ `fillMissingLabels` 改動的是從資料庫撈出來的 JPA 實體，而這個方法在 `@Transactional` 裡面，所以 Hibernate 會在交易結束時自動把變更寫回去，不需要再呼叫 `save`。這叫「dirty checking」，是 JPA 的預設行為。

- [ ] **Step 5：跑測試，確認通過**

```powershell
.\mvnw.cmd test -Dtest=TranslationPersistenceServiceTest
```

- [ ] **Step 6：回報並徵求同意後 commit**

```
寫入服務支援多重說法與標籤補齊

Feat:
- 單字查詢的每一種說法各寫入一列
- 已存在但標籤為空的說法會被補齊，已有值的不覆蓋

Modify:
- persist 改收翻譯方向與說話者性別，不再處理音檔
```

---

## Task 13：主流程改寫（含移除單字庫捷徑）

**Files:**
- Modify: `src/main/java/com/tim/language_project/service/TranslationService.java`
- Modify: `src/main/java/com/tim/language_project/service/VocabularyService.java`
- Modify: `src/main/java/com/tim/language_project/config/AudioStorageProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/java/com/tim/language_project/service/TranslationServiceTest.java`

這是整個計畫最關鍵的一個任務。決策 9（移除單字庫捷徑）就在這裡執行 —— 沒做這件事，前面所有多重說法的工作都會沒有作用。

- [ ] **Step 1：在測試檔補上會失敗的測試**

既有測試的呼叫全部改成 `translate(sourceText, gender)`，`findBySourceText` 改成 `findByKey`。新增：

```java
    /*
     * ★★ 這個測試是整個改版的命脈 ★★
     *
     * 舊的程式碼有一段「省錢捷徑」：整段輸入剛好是單字庫裡有的詞時，
     * 直接拿單字庫的答案回傳，不呼叫 AI。
     *
     * 那段捷徑會讓多重說法功能完全失效：
     *
     *   第 1 天  你查「我想喝酒」→「我 → ฉัน」被沉澱進單字庫
     *   第 3 天  你單獨查「我」  → 捷徑看到單字庫有「我」，直接回 ฉัน
     *                            → 永遠不會去問 AI 要 ผม 和 กู
     *
     * 這個測試把那條捷徑釘死：單字庫裡明明有「我」，仍然必須呼叫 AI。
     *
     * 如果有人日後為了「省錢」把捷徑加回來，這裡會亮紅燈。
     */
    @Test
    @DisplayName("單字庫已有該詞時仍必須呼叫翻譯服務")
    void shouldStillCallTranslationClientWhenVocabularyAlreadyHasTheWord() {
        when(translationQueryRepository.findByKey(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(Optional.empty());
        when(translationClient.translate(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(singleWordResultWithVariants());
        when(audioAssetService.resolveAudioUrl(anyString(), any()))
                .thenReturn(Optional.of("/audio/th/a1b2c3.mp3"));

        TranslationResponseDto response =
                translationService.translate("我", SpeakerGenderEnum.MALE);

        // ★ 重點：AI 一定要被呼叫，不可以被單字庫擋下來
        verify(translationClient).translate(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE);
        assertThat(response.variants()).hasSize(3);
    }

    /*
     * ★ 決策 7：同一句話的男版與女版要分開查快取。
     *   共用的話，你切到女生會看到男生的講法。
     */
    @Test
    @DisplayName("查快取時性別必須一起帶入")
    void shouldIncludeGenderWhenLookingUpCache() {
        when(translationQueryRepository.findByKey(
                "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.FEMALE))
                .thenReturn(Optional.of(new TranslationQueryDto(
                        1L, "我想喝酒", TranslationDirectionEnum.ZH_TO_TH,
                        SpeakerGenderEnum.FEMALE, "我想喝酒",
                        "ฉันอยากดื่มเหล้าค่ะ", "chǎn yàak dùuem lâo khâ")));
        when(translationSegmentRepository.findByQueryIdOrderBySeqNo(1L))
                .thenReturn(List.of());
        when(audioAssetService.findExistingAudioUrl(anyString(), any()))
                .thenReturn(Optional.of("/audio/th/a1b2c3.mp3"));

        TranslationResponseDto response =
                translationService.translate("我想喝酒", SpeakerGenderEnum.FEMALE);

        assertThat(response.fromCache()).isTrue();
        verify(translationClient, never()).translate(anyString(), any(), any());
    }

    /*
     * 泰翻中不分性別。就算前端照樣送了性別過來，也要存成 null，
     * 否則同一句泰文會因為性別不同被翻譯兩次，白花一次錢。
     */
    @Test
    @DisplayName("輸入泰文時性別應被忽略並存為 null")
    void shouldIgnoreGenderWhenInputIsThai() {
        when(translationQueryRepository.findByKey(
                "ผมอยากดื่มเหล้า", TranslationDirectionEnum.TH_TO_ZH, null))
                .thenReturn(Optional.empty());
        when(translationClient.translate(
                "ผมอยากดื่มเหล้า", TranslationDirectionEnum.TH_TO_ZH, null))
                .thenReturn(thaiToChineseResult());
        when(audioAssetService.resolveAudioUrl(anyString(), any()))
                .thenReturn(Optional.of("/audio/th/a1b2c3.mp3"));

        translationService.translate("ผมอยากดื่มเหล้า", SpeakerGenderEnum.MALE);

        verify(translationClient).translate(
                "ผมอยากดื่มเหล้า", TranslationDirectionEnum.TH_TO_ZH, null);
        verify(translationPersistenceService).persist(
                eq("ผมอยากดื่มเหล้า"), eq(TranslationDirectionEnum.TH_TO_ZH),
                eq(null), any(TranslationResult.class));
    }

    /*
     * ★ 決策 14：中文音檔不自動產生。
     *   自動產生的話，每次查詢都要多打一次 OpenAI、多等一兩秒，
     *   而唯一的使用者根本不需要聽中文。
     */
    @Test
    @DisplayName("不得自動產生中文音檔")
    void shouldNotAutoGenerateChineseAudio() {
        when(translationQueryRepository.findByKey(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(Optional.empty());
        when(translationClient.translate(
                "我", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(singleWordResultWithVariants());
        when(audioAssetService.resolveAudioUrl(anyString(), any()))
                .thenReturn(Optional.of("/audio/th/a1b2c3.mp3"));

        TranslationResponseDto response =
                translationService.translate("我", SpeakerGenderEnum.MALE);

        assertThat(response.chineseAudioUrl()).isNull();
        verify(audioAssetService, never())
                .resolveAudioUrl(anyString(), eq(SpeechLanguageEnum.ZH));
    }
```

輔助方法（`singleWordResultWithVariants` 與 `thaiToChineseResult` 照 Task 12 測試檔的寫法建立，`TranslationResult` 的建構子已含 `chineseText` 與 `variants`）。

- [ ] **Step 2：跑測試，確認它失敗**

```powershell
.\mvnw.cmd test -Dtest=TranslationServiceTest
```

- [ ] **Step 3：在 `AudioAssetService` 補一個「只查不生」的方法**

讀快取時不該觸發合成 —— 快取命中的意義就是「這次不花錢」。

```java
    /**
     * 只查現成的音檔，查不到就回空的，★絕對不會觸發合成★。
     * 讀取快取時用這個 —— 快取命中的意義就是「這次不花錢」，
     * 若在那條路上呼叫 resolveAudioUrl，音檔缺失時會偷偷變成一次付費呼叫。
     */
    public Optional<String> findExistingAudioUrl(String speechText,
                                                 SpeechLanguageEnum language) {
        if (ObjectUtils.isEmpty(speechText)) {
            return Optional.empty();
        }

        return audioAssetRepository.findBySpeechTextAndLanguage(speechText, language)
                .map(audioAsset -> toAudioUrl(audioAsset.filePath()));
    }
```

- [ ] **Step 4：在 `AudioStorageProperties` 新增自動產生的設定**

```java
    /**
     * 查詢完成時要自動產生哪些語言的音檔（涵蓋整句與多重說法）。
     * 預設只有泰文 —— 目前的使用者是中文母語者，中文音檔對他價值為零，
     * 為它每次多等一兩秒不划算。日後開放給泰國使用者時改成 TH, ZH 即可。
     */
    private List<SpeechLanguageEnum> autoGenerate = List.of(SpeechLanguageEnum.TH);
```

`application.yml` 的 `audio.storage` 底下加：

```yaml
    # 查詢完成時自動產生哪些語言的音檔（涵蓋整句與多重說法）。
    # 中文音檔的機制已完整建置，只是預設不主動產生 —— 目前的使用者不需要聽中文，
    # 為它每次查詢多等一兩秒不划算。要啟用改成 [TH, ZH] 即可，不需改程式。
    auto-generate: [TH]
```

- [ ] **Step 5：改寫 `TranslationService`**

移除 `resolveTranslation`、`speechClient`、`vocabularyRepository` 三者，改用 `languageDetector` 與 `audioAssetService`：

```java
    public TranslationResponseDto translate(String rawInput, SpeakerGenderEnum gender) {
        String sourceText = validateAndNormalize(rawInput);
        TranslationDirectionEnum direction = languageDetector.detect(sourceText);

        // ★ 泰翻中沒有性別概念。前端照樣會送性別過來，這裡直接歸零 ——
        //   不歸零的話，同一句泰文會因為性別不同被翻譯兩次，白花一次錢。
        SpeakerGenderEnum effectiveGender =
                Objects.equals(direction, TranslationDirectionEnum.TH_TO_ZH) ? null : gender;

        Optional<TranslationQueryDto> cached =
                translationQueryRepository.findByKey(sourceText, direction, effectiveGender);

        if (cached.isPresent()) {
            return buildCachedResponse(cached.get());
        }

        TranslationResult result =
                translationClient.translate(sourceText, direction, effectiveGender);

        if (!result.translatable()) {
            // 模型自己說翻不出來。當場停手，不生語音也不寫資料庫，
            // 免得一個編造的詞被永久留在快取與單字庫裡。
            log.warn("model reported untranslatable input, length={}", sourceText.length());
            throw new BusinessException(ErrorCodeEnum.INPUT_UNSUPPORTED_CONTENT);
        }

        String thaiAudioUrl = autoGenerateAudio(result.thaiText(), SpeechLanguageEnum.TH);
        String chineseAudioUrl = autoGenerateAudio(result.chineseText(), SpeechLanguageEnum.ZH);
        List<TranslationVariantDto> variants = buildVariants(result);

        try {
            translationPersistenceService.persist(
                    sourceText, direction, effectiveGender, result);
        } catch (DataIntegrityViolationException exception) {
            // 撞到唯一鍵，代表在我們翻譯的這幾秒內，另一個請求已經把同一句寫進去了。
            // 這不是錯誤，是「有人比我們快」。改讀他寫好的那筆回傳，
            // 使用者完全不會發現發生過這件事。
            log.warn("concurrent write detected for the same input, falling back to the cached row");

            return translationQueryRepository.findByKey(sourceText, direction, effectiveGender)
                    .map(this::buildCachedResponse)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCodeEnum.DATA_PERSIST_FAILED, exception));
        }

        return new TranslationResponseDto(
                sourceText, direction, effectiveGender,
                result.chineseText(), result.thaiText(), result.romanization(),
                thaiAudioUrl, chineseAudioUrl, false,
                toSegmentDtos(result.words()), variants);
    }

    /**
     * 設定裡有列到的語言才自動產生音檔，沒列到的一律回 null（改由使用者點擊產生）。
     */
    private String autoGenerateAudio(String speechText, SpeechLanguageEnum language) {
        if (!audioStorageProperties.getAutoGenerate().contains(language)) {
            return null;
        }

        return audioAssetService.resolveAudioUrl(speechText, language).orElse(null);
    }

    /**
     * 把每一種說法的音檔一併產生。
     * ★ 整句與第一個說法常常是同一段文字（查「我」時 thaiText 就是 ผม），
     *   靠 audio_asset 的唯一鍵自動共用，不會重複合成。
     */
    private List<TranslationVariantDto> buildVariants(TranslationResult result) {
        if (ObjectUtils.isEmpty(result.variants())) {
            return List.of();
        }

        List<TranslationVariantDto> variants = new ArrayList<>();

        for (TranslationVariant variant : result.variants()) {
            variants.add(new TranslationVariantDto(
                    variant.thaiText(),
                    variant.romanization(),
                    variant.genderUsage(),
                    variant.politeness(),
                    variant.note(),
                    autoGenerateAudio(variant.thaiText(), SpeechLanguageEnum.TH)));
        }

        return variants;
    }
```

`buildCachedResponse` 要改成用 `findExistingAudioUrl`（★ 不可用 `resolveAudioUrl`，否則快取命中卻音檔缺失時會偷偷變成付費呼叫），並從單字庫撈出說法：

```java
    private TranslationResponseDto buildCachedResponse(TranslationQueryDto cached) {
        List<TranslationSegmentDto> segments =
                translationSegmentRepository.findByQueryIdOrderBySeqNo(cached.id());

        // 快取命中代表「這次不花錢」，所以只查現成的音檔，絕不合成。
        String thaiAudioUrl = audioAssetService
                .findExistingAudioUrl(cached.thaiText(), SpeechLanguageEnum.TH).orElse(null);
        String chineseAudioUrl = audioAssetService
                .findExistingAudioUrl(cached.chineseText(), SpeechLanguageEnum.ZH).orElse(null);

        return new TranslationResponseDto(
                cached.sourceText(), cached.direction(), cached.gender(),
                cached.chineseText(), cached.thaiText(), cached.romanization(),
                thaiAudioUrl, chineseAudioUrl, true,
                segments, cachedVariants(cached));
    }

    /**
     * 快取命中時，說法從單字庫撈 —— 那裡就是它們的家。
     * 只有單字查詢才有說法，句子查詢撈出來會是空的（因為句子不是一個詞）。
     */
    private List<TranslationVariantDto> cachedVariants(TranslationQueryDto cached) {
        List<VocabularyDto> words =
                vocabularyRepository.findByChineseText(cached.chineseText());

        if (words.size() <= 1) {
            return List.of();
        }

        List<TranslationVariantDto> variants = new ArrayList<>();

        for (VocabularyDto word : words) {
            variants.add(new TranslationVariantDto(
                    word.thaiText(), word.romanization(),
                    word.genderUsage(), word.politeness(), word.note(),
                    audioAssetService.findExistingAudioUrl(
                            word.thaiText(), SpeechLanguageEnum.TH).orElse(null)));
        }

        return variants;
    }
```

`toSegmentDtos` 要補上兩個音檔網址（用 `findExistingAudioUrl`，逐詞是點了才生）：

```java
    private List<TranslationSegmentDto> toSegmentDtos(List<TranslationWord> words) {
        List<TranslationSegmentDto> segments = new ArrayList<>();
        int seqNo = 1;

        for (TranslationWord word : words) {
            // ★ 逐詞音檔是「點了才生」，所以這裡只查現成的，不合成。
            //   改成 resolveAudioUrl 的話，查一句話會多打好幾次 OpenAI，
            //   使用者要多等四到八秒。
            segments.add(new TranslationSegmentDto(
                    seqNo++, word.chineseText(), word.thaiText(), word.romanization(),
                    audioAssetService.findExistingAudioUrl(
                            word.thaiText(), SpeechLanguageEnum.TH).orElse(null),
                    audioAssetService.findExistingAudioUrl(
                            word.chineseText(), SpeechLanguageEnum.ZH).orElse(null)));
        }

        return segments;
    }
```

`translationSegmentRepository.findByQueryIdOrderBySeqNo` 的建構子表達式也要補上兩個音檔欄位（回傳 null，由 Service 之後補；或改成先撈基本欄位再逐筆補音檔 —— 擇一實作，測試通過即可）。

**檔案開頭的流程註解要大改：**
- 第 3 步的「第二道省錢關卡：單字庫」整段刪除，改寫成一段說明「為什麼把它拿掉」
- 新增方向判斷、性別處理、說法組裝、音檔自動產生規則四段

- [ ] **Step 6：更新 `VocabularyService` 的註解**

類別 Javadoc 改成：

```java
/**
 * 單字庫的讀取。
 * 這裡只有讀，沒有寫 —— 單字是查詢流程自己沉澱進去的，使用者不能手動新增或修改。
 *
 * ★ 2026-08-14 起，這張表不再兼任「省錢用的快取」。
 *   以前 TranslationService 會先查這裡，命中就不呼叫 AI，
 *   但那條捷徑會讓「多重說法」永遠拿不到完整資料（詳見 TranslationService 說明）。
 *   現在它是純粹的學習資產：給單字列表頁瀏覽用。
 *
 *   同一個中文詞在這裡可能有多列（「我」有 ผม / ฉัน / กู 三列），
 *   所以列表頁會出現重複的中文詞，那是預期行為。
 */
```

- [ ] **Step 7：跑全部測試**

```powershell
.\mvnw.cmd clean test
```

預期：全部 PASS。

- [ ] **Step 8：回報並徵求同意後 commit**

```
主流程支援雙向翻譯與多重說法

Feat:
- 依輸入自動判斷翻譯方向，泰翻中不帶性別
- 單字查詢回傳多種說法，各自附帶音檔

Fix:
- 移除單字庫捷徑。該捷徑會讓查過句子的詞永遠拿不到完整說法，
  而它一輩子只省一次呼叫

Modify:
- 音檔改由 AudioAssetService 統一管理，快取命中時只查不生
- 新增 audio.storage.auto-generate 設定，中文音檔預設不自動產生
```

---

## Task 14：DTO 與 Controller

**Files:**
- Create: `src/main/java/com/tim/language_project/dto/response/TranslationVariantDto.java`
- Modify: `src/main/java/com/tim/language_project/dto/request/TranslationRequestDto.java`
- Modify: `src/main/java/com/tim/language_project/dto/response/TranslationResponseDto.java`
- Modify: `src/main/java/com/tim/language_project/dto/response/TranslationSegmentDto.java`
- Modify: `src/main/java/com/tim/language_project/controller/TranslationController.java`
- Modify: `src/test/java/com/tim/language_project/controller/TranslationControllerTest.java`

> 這個任務的 DTO 在 Task 13 就會被用到，實務上兩個任務會一起做完才編譯得過。分開列出是為了讓改動範圍清楚。

- [ ] **Step 1：建立 `TranslationVariantDto`**

```java
package com.tim.language_project.dto.response;

import com.tim.language_project.enums.GenderUsageEnum;
import com.tim.language_project.enums.PolitenessEnum;

/**
 * 一個中文詞在泰文的其中一種說法，要回傳給前端。
 * 前端依 genderUsage 排序（符合使用者性別的排前面）、依 politeness 上色。
 * thaiAudioUrl 為 null 代表音檔還沒產生。
 */
public record TranslationVariantDto(
        String thaiText,
        String romanization,
        GenderUsageEnum genderUsage,
        PolitenessEnum politeness,
        String note,
        String thaiAudioUrl) {
}
```

- [ ] **Step 2：改三個既有 DTO**

```java
package com.tim.language_project.dto.request;

import com.tim.language_project.enums.SpeakerGenderEnum;

/**
 * 查詢請求：{ "sourceText": "我", "gender": "MALE" }。
 * 輸入泰文時 gender 會被後端忽略，因為泰翻中沒有性別概念。
 */
public record TranslationRequestDto(String sourceText, SpeakerGenderEnum gender) {
}
```

```java
package com.tim.language_project.dto.response;

import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;

import java.util.List;

/**
 * 一次查詢回給前端的完整結果。
 * 音檔網址為 null 代表還沒產生，前端顯示成灰色的播放鍵，點擊才會產生。
 * fromCache 讓前端（和我們自己）看得出這次有沒有花錢。
 * variants 只有查單一個詞時才有內容。
 */
public record TranslationResponseDto(
        String sourceText,
        TranslationDirectionEnum direction,
        SpeakerGenderEnum gender,
        String chineseText,
        String thaiText,
        String romanization,
        String thaiAudioUrl,
        String chineseAudioUrl,
        boolean fromCache,
        List<TranslationSegmentDto> segments,
        List<TranslationVariantDto> variants) {
}
```

```java
package com.tim.language_project.dto.response;

/**
 * 逐詞對照裡的其中一個詞。
 * 兩個音檔網址為 null 代表還沒產生，使用者點擊播放鍵時才會合成。
 */
public record TranslationSegmentDto(
        Integer seqNo,
        String chineseText,
        String thaiText,
        String romanization,
        String thaiAudioUrl,
        String chineseAudioUrl) {
}
```

- [ ] **Step 3：改 `TranslationController`**

```java
    @PostMapping
    public ResponseEntity<TranslationResponseDto> translate(
            @RequestBody TranslationRequestDto request) {
        TranslationResponseDto response =
                translationService.translate(request.sourceText(), request.gender());

        // 讀快取回 200，真的建立了新東西回 201。
        HttpStatus status = response.fromCache() ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }
```

檔案開頭流程註解的第 1 步與第 6 步要更新成含 `gender` 的請求與新的回應格式。

- [ ] **Step 4：更新 `TranslationControllerTest`**

既有測試的請求 JSON 補上 `gender`，回應斷言改用新欄位名（`audioUrl` → `thaiAudioUrl`）。新增一個測試：

```java
    @Test
    @DisplayName("性別應原樣傳給 Service")
    void shouldPassGenderToService() throws Exception {
        when(translationService.translate("我", SpeakerGenderEnum.FEMALE))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/translations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TranslationRequestDto("我", SpeakerGenderEnum.FEMALE))))
                .andExpect(status().isCreated());

        verify(translationService).translate("我", SpeakerGenderEnum.FEMALE);
    }
```

- [ ] **Step 5：跑全部測試**

```powershell
.\mvnw.cmd clean test
```

預期：全部 PASS。

- [ ] **Step 6：回報並徵求同意後 commit**

```
API 格式支援方向、性別與多重說法

Modify:
- 請求新增 gender
- 回應新增 direction、gender、chineseText、variants，音檔網址拆成中泰兩個
- 逐詞對照新增兩個音檔網址
```

---

## Task 15：前端

**Files:**
- Modify: `frontend/src/app/models/translation.ts`
- Modify: `frontend/src/app/services/translation-service.ts`
- Modify: `frontend/src/app/translation/translation.ts`
- Modify: `frontend/src/app/translation/translation.html`
- Modify: `frontend/src/app/translation/translation.css`

> ★ `models/translation.ts` 的欄位名稱必須與後端 record **完全一致**。對不上不會有錯誤訊息，只會安靜地拿到 `undefined`（該檔案開頭的註解就是在講這件事）。

- [ ] **Step 1：更新型別宣告**

```typescript
/** 說話者性別，對應後端 SpeakerGenderEnum。 */
export type SpeakerGender = 'MALE' | 'FEMALE';

/** 翻譯方向，對應後端 TranslationDirectionEnum。由後端依輸入自動判斷。 */
export type TranslationDirection = 'ZH_TO_TH' | 'TH_TO_ZH';

/** 一個說法適合誰用，對應後端 GenderUsageEnum。 */
export type GenderUsage = 'MALE' | 'FEMALE' | 'BOTH';

/** 禮貌程度，對應後端 PolitenessEnum。 */
export type Politeness = 'FORMAL' | 'NEUTRAL' | 'CASUAL' | 'RUDE';

/** 音檔語言，對應後端 SpeechLanguageEnum。 */
export type SpeechLanguage = 'TH' | 'ZH';

/** 對應後端 TranslationSegmentDto，一列逐詞對照。 */
export interface TranslationSegment {
  seqNo: number;
  chineseText: string;
  thaiText: string;
  romanization: string;
  /** null 代表音檔還沒產生，顯示成灰色的播放鍵，點擊才會產生。 */
  thaiAudioUrl: string | null;
  chineseAudioUrl: string | null;
}

/** 對應後端 TranslationVariantDto，一個詞的其中一種說法。 */
export interface TranslationVariant {
  thaiText: string;
  romanization: string;
  genderUsage: GenderUsage;
  politeness: Politeness;
  note: string;
  thaiAudioUrl: string | null;
}

/** 對應後端 TranslationResponseDto，一次查詢的完整結果。 */
export interface TranslationResponse {
  sourceText: string;
  direction: TranslationDirection;
  gender: SpeakerGender | null;
  chineseText: string;
  thaiText: string;
  romanization: string;
  thaiAudioUrl: string | null;
  chineseAudioUrl: string | null;
  /** true 代表這次讀快取、沒有呼叫 OpenAI，也就是沒有花錢。 */
  fromCache: boolean;
  segments: TranslationSegment[];
  /** 只有查單一個詞時才有內容，查句子時是空陣列。 */
  variants: TranslationVariant[];
}

/** 對應後端 AudioResponseDto。 */
export interface AudioResponse {
  audioUrl: string;
}

/** 對應後端 ErrorResponseDto，所有錯誤都是這個格式。 */
export interface ErrorResponse {
  code: string;
  message: string;
  traceId: string;
}
```

- [ ] **Step 2：Service 加上性別參數與合成音檔的方法**

```typescript
  translate(sourceText: string, gender: SpeakerGender) {
    return this.http.post<TranslationResponse>(
      '/api/v1/translations', { sourceText, gender });
  }

  /**
   * 產生一段文字的音檔。逐詞的播放鍵是灰色時，點下去就是打這支。
   * 後端會先查現成的，沒有才真的合成 —— 所以同一個詞點第二次不會再花錢。
   */
  synthesize(speechText: string, language: SpeechLanguage) {
    return this.http.post<AudioResponse>(
      '/api/v1/audio', { speechText, language });
  }
```

檔案開頭的流程註解要補上：請求多了 `gender`、以及新增的音檔端點。

- [ ] **Step 3：元件加上性別狀態與排序邏輯**

```typescript
  /** 使用者選的性別，存在 localStorage，重開瀏覽器記得上次的選擇。 */
  gender: SpeakerGender = (localStorage.getItem('gender') as SpeakerGender) ?? 'MALE';

  /**
   * 切換性別。畫面上已經有結果時要重新查一次 ——
   * 因為整句翻譯的泰文會跟著性別改變（ผม vs ฉัน、ครับ vs ค่ะ）。
   */
  changeGender(gender: SpeakerGender): void {
    this.gender = gender;
    localStorage.setItem('gender', gender);

    if (this.result) {
      this.search();
    }
  }

  /**
   * 說法的排序規則（規格決策 3）：
   *   ① 符合使用者性別的排前面（BOTH 也算符合）
   *   ② 再依禮貌程度，正式的在前
   *   ③ 最後保持後端回傳的順序
   *
   * ★ 刻意「不過濾」異性的說法。你是男生沒錯，但泰國女生跟你講話時
   *   就是會說 ฉัน，藏起來的話你會聽不懂對方。
   */
  sortedVariants(variants: TranslationVariant[]): TranslationVariant[] {
    const politenessOrder: Politeness[] = ['FORMAL', 'NEUTRAL', 'CASUAL', 'RUDE'];

    return [...variants].sort((left, right) => {
      const leftMatches = this.matchesGender(left) ? 0 : 1;
      const rightMatches = this.matchesGender(right) ? 0 : 1;

      if (leftMatches !== rightMatches) {
        return leftMatches - rightMatches;
      }

      return politenessOrder.indexOf(left.politeness)
        - politenessOrder.indexOf(right.politeness);
    });
  }

  matchesGender(variant: TranslationVariant): boolean {
    return variant.genderUsage === this.gender || variant.genderUsage === 'BOTH';
  }

  /**
   * 點擊灰色的播放鍵：先跟後端要音檔，拿到後直接播放並記在畫面上，
   * 這樣同一個詞再點就是亮的、直接播，不會再打一次後端。
   */
  playOrSynthesize(target: TranslationSegment | TranslationVariant,
                   language: SpeechLanguage): void {
    const existingUrl = language === 'TH'
      ? target.thaiAudioUrl
      : (target as TranslationSegment).chineseAudioUrl;

    if (existingUrl) {
      new Audio(existingUrl).play();
      return;
    }

    const speechText = language === 'TH'
      ? target.thaiText
      : (target as TranslationSegment).chineseText;

    this.synthesizing.add(speechText);

    this.translationService.synthesize(speechText, language).subscribe({
      next: (response) => {
        this.synthesizing.delete(speechText);

        if (language === 'TH') {
          target.thaiAudioUrl = response.audioUrl;
        } else {
          (target as TranslationSegment).chineseAudioUrl = response.audioUrl;
        }

        new Audio(response.audioUrl).play();
      },
      error: () => {
        this.synthesizing.delete(speechText);
      },
    });
  }

  /** 正在合成中的文字，用來顯示載入狀態。 */
  synthesizing = new Set<string>();
```

`search()` 呼叫改成 `this.translationService.translate(this.input, this.gender)`。

- [ ] **Step 4：畫面**

`translation.html` 需要：

1. **性別切換鈕**放在輸入框旁邊，兩個選項（男／女），目前選的要看得出來
2. **說法區塊**，只在 `result.variants.length > 0` 時顯示。每一列：
   - 泰文（大字）
   - 羅馬拼音
   - 【適用性別】【禮貌程度】兩個標籤
   - 中文說明
   - 播放鍵
   - `matchesGender(variant) && 是第一個` 時加上「★ 適合你」
3. **逐詞對照**每列加播放鍵，`thaiAudioUrl` 為 null 時樣式是灰的，`synthesizing.has(...)` 時顯示載入中
4. **整句播放鍵**用 `result.thaiAudioUrl`

`translation.css` 需要：

- 性別切換鈕的選中樣式
- 禮貌程度標籤：`RUDE` 用警示色（紅系），其餘用中性色
- 播放鍵的三種狀態：可播放（實色）／未產生（灰色）／載入中

- [ ] **Step 5：建置確認**

```powershell
cd frontend
npm run build
```

預期：建置成功，無 TypeScript 錯誤。

- [ ] **Step 6：回報並徵求同意後 commit**

```
前端支援性別切換與多重說法

Feat:
- 新增男女性別切換，選擇存於 localStorage，切換時自動重查
- 單字查詢顯示所有說法，符合性別的排前面並標記，粗俗用語以警示色標示
- 逐詞對照新增播放鍵，未產生的音檔點擊後即時合成
```

---

## Task 16：端對端驗證

**Files:** 無（手動驗證）

程式都對了不代表功能是對的。這一關要真的打開瀏覽器操作，特別是那些單元測試測不到的事：模型到底會不會照格式回答、泰文發音聽起來對不對。

- [ ] **Step 1：確認前置條件**

```powershell
.\mvnw.cmd clean test
```

預期：全部 PASS。並確認 SQL Server 容器已啟動、資料表已依 Task 3 重建、`audio/th/` 與 `audio/zh/` 資料夾存在。

- [ ] **Step 2：啟動後端與前端**

```powershell
.\mvnw.cmd spring-boot:run
```

另開一個視窗：

```powershell
cd frontend
npm start
```

- [ ] **Step 3：逐項驗證，每項打勾**

| # | 操作 | 預期結果 |
|---|---|---|
| 1 | 性別選【男】，查「我」 | 出現多個說法，`ผม` 排最前並標「★ 適合你」，`กู` 有警示色 |
| 2 | 點每個說法的播放鍵 | 三個都能播，聽得出 `ผม` 和 `กู` 語感不同 |
| 3 | 切到【女】 | 自動重查，`ฉัน` 排到最前面 |
| 4 | 看伺服器日誌 | 第 3 步**沒有**呼叫 OpenAI（★ 決策 7，單字不分性別） |
| 5 | 性別選【男】，查「我想喝酒」 | 泰文用 `ผม`、句尾 `ครับ` |
| 6 | 切到【女】，查同一句 | 泰文改用 `ฉัน`、句尾 `ค่ะ`，且日誌顯示有呼叫 OpenAI |
| 7 | 逐詞對照 | 播放鍵是灰的（除非那個詞查過） |
| 8 | 點灰色播放鍵 | 轉圈一兩秒後變亮並播放 |
| 9 | 重新整理再點同一個 | 直接播放，日誌**沒有**呼叫 OpenAI（★ 決策 6） |
| 10 | 貼上泰文 `ผมอยากดื่มเหล้าครับ` | 回傳中文，逐詞含 `ครับ` 且標為「（男性禮貌語助詞）」 |
| 11 | 查「5」 | 正常翻成 `ห้า`（★ 防止數字輸入被方向判斷弄壞） |
| 12 | 查「asdfgh」 | 回「輸入內容無法翻譯」，不寫入資料庫 |
| 13 | 打開單字列表頁 | 「我」出現多列，各自帶標籤 |
| 14 | 檢查 `audio/` 資料夾 | 檔案分別在 `th/` 與 `zh/` 底下，`zh/` 只有你手動點過的 |
| 15 | 查詢資料庫 `SELECT * FROM audio_asset` | 同一段泰文只有一列 |

- [ ] **Step 4：驗證單字庫捷徑真的不見了（★ 最重要的一項）**

```
1. 清空資料庫（或換一個沒查過的詞，例如「你」）
2. 先查「你好嗎」→ 逐詞的「你」被沉澱進單字庫
3. 再單獨查「你」
4. ★ 必須出現多個說法（คุณ / เธอ / นาย …），而不是只有句子裡的那一個
5. 看日誌，第 3 步必須有呼叫 OpenAI
```

這一項失敗代表捷徑還在（或以別的形式回來了），整個改版的價值歸零。

- [ ] **Step 5：把實測結果補進規格書**

在 spec 末尾新增一節「實測結果」，記錄實際看到的說法內容、費用、以及任何與預期不符的地方。前一份規格（`2026-08-11`）也是這樣做的。

- [ ] **Step 6：回報結果**

把第 3、4 步的每一項結果告訴 Awei，包含**沒過的項目**。不要只報成功的。

---

## 自我檢查結果

寫完後對照規格逐節檢查，記錄如下：

| 規格章節 | 對應任務 | 狀態 |
|---|---|---|
| 4.2 `translation_query` | Task 3、8 | ✅ |
| 4.3 `translation_segment` 不變 | — | ✅ 確實不需異動 |
| 4.4 `vocabulary` 與合併規則 | Task 3、9、12 | ✅ |
| 4.5 `audio_asset` | Task 3、4 | ✅ |
| 5 五個 Enum 與錯誤碼 | Task 1 | ✅ |
| 5 方向判斷規則 | Task 2 | ✅ |
| 6 音檔資料夾與 `auto-generate` | Task 5、13 | ✅ |
| 7 移除單字庫捷徑 | Task 13 Step 1、16 Step 4 | ✅ 有專屬測試釘住 |
| 8.1 中翻泰提示詞 | Task 11 | ✅ |
| 8.2 泰翻中提示詞 | Task 11 | ✅ |
| 9.1 翻譯 API | Task 14 | ✅ |
| 9.2 合成音檔 API | Task 7 | ✅ |
| 12 前端 | Task 15 | ✅ |
| 13 測試 | 各任務內 | ✅ |
| 4.1 清資料 | Task 3 Step 6 | ✅ 標明由 Awei 執行 |

**檢查中發現並已補進計畫的兩件事：**

1. **讀快取時不可觸發合成。** 規格只寫了「快取命中 0 元」，沒說音檔怎麼取。若快取路徑直接用 `resolveAudioUrl`，音檔缺失時會偷偷變成一次付費呼叫，而回應還標著 `fromCache: true` —— 帳目會對不起來。因此 Task 13 Step 3 加了只查不生的 `findExistingAudioUrl`。

2. **快取命中時的 `variants` 從哪來。** 規格沒交代。答案是從單字庫撈（Task 13 的 `cachedVariants`），因為說法本來就存在那裡。

**已知不完美（刻意接受）：**

- Task 14 的 DTO 在 Task 13 就會被用到，兩個任務實務上要一起做完才編譯得過。分開列是為了讓改動範圍清楚。
- Task 15 的 HTML 與 CSS 沒有給完整程式碼，只列出必要元素與狀態。前端樣式屬於既有設計的延伸，照現有 `translation.html` 的風格寫即可。

