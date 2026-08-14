/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  你在瀏覽器看到的整個查詢畫面。輸入框、查詢鈕、泰文、拼音、播放鍵、
 *  逐詞對照表，全部由這一個元件管。
 *
 *  一個 Angular 元件是三個檔案合起來的一件事：
 *      translation.ts    ← 這裡。狀態與行為（按下去要做什麼）
 *      translation.html  ← 長相（畫面怎麼排）
 *      translation.css   ← 樣式（顏色、字級、間距）
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：你在網頁輸入「我想喝酒」，按下查詢
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在輸入框打字 ───────────────────────────────────────────────
 *
 *    每打一個字，translation.html 上的 (input) 就呼叫下面的 onInput，
 *    把當下的值寫進 sourceText 這個「訊號」：
 *
 *        sourceText()  →  '我'  →  '我想'  →  '我想喝'  →  '我想喝酒'
 *
 *    ★ 什麼是訊號（signal）？
 *
 *      它是一個「會通知別人自己變了」的變數。
 *      讀值要加括號：sourceText()；寫值用 .set('新的值')。
 *
 *      為什麼要這麼麻煩，不用普通變數就好？
 *      因為這個專案是 zoneless 模式 —— Angular 不會再暗中監視所有變數，
 *      它只重畫「訊號說自己變了」的地方。普通變數改了，畫面不會跟著動。
 *
 *    不想自己打字的話，還沒查過東西時畫面下方會列出三句範例。
 *    這三句不是寫死的，是從 EXAMPLE_POOL 這個 15 句的題庫隨機抽的：
 *
 *        pickExamples()  →  ['我迷路了', '不要辣', '機場怎麼走']
 *                        →  examples 訊號
 *                        →  translation.html 的 @for 排出三顆膠囊
 *
 *    按「換一批」就是再抽一次，重新灌進同一個訊號。
 *    點其中一句走的是 useExample，效果跟你自己打完一模一樣：
 *
 *        useExample('我迷路了')  →  sourceText.set('我迷路了')
 *                                →  輸入框因為 [value]="sourceText()" 跟著變
 *
 *    ★ 點範例「只填字，不送出」。因為沒查過的句子要真的去呼叫 OpenAI，
 *      是會花錢的，不該讓你手滑點到就直接打一次 API。
 *
 * ── 第 2 步｜你按下「查詢」，進入 search() ────────────────────────────────
 *
 *    先把畫面清乾淨、把按鈕鎖起來：
 *
 *        loading.set(true)        → 按鈕變成「查詢中…」且按不動（防連點重複送出）
 *        result.set(null)         → 清掉上一次的結果
 *        errorMessage.set(null)   → 清掉上一次的錯誤
 *        noticeMessage.set(null)
 *
 * ── 第 3 步｜交給 TranslationService 送出請求 ─────────────────────────────
 *
 *        translationService.translate('我想喝酒').subscribe({ ... })
 *
 *    ★ .subscribe() 才是真正按下送出鍵的那一刻。
 *      在那之前只是一份「要怎麼拿資料」的食譜，網路上什麼都沒發生。
 *
 *    ★ subscribe 不會卡住畫面。程式碼會立刻往下走完，
 *      等後端回來（可能好幾秒）才回頭執行 next 或 error 裡面的東西。
 *      所以「把按鈕解鎖」這件事一定要寫在 next / error 裡面，
 *      寫在 subscribe 後面的話，按鈕會在請求還沒回來時就解鎖。
 *
 * ── 第 4 步｜成功：資料進 result 訊號，畫面自己長出來 ─────────────────────
 *
 *    後端回來的東西長這樣：
 *
 *        {
 *          sourceText: '我想喝酒',
 *          thaiText: 'ฉันอยากดื่มเหล้า',
 *          romanization: 'chan yaak duem lao',
 *          audioUrl: '/audio/a3f9c2b81e47.mp3',
 *          fromCache: true,
 *          segments: [
 *            { seqNo: 1, chineseText: '我',  thaiText: 'ฉัน',    romanization: 'chan' },
 *            { seqNo: 2, chineseText: '想',  thaiText: 'อยาก',   romanization: 'yaak' },
 *            { seqNo: 3, chineseText: '喝',  thaiText: 'ดื่ม',    romanization: 'duem' },
 *            { seqNo: 4, chineseText: '酒',  thaiText: 'เหล้า',   romanization: 'lao'  }
 *          ]
 *        }
 *
 *        result.set(那包東西)
 *              ↓
 *        translation.html 裡的 @if (result()) 這段整塊冒出來
 *
 *    fromCache 為 true 時畫面會標示「讀取自快取」——
 *    開發階段光看這個標示就知道這次有沒有花到錢。
 *
 * ── 第 5 步｜失敗：分成三種，不是三種都該嚇人 ─────────────────────────────
 *
 *    後端所有錯誤都是同一個格式（ErrorResponseDto）：
 *
 *        { "code": "INPUT_REQUIRED", "message": "輸入內容不可為空", "traceId": "a3f9c2b8" }
 *
 *    這包東西在 HttpClient 裡放在 error.error（第一層是整個 HTTP 錯誤，
 *    第二層才是後端回的內容）。message 本來就是寫給使用者看的中文，直接顯示即可。
 *
 *    ★ 三種情況要分開處理，這是這個檔案最容易寫錯的地方：
 *
 *      (1) code 是 INPUT_UNSUPPORTED_CONTENT（你查了「嘎逼」）
 *          → 這是正常結果，不是故障。AI 老實說「這我翻不出來」，
 *            而且後端刻意不寫資料庫、不沉澱單字。
 *            所以顯示成灰色提示，不要用紅色錯誤框嚇人。
 *
 *      (2) 其他有 message 的錯誤（空輸入、翻譯服務掛掉、額度不足）
 *          → 顯示紅色錯誤框，內容用後端給的 message。
 *
 *      (3) 連 message 都沒有（後端根本沒開，或網路斷了）
 *          → 這時 error.error 不是我們的格式，是瀏覽器的 ProgressEvent，
 *            硬讀 message 會拿到 undefined，畫面就會出現一塊空白的紅框。
 *            所以要自己補一句話。
 *
 * ── 第 6 步｜按下播放鍵 ───────────────────────────────────────────────────
 *
 *    ★ 為什麼不是單純一個 <audio controls> 就好？
 *
 *      因為 OpenAI 生出來的泰文音檔音量偏小，而 HTML 的 <audio> 音量
 *      最大只能到 1.0（原音量），沒辦法再放大。
 *
 *      要真的放大，得走瀏覽器的 Web Audio API：
 *
 *          <audio> 元素 → MediaElementSource（把聲音接出來）
 *                       → GainNode（音量放大 AUDIO_GAIN 倍，目前是 2）
 *                       → destination（送到喇叭）
 *
 *      接好之後，聲音就不再直接從 <audio> 流向喇叭，而是繞過放大器。
 *
 *    ★ 這裡有兩個坑：
 *
 *      (1) AudioContext 只能在「使用者動作之後」建立。
 *          瀏覽器不准網頁一載入就自己出聲，所以放在按鈕點擊裡才建立。
 *      (2) createMediaElementSource 對同一個 <audio> 元素只能呼叫一次，
 *          呼叫第二次會直接丟例外。所以 <audio> 元素刻意「永遠留在畫面上」
 *          （藏起來但不移除），而且用 audioContext 有沒有值來擋掉重複建立。
 */

import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse, TranslationResponse } from '../models/translation';
import { TranslationService } from '../services/translation-service';

/**
 * 音量放大倍率。1 是原音量，OpenAI 的泰文音檔偏小，需要放大才聽得清楚。
 * 原本設 3 倍，2026-08-14 實測音量峰值會削頂產生輕微爆音，調降為 2 倍。
 */
const AUDIO_GAIN = 2;

/** 後端在「AI 判定翻不出來」時回的錯誤碼，要當成正常結果顯示。 */
const CODE_UNTRANSLATABLE = 'INPUT_UNSUPPORTED_CONTENT';

/**
 * 範例句的題庫。空畫面每次從這裡隨機抽 EXAMPLE_COUNT 句出來顯示，
 * 所以要放得比實際顯示的數量多，抽起來才有變化。
 */
const EXAMPLE_POOL = [
  '我想喝酒',
  '廁所在哪裡',
  '這個多少錢',
  '我肚子餓了',
  '不要辣',
  '請給我一杯水',
  '我聽不懂',
  '可以便宜一點嗎',
  '請幫我叫計程車',
  '我要結帳',
  '這附近有便利商店嗎',
  '我迷路了',
  '請等一下',
  '機場怎麼走',
  '這個很好吃',
];

/** 一次顯示幾句範例。 */
const EXAMPLE_COUNT = 3;

/**
 * 從題庫隨機抽出 EXAMPLE_COUNT 句不重複的範例。
 *
 * 做法是先複製一份題庫（[...EXAMPLE_POOL] 是複製，不是直接用原本那個），
 * 每抽中一句就用 splice 從複製品裡拿走，下一輪就不可能再抽到同一句。
 * 動的是複製品，原本的 EXAMPLE_POOL 不會被抽空。
 *
 * 寫成不屬於任何類別的函式，是因為下面的欄位初始化要用它，
 * 那個時機點還不能用 this。
 */
function pickExamples(): string[] {
  const pool = [...EXAMPLE_POOL];
  const picked: string[] = [];

  while (picked.length < EXAMPLE_COUNT && pool.length > 0) {
    const index = Math.floor(Math.random() * pool.length);
    picked.push(pool.splice(index, 1)[0]);
  }

  return picked;
}

@Component({
  selector: 'app-translation',
  imports: [],
  templateUrl: './translation.html',
  styleUrl: './translation.css',
})
export class Translation {

  private readonly translationService = inject(TranslationService);

  /** 對應 translation.html 裡的 <audio #audioPlayer>，用來實際播放與接上放大器。 */
  private readonly audioPlayer = viewChild<ElementRef<HTMLAudioElement>>('audioPlayer');

  /** 輸入框當下的內容。 */
  protected readonly sourceText = signal('');

  /** 請求進行中，用來鎖住按鈕。 */
  protected readonly loading = signal(false);

  /** 這次查詢的結果，null 代表還沒查或查失敗。 */
  protected readonly result = signal<TranslationResponse | null>(null);

  /** 紅色錯誤訊息。 */
  protected readonly errorMessage = signal<string | null>(null);

  /** 灰色提示訊息，目前只有「翻不出來」會用到。 */
  protected readonly noticeMessage = signal<string | null>(null);

  /**
   * 這次要顯示的範例句。做成訊號是因為按「換一批」時整排要重畫，
   * 普通陣列改了畫面不會跟著動（zoneless 模式，見檔頭第 1 步）。
   */
  protected readonly examples = signal(pickExamples());

  /** Web Audio 的放大鏈，第一次按播放時才建立，之後重複使用。 */
  private audioContext?: AudioContext;

  /** 輸入框每打一個字就同步到 sourceText 訊號。 */
  protected onInput(event: Event): void {
    this.sourceText.set((event.target as HTMLInputElement).value);
  }

  /**
   * 點下範例句：只把文字填進輸入框，不自動送出。
   * 沒查過的句子送出去是要呼叫 OpenAI 花錢的，送不送由使用者自己按。
   */
  protected useExample(text: string): void {
    this.sourceText.set(text);
  }

  /** 按下「換一批」：重新抽三句範例。 */
  protected shuffleExamples(): void {
    this.examples.set(pickExamples());
  }

  /** 按下查詢，或在輸入框按 Enter。 */
  protected search(): void {
    if (this.loading()) {
      return;
    }

    this.loading.set(true);
    this.result.set(null);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);

    this.translationService.translate(this.sourceText()).subscribe({
      next: (response) => {
        this.result.set(response);
        this.loading.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.showError(error);
        this.loading.set(false);
      },
    });
  }

  /** 播放整句泰文，音量放大 AUDIO_GAIN 倍。 */
  protected play(): void {
    const audioUrl = this.result()?.audioUrl;
    const element = this.audioPlayer()?.nativeElement;

    if (!audioUrl || !element) {
      return;
    }

    this.connectAmplifier(element);

    // 換了一句才重新載入，同一句重播不必再抓一次檔案。
    if (!element.src.endsWith(audioUrl)) {
      element.src = audioUrl;
    }

    element.currentTime = 0;
    void element.play();
  }

  /**
   * 把 <audio> 的聲音改道經過放大器再送到喇叭。
   * 只做一次 —— 同一個元素重複接會丟例外，見檔頭第 6 步的說明。
   */
  private connectAmplifier(element: HTMLAudioElement): void {
    if (this.audioContext) {
      // 分頁切走再切回來時瀏覽器會把 AudioContext 暫停，這裡叫醒它。
      void this.audioContext.resume();
      return;
    }

    const context = new AudioContext();
    const gainNode = context.createGain();
    gainNode.gain.value = AUDIO_GAIN;

    context.createMediaElementSource(element).connect(gainNode);
    gainNode.connect(context.destination);

    this.audioContext = context;
  }

  /** 依錯誤內容決定要顯示灰色提示、紅色錯誤，還是自己補一句連線失敗。 */
  private showError(error: HttpErrorResponse): void {
    const body = error.error as ErrorResponse | null;

    if (body?.code === CODE_UNTRANSLATABLE) {
      this.noticeMessage.set(body.message);
      return;
    }

    if (body?.message) {
      this.errorMessage.set(body.message);
      return;
    }

    this.errorMessage.set('無法連線到伺服器，請確認後端是否已啟動。');
  }
}
