# 部署上雲與手機 App 化 — 設計規格

- **文件日期**：2026-08-15
- **專案路徑**：`C:\Tim\language_project`
- **狀態**：已確認，待排實作計畫
- **前一份規格**：`2026-08-14-bidirectional-variants-design.md`

---

## 1. 這次要解決什麼

目前這個網站只活在開發者自己的電腦上：

- 後端跑在 `localhost:8080`，資料庫是本機 Docker 的 SQL Server，音檔存在專案底下的 `audio/` 資料夾。
- `localhost` 的意思是「這台電腦自己」，所以手機連不到；電腦關機或睡眠，服務就消失。
- 想在外面（例如人在泰國的餐廳）查一句話時，完全用不到。

**目標**：讓這個網站跑在一台永遠開著、有公開網址的機器上，並且在手機桌面以 App 的形式（PWA）開啟使用。

**不是目標**：把它改寫成原生 App（Android/iOS 的 apk/ipa）。PWA 已足夠，且不需要 Mac、不需要 Apple 開發者帳號、不需要上架。

---

## 2. 決策紀錄

以下為釐清階段逐題確認的結果，實作時以此為準。

| # | 決策 | 選定 | 理由摘要 |
|---|---|---|---|
| 1 | 使用者範圍 | **少數幾個認識的人** | 不做公開註冊，不需要防機器人與個別用量上限 |
| 2 | 登入方式 | **共用一組帳號密碼** | 半小時可完成，且擋得住真正的威脅（機器人掃網址狂刷 API）。日後要換成一人一組，Spring Security 可抽換，不用重寫 |
| 3 | 主機費用 | **付費、一直開著** | 免費方案閒置後冷啟動 30～60 秒，對「掏出手機馬上查」的情境不可用 |
| 4 | 部署平台 | **GCP**（Cloud Run + Cloud SQL + Cloud Storage） | 帳號 2026-08-15 啟用，$300 額度至 **2026-11-14** 到期，前三個月零成本；且具履歷價值 |
| 5 | 音檔播放路徑 | **後端當中介**（方案 A） | 前端零修改；音檔一併受登入保護；使用量小，效能無虞 |
| 6 | 本機資料庫 | **也換成 PostgreSQL** | 開發與正式環境一致，避免「本機測得過、上雲端才壞」且難以重現的 bug |
| 7 | 既有資料 | **不搬遷** | 全部資料至今僅花費 $1.58 美金，搬遷需 2～3 小時，不划算。改為匯出 `api_usage_log` 成 CSV 留存 |
| 8 | 建置方式 | **自寫 Dockerfile（多階段）** | 專案含 Angular 與 Spring Boot 兩種技術，Dockerfile 最乾淨；同時是搬家保險，不被 GCP 綁死 |
| 9 | 費用防線 | **只設預算警示，不自動關閉服務** | OpenAI 為預付制（已確認未開啟 auto-recharge），天然有上限；GCP 前 90 天有額度擋著 |
| 10 | 離線能力 | **只快取畫面外殼** | 查詢本來就必須連網；快取 API 回應會在開發期造成「分不清是改壞了還是看到舊資料」 |
| 11 | App 名稱 | **ThaiLan** | |
| 12 | App 圖示 | **墨黑底 + 金色漸層的泰文字母「ท」** | 沿用前端既有配色，小尺寸辨識度最佳 |
| 13 | 網址 | **先用 Cloud Run 自動配發的 `*.run.app`** | 自訂網域隨時可加且舊網址仍通；PWA 加到主畫面後不顯示網址列，網址美醜不影響體驗 |

---

## 3. 現況盤點（實測數字）

| 項目 | 現況 |
|---|---|
| 後端 | Spring Boot，`localhost:8080` |
| 前端 | Angular 22，獨立 `ng serve`，經 `proxy.conf.json` 轉發 |
| 資料庫 | Docker SQL Server 2022，`localhost:1433` |
| 音檔 | 專案下 `audio/th`、`audio/zh`，**副檔名實際為 `.wav`**（非 schema 註解所寫的 `.mp3`） |
| 金鑰 | `application-local.yml`，已排除於版控外 |
| 驗證機制 | **完全沒有**（無 Spring Security） |
| 分支狀態 | `main` 與工作分支同在 `f039c5e`，已推上 GitHub |
| `translation_query` | 11 筆 |
| `translation_segment` | 43 筆 |
| `vocabulary` | 27 筆 |
| `audio_asset` | 35 筆（檔案共 1.7 MB） |
| `api_usage_log` | 188 筆，累計花費 **$1.582260 USD** |

### 有利於本次工作的既有設計

- **前端已使用相對路徑**（`/api/v1/translations`），打包進 `static/` 後自動正確，無須修改。
- **`SpeechClient` 已是介面 + 雙實作 + 設定切換**的模式，音檔儲存直接沿用同一套做法。
- **Repository 測試標註 `@AutoConfigureTestDatabase(replace = NONE)`**，打的是真資料庫，可直接充當 PostgreSQL 遷移的驗收工具。
- **`VocabularyController` 已分頁**（`@PageableDefault(size = 20)`），資料量成長不影響記憶體。
- **無 `@Cacheable`、無 static 快取、無不分頁的 `findAll()`**，不存在隨資料量累積的記憶體風險。

---

## 4. 架構

### 4.1 雲端（全部置於 `asia-east1` 台灣機房）

```
        手機 / 電腦瀏覽器
                 │  https://thailan-xxxx.asia-east1.run.app
                 ▼
    ┌────────────────────────────────────┐
    │        Cloud Run（單一容器）         │
    │   Spring Security ← 先問帳號密碼     │
    │            ▼                        │
    │   Spring Boot                       │
    │     ├─ Angular 靜態檔（static/）     │
    │     ├─ TranslationController        │
    │     ├─ AudioController              │
    │     └─ VocabularyController         │
    └────┬──────────────┬────────────┬────┘
         ▼              ▼            ▼
   ┌──────────┐  ┌────────────┐  ┌──────────┐
   │ Cloud SQL│  │   Cloud    │  │ OpenAI   │
   │PostgreSQL│  │  Storage   │  │ Google   │
   │  五張表   │  │  音檔 .wav  │  │   TTS    │
   └──────────┘  └────────────┘  └──────────┘
```

**三個服務必須同區**：跨區會產生服務之間的流量費，且每次查詢多出跨洲延遲。

### 4.2 本機（僅資料庫更換）

```
    IntelliJ 執行              ng serve (4200)
         │                          │ proxy.conf.json
         ▼                          │
    Spring Boot (8080) ◄────────────┘
         ├─► Docker PostgreSQL   ← 唯一改變（原為 SQL Server）
         ├─► audio/ 資料夾        ← 不變
         └─► application-local.yml 金鑰 ← 不變
```

開發流程完全不變：兩個終端機、熱更新、音檔仍存本機資料夾。

### 4.3 兩套環境如何切換

以 Spring profile 區分（專案已在使用 `application-local.yml`）。

| | **local（本機）** | **prod（雲端）** |
|---|---|---|
| 資料庫 | Docker PostgreSQL | Cloud SQL |
| 音檔 | `audio/` 資料夾 | Cloud Storage |
| 金鑰 | `application-local.yml` | Cloud Run 環境變數 |
| 登入 | **關閉**（開發時不受干擾） | **開啟** |

### 4.4 新增的抽象：`AudioStorage`

沿用既有 `SpeechClient` 的模式，非新發明：

```
既有：                            新增：
SpeechClient（介面）              AudioStorage（介面）
  ├─ GoogleSpeechClient            ├─ LocalDiskAudioStorage    ← 本機
  └─ OpenAiSpeechClient            └─ GoogleCloudAudioStorage  ← 雲端
        ▲                                 ▲
   speech.provider 切換              audio.storage.provider 切換
```

`AudioAssetService` 改為只依賴此介面，不再直接操作檔案系統。

**★ 兩個實作回傳相同格式的相對路徑（如 `th/a3f9.wav`）**，因此 `AudioAssetService` 以上的所有程式碼無法、也不需要分辨自己跑在哪裡。

### 4.5 其他既定設定

- **機房**：`asia-east1`（台灣彰化）
- **金鑰保存**：Cloud Run 環境變數。Secret Manager 為日後可選的升級，本次不做。
- **執行個體**：`min-instances=1`，避免冷啟動。

---

## 5. 程式碼變更清單

🔴 大改　🟡 小改　🟢 幾乎不動

### 5.1 🔴 資料庫換成 PostgreSQL

| 檔案 | 動作 |
|---|---|
| `pom.xml` | 移除 `mssql-jdbc`，加入 `postgresql` |
| `db/schema.sql` | 改寫為 PostgreSQL 語法 |
| `db/reset-*.sql` | 同上 |
| `application-local.yml` 與 `.example` | 連線字串改為 PostgreSQL |
| 五個 Entity | 檢查即可；`GenerationType.IDENTITY` 兩邊皆支援，預期不需修改 |
| Service / Repository | **不動** |

語法對照：

| SQL Server | PostgreSQL |
|---|---|
| `IDENTITY(1,1)` | `GENERATED BY DEFAULT AS IDENTITY` |
| `NVARCHAR(n)` | `VARCHAR(n)` |
| `BIT` | `BOOLEAN` |
| `DATETIME2` | `TIMESTAMP` |
| `SYSDATETIME()` | `CURRENT_TIMESTAMP` |
| `IF OBJECT_ID(...) IS NULL` | `CREATE TABLE IF NOT EXISTS` |
| `GO` | 刪除 |

**★ `NVARCHAR` 與 `VARCHAR` 的區分在 PostgreSQL 不再需要。** 原 schema 開頭那段「VARCHAR 會讓泰文靜默變成 `?`」的警告是 SQL Server collation 造成的；PostgreSQL 預設 UTF-8，中文泰文一律正常。該段註解改寫為說明「為何此處不再需要擔心」，不可直接刪除——那段知識仍有價值。

**★ `schema.sql` 必須維持「可重複執行且不刪資料」的特性**，不得加入 `DROP TABLE`。

### 5.2 🔴 音檔儲存抽象化

| 檔案 | 動作 |
|---|---|
| `client/storage/AudioStorage.java` | **新增**（介面：存、讀、算路徑） |
| `client/storage/LocalDiskAudioStorage.java` | **新增**（搬入現有存檔邏輯） |
| `client/storage/GoogleCloudAudioStorage.java` | **新增** |
| `service/AudioAssetService.java` | 🟡 改為依賴介面 |
| `client/google/GoogleSpeechClient.java` | 🟡 存檔段改呼叫介面 |
| `client/openai/OpenAiSpeechClient.java` | 🟡 同上 |
| `controller/AudioController.java` | 🟡 新增**串流**端點，統一處理 `/audio/**` |
| `config/WebMvcConfig.java` | 🟡 **移除** `/audio/**` 靜態資源對應 |
| `config/AudioStorageProperties.java` | 🟡 新增 `provider` 欄位 |
| `pom.xml` | 加入 `google-cloud-storage` |

**★ 音檔一律以串流方式輸出**，不可讀進 `byte[]` 再回傳。串流的記憶體佔用固定於一個小緩衝區，與檔案大小、同時播放數無關。

**★ 兩種環境都走 `AudioController` 的串流端點，不保留 `WebMvcConfig` 的靜態資源對應。** 若兩者並存，`/audio/**` 會有兩個處理者互相打架，且本機與雲端的輸出路徑不同，等於本機測不到雲端真正會走的那條路。改為統一由 Controller 處理後，`LocalDiskAudioStorage` 從磁碟開串流、`GoogleCloudAudioStorage` 從 Cloud Storage 開串流，Controller 以上完全一致。

此變更**不影響使用者可見行為**：網址仍是 `/audio/th/a3f9.wav`，前端仍然不用修改。

### 5.3 🟡 登入保護

| 檔案 | 動作 |
|---|---|
| `pom.xml` | 加入 `spring-boot-starter-security` |
| `config/SecurityConfig.java` | **新增**：prod 啟用、local 全放行 |
| `application-prod.yml` | 帳密讀自環境變數 |

### 5.4 🟢 前端併入與 PWA

| 檔案 | 動作 |
|---|---|
| 前端 `.ts` / `.html` / `.css` | **完全不動** |
| `frontend/` | 執行 `ng add @angular/pwa` |
| `manifest.webmanifest` | `ThaiLan`、主題色 `#0b100e`、`display: standalone` |
| `icon-192.png` / `icon-512.png` | **新增**（墨黑底 + 金漸層「ท」，以 SVG 產出後轉 PNG） |

配色取自 `frontend/src/styles.css`：

| 變數 | 色碼 |
|---|---|
| `--ink-900` | `#0b100e` |
| `--gold-400` | `#e6c67e` |
| `--gold-500` | `#c9a253` |
| `--gold-600` | `#9a7b34` |
| `--jade` | `#6fb8a0` |
| `--ivory` | `#f1e9da` |

圖示漸層沿用按鈕的 `linear-gradient(135deg, gold-400 → gold-600)`。

### 5.5 🟡 打包與設定

| 檔案 | 動作 |
|---|---|
| `Dockerfile` | **新增**：① Node 建置前端 ② Maven 建置後端 ③ 精簡 JRE 執行環境 |
| `.dockerignore` | **新增**：排除 `target/`、`node_modules/`、`audio/` |
| `application-prod.yml` | **新增** |
| `application.yml` | 🟡 金鑰改為 `${OPENAI_API_KEY}` 形式 |

**Service 層核心流程（翻譯、快取判斷、費用記帳）完全不動。**

---

## 6. 資料流

以「在泰國餐廳查一句沒查過的話」為例。

| 步驟 | 誰 | 做什麼 | 當下資料 |
|---|---|---|---|
| 0 | Service Worker | 從手機本機取出畫面資源，不連網 | 畫面秒開 |
| 1 | 使用者 | 輸入「我想喝酒」、選男性、按查詢 | `POST /api/v1/translations {"sourceText":"我想喝酒","gender":"MALE"}` |
| 2 | Spring Security | 驗證身分，未登入則導向登入頁 | |
| 3 | `LanguageDetector` | 依字元範圍判斷方向 | `ZH_TO_TH` |
| 4 | `TranslationService` | 查 Cloud SQL 快取 | 命中即直接回傳，零費用 |
| 5 | `OpenAiTranslationClient` | 呼叫 gpt-5.5 | 得 `ผมอยากดื่มเหล้าครับ` + 拼音 + 逐詞 + 多重說法 |
| 6 | `ApiUsageRecorder` | 寫入 `api_usage_log` | token 數、當下單價、花費美金 |
| 7 | `TranslationPersistenceService` | 寫入三張表 | `translation_query` / `translation_segment` / `vocabulary` |
| 8 | `AudioAssetService` | 查 `audio_asset` → 無 → `GoogleSpeechClient` 合成 → `WavAudio.tidy()` → **`AudioStorage.save()`** | 本機寫入 `audio/th/a3f9.wav`；雲端上傳 Cloud Storage。**兩者皆回傳 `th/a3f9.wav`** |
| 9 | `TranslationController` | 回傳 JSON | 畫面顯示泰文、拼音、逐詞 |
| 10 | `AudioController` | 收到 `GET /audio/th/a3f9.wav`，從儲存層開串流邊讀邊吐 | 記憶體佔用固定 |

---

## 7. 錯誤處理

既有的 `GlobalExceptionHandler` 與 `ErrorCodeEnum` **不動**，僅新增情境。

| 情境 | 處理方式 |
|---|---|
| Cloud Storage 上傳失敗 | **只讓語音失敗，翻譯結果照常顯示**。沿用既有 `SpeechFailureReasonEnum` 機制 |
| Cloud SQL 連線未就緒 | 沿用既有 `initialization-fail-timeout: 60000` |
| 帳號密碼錯誤 | Spring Security 回 401，前端顯示提示 |
| 環境變數缺漏 | **啟動即失敗**（刻意如此，優於執行期才炸），原因見 Cloud Run 日誌 |
| 容器重啟 | `min-instances=1` 自動拉起；音檔與資料皆在外部服務，不會遺失 |

---

## 8. 測試

### 驗收標準

**既有 16 個測試檔於 PostgreSQL 上必須全數通過。**

Repository 測試標註 `@AutoConfigureTestDatabase(replace = NONE)`，實際打真資料庫，因此可驗證：

- 中文、泰文存取無字元損毀
- `UQ_audio_asset_text_language` 唯一鍵確實阻擋重複
- 分頁與排序行為正常

### 新增測試

| 測試 | 防什麼 |
|---|---|
| `LocalDiskAudioStorageTest` | 存取往返正確、路徑格式符合約定 |
| `GoogleCloudAudioStorageTest` | 以假的 Storage 元件驗證呼叫參數與錯誤處理 |
| `SecurityConfigTest` | prod 確實阻擋、local 確實放行 |

### 手動驗證清單（部署後執行）

1. 電腦開啟網址 → 出現登入頁 → 輸入密碼 → 進入成功
2. 查一句未查過的中文 → 有泰文、拼音、逐詞拆解
3. 按播放 → 聽得到聲音
4. Cloud Storage 後台 → 看得到該 `.wav` 檔
5. **重新部署一次 → 再播放同一句 → 音檔仍在**（驗證持久化）
6. 手機加入主畫面 → 圖示為「ท」、名稱為 ThaiLan → 開啟後全螢幕、無網址列

---

## 9. 施工順序

**核心原則：階段 0～4 完全不碰 GCP。** 即使雲端端出現任何狀況，前面的成果都仍然可用。**每個階段結束時系統都處於可執行狀態。**

| 階段 | 內容 | 完成時的狀態 | 工時 |
|---|---|---|---|
| **0** | 匯出 `api_usage_log` 為 CSV；確認 GCP 額度餘額與到期日 | 舊紀錄留存 | 0.5h |
| **1** | 🔴 本機換 PostgreSQL：改 `pom.xml`、改寫 `schema.sql`、啟動容器 | **16 個測試全過**，本機行為同以往 | 3h |
| **2** | 🔴 音檔抽象化（先只做 `LocalDiskAudioStorage`） | 測試全過，**使用者可見行為完全未變**（音檔改由 Controller 串流輸出，網址不變） | 2h |
| **3** | 🟡 加入登入保護 | 本機仍免登入，prod 才啟用 | 1h |
| **4** | 🟡 Dockerfile + 前端併入 | **在本機以 Docker 跑起整包並可正常使用** | 2h |
| **5** | ☁️ GCP 建立資源並首次部署 | **手機瀏覽器連得上公開網址** | 3h |
| **6** | 🟢 PWA 與圖示 | **手機桌面出現 ThaiLan 圖示** | 1h |
| **7** | 🟢 預算警示 | 收得到花費通知 | 0.5h |

**合計約 13 小時**，其中：

| 分類 | 時間 |
|---|---|
| 產出程式碼 | 約 2h |
| 需人工於後台操作 | 約 1.5h |
| 純等待（建立實例、建置、部署） | 約 1h |
| **除錯緩衝** | **約 5.5h** |
| 審閱與驗證 | 其餘 |

順利約 7～8 小時，卡關可能超過 18 小時。**除錯緩衝佔比高是因為第一次部署卡關是常態**，雲端的錯誤訊息通常含糊，需多次嘗試才能定位。

**★ 階段 4 是關鍵里程碑**：在本機以 Docker 完整跑起來後，上雲端就只剩「換個地方執行」的問題，絕大多數的坑會在此階段就被發現，而非在雲端上盲目猜測。

---

## 10. 已知風險與待驗證項目

| 項目 | 說明 | 何時處理 |
|---|---|---|
| **記憶體用量未實測** | Spring Boot 啟動固定開銷約 250～400MB。需以 `-Xmx` 於本機實測後再決定 Cloud Run 記憶體規格，避免盲目付費或部署後被強制重啟 | 階段 1 一併量測 |
| **Cloud Run 記憶體與費用** | 規格未定，待上一項實測結果 | 階段 5 |
| **GCP 實際定價** | 本文件所列為查詢當下的概數，實作前需再次確認官方頁面 | 階段 5 前 |
| **$300 額度到期日** | **2026-11-14**（已於 Billing 頁面確認）。到期時試用帳戶自動關閉、服務停止，**不會自動扣款**；要繼續需手動升級為付費帳戶 | 已確認 |
| **服務帳號權限** | Cloud Run 存取 Cloud Storage 與 Cloud SQL 所需的 IAM 角色，第一次設定容易少給 | 階段 5 主要卡點 |

---

## 11. 明確排除於本次範圍之外

| 項目 | 為何排除 |
|---|---|
| **音檔改存 mp3** | 目前實際存為未壓縮的 `.wav`，體積約為 mp3 的 5～6 倍，行動網路下較耗流量。屬獨立的優化議題，納入會使本次範圍膨脹。**列為後續建議項目。** |
| 一人一組帳號、個人單字本 | 決策 #2 已選共用密碼；日後可抽換 |
| 離線快取查詢結果與音檔 | 決策 #10 |
| 自訂網域 | 決策 #13，第一次部署後可獨立進行，約 1 小時 |
| Secret Manager | 先用環境變數，日後升級 |
| 帳單超額自動關閉服務 | 決策 #9 |
| 既有資料搬遷 | 決策 #7 |
| 原生 App（apk / ipa） | PWA 已滿足需求 |
