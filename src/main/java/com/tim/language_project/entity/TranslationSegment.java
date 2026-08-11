package com.tim.language_project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 一筆查詢拆解後的其中一個詞。記錄的是「這一句話當初怎麼拆」，
 * 所以同一個詞可能出現在很多筆查詢底下。
 * 父層只用單純的 id 欄位參照，不使用 JPA 關聯註解。
 */
@Entity
@Table(name = "translation_segment")
@IdClass(TranslationSegmentId.class)
@Getter
@Setter
@NoArgsConstructor
public class TranslationSegment {

    @Id
    @Column(name = "query_id")
    private Long queryId;

    /** 顯示順序，從 1 開始。 */
    @Id
    @Column(name = "seq_no")
    private Integer seqNo;

    @Column(name = "chinese_text", columnDefinition = "NVARCHAR(50)", nullable = false)
    private String chineseText;

    @Column(name = "thai_text", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String thaiText;

    @Column(name = "romanization", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String romanization;
}
