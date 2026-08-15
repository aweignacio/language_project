package com.tim.language_project.client.google;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  WavAudio 負責在音檔存檔之前做兩件事：剪掉前後的靜音、把音量拉到一致。
 *
 *  為什麼需要它（這兩件事都是實測踩出來的）：
 *
 *  ① 前置靜音
 *     Google 的 Chirp3-HD 是生成式的，每次回來的前置留白都不一樣。
 *     2026-08-15 實測同一個聲音同一個詞：一次留白 0.82 秒、一次 0.32 秒。
 *     不剪的話，你點播放鍵要等快一秒才出聲，而且每次等的長度還不一樣。
 *
 *  ② 音量不一致
 *     不同次合成的音量差很多。前端本來用「統一放大兩倍」硬撐，
 *     但那對本來就大聲的檔案會破音。在這裡把每個檔案都正規化到同一個峰值，
 *     前端就不需要猜要放大幾倍。
 *
 *  ③ 順帶擋掉「整段都是靜音」的壞檔案
 *     ★ 這一條是從 OpenAI 那邊學到的教訓：2026-08-14 遇過一次 tts-1 回了一個
 *       長度正常、但音量幾乎是零的檔案。當時的檢查只看「位元組是不是空的」，
 *       所以它通過了、被存起來、被永久快取，那個詞就再也沒有聲音。
 *       現在只要剪完之後什麼都不剩，就當作這次合成失敗。
 *
 * ── 哪些東西被換成假的 ──────────────────────────────────────────────────
 *
 *  一個都沒有。WavAudio 只是對一堆位元組做數學運算，不連網路也不碰硬碟。
 *  測試資料是這個檔案自己組出來的 WAV（見最下面的 buildWav）。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  測試一  前後有靜音   → 要被剪掉，而且中間的聲音不可以被剪到
 *  測試二  整段都是靜音 → 要回傳空的 Optional（★ 防止無聲檔被永久快取）
 *  測試三  音量很小     → 正規化後峰值要接近目標值
 *  測試四  剪完的檔案   → 還要是一個瀏覽器讀得懂的合法 WAV
 */

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WavAudioTest {

    private static final int SAMPLE_RATE = 24000;

    /*
     * ═══ 測試一：前後的靜音要剪掉，中間的聲音不可以少 ═══════════════════
     */
    @Test
    @DisplayName("前後的靜音應被剪掉，中間的聲音完整保留")
    void shouldTrimLeadingAndTrailingSilence() {
        // 0.5 秒靜音 ＋ 0.2 秒聲音 ＋ 0.4 秒靜音
        short[] samples = new short[(int) (SAMPLE_RATE * 1.1)];
        int soundStart = (int) (SAMPLE_RATE * 0.5);
        int soundEnd = (int) (SAMPLE_RATE * 0.7);

        for (int i = soundStart; i < soundEnd; i++) {
            samples[i] = (short) (i % 2 == 0 ? 8000 : -8000);
        }

        Optional<byte[]> tidied = WavAudio.tidy(buildWav(samples));

        assertThat(tidied).isPresent();

        double seconds = durationSeconds(tidied.get());

        // 我主張：長度剩下「聲音那 0.2 秒」再加上前後各留一點點餘裕，
        // 不會是原本的 1.1 秒，也不會短到把聲音本身剪掉。
        assertThat(seconds).isBetween(0.2, 0.4);
    }

    /*
     * ═══ 測試二：整段都是靜音要當成失敗 ═════════════════════════════════
     *
     * ★ 這一條守的是「壞掉的音檔不可以被永久快取」。
     *   回空的 Optional，AudioAssetService 就不會寫入資料庫，
     *   使用者下次點還有機會重新合成。
     */
    @Test
    @DisplayName("整段都是靜音時應回傳空的 Optional")
    void shouldRejectCompletelySilentAudio() {
        short[] samples = new short[SAMPLE_RATE];

        // 全部留在 0 附近，只有極小的雜訊
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (i % 2 == 0 ? 3 : -3);
        }

        assertThat(WavAudio.tidy(buildWav(samples))).isEmpty();
    }

    /*
     * ═══ 測試三：音量要被拉到一致 ═══════════════════════════════════════
     */
    @Test
    @DisplayName("音量偏小的音檔應被正規化到接近目標峰值")
    void shouldNormaliseQuietAudio() {
        short[] samples = new short[SAMPLE_RATE];

        // 峰值只有滿刻度的 5% 左右，很小聲但聽得見
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (i % 2 == 0 ? 1600 : -1600);
        }

        Optional<byte[]> tidied = WavAudio.tidy(buildWav(samples));

        assertThat(tidied).isPresent();

        double peak = peakRatio(tidied.get());

        // 我主張：拉到接近滿刻度，但刻意留一點餘裕不要頂到 1.0（會破音）
        assertThat(peak).isBetween(0.85, 0.98);
    }

    /*
     * ═══ 測試四：吐出來的還要是合法的 WAV ═══════════════════════════════
     *
     * 剪裁時如果忘了同步更新標頭裡的長度欄位，瀏覽器會讀不出來或播到一半斷掉，
     * 而那種錯誤在程式這一端完全看不出來。
     */
    @Test
    @DisplayName("處理後仍應是標頭正確的合法 WAV")
    void shouldProduceValidWavHeader() {
        short[] samples = new short[SAMPLE_RATE];

        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (i % 2 == 0 ? 8000 : -8000);
        }

        byte[] result = WavAudio.tidy(buildWav(samples)).orElseThrow();

        assertThat(new String(result, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(result, 8, 4)).isEqualTo("WAVE");

        // RIFF 的長度欄位要等於「整個檔案長度減 8」
        int riffSize = ByteBuffer.wrap(result, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        assertThat(riffSize).isEqualTo(result.length - 8);
    }

    /**
     * 組一個 16 位元、單聲道的 WAV，內容就是傳進來的取樣點。
     * 這是測試用的假資料來源，格式跟 Google 回傳的 LINEAR16 一致。
     */
    private byte[] buildWav(short[] samples) {
        int dataSize = samples.length * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);

        buffer.put("RIFF".getBytes());
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);
        buffer.putShort((short) 1);              // PCM
        buffer.putShort((short) 1);              // 單聲道
        buffer.putInt(SAMPLE_RATE);
        buffer.putInt(SAMPLE_RATE * 2);          // byteRate
        buffer.putShort((short) 2);              // blockAlign
        buffer.putShort((short) 16);             // bitsPerSample
        buffer.put("data".getBytes());
        buffer.putInt(dataSize);

        for (short sample : samples) {
            buffer.putShort(sample);
        }

        return buffer.array();
    }

    /** 從結果的 WAV 算出秒數。 */
    private double durationSeconds(byte[] wav) {
        int dataSize = ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return (dataSize / 2.0) / SAMPLE_RATE;
    }

    /** 從結果的 WAV 算出峰值佔滿刻度的比例。 */
    private double peakRatio(byte[] wav) {
        ByteBuffer buffer = ByteBuffer.wrap(wav, 44, wav.length - 44).order(ByteOrder.LITTLE_ENDIAN);
        int peak = 0;

        while (buffer.remaining() >= 2) {
            peak = Math.max(peak, Math.abs(buffer.getShort()));
        }

        return peak / (double) Short.MAX_VALUE;
    }
}
