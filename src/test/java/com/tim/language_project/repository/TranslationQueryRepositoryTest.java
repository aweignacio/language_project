package com.tim.language_project.repository;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案在測什麼？
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測 translation_query 這張表 —— 也就是「查詢結果快取」。
 *
 *  使用者輸入「我想喝酒」，程式去問 OpenAI 拿到泰文之後，
 *  會把結果存進這張表。下次有人再輸入同一句，就直接從這裡撈出來，
 *  不用再花錢問 OpenAI 一次。
 *
 *  這支測試要確認兩件事：存進去再讀出來內容不會壞掉，以及快取的鑰匙是對的。
 *
 * ── ★ 2026-08-14 改版：快取的鑰匙從一欄變成三欄 ─────────────────────────
 *
 *      以前  source_text
 *      現在  source_text ＋ direction ＋ gender
 *
 *    為什麼要多兩欄：同一句「我想喝酒」，男生講出來是 ผมอยากดื่มเหล้าครับ，
 *    女生講出來是 ฉันอยากดื่มเหล้าค่ะ —— 那是兩句不同的泰文，
 *    共用一筆快取的話，切換性別會看到另一個性別的講法。
 *
 *    同時，「音檔為 null 也要能存」那個舊測試被刪掉了 ——
 *    audio_file 欄位已經不在這張表上，音檔改由 audio_asset 統一管理，
 *    對應的測試搬到了 AudioAssetRepositoryTest。
 *
 *  ※ 測試的基本概念（什麼是 @Test、assertThat、三段式結構、交易回滾……）
 *    寫在同資料夾的 VocabularyRepositoryTest.java 檔頭與註解裡，
 *    第一次看建議從那支開始。這支只補充本檔特有的部分。
 */

import com.tim.language_project.dto.response.TranslationQueryDto;
import com.tim.language_project.entity.TranslationQuery;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * 兩張貼在整個類別上的「貼紙」，影響裡面所有測試：
 *
 *   @DataJpaTest
 *     跟 Spring 說「我要測資料庫，請幫我連好 SQL Server、
 *     並把 TranslationQueryRepository 準備好給我」。
 *     （Repository 你只寫了 interface，實作是 Spring 動態生出來的，
 *       沒有它幫忙，你拿不到能用的東西。）
 *
 *   @AutoConfigureTestDatabase(replace = NONE)
 *     擋掉 @DataJpaTest「偷偷把資料庫換成記憶體 H2」的預設行為，
 *     強制連真正的 SQL Server。
 *     原因就是下面第一個測試要驗的事 —— H2 分不出 NVARCHAR 和 VARCHAR，
 *     用 H2 測，泰文怎麼存都對，那這支測試就完全失去意義了。
 *
 *   ※ 這兩張貼紙的 import 路徑在 Spring Boot 4.1 換過位置，
 *     網路上找到的 3.x 範例會是 org.springframework.boot.test.autoconfigure.*，
 *     在這個專案會編譯失敗。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TranslationQueryRepositoryTest {

    // 跟 Spring 要一個準備好的 Repository。
    // 這裡沒有寫 new，也不能寫 —— 它是 interface。
    @Autowired
    private TranslationQueryRepository translationQueryRepository;

    /*
     * ═══ 測試一：泰文與拼音存進去再讀出來，不可以壞掉 ═══════════════════
     *
     * 這是整個專案「最重要」的一支測試，理由值得說清楚。
     *
     * 這個資料庫的 collation 是 SQL_Latin1_General_CP1_CI_AS，
     * 在這個設定下，VARCHAR 欄位「無法保存非 ASCII 字元」。
     *
     * 把泰文存進 VARCHAR 會發生什麼事？
     *     ฉันอยากดื่มเหล้า  →  ????????????????
     *
     * 而且 ——「寫入的當下不會拋出任何錯誤」。
     * 程式看起來一切正常，資料卻已經永久損毀了。這叫「靜默損毀」，
     * 是最難發現的一種 bug，因為沒有任何東西會提醒你。
     *
     * 唯一的防線，就是在 entity 的每個文字欄位標上
     * columnDefinition = "NVARCHAR(n)"。少標一個，那個欄位就完了。
     *
     * 這支測試的作用，就是每次跑 mvnw test 都幫你檢查一次有沒有標漏。
     */
    @Test
    @DisplayName("泰文與拼音寫入後讀回不應損毀")
    void shouldPreserveThaiTextAndRomanization() {
        /*
         * ── 第一段：準備資料（Arrange）──
         *
         * 手動組一筆完整的查詢結果。
         * 這裡刻意挑了三種「會踩到地雷」的字元：
         *   中文  我想喝酒          → 非 ASCII
         *   泰文  ฉันอยากดื่มเหล้า  → 非 ASCII
         *   拼音  chǎn（ǎ 有聲調符號）→ 也是非 ASCII，最容易被忽略
         *
         * 拼音看起來像英文，很容易讓人以為用 VARCHAR 就夠了 ——
         * 但只要有一個聲調符號，就會壞掉。所以拼音也必須驗。
         */
        TranslationQuery query = newQuery(
                "測試勿刪我想喝酒",
                TranslationDirectionEnum.ZH_TO_TH,
                SpeakerGenderEnum.FEMALE,
                "測試勿刪我想喝酒",
                "ฉันอยากดื่มเหล้า",
                "chǎn yàak dùuem lâo");

        // saveAndFlush 的 flush 是關鍵：
        // 強迫 JPA「現在就把 INSERT 真的送進資料庫」，而不是留在記憶體裡。
        // 沒有 flush 的話，下面查出來的可能只是記憶體裡的同一個物件，
        // 根本沒經過 SQL Server，NVARCHAR 對不對就驗不到了。
        translationQueryRepository.saveAndFlush(query);

        /*
         * ── 第二段：執行要測的動作（Act）──
         *
         * 用中文原文去查快取。這正是正式流程每次查詢的第一個動作：
         * 「這句話以前有人查過嗎？」有的話就不用花錢問 OpenAI 了。
         */
        Optional<TranslationQueryDto> found = translationQueryRepository.findByKey(
                "測試勿刪我想喝酒",
                TranslationDirectionEnum.ZH_TO_TH,
                SpeakerGenderEnum.FEMALE);

        /*
         * ── 第三段：檢查結果（Assert）──
         *
         * assertThat = 「我主張……」
         * 條件成立就沒事，不成立就當場失敗並印出期望值與實際值。
         */

        // 我主張：有查到（Optional 裡面不是空的）
        assertThat(found).isPresent();

        // 我主張：泰文一字不差。若 thai_text 欄位標成了 VARCHAR，
        // 這裡實際拿到的會是「????????????????」，測試立刻紅字。
        assertThat(found.get().thaiText()).isEqualTo("ฉันอยากดื่มเหล้า");

        // 我主張：連聲調符號都完整保留。
        assertThat(found.get().romanization()).isEqualTo("chǎn yàak dùuem lâo");

        // 我主張：中文原文也沒壞。
        // 這欄還兼任「快取的鑰匙」—— 它要是壞了，之後永遠查不到快取，
        // 每次查詢都會重新付費呼叫 OpenAI。
        assertThat(found.get().sourceText()).isEqualTo("測試勿刪我想喝酒");
    }

    /*
     * ═══ 測試二：同一句話的男版與女版要能各存一筆 ═══════════════════════
     *
     * ★ 決策 7 的第一半：同一句話的男版與女版，泰文真的不一樣，必須各存一筆。
     *
     *     男：ผมอยากดื่มเหล้าครับ    自稱 ผม、句尾 ครับ
     *     女：ฉันอยากดื่มเหล้าค่ะ    自稱 ฉัน、句尾 ค่ะ
     *
     *   這條唯一鍵如果只用 source_text，男版會把女版蓋掉（或反過來），
     *   使用者切換性別後看到的是另一個性別的講法。
     */
    @Test
    @DisplayName("同一句話的男版與女版可各存一筆")
    void shouldAllowSameSourceTextWithDifferentGender() {
        translationQueryRepository.saveAndFlush(newQuery(
                "測試勿刪我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                "測試勿刪我想喝酒", "ผมอยากดื่มเหล้าครับ", "pǒm yàak dùuem lâo khráp"));

        translationQueryRepository.saveAndFlush(newQuery(
                "測試勿刪我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.FEMALE,
                "測試勿刪我想喝酒", "ฉันอยากดื่มเหล้าค่ะ", "chǎn yàak dùuem lâo khâ"));

        assertThat(translationQueryRepository.findByKey(
                "測試勿刪我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE))
                .isPresent();
        assertThat(translationQueryRepository.findByKey(
                "測試勿刪我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.FEMALE))
                .isPresent();
    }

    /*
     * ═══ 測試三：同一句話同一性別只能有一筆 ═════════════════════════════
     *
     * 唯一鍵的另一半。少了它，同一句話會被重複寫入好幾筆，
     * 每一筆都是付過錢的 —— 而快取也會因為撈到多筆而出問題。
     */
    @Test
    @DisplayName("同一句話同一性別不可重複寫入")
    void shouldRejectDuplicateKey() {
        translationQueryRepository.saveAndFlush(newQuery(
                "測試勿刪我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                "測試勿刪我想喝酒", "ผมอยากดื่มเหล้าครับ", "pǒm yàak dùuem lâo khráp"));

        assertThatThrownBy(() -> translationQueryRepository.saveAndFlush(newQuery(
                "測試勿刪我想喝酒", TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE,
                "測試勿刪我想喝酒", "ผมอยากทานเหล้าครับ", "pǒm yàak thaan lâo khráp")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /*
     * ═══ 測試四：泰翻中的性別是 null，但仍然要唯一 ══════════════════════
     *
     * ★ 泰翻中沒有性別概念，gender 存 null。
     *   SQL Server 的 UNIQUE 把 null 當成一個值來比對，
     *   所以「同一句泰文只會有一筆」這件事仍然成立。
     *
     *   這個測試就是在確認那個行為真的如我們所想 ——
     *   如果哪天換了資料庫（有些資料庫把 null 視為「彼此都不相等」），
     *   同一句泰文就會被重複寫入，而且不會有任何錯誤。
     */
    @Test
    @DisplayName("泰翻中的性別為 null 時仍然唯一")
    void shouldEnforceUniquenessWhenGenderIsNull() {
        translationQueryRepository.saveAndFlush(newQuery(
                "測試勿刪ผมอยากดื่มเหล้า", TranslationDirectionEnum.TH_TO_ZH, null,
                "我想喝酒", "ผมอยากดื่มเหล้า", "pǒm yàak dùuem lâo"));

        assertThatThrownBy(() -> translationQueryRepository.saveAndFlush(newQuery(
                "測試勿刪ผมอยากดื่มเหล้า", TranslationDirectionEnum.TH_TO_ZH, null,
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
}

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  兩個測試合起來守住了什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *   測試一  →  資料不會靜默損毀（entity 的 NVARCHAR 標對了）
 *   測試二  →  語音失敗時系統仍能運作（audio_file 允許 null）
 *
 *  這兩件事都是「壞掉的時候很難查」的類型：
 *  第一個不會報錯，第二個要等到語音服務真的出問題才會浮現。
 *  寫成測試之後，每次 mvnw test 都會自動幫你檢查一遍。
 *
 * ── 想看失敗長什麼樣子？ ────────────────────────────────────────────────
 *
 *  把上面任何一行的期望值故意改錯，例如：
 *      assertThat(found.get().thaiText()).isEqualTo("這是錯的");
 *
 *  然後執行：
 *      .\mvnw.cmd -B test -Dtest=TranslationQueryRepositoryTest
 *
 *  會看到類似這樣的訊息（記得改回來）：
 *      expected: "這是錯的"
 *       but was: "ฉันอยากดื่มเหล้า"
 */
