package com.tim.language_project.repository;

import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.entity.Vocabulary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 累積下來的中泰單字庫資料存取。
 */
public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {

    @Query("""
            SELECT new com.tim.language_project.dto.response.VocabularyDto(
                vocabulary.id,
                vocabulary.chineseText,
                vocabulary.thaiText,
                vocabulary.romanization
            )

            FROM Vocabulary vocabulary

            WHERE vocabulary.chineseText = :chineseText
            """)
    Optional<VocabularyDto> findByChineseText(@Param("chineseText") String chineseText);

    @Query("""
            SELECT vocabulary.chineseText

            FROM Vocabulary vocabulary

            WHERE vocabulary.chineseText IN :chineseTexts
            """)
    List<String> findExistingChineseTexts(
            @Param("chineseTexts") Collection<String> chineseTexts);

    @Query("""
            SELECT new com.tim.language_project.dto.response.VocabularyDto(
                vocabulary.id,
                vocabulary.chineseText,
                vocabulary.thaiText,
                vocabulary.romanization
            )

            FROM Vocabulary vocabulary

            ORDER BY vocabulary.id DESC
            """)
    Page<VocabularyDto> findAllOrderByIdDesc(Pageable pageable);
}
