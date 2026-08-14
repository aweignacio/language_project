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

    /*
     * ★ 回傳的是 List 不是 Optional。
     *   一個中文詞在這張表可能佔好幾列（「我」有 ผม / ฉัน / กู 三列），
     *   那就是「多重說法」這個功能的全部意義。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.VocabularyDto(
                vocabulary.id,
                vocabulary.chineseText,
                vocabulary.thaiText,
                vocabulary.romanization,
                vocabulary.genderUsage,
                vocabulary.politeness,
                vocabulary.note
            )

            FROM Vocabulary vocabulary

            WHERE vocabulary.chineseText = :chineseText

            ORDER BY vocabulary.id
            """)
    List<VocabularyDto> findByChineseText(@Param("chineseText") String chineseText);

    /*
     * ★ 撈出「整個實體」而不是只撈中文字。
     *   因為寫入時要判斷的不只是「這個說法在不在」，
     *   還要在它已經存在、但性別／禮貌／說明是 null 時把那三欄補上
     *   （合併規則見 TranslationPersistenceService）。只撈字串就做不到這件事。
     */
    @Query("""
            SELECT vocabulary

            FROM Vocabulary vocabulary

            WHERE vocabulary.chineseText IN :chineseTexts
            """)
    List<Vocabulary> findAllByChineseTextIn(
            @Param("chineseTexts") Collection<String> chineseTexts);

    @Query("""
            SELECT new com.tim.language_project.dto.response.VocabularyDto(
                vocabulary.id,
                vocabulary.chineseText,
                vocabulary.thaiText,
                vocabulary.romanization,
                vocabulary.genderUsage,
                vocabulary.politeness,
                vocabulary.note
            )

            FROM Vocabulary vocabulary

            ORDER BY vocabulary.id DESC
            """)
    Page<VocabularyDto> findAllOrderByIdDesc(Pageable pageable);

    /*
     * 這兩個是 SpeechTextGuard 在用的：判斷一段文字是不是系統自己產生過的，
     * 用來擋掉會花錢的任意合成請求。方法名稱照 Spring Data 的規則寫，
     * 查詢語句由它自動產生，不需要 @Query。
     */
    boolean existsByThaiText(String thaiText);

    boolean existsByChineseText(String chineseText);
}
