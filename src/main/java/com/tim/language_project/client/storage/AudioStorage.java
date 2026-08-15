package com.tim.language_project.client.storage;

import com.tim.language_project.enums.SpeechLanguageEnum;

import java.io.InputStream;
import java.util.Optional;

/**
 * 音檔要存到哪裡。
 *
 * 這個介面存在的理由：本機把音檔存在 audio 資料夾，雲端存在 Cloud Storage，
 * 但呼叫端不應該知道這件事。與 SpeechClient 是同一種做法 ——
 * 一個介面、兩個實作、靠設定切換。
 *
 * ★ 兩個實作回傳的 filePath 格式必須完全一致（例如 th/a1b2c3d4e5f6.wav），
 *   因為那個字串會被寫進 audio_asset.file_path，兩種環境共用同一張表的語意。
 */
public interface AudioStorage {

    /**
     * 存一份音檔。
     *
     * ★ 為什麼要傳副檔名進來：兩家語音服務給的格式不一樣。
     *   Google 走 LINEAR16，經 WavAudio.tidy 處理後是 wav；
     *   OpenAI 直接回 mp3。這一層不該去猜是誰呼叫的，由呼叫端明講。
     *
     * @param language  決定放在哪個子資料夾（th / zh）
     * @param content   音檔的位元組內容
     * @param extension 副檔名，不含點，例如 wav 或 mp3
     * @return 相對路徑（例：th/a1b2c3d4e5f6.wav）；存檔失敗回傳空值
     */
    Optional<String> save(SpeechLanguageEnum language, byte[] content, String extension);

    /**
     * 開一條讀取串流。
     *
     * ★ 回傳 InputStream 而非 byte[] 是刻意的：
     *   讀成 byte[] 會把整個檔案攤在記憶體裡，同時播放多個就會疊加。
     *   串流的記憶體佔用固定於一個小緩衝區，與檔案大小、併發數無關。
     *   呼叫端負責關閉這條串流。
     *
     * @param filePath 相對路徑，格式同 save 的回傳值
     * @return 檔案不存在時回傳空值
     */
    Optional<InputStream> openStream(String filePath);
}
