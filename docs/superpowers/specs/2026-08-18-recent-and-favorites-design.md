# 最近搜尋與收藏 — 設計規格

- **文件日期**：2026-08-18
- **專案路徑**：`C:\Tim\language_project`
- **狀態**：已確認，待排實作計畫
- **前一份規格**：`2026-08-15-deploy-to-gcp-design.md`

---

## 1. 這次要解決什麼

### 問題一：查過的東西找不回來

每一次查詢都寫進 `translation_query`，資料一直都在，但畫面上沒有任何入口。想再看一次剛剛那句，只能把中文重打一次。

而且重打不是沒有代價：快取的鑰匙是「原文 ＋ 方向 ＋ 性別」三者的組合，打錯一個字、或當下性別切換的位置跟上次不同，就是一筆全新的查詢 —— 真的呼叫 OpenAI、真的付錢，畫面上完全看不出來。

### 問題二：沒有辦法把有用的句子留起來

「幫我叫計程車」「不要放香菜」這類真的會用到的句子，跟一時興起查的詞混在一起。使用者需要一個地方放「我之後要拿出來聽的句子」。

### 問題三：`created_at` 不能拿來當「最近」

`translation_query` 有 `created_at`，但那是「第一次查的時間」。快取命中時整列不動，所以昨天查過的句子今天再查一次，它的時間戳不會更新。直接拿 `created_at DESC` 排出來的是「第一次查的順序」，不是「最近看過的順序」。

---

## 2. 範圍

### 這次要做

| 項目 | 說明 |
|---|---|
| 最近搜尋 | 去重後最近 20 筆，依「最後一次按查詢」的時間排序 |
| 收藏 | 在查詢結果按愛心加入收藏，收藏清單依加入時間排序，無筆數上限 |
| 清單原地播放 | 清單每一列都有播放鍵，不必點進去就能聽 |
| 還原一筆查詢 | 點清單的一列，回到「查詢」分頁顯示完整結果，可續點逐詞拆解／各種說法 |
| 分頁式導覽 | 畫面上方三個分頁：查詢 ／ 最近 ／ 收藏 |

### 這次不做

| 項目 | 原因 |
|---|---|
| 收藏「單一個說法」或「逐詞的某個詞」 | 使用者的需求是「找這句話來聽」，單位是整句。說法與詞已經沉澱在 `vocabulary`，日後要做是另一個功能 |
| 完整歷史頁（查過的全都翻得到） | 「最近」的用途是短期回溯。做成完整歷史就要分頁、要能在歷史裡搜尋、要能刪掉打錯字的那些，是另一個規模的功能 |
| 查詢次數統計（這句查過幾次） | 需要事件記錄表，資料量成長最快，而目前沒有要看統計的需求 |
| 最近清單的單筆刪除 | 只留 20 筆，會自然滾掉 |
| 收藏清單的搜尋／分類／標籤 | 收藏量到幾百筆才有意義，屆時再說 |
| 收藏時自動補生缺少的音檔 | 查詢時本來就會自動合成泰文音檔，缺音檔只發生在合成失敗那幾筆。清單沿用既有的「灰鍵點了才生」即可，不值得為此多寫一條路徑 |

---

## 3. 決策紀錄

實作時如果覺得某個決定「好像可以更好」，請先讀這裡的理由再判斷。

| # | 決定 | 理由 |
|---|---|---|
| 1 | 收藏的單位是「一次查詢」（`translation_query` 的一列），不是詞也不是說法 | 使用者的需求是「找這句話來聽」。單位一旦擴大，清單裡會混著三種形狀的資料，畫面與程式都要處理型別分支 |
| 2 | 兩份清單都存後端資料庫，不用 localStorage | 收藏是會依賴很久的東西，清一次 PWA 快取或換一支手機就全沒了。副作用是手機查的在電腦上也看得到 —— 那是優點 |
| 3 | 不新增資料表，在 `translation_query` 加兩個欄位 | 收藏的單位就是這張表的一列，加一張只有兩個欄位的表要多一次 join，換不到什麼 |
| 4 | `favorited_at` 為 `NULL` 就代表「沒收藏」，不另設 boolean 旗標 | 一個欄位同時當旗標和排序依據。多一個 boolean 就多一種「旗標是 true 但時間是 NULL」的不一致狀態 |
| 5 | ★ 點清單的一列是「用 id 還原」，不是「把文字填回輸入框重查」 | 重查會經過快取鑰匙的比對。那筆是男生版而當下切在女生，就是一筆全新查詢，真的花錢，而畫面上看起來完全正常 |
| 6 | ★ 新增的五支 API 全部是 `GET` / `PUT` / `DELETE`，沒有一支 `POST` | 既有三支用 `POST` 的理由是「可能呼叫 OpenAI 花錢」。新的五支保證不會，用不同的動詞讓「會花錢」和「不會花錢」在網址層面就分得開 |
| 7 | `last_viewed_at` 只在使用者真的按「查詢」時更新，從清單還原不更新 | 否則翻一輪收藏就會把「最近」洗成另一個順序，清單在眼皮底下跳動。「最近」記的是查了什麼，不是點了什麼 |
| 8 | 加入收藏用 `PUT`，且已收藏過的再 `PUT` 一次不覆寫 `favorited_at` | `PUT` 代表「把它設成收藏狀態」，重複呼叫結果一致。覆寫時間會讓清單排序莫名其妙跳動 |
| 9 | ★ 點清單項目「不會」改動畫面上的性別切換 | 性別切換是持久設定（存 localStorage），代表「我是誰」。被清單默默改掉的話，下一句自己打的字會用到錯的性別，而那是一筆真的會花錢的新查詢 |
| 10 | 清單每一列標示自己的性別（男／女／泰→中） | 承上，還原出來的內容可能與當下的性別設定不符。不標的話會看到「我選女生怎麼跑出 ครับ」 |
| 11 | 清單用獨立的 `TranslationSummaryDto`，不重用 `TranslationResponseDto` | `fromCache` 與 `isWord` 在清單的情境下沒有意義，硬塞會讓前端不知道能不能信任它們 |
| 12 | ★ 音檔網址用一次 `IN` 查詢批次撈，不是每列查一次 | 音檔在 `audio_asset`，JPQL 的建構子投影跨不了表。每列查一次就是 N+1：收藏一百筆就是一百次查詢。這件事在資料少的時候完全看不出來 |
| 13 | 「最近」與「收藏」共用同一個前端元件 | 兩者版面只差三個細節。寫成兩個元件等於維護兩份幾乎一樣的 HTML 與 CSS，改一邊忘了改另一邊是遲早的事 |
| 14 | 分頁切換用 signal，不引入 Angular Router | 三個分頁、不需要分享網址。加 router 要多一套設定並顧到 service worker 的路由 fallback。代價是手機的返回手勢會直接離開 App 而不是退回上一個分頁 |
| 15 | `translation.ts` 現有的 729 行不重構 | 它原封不動變成「查詢」分頁的內容，只多一顆愛心。這次的目標不是整理那個檔案 |
| 16 | 收藏無筆數上限 | 使用者自己按的東西不該被系統丟掉。真的多到有效能問題時再處理 |

---

## 4. 資料表變更

### 4.1 執行方式

沿用 `db/schema.sql` 一貫的作法：**可重複執行、不刪任何資料**。

★ 新欄位要寫成獨立的 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`，不可以只加在 `CREATE TABLE` 裡面。`CREATE TABLE IF NOT EXISTS` 在既有的資料庫（本機與 Cloud SQL）會整段被跳過，新欄位永遠不會出現，程式一啟動就會在查詢時炸掉說找不到欄位。這與 `is_word`（2026-08-17）踩到的是同一個坑。

### 4.2 `translation_query` 新增欄位

| 欄位 | 型別 | 可為空 | 意義 |
|---|---|---|---|
| `last_viewed_at` | `TIMESTAMP` | 是 | 最後一次「使用者按下查詢」而命中或建立這一列的時間。`NULL` 代表這一列早於本功能，不出現在最近清單 |
| `favorited_at` | `TIMESTAMP` | 是 | 加入收藏的時間。**`NULL` 就代表沒有收藏** |

```sql
ALTER TABLE translation_query
    ADD COLUMN IF NOT EXISTS last_viewed_at TIMESTAMP;

ALTER TABLE translation_query
    ADD COLUMN IF NOT EXISTS favorited_at   TIMESTAMP;
```

★ 兩個欄位都**不要**補 `NOT NULL` 或 `DEFAULT`。`favorited_at` 補了預設值等於全部舊資料都變成已收藏；`last_viewed_at` 補 `CURRENT_TIMESTAMP` 的話，所有舊資料會在同一秒被視為「剛剛看過」，最近清單第一次打開就是一批亂序的舊東西。

### 4.3 索引

```sql
-- 最近清單的排序依據
CREATE INDEX IF NOT EXISTS ix_translation_query_last_viewed_at
    ON translation_query (last_viewed_at DESC);

-- 收藏清單的排序依據。
-- ★ 只索引「有收藏」的列（partial index）—— 絕大多數列的 favorited_at 是 NULL，
--   把它們一起放進索引只是讓索引變大、寫入變慢，查詢一點也不會變快。
CREATE INDEX IF NOT EXISTS ix_translation_query_favorited_at
    ON translation_query (favorited_at DESC)
    WHERE favorited_at IS NOT NULL;
```

### 4.4 已知取捨

`translation_query` 在既有註解裡的定位是「查詢結果**快取**」，這次在它身上掛了「使用者的收藏」。

後果：**這張表以後不能做快取淘汰**。哪天想清掉三個月沒用的舊查詢省空間，會連收藏一起清掉。

接受這個取捨的理由：這張表的每一列都是花過錢的 AI 結果，越舊越有價值，本來就沒有淘汰的動機。真的需要淘汰時，改法是「淘汰時跳過 `favorited_at IS NOT NULL` 的列」，而不是回頭拆表。

---

## 5. 後端 API

五支全部掛在既有的 `TranslationController`（`/api/v1/translations`）底下。

### 5.1 最近搜尋

```
GET /api/v1/translations/recent
→ 200 OK
[
  {
    "queryId": 137,
    "chineseText": "幫我叫計程車",
    "thaiText": "ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ",
    "romanization": "chûai rîak tháek-sîi hâi pǒm nòi khráp",
    "direction": "ZH_TO_TH",
    "gender": "MALE",
    "thaiAudioUrl": "/audio/th/a3f9c2b81e47.mp3",
    "favorited": true
  },
  ...
]
```

- `last_viewed_at IS NOT NULL` 的列，依 `last_viewed_at DESC`，最多 20 筆
- 沒有任何紀錄時回空陣列（`200`，不是 `404`）

### 5.2 收藏清單

```
GET /api/v1/translations/favorites
→ 200 OK  （格式同上，favorited 恆為 true）
```

- `favorited_at IS NOT NULL` 的列，依 `favorited_at DESC`，無上限

### 5.3 加入 / 取消收藏

```
PUT    /api/v1/translations/{id}/favorite   → 204 No Content
DELETE /api/v1/translations/{id}/favorite   → 204 No Content
```

- `PUT`：`favorited_at` 為 `NULL` 時設成現在時間；**已經有值就不動**
- `DELETE`：`favorited_at` 設回 `NULL`
- 兩者對不存在的 `id` 都回 `404`，走既有的 `BusinessException` ＋ `GlobalExceptionHandler`
- 回 `204` 不回內容：前端已經知道自己按了什麼，回傳整列只是多餘的傳輸

### 5.4 還原一筆查詢

```
GET /api/v1/translations/{id}
→ 200 OK  （TranslationResponseDto，與 POST 翻譯的回應同一個格式）
```

- ★ **保證不呼叫 OpenAI**：只從 `translation_query` 讀，音檔只用 `findExistingAudioUrl`（只查不生）
- `fromCache` 固定為 `true`
- **不更新 `last_viewed_at`**（決策 7）
- 找不到 `id` 回 `404`
- 逐詞拆解與各種說法不隨這支回傳，維持既有的「點了才載」

### 5.5 `TranslationSummaryDto`

```java
public record TranslationSummaryDto(
        Long queryId,
        String chineseText,
        String thaiText,
        String romanization,
        TranslationDirectionEnum direction,
        SpeakerGenderEnum gender,
        String thaiAudioUrl,
        boolean favorited) { }
```

純宣告的 record，不加流程註解。

---

## 6. 後端程式異動

| 檔案 | 動作 | 內容 |
|---|---|---|
| `db/schema.sql` | 修改 | 第 4 節的兩個 `ALTER TABLE` 與兩個索引，並在 `CREATE TABLE` 區塊補上欄位說明 |
| `entity/TranslationQuery.java` | 修改 | 加 `lastViewedAt`、`favoritedAt` 兩個 `LocalDateTime` 欄位 |
| `dto/response/TranslationSummaryDto.java` | 新增 | 見 5.5 |
| `repository/TranslationQueryRepository.java` | 修改 | 見下方 |
| `repository/AudioAssetRepository.java` | 修改 | 加批次查詢：`findByLanguageAndSpeechTextIn(...)` |
| `service/TranslationService.java` | 修改 | `translate()` 內更新 `last_viewed_at`；新增 `resolveById()` |
| `service/QueryListService.java` | 新增 | 最近／收藏清單的組裝，以及加入／取消收藏 |
| `controller/TranslationController.java` | 修改 | 五個新端點 |

### 6.1 Repository 新增方法

```java
// 最近 20 筆。Pageable 由 Service 傳 PageRequest.of(0, 20) 進來。
@Query("""
        SELECT new com.tim.language_project.dto.response.TranslationSummaryDto(
                   q.id, q.chineseText, q.thaiText, q.romanization,
                   q.direction, q.gender, NULL,
                   CASE WHEN q.favoritedAt IS NOT NULL THEN TRUE ELSE FALSE END)
        FROM TranslationQuery q
        WHERE q.lastViewedAt IS NOT NULL
        ORDER BY q.lastViewedAt DESC
        """)
List<TranslationSummaryDto> findRecent(Pageable pageable);
```

★ 投影裡的音檔欄位是 `NULL`，由 Service 事後補上 —— 音檔在 `audio_asset`，JPQL 的建構子投影跨不了表。這與 `withSegmentAudio()` 遇到的是同一件事。

★ 建構子投影裡的裸 `NULL` 有可能被 Hibernate 拒絕（推不出型別）。真的報錯就寫成 `CAST(NULL AS string)`，不要為了閃它把 DTO 的欄位型別改掉。

收藏清單同理（`favoritedAt IS NOT NULL` ／ `ORDER BY q.favoritedAt DESC`）。

加入與取消用 `@Modifying @Query` 的 update，兩支都以 `id` 為條件；加入那支多一個 `AND q.favoritedAt IS NULL`，如此「已收藏再按一次」自然不覆寫時間，不需要先讀出來判斷。

### 6.2 ★ 音檔要批次撈，不可以每列查一次

清單組裝的正確作法：

1. Repository 回一批 `TranslationSummaryDto`（音檔欄位是 `null`）
2. 把這批的 `thaiText` 收成一個 `Set`
3. **一次** `findByLanguageAndSpeechTextIn(TH, thaiTexts)`，做成 `Map<String, String>`（文字 → 網址）
4. 逐列從 Map 取出網址，組成最終清單

每列各查一次是 N+1：收藏一百筆就是一百次資料庫往返。這件事在資料只有十幾筆的時候完全看不出來，累積之後每次打開收藏都會慢一拍，而且不會有任何錯誤訊息。

### 6.3 `last_viewed_at` 的更新位置

在 `TranslationService.translate()` 裡，**快取命中與新建立兩條路都要更新**。只更新其中一條的話，常查的句子反而永遠停在清單底部。

這是唯一會寫這個欄位的地方。

---

## 7. 前端

### 7.1 結構

```
app.html         分頁殼：三顆分頁鍵 + 依當前分頁顯示內容
├─ translation   既有元件，原封不動當「查詢」分頁的內容
└─ query-list    新元件，「最近」與「收藏」共用
```

| 檔案 | 動作 | 內容 |
|---|---|---|
| `app.ts` / `app.html` / `app.css` | 修改 | 分頁狀態（signal）與分頁鍵樣式 |
| `models/translation.ts` | 修改 | 加 `TranslationSummary` 介面 |
| `services/translation-service.ts` | 修改 | 五個新方法對應第 5 節的五支 API |
| `translation/query-list/*` | 新增 | 清單元件（ts / html / css） |
| `translation/translation.ts` / `.html` | 修改 | 結果區加一顆愛心；提供「從外部還原一筆結果」的入口 |

### 7.2 清單元件

一個輸入參數決定模式（`'recent'` ／ `'favorite'`），差別只有三處：打哪支 API、愛心是實心還空心、有沒有筆數上限。其餘版面完全共用。

一列的內容，由上到下：

```
中文（標題，掃描用）                    〔性別標籤〕 ♥
泰文（大字）
羅馬拼音（小字、灰）                                 ▶
```

- 中文放第一行當標題：使用者在清單裡找東西是靠中文找的
- 性別標籤三種值：`男` ／ `女` ／ `泰→中`（`gender` 為 `null` 時）
- `▶`：沿用既有的播放鍵行為 —— `thaiAudioUrl` 為 `null` 時是灰的，點了才合成
- `♥`：最近清單是空心（點了加入收藏），收藏清單是實心（點了取消，該列從畫面消失）
- **點整列**（非 `▶`、非 `♥`）→ 呼叫 `GET /{id}`，切到「查詢」分頁顯示完整結果

空清單時顯示一句灰字：最近是「還沒有查過任何東西」，收藏是「在查詢結果按 ♡ 就會收進這裡」。

### 7.3 資料何時載入

切到該分頁時打一次 API。不做前端快取 —— 資料量小，而且每次切過去都是最新的比較不會有困惑。

收藏／取消之後只改本地的 signal，不重打清單 API。

### 7.4 錯誤處理

沿用既有作法：`401` 由 `auth-interceptor` 帶去登入頁，其餘顯示一行紅字並提供重試。收藏切換失敗時愛心要退回原本的狀態 —— 不可以留在「看起來成功了」的樣子。

---

## 8. 測試範圍

| 測試 | 防什麼 |
|---|---|
| `TranslationQueryRepositoryTest` | 最近清單只含 `last_viewed_at` 非空的列、排序正確、上限 20；收藏清單排序正確；已收藏再 `PUT` 不覆寫時間 |
| `QueryListServiceTest` | 音檔以批次查詢補上（驗證只呼叫一次批次方法，不是每列一次）；缺音檔的列 `thaiAudioUrl` 為 `null` 而不是整支失敗 |
| `TranslationServiceTest` | 快取命中與新建立兩條路都更新 `last_viewed_at`；`resolveById()` 完全不呼叫 `TranslationClient` 與 `SpeechClient` |
| `TranslationControllerTest` | 五支端點的狀態碼；不存在的 `id` 回 `404` |

★ `@DataJpaTest` 等測試切片註解在 Spring Boot 4.1 已換套件路徑，照 3.x 範例寫會編譯失敗。

---

## 9. 風險與注意事項

| # | 風險 | 對策 |
|---|---|---|
| 1 | 忘了寫 `ALTER TABLE ADD COLUMN IF NOT EXISTS`，只加在 `CREATE TABLE` 裡 | 既有資料庫不會有新欄位，程式一啟動就炸。與 `is_word` 同一個坑，見 4.1 |
| 2 | 清單的音檔用每列查一次 | N+1，資料少時看不出來。見 6.2 |
| 3 | 點清單項目時順手改了性別設定 | 下一次自己打的字會用錯的性別查，產生會花錢的新查詢。見決策 9 |
| 4 | 把「還原」實作成「重新查一次」 | 同上，會真的花錢。見決策 5 |
| 5 | `favorited_at` 補了 `DEFAULT` | 全部舊資料變成已收藏。見 4.2 |
| 6 | 前端愛心在 API 失敗後停留在成功狀態 | 使用者以為收藏了，下次打開收藏清單找不到。見 7.4 |
