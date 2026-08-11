package com.tim.language_project.repository;

import com.tim.language_project.dto.response.TranslationSegmentDto;
import com.tim.language_project.entity.TranslationSegment;
import com.tim.language_project.entity.TranslationSegmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Data access for per-query word segmentation.
 */
public interface TranslationSegmentRepository
        extends JpaRepository<TranslationSegment, TranslationSegmentId> {

    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationSegmentDto(
                translationSegment.seqNo,
                translationSegment.chineseText,
                translationSegment.thaiText,
                translationSegment.romanization
            )

            FROM TranslationSegment translationSegment

            WHERE translationSegment.queryId = :queryId

            ORDER BY translationSegment.seqNo
            """)
    List<TranslationSegmentDto> findByQueryIdOrderBySeqNo(@Param("queryId") Long queryId);
}
