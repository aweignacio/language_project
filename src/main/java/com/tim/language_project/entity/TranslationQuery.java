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
 * 翻譯結果的快取，以使用者輸入的原始文字當作查詢的鍵。
 * 整個專案只有這張表擁有音檔。
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

    /** 使用者輸入的中文原文，寫入前會先去掉頭尾空白。不可重複。 */
    @Column(name = "source_text", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String sourceText;

    @Column(name = "thai_text", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String thaiText;

    @Column(name = "romanization", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String romanization;

    /**
     * 產生出來的音檔檔名。語音合成失敗時是 null ——
     * 翻譯結果照樣回傳，只是前端不顯示播放鍵。
     */
    @Column(name = "audio_file", length = 100)
    private String audioFile;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
