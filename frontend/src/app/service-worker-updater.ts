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
 *  第 2 步｜service worker 定期去比對 ngsw.json，發現有新版本
 *
 *  第 3 步｜它在背景把新版本的檔案下載好，發出 VERSION_READY
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
  }
}
