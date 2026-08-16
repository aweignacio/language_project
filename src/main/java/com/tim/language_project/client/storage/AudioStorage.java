package com.tim.language_project.client.storage;

import com.tim.language_project.enums.SpeechLanguageEnum;

import org.springframework.core.io.Resource;
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
     * 取出一份音檔。
     *
     * ★ 回傳 Resource 而不是 InputStream，這一點是被 iOS 逼出來的（2026-08-16）：
     *
     *   iOS Safari 播放音訊時，一定會先送一個「只要前面一小段」的請求
     *   （HTTP Range）。伺服器若不理會、把整包丟回去，iOS 就拒絕播放，
     *   而且不會有任何錯誤訊息 —— 手機上就是按了沒聲音，電腦上卻正常。
     *
     *   Spring 有能力自動處理 Range 並回 206，但前提是拿得到一個
     *   「知道自己多長、而且可以重複讀取」的資源。InputStreamResource
     *   兩者都做不到（長度未知、讀過就沒了），所以 Range 支援不起來。
     *
     *   ★ 原本選 InputStream 是為了「不要把整個檔案載進記憶體」。
     *     那個顧慮在這個專案不成立 —— 音檔只有 20～80 KB，
     *     而且雲端實作本來就是整包讀取。真正需要串流的是長篇影音，不是單字發音。
     *
     * @param filePath 相對路徑，格式同 save 的回傳值
     * @return 檔案不存在時回傳空值
     */
    Optional<Resource> load(String filePath);
}
