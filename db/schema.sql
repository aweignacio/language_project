/*
 * 中泰語言學習網站 —— 資料表建立腳本
 *
 * 資料庫：SQL Server 2022 / language_project
 * 執行方式見本檔最下方說明。
 *
 * ── 重要：所有存放文字的欄位一律使用 NVARCHAR ──────────────────────────
 * 本資料庫的 collation 為 SQL_Latin1_General_CP1_CI_AS，VARCHAR 無法保存
 * 非 ASCII 字元。中文、泰文、拼音聲調符號存入 VARCHAR 後會全部變成「?」，
 * 且寫入時不會拋出任何錯誤，屬於靜默損毀。
 *
 * 實測：泰文首字存入 NVARCHAR 得到字碼 3626（正確），存入 VARCHAR 得到 63（即「?」）。
 *
 * 僅有內容確定為 ASCII 的欄位（系統產生的檔名、Enum 名稱、模型代號）使用 VARCHAR。
 * ──────────────────────────────────────────────────────────────────
 *
 * ── 音檔存在哪裡 ────────────────────────────────────────────────────
 * 五張表中，音檔一律由 audio_asset 持有，其他表只存文字。
 *
 * （2026-08-14 之前只有 translation_query 存音檔。改版後改成以「文字內容」
 *   為鍵全站共用 —— 同一段泰文不管在哪裡出現，都指向同一個 mp3，
 *   只會被合成一次。理由見 audio_asset 的說明。）
 * ──────────────────────────────────────────────────────────────────
 *
 * 本腳本可重複執行：所有建立動作都有存在性檢查，不會覆蓋既有資料表。
 * ★ 這個特性請維持住 —— 不要把 DROP TABLE 加進這個檔案。
 *   需要重建時用 db\reset-2026-08-14.sql，那支才是會刪資料的。
 */

USE language_project;
GO

/* ============================================================
 * 1. translation_query —— 查詢結果快取
 *
 * Key 為「使用者輸入的原文 ＋ 翻譯方向 ＋ 說話者性別」三者的組合，
 * 不區分單字或句子。
 *
 * 這張表不持有音檔，音檔在 audio_asset。
 * ============================================================ */
IF OBJECT_ID('dbo.translation_query', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.translation_query
    (
        -- 代理主鍵。子表需以外鍵參考，中文字串當外鍵佔用空間大且 join 較慢，
        -- 故另設流水號。
        id            BIGINT          IDENTITY(1,1)   NOT NULL,

        -- 使用者輸入的原文。前後空白於寫入前去除。
        -- 中翻泰時這裡是中文，泰翻中時這裡是泰文。
        source_text   NVARCHAR(100)                   NOT NULL,

        -- TranslationDirectionEnum：ZH_TO_TH / TH_TO_ZH
        -- 由 LanguageDetector 依輸入的字元範圍自動判斷，使用者不需要選。
        direction     VARCHAR(20)                     NOT NULL,

        -- SpeakerGenderEnum：MALE / FEMALE
        --
        -- 泰文的自稱與句尾助詞都分性別（男 ผม/ครับ、女 ฉัน/ค่ะ），
        -- 所以同一句中文的男版與女版是兩句不同的泰文，必須各存一筆。
        --
        -- ★ 泰翻中沒有性別概念，該方向一律為 NULL。
        gender        VARCHAR(10)                     NULL,

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

        -- 快取命中判斷依據，同時防止重複寫入。
        --
        -- ★ SQL Server 的 UNIQUE 把 NULL 當成一個值來比對，
        --   所以「同一句泰文（gender 為 NULL）只會有一筆」仍然成立，
        --   不需要為泰翻中另外處理。
        CONSTRAINT UQ_translation_query_key
            UNIQUE (source_text, direction, gender),

        CONSTRAINT CK_translation_query_direction
            CHECK (direction IN ('ZH_TO_TH', 'TH_TO_ZH')),

        CONSTRAINT CK_translation_query_gender
            CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE'))
    );
END
GO

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
IF OBJECT_ID('dbo.translation_segment', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.translation_segment
    (
        query_id        BIGINT                          NOT NULL,

        -- 顯示順序，自 1 起算
        seq_no          INT                             NOT NULL,

        chinese_text    NVARCHAR(50)                    NOT NULL,
        thai_text       NVARCHAR(100)                   NOT NULL,
        romanization    NVARCHAR(100)                   NOT NULL,

        CONSTRAINT PK_translation_segment
            PRIMARY KEY (query_id, seq_no),

        -- 資料庫層級的完整性約束。
        -- 注意：程式端刻意不使用 JPA 關聯註解（@ManyToOne 等），
        -- 關聯由 Service 層自行查詢組裝，此約束僅確保資料不會孤立。
        CONSTRAINT FK_translation_segment_query_id
            FOREIGN KEY (query_id)
            REFERENCES dbo.translation_query (id)
            ON DELETE CASCADE
    );
END
GO

/* ============================================================
 * 3. vocabulary —— 單字表
 *
 * 純文字字典，不含音檔。
 * 由句子拆解結果沉澱而成，是本專案長期累積的資產。
 *
 * ★ 2026-08-14 起，一列代表「一個說法」，不是「一個詞」。
 *
 *   泰文的「我」至少有 ผม（男性禮貌）、ฉัน（女性）、กู（粗俗）三種說法，
 *   所以「我」會佔三列。單字列表頁因此會出現同一個中文詞連續多列 ——
 *   那是預期行為，不是資料重複。
 *
 *   實際會有多列的只有人稱代詞與句尾助詞等十來個詞，其餘都還是一列。
 *
 * ★ 這張表不再兼任「省錢用的快取」。
 *
 *   2026-08-14 之前，TranslationService 會先查這裡，命中就不呼叫 AI。
 *   那條捷徑會讓多重說法功能永遠失效：你先查過「我想喝酒」，
 *   「我 → ฉัน」被沉澱進來，之後單獨查「我」就會直接拿到 ฉัน，
 *   永遠不會去問 AI 要 ผม 和 กู。該捷徑已移除。
 * ============================================================ */
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
        --   而且只有這裡才有 BOTH（使用者不可能男女都是，
        --   但一個詞可以是男女通用的，例如 กู）。
        --
        -- 從句子拆解沉澱下來的詞沒有這項資訊，為 NULL。
        -- 日後單獨查詢該詞時會補上（合併規則見 TranslationPersistenceService）。
        gender_usage  VARCHAR(10)                     NULL,

        -- PolitenessEnum：FORMAL / NEUTRAL / CASUAL / RUDE
        -- 前端要把 RUDE 用警示色標出來 ——
        -- 用錯場合的後果是冒犯到人，不是講得不夠好。
        politeness    VARCHAR(10)                     NULL,

        -- 中文說明，例如「男生自稱，正式或對不熟的人使用」
        note          NVARCHAR(200)                   NULL,

        -- VocabularySourceTypeEnum：
        --   SEGMENT —— 由多詞句子拆解而來
        --   DIRECT  —— 使用者輸入的完整內容即為此詞（拆解長度為 1）
        -- 已存在的列不更新此欄位，以首次寫入的值為準。
        source_type   VARCHAR(20)                     NOT NULL,

        created_at    DATETIME2                       NOT NULL
            CONSTRAINT DF_vocabulary_created_at DEFAULT SYSDATETIME(),
        updated_at    DATETIME2                       NOT NULL
            CONSTRAINT DF_vocabulary_updated_at DEFAULT SYSDATETIME(),

        CONSTRAINT PK_vocabulary
            PRIMARY KEY (id),

        -- 字典的唯一鍵，同時避免重複寫入。
        -- ★ 2026-08-14 從單獨的 chinese_text 改成中文＋泰文的組合，
        --   這一改才讓同一個詞能存多種說法。
        CONSTRAINT UQ_vocabulary_chinese_thai
            UNIQUE (chinese_text, thai_text),

        CONSTRAINT CK_vocabulary_source_type
            CHECK (source_type IN ('SEGMENT', 'DIRECT')),

        CONSTRAINT CK_vocabulary_gender_usage
            CHECK (gender_usage IS NULL
                   OR gender_usage IN ('MALE', 'FEMALE', 'BOTH')),

        CONSTRAINT CK_vocabulary_politeness
            CHECK (politeness IS NULL
                   OR politeness IN ('FORMAL', 'NEUTRAL', 'CASUAL', 'RUDE'))
    );
END
GO

-- 查一個中文詞的所有說法時會用到（單字查詢的主要路徑）。
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_vocabulary_chinese_text'
                 AND object_id = OBJECT_ID('dbo.vocabulary'))
BEGIN
    CREATE INDEX IX_vocabulary_chinese_text
        ON dbo.vocabulary (chinese_text);
END
GO

/* ============================================================
 * 4. audio_asset —— 音檔資產
 *
 * 規則只有一句話：★ 同一段文字，全站只會有一個 mp3 ★
 *
 * 不管這段泰文是「整句翻譯的結果」、「某個單字的一種說法」，
 * 還是「別的句子裡剛好出現的同一個詞」，通通指向同一個檔案。
 *
 * 為什麼要這樣設計：合成語音要付錢。如果音檔綁在「那一次查詢」身上，
 * 同一個 เหล้า 會被合成好幾次 —— 查「酒」一次、查「我想喝酒」逐詞一次、
 * 查「他喝酒了」又一次。三個一模一樣的 mp3，付了三次錢。
 *
 * 改成以文字內容為鍵之後，查得越多、覆蓋率越高，語音費用趨近於零。
 * 這是本專案「用越久越省錢」的核心。
 *
 * ★ 語言欄位是必要的：中文和泰文各自有自己的音檔，
 *   存在不同的子資料夾（audio/th、audio/zh）。
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
        -- 前端把它接在 /audio/ 後面就是可以直接播放的網址。
        file_path   VARCHAR(100)                NOT NULL,

        created_at  DATETIME2                   NOT NULL
            CONSTRAINT DF_audio_asset_created_at DEFAULT SYSDATETIME(),

        CONSTRAINT PK_audio_asset
            PRIMARY KEY (id),

        -- ★ 這條唯一鍵就是「同一段文字只合成一次」的保證。
        --   拿掉它程式仍然會跑，只是會安靜地一直重複付錢，
        --   而且畫面上完全看不出異常。務必保留。
        CONSTRAINT UQ_audio_asset_text_language
            UNIQUE (speech_text, language),

        CONSTRAINT CK_audio_asset_language
            CHECK (language IN ('TH', 'ZH'))
    );
END
GO

/* ============================================================
 * 5. api_usage_log —— API 用量與費用紀錄
 *
 * 事件紀錄表，無自然鍵。屬營運監控資料，刪除不影響業務功能。
 * 本表所有欄位內容皆為 ASCII，故一律使用 VARCHAR。
 * ============================================================ */
IF OBJECT_ID('dbo.api_usage_log', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.api_usage_log
    (
        id                  BIGINT      IDENTITY(1,1)   NOT NULL,

        -- 對應的查詢，可追溯。
        -- 刻意「不」建立外鍵約束：用量紀錄於呼叫外部服務的當下寫入，
        -- 此時 translation_query 尚未寫入（外部呼叫在交易之外執行），
        -- 建立外鍵會導致寫入失敗。同時本表為稽核用途，
        -- 即使對應的查詢日後被刪除，紀錄仍應保留。
        query_id            BIGINT                      NULL,

        -- AiProviderEnum：OPENAI / ANTHROPIC / GOOGLE / AZURE
        provider            VARCHAR(20)                 NOT NULL,

        -- AiServiceTypeEnum：TRANSLATION / SPEECH
        service_type        VARCHAR(20)                 NOT NULL,

        -- 實際使用的模型名稱字串
        model_name          VARCHAR(100)                NOT NULL,

        -- UsageUnitTypeEnum：TOKEN（對話模型）/ CHARACTER（語音合成）
        unit_type           VARCHAR(20)                 NOT NULL,

        input_units         BIGINT                      NOT NULL,

        -- 語音服務無輸出計價，固定為 0
        output_units        BIGINT                      NOT NULL,

        -- 呼叫「當下」的單價。價格調整後歷史紀錄仍可驗算。
        -- 金額一律使用 DECIMAL，Java 端對應 BigDecimal，
        -- 禁止使用 float / double，否則累加後金額會有誤差。
        input_unit_price    DECIMAL(12,8)               NOT NULL,
        output_unit_price   DECIMAL(12,8)               NOT NULL,
        cost_amount         DECIMAL(12,6)               NOT NULL,

        -- 固定 USD。不存台幣，匯率浮動，統計時再換算。
        currency            CHAR(3)                     NOT NULL
            CONSTRAINT DF_api_usage_log_currency DEFAULT 'USD',

        -- SQL Server 無 boolean 型別，使用 BIT。
        -- 失敗仍可能計費，且保留紀錄才能觀察失敗率。
        is_success          BIT                         NOT NULL,

        created_at          DATETIME2                   NOT NULL
            CONSTRAINT DF_api_usage_log_created_at DEFAULT SYSDATETIME(),

        CONSTRAINT PK_api_usage_log
            PRIMARY KEY (id),

        CONSTRAINT CK_api_usage_log_service_type
            CHECK (service_type IN ('TRANSLATION', 'SPEECH')),

        CONSTRAINT CK_api_usage_log_unit_type
            CHECK (unit_type IN ('TOKEN', 'CHARACTER'))
    );
END
GO

-- 期間統計用（例如「這個月花了多少」）
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_api_usage_log_created_at'
                 AND object_id = OBJECT_ID('dbo.api_usage_log'))
BEGIN
    CREATE INDEX IX_api_usage_log_created_at
        ON dbo.api_usage_log (created_at);
END
GO

-- 追溯某次查詢花了多少錢
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_api_usage_log_query_id'
                 AND object_id = OBJECT_ID('dbo.api_usage_log'))
BEGIN
    CREATE INDEX IX_api_usage_log_query_id
        ON dbo.api_usage_log (query_id);
END
GO


/* ============================================================
 * 執行方式
 * ============================================================
 *
 * 【方式一】IntelliJ IDEA
 *   在 Database 工具視窗連上 language_project 後，開啟本檔案，
 *   右上角選擇該資料來源，按執行（Ctrl + Enter）。
 *
 * 【方式二】指令列
 *   docker cp db\schema.sql sqlserver:/tmp/schema.sql
 *   docker exec sqlserver /opt/mssql-tools18/bin/sqlcmd `
 *       -S localhost -U sa -P 'Sqlserver123456' -C -f 65001 -i /tmp/schema.sql
 *
 *   -f 65001 指定輸入檔為 UTF-8，缺少此參數時中文註解會被誤判編碼。
 *
 * 【重新建立】
 *   本腳本不會覆蓋既有資料表，直接重跑不會清掉任何東西。
 *
 *   要真的重建，請執行 db\reset-2026-08-14.sql（那支才會 DROP），
 *   再回來跑這一支。順序不能反。
 *
 *   ★ 不要把 DROP TABLE 加進這個檔案。
 *     「可重複執行且不刪資料」是這個腳本的安全特性，
 *     加了之後任何人手滑跑一次就全沒了。
 *
 * 【音檔資料夾】
 *   資料表建好之後，確認專案根目錄下有這兩個資料夾：
 *
 *       audio\th\    泰文發音
 *       audio\zh\    中文發音
 *
 *   audio_asset.file_path 存的就是相對於 audio\ 的路徑。
 */
