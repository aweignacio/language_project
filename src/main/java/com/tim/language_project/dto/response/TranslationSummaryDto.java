package com.tim.language_project.dto.response;

import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;

/**
 * 「最近搜尋」與「收藏」清單裡的一列。
 *
 * 刻意不重用 TranslationResponseDto：那個 record 上的 fromCache 與 isWord
 * 在清單的情境下沒有意義，硬塞會讓前端不知道能不能信任它們。
 *
 * thaiAudioUrl 為 null 代表音檔還沒產生，前端顯示成灰色的播放鍵，點了才合成。
 */
public record TranslationSummaryDto(
        Long queryId,
        String chineseText,
        String thaiText,
        String romanization,
        TranslationDirectionEnum direction,
        SpeakerGenderEnum gender,
        String thaiAudioUrl,
        boolean favorited) {
}
