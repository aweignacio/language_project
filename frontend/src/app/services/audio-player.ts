/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  全站唯一一個真的會發出聲音的地方。
 *
 *  查詢結果、逐詞拆解、各種說法、最近清單、收藏清單 —— 五個地方都有播放鍵，
 *  全部經過這裡，共用同一個 <audio> 與同一條放大鏈。
 *
 *  ★ 為什麼要獨立成一個服務（2026-08-18）？
 *
 *    原本這段程式住在 Translation 元件裡，<audio> 也放在它的樣板上。
 *    但「最近」與「收藏」兩份清單也要能播放 —— 讓它們各自再放一個 <audio>
 *    與一條放大鏈的話，同一頁上會有兩個 AudioContext，音量倍率與
 *    「分頁切走再切回來要叫醒它」的處理都得維護兩份。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：你在收藏清單點下某一列的 ▶
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜元件呼叫 play ───────────────────────────────────────────────
 *
 *        audioPlayer.play('/audio/th/a3f9c2.mp3')
 *
 *    網址是空的就什麼都不做 —— 那代表那一句的音檔還沒產生，
 *    元件會先去合成，拿到網址之後才會再呼叫一次這裡。
 *
 * ── 第 2 步｜第一次播放時建立放大鏈 ─────────────────────────────────────
 *
 *        <audio> → MediaElementSource（把聲音接出來）
 *                → GainNode（音量乘上 AUDIO_GAIN）
 *                → destination（送到喇叭）
 *
 *    ★ 兩個坑：
 *
 *      (1) AudioContext 只能在「使用者動作之後」建立。瀏覽器不准網頁一載入
 *          就自己出聲，所以這件事放在點擊觸發的 play() 裡面做。
 *
 *      (2) createMediaElementSource 對同一個元素只能呼叫一次，
 *          第二次會直接丟例外。所以用 audioContext 有沒有值來擋，
 *          而且 <audio> 是這個服務自己持有的，不會被畫面拆掉重建。
 *
 * ── 第 3 步｜換檔案、歸零、播放 ─────────────────────────────────────────
 *
 *    同一段重播不重新載入（src 沒變就不動它），只把 currentTime 歸零。
 *
 * ── ★ 為什麼 <audio> 不放在畫面上 ───────────────────────────────────────
 *
 *  它不需要被看到（沒有 controls），而放在某個元件的樣板裡，
 *  那個元件被 @if 拆掉時元素就跟著消失，放大器會接到一個不存在的東西。
 *  用 new Audio() 自己建一個，它的生命週期就跟整個 App 一樣長。
 */

import { Service } from '@angular/core';

/**
 * 音量放大倍率。1 是原音量。
 *
 * ★ 2026-08-15 從 2 倍改回 1 倍。
 *
 *   放大是為了救 OpenAI 那些偏小聲的音檔。改用 Google 之後，後端在存檔前
 *   就把每個音檔都正規化到同一個峰值（見 WavAudio），所以每個檔案本來就
 *   一樣大聲，這裡再乘 2 只會削頂破音。
 *
 *   放大鏈本身保留著 —— 日後若要調整音量，改這個數字就好。
 */
const AUDIO_GAIN = 1;

/**
 * 全站共用的播放器。
 * @Service() 等同於舊寫法的 @Injectable({ providedIn: 'root' })，
 * 代表整個應用程式共用同一個實例。
 */
@Service()
export class AudioPlayerService {

  /**
   * 自己持有的 <audio>，不放進任何樣板。
   * 放在樣板裡的話，元件被 @if 拆掉時放大器會接到不存在的元素。
   */
  private readonly element = new Audio();

  private audioContext?: AudioContext;

  /** 播放一段音檔。網址是空的就什麼都不做（那代表音檔還沒產生）。 */
  play(audioUrl: string | null | undefined): void {
    if (!audioUrl) {
      return;
    }

    this.connectAmplifier();

    // 換了一段才重新載入，同一段重播不必再抓一次檔案。
    if (!this.element.src.endsWith(audioUrl)) {
      this.element.src = audioUrl;
    }

    this.element.currentTime = 0;
    void this.element.play();
  }

  /**
   * 把聲音改道經過放大器再送到喇叭。
   * ★ 只做一次 —— 同一個元素重複接 createMediaElementSource 會丟例外。
   */
  private connectAmplifier(): void {
    if (this.audioContext) {
      // 分頁切走再切回來時瀏覽器會把 AudioContext 暫停，這裡叫醒它。
      void this.audioContext.resume();
      return;
    }

    const context = new AudioContext();
    const gainNode = context.createGain();
    gainNode.gain.value = AUDIO_GAIN;

    context.createMediaElementSource(this.element).connect(gainNode);
    gainNode.connect(context.destination);

    this.audioContext = context;
  }
}
