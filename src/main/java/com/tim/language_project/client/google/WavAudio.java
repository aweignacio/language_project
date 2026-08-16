package com.tim.language_project.client.google;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  拿到 Google 回來的音檔之後、存進硬碟之前，把它整理乾淨：
 *  剪掉前後的靜音、把音量拉到一致，順便擋掉整段都是靜音的壞檔案。
 *
 *  ★ 為什麼是 WAV 不是 mp3？
 *
 *    因為 Java 沒有內建的 mp3 解碼器 —— 拿到 mp3 我們只看得到一堆壓縮過的
 *    位元組，量不出音量、也找不到哪裡是靜音。
 *
 *    WAV（Google 那邊叫 LINEAR16）是「沒有壓縮」的格式，
 *    檔案裡就是一連串數字，每個數字代表那一瞬間的振幅：
 *
 *        0, 0, 0, 152, -300, 891, -1204, ...
 *        ↑ 這幾個接近 0 的就是靜音   ↑ 這幾個離 0 很遠的就是有聲音
 *
 *    所以剪裁和調音量都只是簡單的數學，不需要任何函式庫。
 *    代價是檔案比較大（一秒約 48KB，mp3 約 16KB），本機使用完全無所謂。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從 Google 回來的位元組到存得下去的乾淨音檔
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜GoogleSpeechClient 拿到 WAV，丟進來 ────────────────────────
 *
 *        WavAudio.tidy(wavBytes)
 *
 *    這時候的內容長這樣（實測 Chirp3-HD 唸「ขับรถ」）：
 *
 *        總長 1.54 秒，其中：
 *          前面 0.82 秒  ← 全是靜音，白等
 *          中間 0.46 秒  ← 真正的聲音
 *          後面 0.25 秒  ← 又是靜音
 *
 * ── 第 2 步｜讀懂這個檔案的格式 ─────────────────────────────────────────
 *
 *    WAV 的開頭是一段「標頭」，寫著取樣率、幾聲道、幾位元。
 *    我們要找出「聲音資料從第幾個位元組開始」——
 *    因為標頭長度不是固定的，不能寫死跳過 44 個位元組。
 *
 * ── 第 3 步｜找出聲音的頭尾 ─────────────────────────────────────────────
 *
 *    從前面往後找第一個「夠大聲」的取樣點，從後面往前找最後一個。
 *
 *        0,0,0,0,152,-300,891,...,-42,0,0,0
 *                 ↑ 這裡是頭        ↑ 這裡是尾
 *
 *    ★ 前後各留 40 毫秒的餘裕，不要剪得死緊 ——
 *      剪太貼會把氣音的起頭削掉，聽起來像被切斷。
 *
 * ── 第 4 步｜整段都沒聲音的話，當作這次失敗 ─────────────────────────────
 *
 *    回傳空的 Optional。呼叫端就不會寫入資料庫，
 *    使用者下次點還有機會重新合成。
 *
 *    ★ 這一條是 2026-08-14 的教訓：OpenAI 那邊回過一個「長度正常、
 *      音量幾乎是零」的檔案，當時只檢查「位元組是不是空的」所以沒擋住，
 *      它被永久快取，那個詞就再也沒有聲音了。
 *
 * ── 第 5 步｜把音量拉到一致 ─────────────────────────────────────────────
 *
 *    找出這段聲音的最大振幅，整段乘上一個倍率，讓最大值剛好到 90%。
 *
 *        原本峰值 5%  → 乘以 18 倍
 *        原本峰值 80% → 乘以 1.1 倍
 *
 *    ★ 為什麼留 10% 不要頂滿：頂到 100% 會削頂（破音）。
 *
 *    這一步做完之後，前端就不需要再放大音量了 —— 每個檔案都一樣大聲。
 *
 * ── 第 6 步｜重新組一個 WAV 吐回去 ──────────────────────────────────────
 *
 *    ★ 標頭裡的兩個長度欄位一定要跟著改成新的長度。
 *      忘記改的話瀏覽器會讀不出來，或播到一半就斷掉，
 *      而那種錯誤在 Java 這一端完全看不出來。
 *
 *  測試檔：src/test/java/com/tim/language_project/client/google/WavAudioTest.java
 */

import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

/**
 * WAV 音檔的剪裁與音量正規化。只做數學運算，不碰網路與硬碟。
 */
@Slf4j
public final class WavAudio {

    /**
     * 低於這個振幅就當成靜音（滿刻度的 1.5%）。
     * 太低會把底噪也當成聲音而剪不乾淨，太高會把氣音的起頭削掉。
     */
    private static final double SILENCE_THRESHOLD = 0.015;

    /** 剪裁時前後各保留的餘裕，避免起頭聽起來像被切斷。 */
    private static final int MARGIN_MILLIS = 40;

    /**
     * 補在最前面的前導靜音長度。
     *
     * ★ 這是給 iOS 暖機用的，不是給人聽的 —— 詳見 withLeadIn 的說明。
     *   調小的話 iPhone 上會開始漏掉第一個音，調大則每次播放前的空白變明顯。
     *   150 毫秒是「iOS 來得及、耳朵察覺不到」的折衷。
     */
    private static final int LEAD_IN_MILLIS = 150;

    /** 正規化的目標峰值。刻意不到 1.0，留餘裕避免削頂破音。 */
    private static final double TARGET_PEAK = 0.90;

    private WavAudio() {
    }

    /**
     * 剪掉前後靜音並把音量正規化。
     * 整段都是靜音時回傳空的 Optional —— 那代表這次合成其實失敗了。
     */
    public static Optional<byte[]> tidy(byte[] wavBytes) {
        WavLayout layout = WavLayout.parse(wavBytes);

        if (layout == null) {
            // 讀不懂就原樣放行。寧可存一個沒整理過的音檔，也不要讓使用者沒聲音聽。
            log.warn("could not parse wav header, keeping the audio untouched");
            return Optional.of(wavBytes);
        }

        short[] samples = layout.readSamples(wavBytes);

        int first = firstAudibleIndex(samples);

        if (first < 0) {
            log.warn("synthesised audio is silent from start to end, treating it as a failure");
            return Optional.empty();
        }

        int last = lastAudibleIndex(samples);
        int margin = (int) (layout.sampleRate() / 1000.0 * MARGIN_MILLIS) * layout.channels();

        int start = Math.max(0, first - margin);
        int end = Math.min(samples.length, last + margin + 1);

        short[] trimmed = new short[end - start];
        System.arraycopy(samples, start, trimmed, 0, trimmed.length);

        normalise(trimmed);

        return Optional.of(layout.rebuild(withLeadIn(trimmed, layout)));
    }

    /**
     * 在最前面補一段數位靜音。
     *
     * ★ 為什麼需要（2026-08-16 手機實測發現）：
     *
     *   iPhone 上聽起來第一個音節被吃掉，電腦上卻正常。
     *   原因是 iOS 的音訊管線從「按下播放」到「真的發出聲音」有一段啟動延遲
     *   （解碼、與系統音訊服務交握），通常 100～300 毫秒。
     *   電腦瀏覽器會先緩衝較多才開始播，所以感覺不出來。
     *
     *   上面的 MARGIN_MILLIS 是「保留原本就有的留白，最多 40 毫秒」——
     *   來源音檔前面留白不足時，實際保留的更少甚至是零，而且 40 毫秒
     *   本來就不夠 iOS 暖機。
     *
     *   這裡改成「主動補上去」，不管 Google 回來的音檔長什麼樣，
     *   都保證有一段夠長的前導靜音。
     *
     * ★ 代價是每個音檔多 150 毫秒。對單字與短句的發音範本無感，
     *   換到的是「不會漏掉第一個音」，很划算。
     */
    private static short[] withLeadIn(short[] samples, WavLayout layout) {
        int leadInSamples =
                (int) (layout.sampleRate() / 1000.0 * LEAD_IN_MILLIS) * layout.channels();

        short[] padded = new short[leadInSamples + samples.length];
        // Java 的陣列預設就是 0，而 16 位元 PCM 的 0 正是「無聲」，
        // 所以前面那段不必特別填值。
        System.arraycopy(samples, 0, padded, leadInSamples, samples.length);

        return padded;
    }

    /** 第一個「夠大聲」的取樣點，整段都是靜音時回傳 -1。 */
    private static int firstAudibleIndex(short[] samples) {
        for (int i = 0; i < samples.length; i++) {
            if (isAudible(samples[i])) {
                return i;
            }
        }

        return -1;
    }

    /** 最後一個「夠大聲」的取樣點。呼叫前已確認至少有一個。 */
    private static int lastAudibleIndex(short[] samples) {
        for (int i = samples.length - 1; i >= 0; i--) {
            if (isAudible(samples[i])) {
                return i;
            }
        }

        return samples.length - 1;
    }

    private static boolean isAudible(short sample) {
        return Math.abs(sample) / (double) Short.MAX_VALUE > SILENCE_THRESHOLD;
    }

    /**
     * 整段乘上同一個倍率，讓最大振幅剛好到 TARGET_PEAK。
     * 乘完之後要夾住上下限，否則超出 short 範圍會溢位變成反向的雜音。
     */
    private static void normalise(short[] samples) {
        int peak = 0;

        for (short sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }

        if (peak == 0) {
            return;
        }

        double gain = (TARGET_PEAK * Short.MAX_VALUE) / peak;

        for (int i = 0; i < samples.length; i++) {
            double amplified = samples[i] * gain;
            samples[i] = (short) Math.max(Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE, Math.round(amplified)));
        }
    }

    /**
     * WAV 標頭讀出來的結果：聲音資料在哪裡、取樣率多少、幾聲道。
     * 這是純資料，沒有邏輯。
     */
    private record WavLayout(byte[] header, int dataOffset, int dataSize,
                             int sampleRate, int channels) {

        /**
         * 掃過 WAV 的每一個區塊，找出 fmt 與 data 的位置。
         * ★ 不可以寫死「跳過 44 個位元組」—— 有些檔案在 fmt 之後還會夾帶別的區塊，
         *   寫死的話會把那些區塊當成聲音，播出來是一段雜音。
         */
        static WavLayout parse(byte[] bytes) {
            if (bytes.length < 12 || !"RIFF".equals(new String(bytes, 0, 4))
                    || !"WAVE".equals(new String(bytes, 8, 4))) {
                return null;
            }

            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int position = 12;
            int sampleRate = 0;
            int channels = 1;

            while (position + 8 <= bytes.length) {
                String chunkId = new String(bytes, position, 4);
                int chunkSize = buffer.getInt(position + 4);
                int contentStart = position + 8;

                if ("fmt ".equals(chunkId) && contentStart + 16 <= bytes.length) {
                    channels = buffer.getShort(contentStart + 2);
                    sampleRate = buffer.getInt(contentStart + 4);
                }

                if ("data".equals(chunkId)) {
                    int available = Math.min(chunkSize, bytes.length - contentStart);
                    return new WavLayout(bytes, contentStart, available, sampleRate, channels);
                }

                // 區塊長度是奇數時後面會補一個位元組對齊，跳過時要算進去。
                position = contentStart + chunkSize + (chunkSize % 2);
            }

            return null;
        }

        short[] readSamples(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes, dataOffset, dataSize)
                    .order(ByteOrder.LITTLE_ENDIAN);
            short[] samples = new short[dataSize / 2];

            for (int i = 0; i < samples.length; i++) {
                samples[i] = buffer.getShort();
            }

            return samples;
        }

        /**
         * 用原本的標頭配上新的聲音資料，重組一個 WAV。
         * ★ 兩個長度欄位（RIFF 的總長、data 的長度）一定要跟著改。
         */
        byte[] rebuild(short[] samples) {
            int newDataSize = samples.length * 2;
            ByteBuffer result = ByteBuffer.allocate(dataOffset + newDataSize)
                    .order(ByteOrder.LITTLE_ENDIAN);

            result.put(header, 0, dataOffset);

            for (short sample : samples) {
                result.putShort(sample);
            }

            byte[] bytes = result.array();

            // RIFF 的長度欄位：整個檔案長度減 8
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, bytes.length - 8);
            // data 區塊的長度欄位就在聲音資料的前 4 個位元組
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(dataOffset - 4, newDataSize);

            return bytes;
        }
    }
}
