package com.tim.language_project.dto.response;

/**
 * 逐詞對照裡的其中一個詞。
 * 兩個音檔網址為 null 代表還沒產生，使用者點擊播放鍵時才會合成。
 */
public record TranslationSegmentDto(
        Integer seqNo,
        String chineseText,
        String thaiText,
        String romanization,
        String thaiAudioUrl,
        String chineseAudioUrl) {
}
