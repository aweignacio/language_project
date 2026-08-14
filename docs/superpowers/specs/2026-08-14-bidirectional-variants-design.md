# 雙向翻譯與多重說法 — 設計規格

- **文件日期**：2026-08-14
- **專案路徑**：`C:\Tim\language_project`
- **狀態**：已確認，待排實作計畫
- **前一份規格**：`2026-08-11-thai-learning-design.md`

---

## 1. 這次要解決什麼

### 問題一：一個中文詞在泰文有多種說法，現在只看得到一種

查「我」只會回傳一個泰文，但泰文的「我」至少有 `ผม`（男性、禮貌）、`ฉัน`（女性）、`กู`（不分性別、粗俗）等多種說法。使用者看不到其他選項，等於學不到泰文最基本的人稱區別。

### 問題二：整句翻譯沒有性別概念，男性使用者拿到女性的講法

泰文的自稱與句尾助詞都分性別（男 `ครับ`、女 `ค่ะ`）。目前系統不知道使用者是誰，翻「我想喝酒」固定給 `ฉันอยากดื่มเหล้า`，對男性使用者是錯的講法。

### 問題三：只能中翻泰，不能泰翻中

看到泰文招牌、訊息、菜單時無法查詢。

### 問題四：只有整句有音檔，逐詞沒有

逐詞對照看得到拼音卻聽不到，而泰文是聲調語言，拼音符號無法取代實際發音。

---

## 2. 範圍

### 這次要做

| 項目 | 說明 |
|---|---|
| 單字的多重說法 | 查單一個詞時列出所有說法，每個附【適用性別】【禮貌程度】【中文說明】 |
| 說話者性別 | 畫面上可切換男／女，影響整句翻譯的自稱與句尾助詞 |
| 泰翻中 | 輸入泰文，回傳中文、逐詞拆解 |
| 逐詞音檔 | 逐詞對照每個詞都可發音，點擊時才合成 |
| 中文音檔 | 輸入泰文時自動產生（中文是翻譯結果）；輸入中文時不產生 |
| 音檔全站共用 | 同一段文字全站只合成一次 |

### 這次不做

| 項目 | 原因 |
|---|---|
| 句子裡的逐詞也展開多重說法 | 句子裡的詞已被語境決定，價值低於查單字時；且資料量與成本會膨脹 |
| 羅馬拼音輸入（`pom yak duem lao`） | 拼音無標準寫法，歧義高，AI 猜錯就翻錯 |
| 中翻泰時也產生中文音檔 | 那時中文是使用者自己剛打的字，唸給他聽沒有價值，卻要多打一次 OpenAI |
| 會員 / 收藏 / 學習紀錄 | 沿用前一份規格的判斷，仍不做 |

---

## 3. 決策紀錄

實作時如果覺得某個決定「好像可以更好」，請先讀這裡的理由再判斷。

| # | 決定 | 理由 |
|---|---|---|
| 1 | 多重說法只在「整段輸入就是一個詞」時提供 | 句子裡的詞已被語境決定，展開的成本高、價值低 |
| 2 | 性別由前端切換，不是後端設定 | 使用者可能借給他人使用 |
| 3 | 說法全部列出，符合使用者性別的排最前並標記 | 過濾掉異性的說法會讓使用者聽不懂對方講話。學習用途下，看得到比看得少重要 |
| 4 | 每個說法查詢當下就產生泰文音檔 | `ผม` / `กู` 的差別在語感，只看拼音感受不到。語音成本僅佔翻譯成本約 1.3% |
| 5 | 逐詞音檔點擊時才產生 | 每句 4～5 個詞若一次合成，每次查詢多等 4～8 秒。錢不是問題，等待才是 |
| 6 | 音檔以「文字內容 + 語言」為鍵全站共用 | 使用越久覆蓋率越高，同一段文字永遠只付一次錢 |
| 7 | 整句快取分性別，單字快取不分 | 單字查詢一次就把男女兩種說法都拿回來了，再分一次是重複付費 |
| 8 | 一個詞最多 5 個說法 | 泰文人稱代詞通常 3～5 個。上限是保險，真正防編造的是提示詞那句「寧可少給不要硬湊」 |
| 9 | 移除 `TranslationService.resolveTranslation()` 的單字庫捷徑 | 它一輩子只省一次呼叫（約 NT$0.25），代價卻是讓多重說法功能失效。見第 7 節 |
| 10 | 用單一 `vocabulary` 表容納多重說法，不拆父子表 | 實際有多重說法的只有人稱代詞與句尾助詞等十來個詞，拆表的複雜度不划算 |
| 11 | 泰文輸入只接受泰文字，不接受羅馬拼音 | 見「這次不做」 |
| 12 | 泰翻中不分性別 | 中文沒有對應的性別語法區別 |
| 13 | 句尾助詞照樣拆解、照樣沉澱進單字庫 | `ครับ` / `ค่ะ` 是泰文最高頻的字。因為「翻不出中文」就藏起來，等於把最該學的東西擋掉 |
| 14 | **中文音檔只在「輸入泰文」時自動產生** | 泰翻中時中文是翻譯結果，使用者需要聽它確認自己理解得對不對；中翻泰時中文是他自己打的字，唸給他聽沒有價值。★ 2026-08-14 修正：原本寫成「一律不自動產生」，與實際需求不符 |
| 15 | 既有資料全部清除 | 資料量僅 5 個音檔，重查成本約新台幣數元。保留舊資料要在程式裡永久留下「這是舊資料」的判斷 |

---

## 4. 資料表變更

### 4.1 執行方式

既有資料全部清除（決策 15），因此**不使用 `ALTER TABLE`**，直接改寫 `db/schema.sql` 並重建。

```sql
-- 刪除順序：有外鍵的先刪
DROP TABLE IF EXISTS dbo.translation_segment;
DROP TABLE IF EXISTS dbo.translation_query;
DROP TABLE IF EXISTS dbo.vocabulary;
DROP TABLE IF EXISTS dbo.audio_asset;
-- api_usage_log 保留，那是花錢的稽核紀錄
```

再刪除 `audio/` 底下所有 mp3，建立 `audio/th/` 與 `audio/zh/` 兩個子資料夾。

> ⚠ 這段 SQL 由 Awei 自己執行，實作時不代為執行。

### 4.2 `translation_query` — 加方向與性別

```sql
CREATE TABLE dbo.translation_query
(
    id            BIGINT IDENTITY(1,1) NOT NULL,

    -- 使用者實際輸入的原文，前後空白已去除。快取的鑰匙。
    source_text   NVARCHAR(100)        NOT NULL,

    -- TranslationDirectionEnum：ZH_TO_TH / TH_TO_ZH
    direction     VARCHAR(20)          NOT NULL,

    -- SpeakerGenderEnum：MALE / FEMALE。
    -- 泰翻中沒有性別概念，該方向一律為 NULL。
    gender        VARCHAR(10)          NULL,

    -- 這句話的中文面與泰文面。★ source_text 必定與其中一面相同，
    -- 這份重複是刻意的：source_text 專職當快取的鑰匙，
    -- 另外兩欄專職表示「這句話的兩面」。混用會讓程式每次都要先判斷方向
    -- 才知道哪個欄位裝什麼。
    chinese_text  NVARCHAR(500)        NOT NULL,
    thai_text     NVARCHAR(500)        NOT NULL,

    -- 泰文的羅馬拼音（含聲調符號）
    romanization  NVARCHAR(500)        NOT NULL,

    created_at    DATETIME2            NOT NULL
        CONSTRAINT DF_translation_query_created_at DEFAULT SYSDATETIME(),
    updated_at    DATETIME2            NOT NULL
        CONSTRAINT DF_translation_query_updated_at DEFAULT SYSDATETIME(),

    CONSTRAINT PK_translation_query PRIMARY KEY (id),

    -- ★ SQL Server 的 UNIQUE 把 NULL 當成一個值來比對，
    --   所以「同一句泰文（gender 為 NULL）只會有一筆」仍然成立，不需額外處理。
    CONSTRAINT UQ_translation_query_key
        UNIQUE (source_text, direction, gender),

    CONSTRAINT CK_translation_query_direction
        CHECK (direction IN ('ZH_TO_TH', 'TH_TO_ZH')),

    CONSTRAINT CK_translation_query_gender
        CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE'))
);
```

**移除的欄位**：`audio_file`。音檔改由 `audio_asset` 統一管理（決策 6），留著會變成兩個地方都聲稱自己有音檔。

**兩個方向的實際資料：**

```
輸入「我想喝酒」，性別男
  source_text  = 我想喝酒
  direction    = ZH_TO_TH
  gender       = MALE
  chinese_text = 我想喝酒
  thai_text    = ผมอยากดื่มเหล้าครับ
  romanization = pǒm yàak dùuem lâo khráp

輸入「ผมอยากดื่มเหล้าครับ」
  source_text  = ผมอยากดื่มเหล้าครับ
  direction    = TH_TO_ZH
  gender       = NULL
  chinese_text = 我想喝酒
  thai_text    = ผมอยากดื่มเหล้าครับ
  romanization = pǒm yàak dùuem lâo khráp
```

### 4.3 `translation_segment` — 結構不變

現有的 `chinese_text` / `thai_text` / `romanization` 三欄兩個方向共用，**不需異動**。

句尾助詞的 `chinese_text` 填括號標籤，例如 `（男性禮貌語助詞）`。

### 4.4 `vocabulary` — 改唯一鍵，加三欄

```sql
CREATE TABLE dbo.vocabulary
(
    id            BIGINT IDENTITY(1,1) NOT NULL,

    chinese_text  NVARCHAR(50)         NOT NULL,
    thai_text     NVARCHAR(100)        NOT NULL,
    romanization  NVARCHAR(100)        NOT NULL,

    -- GenderUsageEnum：MALE / FEMALE / BOTH。這個「說法」適合誰用。
    -- ★ 與 translation_query.gender 是不同的概念：
    --   那個是「使用者是誰」，這個是「這個說法適合誰」，且只有這裡才有 BOTH。
    -- 從句子拆解沉澱下來的詞沒有這項資訊，為 NULL。
    gender_usage  VARCHAR(10)          NULL,

    -- PolitenessEnum：FORMAL / NEUTRAL / CASUAL / RUDE
    politeness    VARCHAR(10)          NULL,

    -- 中文說明，例如「男生自稱，正式或對不熟的人使用」
    note          NVARCHAR(200)        NULL,

    -- VocabularySourceTypeEnum：SEGMENT / DIRECT。沿用既有定義。
    source_type   VARCHAR(20)          NOT NULL,

    created_at    DATETIME2            NOT NULL
        CONSTRAINT DF_vocabulary_created_at DEFAULT SYSDATETIME(),
    updated_at    DATETIME2            NOT NULL
        CONSTRAINT DF_vocabulary_updated_at DEFAULT SYSDATETIME(),

    CONSTRAINT PK_vocabulary PRIMARY KEY (id),

    -- ★ 從「一個中文詞一列」改成「一個說法一列」。
    --   「我」會佔 ผม / ฉัน / กู 三列，這是預期行為。
    CONSTRAINT UQ_vocabulary_chinese_thai UNIQUE (chinese_text, thai_text),

    CONSTRAINT CK_vocabulary_source_type
        CHECK (source_type IN ('SEGMENT', 'DIRECT')),

    CONSTRAINT CK_vocabulary_gender_usage
        CHECK (gender_usage IS NULL OR gender_usage IN ('MALE', 'FEMALE', 'BOTH')),

    CONSTRAINT CK_vocabulary_politeness
        CHECK (politeness IS NULL OR politeness IN ('FORMAL', 'NEUTRAL', 'CASUAL', 'RUDE'))
);
```

**副作用（已接受）**：單字列表頁會出現同一個中文詞連續多列，且分頁時可能被切開。實際會多列的只有人稱代詞與句尾助詞等十來個詞，不特別處理。

**寫入時的合併規則**（★ 這段一定要照做，否則沉澱過的詞永遠補不齊）：

同一組 `(chinese_text, thai_text)` 已存在時：

| 欄位 | 處理 |
|---|---|
| `gender_usage` / `politeness` / `note` | 原本是 `NULL` 且新資料有值 → **補上**；原本有值 → 不覆蓋 |
| `source_type` | 維持首次寫入的值，永不更新（沿用既有規則） |
| `romanization` | 不覆蓋 |

情境：先查過「我想喝酒」，`我 → ฉัน` 以 `SEGMENT` 沉澱且三個新欄位都是 `NULL`。之後單獨查「我」拿到完整說法時，`ฉัน` 那一列的三個欄位會被補齊，`source_type` 仍是 `SEGMENT`。

### 4.5 `audio_asset` — 新表

```sql
CREATE TABLE dbo.audio_asset
(
    id          BIGINT IDENTITY(1,1) NOT NULL,

    -- 要唸出來的文字。可能短到一個詞，長到一整句。
    speech_text NVARCHAR(500)        NOT NULL,

    -- SpeechLanguageEnum：TH / ZH
    language    VARCHAR(10)          NOT NULL,

    -- 相對於 audio 資料夾的路徑，例如 th/a1b2c3d4e5f6.mp3。
    -- 系統產生的 ASCII 字串，故用 VARCHAR。
    file_path   VARCHAR(100)         NOT NULL,

    created_at  DATETIME2            NOT NULL
        CONSTRAINT DF_audio_asset_created_at DEFAULT SYSDATETIME(),

    CONSTRAINT PK_audio_asset PRIMARY KEY (id),

    -- ★ 這條唯一鍵就是「同一段文字全站只合成一次」的保證。
    CONSTRAINT UQ_audio_asset_text_language UNIQUE (speech_text, language),

    CONSTRAINT CK_audio_asset_language CHECK (language IN ('TH', 'ZH'))
);
```

### 4.6 `api_usage_log` — 不變

---

## 5. 新增的 Enum

放在 `enums/` 底下，照專案規範以 `Enum` 結尾、標 `@Getter`、附中文說明欄位。

| Enum | 值 | 用途 |
|---|---|---|
| `TranslationDirectionEnum` | `ZH_TO_TH` / `TH_TO_ZH` | 翻譯方向 |
| `SpeakerGenderEnum` | `MALE` / `FEMALE` | 使用者是誰 |
| `GenderUsageEnum` | `MALE` / `FEMALE` / `BOTH` | 某個說法適合誰用 |
| `PolitenessEnum` | `FORMAL` / `NEUTRAL` / `CASUAL` / `RUDE` | 禮貌程度 |
| `SpeechLanguageEnum` | `TH` / `ZH` | 音檔語言，同時決定子資料夾 |

`SpeakerGenderEnum` 與 `GenderUsageEnum` 刻意不共用：前者描述使用者，後者描述詞條，且 `BOTH` 只在後者有意義。

`ErrorCodeEnum` 需新增：
- `SPEECH_TEXT_UNKNOWN` — 要求合成的文字不在資料庫裡（見 9.2）

**方向判斷規則（★ 不可改成「兩者皆非就報錯」）：**

```
輸入含泰文字（U+0E00–U+0E7F） → TH_TO_ZH
其餘一律                        → ZH_TO_TH
```

★ 「其餘一律」包含純數字。現有提示詞明確支援數字輸入（`OpenAiTranslationClient.java:227`：「包含數字，例如『5』就是『ห้า』」），若改成「不是中文也不是泰文就報錯」，「5」這種現在可用的輸入會壞掉。

亂碼（`asdfgh`）照樣走 `ZH_TO_TH`，由既有的 `translatable` 判斷擋下，行為與現在完全一致。

---

## 6. 音檔存放

```
audio/
  th/   泰文發音
  zh/   中文發音
```

`WebMvcConfig` **不需異動**。現有的 `/audio/**` 萬用字元本來就吃得下子路徑，`/audio/th/a1b2c3.mp3` 會自動對應到 `audio/th/a1b2c3.mp3`。

`AudioStorageProperties` 新增一個設定，控制哪些語言的整句音檔要自動產生：

```yaml
audio:
  storage:
    directory: audio
  # 查詢完成時自動產生哪些語言的音檔（涵蓋整句與多重說法）。
  #
  # 這是總開關，還有第二道規則寫在 TranslationService.autoGenerateAudio：
  # ★ 中文只有在「使用者輸入泰文」時才產生（決策 14）。
  auto-generate: [TH, ZH]
```

★ 多重說法的音檔一律是泰文，所以 `TH` 在清單裡時才會自動產生（決策 4）。不在清單裡時，說法的 `thaiAudioUrl` 為 `null`，改由使用者點擊產生。

---

## 7. 移除單字庫捷徑（決策 9）

`TranslationService.java:246` 目前有這段：

```java
private TranslationResult resolveTranslation(String sourceText) {
    Optional<VocabularyDto> knownWord = vocabularyRepository.findByChineseText(sourceText);
    ...
}
```

**整段移除。** 理由：

```
第 1 天  查「我想喝酒」→ 逐詞的「我」沉澱進單字庫，只有 ฉัน 這一個
                        （翻句子時系統只知道「這句話裡的我是 ฉัน」，
                          它從來沒問過「我總共有幾種說法」）

第 3 天  單獨查「我」  → 捷徑看到單字庫有「我」，直接回傳 ฉัน
                        → 永遠不會去問 AI 要 ผม 和 กู
                        → 多重說法功能等於沒作用
```

移除後省錢能力沒有損失：`translation_query` 那層快取本來就擋住重複查詢。捷徑真正省到的只有「先查過含該詞的句子，再第一次單獨查該詞」這一種情況，一輩子每個詞只省一次呼叫，約新台幣 0.25 元。

`vocabulary` 表因此變成**純粹的學習資產**（給單字列表頁瀏覽），不再兼任省錢用的快取。這個角色轉換要寫進該表與 `VocabularyService` 的註解。

---

## 8. 提示詞

拆成兩套，依方向選用。

### 8.1 中翻泰（`ZH_TO_TH`）

在現有 `SYSTEM_PROMPT` 基礎上新增兩段。**現有的內容全部保留**，特別是 `romanization` 不可填漢語拼音那段，以及 `translatable` 的規則。

**新增段落一：性別**

```
使用者的性別會隨每次請求傳入。造句時請遵守：
- 男性：自稱用 ผม，句尾禮貌助詞用 ครับ
- 女性：自稱用 ฉัน 或 ดิฉัน，句尾禮貌助詞用 ค่ะ
```

**新增段落二：多重說法**

```
如果整段輸入本身就是一個詞（也就是 words 只有一個元素），
請額外回傳 variants，列出這個詞在泰文的各種說法。

每個說法要給：
  thaiText      泰文
  romanization  羅馬拼音（含聲調符號）
  genderUsage   MALE / FEMALE / BOTH ——「哪種性別的人會這樣說」，
                不分性別就填 BOTH
  politeness    FORMAL / NEUTRAL / CASUAL / RUDE
  note          一句中文說明，講清楚什麼場合用、對誰用會失禮

規則（很重要）：
- 最多 5 個
- ★ 寧可只給一個，也不要為了看起來豐富而硬湊。
  大部分的詞就只有一種說法，這很正常，誠實回報即可。
  使用者是學習者，一個編造出來的說法會被他背起來。
- 不同的說法泰文必須真的不同。不可以同一個泰文換個拼音寫法充數。
- 輸入不是單一個詞時，variants 給空陣列。
```

### 8.2 泰翻中（`TH_TO_ZH`）

全新的提示詞。

```
你是泰文轉中文的翻譯助理，服務對象是正在學泰文的中文使用者。

收到一段泰文後，請回傳：
1. 對應的繁體中文
2. 「輸入那段泰文」的羅馬拼音，需標註聲調符號
3. 逐詞對照：把泰文依語意切成詞，每個詞給出泰文、羅馬拼音、中文意思

★ 泰文書寫時詞與詞之間沒有空格，切詞是這項工作最重要的部分。

句尾助詞的處理（不要省略）：
- ครับ、ค่ะ、นะ、จ๊ะ 這類助詞沒有對應的中文詞，但一定要列進逐詞對照
- 它們的 chineseText 請填一個括號標籤，例如「（男性禮貌語助詞）」
- note 欄位再補一句說明用法
- ★ 不可以因為「翻不出中文」就把它從逐詞對照裡拿掉。
  這些是泰文最高頻的字，使用者正需要知道它們在做什麼。

translatable 的規則：
- 輸入是有意義、看得懂的泰文 → true
- 輸入是亂碼或你無法確定意思 → false，其餘欄位留空
- 寧可誠實回報 false，也不要硬湊一個看起來合理的答案

這個方向不需要 variants，一律回空陣列。
```

---

## 9. API

### 9.1 翻譯

```
POST /api/v1/translations
{
  "sourceText": "我",
  "gender": "MALE"
}
```

沿用既有的 `/api/v1/` 前綴與 `sourceText` 欄位名，只新增 `gender`。

`gender` 一律傳送。輸入是泰文時後端忽略它並存成 NULL。

**回應：**

```json
{
  "sourceText": "我",
  "direction": "ZH_TO_TH",
  "gender": "MALE",
  "chineseText": "我",
  "thaiText": "ผม",
  "romanization": "pǒm",
  "thaiAudioUrl": "/audio/th/a1b2c3.mp3",
  "chineseAudioUrl": null,
  "fromCache": false,
  "segments": [
    {
      "seqNo": 1,
      "chineseText": "我",
      "thaiText": "ผม",
      "romanization": "pǒm",
      "thaiAudioUrl": "/audio/th/a1b2c3.mp3",
      "chineseAudioUrl": null
    }
  ],
  "variants": [
    {
      "thaiText": "ผม",
      "romanization": "pǒm",
      "genderUsage": "MALE",
      "politeness": "FORMAL",
      "note": "男生自稱，正式或對不熟的人使用",
      "thaiAudioUrl": "/audio/th/a1b2c3.mp3"
    },
    {
      "thaiText": "ฉัน",
      "romanization": "chǎn",
      "genderUsage": "FEMALE",
      "politeness": "FORMAL",
      "note": "女生自稱",
      "thaiAudioUrl": "/audio/th/d4e5f6.mp3"
    },
    {
      "thaiText": "กู",
      "romanization": "guu",
      "genderUsage": "BOTH",
      "politeness": "RUDE",
      "note": "很不客氣，只能對很熟的朋友使用，對長輩用會失禮",
      "thaiAudioUrl": "/audio/th/g7h8i9.mp3"
    }
  ]
}
```

`variants` 在句子查詢時是空陣列。`chineseAudioUrl` 在泰翻中時會有值（決策 14），中翻泰時為 `null`。

### 9.2 合成音檔

```
POST /api/v1/audio
{
  "speechText": "เหล้า",
  "language": "TH"
}

→ { "audioUrl": "/audio/th/j1k2l3.mp3" }
```

後端流程：

1. **驗證這段文字存在於資料庫**（`translation_segment`、`translation_query` 或 `vocabulary` 找得到）。找不到丟 `SPEECH_TEXT_UNKNOWN`。
   > ★ 這一步是防護，不是效能考量。這支 API 會花錢，不擋的話任何人送任意文字進來都能燒掉帳戶餘額。
2. 查 `audio_asset`，命中就直接回傳，**不花錢**。
3. 未命中才呼叫 TTS，寫入 `audio_asset`，回傳。

---

## 10. 程式異動清單

### 新增

| 檔案 | 說明 | 需要流程註解 |
|---|---|---|
| `enums/TranslationDirectionEnum.java` | | 否 |
| `enums/SpeakerGenderEnum.java` | | 否 |
| `enums/GenderUsageEnum.java` | | 否 |
| `enums/PolitenessEnum.java` | | 否 |
| `enums/SpeechLanguageEnum.java` | | 否 |
| `entity/AudioAsset.java` | | 否 |
| `repository/AudioAssetRepository.java` | | 否 |
| `service/AudioAssetService.java` | 「查表 → 命中就回 → 未命中才合成」的守門人 | **是** |
| `controller/AudioController.java` | `POST /api/v1/audio` | **是** |
| `dto/request/AudioRequestDto.java` | | 否 |
| `dto/response/AudioResponseDto.java` | | 否 |
| `dto/response/VocabularyVariantDto.java` | | 否 |
| `client/model/TranslationVariant.java` | | 否 |
| `service/LanguageDetector.java` | 依字元範圍判斷翻譯方向 | **是** |

### 修改

| 檔案 | 改什麼 |
|---|---|
| `db/schema.sql` | 依第 4 節重寫 |
| `client/TranslationClient.java` | `translate(String, TranslationDirectionEnum, SpeakerGenderEnum)` |
| `client/openai/OpenAiTranslationClient.java` | 兩套提示詞、解析 `variants`、去重、上限檢查 |
| `client/SpeechClient.java` | `synthesize(String, SpeechLanguageEnum)` |
| `client/openai/OpenAiSpeechClient.java` | 依語言寫入 `th/` 或 `zh/` 子資料夾 |
| `service/TranslationService.java` | 方向判斷、性別傳遞、**移除單字庫捷徑**、組裝 variants |
| `service/TranslationPersistenceService.java` | 寫入 direction / gender / chinese_text、寫入多筆 vocabulary |
| `service/VocabularyService.java` | 註解更新：這張表不再是快取，是學習資產 |
| `entity/TranslationQuery.java` | 新欄位，移除 `audioFile` |
| `entity/Vocabulary.java` | 新欄位 |
| `repository/TranslationQueryRepository.java` | 依 `(source_text, direction, gender)` 查詢 |
| `repository/VocabularyRepository.java` | `findByChineseText` 改回傳 `List` |
| `dto/**` | 依第 9 節調整 |
| `config/AudioStorageProperties.java` | 新增 `autoGenerate` |
| `enums/ErrorCodeEnum.java` | 兩個新錯誤碼 |

**`config/WebMvcConfig.java` 不需異動**（`/audio/**` 已支援子路徑）。

---

## 11. 流程

### 11.1 查單字（中翻泰）

```
1. 前端送 { text: "我", gender: "MALE" }

2. LanguageDetector 看字元範圍
     含泰文（U+0E00–U+0E7F）→ direction = TH_TO_ZH
     其餘一律                → direction = ZH_TO_TH（含純數字，見第 5 節）

3. 找 translation_query where (我, ZH_TO_TH, MALE)
     命中 → 直接組回應，0 元
     未命中 → 往下

4. 呼叫 AI（★ 只有一次呼叫，翻譯與多重說法一起拿）
     回傳 thaiText / romanization / words[1] / variants[3] / translatable

5. 驗證
     translatable = false      → INPUT_UNSUPPORTED_CONTENT，當場停手
     泰文欄位混有中文字         → TRANSLATION_RESPONSE_INVALID
                                 ★ 現有的 containsChinese() 只檢查 thaiText 與 words，
                                   必須擴充到 variants，否則污染會從新的路徑漏進單字庫
     variants 超過 5 個         → 只取前 5 個
     variants 泰文重複          → 去重，保留第一筆
     variants 欄位殘缺           → 該筆丟棄，其餘保留
                                 （少一個說法不影響使用，但殘缺的資料存進去會一直錯下去）

6. 產生音檔（都走 AudioAssetService，命中就不花錢）
     整句泰文 ผม
     每個說法的泰文 ผม / ฉัน / กู
     ★ 此例中「整句」與「第一個說法」是同一段文字，
       靠 audio_asset 的唯一鍵自動共用，只會產生 3 個檔案

7. 寫入（單一交易）
     translation_query  一筆
     translation_segment 一筆
     vocabulary          三筆，source_type = DIRECT

8. 回應
```

### 11.2 查句子（中翻泰）

與 11.1 相同，差別只在：

- 第 4 步 `words` 長度大於 1、`variants` 為空陣列
- 第 6 步只產生整句泰文一個音檔
- 第 7 步 `translation_segment` 寫多筆、`vocabulary` 依逐詞寫入，`source_type = SEGMENT`，且 `gender_usage` / `politeness` / `note` 留 NULL

### 11.3 泰翻中

```
1. 前端送 { text: "ผมอยากดื่มเหล้าครับ", gender: "MALE" }
2. LanguageDetector 判定 TH_TO_ZH，★ gender 忽略，存 NULL
3. 找 translation_query where (那段泰文, TH_TO_ZH, NULL)
4. 未命中才呼叫 AI，用 8.2 的提示詞
5. 產生整句泰文音檔（就是使用者輸入的那段）
6. 寫入，逐詞包含 ครับ（chinese_text = 「（男性禮貌語助詞）」）
7. 回應
```

### 11.4 點擊逐詞音檔

```
1. 前端逐詞每行一個播放鍵
     thaiAudioUrl 有值 → 亮的，直接播
     為 null           → 灰的，點了才生

2. 點擊 → POST /api/audio { speechText, language }
3. 後端驗證 → 查 audio_asset → 未命中才合成 → 寫入 → 回 url
4. 前端把該行的播放鍵換成亮的並播放
```

---

## 12. 前端

| 項目 | 說明 |
|---|---|
| 性別切換 | 男／女，存 `localStorage`，重新開啟記得上次的選擇 |
| 切換時重查 | 畫面上有結果時切換性別，自動重新查詢一次 |
| 方向 | 不做切換鈕，由後端依輸入自動判斷 |
| 說法排序 | ① `genderUsage` 符合使用者的排前面（`BOTH` 視為符合）② 再依 `politeness`：FORMAL → NEUTRAL → CASUAL → RUDE ③ 最後依回傳順序 |
| 「適合你」標記 | 排序後第一個且 `genderUsage` 相符者標上 ★ |
| 禮貌程度標籤 | 顯眼呈現。`RUDE` 用警示色 —— 用錯場合的後果是冒犯人，不是講得不夠好 |
| 逐詞播放鍵 | 兩種狀態（已有／未生成），未生成點擊後顯示載入中 |
| 單字列表頁 | 同一個中文詞出現多列，不特別處理 |

---

## 13. 測試

沿用既有做法：外部呼叫全部換成假的，測試不花錢。所有測試檔都要有流程註解，說明哪些東西被換成假的、每個測試各自在防什麼。

| 測試檔 | 要防的事 |
|---|---|
| `OpenAiTranslationClientTest` | variants 正確解析；超過 5 個被截斷；泰文重複被去重；性別有寫進提示詞；兩套提示詞依方向選對；`translatable = false` 照樣擋下 |
| `OpenAiSpeechClientTest` | `TH` 寫進 `th/`、`ZH` 寫進 `zh/`；合成失敗仍回空 Optional 不影響翻譯 |
| `AudioAssetServiceTest` | 已存在的文字不重複合成（★ 這是決策 6 的核心，壞掉會默默一直花錢）；未命中才呼叫 TTS |
| `AudioControllerTest` | 資料庫裡沒有的文字被擋下並回 `SPEECH_TEXT_UNKNOWN`（★ 這是防止帳戶被燒的那道關卡） |
| `LanguageDetectorTest` | 純中文 → ZH_TO_TH；純泰文 → TH_TO_ZH；中泰混合 → TH_TO_ZH；**純數字「5」→ ZH_TO_TH**（★ 防止數字輸入這個現有功能被判斷邏輯弄壞）；亂碼 `asdfgh` → ZH_TO_TH |
| `TranslationServiceTest` | 同一句話男女各存一筆；同一個單字男女共用一筆（★ 決策 7，壞掉會重複付費）；**單字庫裡已有「我 → ฉัน」時，單獨查「我」仍必須呼叫 AI**（★ 決策 9，這是捷徑真的被移除的證明，也是整個多重說法功能的命脈）；泰翻中的 gender 存成 NULL |
| `TranslationQueryRepositoryTest` | 新唯一鍵；gender 為 NULL 時仍然唯一 |
| `VocabularyRepositoryTest` | 同一中文詞可存多列；同一組中文＋泰文不可重複 |

---

## 14. 已知取捨

| 取捨 | 說明 |
|---|---|
| 單字列表頁會有重複的中文詞 | 決策 10 的代價。實際受影響的約十來個詞 |
| `source_text` 與 `chinese_text`／`thai_text` 其中一欄重複 | 刻意的。換取「不必先判斷方向才知道欄位裝什麼」 |
| 每個詞第一次單獨查詢一定要付費 | 決策 9 的代價，約 NT$0.25，換掉一整套繞道機制 |
| 中翻泰時中文那面沒有音檔 | 決策 14。畫面上不顯示中文播放鍵，因為那是使用者自己打的字 |
| 改版前就存在的泰文快取，中文音檔是空的 | 快取命中時只查現成的、不合成（否則 `fromCache: true` 卻偷偷花錢）。點一下就會補生 |
| 同一句話男女版各付一次錢 | 泰文本身真的不同，省不掉。但是「用到才生」，不切換就不會產生 |

---

## 15. 未來可能（這次不做）

- 句子裡的逐詞也能展開多重說法
- 羅馬拼音輸入
- 依禮貌程度過濾單字列表（只看正式用語 / 只看口語）

---

## 16. 實測結果（2026-08-14）

實際啟動後端（`local` profile，跑在 8081 以免撞到已在 8080 執行的舊版）並用真實
金鑰打過一輪。以下是逐項結果，**沒過的也列出來**。

### 過了的項目

| # | 驗證項目 | 實際結果 |
|---|---|---|
| 1 | 查「我」出現多重說法 | ผม / กระผม / ฉัน / ดิฉัน / เรา / กู，各帶性別、禮貌、中文說明 |
| 5 | 「我想喝酒」男 | `ผมอยากดื่มเหล้า`（自稱 ผม） |
| 6 | 「我想喝酒」女 | `ฉันอยากดื่มเหล้า`（自稱 ฉัน），與男版各存一筆 |
| 8 | 點逐詞的灰色播放鍵 | `POST /api/v1/audio` 回 200 與音檔網址 |
| 9 | 同一個詞再點一次 | 回傳「完全相同」的檔名，沒有重新合成 |
| 10 | 貼上泰文 `ผมอยากดื่มเหล้าครับ` | 判為 TH_TO_ZH、gender 存 NULL、中文「我想喝酒」，且 `ครับ` 有留在逐詞裡並標成「（男性禮貌語助詞）」 |
| 11 | 查「5」 | 翻成 `ห้า`，沒有被方向判斷擋下來 |
| 12 | 查「asdfgh」 | 回 `INPUT_UNSUPPORTED_CONTENT`，資料庫沒有新增任何一筆 |
| 13 | 單字列表的「我」 | 6 列，各自帶標籤 |
| 14 | `audio/` 資料夾 | `th/` 17 個檔、`zh/` 0 個、根目錄沒有散落的 mp3 |
| 15 | `audio_asset` | 「同一段文字＋同一語言」重複的列數為 **0** |
| — | 未知文字的合成請求 | 回 400 `SPEECH_TEXT_UNKNOWN`，守門檢查確實擋住了花錢的路徑 |

### ★ 最重要的一項：單字庫捷徑真的不見了

照第 16 章步驟 4 實測：

1. 先查「你好嗎」→ 逐詞的「你 → คุณ」被沉澱進單字庫（三個標籤都是 NULL）
2. 再單獨查「你」→ **仍然呼叫了 AI**，回傳 คุณ / เธอ / ท่าน / แก / มึง 五種說法
3. 回頭看單字庫：原本那筆「你 → คุณ」的標籤已被補成 `BOTH / NEUTRAL` 與完整說明，
   而同一句沉澱下來的「好」「嗎」仍然是 NULL（它們沒有被單獨查過）

**決策 9（移除捷徑）與 4.4 的合併規則兩者都在正式環境下確認可用。**

### ✗ 沒過的一項：切換性別時，單字也會重新呼叫一次 AI

| # | 預期 | 實際 |
|---|---|---|
| 3 / 4 | 查完「我」（男）再切到女，**不應**呼叫 OpenAI（決策 7：單字不分性別） | `fromCache: false`，**有**呼叫 OpenAI |

原因不是寫錯，是規格本身有矛盾：

- 決策 7 說「單字不分性別，男女共用一筆」
- 但第 4.2 節的唯一鍵是 `(source_text, direction, gender)`，中翻泰一律帶性別

**而且這個矛盾解不掉：** 快取是在呼叫 AI「之前」查的，那時我們還不知道使用者輸入的
是一個詞還是一句話 —— 「是不是單一個詞」要等 AI 回傳 `words` 才知道。所以沒辦法在
查快取的當下決定「這次要不要把 gender 放進鑰匙」。

代價很小：每個單字在每個性別各付一次錢（約 NT$0.25），之後兩邊都命中快取。

三個可以選的方向，**建議先維持現狀**：

1. **維持現狀** —— 每個單字最多付兩次。實作最單純，也不會有下面兩個的副作用。
2. 先用 `LanguageDetector` 之外再加一個「看起來像單一個詞」的猜測（例如長度 ≤ 3 字），
   猜中就不帶 gender 查快取。缺點是猜錯會讓短句的男女版互相蓋掉，那是使用者看得到的錯。
3. 查快取時中翻泰一律先用 `gender = NULL` 再查一次帶性別的。缺點是每次查詢多一次
   資料庫往返，而且要處理「同一句話同時存在兩種鑰匙」的資料清理問題。

### 需要 Awei 自己確認的項目

以下三項機器沒辦法代勞：

| # | 項目 | 為什麼要人來 |
|---|---|---|
| 2 | 三個說法都能播，且聽得出 `ผม` 與 `กู` 語感不同 | 要用耳朵聽 |
| 7 | 逐詞播放鍵預設是灰的 | 要用眼睛看畫面 |
| — | 性別切換鈕、警示色、「★ 適合你」標記的排版 | 同上 |

### 順帶觀察到的兩件事

- 模型這次造句「沒有」加句尾助詞 `ครับ`／`ค่ะ`（`ผมอยากดื่มเหล้า` 而不是
  `ผมอยากดื่มเหล้าครับ`）。提示詞有寫要加，模型自行判斷這句不需要。
  這不影響正確性，但如果 Awei 想要句尾一定有助詞，要把提示詞那一條寫得更硬。
- 逐詞的「我 → ผม」在查「我想喝酒」時就已經是亮的了 —— 因為稍早查「我」時
  合成過同一段泰文。**`audio_asset` 的跨查詢共用在實際使用中確實有效。**

---

## 17. 決策 14 的修正（2026-08-14，Awei 指正）

第 16 章實測完之後，Awei 指出規格寫錯了：

> 我輸入泰文的時候，要有中文音檔啊

原本的決策 14 寫成「中文音檔一律不自動產生，只在點擊時合成」，那是把兩種情況
混在一起了。分開看就很清楚：

| 你輸入 | 中文那面是什麼 | 要不要音檔 |
|---|---|---|
| 中文「我想喝酒」 | **你自己剛打的字** | 不要。唸你剛打的字給你聽沒有價值，還要多等一兩秒 |
| 泰文「ผม」 | **翻譯結果** | 要。你貼一段看不懂的泰文進來，要聽中文確認自己理解得對 |

泰文則兩個方向都照生 —— 那是你要學的那一面。

### 改了哪裡

| 檔案 | 改動 |
|---|---|
| `TranslationService.autoGenerateAudio` | 多收一個 `direction`，中文只在 `TH_TO_ZH` 時才生 |
| `AudioStorageProperties` / `application.yml` | `auto-generate` 改成 `[TH, ZH]`（總開關打開，方向規則在程式裡） |
| `translation.html` / `.ts` / `.css` | 中文那行加一顆播放鍵（泰翻中時才顯示） |
| `TranslationServiceTest` | 原本的「不得自動產生中文音檔」縮小成「輸入中文時不得產生」，另加一條「輸入泰文時應產生」 |

### 實測（改完之後）

| 輸入 | direction | thaiAudioUrl | chineseAudioUrl |
|---|---|---|---|
| `ขอบคุณมากครับ`（全新的泰文） | TH_TO_ZH | `/audio/th/eee4af7083bb.mp3` | **`/audio/zh/d8f07440a5a7.mp3`** ✅ |
| `我想吃飯` | ZH_TO_TH | `/audio/th/6594f294c235.mp3` | `null` ✅ |

`audio/zh/` 底下確認出現了第一個檔案（17,664 bytes）。後端測試 73 項全過。

### 一個要知道的邊界情況

**改版前就查過的泰文**（例如 `ผม`）再查一次會是 `fromCache: true`，
而那筆快取當初沒有生中文音檔，所以 `chineseAudioUrl` 仍然是 `null`。

這是刻意的：快取命中的意義就是「這次不花錢」，那條路上只查現成的音檔、絕不合成
（否則回應標著 `fromCache: true` 卻偷偷付了錢，帳目會對不起來）。

畫面上那顆鍵會是灰的，點一下就會補生，之後就永久亮著。
