package com.tim.language_project.repository;

import com.tim.language_project.dto.response.TranslationQueryDto;
import com.tim.language_project.entity.TranslationQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 查詢結果快取的資料存取。
 */
public interface TranslationQueryRepository extends JpaRepository<TranslationQuery, Long> {

    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationQueryDto(
                translationQuery.id,
                translationQuery.sourceText,
                translationQuery.thaiText,
                translationQuery.romanization,
                translationQuery.audioFile
            )

            FROM TranslationQuery translationQuery

            WHERE translationQuery.sourceText = :sourceText
            """)
    Optional<TranslationQueryDto> findBySourceText(@Param("sourceText") String sourceText);

    /*
     * 這兩個是 SpeechTextGuard 在用的：判斷一段文字是不是系統自己產生過的，
     * 用來擋掉會花錢的任意合成請求。方法名稱照 Spring Data 的規則寫，
     * 查詢語句由它自動產生，不需要 @Query。
     */
    boolean existsByThaiText(String thaiText);

    boolean existsByChineseText(String chineseText);
}
