package com.tim.language_project.repository;

import com.tim.language_project.dto.response.AudioAssetDto;
import com.tim.language_project.entity.AudioAsset;
import com.tim.language_project.enums.SpeechLanguageEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 音檔資產的資料存取。
 */
public interface AudioAssetRepository extends JpaRepository<AudioAsset, Long> {

    @Query("""
            SELECT new com.tim.language_project.dto.response.AudioAssetDto(
                audioAsset.id,
                audioAsset.speechText,
                audioAsset.language,
                audioAsset.filePath
            )

            FROM AudioAsset audioAsset

            WHERE audioAsset.speechText = :speechText
              AND audioAsset.language = :language
            """)
    Optional<AudioAssetDto> findBySpeechTextAndLanguage(
            @Param("speechText") String speechText,
            @Param("language") SpeechLanguageEnum language);
}
