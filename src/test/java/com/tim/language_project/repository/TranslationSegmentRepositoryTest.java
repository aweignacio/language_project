package com.tim.language_project.repository;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  防「同一句泰文被重新拆解一次，白花一次 OpenAI 的錢」。
 *
 *  情境是這樣的：
 *
 *      你查「我想喝酒」    → AI 回 ผมอยากดื่มเหล้าครับ  → 存成第 42 筆
 *      你查「我想要喝酒」  → AI 回 ผมอยากดื่มเหล้าครับ  → 存成第 57 筆
 *                                   ↑ 一模一樣的泰文
 *
 *  兩句中文不同，所以快取（鑰匙是原文）一定會存兩筆，這是對的。
 *  但「泰文的逐詞拆解」對這兩筆來說是完全一樣的東西 ——
 *  第 57 筆不該再付一次錢去拆同一句泰文。
 *
 *  ★ 這件事只有 SQL 做得到，所以只有這種「連真的資料庫」的測試驗得到。
 *    Service 那邊的測試把 Repository 換成假的，假的永遠會照你教的回答，
 *    根本不會去比對泰文 —— 那種測試對這個 bug 完全免疫。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：這支測試跑起來會發生什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜Spring 連上真的 PostgreSQL ─────────────────────────────────
 *
 *      @DataJpaTest 只載入資料庫那一層（Entity、Repository），
 *      不會把整個網站啟動起來，所以跑得很快。
 *
 *      @AutoConfigureTestDatabase(replace = NONE)
 *        擋掉「偷偷把資料庫換成記憶體 H2」的預設行為。
 *        ★ 一定要擋。這支測試驗的是 SQL 的行為，
 *          用 H2 驗過不代表正式環境的 PostgreSQL 也會這樣。
 *
 *      ※ 這兩張貼紙的 import 路徑在 Spring Boot 4.1 換過位置，
 *        網路上的 3.x 範例在這個專案會編譯失敗。
 *
 * ── 第 2 步｜塞兩筆泰文相同、但只有一筆拆解過的查詢 ─────────────────────
 *
 *      translation_query
 *        id  source_text        thai_text
 *        ──  ─────────────────  ──────────────────────
 *        42  測試勿刪我想喝酒    ผมอยากดื่มเหล้าครับ   ← 有逐詞
 *        57  測試勿刪我想要喝酒  ผมอยากดื่มเหล้าครับ   ← 沒有逐詞
 *
 *      translation_segment
 *        query_id  seq_no  chinese_text  thai_text
 *        ────────  ──────  ────────────  ─────────
 *        42        1       我            ผม
 *        42        2       想            อยาก
 *
 * ── 第 3 步｜拿泰文去找，應該撈到 42 那兩列 ─────────────────────────────
 *
 *      findByThaiTextOrderBySeqNo("ผมอยากดื่มเหล้าครับ")  →  2 列
 *
 *      ★ 正式流程走到這裡就會直接回傳給使用者，不呼叫 OpenAI ——
 *        第 57 筆撿了第 42 筆拆好的結果，一毛錢都不花。
 *
 * ── 第 4 步｜測完自動退回 ───────────────────────────────────────────────
 *
 *      @DataJpaTest 每個測試跑在一個交易裡、結束後自動 rollback，
 *      所以上面塞的資料不會真的留在資料庫。
 *      （原文仍加「測試勿刪」前綴 —— 萬一哪天 rollback 失效，
 *        你在資料庫看到這幾筆才知道它們是哪來的。）
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  三個測試各自在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測試一  泰文相同就要撈得到別筆查詢的逐詞
 *          → 防「白花錢重拆同一句泰文」
 *
 *  測試二  ★兩筆都拆過時，只能回其中一份
 *          → 防「畫面上出現重複的逐詞列」。
 *            SQL 少了那層 MIN 就會兩份疊在一起，
 *            而且要等資料累積之後才看得出來。
 *
 *  測試三  ★選的那一筆必須「真的有逐詞」
 *          → 防最陰險的那種寫法：SQL 只挑「id 最小的同泰文查詢」，
 *            挑到一筆沒拆過的就回空，我們以為「還沒拆過」而重新付錢 ——
 *            明明另一筆早就拆好了。而且畫面完全正常，你永遠不會發現。
 */

import com.tim.language_project.dto.response.TranslationSegmentDto;
import com.tim.language_project.entity.TranslationQuery;
import com.tim.language_project.entity.TranslationSegment;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TranslationSegmentRepositoryTest {

    /** 兩句不同的中文會翻出這同一句泰文，整支測試都圍繞著它。 */
    private static final String SHARED_THAI = "ผมอยากดื่มเหล้าครับ";

    @Autowired
    private TranslationQueryRepository translationQueryRepository;

    @Autowired
    private TranslationSegmentRepository translationSegmentRepository;

    /*
     * ═══ 測試一：泰文相同時，要撈得到「別筆查詢」拆好的逐詞 ═════════════
     *
     * 這是整個優化的重點。沒有它，「我想要喝酒」會重新付一次錢，
     * 拆出跟「我想喝酒」一模一樣的東西。
     */
    @Test
    @DisplayName("泰文相同時應撈到另一筆查詢已拆好的逐詞")
    void shouldFindSegmentsFromAnotherQueryWithTheSameThaiText() {
        Long segmentedId = givenQuery("測試勿刪我想喝酒");
        givenSegments(segmentedId, "我", "想");

        // 另一句中文，同一句泰文，而且「沒有」自己的逐詞
        givenQuery("測試勿刪我想要喝酒");

        List<TranslationSegmentDto> found =
                translationSegmentRepository.findByThaiTextOrderBySeqNo(SHARED_THAI);

        // ★ 撈得到 = 正式流程不會再呼叫 OpenAI
        assertThat(found).hasSize(2);
        assertThat(found).extracting(TranslationSegmentDto::chineseText)
                .containsExactly("我", "想");
    }

    /*
     * ═══ 測試二：兩筆都拆過時，只能回其中一份 ═══════════════════════════
     *
     * ★ SQL 裡那層 MIN 就是為了這件事。
     *   少了它會把兩筆的逐詞全部撈出來疊在一起，畫面上出現重複的列 ——
     *   而且要等到真的有兩筆同泰文的查詢才會發生，開發時很難踩到。
     */
    @Test
    @DisplayName("兩筆查詢都拆過時只應回傳其中一份")
    void shouldNotReturnDuplicatedSegmentsWhenBothQueriesHaveThem() {
        Long firstId = givenQuery("測試勿刪我想喝酒");
        givenSegments(firstId, "我", "想");

        Long secondId = givenQuery("測試勿刪我想要喝酒");
        givenSegments(secondId, "我", "想");

        List<TranslationSegmentDto> found =
                translationSegmentRepository.findByThaiTextOrderBySeqNo(SHARED_THAI);

        // 四列疊在一起就是錯的
        assertThat(found).hasSize(2);
    }

    /*
     * ═══ 測試三：★選到的那一筆必須「真的有逐詞」 ═══════════════════════
     *
     * 這支測試專門釘死一種很容易寫出來、而且看起來完全正確的 SQL：
     *
     *     「找 id 最小的同泰文查詢，撈它的逐詞」
     *
     * 下面的資料就是它的反例 —— 先存的那筆「沒有」拆解，後存的才有。
     * 那種寫法會挑到前面那筆、撈出空的，於是我們以為「還沒拆過」，
     * 重新呼叫一次 OpenAI ——★ 明明後面那筆早就拆好了 ★
     *
     * 而且畫面上完全正常（你會看到正確的拆解結果），
     * 只是每點一次就默默多付一次錢。這種錯不靠測試是抓不到的。
     */
    @Test
    @DisplayName("先存的那筆沒有逐詞時，應改撈有逐詞的那一筆")
    void shouldSkipQueriesThatHaveNoSegments() {
        // 先存的這筆沒有拆解
        givenQuery("測試勿刪我想喝酒");

        // 後存的這筆才有（id 比較大）
        Long segmentedId = givenQuery("測試勿刪我想要喝酒");
        givenSegments(segmentedId, "我", "想");

        List<TranslationSegmentDto> found =
                translationSegmentRepository.findByThaiTextOrderBySeqNo(SHARED_THAI);

        assertThat(found).hasSize(2);
    }

    /**
     * 存一筆查詢，泰文固定用 SHARED_THAI，回傳資料庫給的 id。
     * saveAndFlush 的 flush 是為了「現在就拿到 id」，逐詞要拿它當外鍵。
     */
    private Long givenQuery(String sourceText) {
        TranslationQuery query = new TranslationQuery();
        query.setSourceText(sourceText);
        query.setDirection(TranslationDirectionEnum.ZH_TO_TH);
        query.setGender(SpeakerGenderEnum.MALE);
        query.setChineseText(sourceText);
        query.setThaiText(SHARED_THAI);
        query.setRomanization("pǒm yàak dùuem lâo khráp");

        return translationQueryRepository.saveAndFlush(query).getId();
    }

    /** 幫某一筆查詢存上逐詞，seq_no 從 1 開始遞增。 */
    private void givenSegments(Long queryId, String... chineseTexts) {
        int seqNo = 1;

        for (String chineseText : chineseTexts) {
            TranslationSegment segment = new TranslationSegment();
            segment.setQueryId(queryId);
            segment.setSeqNo(seqNo++);
            segment.setChineseText(chineseText);
            segment.setThaiText("ผม");
            segment.setRomanization("pǒm");
            translationSegmentRepository.save(segment);
        }

        translationSegmentRepository.flush();
    }
}
