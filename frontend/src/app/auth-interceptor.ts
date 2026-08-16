import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  攔住「登入狀態過期」的回應，把畫面帶去登入頁。
 *
 * ── 為什麼需要它（2026-08-16 實際被咬到）─────────────────────────────
 *
 *  登入狀態是有期限的。過期之後你按查詢，畫面會顯示
 *  「無法連線到伺服器，請確認後端是否已啟動」——
 *  那個訊息完全誤導：後端好好的，它只是要你重新登入。
 *
 *  使用者看到那句話只會去檢查後端有沒有掛，永遠想不到答案是「重新登入」。
 *
 * ── 流程：你放著一段時間沒用，回來按查詢 ───────────────────────────────
 *
 *  第 1 步｜Angular 送出 POST /api/v1/translations
 *
 *  第 2 步｜後端發現登入狀態過期，回 401
 *
 *    ★ 後端特地為 /api 回 401 而不是導向登入頁，就是為了讓這裡收得到。
 *      （導向的話瀏覽器會自動跟隨，Angular 拿到的是一頁 HTML，
 *        它看不懂，只能報最籠統的錯 —— 那正是上面那句誤導訊息的來源。）
 *
 *  第 3 步｜這個攔截器收到 401
 *
 *  第 4 步｜整頁導向 /login.html
 *
 *    ★ 用 location.href 而不是 Angular 的路由：
 *      登入頁是後端提供的純 HTML，不在 Angular 的路由表裡。
 *      而且登入成功後要由後端重新發 cookie，本來就需要一次完整的頁面載入。
 * ══════════════════════════════════════════════════════════════════════════
 */

/** 後端提供的登入頁。與 SecurityConfig 的 LOGIN_PAGE 必須一致。 */
const LOGIN_PAGE = '/login.html';

export const authInterceptor: HttpInterceptorFn = (request, next) =>
  next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // 帶上原本要去的頁面，登入後可以導回來。
        // 目前後端固定導回首頁，這個參數先留著給日後用。
        const from = encodeURIComponent(location.pathname + location.search);
        location.href = `${LOGIN_PAGE}?from=${from}`;
      }

      return throwError(() => error);
    })
  );
