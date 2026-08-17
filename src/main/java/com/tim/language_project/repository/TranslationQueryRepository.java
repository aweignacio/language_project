package com.tim.language_project.repository;

import com.tim.language_project.dto.response.TranslationQueryDto;
import com.tim.language_project.entity.TranslationQuery;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 查詢結果快取的資料存取。
 */
public interface TranslationQueryRepository extends JpaRepository<TranslationQuery, Long> {

    /*
     * ★ gender 可能是 null（泰翻中）。JPQL 的 = 比不到 null，
     *   所以要寫成「(:gender IS NULL AND ... IS NULL) OR ... = :gender」的形式。
     *   直接寫 gender = :gender 的話，泰翻中的快取永遠不會命中，
     *   每次查同一句泰文都會重新付費 —— 而且不會有任何錯誤訊息。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationQueryDto(
                translationQuery.id,
                translationQuery.sourceText,
                translationQuery.direction,
                translationQuery.gender,
                translationQuery.chineseText,
                translationQuery.thaiText,
                translationQuery.romanization,
                translationQuery.isWord
            )

            FROM TranslationQuery translationQuery

            WHERE translationQuery.sourceText = :sourceText
              AND translationQuery.direction = :direction
              AND ((:gender IS NULL AND translationQuery.gender IS NULL)
                   OR translationQuery.gender = :gender)
            """)
    Optional<TranslationQueryDto> findByKey(
            @Param("sourceText") String sourceText,
            @Param("direction") TranslationDirectionEnum direction,
            @Param("gender") SpeakerGenderEnum gender);

    /*
     * ★ 逐詞拆解與各種說法是「點了才跑」的獨立呼叫，前端只帶得回一個 queryId，
     *   所以要能單獨用 id 把那筆查詢撈回來。用投影而不是 findById 拿實體，
     *   是因為這兩支 API 都在交易外面跑，拿實體回來只會多一份用不到的狀態。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationQueryDto(
                translationQuery.id,
                translationQuery.sourceText,
                translationQuery.direction,
                translationQuery.gender,
                translationQuery.chineseText,
                translationQuery.thaiText,
                translationQuery.romanization,
                translationQuery.isWord
            )

            FROM TranslationQuery translationQuery

            WHERE translationQuery.id = :id
            """)
    Optional<TranslationQueryDto> findDtoById(@Param("id") Long id);

    /*
     * 這兩個是 SpeechTextGuard 在用的：判斷一段文字是不是系統自己產生過的，
     * 用來擋掉會花錢的任意合成請求。方法名稱照 Spring Data 的規則寫，
     * 查詢語句由它自動產生，不需要 @Query。
     */
    boolean existsByThaiText(String thaiText);

    boolean existsByChineseText(String chineseText);
}
