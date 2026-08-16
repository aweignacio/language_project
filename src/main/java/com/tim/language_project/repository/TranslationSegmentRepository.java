package com.tim.language_project.repository;

import com.tim.language_project.dto.response.TranslationSegmentDto;
import com.tim.language_project.entity.TranslationSegment;
import com.tim.language_project.entity.TranslationSegmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 各筆查詢逐詞拆解結果的資料存取。
 */
public interface TranslationSegmentRepository
        extends JpaRepository<TranslationSegment, TranslationSegmentId> {

    /*
     * ★ 兩個音檔網址在這裡固定給 null。
     *   音檔不存在這張表上（全站的音檔由 audio_asset 持有），
     *   而 JPQL 沒辦法在建構子表達式裡跨表把它們一起撈出來。
     *   TranslationService 會在拿到結果之後自己補上。
     *
     * ══════════════════════════════════════════════════════════════════════
     *  ★ 為什麼是用「泰文」找逐詞，不是用 queryId（2026-08-16 改的）
     * ══════════════════════════════════════════════════════════════════════
     *
     *  「我想喝酒」和「我想要喝酒」是兩句不同的中文，會各自存一列
     *  translation_query（因為快取的鑰匙是原文），但 AI 翻出來的泰文
     *  常常一模一樣：
     *
     *      id  source_text  thai_text
     *      ──  ───────────  ──────────────────────
     *      42  我想喝酒     ผมอยากดื่มเหล้าครับ
     *      57  我想要喝酒   ผมอยากดื่มเหล้าครับ   ← 同一句泰文
     *
     *  用 queryId 去找的話，57 找不到自己的逐詞，於是又打一次 OpenAI
     *  把「同一句泰文」重新拆一次 —— 那是白花的錢，而且拆出來的東西
     *  跟 42 的完全一樣。
     *
     *  ★ 改成用 thai_text 找，57 就會直接撿 42 拆好的結果，一毛錢都不花。
     *
     *  ★ 性別不必另外比對。男版女版的泰文本來就是兩個不同的字串
     *    （ผม/ครับ 對 ฉัน/ค่ะ），泰文一樣就代表性別已經對上了。
     *
     *  ★ 「翻譯」那一次呼叫省不掉 —— 要先拿到泰文才知道泰文一樣，
     *    而拿到泰文就是付錢的那一刻。這裡省下的是「逐詞拆解」那一次，
     *    也就是實測 867 個 token 裡最大的那一塊。
     *
     * ── 那個看起來很嚇人的巢狀子查詢在做什麼 ──────────────────────────
     *
     *      最內層  找出所有「泰文是這一句」的查詢 id      → 42、57
     *      中間層  這些 id 裡面，哪一個「真的有逐詞」→ 取最小的那個 → 42
     *      最外層  把 42 的逐詞照 seq_no 撈出來
     *
     *  ★ 中間層一定要有「真的有逐詞」這個條件。
     *    少了它會選到 57（如果它比較小），撈出空的，我們就以為
     *    「還沒拆過」而重新付錢 —— 明明 42 已經拆好了。
     *
     *  ★ 為什麼要取「一個」而不是全部撈出來？
     *    42 和 57 各有一份逐詞時，全部撈會得到兩份疊在一起，
     *    畫面上會出現重複的列。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationSegmentDto(
                translationSegment.seqNo,
                translationSegment.chineseText,
                translationSegment.thaiText,
                translationSegment.romanization,
                NULL,
                NULL
            )

            FROM TranslationSegment translationSegment

            WHERE translationSegment.queryId = (
                SELECT MIN(sibling.queryId)
                FROM TranslationSegment sibling
                WHERE sibling.queryId IN (
                    SELECT translationQuery.id
                    FROM TranslationQuery translationQuery
                    WHERE translationQuery.thaiText = :thaiText
                )
            )

            ORDER BY translationSegment.seqNo
            """)
    List<TranslationSegmentDto> findByThaiTextOrderBySeqNo(@Param("thaiText") String thaiText);

    /*
     * 這兩個是 SpeechTextGuard 在用的：判斷一段文字是不是系統自己產生過的，
     * 用來擋掉會花錢的任意合成請求。逐詞拆解是最常被點播放鍵的地方，
     * 所以守門檢查會先查這張表。方法名稱照 Spring Data 的規則寫，
     * 查詢語句由它自動產生，不需要 @Query。
     */
    boolean existsByThaiText(String thaiText);

    boolean existsByChineseText(String chineseText);
}
