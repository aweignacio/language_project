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
    @Column(name = "source_text", length = 100, nullable = false)
    private String sourceText;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 20, nullable = false)
    private TranslationDirectionEnum direction;

    /** 泰翻中沒有性別概念，該方向為 null。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private SpeakerGenderEnum gender;

    /** 這句話的中文面。sourceText 必定與這一欄或 thaiText 其中之一相同。 */
    @Column(name = "chinese_text", length = 500, nullable = false)
    private String chineseText;

    @Column(name = "thai_text", length = 500, nullable = false)
    private String thaiText;

    @Column(name = "romanization", length = 500, nullable = false)
    private String romanization;

    /**
     * 這筆查的是「一個詞」還是「一句話」，由模型在翻譯那一次順便判斷。
     * 前端拿它決定「各種說法」那顆按鈕要不要出現。
     *
     * ★ 允許 null，而且 null 有意義：
     *   一是模型沒給這個欄位，二是這一列比這個欄位還早存進來（2026-08-17 之前的舊資料）。
     *   兩種情況都當作「不知道」，按鈕照常顯示 —— 藏錯了使用者不會發現，
     *   多顯示一顆只是白按一下。
     */
    @Column(name = "is_word")
    private Boolean isWord;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
