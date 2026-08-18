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
 *        translationService.translate('我想喝酒', 'MALE')
 *                                                  ↑ 使用者選的性別
 *
 * ── 第 2 步｜HttpClient 把它變成一個真正的 HTTP 請求 ───────────────────────
 *
 *        POST /api/v1/translations
 *        Content-Type: application/json
 *
 *        { "sourceText": "我想喝酒", "gender": "MALE" }
 *
 *    ★ gender 每次都要送。泰文的自稱與句尾助詞分性別
 *      （男 ผม/ครับ、女 ฉัน/ค่ะ），同一句中文的男版與女版是兩句不同的泰文。
 *      輸入泰文時後端會忽略它 —— 泰翻中沒有性別概念。
 *
 *    ★ 「方向」不用送。你打中文還是泰文，後端自己看得出來。
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
 *          "queryId": 137,
 *          "sourceText": "我想喝酒",
 *          "direction": "ZH_TO_TH",
 *          "gender": "MALE",
 *          "chineseText": "我想喝酒",
 *          "thaiText": "ผมอยากดื่มเหล้าครับ",
 *          "romanization": "pǒm yàak dùuem lâo khráp",
 *          "thaiAudioUrl": "/audio/th/a3f9c2b81e47.mp3",
 *          "chineseAudioUrl": null,
 *          "fromCache": true
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
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  另一支 API：合成音檔（synthesize）
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  逐詞對照的播放鍵是灰色時（thaiAudioUrl 為 null），點下去就是打這支：
 *
 *        POST /api/v1/audio
 *        { "speechText": "ดื่ม", "language": "TH" }
 *
 *        → { "audioUrl": "/audio/th/d4e5f6.mp3" }
 *
 *  ★ 後端會先查資料庫，沒有才真的合成。所以同一個詞點第二次不會再花錢，
 *    而且那個詞之後出現在「任何句子裡」都直接是亮的。
 *
 *  ★ 為什麼不在查詢時就把逐詞音檔全做好？
 *    一句話拆成四五個詞，每個都生要多打四五次 OpenAI、多等好幾秒，
 *    而那些詞你未必想聽。改成「想聽哪個就點哪個」。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  ★ 另外兩支 API：逐詞拆解與各種說法（2026-08-16 新增）
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  跟上面的音檔是同一套思路，只是這次省的不是幾秒，是二十幾秒。
 *
 *  以前一次翻譯就要模型把翻譯、拼音、逐詞拆解、各種說法全部產出來，
 *  實測平均 867 個 token。模型是一個 token 一個 token 吐的，
 *  以每秒約 40 個計算，光是產出就要 22 秒 —— 這就是「第一次查詢好慢」的真正原因。
 *
 *  ★ 而你按下查詢的那一刻，只想看到泰文和拼音。
 *
 *  所以拆成三支，後兩支等你在畫面上點下去才打：
 *
 *        POST /api/v1/translations/137/segments   →  [ {逐詞}, {逐詞}, ... ]
 *        POST /api/v1/translations/137/variants   →  [ {說法}, {說法}, ... ]
 *
 *  ★ 為什麼是 POST 不是 GET？因為這兩支「可能會呼叫 OpenAI 並寫資料庫」。
 *    GET 在規範上代表只讀不寫，瀏覽器和中間的快取都會依這個前提行事，
 *    用 GET 的話重新整理或預先載入都可能默默多花一次錢。
 *
 *  ★ 137 是哪來的？就是第 4 步回應裡的 queryId。
 */

import { Service, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AudioResponse,
  SpeakerGender,
  SpeechLanguage,
  TranslationResponse,
  TranslationSegment,
  TranslationSummary,
  TranslationVariant,
} from '../models/translation';

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
   * @param sourceText 使用者輸入的內容，可以是中文也可以是泰文（後端自己判斷方向）
   * @param gender 使用者選的性別，影響泰文造句。輸入泰文時後端會忽略它。
   */
  translate(sourceText: string, gender: SpeakerGender): Observable<TranslationResponse> {
    return this.http.post<TranslationResponse>(
      '/api/v1/translations', { sourceText, gender });
  }

  /**
   * 產生一段文字的音檔。逐詞的播放鍵是灰色時，點下去就是打這支。
   * 後端會先查現成的，沒有才真的合成 —— 所以同一個詞點第二次不會再花錢。
   */
  synthesize(speechText: string, language: SpeechLanguage): Observable<AudioResponse> {
    return this.http.post<AudioResponse>(
      '/api/v1/audio', { speechText, language });
  }

  /**
   * 取得一筆查詢的逐詞拆解。使用者點了「逐詞拆解」才會呼叫。
   * 後端會先查資料庫，沒有才呼叫 OpenAI —— 同一句話拆第二次不會再花錢。
   *
   * @param queryId 翻譯回應裡的 queryId
   */
  segments(queryId: number): Observable<TranslationSegment[]> {
    return this.http.post<TranslationSegment[]>(
      `/api/v1/translations/${queryId}/segments`, null);
  }

  /**
   * 取得一個詞的各種說法。使用者點了「各種說法」才會呼叫。
   * 查句子時會回空陣列 —— 整句沒有「另一種說法」這種東西，那是正常結果不是錯誤。
   *
   * @param queryId 翻譯回應裡的 queryId
   */
  variants(queryId: number): Observable<TranslationVariant[]> {
    return this.http.post<TranslationVariant[]>(
      `/api/v1/translations/${queryId}/variants`, null);
  }

  /*
   * ── ★ 底下五支「不會花錢」，所以動詞跟上面四支不一樣 ──────────────────
   *
   *  上面的 translate / synthesize / segments / variants 都是 POST，
   *  理由是它們可能呼叫 OpenAI。
   *
   *  底下這五支只讀資料庫、或只改一個時間欄位，不可能花錢，
   *  所以用 GET / PUT / DELETE。看網址就知道哪些請求有成本。
   */

  /** 最近搜尋，去重後最多 20 筆。沒有紀錄時回空陣列，那是正常結果不是錯誤。 */
  recent(): Observable<TranslationSummary[]> {
    return this.http.get<TranslationSummary[]>('/api/v1/translations/recent');
  }

  /** 收藏清單，加入收藏的時間新的在前。 */
  favorites(): Observable<TranslationSummary[]> {
    return this.http.get<TranslationSummary[]>('/api/v1/translations/favorites');
  }

  /**
   * 加入收藏。用 PUT 是因為它的語意是「把它設成收藏狀態」——
   * 連按兩下愛心不會產生第二筆，也不會出錯。
   */
  addFavorite(queryId: number): Observable<void> {
    return this.http.put<void>(`/api/v1/translations/${queryId}/favorite`, null);
  }

  /** 取消收藏。 */
  removeFavorite(queryId: number): Observable<void> {
    return this.http.delete<void>(`/api/v1/translations/${queryId}/favorite`);
  }

  /**
   * 用 id 還原一筆查詢的完整結果，點清單的一列時用這支。
   *
   * ★ 千萬不要改成「把文字填回輸入框再呼叫 translate()」。
   *   translate 會經過快取鑰匙（原文＋方向＋性別）的比對，
   *   那一筆是男生版而你當下切在女生的話，就是一筆全新的查詢 ——
   *   真的呼叫 OpenAI、真的付錢，而畫面上看起來完全正常。
   */
  restore(queryId: number): Observable<TranslationResponse> {
    return this.http.get<TranslationResponse>(`/api/v1/translations/${queryId}`);
  }
}
