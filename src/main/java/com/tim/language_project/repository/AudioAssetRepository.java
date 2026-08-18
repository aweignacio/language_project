package com.tim.language_project.repository;

import com.tim.language_project.dto.response.AudioAssetDto;
import com.tim.language_project.entity.AudioAsset;
import com.tim.language_project.enums.SpeechLanguageEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    /*
     * ★ 批次版。清單畫面用這支，不要在迴圈裡呼叫上面那支單筆的。
     *
     *   收藏一百筆、每列各查一次就是一百趟資料庫往返（N+1）。
     *   資料少的時候完全看不出來，這正是它難查的原因。
     *
     * 查不到的文字不會出現在結果裡，呼叫端據此判斷「這一句還沒有音檔」。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.AudioAssetDto(
                audioAsset.id,
                audioAsset.speechText,
                audioAsset.language,
                audioAsset.filePath
            )

            FROM AudioAsset audioAsset

            WHERE audioAsset.speechText IN :speechTexts
              AND audioAsset.language = :language
            """)
    List<AudioAssetDto> findBySpeechTextInAndLanguage(
            @Param("speechTexts") Collection<String> speechTexts,
            @Param("language") SpeechLanguageEnum language);
}
