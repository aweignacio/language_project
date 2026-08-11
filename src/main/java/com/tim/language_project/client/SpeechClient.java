package com.tim.language_project.client;

import java.util.Optional;

/**
 * 把泰文轉成音檔並存起來，回傳檔名。
 * 失敗時回傳空的 Optional —— 語音出問題絕對不能連帶讓翻譯失敗，
 * 呼叫端改成存入 null 的音檔欄位即可。
 */
public interface SpeechClient {

    Optional<String> synthesize(String thaiText);
}
