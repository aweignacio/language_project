# 最近搜尋與收藏 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 讓使用者看得到最近查過的 20 筆查詢、能把整句加入收藏，並在清單裡直接播放或還原成完整結果。

**Architecture:** 不新增資料表 —— 在既有的 `translation_query` 加 `last_viewed_at` 與 `favorited_at` 兩個可為空的時間欄位（`favorited_at` 為 `NULL` 就代表沒收藏）。後端新增五支不花錢的端點（`GET`／`PUT`／`DELETE`），與既有會呼叫 OpenAI 的 `POST` 端點在動詞上就分開。前端把單一畫面改成「查詢／最近／收藏」三個分頁，兩份清單共用同一個元件。

**Tech Stack:** Java 25 / Spring Boot 4.1 / Spring Data JPA / PostgreSQL 15+ / Angular 22（zoneless、signal）/ JUnit 6 + AssertJ + Mockito / Vitest

**Spec:** `docs/superpowers/specs/2026-08-18-recent-and-favorites-design.md`

## Global Constraints

- 註解一律**繁體中文**、Javadoc 風格，**不可**使用 `<p>` `</p>`。類別名稱、註解、`null`、`token`、HTTP 狀態碼等技術名詞保持原文。
- **有執行流程的檔案**（Service、Client、有邏輯的元件、**所有測試檔**）要在 `package` 之後加中文區塊註解說明流程：由上往下、從使用者的實際動作開始、分步驟編號、標明誰／哪個方法／資料實際長相、貼真實資料、名詞第一次出現要解釋為什麼需要它、用 ★ 標出最容易搞混的地方。**純宣告的檔案不要加**（Repository、DTO、record、enum、Entity）。
- 空值與相等比較**禁止**直接用 `== null`、`!= null`、`.equals()`。一律用 `Objects.isNull` / `Objects.nonNull` / `ObjectUtils.isEmpty` / `ObjectUtils.isNotEmpty` / `Objects.equals`。
- Lambda 參數**不可**用單字母，要用能看出型別的名字（`summary`、`asset`、`queryId`）。
- 固定值一律用 Enum，類別名以 `Enum` 結尾，加 Lombok `@Getter`。本計畫**不需要新增任何 Enum** —— 「找不到查詢」重用既有的 `ErrorCodeEnum.RESOURCE_NOT_FOUND`。
- commit 訊息格式：英文標籤 ＋ 繁體中文描述（`Feat:` / `Fix:` / `Improve:` / `Modify:`），結尾加 `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`。**絕對不可以用 `--no-verify`。**
- 測試切片註解在 Spring Boot 4.1 已換套件路徑：`org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`、`org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`、`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`。網路上的 3.x 範例（`org.springframework.boot.test.autoconfigure.*`）在這個專案會編譯失敗。
- 後端測試指令：`.\mvnw.cmd -B test -Dtest=類別名稱`。IntelliJ 2023.3 的綠色箭頭跑不動 JUnit 6，一律用 Maven 執行。
- Repository 測試連的是**真的 PostgreSQL**（`@AutoConfigureTestDatabase(replace = NONE)`），跑之前容器要開著。
- 前端測試指令：`npm test`（於 `frontend/` 目錄）。

**執行前置條件：** 資料庫容器已啟動（`docker compose up -d`，於專案根目錄）。

---

## 檔案結構

### 後端新增

| 檔案 | 職責 |
|---|---|
| `dto/response/TranslationSummaryDto.java` | 清單一列的形狀。純宣告，不加流程註解 |
| `service/QueryListService.java` | 兩份清單的組裝與收藏的加入／取消。唯一知道「清單要怎麼補音檔」的地方 |
| `test/.../repository/TranslationQueryListRepositoryTest.java` | 最近與收藏兩個查詢、以及三個 update 的行為 |
| `test/.../service/QueryListServiceTest.java` | 音檔批次補上、收藏開關 |

### 後端修改

| 檔案 | 改什麼 |
|---|---|
| `db/schema.sql` | 兩個 `ALTER TABLE ADD COLUMN IF NOT EXISTS`、兩個索引、`CREATE TABLE` 區塊補欄位說明 |
| `entity/TranslationQuery.java` | 加 `lastViewedAt`、`favoritedAt` |
| `repository/TranslationQueryRepository.java` | 加 `findRecent` / `findFavorites` / `touchLastViewedAt` / `markFavorite` / `clearFavorite` |
| `repository/AudioAssetRepository.java` | 加 `findBySpeechTextInAndLanguage` |
| `service/AudioAssetService.java` | 加 `findExistingAudioUrls`（批次版） |
| `service/TranslationService.java` | `translate()` 更新 `last_viewed_at`；新增 `resolveById()` |
| `controller/TranslationController.java` | 五個新端點 |
| `test/.../controller/TranslationControllerTest.java` | 五個新端點的測試 |
| `test/.../service/TranslationServiceTest.java` | `last_viewed_at` 與 `resolveById` 的測試 |

### 前端新增

| 檔案 | 職責 |
|---|---|
| `services/audio-player.ts` | 全站唯一的播放器：一個 `<audio>` ＋ 一個放大器。從 `translation.ts` 搬出來，兩個元件共用 |
| `translation/query-list/query-list.ts` / `.html` / `.css` | 清單元件，「最近」與「收藏」共用 |

### 前端修改

| 檔案 | 改什麼 |
|---|---|
| `models/translation.ts` | 加 `TranslationSummary` |
| `services/translation-service.ts` | 五個新方法 |
| `app.ts` / `app.html` / `app.css` | 分頁殼 |
| `translation/translation.ts` / `.html` | 結果區加愛心；提供外部還原一筆結果的入口；播放改用 `AudioPlayerService` |
| `app.spec.ts` | 分頁殼改版後的煙霧測試 |

---

## Task 1：資料表欄位與 Entity

**Files:**
- Modify: `db/schema.sql`
- Modify: `src/main/java/com/tim/language_project/entity/TranslationQuery.java`
- Test: `src/test/java/com/tim/language_project/repository/TranslationQueryListRepositoryTest.java`（新建，本任務只放第一個測試）

**Interfaces:**
- Consumes: 無
- Produces: `TranslationQuery.getLastViewedAt()` / `setLastViewedAt(LocalDateTime)`、`getFavoritedAt()` / `setFavoritedAt(LocalDateTime)`；資料表欄位 `last_viewed_at`、`favorited_at`

- [ ] **Step 1: 先寫會失敗的測試**

新建 `src/test/java/com/tim/language_project/repository/TranslationQueryListRepositoryTest.java`：

```java
package com.tim.language_project.repository;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案在測什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測「最近搜尋」與「收藏」這兩份清單，在資料庫這一層的行為。
 *
 * ── 第 1 步｜你在網頁輸入「我想喝酒」，按下查詢 ─────────────────────────
 *
 *    後端翻完之後，除了把結果存進 translation_query，還會把這一列的
 *    last_viewed_at 更新成現在時間。這個欄位就是「最近」清單的排序依據。
 *
 *    ★ 為什麼不能用既有的 created_at？
 *      created_at 是「第一次查的時間」，快取命中時整列不動。
 *      昨天查過的句子今天再查一次，created_at 不會變 ——
 *      拿它排序，排出來的是第一次查的順序，不是最近看過的順序。
 *
 * ── 第 2 步｜你按下畫面上的愛心 ─────────────────────────────────────────
 *
 *    favorited_at 被設成現在時間。這個欄位一欄兩用：
 *
 *        favorited_at IS NULL      → 沒有收藏
 *        favorited_at IS NOT NULL  → 有收藏，而且值就是收藏清單的排序依據
 *
 *    ★ 所以不需要另一個 boolean 欄位。多一個的話就多一種
 *      「旗標是 true 但時間是 null」的不一致狀態要處理。
 *
 * ── 什麼東西被換成假的 ──────────────────────────────────────────────────
 *
 *    ★ 一個都沒有。這支連的是真正的 PostgreSQL
 *      （@AutoConfigureTestDatabase(replace = NONE)），
 *      因為要驗的正是「資料庫欄位真的存在、真的存得進去」。
 *      換成記憶體資料庫的話，schema.sql 漏改也測得過，這支就白寫了。
 *
 *  ※ 測試的基本概念寫在 VocabularyRepositoryTest 檔頭，第一次看從那支開始。
 */

import com.tim.language_project.entity.TranslationQuery;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TranslationQueryListRepositoryTest {

    @Autowired
    private TranslationQueryRepository translationQueryRepository;

    /*
     * ═══ 測試一：兩個新欄位存得進去、讀得回來 ═══════════════════════════
     *
     * 這支防的是「schema.sql 改了但既有資料庫沒跑到」這件事。
     * ★ CREATE TABLE IF NOT EXISTS 在早就建好表的資料庫會整段被跳過，
     *   新欄位永遠不會出現 —— 必須另外寫 ALTER TABLE ADD COLUMN IF NOT EXISTS。
     *   這與 is_word（2026-08-17）踩到的是同一個坑。
     */
    @Test
    @DisplayName("last_viewed_at 與 favorited_at 應可寫入並讀回")
    void shouldPersistListColumns() {
        LocalDateTime viewedAt = LocalDateTime.of(2026, 8, 18, 10, 30);
        LocalDateTime favoritedAt = LocalDateTime.of(2026, 8, 18, 11, 0);

        TranslationQuery query = newQuery("測試勿刪清單欄位", "ผมอยากดื่มเหล้าครับ");
        query.setLastViewedAt(viewedAt);
        query.setFavoritedAt(favoritedAt);

        TranslationQuery saved = translationQueryRepository.saveAndFlush(query);

        TranslationQuery found = translationQueryRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getLastViewedAt()).isEqualTo(viewedAt);
        assertThat(found.getFavoritedAt()).isEqualTo(favoritedAt);
    }

    /**
     * 組一筆最小可寫入的查詢。
     * gender 固定用 MALE、direction 固定 ZH_TO_TH —— 這支測的是清單欄位，
     * 那兩個欄位只是為了滿足 NOT NULL 而存在。
     */
    private TranslationQuery newQuery(String sourceText, String thaiText) {
        TranslationQuery query = new TranslationQuery();
        query.setSourceText(sourceText);
        query.setDirection(TranslationDirectionEnum.ZH_TO_TH);
        query.setGender(SpeakerGenderEnum.MALE);
        query.setChineseText(sourceText);
        query.setThaiText(thaiText);
        query.setRomanization("pǒm yàak dùuem lâo khráp");

        return query;
    }
}
```

- [ ] **Step 2: 跑測試確認它失敗**

```
.\mvnw.cmd -B test -Dtest=TranslationQueryListRepositoryTest
```

預期：編譯失敗，`cannot find symbol: method setLastViewedAt`。

- [ ] **Step 3: Entity 加兩個欄位**

在 `TranslationQuery.java` 的 `isWord` 之後、`createdAt` 之前插入：

```java
    /**
     * 最後一次「使用者按下查詢」而命中或建立這一列的時間，「最近搜尋」清單的排序依據。
     *
     * ★ 不能用 createdAt 代替：那是第一次查的時間，快取命中時整列不動，
     *   拿它排序會排出「第一次查的順序」而不是「最近看過的順序」。
     *
     * ★ 從清單點進去還原一筆時「不會」更新這個欄位 ——
     *   否則翻一輪收藏就會把最近清單洗成另一個順序，清單在眼皮底下跳動。
     *
     * null 代表這一列早於本功能（2026-08-18 之前），不會出現在最近清單。
     */
    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;

    /**
     * 加入收藏的時間。★ null 就代表「沒有收藏」，一欄同時當旗標與排序依據。
     *
     * 不另外設一個 boolean 旗標，是因為兩個欄位就會多出
     * 「旗標是 true 但時間是 null」這種不一致狀態要處理。
     */
    @Column(name = "favorited_at")
    private LocalDateTime favoritedAt;
```

- [ ] **Step 4: `db/schema.sql` 補欄位與索引**

在 `translation_query` 的 `CREATE TABLE` 區塊裡，`is_word` 之後、`created_at` 之前加上欄位宣告（新資料庫走這條）：

```sql
    /*
     * 「最近搜尋」的排序依據：最後一次「使用者按下查詢」而命中或建立這一列的時間。
     *
     * ★ 不可以拿 created_at 代替。created_at 是第一次查的時間，
     *   快取命中時整列不動 —— 昨天查過的句子今天再查一次它也不會變，
     *   拿它排序排出來的是「第一次查的順序」，不是「最近看過的順序」。
     *
     * ★ 從清單點進去還原一筆時不更新這個欄位。更新的話，
     *   翻一輪收藏就會把最近清單洗成另一個順序。
     */
    last_viewed_at TIMESTAMP     NULL,

    /*
     * 加入收藏的時間。★ NULL 就代表「沒有收藏」——
     * 一個欄位同時當旗標與排序依據，不需要第二個 boolean。
     *
     * ★ 這個欄位不要補 DEFAULT。補了的話全部舊資料會一次變成「已收藏」，
     *   而且畫面上看起來完全正常，你只會覺得收藏清單裡多了一堆沒印象的東西。
     */
    favorited_at   TIMESTAMP     NULL,
```

在既有的 `ALTER TABLE translation_query ADD COLUMN IF NOT EXISTS is_word BOOLEAN;` 之後追加（既有資料庫走這條）：

```sql
/*
 * ★ 補欄位給「已經存在」的資料庫（2026-08-18）。
 *
 * 理由與上面的 is_word 完全相同：CREATE TABLE IF NOT EXISTS 在早就建好表的
 * 環境（你的本機、正式的 Cloud SQL）整段會被跳過，新欄位永遠不會出現，
 * 程式一啟動就會在查詢時炸掉說找不到欄位。
 *
 * 兩個欄位都保持 NULL，那是正確的初始狀態：
 *   last_viewed_at NULL → 這一列早於本功能，不出現在最近清單
 *   favorited_at   NULL → 沒有收藏
 */
ALTER TABLE translation_query
    ADD COLUMN IF NOT EXISTS last_viewed_at TIMESTAMP;

ALTER TABLE translation_query
    ADD COLUMN IF NOT EXISTS favorited_at TIMESTAMP;

-- 「最近搜尋」的排序依據。沒有它，每次打開最近清單都會整表掃描後再排序。
CREATE INDEX IF NOT EXISTS ix_translation_query_last_viewed_at
    ON translation_query (last_viewed_at DESC);

/*
 * 「收藏」的排序依據。
 *
 * ★ 這是 partial index —— 只索引真的有收藏的那些列。
 *   絕大多數列的 favorited_at 是 NULL，把它們一起放進索引只會讓索引變大、
 *   每次寫入多做一次維護，而查詢一點也不會變快（反正條件就是 IS NOT NULL）。
 */
CREATE INDEX IF NOT EXISTS ix_translation_query_favorited_at
    ON translation_query (favorited_at DESC)
    WHERE favorited_at IS NOT NULL;
```

- [ ] **Step 5: 把 schema 套用到本機資料庫**

```bash
docker cp db/schema.sql language-project-postgres:/tmp/schema.sql
docker exec language-project-postgres psql -U postgres -d language_project -f /tmp/schema.sql
```

預期輸出裡看得到 `ALTER TABLE` 與 `CREATE INDEX`，沒有 `ERROR`。

- [ ] **Step 6: 跑測試確認通過**

```
.\mvnw.cmd -B test -Dtest=TranslationQueryListRepositoryTest
```

預期：PASS。

- [ ] **Step 7: Commit**

```bash
git add db/schema.sql src/main/java/com/tim/language_project/entity/TranslationQuery.java src/test/java/com/tim/language_project/repository/TranslationQueryListRepositoryTest.java
git commit -m "$(cat <<'EOF'
新增最近搜尋與收藏的資料表欄位

Feat:
- translation_query 新增 last_viewed_at 與 favorited_at
- favorited_at 為 NULL 即代表未收藏，一欄兼作旗標與排序依據
- 收藏索引採 partial index，只索引真的有收藏的列

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2：清單 DTO 與 Repository 查詢

**Files:**
- Create: `src/main/java/com/tim/language_project/dto/response/TranslationSummaryDto.java`
- Modify: `src/main/java/com/tim/language_project/repository/TranslationQueryRepository.java`
- Test: `src/test/java/com/tim/language_project/repository/TranslationQueryListRepositoryTest.java`

**Interfaces:**
- Consumes: Task 1 的 `lastViewedAt` / `favoritedAt`
- Produces:
  - `record TranslationSummaryDto(Long queryId, String chineseText, String thaiText, String romanization, TranslationDirectionEnum direction, SpeakerGenderEnum gender, String thaiAudioUrl, boolean favorited)`
  - `List<TranslationSummaryDto> TranslationQueryRepository.findRecent(Pageable pageable)`
  - `List<TranslationSummaryDto> TranslationQueryRepository.findFavorites()`
  - `int TranslationQueryRepository.touchLastViewedAt(Long id, LocalDateTime viewedAt)`
  - `int TranslationQueryRepository.markFavorite(Long id, LocalDateTime favoritedAt)`
  - `int TranslationQueryRepository.clearFavorite(Long id)`

- [ ] **Step 1: 建立 DTO**

`src/main/java/com/tim/language_project/dto/response/TranslationSummaryDto.java`：

```java
package com.tim.language_project.dto.response;

import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;

/**
 * 「最近搜尋」與「收藏」清單裡的一列。
 *
 * 刻意不重用 TranslationResponseDto：那個 record 上的 fromCache 與 isWord
 * 在清單的情境下沒有意義，硬塞會讓前端不知道能不能信任它們。
 *
 * thaiAudioUrl 為 null 代表音檔還沒產生，前端顯示成灰色的播放鍵，點了才合成。
 */
public record TranslationSummaryDto(
        Long queryId,
        String chineseText,
        String thaiText,
        String romanization,
        TranslationDirectionEnum direction,
        SpeakerGenderEnum gender,
        String thaiAudioUrl,
        boolean favorited) {
}
```

- [ ] **Step 2: 寫會失敗的測試**

在 `TranslationQueryListRepositoryTest` 的 `newQuery` 之前插入四個測試，並補上 import：

```java
import com.tim.language_project.dto.response.TranslationSummaryDto;
import org.springframework.data.domain.PageRequest;
import java.util.List;
```

```java
    /*
     * ═══ 測試二：最近清單只收 last_viewed_at 有值的列，且新的排前面 ═══════
     *
     * 兩件事一起驗：
     *   ① 沒有 last_viewed_at 的列（2026-08-18 之前的舊資料）不可以混進來
     *   ② 排序是「時間新的在前」
     */
    @Test
    @DisplayName("最近清單應只含有 last_viewed_at 的列且新的在前")
    void shouldReturnRecentOrderedByLastViewedAt() {
        TranslationQuery older = newQuery("測試勿刪最近舊", "เก่า");
        older.setLastViewedAt(LocalDateTime.of(2026, 8, 18, 9, 0));

        TranslationQuery newer = newQuery("測試勿刪最近新", "ใหม่");
        newer.setLastViewedAt(LocalDateTime.of(2026, 8, 18, 10, 0));

        // 這一筆沒有 last_viewed_at，代表本功能上線前就存在的舊資料。
        TranslationQuery legacy = newQuery("測試勿刪最近舊資料", "เดิม");

        translationQueryRepository.saveAndFlush(older);
        translationQueryRepository.saveAndFlush(newer);
        translationQueryRepository.saveAndFlush(legacy);

        List<TranslationSummaryDto> recent =
                translationQueryRepository.findRecent(PageRequest.of(0, 20));

        // 我主張：新的排在舊的前面。
        assertThat(recent).extracting(TranslationSummaryDto::chineseText)
                .containsSubsequence("測試勿刪最近新", "測試勿刪最近舊");

        // 我主張：沒有 last_viewed_at 的舊資料完全不出現。
        assertThat(recent).extracting(TranslationSummaryDto::chineseText)
                .doesNotContain("測試勿刪最近舊資料");
    }

    /*
     * ═══ 測試三：收藏清單只收 favorited_at 有值的列 ══════════════════════
     *
     * ★ 這支同時驗了 favorited 這個布林欄位真的有算出來。
     *   算錯的話前端的愛心會全部顯示成空心，使用者會以為收藏沒存到，
     *   然後重複按 —— 而畫面上看不出任何異常。
     */
    @Test
    @DisplayName("收藏清單應只含有 favorited_at 的列並標記 favorited")
    void shouldReturnFavoritesOnly() {
        TranslationQuery favorited = newQuery("測試勿刪已收藏", "ชอบ");
        favorited.setFavoritedAt(LocalDateTime.of(2026, 8, 18, 11, 0));

        TranslationQuery plain = newQuery("測試勿刪未收藏", "ไม่ชอบ");

        translationQueryRepository.saveAndFlush(favorited);
        translationQueryRepository.saveAndFlush(plain);

        List<TranslationSummaryDto> favorites = translationQueryRepository.findFavorites();

        assertThat(favorites).extracting(TranslationSummaryDto::chineseText)
                .contains("測試勿刪已收藏")
                .doesNotContain("測試勿刪未收藏");

        assertThat(favorites).allMatch(TranslationSummaryDto::favorited);
    }

    /*
     * ═══ 測試四：已收藏的再按一次，收藏時間不可以被覆寫 ═════════════════
     *
     * ★ 覆寫的話收藏清單的排序會莫名其妙跳動 ——
     *   你只是重新整理了一下畫面，某一句就突然跑到最上面。
     *
     * 防法寫在 SQL 的條件裡（AND favoritedAt IS NULL），
     * 不是在 Service 先讀出來判斷 —— 讀了再寫中間會有空隙。
     */
    @Test
    @DisplayName("已收藏的再次加入不應覆寫 favorited_at")
    void shouldNotOverwriteExistingFavoritedAt() {
        LocalDateTime original = LocalDateTime.of(2026, 8, 18, 11, 0);

        TranslationQuery query = newQuery("測試勿刪重複收藏", "ซ้ำ");
        query.setFavoritedAt(original);

        TranslationQuery saved = translationQueryRepository.saveAndFlush(query);

        int affected = translationQueryRepository.markFavorite(
                saved.getId(), LocalDateTime.of(2026, 8, 18, 12, 0));

        // 我主張：一列都沒有被改到（條件裡的 IS NULL 擋下來了）。
        assertThat(affected).isZero();
    }

    /*
     * ═══ 測試五：touchLastViewedAt 與 clearFavorite 真的有寫進去 ═════════
     */
    @Test
    @DisplayName("更新最後查看時間與取消收藏應生效")
    void shouldTouchLastViewedAtAndClearFavorite() {
        TranslationQuery query = newQuery("測試勿刪更新", "อัปเดต");
        query.setFavoritedAt(LocalDateTime.of(2026, 8, 18, 11, 0));

        TranslationQuery saved = translationQueryRepository.saveAndFlush(query);

        LocalDateTime viewedAt = LocalDateTime.of(2026, 8, 18, 13, 0);

        assertThat(translationQueryRepository.touchLastViewedAt(saved.getId(), viewedAt)).isOne();
        assertThat(translationQueryRepository.clearFavorite(saved.getId())).isOne();

        // ★ 這兩支是 @Modifying 的 update，Hibernate 是直接對資料庫下 SQL，
        //   記憶體裡那個 saved 物件不會自己跟著變。不清掉快取就會讀到舊值。
        translationQueryRepository.flush();

        List<TranslationSummaryDto> favorites = translationQueryRepository.findFavorites();

        assertThat(favorites).extracting(TranslationSummaryDto::chineseText)
                .doesNotContain("測試勿刪更新");
    }
```

- [ ] **Step 3: 跑測試確認失敗**

```
.\mvnw.cmd -B test -Dtest=TranslationQueryListRepositoryTest
```

預期：編譯失敗，找不到 `findRecent` 等方法。

- [ ] **Step 4: Repository 加五個方法**

在 `TranslationQueryRepository` 的 `findDtoById` 之後插入，並補 import：

```java
import com.tim.language_project.dto.response.TranslationSummaryDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import java.time.LocalDateTime;
import java.util.List;
```

```java
    /*
     * ★ 建構子投影裡的音檔欄位固定給 null，由 Service 事後補上。
     *
     *   音檔不在這張表，在 audio_asset。JPQL 的建構子投影跨不了表，
     *   所以只能先留一個洞。這與 TranslationService.withSegmentAudio()
     *   遇到的是同一件事。
     *
     * ★ 裸 NULL 有可能被 Hibernate 拒絕（推不出型別）。真的報錯時
     *   改寫成 CAST(NULL AS string)，不要為了閃它去改 DTO 的欄位型別。
     *
     * ★ 條件裡的 lastViewedAt IS NOT NULL 不可省略。
     *   省了的話，2026-08-18 以前的舊資料會全部混進來排在最後面，
     *   而且它們的時間是 null，排序結果依資料庫實作而定 —— 每次打開順序都不一樣。
     *
     * 筆數上限由呼叫端傳 Pageable 決定（PageRequest.of(0, 20)），
     * 不寫死在這裡 —— 上限是產品決策，屬於 Service。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationSummaryDto(
                translationQuery.id,
                translationQuery.chineseText,
                translationQuery.thaiText,
                translationQuery.romanization,
                translationQuery.direction,
                translationQuery.gender,
                NULL,
                CASE WHEN translationQuery.favoritedAt IS NOT NULL THEN TRUE ELSE FALSE END
            )

            FROM TranslationQuery translationQuery

            WHERE translationQuery.lastViewedAt IS NOT NULL

            ORDER BY translationQuery.lastViewedAt DESC
            """)
    List<TranslationSummaryDto> findRecent(Pageable pageable);

    /*
     * 收藏清單。沒有筆數上限 —— 使用者自己按進去的東西不該被系統丟掉。
     * favorited 恆為 true（條件已經保證了），仍照樣算出來，
     * 讓前端不必為兩份清單寫兩種判斷。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationSummaryDto(
                translationQuery.id,
                translationQuery.chineseText,
                translationQuery.thaiText,
                translationQuery.romanization,
                translationQuery.direction,
                translationQuery.gender,
                NULL,
                TRUE
            )

            FROM TranslationQuery translationQuery

            WHERE translationQuery.favoritedAt IS NOT NULL

            ORDER BY translationQuery.favoritedAt DESC
            """)
    List<TranslationSummaryDto> findFavorites();

    /*
     * 更新「最後查看時間」。只在使用者真的按下查詢時呼叫。
     *
     * 用 update 而不是把整個實體讀出來改再存，是因為這件事發生在每一次查詢，
     * 讀一次寫一次等於每次查詢多一趟資料庫往返，而我們只想改一個欄位。
     */
    @Modifying
    @Query("""
            UPDATE TranslationQuery translationQuery
               SET translationQuery.lastViewedAt = :viewedAt
             WHERE translationQuery.id = :id
            """)
    int touchLastViewedAt(@Param("id") Long id, @Param("viewedAt") LocalDateTime viewedAt);

    /*
     * 加入收藏。
     *
     * ★ 條件裡的 favoritedAt IS NULL 是重點：已經收藏過的再按一次，
     *   這句 update 會影響 0 列，收藏時間因此不會被覆寫。
     *   改成先讀出來判斷再寫的話，讀與寫中間會有空隙，而且多一趟往返。
     *
     * @return 受影響的列數。0 代表「本來就已經收藏了」，不是錯誤。
     */
    @Modifying
    @Query("""
            UPDATE TranslationQuery translationQuery
               SET translationQuery.favoritedAt = :favoritedAt
             WHERE translationQuery.id = :id
               AND translationQuery.favoritedAt IS NULL
            """)
    int markFavorite(@Param("id") Long id, @Param("favoritedAt") LocalDateTime favoritedAt);

    /** 取消收藏。把 favoritedAt 設回 null，該列就自動退出收藏清單。 */
    @Modifying
    @Query("""
            UPDATE TranslationQuery translationQuery
               SET translationQuery.favoritedAt = NULL
             WHERE translationQuery.id = :id
            """)
    int clearFavorite(@Param("id") Long id);
```

- [ ] **Step 5: 跑測試確認通過**

```
.\mvnw.cmd -B test -Dtest=TranslationQueryListRepositoryTest
```

預期：五個測試全部 PASS。若 Hibernate 對建構子投影裡的 `NULL` 報錯（訊息類似 `Could not determine type`），把兩處 `NULL,` 改成 `CAST(NULL AS string),` 再跑一次。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/tim/language_project/dto/response/TranslationSummaryDto.java src/main/java/com/tim/language_project/repository/TranslationQueryRepository.java src/test/java/com/tim/language_project/repository/TranslationQueryListRepositoryTest.java
git commit -m "$(cat <<'EOF'
新增最近與收藏清單的查詢

Feat:
- TranslationSummaryDto 作為清單一列的形狀
- findRecent 只取 last_viewed_at 有值的列，依時間新到舊
- findFavorites 只取 favorited_at 有值的列
- markFavorite 以 SQL 條件擋下重複收藏，避免覆寫收藏時間

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3：音檔批次查詢

**Files:**
- Modify: `src/main/java/com/tim/language_project/repository/AudioAssetRepository.java`
- Modify: `src/main/java/com/tim/language_project/service/AudioAssetService.java`
- Test: `src/test/java/com/tim/language_project/service/AudioAssetServiceTest.java`

**Interfaces:**
- Consumes: 既有的 `AudioAssetDto(Long id, String speechText, SpeechLanguageEnum language, String filePath)`
- Produces:
  - `List<AudioAssetDto> AudioAssetRepository.findBySpeechTextInAndLanguage(Collection<String> speechTexts, SpeechLanguageEnum language)`
  - `Map<String, String> AudioAssetService.findExistingAudioUrls(Collection<String> speechTexts, SpeechLanguageEnum language)` —— 鍵是文字、值是網址；查不到的文字不會出現在 Map 裡

- [ ] **Step 1: 寫會失敗的測試**

在 `AudioAssetServiceTest` 類別裡新增（依該檔既有的 mock 命名調整；`audioAssetRepository` 與 `speechClient` 應該已經是 `@Mock` 欄位）：

```java
    /*
     * ═══ 批次查音檔：一次查詢就要拿到全部，不可以一列查一次 ═══════════════
     *
     * ★ 這支防的是 N+1。
     *
     *   收藏清單有一百筆，若每一列各自呼叫 findExistingAudioUrl，
     *   就是一百趟資料庫往返。資料只有十幾筆的時候完全看不出來，
     *   累積之後每次打開收藏都慢一拍，而且沒有任何錯誤訊息。
     *
     *   所以這裡 verify 的不只是「結果對」，還有「只查了一次」。
     */
    @Test
    @DisplayName("批次查音檔應只打一次資料庫並回傳文字對網址的對照")
    void shouldFindExistingAudioUrlsInOneQuery() {
        List<String> speechTexts = List.of("ผมอยากดื่มเหล้าครับ", "ไม่ใส่ผักชีครับ");

        when(audioAssetRepository.findBySpeechTextInAndLanguage(
                speechTexts, SpeechLanguageEnum.TH))
                .thenReturn(List.of(new AudioAssetDto(
                        1L, "ผมอยากดื่มเหล้าครับ", SpeechLanguageEnum.TH, "th/a3f9c2.mp3")));

        Map<String, String> urls = audioAssetService.findExistingAudioUrls(
                speechTexts, SpeechLanguageEnum.TH);

        // 我主張：查到的那句變成可以直接放進 <audio src> 的網址。
        assertThat(urls).containsEntry("ผมอยากดื่มเหล้าครับ", "/audio/th/a3f9c2.mp3");

        // 我主張：沒有音檔的那句「不出現在 Map 裡」，而不是對到一個 null。
        // 呼叫端用 Map.get 拿到 null 就知道是灰色的鍵，不必再處理第二種空值。
        assertThat(urls).doesNotContainKey("ไม่ใส่ผักชีครับ");

        // 我主張：整批只打了一次資料庫。
        verify(audioAssetRepository, times(1))
                .findBySpeechTextInAndLanguage(speechTexts, SpeechLanguageEnum.TH);
    }

    /*
     * 空清單不可以打資料庫 —— 收藏一筆都沒有時，
     * 送出 WHERE speech_text IN () 這種空集合查詢是白跑一趟。
     */
    @Test
    @DisplayName("文字清單為空時不應查詢資料庫")
    void shouldSkipQueryWhenSpeechTextsEmpty() {
        Map<String, String> urls = audioAssetService.findExistingAudioUrls(
                List.of(), SpeechLanguageEnum.TH);

        assertThat(urls).isEmpty();

        verify(audioAssetRepository, never())
                .findBySpeechTextInAndLanguage(any(), any());
    }
```

需要的 import（若該檔還沒有）：

```java
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
```

- [ ] **Step 2: 跑測試確認失敗**

```
.\mvnw.cmd -B test -Dtest=AudioAssetServiceTest
```

預期：編譯失敗，找不到 `findExistingAudioUrls`。

- [ ] **Step 3: Repository 加批次查詢**

在 `AudioAssetRepository` 的 `findBySpeechTextAndLanguage` 之後插入，並補 import `java.util.Collection`、`java.util.List`：

```java
    /*
     * ★ 批次版。清單畫面用這支，不要在迴圈裡呼叫上面那支單筆的。
     *
     *   收藏一百筆、每列各查一次就是一百趟資料庫往返（N+1）。
     *   資料少的時候完全看不出來，這正是它難查的原因。
     *
     * 查不到的文字不會出現在結果裡，呼叫端據此判斷「這一句還沒有音檔」。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.AudioAssetDto(
                audioAsset.id,
                audioAsset.speechText,
                audioAsset.language,
                audioAsset.filePath
            )

            FROM AudioAsset audioAsset

            WHERE audioAsset.speechText IN :speechTexts
              AND audioAsset.language = :language
            """)
    List<AudioAssetDto> findBySpeechTextInAndLanguage(
            @Param("speechTexts") Collection<String> speechTexts,
            @Param("language") SpeechLanguageEnum language);
```

- [ ] **Step 4: Service 加批次方法**

在 `AudioAssetService.findExistingAudioUrl` 之後插入，並補 import `java.util.Collection`、`java.util.Map`、`java.util.stream.Collectors`：

```java
    /**
     * 一次查一整批文字的現成音檔，★絕對不會觸發合成★。
     *
     * 回傳的 Map 以文字為鍵、網址為值；沒有音檔的文字「不會出現在 Map 裡」，
     * 呼叫端用 get 拿到 null 就知道那一列的播放鍵要顯示成灰的。
     *
     * ★ 清單畫面一定要用這支，不可以在迴圈裡呼叫 findExistingAudioUrl ——
     *   那是 N+1，收藏一百筆就是一百趟資料庫往返，而且資料少時看不出來。
     */
    public Map<String, String> findExistingAudioUrls(Collection<String> speechTexts,
                                                     SpeechLanguageEnum language) {
        if (ObjectUtils.isEmpty(speechTexts)) {
            return Map.of();
        }

        return audioAssetRepository.findBySpeechTextInAndLanguage(speechTexts, language)
                .stream()
                .collect(Collectors.toMap(
                        AudioAssetDto::speechText,
                        audioAsset -> toAudioUrl(audioAsset.filePath()),
                        // 同一段文字同一語言在資料庫有唯一鍵，理論上撞不到。
                        // 真的撞到時取先出現的那一個 —— 兩個檔案內容一樣，播哪個都對。
                        (existing, duplicate) -> existing));
    }
```

- [ ] **Step 5: 跑測試確認通過**

```
.\mvnw.cmd -B test -Dtest=AudioAssetServiceTest
```

預期：PASS（含該檔原有的測試）。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/tim/language_project/repository/AudioAssetRepository.java src/main/java/com/tim/language_project/service/AudioAssetService.java src/test/java/com/tim/language_project/service/AudioAssetServiceTest.java
git commit -m "$(cat <<'EOF'
新增音檔的批次查詢

Feat:
- AudioAssetService.findExistingAudioUrls 一次查一整批文字的現成音檔
- 清單畫面改用批次查詢，避免每列各查一次造成的 N+1

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4：QueryListService

**Files:**
- Create: `src/main/java/com/tim/language_project/service/QueryListService.java`
- Test: `src/test/java/com/tim/language_project/service/QueryListServiceTest.java`

**Interfaces:**
- Consumes: Task 2 的五個 Repository 方法、Task 3 的 `findExistingAudioUrls`
- Produces:
  - `List<TranslationSummaryDto> QueryListService.recent()`
  - `List<TranslationSummaryDto> QueryListService.favorites()`
  - `void QueryListService.addFavorite(Long queryId)` —— 查詢不存在時丟 `BusinessException(RESOURCE_NOT_FOUND)`
  - `void QueryListService.removeFavorite(Long queryId)` —— 同上

- [ ] **Step 1: 寫會失敗的測試**

`src/test/java/com/tim/language_project/service/QueryListServiceTest.java`：

```java
package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案在測什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測 QueryListService —— 「最近」與「收藏」兩份清單的組裝，
 *  以及愛心的加入與取消。
 *
 * ── 第 1 步｜你在手機上點了「收藏」分頁 ─────────────────────────────────
 *
 *    前端送出 GET /api/v1/translations/favorites，最後打到 favorites()。
 *
 * ── 第 2 步｜先去資料庫撈出那些列 ───────────────────────────────────────
 *
 *    translationQueryRepository.findFavorites() 回傳的每一列長這樣：
 *
 *        TranslationSummaryDto[
 *            queryId=137, chineseText=幫我叫計程車,
 *            thaiText=ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ,
 *            romanization=chûai rîak tháek-sîi hâi pǒm nòi khráp,
 *            direction=ZH_TO_TH, gender=MALE,
 *            thaiAudioUrl=null,        ← ★ 這裡是空的
 *            favorited=true]
 *
 *    ★ 為什麼 thaiAudioUrl 是空的？
 *      音檔不存在 translation_query，存在另一張表 audio_asset。
 *      JPQL 的建構子投影跨不了表，所以資料庫那一層只能先留一個洞。
 *
 * ── 第 3 步｜把那個洞補起來（本檔最重要的一段）───────────────────────────
 *
 *    把整批的泰文收成一個集合，「一次」去問音檔：
 *
 *        findExistingAudioUrls([ช่วยเรียก..., ไม่ใส่ผักชี...], TH)
 *            → { "ช่วยเรียก...": "/audio/th/a3f9c2.mp3" }
 *
 *    然後逐列從這個 Map 取出網址填回去。
 *
 *    ★ 為什麼不能在迴圈裡一列查一次？
 *      那是 N+1：收藏一百筆就是一百趟資料庫往返。
 *      資料只有十幾筆時完全看不出來 —— 這正是它難查的原因。
 *      所以下面的測試不只驗結果，還 verify「只查了一次」。
 *
 * ── 第 4 步｜你按下某一列的愛心把它取消 ─────────────────────────────────
 *
 *    removeFavorite(137) → clearFavorite 把 favorited_at 設回 null。
 *
 *    ★ 找不到那個 id 時要丟 BusinessException，不可以默默當成成功 ——
 *      前端會把愛心變成已取消的樣子，但資料庫其實什麼都沒發生。
 *
 * ── 什麼東西被換成假的 ──────────────────────────────────────────────────
 *
 *    Repository 與 AudioAssetService 都是 @Mock（假的）。
 *    這支測的是「組裝的邏輯」，不是資料庫本身 ——
 *    資料庫的行為由 TranslationQueryListRepositoryTest 負責。
 */

import com.tim.language_project.dto.response.TranslationSummaryDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.repository.TranslationQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryListServiceTest {

    @Mock
    private TranslationQueryRepository translationQueryRepository;

    @Mock
    private AudioAssetService audioAssetService;

    @InjectMocks
    private QueryListService queryListService;

    /*
     * ═══ 測試一：清單的音檔用一次批次查詢補上 ═══════════════════════════
     */
    @Test
    @DisplayName("收藏清單的音檔應以單次批次查詢補上")
    void shouldFillAudioUrlsWithSingleBatchQuery() {
        when(translationQueryRepository.findFavorites()).thenReturn(List.of(
                summary(137L, "幫我叫計程車", "ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ"),
                summary(138L, "不要放香菜", "ไม่ใส่ผักชีครับ")));

        when(audioAssetService.findExistingAudioUrls(any(), eq(SpeechLanguageEnum.TH)))
                .thenReturn(Map.of("ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ", "/audio/th/a3f9c2.mp3"));

        List<TranslationSummaryDto> favorites = queryListService.favorites();

        // 我主張：有音檔的那一列被補上了網址。
        assertThat(favorites.get(0).thaiAudioUrl()).isEqualTo("/audio/th/a3f9c2.mp3");

        // 我主張：沒有音檔的那一列是 null，而不是整支失敗或空字串 ——
        // 前端看到 null 才會把播放鍵畫成灰的，點下去補生。
        assertThat(favorites.get(1).thaiAudioUrl()).isNull();

        // ★ 我主張：整批只問了一次音檔。這一行就是在防 N+1。
        verify(audioAssetService, times(1))
                .findExistingAudioUrls(any(), eq(SpeechLanguageEnum.TH));
    }

    /*
     * ═══ 測試二：最近清單最多 20 筆 ═════════════════════════════════════
     *
     * 上限寫在 Service（產品決策），不寫在 Repository（資料存取）。
     */
    @Test
    @DisplayName("最近清單應以 20 筆為上限查詢")
    void shouldLimitRecentToTwenty() {
        when(translationQueryRepository.findRecent(any())).thenReturn(List.of());

        queryListService.recent();

        verify(translationQueryRepository).findRecent(PageRequest.of(0, 20));
    }

    /*
     * ═══ 測試三：加入收藏 ═══════════════════════════════════════════════
     *
     * ★ markFavorite 回 0 代表「本來就已經收藏了」，那不是錯誤，
     *   不可以丟例外 —— 使用者連按兩下愛心會看到一個沒有道理的紅字。
     */
    @Test
    @DisplayName("重複加入收藏不應視為錯誤")
    void shouldTreatRepeatedFavoriteAsSuccess() {
        when(translationQueryRepository.existsById(137L)).thenReturn(true);
        when(translationQueryRepository.markFavorite(eq(137L), any())).thenReturn(0);

        queryListService.addFavorite(137L);

        verify(translationQueryRepository).markFavorite(eq(137L), any());
    }

    /*
     * ═══ 測試四：對不存在的 id 收藏要丟錯 ═══════════════════════════════
     *
     * ★ 默默當成成功的話，前端的愛心會變成實心，
     *   但下次打開收藏清單裡什麼都沒有 —— 使用者只會覺得功能壞了。
     */
    @Test
    @DisplayName("查詢不存在時加入收藏應丟出 RESOURCE_NOT_FOUND")
    void shouldRejectFavoriteForUnknownQuery() {
        when(translationQueryRepository.existsById(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> queryListService.addFavorite(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCodeEnum.RESOURCE_NOT_FOUND);
    }

    private TranslationSummaryDto summary(Long queryId, String chineseText, String thaiText) {
        return new TranslationSummaryDto(
                queryId, chineseText, thaiText, "chûai rîak tháek-sîi",
                TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE, null, true);
    }
}
```

★ `hasFieldOrPropertyWithValue("errorCode", ...)` 的欄位名稱要與 `BusinessException` 實際的欄位一致 —— 執行前先打開 `exception/BusinessException.java` 確認（若欄位叫別的名字就改成那個）。

- [ ] **Step 2: 跑測試確認失敗**

```
.\mvnw.cmd -B test -Dtest=QueryListServiceTest
```

預期：編譯失敗，找不到 `QueryListService`。

- [ ] **Step 3: 實作 QueryListService**

`src/main/java/com/tim/language_project/service/QueryListService.java`：

```java
package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  「最近搜尋」與「收藏」兩份清單的組裝，以及愛心的加入與取消。
 *
 *  ★ 這個檔案「絕對不會呼叫 OpenAI」。它只讀資料庫、只改時間欄位。
 *    這也是為什麼對應的五支 API 用 GET / PUT / DELETE 而不是 POST ——
 *    這個專案用動詞區分「會花錢」與「不會花錢」。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你點開「收藏」分頁到清單出現在畫面上
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在手機上點了「收藏」分頁 ─────────────────────────────────
 *
 *        GET /api/v1/translations/favorites
 *              ↓
 *        TranslationController.favorites()
 *              ↓
 *        queryListService.favorites()
 *
 * ── 第 2 步｜先去資料庫撈出被收藏的那些列 ───────────────────────────────
 *
 *        translationQueryRepository.findFavorites()
 *
 *    拿回來的每一列長這樣：
 *
 *        TranslationSummaryDto[
 *            queryId=137, chineseText=幫我叫計程車,
 *            thaiText=ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ,
 *            romanization=chûai rîak tháek-sîi hâi pǒm nòi khráp,
 *            direction=ZH_TO_TH, gender=MALE,
 *            thaiAudioUrl=null,        ← ★ 這裡是空的，第 3 步才補
 *            favorited=true]
 *
 *    ★ 為什麼音檔是空的？
 *      音檔不在 translation_query 這張表，在另一張 audio_asset。
 *      同一段泰文全站只合成一次，所以音檔以「文字內容」為鍵獨立存放 ——
 *      這是這個專案「用越久越省錢」的核心。
 *      代價是查詢時跨不了表，只能先留一個洞。
 *
 * ── 第 3 步｜把音檔的洞補起來（★ 本檔最容易寫錯的地方）──────────────────
 *
 *    先把整批的泰文收成一個集合：
 *
 *        ["ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ", "ไม่ใส่ผักชีครับ"]
 *
 *    「一次」去問音檔：
 *
 *        audioAssetService.findExistingAudioUrls(那個集合, TH)
 *            → { "ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ": "/audio/th/a3f9c2.mp3" }
 *
 *    再逐列把網址填回去。查不到的那一句就維持 null。
 *
 *    ★ 絕對不可以改成「在迴圈裡一列查一次」。
 *      那是 N+1：收藏一百筆就是一百趟資料庫往返。
 *      資料只有十幾筆的時候完全看不出來，累積之後每次打開收藏都慢一拍，
 *      而且不會有任何錯誤訊息 —— 這正是它難查的原因。
 *
 *    ★ 用的是 findExistingAudioUrls（只查不生），不是 resolveAudioUrl。
 *      打開一次收藏清單就把一百句沒音檔的全部合成一遍，是會真的付錢的。
 *
 * ── 第 4 步｜回傳，Controller 轉成 JSON ─────────────────────────────────
 *
 *        [ { "queryId":137, ..., "thaiAudioUrl":"/audio/th/a3f9c2.mp3",
 *            "favorited":true }, ... ]
 *
 *    thaiAudioUrl 為 null 的那一列，前端會把播放鍵畫成灰的，點了才補生。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  另一件事：愛心
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  加入收藏 = 把 favorited_at 設成現在時間；取消 = 設回 null。
 *  ★ favorited_at 為 null 就代表「沒收藏」，所以不需要另一個 boolean 欄位。
 *
 *  ★ 已經收藏過的再按一次，markFavorite 會影響 0 列（SQL 條件擋下來的），
 *    那不是錯誤 —— 收藏時間因此不會被覆寫，清單排序才不會莫名其妙跳動。
 *
 *  ★ 但「查詢根本不存在」要丟 BusinessException。默默當成成功的話，
 *    前端的愛心會變成實心，下次打開收藏卻什麼都沒有。
 *
 *  測試檔：src/test/java/com/tim/language_project/service/QueryListServiceTest.java
 */

import com.tim.language_project.dto.response.TranslationSummaryDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.repository.TranslationQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 最近搜尋與收藏兩份清單的組裝，以及收藏的加入與取消。
 * 這一層只讀資料庫與改時間欄位，不會呼叫任何外部服務。
 */
@Service
@RequiredArgsConstructor
public class QueryListService {

    /**
     * 最近搜尋顯示幾筆。
     *
     * 這是產品決策（「短期回溯」而不是「完整歷史」），所以放在 Service，
     * 不放在 Repository —— 那一層只管怎麼取資料，不管取多少才合理。
     */
    private static final int RECENT_LIMIT = 20;

    private final TranslationQueryRepository translationQueryRepository;

    private final AudioAssetService audioAssetService;

    /** 最近搜尋，去重後最多 20 筆，最後查看的時間新的在前。 */
    public List<TranslationSummaryDto> recent() {
        return withAudioUrls(
                translationQueryRepository.findRecent(PageRequest.of(0, RECENT_LIMIT)));
    }

    /** 收藏清單，加入收藏的時間新的在前，沒有筆數上限。 */
    public List<TranslationSummaryDto> favorites() {
        return withAudioUrls(translationQueryRepository.findFavorites());
    }

    /**
     * 加入收藏。已經收藏過的再呼叫一次不會覆寫收藏時間，也不算錯誤。
     *
     * @throws BusinessException 查詢不存在時
     */
    @Transactional
    public void addFavorite(Long queryId) {
        requireExists(queryId);

        translationQueryRepository.markFavorite(queryId, LocalDateTime.now());
    }

    /**
     * 取消收藏。沒有收藏過的再呼叫一次不算錯誤。
     *
     * @throws BusinessException 查詢不存在時
     */
    @Transactional
    public void removeFavorite(Long queryId) {
        requireExists(queryId);

        translationQueryRepository.clearFavorite(queryId);
    }

    /**
     * 把整批清單缺少的泰文音檔網址一次補上。
     *
     * ★ 一定要批次。在迴圈裡一列查一次是 N+1，收藏一百筆就是一百趟往返，
     *   而且資料少的時候完全看不出來。
     */
    private List<TranslationSummaryDto> withAudioUrls(List<TranslationSummaryDto> summaries) {
        Set<String> thaiTexts = summaries.stream()
                .map(TranslationSummaryDto::thaiText)
                .collect(Collectors.toSet());

        Map<String, String> audioUrls =
                audioAssetService.findExistingAudioUrls(thaiTexts, SpeechLanguageEnum.TH);

        return summaries.stream()
                .map(summary -> new TranslationSummaryDto(
                        summary.queryId(),
                        summary.chineseText(),
                        summary.thaiText(),
                        summary.romanization(),
                        summary.direction(),
                        summary.gender(),
                        // 查不到就是 null，前端據此把播放鍵畫成灰的。
                        audioUrls.get(summary.thaiText()),
                        summary.favorited()))
                .toList();
    }

    /** 確認這筆查詢真的存在，不存在就丟 404，不可以默默當成成功。 */
    private void requireExists(Long queryId) {
        if (!translationQueryRepository.existsById(queryId)) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND);
        }
    }
}
```

- [ ] **Step 4: 跑測試確認通過**

```
.\mvnw.cmd -B test -Dtest=QueryListServiceTest
```

預期：四個測試全部 PASS。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/tim/language_project/service/QueryListService.java src/test/java/com/tim/language_project/service/QueryListServiceTest.java
git commit -m "$(cat <<'EOF'
新增最近與收藏清單的服務

Feat:
- QueryListService 組裝兩份清單並批次補上音檔網址
- 最近清單上限 20 筆，收藏清單無上限
- 重複收藏不視為錯誤，查詢不存在則丟 RESOURCE_NOT_FOUND

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5：TranslationService 更新查看時間與還原查詢

**Files:**
- Modify: `src/main/java/com/tim/language_project/service/TranslationService.java`
- Test: `src/test/java/com/tim/language_project/service/TranslationServiceTest.java`

**Interfaces:**
- Consumes: Task 2 的 `touchLastViewedAt`
- Produces: `TranslationResponseDto TranslationService.resolveById(Long queryId)` —— `fromCache` 固定為 `true`；查詢不存在時丟 `BusinessException(RESOURCE_NOT_FOUND)`

- [ ] **Step 1: 寫會失敗的測試**

在 `TranslationServiceTest` 類別裡新增三個測試（mock 欄位沿用該檔既有的命名）：

```java
    /*
     * ═══ 最近搜尋：兩條路都要更新最後查看時間 ═══════════════════════════
     *
     * ★ 只更新其中一條的話，常查的句子反而永遠停在清單底部 ——
     *   因為常查的句子必定走快取那條路。
     */
    @Test
    @DisplayName("快取命中時也要更新最後查看時間")
    void shouldTouchLastViewedAtOnCacheHit() {
        when(translationQueryRepository.findByKey(
                "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .thenReturn(Optional.of(new TranslationQueryDto(
                        137L, "我想喝酒", TranslationDirectionEnum.ZH_TO_TH,
                        SpeakerGenderEnum.MALE, "我想喝酒", "ผมอยากดื่มเหล้าครับ",
                        "pǒm yàak dùuem lâo khráp", false)));

        translationService.translate("我想喝酒", SpeakerGenderEnum.MALE);

        verify(translationQueryRepository).touchLastViewedAt(eq(137L), any());
    }

    /*
     * ═══ 還原一筆查詢：★ 絕對不可以花到錢 ═══════════════════════════════
     *
     * 這支防的是把「還原」實作成「重新查一次」。
     *
     * 重查會經過快取鑰匙（原文＋方向＋性別）的比對 ——
     * 那一筆是男生版而使用者當下切在女生的話，就是一筆全新的查詢，
     * 真的呼叫 OpenAI、真的付錢，而畫面上看起來完全正常。
     */
    @Test
    @DisplayName("還原查詢不應呼叫翻譯服務或語音服務")
    void shouldResolveByIdWithoutCallingAnyPaidService() {
        when(translationQueryRepository.findDtoById(137L))
                .thenReturn(Optional.of(new TranslationQueryDto(
                        137L, "我想喝酒", TranslationDirectionEnum.ZH_TO_TH,
                        SpeakerGenderEnum.MALE, "我想喝酒", "ผมอยากดื่มเหล้าครับ",
                        "pǒm yàak dùuem lâo khráp", false)));

        when(audioAssetService.findExistingAudioUrl("ผมอยากดื่มเหล้าครับ",
                SpeechLanguageEnum.TH))
                .thenReturn(Optional.of("/audio/th/a3f9c2.mp3"));

        TranslationResponseDto response = translationService.resolveById(137L);

        assertThat(response.queryId()).isEqualTo(137L);
        assertThat(response.thaiText()).isEqualTo("ผมอยากดื่มเหล้าครับ");
        assertThat(response.thaiAudioUrl()).isEqualTo("/audio/th/a3f9c2.mp3");

        // 我主張：這次沒有產生任何新東西，所以 fromCache 是 true。
        assertThat(response.fromCache()).isTrue();

        // ★ 我主張：一毛錢都沒花。
        verifyNoInteractions(translationClient);

        // ★ 我主張：也沒有偷偷合成音檔（那同樣要付錢）。
        verify(audioAssetService, never()).resolveAudioUrl(anyString(), any());
    }

    /*
     * ═══ 還原不存在的查詢 ═══════════════════════════════════════════════
     */
    @Test
    @DisplayName("還原不存在的查詢應丟出 RESOURCE_NOT_FOUND")
    void shouldRejectResolveByIdForUnknownQuery() {
        when(translationQueryRepository.findDtoById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> translationService.resolveById(999L))
                .isInstanceOf(BusinessException.class);
    }
```

需要的 import（若該檔還沒有）：

```java
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
```

★ 上面用到的 mock 欄位名（`translationQueryRepository`、`audioAssetService`、`translationClient`、`translationService`）以該測試檔實際的命名為準，不一致就改成該檔的名字。`TranslationQueryDto` 的建構子參數順序以 `dto/response/TranslationQueryDto.java` 為準。

- [ ] **Step 2: 跑測試確認失敗**

```
.\mvnw.cmd -B test -Dtest=TranslationServiceTest
```

預期：編譯失敗，找不到 `resolveById`。

- [ ] **Step 3: `translate()` 更新最後查看時間**

在 `TranslationService.translate()` 裡，快取命中與新建立兩條路都要呼叫。找到快取命中的分支（回傳 `withCachedAudio(...)` 那一段）與新建立的分支（組出 `TranslationResponseDto` 之後），各自在回傳之前加上：

```java
        // ★ 兩條路都要更新，不可以只加在其中一條。
        //   只加在「新建立」那條的話，常查的句子反而永遠停在最近清單的底部 ——
        //   因為常查的句子必定走快取那一條。
        translationQueryRepository.touchLastViewedAt(queryId, LocalDateTime.now());
```

其中 `queryId` 快取命中時來自 `cached.id()`，新建立時來自寫入後拿到的 id。並在檔頭流程註解的對應步驟補一句說明：

```java
 *    ★ 這裡順便把 last_viewed_at 更新成現在時間 —— 那是「最近搜尋」的排序依據。
 *      不能用 created_at 代替：那是第一次查的時間，快取命中時整列不動。
```

補 import `java.time.LocalDateTime`（若尚未有）。

- [ ] **Step 4: 新增 `resolveById()`**

在 `resolveVariants` 之後插入：

```java
    /**
     * 用 id 把一筆查詢原封不動還原成完整結果，供「最近」與「收藏」清單點擊時使用。
     *
     * ★ 這個方法保證不花錢：只讀 translation_query，音檔只用
     *   findExistingAudioUrl（只查不生）。所以對應的 API 才敢用 GET。
     *
     * ★ 千萬不要改成「拿 sourceText 重新呼叫 translate()」。
     *   translate() 會經過快取鑰匙（原文＋方向＋性別）的比對，
     *   那一筆是男生版而使用者當下切在女生時，就是一筆全新的查詢 ——
     *   真的呼叫 OpenAI、真的付錢，而畫面上看起來完全正常。
     *
     * ★ 這裡刻意「不」更新 last_viewed_at。更新的話，
     *   翻一輪收藏就會把最近清單洗成另一個順序，清單在眼皮底下跳動。
     *
     * @param queryId 清單那一列的 queryId
     * @throws BusinessException 查詢不存在時
     */
    public TranslationResponseDto resolveById(Long queryId) {
        TranslationQueryDto cached = translationQueryRepository.findDtoById(queryId)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND));

        return withCachedAudio(cached);
    }
```

★ 若既有的 `withCachedAudio(...)` 私有方法簽章不同（例如需要多個參數），依實際簽章調整；它已經在做「只查不生地補上中泰音檔並把 `fromCache` 設為 `true`」這件事，不要另外寫一份。

- [ ] **Step 5: 跑測試確認通過**

```
.\mvnw.cmd -B test -Dtest=TranslationServiceTest
```

預期：新舊測試全部 PASS。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/tim/language_project/service/TranslationService.java src/test/java/com/tim/language_project/service/TranslationServiceTest.java
git commit -m "$(cat <<'EOF'
查詢時記錄最後查看時間並支援以 id 還原

Feat:
- translate 於快取命中與新建立兩條路都更新 last_viewed_at
- 新增 resolveById，只讀資料庫與現成音檔，保證不呼叫付費服務

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6：Controller 五個端點

**Files:**
- Modify: `src/main/java/com/tim/language_project/controller/TranslationController.java`
- Test: `src/test/java/com/tim/language_project/controller/TranslationControllerTest.java`

**Interfaces:**
- Consumes: Task 4 的 `QueryListService`、Task 5 的 `resolveById`
- Produces: 五個 HTTP 端點（見下）

- [ ] **Step 1: 寫會失敗的測試**

在 `TranslationControllerTest` 新增，並補 `@MockitoBean private QueryListService queryListService;` 欄位與 import：

```java
import com.tim.language_project.dto.response.TranslationSummaryDto;
import com.tim.language_project.service.QueryListService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
```

```java
    /*
     * ═══ 最近清單 ═══════════════════════════════════════════════════════
     *
     * ★ 這支同時在防一個很容易忽略的網址衝突：
     *
     *     GET /api/v1/translations/recent
     *     GET /api/v1/translations/{id}
     *
     *   兩條路徑的形狀一模一樣。Spring 會優先比對「寫死的字」而不是變數，
     *   所以 /recent 會正確地走到 recent()，不會被當成 id=recent 而回 400。
     *   這個測試就是在確認那件事真的成立 —— 哪天換了路徑比對的實作，
     *   壞掉的方式會是「最近分頁突然變成錯誤訊息」。
     */
    @Test
    @DisplayName("最近清單應回傳 200 與清單內容")
    void shouldReturnRecentList() throws Exception {
        when(queryListService.recent()).thenReturn(List.of(new TranslationSummaryDto(
                137L, "幫我叫計程車", "ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ",
                "chûai rîak tháek-sîi hâi pǒm nòi khráp",
                TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                "/audio/th/a3f9c2.mp3", true)));

        mockMvc.perform(get("/api/v1/translations/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].queryId").value(137))
                .andExpect(jsonPath("$[0].thaiText").value("ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ"))
                // 前端靠這個決定愛心是實心還是空心
                .andExpect(jsonPath("$[0].favorited").value(true))
                // 前端靠 gender 顯示那一列右上角的「男／女」標籤
                .andExpect(jsonPath("$[0].gender").value("MALE"));
    }

    /*
     * ═══ 收藏清單為空時回空陣列，不是 404 ══════════════════════════════
     *
     * ★ 回 404 的話前端會顯示錯誤訊息，但「一筆收藏都沒有」是完全正常的狀態，
     *   應該顯示的是「在查詢結果按愛心就會收進這裡」那句引導。
     */
    @Test
    @DisplayName("收藏清單為空應回傳 200 與空陣列")
    void shouldReturnEmptyFavorites() throws Exception {
        when(queryListService.favorites()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/translations/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /*
     * ═══ 加入與取消收藏都回 204 ════════════════════════════════════════
     *
     * 不回內容，因為前端已經知道自己按了什麼，回傳整列只是多餘的傳輸。
     */
    @Test
    @DisplayName("加入與取消收藏應回傳 204")
    void shouldToggleFavorite() throws Exception {
        mockMvc.perform(put("/api/v1/translations/137/favorite"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/translations/137/favorite"))
                .andExpect(status().isNoContent());

        verify(queryListService).addFavorite(137L);
        verify(queryListService).removeFavorite(137L);
    }

    /*
     * ═══ 還原一筆查詢 ═══════════════════════════════════════════════════
     */
    @Test
    @DisplayName("以 id 還原查詢應回傳 200 與完整結果")
    void shouldRestoreTranslationById() throws Exception {
        when(translationService.resolveById(137L)).thenReturn(new TranslationResponseDto(
                137L, "我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                "我想喝酒", "ผมอยากดื่มเหล้าครับ", "pǒm yàak dùuem lâo khráp",
                "/audio/th/a3f9c2.mp3", null, true, false));

        mockMvc.perform(get("/api/v1/translations/137"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").value(137))
                .andExpect(jsonPath("$.thaiText").value("ผมอยากดื่มเหล้าครับ"))
                // 還原不會產生任何新東西，所以一定是 true
                .andExpect(jsonPath("$.fromCache").value(true));
    }
```

★ `TranslationResponseDto` 的建構子參數順序以該檔既有測試（`shouldReturnTranslation`）為準。

- [ ] **Step 2: 跑測試確認失敗**

```
.\mvnw.cmd -B test -Dtest=TranslationControllerTest
```

預期：`QueryListService` 找不到，或請求回 405/404。

- [ ] **Step 3: Controller 加五個端點**

在 `TranslationController` 加入欄位與方法，並補 import：

```java
import com.tim.language_project.dto.response.TranslationSummaryDto;
import com.tim.language_project.service.QueryListService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
```

```java
    private final QueryListService queryListService;

    /*
     * ★ 底下五支刻意「不用」POST，與上面三支形成對比：
     *
     *     POST（上面）→ 可能呼叫 OpenAI，可能花錢
     *     GET / PUT / DELETE（下面）→ 只讀資料庫或只改一個時間欄位，不可能花錢
     *
     *   用動詞把兩者分開，看網址就知道哪些請求有成本。
     */

    /** 最近搜尋，去重後最多 20 筆。沒有紀錄時回空陣列，不是 404。 */
    @GetMapping("/recent")
    public ResponseEntity<List<TranslationSummaryDto>> recent() {
        return ResponseEntity.ok(queryListService.recent());
    }

    /** 收藏清單，加入收藏的時間新的在前。沒有收藏時回空陣列，不是 404。 */
    @GetMapping("/favorites")
    public ResponseEntity<List<TranslationSummaryDto>> favorites() {
        return ResponseEntity.ok(queryListService.favorites());
    }

    /**
     * 加入收藏。
     *
     * ★ 用 PUT 不用 POST：PUT 的語意是「把它設成收藏狀態」，
     *   重複呼叫結果一致，不會產生第二筆，連按兩下愛心也不會出錯。
     */
    @PutMapping("/{queryId}/favorite")
    public ResponseEntity<Void> addFavorite(@PathVariable Long queryId) {
        queryListService.addFavorite(queryId);

        return ResponseEntity.noContent().build();
    }

    /** 取消收藏。 */
    @DeleteMapping("/{queryId}/favorite")
    public ResponseEntity<Void> removeFavorite(@PathVariable Long queryId) {
        queryListService.removeFavorite(queryId);

        return ResponseEntity.noContent().build();
    }

    /**
     * 用 id 還原一筆查詢的完整結果，清單點擊時使用。
     *
     * ★ 這支敢用 GET，是因為它保證不呼叫 OpenAI、不合成音檔。
     *   GET 在規範上代表只讀不寫，瀏覽器與中間的快取都會依這個前提行事 ——
     *   會花錢的東西放在 GET 底下，重新整理或預先載入都可能默默多花一次錢。
     *
     * ★ 這個網址與上面的 /recent、/favorites 形狀相同。
     *   Spring 比對路徑時「寫死的字」優先於變數，所以 /recent 會走到 recent()，
     *   不會被當成 queryId=recent 而回 400。
     */
    @GetMapping("/{queryId}")
    public ResponseEntity<TranslationResponseDto> restore(@PathVariable Long queryId) {
        return ResponseEntity.ok(translationService.resolveById(queryId));
    }
```

- [ ] **Step 4: 跑測試確認通過**

```
.\mvnw.cmd -B test -Dtest=TranslationControllerTest
```

預期：新舊測試全部 PASS。

- [ ] **Step 5: 跑一次完整後端測試**

```
.\mvnw.cmd -B test
```

預期：全綠。若出現 `Unresolved compilation problem`，先 `.\mvnw.cmd -B clean test`（IDE 殘留的舊 class 是常見元兇）。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/tim/language_project/controller/TranslationController.java src/test/java/com/tim/language_project/controller/TranslationControllerTest.java
git commit -m "$(cat <<'EOF'
新增最近搜尋與收藏的端點

Feat:
- GET /recent 與 GET /favorites 回傳清單，無資料時回空陣列
- PUT/DELETE /{queryId}/favorite 切換收藏，回 204
- GET /{queryId} 以 id 還原查詢，保證不呼叫付費服務

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7：前端型別與 API 呼叫

**Files:**
- Modify: `frontend/src/app/models/translation.ts`
- Modify: `frontend/src/app/services/translation-service.ts`

**Interfaces:**
- Consumes: Task 6 的五個端點
- Produces:
  - `interface TranslationSummary { queryId: number; chineseText: string; thaiText: string; romanization: string; direction: TranslationDirection; gender: SpeakerGender | null; thaiAudioUrl: string | null; favorited: boolean; }`
  - `TranslationService.recent(): Observable<TranslationSummary[]>`
  - `TranslationService.favorites(): Observable<TranslationSummary[]>`
  - `TranslationService.addFavorite(queryId: number): Observable<void>`
  - `TranslationService.removeFavorite(queryId: number): Observable<void>`
  - `TranslationService.restore(queryId: number): Observable<TranslationResponse>`

- [ ] **Step 1: 加型別**

在 `models/translation.ts` 的 `AudioResponse` 之前插入：

```typescript
/**
 * 對應後端 TranslationSummaryDto，「最近」與「收藏」清單裡的一列。
 *
 * ★ 這不是 TranslationResponse 的簡化版，是另一個東西。
 *   清單沒有 fromCache 與 isWord —— 那兩個欄位在清單的情境下沒有意義。
 *
 * ★ gender 可以是 null，代表那一筆是泰翻中（泰翻中沒有性別概念）。
 *   前端要據此顯示「泰→中」而不是「男」或「女」。
 */
export interface TranslationSummary {
  queryId: number;
  chineseText: string;
  thaiText: string;
  romanization: string;
  direction: TranslationDirection;
  gender: SpeakerGender | null;
  /** null 代表音檔還沒產生，顯示成灰色的播放鍵，點擊才會產生。 */
  thaiAudioUrl: string | null;
  favorited: boolean;
}
```

- [ ] **Step 2: 加五個 API 方法**

在 `TranslationService` 類別最後插入，並在 import 補上 `TranslationSummary`：

```typescript
  /*
   * ── ★ 底下五支「不會花錢」，所以動詞跟上面三支不一樣 ──────────────────
   *
   *  上面的 translate / synthesize / segments / variants 都是 POST，
   *  理由是它們可能呼叫 OpenAI。
   *
   *  底下這五支只讀資料庫、或只改一個時間欄位，不可能花錢，
   *  所以用 GET / PUT / DELETE。看網址就知道哪些請求有成本。
   */

  /** 最近搜尋，去重後最多 20 筆。沒有紀錄時回空陣列，那是正常結果不是錯誤。 */
  recent(): Observable<TranslationSummary[]> {
    return this.http.get<TranslationSummary[]>('/api/v1/translations/recent');
  }

  /** 收藏清單，加入收藏的時間新的在前。 */
  favorites(): Observable<TranslationSummary[]> {
    return this.http.get<TranslationSummary[]>('/api/v1/translations/favorites');
  }

  /**
   * 加入收藏。用 PUT 是因為它的語意是「把它設成收藏狀態」——
   * 連按兩下愛心不會產生第二筆，也不會出錯。
   */
  addFavorite(queryId: number): Observable<void> {
    return this.http.put<void>(`/api/v1/translations/${queryId}/favorite`, null);
  }

  /** 取消收藏。 */
  removeFavorite(queryId: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/translations/${queryId}/favorite`);
  }

  /**
   * 用 id 還原一筆查詢的完整結果，點清單的一列時用這支。
   *
   * ★ 千萬不要改成「把文字填回輸入框再呼叫 translate()」。
   *   translate 會經過快取鑰匙（原文＋方向＋性別）的比對，
   *   那一筆是男生版而你當下切在女生的話，就是一筆全新的查詢 ——
   *   真的呼叫 OpenAI、真的付錢，而畫面上看起來完全正常。
   */
  restore(queryId: number): Observable<TranslationResponse> {
    return this.http.get<TranslationResponse>(`/api/v1/translations/${queryId}`);
  }
```

- [ ] **Step 3: 確認編譯通過**

```bash
cd frontend
npm run build
```

預期：BUILD 成功。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/models/translation.ts frontend/src/app/services/translation-service.ts
git commit -m "$(cat <<'EOF'
前端新增最近與收藏的型別與 API 呼叫

Feat:
- TranslationSummary 對應後端的清單 DTO
- TranslationService 新增 recent/favorites/addFavorite/removeFavorite/restore

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8：把播放器抽成共用服務

**Files:**
- Create: `frontend/src/app/services/audio-player.ts`
- Modify: `frontend/src/app/translation/translation.ts`
- Modify: `frontend/src/app/translation/translation.html`

**Interfaces:**
- Consumes: 無
- Produces: `AudioPlayerService.play(audioUrl: string | null | undefined): void`

**為什麼要做這一步：** 清單元件也要能播放。若它自己再放一個 `<audio>` 與一套放大器，同一頁上會有兩個 `AudioContext`，音量增益與 iOS 的喚醒行為都得維護兩份。把播放搬進服務，兩個元件共用同一條放大鏈。這是本計畫唯一一處對 `translation.ts` 的結構調整。

- [ ] **Step 1: 建立服務**

`frontend/src/app/services/audio-player.ts`：

```typescript
/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  全站唯一一個真的會發出聲音的地方。
 *
 *  查詢結果、逐詞拆解、各種說法、最近清單、收藏清單 —— 五個地方都有播放鍵，
 *  全部經過這裡，共用同一個 <audio> 與同一條放大鏈。
 *
 * ── 流程：你在收藏清單點下某一列的 ▶ ────────────────────────────────────
 *
 *  第 1 步｜QueryList 元件呼叫 audioPlayer.play('/audio/th/a3f9c2.mp3')
 *
 *  第 2 步｜第一次播放時建立放大器
 *
 *      tts-1 產生的音檔本身偏小聲，手機外放在路邊幾乎聽不到。
 *      所以把 <audio> 的聲音改道經過一個 GainNode 放大，再送到喇叭。
 *
 *      ★ createMediaElementSource 對「同一個元素」只能接一次，
 *        接第二次會直接丟例外。所以這裡用 audioContext 存不存在來擋，
 *        而且 <audio> 元素是這個服務自己持有的、不會被畫面拆掉重建 ——
 *        這正是把它從元件搬到服務的原因之一。
 *
 *  第 3 步｜換檔案、歸零、播放
 *
 *      同一段重播不重新載入（src 沒變就不動它），只把 currentTime 歸零。
 *
 * ── ★ 為什麼 <audio> 不放在畫面上 ───────────────────────────────────────
 *
 *  它不需要被看到（沒有 controls），而放在某個元件的樣板裡，
 *  那個元件被 @if 拆掉時元素就跟著消失，放大器會接到一個不存在的東西。
 *  用 new Audio() 自己建一個，它的生命週期就跟整個 App 一樣長。
 */

import { Service } from '@angular/core';

/**
 * 音量放大倍率。
 * 1 是原始音量；tts-1 的輸出偏小聲，手機外放在路邊幾乎聽不到。
 */
const AUDIO_GAIN = 2.5;

/**
 * 全站共用的播放器。
 * @Service() 等同於 @Injectable({ providedIn: 'root' })，整個應用程式共用同一個實例。
 */
@Service()
export class AudioPlayerService {

  /**
   * 自己持有的 <audio>，不放進任何樣板。
   * 放在樣板裡的話，元件被 @if 拆掉時放大器會接到不存在的元素。
   */
  private readonly element = new Audio();

  private audioContext?: AudioContext;

  /** 播放一段音檔。網址是空的就什麼都不做（那代表音檔還沒產生）。 */
  play(audioUrl: string | null | undefined): void {
    if (!audioUrl) {
      return;
    }

    this.connectAmplifier();

    // 換了一段才重新載入，同一段重播不必再抓一次檔案。
    if (!this.element.src.endsWith(audioUrl)) {
      this.element.src = audioUrl;
    }

    this.element.currentTime = 0;
    void this.element.play();
  }

  /**
   * 把聲音改道經過放大器再送到喇叭。
   * ★ 只做一次 —— 同一個元素重複接 createMediaElementSource 會丟例外。
   */
  private connectAmplifier(): void {
    if (this.audioContext) {
      // 分頁切走再切回來時瀏覽器會把 AudioContext 暫停，這裡叫醒它。
      void this.audioContext.resume();
      return;
    }

    const context = new AudioContext();
    const gainNode = context.createGain();
    gainNode.gain.value = AUDIO_GAIN;

    context.createMediaElementSource(this.element).connect(gainNode);
    gainNode.connect(context.destination);

    this.audioContext = context;
  }
}
```

- [ ] **Step 2: `translation.ts` 改用服務**

- 刪除 `private readonly audioPlayer = viewChild<ElementRef<HTMLAudioElement>>('audioPlayer');`
- 刪除 `private audioContext?: AudioContext;`
- 刪除 `private playAudio(...)` 與 `private connectAmplifier(...)` 兩個方法
- 刪除檔案上方的 `AUDIO_GAIN` 常數（已搬進服務）
- 加入 `private readonly audioPlayer = inject(AudioPlayerService);`
- 把所有 `this.playAudio(x)` 改成 `this.audioPlayer.play(x)`（共四處：`playSentence` 兩處、`playOrSynthesize` 兩處）
- import 移除不再使用的 `ElementRef`、`viewChild`，加入 `AudioPlayerService`
- 檔頭流程註解裡描述「第 6 步：Web Audio 放大器」的段落，改成指向 `services/audio-player.ts`，並保留「同一個元素只能接一次」那段警告

- [ ] **Step 3: `translation.html` 移除 `<audio>`**

刪掉檔案最後的：

```html
  <audio #audioPlayer hidden></audio>
```

連同它上方那段註解一起刪除（該註解描述的行為已經搬到服務裡）。

- [ ] **Step 4: 建置並手動確認聲音還在**

```bash
cd frontend
npm run build
```

啟動後端與前端，查一句話並按「播放發音」，確認：聲音出得來、音量與改動前一樣。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/services/audio-player.ts frontend/src/app/translation/translation.ts frontend/src/app/translation/translation.html
git commit -m "$(cat <<'EOF'
播放器抽成全站共用的服務

Improve:
- 新增 AudioPlayerService，自行持有 audio 元素與放大器
- Translation 改用該服務播放，清單元件之後可共用同一條放大鏈
- 避免同一頁出現兩個 AudioContext

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9：清單元件

**Files:**
- Create: `frontend/src/app/translation/query-list/query-list.ts`
- Create: `frontend/src/app/translation/query-list/query-list.html`
- Create: `frontend/src/app/translation/query-list/query-list.css`

**Interfaces:**
- Consumes: Task 7 的 API 方法與 `TranslationSummary`、Task 8 的 `AudioPlayerService`
- Produces:
  - 元件 selector `app-query-list`
  - 輸入 `mode: 'recent' | 'favorite'`（`input.required<...>()`）
  - 輸出 `restore = output<TranslationSummary>()` —— 使用者點了整列，帶出整列資料（App 需要 `queryId` 與 `favorited` 兩個值）

- [ ] **Step 1: 元件程式**

`frontend/src/app/translation/query-list/query-list.ts`：

```typescript
/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  「最近」與「收藏」兩個分頁的內容。★ 兩者共用這一個元件。
 *
 *  它們的版面只差三個地方：打哪支 API、愛心是實心還空心、有沒有筆數上限。
 *  寫成兩個元件等於維護兩份幾乎一樣的 HTML 與 CSS，
 *  改一邊忘了改另一邊是遲早的事。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你點開「收藏」分頁到聽見聲音
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜App 把這個元件放上畫面，並告訴它是哪一種 ────────────────────
 *
 *        <app-query-list mode="favorite" (restore)="..." />
 *
 *    mode 是「輸入」——由外面決定、元件自己不改。
 *
 * ── 第 2 步｜元件一出現就去要資料 ───────────────────────────────────────
 *
 *        translationService.favorites()   （mode 是 recent 時改打 recent()）
 *              ↓
 *        [ { queryId:137, chineseText:"幫我叫計程車",
 *            thaiText:"ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ",
 *            romanization:"chûai rîak tháek-sîi hâi pǒm nòi khráp",
 *            direction:"ZH_TO_TH", gender:"MALE",
 *            thaiAudioUrl:"/audio/th/a3f9c2.mp3", favorited:true }, ... ]
 *
 *    ★ 空陣列不是錯誤。一筆收藏都沒有是完全正常的狀態，
 *      要顯示引導文字，不是紅色錯誤。
 *
 * ── 第 3 步｜你點某一列的 ▶ ─────────────────────────────────────────────
 *
 *    thaiAudioUrl 有值 → 直接播
 *    thaiAudioUrl 是 null → 先打 POST /api/v1/audio 補合成，拿到網址再播
 *
 *    ★ 為什麼會沒有音檔？查詢時本來就會自動合成，所以這種情況很少見 ——
 *      只有那次合成失敗的才會是 null。灰鍵點一下就補回來了。
 *
 * ── 第 4 步｜你點某一列的 ♥ ─────────────────────────────────────────────
 *
 *    最近分頁（空心）→ PUT，變成實心
 *    收藏分頁（實心）→ DELETE，那一列從畫面上消失
 *
 *    ★ 失敗時愛心要退回原本的樣子。停在「看起來成功了」的話，
 *      你會以為收藏好了，下次打開收藏卻找不到。
 *
 * ── 第 5 步｜你點的是整列（不是 ▶ 也不是 ♥）────────────────────────────
 *
 *    發出 restore 事件把 queryId 交給 App，由 App 切到「查詢」分頁並還原。
 *
 *    ★ 這個元件自己不做還原 —— 還原的結果要顯示在另一個元件（Translation）
 *      裡面，所以它只負責「說一聲」，怎麼處理是外面的事。
 *
 * ── ★ 這個元件絕對不會做的事 ────────────────────────────────────────────
 *
 *    改動使用者的性別設定。
 *
 *    清單裡的一列可能是男生版而你當下切在女生。看起來「順手切過去」比較一致，
 *    但那會默默改掉一個持久設定 —— 你下一句自己打的字就會用錯的性別去查，
 *    而那是一筆真的會呼叫 OpenAI、真的花錢的新查詢。
 *
 *    所以這裡只在每一列標出它自己的性別，設定一動也不動。
 */

import { Component, inject, input, output, signal } from '@angular/core';
import { AudioPlayerService } from '../../services/audio-player';
import { TranslationService } from '../../services/translation-service';
import { TranslationSummary } from '../../models/translation';

/** 這個清單是哪一種。決定打哪支 API、愛心的樣子，以及空清單時說什麼。 */
export type QueryListMode = 'recent' | 'favorite';

@Component({
  selector: 'app-query-list',
  imports: [],
  templateUrl: './query-list.html',
  styleUrl: './query-list.css',
})
export class QueryList {

  private readonly translationService = inject(TranslationService);

  private readonly audioPlayer = inject(AudioPlayerService);

  /** 由外面指定這是「最近」還是「收藏」。元件自己不會改它。 */
  readonly mode = input.required<QueryListMode>();

  /**
   * 使用者點了整列，把整列交出去，由 App 切分頁並還原。
   * ★ 帶整列而不是只帶 queryId：App 還需要 favorited，
   *   才能讓還原後的結果區愛心一開始就是對的樣子。
   */
  readonly restore = output<TranslationSummary>();

  /** 清單資料。null 代表還在載入，空陣列代表「載完了，但是沒有東西」。 */
  protected readonly items = signal<TranslationSummary[] | null>(null);

  protected readonly failed = signal(false);

  /** 正在合成中的文字，用來把那一顆播放鍵顯示成載入中。 */
  protected readonly synthesizing = signal<ReadonlySet<string>>(new Set());

  /** 正在切換收藏中的 queryId，避免連點兩下送出兩個請求。 */
  protected readonly togglingFavorite = signal<ReadonlySet<number>>(new Set());

  ngOnInit(): void {
    this.load();
  }

  /** 去要清單資料。切到這個分頁時呼叫一次。 */
  protected load(): void {
    this.failed.set(false);
    this.items.set(null);

    const request = this.mode() === 'recent'
      ? this.translationService.recent()
      : this.translationService.favorites();

    request.subscribe({
      next: (items) => this.items.set(items),
      error: () => {
        this.failed.set(true);
        this.items.set([]);
      },
    });
  }

  /** 這一列右上角要顯示的標籤。gender 是 null 代表泰翻中，沒有性別概念。 */
  protected genderLabel(item: TranslationSummary): string {
    if (item.gender === 'MALE') {
      return '男';
    }

    if (item.gender === 'FEMALE') {
      return '女';
    }

    return '泰→中';
  }

  protected isSynthesizing(speechText: string): boolean {
    return this.synthesizing().has(speechText);
  }

  /**
   * 點下某一列的播放鍵。
   *
   *   已經有音檔 → 直接播
   *   還沒有音檔 → 先跟後端要，拿到後播放並記在畫面上，下次點就是亮的
   */
  protected play(item: TranslationSummary): void {
    if (item.thaiAudioUrl) {
      this.audioPlayer.play(item.thaiAudioUrl);
      return;
    }

    if (this.isSynthesizing(item.thaiText)) {
      return;
    }

    this.markSynthesizing(item.thaiText, true);

    this.translationService.synthesize(item.thaiText, 'TH').subscribe({
      next: (response) => {
        this.markSynthesizing(item.thaiText, false);
        this.replaceItem({ ...item, thaiAudioUrl: response.audioUrl });
        this.audioPlayer.play(response.audioUrl);
      },
      error: () => {
        // 失敗就讓那顆鍵回到灰色，可以再點一次重試。
        this.markSynthesizing(item.thaiText, false);
      },
    });
  }

  /**
   * 點下某一列的愛心。
   *
   * ★ 失敗時什麼都不改 —— 愛心停在「看起來成功了」的樣子，
   *   使用者會以為收藏好了，下次打開收藏卻找不到。
   */
  protected toggleFavorite(item: TranslationSummary): void {
    if (this.togglingFavorite().has(item.queryId)) {
      return;
    }

    this.markToggling(item.queryId, true);

    const request = item.favorited
      ? this.translationService.removeFavorite(item.queryId)
      : this.translationService.addFavorite(item.queryId);

    request.subscribe({
      next: () => {
        this.markToggling(item.queryId, false);

        // 收藏分頁取消收藏 → 那一列直接消失；最近分頁只是換愛心的樣子。
        if (this.mode() === 'favorite' && item.favorited) {
          this.items.set((this.items() ?? [])
            .filter((current) => current.queryId !== item.queryId));
          return;
        }

        this.replaceItem({ ...item, favorited: !item.favorited });
      },
      error: () => this.markToggling(item.queryId, false),
    });
  }

  /** 點整列：把那一列交給外面，由 App 切到查詢分頁並還原。 */
  protected select(item: TranslationSummary): void {
    this.restore.emit(item);
  }

  /**
   * 換掉清單裡的某一列。
   * ★ 訊號要換一個新陣列才會通知畫面重畫，直接改陣列裡那個物件的欄位沒有用
   *   （zoneless 模式）。
   */
  private replaceItem(updated: TranslationSummary): void {
    this.items.set((this.items() ?? [])
      .map((current) => current.queryId === updated.queryId ? updated : current));
  }

  private markSynthesizing(speechText: string, running: boolean): void {
    const next = new Set(this.synthesizing());

    if (running) {
      next.add(speechText);
    } else {
      next.delete(speechText);
    }

    this.synthesizing.set(next);
  }

  private markToggling(queryId: number, running: boolean): void {
    const next = new Set(this.togglingFavorite());

    if (running) {
      next.add(queryId);
    } else {
      next.delete(queryId);
    }

    this.togglingFavorite.set(next);
  }
}
```

★ `ngOnInit` 需要 `implements OnInit`（從 `@angular/core` import `OnInit`）或改用 `afterNextRender`／建構子內呼叫；依專案 lint 設定擇一，行為以「元件建立後載入一次」為準。

- [ ] **Step 2: 樣板**

`frontend/src/app/translation/query-list/query-list.html`：

```html
<section class="list">

  <!-- 還在載入 -->
  @if (items() === null) {
    <div class="list__progress" aria-hidden="true"><span></span></div>
  }

  @if (failed()) {
    <p class="list__hint">
      載入失敗。
      <button class="list__retry" type="button" (click)="load()">再試一次</button>
    </p>
  }

  <!--
    ★ 空陣列要跟「還在載入」分開處理。
      一筆收藏都沒有是完全正常的狀態，該顯示的是引導，不是錯誤。
  -->
  @if (items(); as loaded) {
    @if (!loaded.length && !failed()) {
      <p class="list__hint">
        {{ mode() === 'recent'
         ? '還沒有查過任何東西，先到「查詢」打一句話試試。'
         : '在查詢結果按 ♡ 就會收進這裡。' }}
      </p>
    }

    <ul class="list__items">
      @for (item of loaded; track item.queryId) {
        <li class="row">

          <!--
            整列可點 → 還原這一筆。
            ★ 用 <button> 不用 <div (click)>：鍵盤與螢幕閱讀器都能操作，
              而且 Enter 鍵自動就會觸發。
          -->
          <button class="row__main" type="button" (click)="select(item)">
            <span class="row__chinese">{{ item.chineseText }}</span>
            <span class="row__thai">{{ item.thaiText }}</span>
            <span class="row__romanization">{{ item.romanization }}</span>
          </button>

          <div class="row__side">
            <!-- 這一列自己的性別。★ 點下去不會改動畫面上的性別設定 -->
            <span class="row__gender">{{ genderLabel(item) }}</span>

            <button
              class="row__heart"
              type="button"
              [class.row__heart--on]="item.favorited"
              [attr.aria-label]="(item.favorited ? '取消收藏 ' : '加入收藏 ') + item.chineseText"
              (click)="toggleFavorite(item)">
              {{ item.favorited ? '♥' : '♡' }}
            </button>

            <!-- 灰色代表音檔還沒產生，點下去才會補生（第一次要等一兩秒） -->
            <button
              class="row__play"
              type="button"
              [class.row__play--ready]="item.thaiAudioUrl"
              [attr.aria-label]="'播放 ' + item.thaiText"
              (click)="play(item)">
              {{ isSynthesizing(item.thaiText) ? '···' : '▶' }}
            </button>
          </div>

        </li>
      }
    </ul>
  }

</section>
```

- [ ] **Step 3: 樣式**

`frontend/src/app/translation/query-list/query-list.css` —— 沿用 `styles.css` 既有的設計變數（`--ink-*`、`--gold-*`、`--ivory*`、`--line`、`--font-serif`、`--font-thai`、`--font-mono`），不要引進新的顏色值：

```css
/*
 * 清單的樣式。「最近」與「收藏」共用這一份。
 * 顏色與字型一律取用 styles.css 的設計變數，不自己寫死色碼 ——
 * 主題要調整時只改那一個檔案。
 */

.list {
  margin-top: 1.5rem;
}

/* 載入中的跑動光條，與查詢畫面同一套視覺語言 */
.list__progress {
  height: 2px;
  overflow: hidden;
  background: var(--line);
}

.list__progress span {
  display: block;
  width: 40%;
  height: 100%;
  background: var(--gold-400);
  animation: list-progress 1.1s ease-in-out infinite;
}

@keyframes list-progress {
  from { transform: translateX(-100%); }
  to   { transform: translateX(250%); }
}

.list__hint {
  padding: 2rem 0;
  color: var(--ivory-faint);
  font-family: var(--font-serif);
  text-align: center;
}

.list__retry {
  border: 0;
  background: none;
  color: var(--gold-400);
  cursor: pointer;
  text-decoration: underline;
}

.list__items {
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.9rem 0;
  border-bottom: 1px solid var(--line);
}

/* 整列可點的區域。撐滿剩餘寬度，讓手指好按 */
.row__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  border: 0;
  padding: 0;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

/* 中文放最上面當標題 —— 在清單裡找東西是靠中文找的 */
.row__chinese {
  color: var(--ivory);
  font-family: var(--font-serif);
  font-size: 0.95rem;
}

.row__thai {
  color: var(--gold-400);
  font-family: var(--font-thai);
  font-size: 1.15rem;
  line-height: 1.5;
}

.row__romanization {
  color: var(--ivory-dim);
  font-family: var(--font-mono);
  font-size: 0.8rem;
}

.row__side {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.row__gender {
  padding: 0.1rem 0.4rem;
  border: 1px solid var(--line);
  border-radius: 2px;
  color: var(--ivory-faint);
  font-size: 0.7rem;
  white-space: nowrap;
}

.row__heart,
.row__play {
  border: 1px solid var(--line);
  border-radius: 50%;
  width: 2rem;
  height: 2rem;
  background: none;
  /* 預設是「還沒有」的灰色，有東西時才變金色 */
  color: var(--ivory-faint);
  cursor: pointer;
  line-height: 1;
}

.row__heart--on {
  color: var(--gold-400);
  border-color: var(--gold-600);
}

.row__play--ready {
  color: var(--gold-400);
  border-color: var(--gold-600);
}
```

- [ ] **Step 4: 建置**

```bash
cd frontend
npm run build
```

預期：BUILD 成功。（此時元件還沒被任何地方使用，Task 10 才接上去。）

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/translation/query-list
git commit -m "$(cat <<'EOF'
新增最近與收藏共用的清單元件

Feat:
- QueryList 依 mode 決定打哪支 API 與愛心樣式
- 每列標示自身性別，點擊不會改動使用者的性別設定
- 播放鍵沿用灰鍵點了才生的既有行為

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10：分頁殼與還原串接

**Files:**
- Modify: `frontend/src/app/app.ts`
- Modify: `frontend/src/app/app.html`
- Modify: `frontend/src/app/app.css`
- Modify: `frontend/src/app/translation/translation.ts`
- Modify: `frontend/src/app/translation/translation.html`
- Test: `frontend/src/app/app.spec.ts`

**Interfaces:**
- Consumes: Task 9 的 `QueryList`（`mode` 輸入、`restore` 輸出）、Task 7 的 `restore()` API
- Produces:
  - `Translation.restoreQuery(queryId: number): void` —— 供 App 在使用者點清單時呼叫（需為 `public`）
  - `Translation` 結果區的愛心

- [ ] **Step 1: Translation 加上「被外面還原」的入口與愛心**

在 `translation.ts` 加入：

```typescript
  /** 結果區愛心的狀態。與 result 分開存，因為 TranslationResponse 沒有這個欄位。 */
  protected readonly resultFavorited = signal(false);

  /** 愛心請求進行中，避免連點兩下送出兩個請求。 */
  protected readonly favoriteBusy = signal(false);

  /**
   * 由外面（App）在使用者點了「最近」或「收藏」清單的某一列時呼叫。
   *
   * ★ 這裡走的是 restore（GET，用 id 還原），不是 search（POST，重新查一次）。
   *   重查會經過快取鑰匙（原文＋方向＋性別）的比對 ——
   *   那一筆是男生版而你當下切在女生的話，就是一筆全新的查詢，
   *   真的呼叫 OpenAI、真的付錢，而畫面上看起來完全正常。
   *
   * ★ 也刻意不去改動性別切換的狀態。那是一個持久設定（存 localStorage），
   *   被清單默默改掉的話，你下一句自己打的字會用錯的性別去查。
   */
  restoreQuery(queryId: number, favorited: boolean): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
    this.resetExpandables();

    this.translationService.restore(queryId).subscribe({
      next: (response) => {
        this.result.set(response);
        this.sourceText.set(response.sourceText);
        // 愛心狀態由清單那一列傳進來 —— 那份資料剛從後端拿到，是準的，
        // 不必為了一顆愛心再多打一支 API。
        this.resultFavorited.set(favorited);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.showError(error);
        this.loading.set(false);
      },
    });
  }

  /** 點下結果區的愛心。失敗時狀態不變，使用者可以再按一次。 */
  protected toggleResultFavorite(): void {
    const translation = this.result();

    if (!translation || this.favoriteBusy()) {
      return;
    }

    this.favoriteBusy.set(true);

    const request = this.resultFavorited()
      ? this.translationService.removeFavorite(translation.queryId)
      : this.translationService.addFavorite(translation.queryId);

    request.subscribe({
      next: () => {
        this.resultFavorited.set(!this.resultFavorited());
        this.favoriteBusy.set(false);
      },
      // ★ 失敗就什麼都不改。愛心停在「看起來成功了」的話，
      //   使用者會以為收藏好了，下次打開收藏卻找不到。
      error: () => this.favoriteBusy.set(false),
    });
  }
```

並在 `search()` 的 `next` 裡加上 `this.resultFavorited.set(false);`。

★ 這代表「查一句以前收藏過的話」時，愛心會顯示成空心。這是刻意的取捨：要顯示對的狀態就得在每次查詢多打一支 API 去問收藏狀態，而按下去只是把 `favorited_at` 重設一次（`markFavorite` 的 `IS NULL` 條件會擋掉，收藏時間不會被覆寫，清單也不會出現第二列）。代價可以接受，不值得為它多一次往返。

- [ ] **Step 2: `translation.html` 結果區加愛心**

在 `.result__head` 區塊裡，`@if (translation.fromCache)` 那組 badge 之後加上：

```html
        <!--
          收藏。★ 這顆鍵永遠都在，實心代表已收藏。
          收藏的單位是「一次查詢」，也就是含當初的方向與性別 ——
          同一句中文的男版與女版是兩筆不同的收藏。
        -->
        <button
          class="favorite"
          type="button"
          [class.favorite--on]="resultFavorited()"
          [disabled]="favoriteBusy()"
          [attr.aria-label]="resultFavorited() ? '取消收藏' : '加入收藏'"
          (click)="toggleResultFavorite()">
          {{ resultFavorited() ? '♥' : '♡' }}
        </button>
```

在 `translation.css` 加上對應樣式（沿用設計變數）：

```css
/* 結果區的收藏鍵。空心是還沒收藏，實心是已收藏 */
.favorite {
  margin-left: auto;
  border: 1px solid var(--line);
  border-radius: 50%;
  width: 2rem;
  height: 2rem;
  background: none;
  color: var(--ivory-faint);
  cursor: pointer;
  line-height: 1;
}

.favorite--on {
  color: var(--gold-400);
  border-color: var(--gold-600);
}
```

- [ ] **Step 3: App 改成分頁殼**

`frontend/src/app/app.ts`：

```typescript
import { Component, ViewChild, signal } from '@angular/core';
import { QueryList } from './translation/query-list/query-list';
import { Translation } from './translation/translation';

/** 三個分頁。 */
export type AppTab = 'search' | 'recent' | 'favorite';

/**
 * 根元件，同時是分頁殼。
 *
 * ★ 刻意不引入 Angular Router：只有三個分頁、不需要分享網址，
 *   加 router 要多一套設定，還要顧到 service worker 的路由 fallback。
 *   代價是手機的返回手勢會直接離開 App，而不是退回上一個分頁。
 */
@Component({
  selector: 'app-root',
  imports: [Translation, QueryList],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {

  /** 目前在哪一個分頁。 */
  protected readonly tab = signal<AppTab>('search');

  /**
   * 查詢分頁的元件實例，用來在使用者點清單時把結果還原進去。
   * ★ 查詢分頁一直存在（用 hidden 而不是 @if 藏起來），
   *   否則切走再切回來，剛剛查的結果會整個消失。
   */
  @ViewChild(Translation) private translation?: Translation;

  protected switchTo(tab: AppTab): void {
    this.tab.set(tab);
  }

  /** 使用者點了清單的某一列：切到查詢分頁並把那一筆還原出來。 */
  protected restore(queryId: number, favorited: boolean): void {
    this.tab.set('search');
    this.translation?.restoreQuery(queryId, favorited);
  }
}
```

`frontend/src/app/app.html`：

```html
<!--
  分頁列。★ 三個分頁的內容都一直存在，用 hidden 藏起來而不是用 @if 拆掉 ——
  拆掉的話切走再切回來，剛剛查的結果與展開的逐詞拆解會整個消失。
-->
<nav class="tabs" role="tablist" aria-label="主要分頁">
  <button
    class="tabs__item"
    type="button"
    role="tab"
    [class.tabs__item--active]="tab() === 'search'"
    [attr.aria-selected]="tab() === 'search'"
    (click)="switchTo('search')">
    查詢
  </button>

  <button
    class="tabs__item"
    type="button"
    role="tab"
    [class.tabs__item--active]="tab() === 'recent'"
    [attr.aria-selected]="tab() === 'recent'"
    (click)="switchTo('recent')">
    最近
  </button>

  <button
    class="tabs__item"
    type="button"
    role="tab"
    [class.tabs__item--active]="tab() === 'favorite'"
    [attr.aria-selected]="tab() === 'favorite'"
    (click)="switchTo('favorite')">
    ♡ 收藏
  </button>
</nav>

<div [hidden]="tab() !== 'search'">
  <app-translation />
</div>

<!--
  ★ 這兩個清單用 @if 而不是 hidden：切過去的時候要重新去要資料，
    不然你在查詢分頁按了愛心，切到收藏卻看不到它。
-->
@if (tab() === 'recent') {
  <app-query-list mode="recent" (restore)="restore($event.queryId, $event.favorited)" />
}

@if (tab() === 'favorite') {
  <app-query-list mode="favorite" (restore)="restore($event.queryId, $event.favorited)" />
}
```

`frontend/src/app/app.css`：

```css
/*
 * 分頁列。查詢畫面自己的排版在 translation.css，這裡只管分頁鍵。
 */

.tabs {
  display: flex;
  justify-content: center;
  gap: 1.5rem;
  padding: 1rem 0 0;
}

.tabs__item {
  position: relative;
  border: 0;
  padding: 0.4rem 0.2rem;
  background: none;
  color: var(--ivory-faint);
  font-family: var(--font-serif);
  font-size: 0.95rem;
  cursor: pointer;
}

/* 選中的分頁：字變金色，底下一條金線 */
.tabs__item--active {
  color: var(--gold-400);
}

.tabs__item--active::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 1px;
  background: var(--gold-500);
}
```

- [ ] **Step 4: 更新煙霧測試**

`app.spec.ts` 的第二個測試改成同時確認分頁列與查詢畫面都在，並在檔頭補一句說明分頁殼的存在：

```typescript
  it('應該渲染出分頁列與查詢畫面', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;

    // 三個分頁都要在
    expect(compiled.querySelectorAll('.tabs__item').length).toBe(3);

    // 查詢畫面預設就顯示
    expect(compiled.querySelector('h1')?.textContent).toContain('中泰翻譯查詢');
    expect(compiled.querySelector('.search__button')?.textContent).toContain('查詢');
  });
```

- [ ] **Step 5: 跑前端測試與建置**

```bash
cd frontend
npm test
npm run build
```

預期：測試 PASS、BUILD 成功。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/app.ts frontend/src/app/app.html frontend/src/app/app.css frontend/src/app/app.spec.ts frontend/src/app/translation frontend/src/app/services
git commit -m "$(cat <<'EOF'
新增查詢／最近／收藏三個分頁

Feat:
- App 改為分頁殼，查詢分頁常駐、兩份清單切過去才載入
- 查詢結果加上收藏鍵
- 點清單一列以 id 還原結果，不重新查詢也不改動性別設定

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 11：整合驗證

**Files:** 無（只跑與確認）

- [ ] **Step 1: 跑完整測試**

```
.\mvnw.cmd -B clean test
```

預期：全綠。

```bash
cd frontend
npm test
```

預期：全綠。

- [ ] **Step 2: 啟動並手動走一遍**

啟動後端（local profile）與 `npm start`，依序確認：

1. 查一句沒查過的中文 → 有結果、有聲音、右上角愛心是空心
2. 按愛心 → 變實心
3. 切到「收藏」→ 那一句在最上面，標籤是「男」（或你當下的性別）
4. 在收藏清單按 ▶ → 直接出聲，不必進入查詢分頁
5. 點那一列 → 跳到「查詢」分頁顯示完整結果，愛心是實心
6. 切到「最近」→ 剛剛查的在最上面，愛心是實心
7. **★ 把性別切到女生，回到「最近」點那一筆男生的** → 顯示的仍是男生版（`ครับ`），而且畫面上方的性別切換**還停在女生**
8. 在收藏清單按實心愛心 → 那一列消失
9. 查同一句話第二次 → 回到「最近」，它應該還在最上面（`last_viewed_at` 有更新）
10. 收藏清單一筆都沒有時 → 顯示「在查詢結果按 ♡ 就會收進這裡」，不是錯誤訊息

- [ ] **Step 3: 確認沒有 N+1**

後端日誌開 SQL（`application-local.yml` 的 `spring.jpa.show-sql`，若原本是關的就暫時打開），打開一次收藏清單，確認：

- `translation_query` 的 SELECT 一次
- `audio_asset` 的 SELECT **一次**（`... IN (?, ?, ?)`），不是每列一次

確認完把設定改回原狀。

- [ ] **Step 4: Commit（若上述步驟有修正）**

```bash
git add -A
git commit -m "$(cat <<'EOF'
修正整合驗證發現的問題

Fix:
- <依實際修正內容填寫>

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

無修正則跳過此步。

---

## Self-Review 紀錄

- **Spec coverage**：spec 第 4 節（資料表）→ Task 1；第 5 節（五支 API）→ Task 2／4／5／6；第 6.2 節（批次撈音檔）→ Task 3／4；第 7 節（前端）→ Task 7～10；第 8 節（測試範圍）→ 各 Task 的測試步驟；第 9 節（風險）→ 各處 ★ 註記與 Task 11 的手動驗證第 7 項。
- **Placeholder**：無 TBD／TODO。Task 11 Step 4 的 commit 訊息留空是刻意的（依實際修正填寫），且該步驟在無修正時跳過。
- **Type consistency**：`TranslationSummaryDto` 八個欄位在 Task 2／3／4／6 一致；前端 `TranslationSummary` 欄位與之對應；`QueryList.restore` 在 Task 9 與 Task 10 一致為 `output<TranslationSummary>`；`Translation.restoreQuery(queryId, favorited)` 在 Task 10 的兩處用法一致。
- **已知偏離 spec 之處**：spec 決策 15 寫「`translation.ts` 不重構」，但 Task 8 把播放器抽成 `AudioPlayerService`。理由是清單元件也要播放，若各自持有 `<audio>` 與放大器，同一頁會出現兩個 `AudioContext`。這是為了本功能而做的必要調整，範圍僅限播放那三個方法。
