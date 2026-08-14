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

            WHERE translationSegment.queryId = :queryId

            ORDER BY translationSegment.seqNo
            """)
    List<TranslationSegmentDto> findByQueryIdOrderBySeqNo(@Param("queryId") Long queryId);

    /*
     * 這兩個是 SpeechTextGuard 在用的：判斷一段文字是不是系統自己產生過的，
     * 用來擋掉會花錢的任意合成請求。逐詞拆解是最常被點播放鍵的地方，
     * 所以守門檢查會先查這張表。方法名稱照 Spring Data 的規則寫，
     * 查詢語句由它自動產生，不需要 @Query。
     */
    boolean existsByThaiText(String thaiText);

    boolean existsByChineseText(String chineseText);
}
