package com.tim.language_project.client.storage;

/**
 * 副檔名對應到 HTTP 的 Content-Type。
 *
 * 為什麼需要它：瀏覽器是靠 Content-Type 決定「這是什麼、能不能播」的。
 * 給錯的話，有些瀏覽器會變成下載檔案而不是播放。
 *
 * ★ 兩家語音服務給的格式不同（Google 是 wav、OpenAI 是 mp3），
 *   而且兩個地方都要用到這份對照 ——
 *   上傳到 Cloud Storage 時標記 blob 的型別，
 *   以及 AudioFileController 回應時的標頭。兩處必須一致，故集中在這裡。
 */
public final class AudioContentType {

    private static final String WAV = "audio/wav";

    private static final String MPEG = "audio/mpeg";

    private AudioContentType() {
    }

    /** 依副檔名（不含點）給出 Content-Type，未知者一律當 wav。 */
    public static String of(String extension) {
        return "mp3".equalsIgnoreCase(extension) ? MPEG : WAV;
    }

    /** 依完整檔名或路徑給出 Content-Type，例如 th/a1b2c3.mp3。 */
    public static String ofPath(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');

        return dotIndex < 0
                ? WAV
                : of(filePath.substring(dotIndex + 1));
    }
}
