package com.tim.language_project.entity;

import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
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
 * 翻譯結果的快取。查詢的鍵是「輸入原文＋方向＋性別」三者的組合。
 * 音檔不在這裡 —— 全站的音檔一律由 audio_asset 持有。
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

    /** 使用者輸入的原文，寫入前會先去掉頭尾空白。 */
    @Column(name = "source_text", columnDefinition = "NVARCHAR(100)", nullable = false)
    private String sourceText;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 20, nullable = false)
    private TranslationDirectionEnum direction;

    /** 泰翻中沒有性別概念，該方向為 null。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private SpeakerGenderEnum gender;

    /** 這句話的中文面。sourceText 必定與這一欄或 thaiText 其中之一相同。 */
    @Column(name = "chinese_text", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String chineseText;

    @Column(name = "thai_text", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String thaiText;

    @Column(name = "romanization", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String romanization;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
