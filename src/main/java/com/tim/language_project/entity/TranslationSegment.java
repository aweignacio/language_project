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
 * One word of the segmentation of a query. Records how a specific sentence was
 * split, so the same word may appear across many queries.
 * The parent is referenced by a plain identifier column, not a JPA association.
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

    /** Display order, starting from 1. */
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
