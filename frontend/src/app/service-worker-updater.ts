import { inject, Injectable } from '@angular/core';
import { SwUpdate } from '@angular/service-worker';
import { filter } from 'rxjs';

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  部署新版本之後，讓已經開著的 App 自己換到新版，而不是壞掉。
 *
 * ── 為什麼需要它（2026-08-16 連續被咬三次）───────────────────────────
 *
 *  service worker 會把整個前端程式快取在使用者的裝置上，而且優先用快取的，
 *  不會主動去抓新的。問題出在「每次建置，檔名都會變」：
 *
 *      main-CJ63TBXR.js   →   main-K7Xm2Qp1.js
 *
 *  舊的 service worker 抓著舊的 index.html，而那份 index.html 指向的
 *  main-CJ63TBXR.js 在新版伺服器上已經不存在了 —— App 載入失敗。
 *
 *  ★ 症狀是「部署之後，所有已經用過的裝置都打不開」，
 *    而且使用者只能自己去開發者工具「停止註冊 service worker」才能救回來。
 *    一般使用者根本不會做那件事。
 *
 *  Angular 的 service worker 其實偵測得到有新版本，但預設只是「準備好」，
 *  要等下一次完整開啟頁面才會換 —— 而那時候 App 早就壞了。
 *
 * ── 流程：你部署了新版本 ───────────────────────────────────────────────
 *
 *  第 1 步｜使用者的 App 還開著，用的是舊版
 *
 *  第 2 步｜★ 我們主動去問「有沒有新版」——checkForUpdate()
 *
 *      ★★ 這一步以前不存在，而它就是 2026-08-17 手機壞掉的原因。
 *
 *      舊版註解寫「service worker 定期去比對 ngsw.json」——那是錯的。
 *      Angular 的 SwUpdate 只在「service worker 註冊的那一刻」問一次，
 *      之後除非你自己開口問，它一輩子都不會再問。
 *
 *      在電腦上看不出來，因為你每次都是重新開分頁 = 重新註冊 = 問一次。
 *      但手機上把 App 加到主畫面之後，從背景切回來只是「回到前景」，
 *      不是重新載入 —— 那份 App 可以掛在那裡好幾天，一次都沒問過。
 *
 *      症狀（真的發生過）：
 *          電腦上是新版，手機上查完只有整句和播放鍵，
 *          逐詞拆解與各種說法的按鈕整組不見。
 *      因為手機跑的是舊版前端、後端卻已經是新版：舊版前端直接讀
 *      translation.variants，新版後端不給了，讀到 undefined 就當場炸掉，
 *      炸點之後的畫面全部沒長出來。
 *
 *      所以現在有兩個時機會主動問：
 *          ① 每次 App 回到前景（visibilitychange）★ 手機靠的是這個
 *          ② 開著不動的話每 30 分鐘問一次
 *
 *  第 3 步｜真的有新版時，它在背景把檔案下載好，發出 VERSION_READY
 *
 *  第 4 步｜★ 這裡攔下來，直接重新載入頁面
 *
 *      使用者會看到畫面閃一下，然後就是新版了。
 *      比起「打不開，要自己去開發者工具清快取」，這個代價很小。
 *
 * ── 另一種情況：已經救不回來 ───────────────────────────────────────────
 *
 *  如果快取已經壞到 service worker 自己都修不了（unrecoverable），
 *  它會發出通知。那時只能整個重新載入，讓瀏覽器重新抓一份乾淨的。
 * ══════════════════════════════════════════════════════════════════════════
 */

/**
 * App 一直開著沒動的話，隔多久主動問一次有沒有新版。
 * 30 分鐘。問一次只是抓一份很小的 ngsw.json，成本可以忽略。
 */
const CHECK_INTERVAL_MS = 30 * 60 * 1000;

@Injectable({ providedIn: 'root' })
export class ServiceWorkerUpdater {

  private readonly updates = inject(SwUpdate);

  /**
   * 開始監聽版本更新。由 app.config.ts 在啟動時呼叫一次。
   *
   * service worker 沒有啟用時（本機開發）isEnabled 是 false，直接跳過。
   */
  start(): void {
    if (!this.updates.isEnabled) {
      return;
    }

    this.updates.versionUpdates
      .pipe(filter(event => event.type === 'VERSION_READY'))
      .subscribe(() => location.reload());

    // 快取壞到修不好的情況。這裡不用 filter，因為這個 Observable
    // 本身就只會在「已經沒救」時發出事件。
    this.updates.unrecoverable.subscribe(() => location.reload());

    /*
     * ★ 手機更新靠的是這一行。
     *
     * 手機上的 App 從背景切回前景時，頁面「沒有」重新載入，
     * 所以不會重新註冊 service worker，也就不會有人去問有沒有新版。
     * 這裡自己補上那一問。
     *
     * 用 visibilitychange 而不是 focus：把 App 切走再切回來一定會觸發
     * visibilitychange，但手機上不一定會有 focus 事件。
     */
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        this.checkForUpdate();
      }
    });

    /*
     * 一直開在前景不動的情況（例如放在電腦上開一整天），
     * 上面那個事件永遠不會發生，所以另外掛一個定時的。
     *
     * 這個 setInterval 刻意不清掉 —— 這個服務是整個 App 共用的單例，
     * 活得跟 App 一樣久，它結束的時候頁面本來就要關了。
     */
    setInterval(() => this.checkForUpdate(), CHECK_INTERVAL_MS);
  }

  /**
   * 問 service worker「伺服器上有沒有新版」。
   *
   * 有新版的話它會在背景下載，下載完才發 VERSION_READY，
   * 由上面那段訂閱負責重新載入頁面 —— 這裡不需要處理結果。
   *
   * 沒網路時這個 Promise 會失敗，忽略即可：
   * 下一次回到前景或下一個 30 分鐘會再問一次。
   */
  private checkForUpdate(): void {
    this.updates.checkForUpdate().catch(() => undefined);
  }
}
