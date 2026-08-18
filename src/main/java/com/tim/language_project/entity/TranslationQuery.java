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

    /**
     * 最後一次「使用者按下查詢」而命中或建立這一列的時間，「最近搜尋」清單的排序依據。
     *
     * ★ 不能用 createdAt 代替：那是第一次查的時間，快取命中時整列不動，
     *   拿它排序會排出「第一次查的順序」而不是「最近看過的順序」。
     *
     * ★ 從清單點進去還原一筆時「不會」更新這個欄位 ——
     *   否則翻一輪收藏就會把最近清單洗成另一個順序，清單在眼皮底下跳動。
     *
     * null 代表這一列早於本功能（2026-08-18 之前），不會出現在最近清單。
     */
    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;

    /**
     * 加入收藏的時間。★ null 就代表「沒有收藏」，一欄同時當旗標與排序依據。
     *
     * 不另外設一個 boolean 旗標，是因為兩個欄位就會多出
     * 「旗標是 true 但時間是 null」這種不一致狀態要處理。
     */
    @Column(name = "favorited_at")
    private LocalDateTime favoritedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
