package com.tim.language_project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Cached translation result keyed by the raw text the user submitted.
 * This is the only table that owns an audio file.
 */
@Entity
@Table(name = "translation_query")
@Getter
@Setter
@NoArgsConstructor
public class TranslationQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Raw Chinese input, trimmed before persisting. Unique. */
    @Column(name = "source_text", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String sourceText;

    @Column(name = "thai_text", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String thaiText;

    @Column(name = "romanization", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String romanization;

    /**
     * Generated audio file name. Null when speech synthesis failed —
     * the translation is still returned, only the play button is hidden.
     */
    @Column(name = "audio_file", length = 100)
    private String audioFile;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
