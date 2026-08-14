package com.tim.language_project.client;

import com.tim.language_project.enums.SpeechLanguageEnum;

import java.util.Optional;

/**
 * 把一段文字轉成音檔並存起來，回傳相對於 audio 資料夾的路徑（例如 th/a1b2c3.mp3）。
 * 失敗時回傳空的 Optional —— 語音出問題絕對不能連帶讓翻譯失敗，
 * 呼叫端當作「這段文字目前沒有音檔」處理即可。
 */
public interface SpeechClient {

    Optional<String> synthesize(String speechText, SpeechLanguageEnum language);
}
