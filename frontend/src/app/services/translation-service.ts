/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  整個前端只有這個檔案知道「後端的網址長什麼樣子」。
 *  畫面元件（Translation）只會說「幫我翻這句」，不必知道那是一個 POST、
 *  網址是什麼、JSON 的鍵叫什麼。
 *
 *  這樣分的好處：日後後端網址改版（v1 → v2），只要改這一個檔案，
 *  畫面一行都不用動。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你按下查詢到資料回來
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜Translation 元件呼叫 translate ────────────────────────────────
 *
 *        translationService.translate('我想喝酒')
 *
 * ── 第 2 步｜HttpClient 把它變成一個真正的 HTTP 請求 ───────────────────────
 *
 *        POST /api/v1/translations
 *        Content-Type: application/json
 *
 *        { "sourceText": "我想喝酒" }
 *
 *    ★ 網址寫的是相對路徑 /api/...，不是 http://localhost:8080/...
 *
 *      為什麼？寫死主機的話，換一台電腦、或哪天真的上線，就得回來改程式。
 *      相對路徑代表「打我自己這台」，也就是 localhost:4200 —— 但後端在 8080。
 *      中間那一段由 proxy.conf.json 補上：Angular 的開發伺服器收到
 *      /api 開頭的請求，會在背後幫你轉發到 8080，瀏覽器全程以為是同一個來源，
 *      所以不會有 CORS 的問題，後端也一行都不用改。
 *
 * ── 第 3 步｜回傳 Observable，此時「還沒有真的送出」 ───────────────────────
 *
 *    ★ 這是 Angular 最容易搞混的地方。
 *
 *      這個方法回傳的不是資料，是一份「食譜」（Observable）——
 *      寫著「要怎麼去拿資料」，但還沒開火。
 *      真正送出請求的瞬間，是元件那邊呼叫 .subscribe() 的時候。
 *
 *      所以只呼叫 translate() 而不 subscribe，網路上什麼事都不會發生。
 *
 * ── 第 4 步｜後端回應，HttpClient 自動把 JSON 轉成物件 ─────────────────────
 *
 *        HTTP 200（讀快取）或 201（新建立）—— 兩種都算成功，不用分開處理
 *
 *        {
 *          "sourceText": "我想喝酒",
 *          "thaiText": "ฉันอยากดื่มเหล้า",
 *          "romanization": "chan yaak duem lao",
 *          "audioUrl": "/audio/a3f9c2b81e47.mp3",
 *          "fromCache": true,
 *          "segments": [ { "seqNo": 1, "chineseText": "我", ... }, ... ]
 *        }
 *
 *              ↓ post<TranslationResponse> 的角括號就是在說「這包是這個形狀」
 *
 *        元件拿到的是一個 TranslationResponse 物件，可以直接 .thaiText 取用。
 *
 *    ★ 角括號只是編譯期的承諾，執行期沒有任何檢查。
 *      如果後端真的改了欄位名稱，TypeScript 不會抱怨，你會拿到 undefined。
 *
 * ── 第 5 步｜出錯的話呢？ ─────────────────────────────────────────────────
 *
 *    這個檔案「完全不處理錯誤」，這是刻意的。
 *    HttpClient 會把非 2xx 的回應轉成錯誤往下丟，由元件的 subscribe
 *    第二個參數（error）接住，因為只有元件知道該怎麼顯示給使用者看。
 */

import { Service, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TranslationResponse } from '../models/translation';

/**
 * 包裝翻譯 API 的呼叫。
 * @Service() 等同於舊寫法的 @Injectable({ providedIn: 'root' })，
 * 代表整個應用程式共用同一個實例，不必手動註冊。
 */
@Service()
export class TranslationService {

  private readonly http = inject(HttpClient);

  /**
   * 送出一次翻譯查詢。
   * @param sourceText 使用者輸入的中文，例如「我想喝酒」
   */
  translate(sourceText: string): Observable<TranslationResponse> {
    return this.http.post<TranslationResponse>('/api/v1/translations', { sourceText });
  }
}
