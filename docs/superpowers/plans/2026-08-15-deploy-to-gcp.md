# 部署上雲與手機 App 化 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把中泰翻譯學習網站部署到 GCP，並在手機桌面以 PWA（ThaiLan）形式使用。

**Architecture:** 單一 Cloud Run 容器同時提供 Angular 靜態檔與 Spring Boot API，資料存 Cloud SQL for PostgreSQL，音檔存 Cloud Storage。本機與雲端以 Spring profile（`local` / `prod`）切換，音檔存取透過新增的 `AudioStorage` 介面抽象化，兩種環境共用同一套上層邏輯。

**Tech Stack:** Java 21、Spring Boot 4.1.0、Spring AI 2.0.0、Angular 22、PostgreSQL、Docker、GCP（Cloud Run / Cloud SQL / Cloud Storage）

**設計規格：** `docs/superpowers/specs/2026-08-15-deploy-to-gcp-design.md`

---

## 執行前必讀

### 環境資訊

| 項目 | 值 |
|---|---|
| 專案路徑 | `C:\Tim\language_project` |
| Shell | PowerShell（本計畫指令皆為 PowerShell 語法） |
| Maven 倉庫 | `C:\m2`（使用者名稱含中文，不可用預設路徑） |
| 執行測試 | **一律用 Maven**，不可用 IntelliJ 綠色箭頭（IDE 版本跑不動 JUnit 6） |
| 編譯異常時 | 先 `.\mvnw clean`，IDE 殘留 class 是「Unresolved compilation problem」的元兇 |

### 分階段原則

**階段 0～4 完全不碰 GCP。** 每個階段結束時系統都可執行，隨時可以停下來。

### ★ 五個必須守住的既有約束

1. **`schema.sql` 必須維持「可重複執行且不刪資料」**，不得加入 `DROP TABLE`。要重建用 `db/reset-postgres.sql`。
2. **`uq_audio_asset_text_language` 是「同一段文字只合成一次」的保證**，拿掉會安靜地一直重複付錢。
3. **`SpeechTextGuard` 檢查（`AudioController` 第 31 行）是防止帳戶被燒的關卡**，不可為了效能移除。
4. **`api_usage_log` 在重建腳本中預設不刪除**（spec 決策 15）。那是唯一能回答「這個專案花了多少錢」的地方，要清空必須手動把註解拿掉。
5. **除非證明註解是錯的，否則不可移除任何註解。** 改寫檔案時，「為什麼這樣設計」的說明一律保留；只有描述舊技術細節（SQL Server 專屬語法）的部分可以改寫。
   ★ 2026-08-15 的教訓：Task 4 首次執行時，計畫提供的 schema 範本本身就把四段設計說明濃縮掉了，執行者照做因而遺失。**計畫裡貼的程式碼範本也要遵守這條。**

---

# 階段 0：準備

## Task 1: 保存既有紀錄與確認 GCP 額度

**Files:**
- Create: `db/backup/api-usage-log-20260815.csv`

- [ ] **Step 1: 建立備份資料夾**

```powershell
New-Item -ItemType Directory -Force db\backup
```

- [ ] **Step 2: 匯出 `api_usage_log` 為 CSV**

```powershell
docker exec sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P 'Sqlserver123456' -C -d language_project -s "," -W -Q "SET NOCOUNT ON; SELECT id, query_id, provider, service_type, model_name, unit_type, input_units, output_units, input_unit_price, output_unit_price, cost_amount, currency, is_success, created_at FROM api_usage_log ORDER BY id" | Out-File -Encoding utf8 db\backup\api-usage-log-20260815.csv
```

預期：產生一個含 188 筆資料的 CSV。

- [ ] **Step 3: 確認檔案有內容**

```powershell
(Get-Content db\backup\api-usage-log-20260815.csv | Measure-Object -Line).Lines
```

預期：190 左右（188 筆 + 標題 + 分隔線）。

- [ ] **Step 4: 把備份排除於版控外**

在 `.gitignore` 末端加入：

```
# 舊資料庫的匯出備份，屬個人紀錄不進版控
db/backup/
```

- [x] **Step 5: 人工確認 GCP 額度（★ 這一步只有你能做）** — 已完成 2026-08-15

**$300 額度到期日：2026-11-14**（帳號 2026-08-15 啟用，共 91 天）。

到期時試用帳戶會自動關閉、服務停止，**不會自動扣款**。要繼續使用需手動升級為付費帳戶，屆時約 $22～27／月。

- [x] **Step 6: 人工確認 OpenAI 未開啟自動儲值** — 已完成 2026-08-15

auto-recharge 為關閉，OpenAI 的花費上限即為儲值金額。

- [ ] **Step 7: Commit**

```powershell
git add .gitignore
git commit -m @'
排除舊資料庫匯出備份

Modify:
- .gitignore 加入 db/backup/，存放遷移前的 api_usage_log 匯出檔
'@
```

---

# 階段 1：本機資料庫換成 PostgreSQL

## Task 2: 啟動 PostgreSQL 容器

**Files:** 無（純環境操作）

- [ ] **Step 1: 確認 5432 沒被佔用**

```powershell
docker ps -a --format "{{.Names}} {{.Ports}}" | Select-String "5432"
```

預期：**沒有任何輸出**。有輸出代表已有容器佔用，先處理再繼續。

- [ ] **Step 2: 建立並啟動 PostgreSQL 容器**

★ 帳號密碼就是在這一行決定的，不是去哪裡找出來的。使用者固定為 `postgres`。

```powershell
docker run -d --name postgres -e POSTGRES_PASSWORD=Postgres123456 -e POSTGRES_DB=language_project -e TZ=Asia/Taipei -p 5432:5432 postgres:latest
```

- [ ] **Step 3: 確認容器起來了**

```powershell
docker ps --filter name=postgres --format "{{.Names}} {{.Status}}"
```

預期：`postgres Up X seconds`

- [ ] **Step 4: 確認資料庫連得進去**

```powershell
docker exec postgres psql -U postgres -d language_project -c "SELECT version();"
```

預期：印出 `PostgreSQL 1x.x ...`

- [ ] **Step 5: ★ 確認版本 ≥ 15**

```powershell
docker exec postgres psql -U postgres -d language_project -t -c "SHOW server_version_num;"
```

預期：**≥ 150000**。

★ 這一步不可跳過。Task 4 要用的 `UNIQUE NULLS NOT DISTINCT` 是 PostgreSQL 15 才有的語法。若小於 15，改拉 `postgres:17` 映像重建容器。

---

## Task 3: 換掉 JDBC 驅動

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 移除 SQL Server 驅動，加入 PostgreSQL 驅動**

在 `pom.xml` 找到這段並整個替換：

```xml
        <dependency>
            <groupId>com.microsoft.sqlserver</groupId>
            <artifactId>mssql-jdbc</artifactId>
            <scope>runtime</scope>
        </dependency>
```

替換為：

```xml
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 2: 確認相依性抓得到**

```powershell
.\mvnw -q dependency:resolve
```

預期：無錯誤結束。

- [ ] **Step 3: Commit**

```powershell
git add pom.xml
git commit -m @'
JDBC 驅動換成 PostgreSQL

Modify:
- pom.xml 移除 mssql-jdbc，改用 postgresql 驅動
'@
```

---

## Task 4: 改寫 schema.sql 為 PostgreSQL 語法

**Files:**
- Modify: `db/schema.sql`（整份改寫）

- [ ] **Step 1: 整份改寫 `db/schema.sql`**

★ 保留原本所有的中文說明。那些註解記錄的是「為什麼這樣設計」，是專案資產。只有 SQL Server 專屬的部分改寫。

```sql
/*
 * 中泰語言學習網站 —— 資料表建立腳本
 *
 * 資料庫：PostgreSQL 15 以上 / language_project
 * 執行方式見本檔最下方說明。
 *
 * ── 文字編碼：PostgreSQL 不需要區分 NVARCHAR 與 VARCHAR ────────────────
 * 舊版（SQL Server）在這裡有一大段警告：collation 為 Latin1 時，
 * VARCHAR 無法保存中文與泰文，存進去會靜默變成「?」，且不會拋出任何錯誤。
 *
 * PostgreSQL 預設編碼為 UTF-8，VARCHAR 本身就能完整保存中文、泰文、
 * 拼音聲調符號，沒有第二種文字型別要選。原本那個坑在這裡不存在。
 *
 * ★ 但這段歷史仍然值得記住：換資料庫時「型別看起來一樣、行為卻不同」
 *   是最難發現的一類問題，因為它不會報錯。
 * ──────────────────────────────────────────────────────────────────
 *
 * ── 音檔存在哪裡 ────────────────────────────────────────────────────
 * 五張表中，音檔一律由 audio_asset 持有，其他表只存文字。
 * 同一段泰文不管在哪裡出現，都指向同一個檔案，只會被合成一次。
 * ──────────────────────────────────────────────────────────────────
 *
 * 本腳本可重複執行：所有建立動作都有存在性檢查，不會覆蓋既有資料表。
 * ★ 這個特性請維持住 —— 不要把 DROP TABLE 加進這個檔案。
 *   需要重建時用 db/reset-postgres.sql，那支才是會刪資料的。
 */

/* ============================================================
 * 1. translation_query —— 查詢結果快取
 *
 * Key 為「使用者輸入的原文 ＋ 翻譯方向 ＋ 說話者性別」三者的組合，
 * 不區分單字或句子。
 *
 * 這張表不持有音檔，音檔在 audio_asset。
 * ============================================================ */
CREATE TABLE IF NOT EXISTS translation_query
(
    -- 代理主鍵。子表需以外鍵參考，中文字串當外鍵佔用空間大且 join 較慢，
    -- 故另設流水號。
    id            BIGINT        GENERATED BY DEFAULT AS IDENTITY,

    -- 使用者輸入的原文。前後空白於寫入前去除。
    -- 中翻泰時這裡是中文，泰翻中時這裡是泰文。
    source_text   VARCHAR(100)  NOT NULL,

    -- TranslationDirectionEnum：ZH_TO_TH / TH_TO_ZH
    -- 由 LanguageDetector 依輸入的字元範圍自動判斷，使用者不需要選。
    direction     VARCHAR(20)   NOT NULL,

    -- SpeakerGenderEnum：MALE / FEMALE
    --
    -- 泰文的自稱與句尾助詞都分性別（男 ผม/ครับ、女 ฉัน/ค่ะ），
    -- 所以同一句中文的男版與女版是兩句不同的泰文，必須各存一筆。
    --
    -- ★ 泰翻中沒有性別概念，該方向一律為 NULL。
    gender        VARCHAR(10)   NULL,

    -- 這句話的中文面與泰文面。
    --
    -- ★ source_text 必定與其中一面完全相同，這份重複是刻意的：
    --   source_text 專職當快取的鑰匙，另外兩欄專職表示「這句話的兩面」。
    --   混用的話，程式每次都要先判斷方向才知道哪個欄位裝什麼，很容易寫錯。
    --
    --   例：輸入「我想喝酒」（男）
    --       source_text  = 我想喝酒
    --       chinese_text = 我想喝酒          ← 與 source_text 相同
    --       thai_text    = ผมอยากดื่มเหล้าครับ
    --
    --       輸入「ผมอยากดื่มเหล้าครับ」
    --       source_text  = ผมอยากดื่มเหล้าครับ
    --       chinese_text = 我想喝酒
    --       thai_text    = ผมอยากดื่มเหล้าครับ ← 與 source_text 相同
    chinese_text  VARCHAR(500)  NOT NULL,
    thai_text     VARCHAR(500)  NOT NULL,

    -- 泰文的羅馬拼音（含聲調符號，如 chǎn、dùuem）
    romanization  VARCHAR(500)  NOT NULL,

    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_translation_query PRIMARY KEY (id),

    /* ★★★ 這一行是本次遷移最危險的地方，改動前請先讀完 ★★★
     *
     * 快取命中判斷依據，同時防止重複寫入。
     *
     * SQL Server 的 UNIQUE 把 NULL 當成「一個值」來比對，所以
     * 「同一句泰文（gender 為 NULL）只會有一筆」自動成立。
     *
     * PostgreSQL 預設相反：每個 NULL 都被視為互不相同，
     * 於是 (source_text, direction, NULL) 可以重複無限多筆。
     *
     * 後果：每查一次泰翻中就新增一筆、每次都重新付錢給 OpenAI，
     *       而且畫面上完全看不出異常 —— 這是會安靜燒錢的那種 bug。
     *
     * NULLS NOT DISTINCT 讓 PostgreSQL 的行為與 SQL Server 一致。
     * 需要 PostgreSQL 15 以上。★ 不可省略這兩個字。
     */
    CONSTRAINT uq_translation_query_key
        UNIQUE NULLS NOT DISTINCT (source_text, direction, gender),

    CONSTRAINT ck_translation_query_direction
        CHECK (direction IN ('ZH_TO_TH', 'TH_TO_ZH')),

    CONSTRAINT ck_translation_query_gender
        CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE'))
);

/* ============================================================
 * 2. translation_segment —— 逐詞拆解結果
 *
 * 複合主鍵 (query_id, seq_no)，無獨立流水號。
 * 同一個詞會在不同句子的拆解中重複出現，這是正確的 ——
 * 本表記錄的是「該句話如何拆解」，而非字典。
 *
 * 兩個方向共用同一組欄位：
 *   中翻泰 → chinese_text 是輸入的詞，thai_text 是翻出來的
 *   泰翻中 → thai_text 是輸入的詞，chinese_text 是翻出來的
 *
 * ★ 泰文的句尾助詞（ครับ、ค่ะ、นะ）沒有中文意思，
 *   chinese_text 會存一個括號標籤，例如「（男性禮貌語助詞）」。
 *   不可以因為翻不出中文就把它從拆解結果裡拿掉 ——
 *   那些是泰文最高頻的字，使用者正需要知道它們在做什麼。
 * ============================================================ */
CREATE TABLE IF NOT EXISTS translation_segment
(
    query_id      BIGINT        NOT NULL,

    -- 顯示順序，自 1 起算
    seq_no        INT           NOT NULL,

    chinese_text  VARCHAR(50)   NOT NULL,
    thai_text     VARCHAR(100)  NOT NULL,
    romanization  VARCHAR(100)  NOT NULL,

    CONSTRAINT pk_translation_segment PRIMARY KEY (query_id, seq_no),

    -- 資料庫層級的完整性約束。
    -- 注意：程式端刻意不使用 JPA 關聯註解（@ManyToOne 等），
    -- 關聯由 Service 層自行查詢組裝，此約束僅確保資料不會孤立。
    CONSTRAINT fk_translation_segment_query_id
        FOREIGN KEY (query_id)
        REFERENCES translation_query (id)
        ON DELETE CASCADE
);

/* ============================================================
 * 3. vocabulary —— 單字表
 *
 * ★ 一列代表「一個說法」，不是「一個詞」。
 *   泰文的「我」至少有 ผม / ฉัน / กู 三種說法，所以「我」會佔三列。
 *   單字列表頁因此會出現同一個中文詞連續多列 —— 那是預期行為。
 *
 * ★ 這張表不兼任「省錢用的快取」。
 *   曾經 TranslationService 會先查這裡、命中就不呼叫 AI，
 *   那條捷徑會讓多重說法功能永遠失效（查過「我想喝酒」之後，
 *   單獨查「我」就只會拿到 ฉัน，永遠問不到 ผม 和 กู）。該捷徑已移除，
 *   不可加回來。
 * ============================================================ */
CREATE TABLE IF NOT EXISTS vocabulary
(
    id            BIGINT        GENERATED BY DEFAULT AS IDENTITY,

    chinese_text  VARCHAR(50)   NOT NULL,
    thai_text     VARCHAR(100)  NOT NULL,
    romanization  VARCHAR(100)  NOT NULL,

    -- GenderUsageEnum：MALE / FEMALE / BOTH。這個「說法」適合誰用。
    --
    -- ★ 與 translation_query.gender 是不同的概念：
    --   那個是「使用者是誰」，這個是「這個說法適合誰」，
    --   而且只有這裡才有 BOTH（使用者不可能男女都是，
    --   但一個詞可以是男女通用的，例如 กู）。
    --
    -- 從句子拆解沉澱下來的詞沒有這項資訊，為 NULL。
    -- 日後單獨查詢該詞時會補上（合併規則見 TranslationPersistenceService）。
    gender_usage  VARCHAR(10)   NULL,

    -- PolitenessEnum：FORMAL / NEUTRAL / CASUAL / RUDE
    -- 前端要把 RUDE 用警示色標出來 ——
    -- 用錯場合的後果是冒犯到人，不是講得不夠好。
    politeness    VARCHAR(10)   NULL,

    -- 中文說明，例如「男生自稱，正式或對不熟的人使用」
    note          VARCHAR(200)  NULL,

    -- VocabularySourceTypeEnum：SEGMENT（由句子拆解而來）/ DIRECT（直接查此詞）
    -- 已存在的列不更新此欄位，以首次寫入的值為準。
    source_type   VARCHAR(20)   NOT NULL,

    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_vocabulary PRIMARY KEY (id),

    -- 字典的唯一鍵。★ 是中文＋泰文的組合，這一點才讓同一個詞能存多種說法。
    -- 兩個欄位都是 NOT NULL，所以此處不需要 NULLS NOT DISTINCT。
    CONSTRAINT uq_vocabulary_chinese_thai
        UNIQUE (chinese_text, thai_text),

    CONSTRAINT ck_vocabulary_source_type
        CHECK (source_type IN ('SEGMENT', 'DIRECT')),

    CONSTRAINT ck_vocabulary_gender_usage
        CHECK (gender_usage IS NULL
               OR gender_usage IN ('MALE', 'FEMALE', 'BOTH')),

    CONSTRAINT ck_vocabulary_politeness
        CHECK (politeness IS NULL
               OR politeness IN ('FORMAL', 'NEUTRAL', 'CASUAL', 'RUDE'))
);

-- 查一個中文詞的所有說法時會用到（單字查詢的主要路徑）。
CREATE INDEX IF NOT EXISTS ix_vocabulary_chinese_text
    ON vocabulary (chinese_text);

/* ============================================================
 * 4. audio_asset —— 音檔資產
 *
 * 規則只有一句話：★ 同一段文字，全站只會有一個音檔 ★
 *
 * 為什麼要這樣設計：合成語音要付錢。如果音檔綁在「那一次查詢」身上，
 * 同一個 เหล้า 會被合成好幾次 —— 查「酒」一次、查「我想喝酒」逐詞一次、
 * 查「他喝酒了」又一次。三個一模一樣的檔案，付了三次錢。
 *
 * 改成以文字內容為鍵之後，查得越多、覆蓋率越高，語音費用趨近於零。
 * 這是本專案「用越久越省錢」的核心。
 *
 * ★ 語言欄位是必要的：中文和泰文各自有自己的音檔，
 *   存在不同的子資料夾（audio/th、audio/zh）。
 * ============================================================ */
CREATE TABLE IF NOT EXISTS audio_asset
(
    id          BIGINT        GENERATED BY DEFAULT AS IDENTITY,

    -- 要唸出來的文字。可能短到一個詞（เหล้า），長到一整句。
    speech_text VARCHAR(500)  NOT NULL,

    -- SpeechLanguageEnum：TH / ZH
    language    VARCHAR(10)   NOT NULL,

    -- 相對於 audio 資料夾的路徑，例如 th/a1b2c3d4e5f6.wav。
    -- 系統產生的 ASCII 字串。
    -- 前端把它接在 /audio/ 後面就是可以直接播放的網址。
    file_path   VARCHAR(100)  NOT NULL,

    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_audio_asset PRIMARY KEY (id),

    -- ★ 這條唯一鍵就是「同一段文字只合成一次」的保證。
    --   拿掉它程式仍然會跑，只是會安靜地一直重複付錢，
    --   而且畫面上完全看不出異常。務必保留。
    --   兩個欄位都是 NOT NULL，不需要 NULLS NOT DISTINCT。
    CONSTRAINT uq_audio_asset_text_language
        UNIQUE (speech_text, language),

    CONSTRAINT ck_audio_asset_language
        CHECK (language IN ('TH', 'ZH'))
);

/* ============================================================
 * 5. api_usage_log —— API 用量與費用紀錄
 *
 * 事件紀錄表，無自然鍵。屬營運監控資料，刪除不影響業務功能。
 * ============================================================ */
CREATE TABLE IF NOT EXISTS api_usage_log
(
    id                  BIGINT         GENERATED BY DEFAULT AS IDENTITY,

    -- 對應的查詢，可追溯。
    -- 刻意「不」建立外鍵約束：用量紀錄於呼叫外部服務的當下寫入，
    -- 此時 translation_query 尚未寫入（外部呼叫在交易之外執行），
    -- 建立外鍵會導致寫入失敗。同時本表為稽核用途，
    -- 即使對應的查詢日後被刪除，紀錄仍應保留。
    query_id            BIGINT         NULL,

    -- AiProviderEnum：OPENAI / ANTHROPIC / GOOGLE / AZURE
    provider            VARCHAR(20)    NOT NULL,

    -- AiServiceTypeEnum：TRANSLATION / SPEECH
    service_type        VARCHAR(20)    NOT NULL,

    model_name          VARCHAR(100)   NOT NULL,

    -- UsageUnitTypeEnum：TOKEN（對話模型）/ CHARACTER（語音合成）
    unit_type           VARCHAR(20)    NOT NULL,

    input_units         BIGINT         NOT NULL,

    -- 語音服務無輸出計價，固定為 0
    output_units        BIGINT         NOT NULL,

    -- 呼叫「當下」的單價。價格調整後歷史紀錄仍可驗算。
    -- 金額一律使用 NUMERIC，Java 端對應 BigDecimal，
    -- 禁止使用 float / double，否則累加後金額會有誤差。
    input_unit_price    NUMERIC(12,8)  NOT NULL,
    output_unit_price   NUMERIC(12,8)  NOT NULL,
    cost_amount         NUMERIC(12,6)  NOT NULL,

    -- 固定 USD。不存台幣，匯率浮動，統計時再換算。
    currency            CHAR(3)        NOT NULL DEFAULT 'USD',

    -- PostgreSQL 有原生的 BOOLEAN，不需要 SQL Server 那套 BIT。
    -- 失敗仍可能計費，且保留紀錄才能觀察失敗率。
    is_success          BOOLEAN        NOT NULL,

    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_api_usage_log PRIMARY KEY (id),

    CONSTRAINT ck_api_usage_log_service_type
        CHECK (service_type IN ('TRANSLATION', 'SPEECH')),

    CONSTRAINT ck_api_usage_log_unit_type
        CHECK (unit_type IN ('TOKEN', 'CHARACTER'))
);

-- 期間統計用（例如「這個月花了多少」）
CREATE INDEX IF NOT EXISTS ix_api_usage_log_created_at
    ON api_usage_log (created_at);

-- 追溯某次查詢花了多少錢
CREATE INDEX IF NOT EXISTS ix_api_usage_log_query_id
    ON api_usage_log (query_id);


/* ============================================================
 * 執行方式
 * ============================================================
 *
 * 【本機】
 *   docker cp db\schema.sql postgres:/tmp/schema.sql
 *   docker exec postgres psql -U postgres -d language_project -f /tmp/schema.sql
 *
 * 【重新建立】
 *   本腳本不會覆蓋既有資料表，直接重跑不會清掉任何東西。
 *   要真的重建，先執行 db\reset-postgres.sql（那支才會 DROP），再回來跑這一支。
 *
 *   ★ 不要把 DROP TABLE 加進這個檔案。
 *     「可重複執行且不刪資料」是這個腳本的安全特性。
 *
 * 【音檔資料夾】
 *   本機開發時，專案根目錄下需有 audio\th\ 與 audio\zh\ 兩個資料夾。
 *   雲端不需要，音檔在 Cloud Storage。
 */
```

- [ ] **Step 2: 改寫重建腳本**

刪除 `db/reset-2026-08-14.sql`，新建 `db/reset-postgres.sql`：

```sql
/*
 * ⚠⚠⚠ 這個腳本會刪掉資料，而且救不回來 ⚠⚠⚠
 *
 * 使用時機：schema 結構改變、或想清空重來。
 * 執行順序：先跑這一支，再跑 db/schema.sql 把資料表重新建起來。順序不能反。
 *
 * ── ★ api_usage_log 刻意不刪 ★ ─────────────────────────────────────────
 *
 *   那是「花了多少錢」的稽核紀錄，與資料表結構無關，刪掉就永遠查不回
 *   歷史費用了。這是 spec 決策 15 的結論。
 *
 *   如果你確定連費用紀錄也要清空，把最下面那一行的 -- 拿掉再執行。
 *
 * ── 別忘了音檔 ──────────────────────────────────────────────────────────
 *
 *   資料表清空後，audio/ 底下的音檔就沒有任何紀錄指向它們了。
 *   刪掉那些檔案，並確認 audio/th 與 audio/zh 兩個子資料夾存在。
 *
 * ── 執行方式 ────────────────────────────────────────────────────────────
 *
 *   docker cp db\reset-postgres.sql postgres:/tmp/reset.sql
 *   docker exec postgres psql -U postgres -d language_project -f /tmp/reset.sql
 */

/* 刪除順序：有外鍵指出去的要先刪。
 * translation_segment 的外鍵指向 translation_query，所以它排在前面。
 * CASCADE 讓相依的約束一併移除。 */
DROP TABLE IF EXISTS translation_segment CASCADE;
DROP TABLE IF EXISTS translation_query   CASCADE;
DROP TABLE IF EXISTS vocabulary          CASCADE;
DROP TABLE IF EXISTS audio_asset         CASCADE;

/* ============================================================
 * 費用稽核紀錄 —— 預設「不」刪除
 *
 * 想連費用紀錄一起清空的話，把下面那行的 -- 拿掉。
 * 想清楚再動：這張表是唯一能回答「這個專案到目前為止花了多少錢」的地方。
 * ============================================================ */
-- DROP TABLE IF EXISTS api_usage_log CASCADE;
```

- [ ] **Step 3: 建立資料表**

```powershell
docker cp db\schema.sql postgres:/tmp/schema.sql
docker exec postgres psql -U postgres -d language_project -f /tmp/schema.sql
```

預期：一連串 `CREATE TABLE`、`CREATE INDEX`，**無 ERROR**。

- [ ] **Step 4: 確認五張表都在**

```powershell
docker exec postgres psql -U postgres -d language_project -c "\dt"
```

預期：列出 `api_usage_log`、`audio_asset`、`translation_query`、`translation_segment`、`vocabulary` 五張。

- [ ] **Step 5: ★ 驗證腳本可重複執行（不會壞、不會刪資料）**

```powershell
docker exec postgres psql -U postgres -d language_project -f /tmp/schema.sql
```

預期：一堆 `NOTICE: relation "xxx" already exists, skipping`，**無 ERROR**。

- [ ] **Step 6: Commit**

```powershell
git add db\schema.sql db\reset-postgres.sql
git rm db\reset-2026-08-14.sql
git commit -m @'
資料表腳本改寫為 PostgreSQL 語法

Modify:
- schema.sql 改寫為 PostgreSQL：IDENTITY、TIMESTAMP、BOOLEAN、NUMERIC、CREATE TABLE IF NOT EXISTS
- 移除 NVARCHAR 與 VARCHAR 的區分，PostgreSQL 預設 UTF-8 不需要
- translation_query 的唯一鍵改用 UNIQUE NULLS NOT DISTINCT，維持「gender 為 NULL 時仍只能有一筆」的行為

Feat:
- 新增 reset-postgres.sql 取代 reset-2026-08-14.sql
'@
```

---

## Task 5: 清掉 Entity 上的 SQL Server 專屬型別

**Files:**
- Modify: `src/main/java/com/tim/language_project/entity/AudioAsset.java:35`
- Modify: `src/main/java/com/tim/language_project/entity/TranslationQuery.java:36,49,52,55`
- Modify: `src/main/java/com/tim/language_project/entity/TranslationSegment.java:34,37,40`
- Modify: `src/main/java/com/tim/language_project/entity/Vocabulary.java:37,40,43,61`

**背景：** 這 12 個欄位寫著 `columnDefinition = "NVARCHAR(n)"`。PostgreSQL 沒有 `NVARCHAR` 型別。目前 `ddl-auto: none` 所以不會立刻爆炸，但它是錯的資訊，且任何人日後開啟 schema 驗證就會失敗。改用 JPA 標準的 `length`，與資料庫無關。

`ApiUsageLog.java:73` 的 `CHAR(3)` 在 PostgreSQL 有效，**不要改**。

- [ ] **Step 1: 逐一替換（共 12 處）**

規則：`columnDefinition = "NVARCHAR(n)"` → `length = n`

| 檔案:行 | 改前 | 改後 |
|---|---|---|
| `AudioAsset.java:35` | `@Column(name = "speech_text", columnDefinition = "NVARCHAR(500)", nullable = false)` | `@Column(name = "speech_text", length = 500, nullable = false)` |
| `TranslationQuery.java:36` | `@Column(name = "source_text", columnDefinition = "NVARCHAR(100)", nullable = false)` | `@Column(name = "source_text", length = 100, nullable = false)` |
| `TranslationQuery.java:49` | `@Column(name = "chinese_text", columnDefinition = "NVARCHAR(500)", nullable = false)` | `@Column(name = "chinese_text", length = 500, nullable = false)` |
| `TranslationQuery.java:52` | `@Column(name = "thai_text", columnDefinition = "NVARCHAR(500)", nullable = false)` | `@Column(name = "thai_text", length = 500, nullable = false)` |
| `TranslationQuery.java:55` | `@Column(name = "romanization", columnDefinition = "NVARCHAR(500)", nullable = false)` | `@Column(name = "romanization", length = 500, nullable = false)` |
| `TranslationSegment.java:34` | `@Column(name = "chinese_text", columnDefinition = "NVARCHAR(50)", nullable = false)` | `@Column(name = "chinese_text", length = 50, nullable = false)` |
| `TranslationSegment.java:37` | `@Column(name = "thai_text", columnDefinition = "NVARCHAR(100)", nullable = false)` | `@Column(name = "thai_text", length = 100, nullable = false)` |
| `TranslationSegment.java:40` | `@Column(name = "romanization", columnDefinition = "NVARCHAR(100)", nullable = false)` | `@Column(name = "romanization", length = 100, nullable = false)` |
| `Vocabulary.java:37` | `@Column(name = "chinese_text", columnDefinition = "NVARCHAR(50)", nullable = false)` | `@Column(name = "chinese_text", length = 50, nullable = false)` |
| `Vocabulary.java:40` | `@Column(name = "thai_text", columnDefinition = "NVARCHAR(100)", nullable = false)` | `@Column(name = "thai_text", length = 100, nullable = false)` |
| `Vocabulary.java:43` | `@Column(name = "romanization", columnDefinition = "NVARCHAR(100)", nullable = false)` | `@Column(name = "romanization", length = 100, nullable = false)` |
| `Vocabulary.java:61` | `@Column(name = "note", columnDefinition = "NVARCHAR(200)")` | `@Column(name = "note", length = 200)` |

- [ ] **Step 2: 確認一個都沒漏**

```powershell
Select-String -Path src\main\java\com\tim\language_project\entity\*.java -Pattern "NVARCHAR"
```

預期：**沒有任何輸出**。

- [ ] **Step 3: 編譯**

```powershell
.\mvnw -q clean compile
```

預期：BUILD SUCCESS。

- [ ] **Step 4: Commit**

```powershell
git add src\main\java\com\tim\language_project\entity
git commit -m @'
Entity 移除 SQL Server 專屬型別宣告

Modify:
- 十二個欄位的 columnDefinition = "NVARCHAR(n)" 改為 JPA 標準的 length = n
- PostgreSQL 沒有 NVARCHAR 型別，且 length 與資料庫無關
- ApiUsageLog 的 CHAR(3) 在 PostgreSQL 有效，維持不變
'@
```

---

## Task 6: 切換連線設定並跑通全部測試

**Files:**
- Modify: `src/main/resources/application-local.yml`
- Modify: `src/main/resources/application-local.yml.example`

- [ ] **Step 1: 改寫 `application-local.yml.example`**

```yaml
# 本機開發設定範本
#
# 使用方式：複製此檔為 application-local.yml，並填入實際值。
# application-local.yml 已列入 .gitignore，不會進入版本控制。
#
# PostgreSQL 容器的啟動方式：
#   docker run -d --name postgres `
#     -e POSTGRES_PASSWORD=你的密碼 `
#     -e POSTGRES_DB=language_project `
#     -e TZ=Asia/Taipei `
#     -p 5432:5432 postgres:latest
#
# ★ 帳號固定是 postgres，密碼就是上面那行 POSTGRES_PASSWORD 自己設的值。

spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:5432/language_project
    username: postgres
    password: 請填入資料庫密碼

  ai:
    openai:
      api-key: 請填入 OpenAI API Key

google:
  speech:
    api-key: 請填入 Google Cloud Text-to-Speech API Key
```

- [ ] **Step 2: 同步改 `application-local.yml`（實際使用的那份）**

把 `spring.datasource` 那一段替換為：

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: jdbc:postgresql://localhost:5432/language_project
    username: postgres
    password: Postgres123456
```

★ 其餘的金鑰設定原封不動保留。

- [ ] **Step 3: 執行全部測試**

```powershell
.\mvnw clean test
```

預期：**Tests run: N, Failures: 0, Errors: 0**，BUILD SUCCESS。

★ 若出現 `Unresolved compilation problem`，先 `.\mvnw clean` 再重跑，那是 IDE 殘留 class。

- [ ] **Step 4: 若有測試失敗，逐一處理**

常見原因與處置：

| 症狀 | 原因 | 處置 |
|---|---|---|
| `relation "xxx" does not exist` | Task 4 的 schema 沒跑進去 | 重跑 Task 4 Step 3 |
| 唯一鍵測試沒抓到預期的例外 | 該表的唯一鍵沒建起來 | `docker exec postgres psql -U postgres -d language_project -c "\d 表名"` 確認 |
| 連線被拒 | 容器沒起來 | `docker start postgres` |

- [ ] **Step 5: 新增一個測試，專門守住 NULL 唯一鍵的行為**

Create: `src/test/java/com/tim/language_project/repository/TranslationQueryNullGenderUniqueTest.java`

```java
package com.tim.language_project.repository;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  防「泰翻中的快取失效、每查一次就重新付一次錢」。
 *
 * ── 為什麼需要這個測試 ─────────────────────────────────────────────────
 *
 *  translation_query 的唯一鍵是 (source_text, direction, gender)。
 *  泰翻中沒有性別概念，所以 gender 一律是 NULL。
 *
 *  ★ 兩種資料庫對「NULL 算不算重複」的看法相反：
 *
 *      SQL Server   → NULL 當成一個值，(A, TH_TO_ZH, NULL) 只能有一筆 ✅
 *      PostgreSQL   → 每個 NULL 互不相同，同樣的組合可以無限多筆 ❌
 *
 *  2026-08-15 從 SQL Server 遷移到 PostgreSQL 時，schema 用
 *  UNIQUE NULLS NOT DISTINCT 補回了 SQL Server 的行為。
 *
 *  這個測試就是那條約束的看門狗 —— 如果有人日後把 NULLS NOT DISTINCT
 *  拿掉（例如整理 schema 時覺得那兩個字是贅字），這個測試會立刻失敗。
 *
 *  ★ 沒有這個測試的話，那個 bug 不會有任何症狀：
 *    畫面正常、沒有錯誤訊息，只是 OpenAI 帳單一直增加。
 *
 * ── 假的東西 ───────────────────────────────────────────────────────────
 *
 *  沒有任何假物件。@AutoConfigureTestDatabase(replace = NONE) 代表
 *  打的是本機真正的 PostgreSQL，因為這裡要驗的就是資料庫本身的行為，
 *  換成 H2 就完全失去意義。
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.tim.language_project.entity.TranslationQuery;
import com.tim.language_project.enums.TranslationDirectionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TranslationQueryNullGenderUniqueTest {

    @Autowired
    private TranslationQueryRepository translationQueryRepository;

    @Test
    @DisplayName("泰翻中的 gender 為 NULL，同一句仍不可重複寫入")
    void shouldRejectDuplicateWhenGenderIsNull() {
        translationQueryRepository.saveAndFlush(nullGenderQuery());

        assertThatThrownBy(() ->
                translationQueryRepository.saveAndFlush(nullGenderQuery()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("gender 為 NULL 的那一句寫入後查得回來")
    void shouldFindBackNullGenderQuery() {
        translationQueryRepository.saveAndFlush(nullGenderQuery());

        // findByKey 的 JPQL 對 NULL 做了特別處理：
        //   ((:gender IS NULL AND query.gender IS NULL) OR query.gender = :gender)
        // 這裡順便驗證那段條件在 PostgreSQL 上仍然成立。
        assertThat(translationQueryRepository
                .findByKey("สวัสดีครับ", TranslationDirectionEnum.TH_TO_ZH, null))
                .isPresent();
    }

    /** 泰翻中的一筆：gender 必為 null，source_text 與 thai_text 相同。 */
    private TranslationQuery nullGenderQuery() {
        TranslationQuery query = new TranslationQuery();
        query.setSourceText("สวัสดีครับ");
        query.setDirection(TranslationDirectionEnum.TH_TO_ZH);
        query.setGender(null);
        query.setChineseText("你好");
        query.setThaiText("สวัสดีครับ");
        query.setRomanization("sà-wàt-dii khráp");
        return query;
    }
}
```

★ `findByKey` 回傳的是 `Optional<TranslationQueryDto>`（不是 Entity），這是本專案刻意的做法——關聯與轉換都在查詢時完成。測試只斷言 `isPresent()`，不碰內容，所以不受影響。

- [ ] **Step 6: 執行新測試，確認它會過**

```powershell
.\mvnw test -Dtest=TranslationQueryNullGenderUniqueTest
```

預期：**Tests run: 2, Failures: 0**

- [ ] **Step 7: 反向驗證這個測試真的有效（★ 不可跳過）**

暫時把 schema 的 `NULLS NOT DISTINCT` 拿掉，確認測試會失敗：

```powershell
docker exec postgres psql -U postgres -d language_project -c "ALTER TABLE translation_query DROP CONSTRAINT uq_translation_query_key;"
docker exec postgres psql -U postgres -d language_project -c "ALTER TABLE translation_query ADD CONSTRAINT uq_translation_query_key UNIQUE (source_text, direction, gender);"
.\mvnw test -Dtest=TranslationQueryNullGenderUniqueTest
```

預期：**第一個測試失敗**（沒有拋出 `DataIntegrityViolationException`）。這證明測試確實在守這件事。

還原：

```powershell
docker exec postgres psql -U postgres -d language_project -c "ALTER TABLE translation_query DROP CONSTRAINT uq_translation_query_key;"
docker exec postgres psql -U postgres -d language_project -c "ALTER TABLE translation_query ADD CONSTRAINT uq_translation_query_key UNIQUE NULLS NOT DISTINCT (source_text, direction, gender);"
.\mvnw test -Dtest=TranslationQueryNullGenderUniqueTest
```

預期：**兩個測試都過**。

- [ ] **Step 8: 手動啟動確認整體可用**

```powershell
.\mvnw spring-boot:run
```

另開終端機跑 `cd frontend; npm start`，開瀏覽器 <http://localhost:4200>，查一句沒查過的中文，確認：
- 有泰文、拼音、逐詞拆解
- 音檔播得出來
- 單字列表頁打得開

- [ ] **Step 9: 量測記憶體用量（★ 階段 5 選規格要用）**

```powershell
.\mvnw clean package -DskipTests
java -Xmx400m -jar target\language_project-0.0.1-SNAPSHOT.jar
```

照樣查一句、聽音檔。**若沒有 `OutOfMemoryError`，Cloud Run 開 512Mi 即可**；若崩潰，改試 `-Xmx700m`，成功則 Cloud Run 開 1Gi。

把結論記下來，階段 5 Task 18 會用到。

- [ ] **Step 10: Commit**

```powershell
git add src\main\resources\application-local.yml.example src\test
git commit -m @'
本機資料庫切換至 PostgreSQL

Modify:
- application-local.yml.example 連線設定改為 PostgreSQL，並補上容器啟動指令

Feat:
- 新增 TranslationQueryNullGenderUniqueTest，守住「gender 為 NULL 時仍不可重複」的行為
- 該行為在 SQL Server 是預設，在 PostgreSQL 需靠 UNIQUE NULLS NOT DISTINCT，拿掉會安靜地重複付費
'@
```

---

# 階段 2：音檔儲存抽象化

**背景：** 目前寫檔的程式碼在 `GoogleSpeechClient:183-200` 與 `OpenAiSpeechClient:224-232`，兩邊各寫一份幾乎相同的 `Files.write`。`AudioAssetService` 本身不碰檔案，它只拿 `SpeechClient.synthesize()` 回傳的路徑。

因此抽象化的注入點是**兩個 SpeechClient**，不是 `AudioAssetService`。

## Task 7: 定義 AudioStorage 介面

**Files:**
- Create: `src/main/java/com/tim/language_project/client/storage/AudioStorage.java`
- Create: `src/main/java/com/tim/language_project/client/storage/AudioContentType.java`

- [ ] **Step 1: 建立介面**

```java
package com.tim.language_project.client.storage;

import com.tim.language_project.enums.SpeechLanguageEnum;

import java.io.InputStream;
import java.util.Optional;

/**
 * 音檔要存到哪裡。
 *
 * 這個介面存在的理由：本機把音檔存在 audio 資料夾，雲端存在 Cloud Storage，
 * 但呼叫端不應該知道這件事。與 SpeechClient 是同一種做法 ——
 * 一個介面、兩個實作、靠設定切換。
 *
 * ★ 兩個實作回傳的 filePath 格式必須完全一致（例如 th/a1b2c3d4e5f6.wav），
 *   因為那個字串會被寫進 audio_asset.file_path，兩種環境共用同一張表的語意。
 */
public interface AudioStorage {

    /**
     * 存一份音檔。
     *
     * ★ 為什麼要傳副檔名進來：兩家語音服務給的格式不一樣。
     *   Google 走 LINEAR16，經 WavAudio.tidy 處理後是 wav；
     *   OpenAI 直接回 mp3。這一層不該去猜是誰呼叫的，由呼叫端明講。
     *
     * @param language  決定放在哪個子資料夾（th / zh）
     * @param content   音檔的位元組內容
     * @param extension 副檔名，不含點，例如 wav 或 mp3
     * @return 相對路徑（例：th/a1b2c3d4e5f6.wav）；存檔失敗回傳空值
     */
    Optional<String> save(SpeechLanguageEnum language, byte[] content, String extension);

    /**
     * 開一條讀取串流。
     *
     * ★ 回傳 InputStream 而非 byte[] 是刻意的：
     *   讀成 byte[] 會把整個檔案攤在記憶體裡，同時播放多個就會疊加。
     *   串流的記憶體佔用固定於一個小緩衝區，與檔案大小、併發數無關。
     *   呼叫端負責關閉這條串流。
     *
     * @param filePath 相對路徑，格式同 save 的回傳值
     * @return 檔案不存在時回傳空值
     */
    Optional<InputStream> openStream(String filePath);
}
```

- [ ] **Step 2: 建立副檔名對 MIME 型別的對照**

```java
package com.tim.language_project.client.storage;

/**
 * 副檔名對應到 HTTP 的 Content-Type。
 *
 * 為什麼需要它：瀏覽器是靠 Content-Type 決定「這是什麼、能不能播」的。
 * 給錯的話，有些瀏覽器會變成下載檔案而不是播放。
 *
 * ★ 兩家語音服務給的格式不同（Google 是 wav、OpenAI 是 mp3），
 *   而且兩個地方都要用到這份對照 ——
 *   上傳到 Cloud Storage 時標記 blob 的型別，
 *   以及 AudioFileController 回應時的標頭。兩處必須一致，故集中在這裡。
 */
public final class AudioContentType {

    private static final String WAV = "audio/wav";

    private static final String MPEG = "audio/mpeg";

    private AudioContentType() {
    }

    /** 依副檔名（不含點）給出 Content-Type，未知者一律當 wav。 */
    public static String of(String extension) {
        return "mp3".equalsIgnoreCase(extension) ? MPEG : WAV;
    }

    /** 依完整檔名或路徑給出 Content-Type，例如 th/a1b2c3.mp3。 */
    public static String ofPath(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');

        return dotIndex < 0
                ? WAV
                : of(filePath.substring(dotIndex + 1));
    }
}
```

- [ ] **Step 3: 編譯**

```powershell
.\mvnw -q clean compile
```

預期：BUILD SUCCESS。

- [ ] **Step 4: Commit**

```powershell
git add src\main\java\com\tim\language_project\client\storage
git commit -m @'
新增音檔儲存介面

Feat:
- 新增 AudioStorage 介面，抽象化「音檔存到哪裡」
- 沿用 SpeechClient 既有模式：一個介面、兩個實作、靠設定切換
- save 帶副檔名參數，因 Google 回 wav 而 OpenAI 回 mp3
- openStream 回傳 InputStream 而非 byte[]，避免整個檔案進記憶體
- 新增 AudioContentType，集中副檔名與 Content-Type 的對照
'@
```

---

## Task 8: 實作 LocalDiskAudioStorage

**Files:**
- Create: `src/main/java/com/tim/language_project/client/storage/LocalDiskAudioStorage.java`
- Test: `src/test/java/com/tim/language_project/client/storage/LocalDiskAudioStorageTest.java`

- [ ] **Step 1: 先寫失敗的測試**

```java
package com.tim.language_project.client.storage;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  防「本機存進去的音檔讀不回來」，以及「路徑格式跟雲端那份不一致」。
 *
 *  路徑格式為什麼要緊：save 回傳的字串會被寫進 audio_asset.file_path，
 *  而那張表是本機與雲端共用的語意。如果本機回傳 "audio/th/x.wav"、
 *  雲端回傳 "th/x.wav"，資料就沒辦法互通，將來要搬家也會全部對不上。
 *
 * ── 假的東西 ───────────────────────────────────────────────────────────
 *
 *  用 @TempDir 給一個測試專用的暫存資料夾，取代真正的 audio 資料夾。
 *  這樣測試不會弄髒專案，也不會受既有音檔影響。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  1. 存進去讀得回來       → 最基本的往返，壞了就整個功能不能用
 *  2. 路徑格式是 語言/檔名 → 防路徑格式跟雲端實作分家
 *  3. 子資料夾自動建立     → 第一次跑的機器上 audio/th 還不存在
 *  4. 讀不存在的檔回空值   → 防呼叫端拿到例外而不是空值
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.enums.SpeechLanguageEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDiskAudioStorageTest {

    @TempDir
    private Path tempDirectory;

    private LocalDiskAudioStorage localDiskAudioStorage;

    @BeforeEach
    void setUp() {
        AudioStorageProperties properties = new AudioStorageProperties();
        properties.setDirectory(tempDirectory.toString());
        localDiskAudioStorage = new LocalDiskAudioStorage(properties);
    }

    @Test
    @DisplayName("存進去的內容應原樣讀得回來")
    void shouldReadBackWhatWasSaved() throws Exception {
        byte[] content = {1, 2, 3, 4, 5};

        Optional<String> filePath =
                localDiskAudioStorage.save(SpeechLanguageEnum.TH, content, "wav");

        assertThat(filePath).isPresent();

        try (InputStream stream =
                     localDiskAudioStorage.openStream(filePath.get()).orElseThrow()) {
            assertThat(stream.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("回傳的路徑應為「語言資料夾/檔名.副檔名」")
    void shouldReturnPathWithLanguageFolder() {
        Optional<String> filePath =
                localDiskAudioStorage.save(SpeechLanguageEnum.TH, new byte[]{1}, "wav");

        assertThat(filePath).isPresent();
        assertThat(filePath.get()).startsWith("th/");
        assertThat(filePath.get()).endsWith(".wav");
    }

    @Test
    @DisplayName("副檔名應照呼叫端指定的來，OpenAI 那條路存的是 mp3")
    void shouldHonourRequestedExtension() {
        Optional<String> filePath =
                localDiskAudioStorage.save(SpeechLanguageEnum.TH, new byte[]{1}, "mp3");

        assertThat(filePath.orElseThrow()).endsWith(".mp3");
    }

    @Test
    @DisplayName("中文與泰文應存到不同的子資料夾")
    void shouldSeparateLanguagesIntoFolders() {
        Optional<String> thaiPath =
                localDiskAudioStorage.save(SpeechLanguageEnum.TH, new byte[]{1}, "wav");
        Optional<String> chinesePath =
                localDiskAudioStorage.save(SpeechLanguageEnum.ZH, new byte[]{1}, "wav");

        assertThat(thaiPath.orElseThrow()).startsWith("th/");
        assertThat(chinesePath.orElseThrow()).startsWith("zh/");
    }

    @Test
    @DisplayName("讀取不存在的檔案應回傳空值，而非拋出例外")
    void shouldReturnEmptyWhenFileMissing() {
        assertThat(localDiskAudioStorage.openStream("th/notexist.wav")).isEmpty();
    }
}
```

- [ ] **Step 2: 執行測試，確認它失敗**

```powershell
.\mvnw test -Dtest=LocalDiskAudioStorageTest
```

預期：**編譯失敗**，訊息類似 `cannot find symbol: class LocalDiskAudioStorage`。

- [ ] **Step 3: 寫實作**

```java
package com.tim.language_project.client.storage;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  把音檔存在這台電腦的 audio 資料夾裡，本機開發時用的就是它。
 *
 * ── 流程：一段泰文合成完之後，檔案怎麼落地 ────────────────────────────
 *
 *  第 1 步｜GoogleSpeechClient 拿到整理好的 WAV 位元組，呼叫
 *
 *      audioStorage.save(SpeechLanguageEnum.TH, wavBytes, "wav");
 *
 *  第 2 步｜這裡產生一個隨機檔名，並組出相對路徑
 *
 *      "th" + "/" + "a1b2c3d4e5f6.wav"  →  "th/a1b2c3d4e5f6.wav"
 *
 *    ★ 檔名用隨機碼而不是那段泰文，理由有二：
 *      泰文含檔案系統不接受的字元，且同一段文字可能很長。
 *      「哪個檔案對應哪段文字」由 audio_asset 那張表負責記住。
 *
 *  第 3 步｜確保 audio/th 資料夾存在（第一次跑的機器上還沒有），寫檔
 *
 *      C:\Tim\language_project\audio\th\a1b2c3d4e5f6.wav
 *
 *  第 4 步｜回傳 "th/a1b2c3d4e5f6.wav"
 *
 *    ★ 回傳的是相對路徑，不含 audio 這一層，也不含磁碟機代號。
 *      GoogleCloudAudioStorage 回傳的格式必須與此完全相同 ——
 *      這個字串會被寫進 audio_asset.file_path，兩種環境共用同一張表。
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.enums.SpeechLanguageEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "audio.storage.provider", havingValue = "LOCAL", matchIfMissing = true)
public class LocalDiskAudioStorage implements AudioStorage {

    private final AudioStorageProperties audioStorageProperties;

    @Override
    public Optional<String> save(SpeechLanguageEnum language, byte[] content, String extension) {
        String filePath = language.getFolderName() + "/" + newFileName(extension);

        try {
            Path root = Paths.get(audioStorageProperties.getDirectory());
            Files.createDirectories(root.resolve(language.getFolderName()));
            Files.write(root.resolve(filePath), content);

            return Optional.of(filePath);
        } catch (Exception exception) {
            log.error("音檔寫入本機失敗，路徑 {}", filePath, exception);
            return Optional.empty();
        }
    }

    @Override
    public Optional<InputStream> openStream(String filePath) {
        try {
            Path target = Paths.get(audioStorageProperties.getDirectory()).resolve(filePath);

            if (!Files.exists(target)) {
                return Optional.empty();
            }

            return Optional.of(Files.newInputStream(target));
        } catch (Exception exception) {
            log.error("音檔讀取失敗，路徑 {}", filePath, exception);
            return Optional.empty();
        }
    }

    /** 隨機十二碼加副檔名。與原本兩個 SpeechClient 的產生方式一致。 */
    private String newFileName(String extension) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                + "." + extension;
    }
}
```

- [ ] **Step 4: 執行測試，確認通過**

```powershell
.\mvnw test -Dtest=LocalDiskAudioStorageTest
```

預期：**Tests run: 5, Failures: 0, Errors: 0**

- [ ] **Step 5: Commit**

```powershell
git add src\main\java\com\tim\language_project\client\storage\LocalDiskAudioStorage.java src\test\java\com\tim\language_project\client\storage\LocalDiskAudioStorageTest.java
git commit -m @'
新增本機磁碟的音檔儲存實作

Feat:
- 新增 LocalDiskAudioStorage，把既有的寫檔邏輯集中到一處
- 以 audio.storage.provider=LOCAL 啟用，未設定時為預設
- 新增四個測試：存讀往返、路徑格式、語言分資料夾、讀不到時回空值
'@
```

---

## Task 9: 讓兩個 SpeechClient 改用 AudioStorage

**Files:**
- Modify: `src/main/java/com/tim/language_project/client/google/GoogleSpeechClient.java`
- Modify: `src/main/java/com/tim/language_project/client/openai/OpenAiSpeechClient.java`

- [ ] **Step 1: 改 `GoogleSpeechClient` 的建構子與欄位**

把 `AudioStorageProperties` 欄位換成 `AudioStorage`：

改前：

```java
    private final AudioStorageProperties audioStorageProperties;

    public GoogleSpeechClient(RestClient.Builder restClientBuilder,
                              ApiUsageRecorder apiUsageRecorder,
                              GoogleSpeechProperties googleSpeechProperties,
                              AudioStorageProperties audioStorageProperties) {
        this.restClient = restClientBuilder.build();
        this.apiUsageRecorder = apiUsageRecorder;
        this.googleSpeechProperties = googleSpeechProperties;
        this.audioStorageProperties = audioStorageProperties;
    }
```

改後：

```java
    private final AudioStorage audioStorage;

    public GoogleSpeechClient(RestClient.Builder restClientBuilder,
                              ApiUsageRecorder apiUsageRecorder,
                              GoogleSpeechProperties googleSpeechProperties,
                              AudioStorage audioStorage) {
        this.restClient = restClientBuilder.build();
        this.apiUsageRecorder = apiUsageRecorder;
        this.googleSpeechProperties = googleSpeechProperties;
        this.audioStorage = audioStorage;
    }
```

- [ ] **Step 2: 改 `GoogleSpeechClient` 的存檔區塊（原第 183-200 行）**

改前：

```java
        try {
            String filePath = language.getFolderName() + "/" + newFileName();
            Path directory = Paths.get(audioStorageProperties.getDirectory())
                    .resolve(language.getFolderName());

            Files.createDirectories(directory);
            Files.write(Paths.get(audioStorageProperties.getDirectory()).resolve(filePath),
                    tidied.get());

            recordUsage(voiceName, speechText.length(), true);

            return Optional.of(filePath);
        } catch (Exception exception) {
            // 聲音已經拿到了，錢也付了，是我們自己沒存下來。
            recordFailure(SpeechFailureReasonEnum.FILE_SAVE_FAILED, voiceName,
                    speechText.length(), exception);
            return Optional.empty();
        }
```

改後：

```java
        // 存到哪裡由 AudioStorage 決定 —— 本機是 audio 資料夾，雲端是 Cloud Storage。
        // 這裡不需要知道是哪一種，只在乎「有沒有存成功」。
        //
        // ★ 副檔名是 wav 不是 mp3：Google 回的是 LINEAR16，
        //   而且 WavAudio.tidy 處理完仍然是 WAV，中間沒有任何轉檔。
        Optional<String> filePath = audioStorage.save(language, tidied.get(), "wav");

        if (filePath.isEmpty()) {
            // 聲音已經拿到了，錢也付了，是我們自己沒存下來。
            recordFailure(SpeechFailureReasonEnum.FILE_SAVE_FAILED, voiceName,
                    speechText.length(), null);
            return Optional.empty();
        }

        recordUsage(voiceName, speechText.length(), true);

        return filePath;
```

- [ ] **Step 3: 移除 `GoogleSpeechClient` 中不再使用的東西**

刪除這些 import：

```java
import com.tim.language_project.config.AudioStorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
```

加上這個 import：

```java
import com.tim.language_project.client.storage.AudioStorage;
```

刪除 `newFileName()` 方法（原第 271-273 行），它已經搬到 `LocalDiskAudioStorage`。

- [ ] **Step 4: 改 `OpenAiSpeechClient` 的欄位與建構子**

把 `AudioStorageProperties audioStorageProperties` 這個欄位換成 `AudioStorage audioStorage`，建構子參數同步換掉（`OpenAiSpeechClient` 的建構子在第 186 行附近，參數列中把 `AudioStorageProperties audioStorageProperties` 改成 `AudioStorage audioStorage`，指派那行改成 `this.audioStorage = audioStorage;`）。

- [ ] **Step 5: 改 `OpenAiSpeechClient` 的存檔區塊（原第 221-240 行）**

改前：

```java
        try {
            // 相對路徑，例如 th/a1b2c3d4e5f6.mp3。
            // 前端把它接在 /audio/ 後面就是可以直接播放的網址。
            String filePath = language.getFolderName() + "/" + newFileName();
            Path directory = Paths.get(audioStorageProperties.getDirectory())
                    .resolve(language.getFolderName());

            Files.createDirectories(directory);
            Files.write(Paths.get(audioStorageProperties.getDirectory()).resolve(filePath),
                    audioBytes);

            recordUsage(spokenText.length(), true);

            return Optional.of(filePath);
        } catch (Exception exception) {
            // 聲音已經拿到了，錢也付了，是我們自己沒存下來。
            recordFailure(SpeechFailureReasonEnum.FILE_SAVE_FAILED,
                    spokenText.length(), exception);
            return Optional.empty();
        }
```

改後：

```java
        // 存到哪裡由 AudioStorage 決定 —— 本機是 audio 資料夾，雲端是 Cloud Storage。
        //
        // ★ 副檔名是 mp3 不是 wav：OpenAI 依 response-format 設定直接回 mp3，
        //   跟 Google 那條路（LINEAR16 → wav）不一樣，所以要各自明講。
        Optional<String> filePath = audioStorage.save(language, audioBytes, "mp3");

        if (filePath.isEmpty()) {
            // 聲音已經拿到了，錢也付了，是我們自己沒存下來。
            recordFailure(SpeechFailureReasonEnum.FILE_SAVE_FAILED,
                    spokenText.length(), null);
            return Optional.empty();
        }

        recordUsage(spokenText.length(), true);

        return filePath;
```

- [ ] **Step 6: 移除 `OpenAiSpeechClient` 中不再使用的東西**

刪除這些 import：

```java
import com.tim.language_project.config.AudioStorageProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
```

加上這個 import：

```java
import com.tim.language_project.client.storage.AudioStorage;
```

刪除 `newFileName()` 方法（原第 302-304 行）。

★ 注意：`OpenAiSpeechClient` 的變數名是 `spokenText` 與 `audioBytes`，`GoogleSpeechClient` 是 `speechText` 與 `tidied`。兩邊不要互相貼錯。

- [ ] **Step 7: 確認兩個檔案都清乾淨了**

```powershell
Select-String -Path src\main\java\com\tim\language_project\client\google\GoogleSpeechClient.java, src\main\java\com\tim\language_project\client\openai\OpenAiSpeechClient.java -Pattern "AudioStorageProperties|Files\.|newFileName"
```

預期：**沒有任何輸出**。

- [ ] **Step 8: 編譯**

```powershell
.\mvnw -q clean compile
```

預期：BUILD SUCCESS。若出現 `AudioStorageProperties cannot be resolved`，代表還有地方沒清乾淨。

- [ ] **Step 9: 執行全部測試**

```powershell
.\mvnw clean test
```

預期：全數通過。

- [ ] **Step 10: 手動確認音檔仍然正常**

```powershell
.\mvnw spring-boot:run
```

查一句沒查過的中文 → 確認 `audio\th\` 底下有**新增**的 `.wav` 檔，且播得出來。

- [ ] **Step 11: Commit**

```powershell
git add src\main\java\com\tim\language_project\client
git commit -m @'
語音客戶端改用 AudioStorage 存檔

Modify:
- GoogleSpeechClient 與 OpenAiSpeechClient 不再直接操作檔案系統，改呼叫 AudioStorage
- 兩處重複的 newFileName 與 Files.write 集中到 LocalDiskAudioStorage
- 存檔失敗仍記為 FILE_SAVE_FAILED，行為不變
'@
```

---

## Task 10: 音檔改由 Controller 串流輸出

**Files:**
- Create: `src/main/java/com/tim/language_project/controller/AudioFileController.java`
- Modify: `src/main/java/com/tim/language_project/config/WebMvcConfig.java`

**背景：** 目前 `/audio/**` 由 `WebMvcConfig` 的靜態資源處理器直接吐本機檔案。雲端沒有本機檔案，必須改由程式讀。**兩種環境統一走 Controller**，若保留靜態處理器，`/audio/**` 會有兩個處理者，且本機測不到雲端真正會走的那條路。

- [ ] **Step 1: 建立串流 Controller**

```java
package com.tim.language_project.controller;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  把音檔吐給瀏覽器播放。網址是 /audio/th/a1b2c3d4e5f6.wav。
 *
 * ── 為什麼需要它（本機明明可以直接開資料夾給瀏覽器看）─────────────────
 *
 *  因為雲端沒有「資料夾」。Cloud Run 的容器是用完就丟的，音檔在
 *  Cloud Storage 上，瀏覽器沒辦法直接指著它。所以要有人去讀出來再轉交。
 *
 *  ★ 那為什麼本機也走這裡，而不是本機走資料夾、雲端走程式？
 *    因為那樣本機就永遠測不到雲端真正會走的那條路。
 *    兩邊走同一條，本機測得過才有意義。
 *
 * ── 流程：你在畫面上按下播放鍵 ─────────────────────────────────────────
 *
 *  第 1 步｜瀏覽器發出請求
 *
 *      GET /audio/th/a1b2c3d4e5f6.wav
 *
 *  第 2 步｜取出 /audio/ 後面那一整段當成相對路徑
 *
 *      "th/a1b2c3d4e5f6.wav"
 *
 *    ★ 為什麼用 ** 而不是兩個 @PathVariable：
 *      路徑固定是「語言/檔名」兩層沒錯，但用萬用字元接住整段，
 *      這一層就不需要知道路徑長什麼樣子 —— 那是 AudioStorage 的事。
 *
 *  第 3 步｜跟 AudioStorage 要一條讀取串流
 *
 *      本機 → 從 audio\th\a1b2c3d4e5f6.wav 開檔案串流
 *      雲端 → 從 Cloud Storage 開下載串流
 *
 *  第 4 步｜包成 InputStreamResource 回傳
 *
 *    ★ 這一步是記憶體安全的關鍵。
 *      如果改成 Files.readAllBytes 再回傳 byte[]，整個檔案會攤在記憶體裡，
 *      三個人同時播三個長句子就是三份疊加。
 *      InputStreamResource 是邊讀邊吐，記憶體只用一個小緩衝區，
 *      跟檔案多大、多少人同時聽都無關。
 *
 *  第 5 步｜檔案不存在時回 404
 *
 *      交給既有的 BusinessException + GlobalExceptionHandler，
 *      沿用 ErrorCodeEnum.AUDIO_FILE_NOT_FOUND，不另外發明錯誤格式。
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.tim.language_project.client.storage.AudioContentType;
import com.tim.language_project.client.storage.AudioStorage;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;

import java.io.InputStream;

@RestController
@RequiredArgsConstructor
public class AudioFileController {

    private static final String AUDIO_PREFIX = "/audio/";

    private final AudioStorage audioStorage;

    @GetMapping("/audio/**")
    public ResponseEntity<InputStreamResource> download(HttpServletRequest request) {
        String filePath = extractFilePath(request);

        InputStream stream = audioStorage.openStream(filePath)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.AUDIO_FILE_NOT_FOUND));

        return ResponseEntity.ok()
                // 依副檔名決定型別。Google 那條路存的是 wav、OpenAI 是 mp3，
                // 給錯的話有些瀏覽器會變成下載檔案而不是播放。
                .contentType(MediaType.parseMediaType(AudioContentType.ofPath(filePath)))
                // 音檔內容永遠不變（檔名是隨機碼，改內容就是新檔名），
                // 所以讓瀏覽器盡量快取，重播同一個詞不必再下載一次。
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .body(new InputStreamResource(stream));
    }

    /** 取出 /audio/ 之後的整段，例如 th/a1b2c3d4e5f6.wav。 */
    private String extractFilePath(HttpServletRequest request) {
        String fullPath = (String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        return fullPath.substring(AUDIO_PREFIX.length());
    }
}
```

- [ ] **Step 2: 移除 `WebMvcConfig` 的靜態資源對應**

整份 `WebMvcConfig.java` 的程式碼部分替換為：

```java
@Configuration
@EnableConfigurationProperties({AudioStorageProperties.class, GoogleSpeechProperties.class})
public class WebMvcConfig {
}
```

並把檔案開頭的 Javadoc 改成：

```java
/**
 * 註冊設定類別。
 *
 * ★ 2026-08-15 移除了 /audio/** 的靜態資源對應。
 *   原本音檔是由 Spring 直接把本機資料夾吐出去，但雲端沒有本機資料夾
 *   （容器是用完就丟的，音檔在 Cloud Storage）。
 *   改由 AudioFileController 統一處理，本機與雲端走同一條路徑，
 *   本機測得過的行為才等於雲端的行為。
 */
```

移除這些不再需要的 import：

```java
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Paths;
```

- [ ] **Step 3: 編譯**

```powershell
.\mvnw -q clean compile
```

預期：BUILD SUCCESS。

- [ ] **Step 4: 手動驗證音檔仍播得出來**

```powershell
.\mvnw spring-boot:run
```

先取一個既有音檔的路徑：

```powershell
docker exec postgres psql -U postgres -d language_project -t -c "SELECT file_path FROM audio_asset LIMIT 1;"
```

用瀏覽器開 `http://localhost:8080/audio/<上面查到的路徑>`，預期**直接播出聲音**。

再開 <http://localhost:4200> 查一句話並按播放，預期正常。

- [ ] **Step 5: 驗證不存在的檔案回 404**

瀏覽器開 `http://localhost:8080/audio/th/notexist.wav`

預期：HTTP 404，回傳既有的錯誤 JSON 格式（含「找不到音檔」）。

- [ ] **Step 6: 執行全部測試**

```powershell
.\mvnw clean test
```

預期：全數通過。

- [ ] **Step 7: Commit**

```powershell
git add src\main\java\com\tim\language_project\controller\AudioFileController.java src\main\java\com\tim\language_project\config\WebMvcConfig.java
git commit -m @'
音檔改由 Controller 串流輸出

Feat:
- 新增 AudioFileController，以 InputStreamResource 串流輸出音檔，記憶體佔用與檔案大小無關
- 音檔加上長期快取標頭，檔名為隨機碼故內容不會變動

Modify:
- WebMvcConfig 移除 /audio/** 靜態資源對應
- 兩種環境統一走 Controller，本機才測得到雲端實際會走的路徑
'@
```

---

## Task 11: 設定項加上 provider 開關

**Files:**
- Modify: `src/main/java/com/tim/language_project/config/AudioStorageProperties.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: 加入 provider 欄位**

在 `AudioStorageProperties` 的 `directory` 欄位上方插入：

```java
    /**
     * 音檔要存到哪裡。LOCAL / GCS。
     *
     * ★ 與 speech.provider 是同一種設計：兩份實作都在，切這一行就換人。
     *   LOCAL 走 LocalDiskAudioStorage（本機的 audio 資料夾）
     *   GCS   走 GoogleCloudAudioStorage（Cloud Storage）
     */
    private String provider = "LOCAL";
```

- [ ] **Step 2: 在 `application.yml` 的 `audio.storage` 區塊加上說明與預設值**

在 `audio.storage.directory` 上方插入：

```yaml
    # 音檔存到哪裡。LOCAL / GCS，兩份實作都在，切這一行就換人。
    #
    # ★ 與 speech.provider 是同一種設計。
    #   本機維持 LOCAL；雲端由 application-prod.yml 覆寫為 GCS。
    provider: LOCAL
```

- [ ] **Step 3: 執行全部測試**

```powershell
.\mvnw clean test
```

預期：全數通過。

- [ ] **Step 4: Commit**

```powershell
git add src\main\java\com\tim\language_project\config\AudioStorageProperties.java src\main\resources\application.yml
git commit -m @'
音檔儲存加上 provider 設定開關

Feat:
- AudioStorageProperties 新增 provider 欄位，預設 LOCAL
- application.yml 補上設定與說明，與既有的 speech.provider 同一種設計
'@
```

---

# 階段 3：登入保護

## Task 12: 加入 Spring Security 並設定 profile 差異

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/tim/language_project/config/SecurityConfig.java`
- Create: `src/main/resources/application-prod.yml`

- [ ] **Step 1: 加入相依性**

在 `pom.xml` 的 `<dependencies>` 內，`spring-boot-starter-webmvc` 之後插入：

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
```

並在測試相依性區塊加入：

```xml
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: 建立 SecurityConfig**

```java
package com.tim.language_project.config;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  決定「誰可以打開這個網站」。
 *
 * ── 為什麼需要它 ───────────────────────────────────────────────────────
 *
 *  在自己電腦上跑的時候完全不需要。但一旦有了公開網址，情況變成：
 *  網路上任何人只要知道網址，就能無限次呼叫翻譯功能，
 *  而每一次都是從你的帳戶扣錢。
 *
 *  ★ 要防的不是「朋友之間互相偷看」，是「機器人掃到網址狂刷」。
 *    網路上有程式整天在掃描新出現的網址，這種事幾天內就會發生。
 *
 * ── 兩種環境，兩種行為 ─────────────────────────────────────────────────
 *
 *  local（你自己的電腦）→ 全部放行。開發時每次重啟都要登入很煩，
 *                          而且 localhost 本來就只有你連得到。
 *
 *  prod （雲端）        → 除了健康檢查以外，全部都要先登入。
 *
 * ── 流程：第一次打開網站 ───────────────────────────────────────────────
 *
 *  第 1 步｜你在手機輸入網址，瀏覽器發出 GET /
 *
 *  第 2 步｜Spring Security 發現這個請求沒有帶身分，擋下來
 *
 *  第 3 步｜跳出瀏覽器內建的帳密輸入框（HTTP Basic）
 *
 *    ★ 為什麼用 Basic 而不是做一個好看的登入頁：
 *      Basic 是瀏覽器內建的，零前端工作，而且手機上「加到主畫面」之後
 *      只要輸入一次就會記住。做登入頁要多花好幾小時，換來的只是比較好看。
 *      使用者只有幾個認識的人，不值得。
 *
 *  第 4 步｜輸入正確後，之後的請求都會自動帶著身分，不用再輸入
 *
 * ── 帳密從哪裡來 ───────────────────────────────────────────────────────
 *
 *  環境變數 APP_USERNAME 與 APP_PASSWORD，在 Cloud Run 後台設定。
 *  ★ 絕對不可以寫死在程式碼或進版控的設定檔裡。
 *
 *  密碼在記憶體中以 BCrypt 雜湊保存。BCrypt 是不可逆的 ——
 *  就算有人拿到雜湊值也還原不出原始密碼。
 * ══════════════════════════════════════════════════════════════════════════
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 雲端：除了健康檢查以外都要登入。
     */
    @Bean
    @Profile("prod")
    public SecurityFilterChain prodFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        // Cloud Run 用來確認容器活著，不能要求登入。
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> {
                })
                // 這個站沒有「以他人身分送出表單」的攻擊面（沒有 cookie 型的登入狀態，
                // 每個請求各自帶 Basic 認證），且前端是純 API 呼叫，故關閉 CSRF。
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /**
     * 本機：全部放行，開發時不受干擾。
     */
    @Bean
    @Profile("local")
    public SecurityFilterChain localFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /**
     * 唯一的那組帳密，來自環境變數。
     * local profile 用不到這個 Bean，但建立它沒有成本，故不特別區分。
     */
    @Bean
    public UserDetailsService userDetailsService(
            @Value("${app.auth.username:awei}") String username,
            @Value("${app.auth.password:local-only}") String password,
            PasswordEncoder passwordEncoder) {

        UserDetails user = User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("USER")
                .build();

        return new InMemoryUserDetailsManager(user);
    }
}
```

- [ ] **Step 3: 建立 `application-prod.yml`**

```yaml
# 雲端專用設定。★ 這個檔案會進版控，所以裡面不可以有任何密碼或金鑰。
#
# 所有敏感值都寫成 ${環境變數名稱}，實際的值在 Cloud Run 後台設定。
# 少填任何一個，程式會在啟動時就失敗 —— 這是刻意的，
# 總比跑起來之後才在查詢時炸掉好。

spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  ai:
    openai:
      api-key: ${OPENAI_API_KEY}

google:
  speech:
    api-key: ${GOOGLE_SPEECH_API_KEY}

audio:
  storage:
    # 雲端沒有本機資料夾，容器是用完就丟的。
    provider: GCS

    # Cloud Storage 的 bucket 名稱。
    bucket: ${GCS_BUCKET}

app:
  auth:
    username: ${APP_USERNAME}
    password: ${APP_PASSWORD}

# Cloud Run 需要一個健康檢查端點確認容器活著。
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
```

- [ ] **Step 4: 加入 actuator 相依性**

`application-prod.yml` 用到 `management.endpoints`，需要在 `pom.xml` 加入：

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
```

- [ ] **Step 5: 執行全部測試**

```powershell
.\mvnw clean test
```

預期：全數通過。

★ 若測試因為 Security 開始要求認證而失敗，代表某個測試在跑 prod profile。檢查失敗的測試類別上有沒有 `@ActiveProfiles("prod")`，本專案的測試都應該用 local。

- [ ] **Step 6: 確認本機仍然不用登入**

```powershell
.\mvnw spring-boot:run
```

開 <http://localhost:4200> 查一句話，預期**沒有跳出帳密輸入框**。

- [ ] **Step 7: Commit**

```powershell
git add pom.xml src\main\java\com\tim\language_project\config\SecurityConfig.java src\main\resources\application-prod.yml
git commit -m @'
新增登入保護與雲端設定檔

Feat:
- 新增 SecurityConfig：prod 要求 HTTP Basic 認證、local 全部放行
- 帳密取自環境變數，以 BCrypt 雜湊保存於記憶體
- 新增 application-prod.yml，所有敏感值皆為環境變數佔位
- 加入 actuator 提供 /actuator/health 供 Cloud Run 健康檢查

Modify:
- pom.xml 加入 spring-boot-starter-security 與 spring-boot-starter-actuator
'@
```

---

## Task 13: 為 SecurityConfig 補測試

**Files:**
- Test: `src/test/java/com/tim/language_project/config/SecurityConfigTest.java`

- [ ] **Step 1: 寫測試**

```java
package com.tim.language_project.config;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  防兩個方向都會出事的錯誤：
 *
 *    ① prod 忘了擋 → 公開網址上任何人都能狂刷 API，直接燒錢
 *    ② local 誤擋   → 每次開發重啟都要輸帳密，煩到最後有人把整個 Security 拿掉
 *
 *  ★ ① 特別危險，因為它「看起來一切正常」——
 *    網站能用、沒有錯誤訊息，只是門是開的。
 *
 * ── 假的東西 ───────────────────────────────────────────────────────────
 *
 *  用 @WebMvcTest 只載入 Web 這一層，不碰資料庫也不呼叫 OpenAI。
 *  這裡要驗的是「有沒有被擋下來」，請求後面接什麼完全不重要。
 *  AudioStorage 用 @MockitoBean 換掉，因為 AudioFileController 需要它才能建立。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  1. prod 未帶帳密 → 401     防止門沒關
 *  2. prod 帶對帳密 → 不是 401 確認擋人的規則沒有把自己人也擋掉
 *  3. prod 健康檢查 → 放行     Cloud Run 探測不到會一直重啟容器
 *  4. local 未帶帳密 → 不是 401 防止開發被干擾
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.tim.language_project.client.storage.AudioStorage;
import com.tim.language_project.controller.AudioFileController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class SecurityConfigTest {

    @Nested
    @WebMvcTest(AudioFileController.class)
    @Import(SecurityConfig.class)
    @ActiveProfiles("prod")
    @DisplayName("雲端環境")
    class ProdProfile {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AudioStorage audioStorage;

        @Test
        @DisplayName("沒帶帳號密碼應回 401")
        void shouldRejectAnonymousRequest() throws Exception {
            int status = mockMvc.perform(get("/audio/th/any.wav"))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("帶對帳號密碼就不該被擋在門外")
        void shouldAcceptCorrectCredentials() throws Exception {
            int status = mockMvc.perform(get("/audio/th/any.wav")
                            .with(httpBasic("awei", "local-only")))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("健康檢查端點不可要求登入，否則 Cloud Run 會一直重啟容器")
        void shouldAllowHealthEndpoint() throws Exception {
            int status = mockMvc.perform(get("/actuator/health"))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
    }

    @Nested
    @WebMvcTest(AudioFileController.class)
    @Import(SecurityConfig.class)
    @ActiveProfiles("local")
    @DisplayName("本機環境")
    class LocalProfile {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AudioStorage audioStorage;

        @Test
        @DisplayName("不帶帳號密碼也不該被擋，開發時不受干擾")
        void shouldAllowAnonymousRequest() throws Exception {
            int status = mockMvc.perform(get("/audio/th/any.wav"))
                    .andReturn().getResponse().getStatus();

            assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
    }
}
```

- [ ] **Step 2: 執行測試**

```powershell
.\mvnw test -Dtest=SecurityConfigTest
```

預期：**Tests run: 4, Failures: 0**

★ 若 `prod` 的測試因為缺少環境變數而啟動失敗，那是因為 `application-prod.yml` 的 `${DB_URL}` 等佔位符無值。此時在測試類別上補：

```java
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/language_project",
        "spring.datasource.username=postgres",
        "spring.datasource.password=Postgres123456",
        "app.auth.username=awei",
        "app.auth.password=local-only"
})
```

- [ ] **Step 3: 執行全部測試**

```powershell
.\mvnw clean test
```

預期：全數通過。

- [ ] **Step 4: Commit**

```powershell
git add src\test\java\com\tim\language_project\config\SecurityConfigTest.java
git commit -m @'
新增登入保護的測試

Feat:
- SecurityConfigTest 驗證 prod 擋匿名請求、放行正確帳密與健康檢查端點
- 同時驗證 local 不擋任何請求，避免開發被干擾
'@
```

---

# 階段 4：Dockerfile 與前端併入

## Task 14: 建立多階段 Dockerfile

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`

- [ ] **Step 1: 建立 `.dockerignore`**

```
# 建置產物與相依套件，容器內會自己重新產生
target/
frontend/node_modules/
frontend/dist/
frontend/.angular/

# 本機音檔，雲端用 Cloud Storage
audio/

# ★ 本機設定含金鑰，絕對不可以進到映像檔裡
src/main/resources/application-local.yml

# 版控與 IDE
.git/
.idea/
.settings/
.vscode/
*.iml

# 舊資料庫的匯出備份
db/backup/
```

- [ ] **Step 2: 建立 `Dockerfile`**

```dockerfile
# ══════════════════════════════════════════════════════════════════════════
#  這個檔案負責什麼
# ══════════════════════════════════════════════════════════════════════════
#
#  把「一堆原始碼」變成「一個可以直接執行的東西」。
#
#  這個專案有兩種技術：Angular 要用 Node 建置，Spring Boot 要用 Maven 建置，
#  而且前端建置出來的檔案要塞進後端。所以說明書分三段。
#
#  ★ 為什麼要「多階段」：
#    建置時需要 Node 和 Maven，但執行時完全用不到它們。
#    分階段之後，那些工具不會被帶進最後的成品 ——
#    映像檔從 1GB 以上縮到 300MB 左右，啟動更快，可被攻擊的面積也更小。
# ══════════════════════════════════════════════════════════════════════════

# ── 第一階段：建置 Angular ────────────────────────────────────────────────
FROM node:22-alpine AS frontend-build

WORKDIR /build

# 先只複製相依定義再安裝。
# ★ 這是為了讓 Docker 的快取生效：只要 package.json 沒變，
#   下次建置就直接沿用上一次裝好的 node_modules，省好幾分鐘。
#   如果一開始就 COPY 全部，改一行 CSS 也會重裝全部套件。
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# 產出落在 /build/dist/frontend/browser/


# ── 第二階段：建置 Spring Boot ────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS backend-build

WORKDIR /build

# 同樣先只複製 pom.xml 讓相依套件的下載能被快取。
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src/ ./src/

# ★ 關鍵的一步：把前端的產出塞進 Spring Boot 的靜態資源資料夾。
#   放進 static/ 之後，Spring Boot 會把它當成一般的網頁檔案直接吐出去，
#   前端和後端從此是同一個網址，不會有跨網址被瀏覽器阻擋的問題。
COPY --from=frontend-build /build/dist/frontend/browser/ ./src/main/resources/static/

# 測試在 CI 或本機跑，這裡不重複跑：
# 建置階段沒有資料庫可連，而本專案的 Repository 測試打的是真資料庫。
RUN mvn -B clean package -DskipTests


# ── 第三階段：執行環境 ────────────────────────────────────────────────────
# 只有 Java，沒有 Maven、沒有 Node、沒有原始碼。
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# 不用 root 執行。萬一程式被攻破，攻擊者拿到的權限也有限。
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=backend-build /build/target/*.jar app.jar

# Cloud Run 會透過 PORT 環境變數告訴容器要聽哪個埠，預設 8080。
ENV PORT=8080
EXPOSE 8080

# ★ -XX:MaxRAMPercentage 讓 JVM 依「容器實際分配到多少記憶體」自動調整堆大小。
#   寫死 -Xmx 的話，之後在 Cloud Run 調整記憶體規格就得跟著改這裡，很容易忘。
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT} -jar app.jar"]
```

- [ ] **Step 3: 建置映像檔**

```powershell
docker build -t thailan:local .
```

預期：三個階段依序完成，最後 `Successfully tagged thailan:local`。

★ 第一次建置要下載 Node、Maven、JRE 映像，可能需要 10～15 分鐘。

- [ ] **Step 4: 確認映像檔大小合理**

```powershell
docker images thailan:local --format "{{.Size}}"
docker history thailan:local --format "{{.Size}}`t{{.CreatedBy}}"
```

2026-08-15 實測：`docker images` 報 **546MB**，但那個數字含 buildx 的 attestation 額外資料；`docker history` 的分層加總才是實際的 **約 350MB**（jar 137MB ＋ JRE 165MB ＋ 字型等 37MB ＋ Alpine 9MB）。

★ 判斷多階段有沒有生效**要看分層內容而不是總數字**：最終映像檔的層裡若出現 maven 或 node 相關指令，就是第三階段用錯了基底映像。

★ 也要驗證前端真的被打包進去了，否則部署後會是一片白頁：

```powershell
docker run --rm --entrypoint sh thailan:local -c "unzip -l app.jar | grep -E 'static/(index.html|main-)'"
```

預期：列出 `BOOT-INF/classes/static/index.html` 與 `static/main-*.js`。

★ 並確認金鑰沒被打包進去：

```powershell
docker run --rm --entrypoint sh thailan:local -c "unzip -p app.jar BOOT-INF/classes/application-local.yml 2>/dev/null | wc -l"
```

預期：**0**。（`application-local.yml.example` 會在裡面，那是只有佔位文字的範本，沒有問題。）

- [ ] **Step 5: Commit**

```powershell
git add Dockerfile .dockerignore
git commit -m @'
新增多階段 Dockerfile

Feat:
- 三階段建置：Node 建置 Angular、Maven 建置 Spring Boot、精簡 JRE 執行
- 前端產出複製進 static/，前後端合併為單一網址
- 以非 root 使用者執行，並用 MaxRAMPercentage 讓 JVM 依容器配額自動調整
- .dockerignore 排除 application-local.yml，避免金鑰進入映像檔
'@
```

---

## Task 15: 在本機用 Docker 完整跑起來

**Files:** 無（驗證用）

**這是階段 4 的關鍵里程碑。** 這一步能通過，上雲端就只剩「換個地方執行」的問題。

- [ ] **Step 1: 建立一個給容器用的 Docker 網路**

容器內的 `localhost` 是容器自己，連不到本機的 PostgreSQL，所以要讓兩個容器在同一個網路上。

```powershell
docker network create thailan-net
docker network connect thailan-net postgres
```

- [ ] **Step 2: 以 prod profile 啟動容器**

★ 這裡刻意用 prod，就是要在本機驗證雲端的行為（含登入保護）。

把 `你的OpenAI金鑰` 與 `你的Google金鑰` 換成 `application-local.yml` 裡的實際值。

```powershell
docker run -d --name thailan --network thailan-net -p 8081:8080 `
  -e SPRING_PROFILES_ACTIVE=prod `
  -e DB_URL=jdbc:postgresql://postgres:5432/language_project `
  -e DB_USERNAME=postgres `
  -e DB_PASSWORD=Postgres123456 `
  -e OPENAI_API_KEY=你的OpenAI金鑰 `
  -e GOOGLE_SPEECH_API_KEY=你的Google金鑰 `
  -e GCS_BUCKET=dummy-not-used-yet `
  -e APP_USERNAME=awei `
  -e APP_PASSWORD=測試用密碼 `
  thailan:local
```

★ 這一步預期**會失敗**：`provider: GCS` 但 `GoogleCloudAudioStorage` 還沒實作（Task 16 才做）。先確認失敗訊息是「找不到 AudioStorage 的實作」而不是別的問題。

- [ ] **Step 3: 看日誌確認失敗原因**

```powershell
docker logs thailan --tail 40
```

預期看到類似 `No qualifying bean of type 'AudioStorage'`。

- [ ] **Step 4: 暫時改用 LOCAL provider 驗證其餘部分**

```powershell
docker rm -f thailan
docker run -d --name thailan --network thailan-net -p 8081:8080 `
  -e SPRING_PROFILES_ACTIVE=prod `
  -e AUDIO_STORAGE_PROVIDER=LOCAL `
  -e DB_URL=jdbc:postgresql://postgres:5432/language_project `
  -e DB_USERNAME=postgres `
  -e DB_PASSWORD=Postgres123456 `
  -e OPENAI_API_KEY=你的OpenAI金鑰 `
  -e GOOGLE_SPEECH_API_KEY=你的Google金鑰 `
  -e GCS_BUCKET=dummy-not-used-yet `
  -e APP_USERNAME=awei `
  -e APP_PASSWORD=測試用密碼 `
  thailan:local
```

- [ ] **Step 5: 確認容器活著**

```powershell
docker logs thailan --tail 30
```

預期看到 `Started LanguageProjectApplication in X seconds`。

- [ ] **Step 6: ★ 驗證登入保護真的在擋**

瀏覽器開 <http://localhost:8081>

預期：**跳出帳號密碼輸入框**。輸入 `awei` / `測試用密碼` 後進入畫面。

★ 如果沒跳出來，代表 prod profile 沒生效或 SecurityConfig 有問題，**停下來處理再繼續**。

- [ ] **Step 7: 驗證前端確實被打包進去了**

登入後應直接看到查詢畫面（不是 Spring 的白頁或 404）。這證明第一階段的 Angular 產出正確落進了 `static/`。

- [ ] **Step 8: 驗證完整流程**

查一句**沒查過**的中文 → 確認有泰文、拼音、逐詞 → 按播放確認有聲音。

- [ ] **Step 9: 量測實際記憶體用量**

```powershell
docker stats thailan --no-stream --format "{{.MemUsage}}"
```

記下數字，Task 18 選 Cloud Run 規格時用。

- [ ] **Step 10: 收拾**

```powershell
docker rm -f thailan
```

---

## Task 16: 實作 GoogleCloudAudioStorage

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/tim/language_project/client/storage/GoogleCloudAudioStorage.java`
- Modify: `src/main/java/com/tim/language_project/config/AudioStorageProperties.java`
- Test: `src/test/java/com/tim/language_project/client/storage/GoogleCloudAudioStorageTest.java`

- [ ] **Step 1: 加入相依性**

在 `pom.xml` 的 `<dependencies>` 加入：

```xml
        <dependency>
            <groupId>com.google.cloud</groupId>
            <artifactId>google-cloud-storage</artifactId>
            <version>2.43.2</version>
        </dependency>
```

★ 版本號請以 <https://central.sonatype.com/artifact/com.google.cloud/google-cloud-storage> 上的最新穩定版為準，上面是撰寫時的版本。

- [ ] **Step 2: 加入 bucket 設定欄位**

在 `AudioStorageProperties` 的 `provider` 欄位下方插入：

```java
    /**
     * Cloud Storage 的 bucket 名稱。provider 為 GCS 時才會用到。
     */
    private String bucket;
```

- [ ] **Step 3: 先寫失敗的測試**

```java
package com.tim.language_project.client.storage;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  防「雲端版的路徑格式跟本機版分家」，以及「上傳失敗時炸出例外」。
 *
 *  路徑格式為什麼要緊：save 回傳的字串會被寫進 audio_asset.file_path。
 *  如果本機回 "th/x.wav"、雲端回 "audio/th/x.wav" 或完整網址，
 *  同一張表就會混進兩種格式，之後誰也讀不對。
 *
 * ── 假的東西 ───────────────────────────────────────────────────────────
 *
 *  整個 Google Storage 用 Mockito 換成假的。
 *
 *  ★ 為什麼不打真的 Cloud Storage：
 *    那要網路、要金鑰、要花錢、而且會在正式的 bucket 裡留下垃圾檔案。
 *    這裡要驗的是「我們有沒有用正確的參數去呼叫它」，不是 Google 會不會壞。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  1. 路徑格式與本機一致  → 防兩個實作分家
 *  2. 上傳到正確的 bucket 與物件名稱 → 防檔案被丟到錯的地方
 *  3. 上傳失敗回空值而非拋例外 → 呼叫端靠 Optional 判斷，拋例外會讓整個查詢失敗
 *  4. 讀不到檔案回空值    → 同上，且 AudioFileController 靠這個回 404
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.enums.SpeechLanguageEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleCloudAudioStorageTest {

    private static final String BUCKET = "thailan-audio-test";

    private Storage storage;

    private GoogleCloudAudioStorage googleCloudAudioStorage;

    @BeforeEach
    void setUp() {
        storage = mock(Storage.class);

        AudioStorageProperties properties = new AudioStorageProperties();
        properties.setBucket(BUCKET);

        googleCloudAudioStorage = new GoogleCloudAudioStorage(storage, properties);
    }

    @Test
    @DisplayName("回傳的路徑格式應與本機實作一致：語言資料夾/檔名.wav")
    void shouldReturnSamePathFormatAsLocal() {
        Optional<String> filePath =
                googleCloudAudioStorage.save(SpeechLanguageEnum.TH, new byte[]{1}, "wav");

        assertThat(filePath).isPresent();
        assertThat(filePath.get()).startsWith("th/");
        assertThat(filePath.get()).endsWith(".wav");
    }

    @Test
    @DisplayName("應上傳到設定的 bucket，且物件名稱等於回傳的路徑")
    void shouldUploadToConfiguredBucket() {
        Optional<String> filePath =
                googleCloudAudioStorage.save(SpeechLanguageEnum.ZH, new byte[]{1, 2}, "wav");

        ArgumentCaptor<BlobInfo> captor = ArgumentCaptor.forClass(BlobInfo.class);
        verify(storage).create(captor.capture(), any(byte[].class));

        assertThat(captor.getValue().getBucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().getName()).isEqualTo(filePath.orElseThrow());
    }

    @Test
    @DisplayName("上傳失敗應回傳空值，不可拋出例外")
    void shouldReturnEmptyWhenUploadFails() {
        when(storage.create(any(BlobInfo.class), any(byte[].class)))
                .thenThrow(new StorageException(500, "boom"));

        assertThat(googleCloudAudioStorage.save(SpeechLanguageEnum.TH, new byte[]{1}, "wav"))
                .isEmpty();
    }

    @Test
    @DisplayName("檔案不存在時應回傳空值，AudioFileController 靠它回 404")
    void shouldReturnEmptyWhenBlobMissing() {
        when(storage.get(any(BlobId.class))).thenReturn(null);

        assertThat(googleCloudAudioStorage.openStream("th/notexist.wav")).isEmpty();
    }
}
```

- [ ] **Step 4: 執行測試確認失敗**

```powershell
.\mvnw test -Dtest=GoogleCloudAudioStorageTest
```

預期：編譯失敗，`cannot find symbol: class GoogleCloudAudioStorage`。

- [ ] **Step 5: 寫實作**

```java
package com.tim.language_project.client.storage;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  把音檔存到 Google Cloud Storage，雲端執行時用的就是它。
 *
 * ── 為什麼雲端不能像本機一樣存資料夾 ───────────────────────────────────
 *
 *  Cloud Run 的容器是「用完就丟的紙杯」。每次重新部署、平台維護、
 *  或容器自動重啟，都會換一個新的容器，舊容器裡的檔案全部消失。
 *
 *  ★ 後果不只是「檔案不見了」，而是「程式會發現 audio_asset 有紀錄
 *    但檔案讀不到，於是重新合成一次」—— 每次重啟都重付一次錢。
 *
 *  Cloud Storage 是獨立於容器之外的儲存空間，容器換幾次都不受影響。
 *
 * ── 流程：合成好的音檔怎麼上雲 ─────────────────────────────────────────
 *
 *  第 1 步｜GoogleSpeechClient 呼叫
 *
 *      audioStorage.save(SpeechLanguageEnum.TH, wavBytes, "wav");
 *
 *  第 2 步｜產生與本機版完全相同格式的路徑
 *
 *      "th/a1b2c3d4e5f6.wav"
 *
 *    ★ 這個格式必須跟 LocalDiskAudioStorage 一模一樣，因為它會被寫進
 *      audio_asset.file_path，而那張表兩種環境共用同一種語意。
 *
 *  第 3 步｜上傳。在 Cloud Storage 的術語裡，一個檔案叫一個 blob，
 *          bucket 則是裝 blob 的桶子（相當於一個獨立的儲存空間）。
 *
 *      BlobId  = (bucket名稱, "th/a1b2c3d4e5f6.wav")
 *      storage.create(blobInfo, content)
 *
 *    ★ 名稱裡的斜線只是名字的一部分，Cloud Storage 其實沒有真正的資料夾。
 *      但後台介面會依斜線顯示成資料夾，看起來跟本機一樣。
 *
 *  第 4 步｜回傳 "th/a1b2c3d4e5f6.wav"
 *
 * ── 身分怎麼來 ─────────────────────────────────────────────────────────
 *
 *  沒有金鑰檔。StorageOptions.getDefaultInstance() 會自動抓「這個程式
 *  正在用誰的身分執行」—— 在 Cloud Run 上就是它綁定的服務帳號。
 *
 *  ★ 這是雲端內部服務互相呼叫的標準做法，比帶著金鑰檔安全得多，
 *    因為根本沒有金鑰可以外流。前提是那個服務帳號要有 bucket 的權限，
 *    那一步在部署時設定（見實作計畫 Task 19）。
 * ══════════════════════════════════════════════════════════════════════════
 */

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.tim.language_project.config.AudioStorageProperties;
import com.tim.language_project.enums.SpeechLanguageEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "audio.storage.provider", havingValue = "GCS")
public class GoogleCloudAudioStorage implements AudioStorage {

    private final Storage storage;

    private final AudioStorageProperties audioStorageProperties;

    @Override
    public Optional<String> save(SpeechLanguageEnum language, byte[] content, String extension) {
        String filePath = language.getFolderName() + "/" + newFileName(extension);

        try {
            BlobId blobId = BlobId.of(audioStorageProperties.getBucket(), filePath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(AudioContentType.of(extension))
                    .build();

            storage.create(blobInfo, content);

            return Optional.of(filePath);
        } catch (Exception exception) {
            log.error("音檔上傳 Cloud Storage 失敗，路徑 {}", filePath, exception);
            return Optional.empty();
        }
    }

    @Override
    public Optional<InputStream> openStream(String filePath) {
        try {
            Blob blob = storage.get(
                    BlobId.of(audioStorageProperties.getBucket(), filePath));

            if (Objects.isNull(blob)) {
                return Optional.empty();
            }

            // getContent 會把整個 blob 讀進記憶體。對這個專案是可接受的：
            // 單一音檔最大不過幾百 KB，而 Cloud Storage 的串流讀取 API
            // 需要額外處理 channel 的生命週期，複雜度不划算。
            // ★ 若日後音檔改為長篇朗讀，這裡要改成 blob.reader() 的串流版本。
            return Optional.of(new ByteArrayInputStream(blob.getContent()));
        } catch (Exception exception) {
            log.error("音檔自 Cloud Storage 讀取失敗，路徑 {}", filePath, exception);
            return Optional.empty();
        }
    }

    /** 隨機十二碼加副檔名。★ 必須與 LocalDiskAudioStorage 完全一致。 */
    private String newFileName(String extension) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                + "." + extension;
    }
}
```

- [ ] **Step 6: 建立 Storage Bean**

Create: `src/main/java/com/tim/language_project/config/GoogleCloudStorageConfig.java`

```java
package com.tim.language_project.config;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 建立 Cloud Storage 的連線物件。
 *
 * ★ 只有 audio.storage.provider=GCS 時才建立。
 *   本機開發時這個 Bean 不存在，因此不需要任何 Google 憑證，
 *   也不會因為連不上而拖慢啟動。
 *
 * getDefaultInstance 會自動使用「目前這個程式的執行身分」——
 * 在 Cloud Run 上就是它綁定的服務帳號，不需要金鑰檔。
 */
@Configuration
@ConditionalOnProperty(name = "audio.storage.provider", havingValue = "GCS")
public class GoogleCloudStorageConfig {

    @Bean
    public Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }
}
```

- [ ] **Step 7: 執行測試**

```powershell
.\mvnw test -Dtest=GoogleCloudAudioStorageTest
```

預期：**Tests run: 4, Failures: 0**

- [ ] **Step 8: 執行全部測試**

```powershell
.\mvnw clean test
```

預期：全數通過。

- [ ] **Step 9: 重新建置映像檔**

```powershell
docker build -t thailan:local .
```

- [ ] **Step 10: Commit**

```powershell
git add pom.xml src\main\java\com\tim\language_project\client\storage\GoogleCloudAudioStorage.java src\main\java\com\tim\language_project\config src\test\java\com\tim\language_project\client\storage\GoogleCloudAudioStorageTest.java
git commit -m @'
新增 Cloud Storage 的音檔儲存實作

Feat:
- 新增 GoogleCloudAudioStorage，以 audio.storage.provider=GCS 啟用
- 路徑格式與本機實作完全一致，兩種環境共用 audio_asset.file_path 的語意
- 憑證取自執行身分（Cloud Run 的服務帳號），不需金鑰檔
- 新增 GoogleCloudStorageConfig，僅在 GCS 模式下建立 Storage Bean
- 新增四個測試：路徑格式、上傳目標、上傳失敗回空值、檔案不存在回空值
'@
```

---

# 階段 5：GCP 建立資源與首次部署

★ **本階段大量步驟需在瀏覽器中人工操作**，無法自動化。每一步都寫明點哪裡。

## Task 17: 建立 GCP 專案與啟用服務

- [ ] **Step 1: 安裝 gcloud CLI**

下載安裝：<https://cloud.google.com/sdk/docs/install>

安裝後在**新開的** PowerShell 確認：

```powershell
gcloud version
```

- [ ] **Step 2: 登入**

```powershell
gcloud auth login
```

會開啟瀏覽器，選擇你的 Google 帳號並允許。

- [ ] **Step 3: 建立專案**

```powershell
gcloud projects create thailan-app --name="ThaiLan"
gcloud config set project thailan-app
```

★ 專案 ID 全球唯一，`thailan-app` 若被佔用，換成 `thailan-app-awei` 之類，**並在後續所有指令中一致使用你實際採用的 ID**。

- [ ] **Step 4: 把專案連上帳單帳戶**

```powershell
gcloud billing accounts list
```

記下 `ACCOUNT_ID`，然後：

```powershell
gcloud billing projects link thailan-app --billing-account=你的ACCOUNT_ID
```

- [ ] **Step 5: 啟用需要的 API**

```powershell
gcloud services enable run.googleapis.com sqladmin.googleapis.com storage.googleapis.com artifactregistry.googleapis.com cloudbuild.googleapis.com texttospeech.googleapis.com
```

預期：`Operation ... finished successfully.`（可能需要 1～2 分鐘）

- [ ] **Step 6: 設定預設區域**

```powershell
gcloud config set run/region asia-east1
```

---

## Task 18: 建立 Cloud SQL

- [ ] **Step 1: 建立 PostgreSQL 執行個體**

★ 這一步要等 **5～10 分鐘**，屬正常。

```powershell
gcloud sql instances create thailan-db --database-version=POSTGRES_17 --tier=db-f1-micro --region=asia-east1 --storage-size=10GB --storage-type=HDD
```

- [ ] **Step 2: 設定 postgres 使用者的密碼**

```powershell
gcloud sql users set-password postgres --instance=thailan-db --password=請自己想一個強密碼
```

把密碼記下來，Task 20 會用到。

- [ ] **Step 3: 建立資料庫**

```powershell
gcloud sql databases create language_project --instance=thailan-db
```

- [ ] **Step 4: 取得連線名稱**

```powershell
gcloud sql instances describe thailan-db --format="value(connectionName)"
```

預期格式：`thailan-app:asia-east1:thailan-db`。記下來。

- [ ] **Step 5: 建立資料表**

用 Cloud SQL 的互動式連線：

```powershell
gcloud sql connect thailan-db --user=postgres --database=language_project
```

輸入密碼後進入 `psql`。**把 `db/schema.sql` 的全部內容複製貼上**，按 Enter 執行。

完成後輸入 `\dt` 確認五張表都在，再輸入 `\q` 離開。

★ 若 `gcloud sql connect` 因 IP 白名單失敗，改用這個方式：先允許你目前的 IP

```powershell
$myIp = (Invoke-WebRequest -Uri "https://api.ipify.org").Content
gcloud sql instances patch thailan-db --authorized-networks="$myIp/32"
```

再重試連線。

- [ ] **Step 6: ★ 確認 NULLS NOT DISTINCT 有生效**

在 `psql` 中執行：

```sql
SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'uq_translation_query_key';
```

預期輸出**必須包含 `NULLS NOT DISTINCT`**。沒有的話，代表 Cloud SQL 的 PostgreSQL 版本低於 15，需重建為 `POSTGRES_17`。

---

## Task 19: 建立 Cloud Storage 與權限

- [ ] **Step 1: 建立 bucket**

★ bucket 名稱全球唯一，把 `awei` 換成你自己的識別字串。

```powershell
gcloud storage buckets create gs://thailan-audio-awei --location=asia-east1 --uniform-bucket-level-access
```

記下 bucket 名稱，Task 20 會用到。

- [ ] **Step 2: 建立專用的服務帳號**

★ 不用預設服務帳號，因為預設的權限太大。這個帳號只給它需要的。

```powershell
gcloud iam service-accounts create thailan-run --display-name="ThaiLan Cloud Run"
```

- [ ] **Step 3: 給它讀寫 bucket 的權限**

```powershell
gcloud storage buckets add-iam-policy-binding gs://thailan-audio-awei --member="serviceAccount:thailan-run@thailan-app.iam.gserviceaccount.com" --role="roles/storage.objectAdmin"
```

- [ ] **Step 4: 給它連 Cloud SQL 的權限**

```powershell
gcloud projects add-iam-policy-binding thailan-app --member="serviceAccount:thailan-run@thailan-app.iam.gserviceaccount.com" --role="roles/cloudsql.client"
```

---

## Task 20: 首次部署到 Cloud Run

- [x] **Step 1: 決定記憶體規格** — 已於 2026-08-15 實測

**結論：`--memory 512Mi`。**

實測方式與數據（`docker run --memory 512m --memory-swap 512m`，即與 Cloud Run 相同的硬上限）：

| 項目 | 結果 |
|---|---|
| 啟動 | 成功，11.2 秒 |
| 音檔合成（最吃記憶體的路徑） | 成功 |
| 記憶體峰值 | **326 MiB / 512 MiB（64%）** |
| `State.OOMKilled` | `false`，重啟 0 次 |

★ **量測時一定要設 `--memory` 硬上限。** 不設的話容器看得到整台機器的記憶體，`MaxRAMPercentage=75` 就照那個比例配，量到的數字（本機是 416 MiB）完全不能代表 512Mi 環境下的行為。

- [ ] **Step 2: 部署**

★ 把以下的 bucket 名稱、連線名稱、密碼、金鑰換成你的實際值。`--memory` 用上一步的結論。

```powershell
gcloud run deploy thailan `
  --source . `
  --region asia-east1 `
  --service-account thailan-run@thailan-app.iam.gserviceaccount.com `
  --add-cloudsql-instances thailan-app:asia-east1:thailan-db `
  --memory 512Mi `
  --min-instances 1 `
  --max-instances 2 `
  --allow-unauthenticated `
  --set-env-vars "SPRING_PROFILES_ACTIVE=prod" `
  --set-env-vars "DB_URL=jdbc:postgresql:///language_project?cloudSqlInstance=thailan-app:asia-east1:thailan-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory" `
  --set-env-vars "DB_USERNAME=postgres" `
  --set-env-vars "DB_PASSWORD=你的資料庫密碼" `
  --set-env-vars "OPENAI_API_KEY=你的OpenAI金鑰" `
  --set-env-vars "GOOGLE_SPEECH_API_KEY=你的Google金鑰" `
  --set-env-vars "GCS_BUCKET=thailan-audio-awei" `
  --set-env-vars "APP_USERNAME=awei" `
  --set-env-vars "APP_PASSWORD=你的登入密碼"
```

★ `--allow-unauthenticated` 指的是「Cloud Run 這一層不擋」，因為擋人的工作交給 Spring Security。不加這個的話會需要 Google 帳號才能連，那不是我們要的。

★ 首次部署要 **5～10 分鐘**（要上傳原始碼、建置映像、推到 Artifact Registry、啟動）。

- [ ] **Step 3: 若出現「找不到 SocketFactory」的錯誤，加入相依性**

Cloud SQL 的 socketFactory 需要額外的函式庫。在 `pom.xml` 加入：

```xml
        <dependency>
            <groupId>com.google.cloud.sql</groupId>
            <artifactId>postgres-socket-factory</artifactId>
            <version>1.20.1</version>
        </dependency>
```

然後 commit 並重新部署：

```powershell
git add pom.xml
git commit -m @'
加入 Cloud SQL 連線函式庫

Feat:
- pom.xml 加入 postgres-socket-factory，供 Cloud Run 以 socket 方式連線 Cloud SQL
'@
gcloud run deploy thailan --source . --region asia-east1
```

- [ ] **Step 4: 取得網址**

```powershell
gcloud run services describe thailan --region asia-east1 --format="value(status.url)"
```

- [ ] **Step 5: 卡關時看日誌**

```powershell
gcloud run services logs read thailan --region asia-east1 --limit 50
```

常見錯誤對照：

| 日誌訊息 | 原因 | 處置 |
|---|---|---|
| `Could not create connection to database` | `DB_URL` 格式錯或未加 `--add-cloudsql-instances` | 檢查 Step 2 那兩項 |
| `403 ... storage.objects.create` | 服務帳號少了 bucket 權限 | 重跑 Task 19 Step 3 |
| `Container failed to start ... PORT 8080` | 程式啟動失敗，真正原因在更前面的日誌 | 往上翻找第一個 ERROR |
| `OutOfMemoryError` | 記憶體規格太小 | `gcloud run services update thailan --region asia-east1 --memory 1Gi` |
| `Could not resolve placeholder 'XXX'` | 漏填某個環境變數 | 比對 Step 2 的清單 |

- [ ] **Step 6: 執行手動驗證清單**

用電腦瀏覽器開上一步取得的網址：

1. **跳出帳密輸入框** → 輸入後進得去
2. 查一句沒查過的中文 → 有泰文、拼音、逐詞拆解
3. 按播放 → **聽得到聲音**
4. 確認檔案真的上雲了：

```powershell
gcloud storage ls gs://thailan-audio-awei/th/
```

預期：列出剛剛產生的 `.wav` 檔。

5. **★ 驗證持久化**（最重要的一條）：

```powershell
gcloud run deploy thailan --source . --region asia-east1
```

重新部署完成後，再播放同一句話 —— **聲音還在**，代表音檔確實存在容器之外。

6. 用**手機瀏覽器**開同一個網址，確認能登入、能查詢、能播放。

- [ ] **Step 7: Commit（若 Step 3 有改動）**

已於 Step 3 內完成。

---

# 階段 6：PWA

## Task 21: 產生 App 圖示

**Files:**
- Create: `frontend/public/icon.svg`
- Create: `frontend/public/icon-192.png`
- Create: `frontend/public/icon-512.png`

- [ ] **Step 1: 建立 SVG 原稿**

```svg
<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <!--
    ThaiLan 的 App 圖示。

    配色取自 frontend/src/styles.css 的既有變數：
      底色   #0b100e（--ink-900，帶一點綠的近黑）
      金漸層 #e6c67e → #9a7b34（--gold-400 → --gold-600）

    金漸層的角度與畫面上按鈕的 linear-gradient(135deg, ...) 一致，
    讓圖示與 App 內部是同一個視覺語言。

    字母是泰文的 ท（tho thahan）。選它的理由：
    筆畫簡單，縮到手機桌面的尺寸仍然看得清楚，
    而且一眼就知道這是跟泰文有關的東西。
  -->
  <defs>
    <linearGradient id="gold" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#e6c67e"/>
      <stop offset="100%" stop-color="#9a7b34"/>
    </linearGradient>
  </defs>

  <rect width="512" height="512" rx="112" fill="#0b100e"/>

  <!-- 細金框，呼應畫面上卡片的 1px 金色邊 -->
  <rect x="8" y="8" width="496" height="496" rx="104"
        fill="none" stroke="url(#gold)" stroke-width="6" opacity="0.45"/>

  <text x="256" y="256" font-family="Noto Sans Thai, Leelawadee UI, Tahoma, sans-serif"
        font-size="300" font-weight="600" fill="url(#gold)"
        text-anchor="middle" dominant-baseline="central">ท</text>
</svg>
```

- [ ] **Step 2: 轉成兩個尺寸的 PNG**

用 npm 的 sharp 套件轉（不需要額外安裝繪圖軟體）：

```powershell
cd frontend
npx --yes sharp-cli -i public\icon.svg -o public\icon-512.png resize 512 512
npx --yes sharp-cli -i public\icon.svg -o public\icon-192.png resize 192 192
cd ..
```

- [ ] **Step 3: 確認兩個檔案產生了且看起來正確**

```powershell
Get-ChildItem frontend\public\icon-*.png | Select-Object Name, Length
```

用檔案總管開啟 `frontend\public\icon-512.png` **目視確認**：黑底、金色的泰文字、圓角框。

★ 若泰文字沒顯示（變成空白或方框），代表系統缺泰文字型。改用這個替代方案：把 `<text>` 那一段換成手繪路徑版本，或先用字母 `T` 替代，之後再換圖。**圖示可以隨時替換，不要卡在這一步。**

---

## Task 22: 加入 PWA 設定

**Files:**
- Modify: `frontend/` （由 `ng add` 自動產生多個檔案）
- Modify: `frontend/public/manifest.webmanifest`
- Modify: `frontend/src/index.html`

- [ ] **Step 1: 加入 Angular PWA 支援**

```powershell
cd frontend
npx ng add @angular/pwa --skip-confirmation
cd ..
```

這會自動：新增 `@angular/service-worker` 相依性、產生 `ngsw-config.json`、產生 `public/manifest.webmanifest`、在 `app.config.ts` 註冊 service worker、在 `index.html` 加入 manifest 連結。

- [ ] **Step 2: 改寫 `frontend/public/manifest.webmanifest`**

```json
{
  "name": "ThaiLan 中泰翻譯學習",
  "short_name": "ThaiLan",
  "description": "中文與泰文雙向翻譯，含逐詞拆解、拼音與發音",
  "theme_color": "#0b100e",
  "background_color": "#0b100e",
  "display": "standalone",
  "scope": "/",
  "start_url": "/",
  "lang": "zh-TW",
  "icons": [
    {
      "src": "icon-192.png",
      "sizes": "192x192",
      "type": "image/png",
      "purpose": "any maskable"
    },
    {
      "src": "icon-512.png",
      "sizes": "512x512",
      "type": "image/png",
      "purpose": "any maskable"
    }
  ]
}
```

★ `short_name` 是手機桌面圖示下方顯示的字，必須短，這裡就是 `ThaiLan`。
★ `display: standalone` 是「打開時全螢幕、不顯示網址列」的關鍵。
★ `theme_color` 會決定手機頂端狀態列的顏色，設成跟底色一樣才不會有一條突兀的色帶。

- [ ] **Step 3: 確認 `ngsw-config.json` 沒有快取 API 回應**

打開 `frontend/ngsw-config.json`，確認：
- `assetGroups` 存在（快取 HTML/JS/CSS，這是我們要的）
- **沒有 `dataGroups` 區塊**（那是快取 API 回應的，決策 #10 明確排除）

若 `ng add` 產生了 `dataGroups`，**整段刪除**。

- [ ] **Step 4: 本機建置確認沒壞**

```powershell
cd frontend
npm run build
cd ..
```

預期：BUILD 成功，`dist/frontend/browser/` 底下有 `manifest.webmanifest`、`ngsw.json`、`icon-192.png`、`icon-512.png`。

- [ ] **Step 5: 重新建置映像並部署**

```powershell
docker build -t thailan:local .
gcloud run deploy thailan --source . --region asia-east1
```

- [ ] **Step 6: 手機驗證（★ 只有你能做）**

**iPhone**：用 Safari 開網址 → 登入 → 點下方**分享鍵** → 下滑找**「加入主畫面」** → 確認名稱是 `ThaiLan` → 加入

**Android**：用 Chrome 開網址 → 登入 → 應自動跳出「安裝應用程式」橫幅；沒跳出則點右上角**三個點** → **「安裝應用程式」**

確認：
1. 桌面出現圖示，圖案是**黑底金色的 ท**
2. 圖示下方文字是 **ThaiLan**
3. 點開是**全螢幕、沒有網址列**
4. 能查詢、能播放聲音
5. 關掉再打開 → **秒開**（service worker 生效）

- [ ] **Step 7: Commit**

```powershell
git add frontend
git commit -m @'
加入 PWA 支援與 App 圖示

Feat:
- 加入 @angular/pwa，產生 service worker 與 manifest
- manifest 設定 ThaiLan、standalone 全螢幕、主題色 #0b100e
- 新增黑底金漸層泰文字母 ท 的圖示，配色沿用前端既有變數
- ngsw-config 僅快取畫面資源，不快取 API 回應，避免開發期看到舊資料
'@
```

---

# 階段 7：預算警示

## Task 23: 設定預算警示

- [ ] **Step 1: 建立預算**

開啟 <https://console.cloud.google.com/billing> → 選你的帳單帳戶 → 左側**「預算與快訊 / Budgets & alerts」** → **「建立預算」**

填寫：

| 欄位 | 值 |
|---|---|
| 名稱 | `ThaiLan 花費監控` |
| 專案範圍 | 只選 `thailan-app` |
| 預算金額 | `300`（對著 $300 額度設，看得出還剩多少） |
| 警示門檻 | `50%`、`75%`、`90%`、`100%` |
| 通知對象 | 勾選「將電子郵件通知傳送給帳單管理員」 |

★ **不要**勾選「連結 Pub/Sub 主題」——那是給自動關閉服務用的，決策 #9 明確不做。

- [ ] **Step 2: 確認收得到通知**

確認 <https://console.cloud.google.com/billing> 的預算列表中出現剛才建立的項目，且 Email 欄位是你的信箱。

- [ ] **Step 3: 把額度到期日寫進行事曆**

**到期日：2026-11-14。** 設一個 **2026-11-07** 的提醒（提前一週）。

★ 到期時試用帳戶會自動關閉、服務會停止，**不會自動扣款**。要繼續用就得手動升級成付費帳戶（屆時約 $22～27／月）。

---

# 完成後的狀態

- 手機桌面有 **ThaiLan** 圖示，點開全螢幕可用
- 電腦瀏覽器用同一個網址也能用，資料互通
- 進站需輸入共用帳密
- 音檔存在 Cloud Storage，重新部署不會消失
- 本機開發流程不變，且本機用的是與雲端相同的 PostgreSQL
- 前 90 天由 $300 額度支付，到期前會收到 Email 提醒

---

# 後續建議項目（不在本計畫範圍）

| 項目 | 說明 | 預估 |
|---|---|---|
| **音檔改存 mp3** | 目前是未壓縮的 `.wav`，體積約 mp3 的 5～6 倍，行動網路下較耗流量且較慢 | 2h |
| 自訂網域 | 買一個 `.com` 約 $12～15／年，Cloud Run 自動配發 SSL 憑證 | 1h |
| 一人一組帳號 | 需新增 `app_user` 表與 BCrypt 密碼欄位；既有五張表仍不需更動 | 3h |
| 離線快取查詢結果 | 系統穩定後再加，需處理快取失效策略 | 3h |
| Secret Manager | 取代環境變數存放金鑰 | 1h |
| GitHub 自動部署 | 推到 main 就自動建置部署 | 1h |
