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
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
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
