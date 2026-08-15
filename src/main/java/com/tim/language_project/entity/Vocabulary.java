package com.tim.language_project.entity;

import com.tim.language_project.enums.GenderUsageEnum;
import com.tim.language_project.enums.PolitenessEnum;
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
 * 中泰對照的一個「說法」。同一個中文詞可以有多列 ——
 * 例如「我」會有 ผม、ฉัน、กู 三列，這是預期行為不是資料重複。
 * 這裡不存音檔，全站的音檔一律由 audio_asset 持有。
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

    @Column(name = "chinese_text", length = 50, nullable = false)
    private String chineseText;

    @Column(name = "thai_text", length = 100, nullable = false)
    private String thaiText;

    @Column(name = "romanization", length = 100, nullable = false)
    private String romanization;

    /**
     * 這個說法適合哪種性別使用。
     * 從句子拆解沉澱下來的詞沒有這項資訊，為 null，
     * 日後單獨查詢該詞時才會補上。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender_usage", length = 10)
    private GenderUsageEnum genderUsage;

    /** 禮貌程度。同樣可能為 null。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "politeness", length = 10)
    private PolitenessEnum politeness;

    /** 中文說明，例如「男生自稱，正式或對不熟的人使用」。 */
    @Column(name = "note", length = 200)
    private String note;

    /** 維持第一次寫入時的值；單字已存在就不會再更新這個欄位。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20, nullable = false)
    private VocabularySourceTypeEnum sourceType;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
