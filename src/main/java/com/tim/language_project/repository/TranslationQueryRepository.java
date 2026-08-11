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
}
