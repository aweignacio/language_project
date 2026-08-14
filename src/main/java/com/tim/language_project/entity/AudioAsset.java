package com.tim.language_project.entity;

import com.tim.language_project.enums.SpeechLanguageEnum;
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
 * 一段文字對應的音檔。整個專案的音檔都由這張表持有，其他表只存文字。
 * speechText 加 language 是唯一的，這保證同一段文字全站只會合成一次。
 */
@Entity
@Table(name = "audio_asset")
@Getter
@Setter
@NoArgsConstructor
public class AudioAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 要唸出來的文字，可能是一個詞也可能是一整句。 */
    @Column(name = "speech_text", columnDefinition = "NVARCHAR(500)", nullable = false)
    private String speechText;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 10, nullable = false)
    private SpeechLanguageEnum language;

    /** 相對於 audio 資料夾的路徑，例如 th/a1b2c3.mp3。 */
    @Column(name = "file_path", length = 100, nullable = false)
    private String filePath;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
