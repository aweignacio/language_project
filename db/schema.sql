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
 * 本腳本可重複執行：所有建立動作都有存在性檢查，不會覆蓋既有資料表。
 */

USE language_project;
GO

/* ============================================================
 * 1. translation_query —— 查詢結果快取
 *
 * Key 為使用者輸入的原始字串，不區分單字或句子。
 * 四張表中「只有這張」持有音檔。
 * ============================================================ */
IF OBJECT_ID('dbo.translation_query', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.translation_query
    (
        -- 代理主鍵。source_text 雖然唯一，但子表需以外鍵參考，
        -- 中文字串當外鍵佔用空間大且 join 較慢，故另設流水號。
        id              BIGINT          IDENTITY(1,1)   NOT NULL,

        -- 使用者輸入的中文原文。前後空白於寫入前去除。
        source_text     NVARCHAR(100)                   NOT NULL,

        -- 整句泰文
        thai_text       NVARCHAR(500)                   NOT NULL,

        -- 整句羅馬拼音（含聲調符號，如 chǎn、dùuem）
        romanization    NVARCHAR(500)                   NOT NULL,

        -- 音檔檔名，例如 a3f9c2.mp3。系統產生的 ASCII 字串，故用 VARCHAR。
        -- 允許 NULL：語音服務失敗時仍保留翻譯結果，音檔留待日後補產生。
        audio_file      VARCHAR(100)                    NULL,

        created_at      DATETIME2                       NOT NULL
            CONSTRAINT DF_translation_query_created_at DEFAULT SYSDATETIME(),
        updated_at      DATETIME2                       NOT NULL
            CONSTRAINT DF_translation_query_updated_at DEFAULT SYSDATETIME(),

        CONSTRAINT PK_translation_query
            PRIMARY KEY (id),

        -- 快取命中判斷依據，同時防止重複寫入
        CONSTRAINT UQ_translation_query_source_text
            UNIQUE (source_text)
    );
END
GO

/* ============================================================
 * 2. translation_segment —— 逐詞拆解結果
 *
 * 複合主鍵 (query_id, seq_no)，無獨立流水號。
 * 同一個詞會在不同句子的拆解中重複出現，這是正確的 ——
 * 本表記錄的是「該句話如何拆解」，而非字典。
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
 * ============================================================ */
IF OBJECT_ID('dbo.vocabulary', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.vocabulary
    (
        id              BIGINT          IDENTITY(1,1)   NOT NULL,

        chinese_text    NVARCHAR(50)                    NOT NULL,
        thai_text       NVARCHAR(100)                   NOT NULL,
        romanization    NVARCHAR(100)                   NOT NULL,

        -- VocabularySourceTypeEnum：
        --   SEGMENT —— 由多詞句子拆解而來
        --   DIRECT  —— 使用者輸入的完整內容即為此詞（拆解長度為 1）
        -- 已存在的詞不更新此欄位，以首次寫入的值為準。
        source_type     VARCHAR(20)                     NOT NULL,

        created_at      DATETIME2                       NOT NULL
            CONSTRAINT DF_vocabulary_created_at DEFAULT SYSDATETIME(),
        updated_at      DATETIME2                       NOT NULL
            CONSTRAINT DF_vocabulary_updated_at DEFAULT SYSDATETIME(),

        CONSTRAINT PK_vocabulary
            PRIMARY KEY (id),

        -- 字典的唯一鍵，同時避免重複寫入
        CONSTRAINT UQ_vocabulary_chinese_text
            UNIQUE (chinese_text),

        CONSTRAINT CK_vocabulary_source_type
            CHECK (source_type IN ('SEGMENT', 'DIRECT'))
    );
END
GO

/* ============================================================
 * 4. api_usage_log —— API 用量與費用紀錄
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
 *   本腳本不會覆蓋既有資料表。若需重建，先手動刪除
 *   （注意順序，translation_segment 有外鍵指向 translation_query）：
 *
 *   DROP TABLE IF EXISTS dbo.api_usage_log;
 *   DROP TABLE IF EXISTS dbo.translation_segment;
 *   DROP TABLE IF EXISTS dbo.translation_query;
 *   DROP TABLE IF EXISTS dbo.vocabulary;
 */
