package com.tim.language_project.entity;

import com.tim.language_project.enums.VocabularySourceTypeEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 中泰對照的單字，由每次查詢的拆解結果慢慢累積而成。
 * 這裡不存音檔 —— 只有查詢快取那張表擁有音檔。
 */
@Entity
@Table(name = "vocabulary")
@Getter
@Setter
@NoArgsConstructor
public class Vocabulary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "chinese_text", columnDefinition = "NVARCHAR(50)", nullable = false)
    private String chineseText;

    @Column(name = "thai_text", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String thaiText;

    @Column(name = "romanization", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String romanization;

    /** 維持第一次寫入時的值；單字已存在就不會再更新這個欄位。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20, nullable = false)
    private VocabularySourceTypeEnum sourceType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
