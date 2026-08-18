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
