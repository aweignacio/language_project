/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  「最近」與「收藏」兩個分頁的內容。★ 兩者共用這一個元件。
 *
 *  它們的版面只差三個地方：打哪支 API、愛心是實心還空心、有沒有筆數上限。
 *  寫成兩個元件等於維護兩份幾乎一樣的 HTML 與 CSS，
 *  改一邊忘了改另一邊是遲早的事。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你點開「收藏」分頁到聽見聲音
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜App 把這個元件放上畫面，並告訴它是哪一種 ────────────────────
 *
 *        <app-query-list mode="favorite" (restore)="..." />
 *
 *    mode 是「輸入」—— 由外面決定，元件自己不改它。
 *
 * ── 第 2 步｜元件一出現就去要資料 ───────────────────────────────────────
 *
 *        translationService.favorites()   （mode 是 recent 時改打 recent()）
 *              ↓
 *        [ { queryId:137, chineseText:"幫我叫計程車",
 *            thaiText:"ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ",
 *            romanization:"chûai rîak tháek-sîi hâi pǒm nòi khráp",
 *            direction:"ZH_TO_TH", gender:"MALE",
 *            thaiAudioUrl:"/audio/th/a3f9c2.mp3", favorited:true }, ... ]
 *
 *    ★ items 有三種狀態，不可以合併：
 *
 *        null      還在載入 → 顯示跑動光條
 *        空陣列     載完了，但是沒有東西 → 顯示引導文字
 *        有東西     排出清單
 *
 *      空陣列不是錯誤。一筆收藏都沒有是完全正常的狀態，
 *      顯示紅色錯誤會讓人以為功能壞了。
 *
 * ── 第 3 步｜你點某一列的 ▶ ─────────────────────────────────────────────
 *
 *    thaiAudioUrl 有值   → 直接播
 *    thaiAudioUrl 是 null → 先打 POST /api/v1/audio 補合成，拿到網址再播
 *
 *    ★ 為什麼會沒有音檔？查詢時本來就會自動合成，所以這種情況很少見 ——
 *      只有那次合成失敗的才會是 null。灰鍵點一下就補回來了，
 *      而且補完會寫回那一列，之後就是亮的。
 *
 * ── 第 4 步｜你點某一列的 ♥ ─────────────────────────────────────────────
 *
 *    最近分頁（空心）→ PUT，變成實心
 *    收藏分頁（實心）→ DELETE，那一列從畫面上消失
 *
 *    ★ 失敗時愛心要退回原本的樣子。停在「看起來成功了」的話，
 *      你會以為收藏好了，下次打開收藏卻找不到。
 *
 * ── 第 5 步｜你點的是整列（不是 ▶ 也不是 ♥）────────────────────────────
 *
 *    發出 restore 事件把那一列交給 App，由 App 切到「查詢」分頁並還原。
 *
 *    ★ 這個元件自己不做還原 —— 還原的結果要顯示在另一個元件（Translation）
 *      裡面，所以它只負責「說一聲」，怎麼處理是外面的事。
 *
 * ── ★ 這個元件絕對不會做的事 ────────────────────────────────────────────
 *
 *    改動使用者的性別設定。
 *
 *    清單裡的一列可能是男生版而你當下切在女生。看起來「順手切過去」比較一致，
 *    但那會默默改掉一個持久設定 —— 你下一句自己打的字就會用錯的性別去查，
 *    而那是一筆真的會呼叫 OpenAI、真的花錢的新查詢。
 *
 *    所以這裡只在每一列標出它自己的性別，設定一動也不動。
 */

import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { TranslationSummary } from '../../models/translation';
import { AudioPlayerService } from '../../services/audio-player';
import { TranslationService } from '../../services/translation-service';

/** 這個清單是哪一種。決定打哪支 API、愛心的樣子，以及空清單時說什麼。 */
export type QueryListMode = 'recent' | 'favorite';

@Component({
  selector: 'app-query-list',
  imports: [],
  templateUrl: './query-list.html',
  styleUrl: './query-list.css',
})
export class QueryList implements OnInit {

  private readonly translationService = inject(TranslationService);

  private readonly audioPlayer = inject(AudioPlayerService);

  /** 由外面指定這是「最近」還是「收藏」。元件自己不會改它。 */
  readonly mode = input.required<QueryListMode>();

  /**
   * 使用者點了整列，把那一列交出去，由 App 切分頁並還原。
   *
   * ★ 帶整列而不是只帶 queryId：App 還需要 favorited，
   *   才能讓還原後的結果區愛心一開始就是對的樣子。
   */
  readonly restore = output<TranslationSummary>();

  /** 清單資料。null 代表還在載入，空陣列代表「載完了，但是沒有東西」。 */
  protected readonly items = signal<TranslationSummary[] | null>(null);

  protected readonly failed = signal(false);

  /** 正在合成中的文字，用來把那一顆播放鍵顯示成載入中。 */
  protected readonly synthesizing = signal<ReadonlySet<string>>(new Set());

  /** 正在切換收藏中的 queryId，避免連點兩下送出兩個請求。 */
  protected readonly togglingFavorite = signal<ReadonlySet<number>>(new Set());

  ngOnInit(): void {
    this.load();
  }

  /**
   * 去要清單資料。元件建立時呼叫一次。
   *
   * ★ App 用 @if 控制這個元件，切到別的分頁再切回來時它會重新建立，
   *   所以每次切過來看到的都是最新的資料 —— 你在查詢分頁按了愛心，
   *   切到收藏就會看到它。
   */
  protected load(): void {
    this.failed.set(false);
    this.items.set(null);

    const request = this.mode() === 'recent'
      ? this.translationService.recent()
      : this.translationService.favorites();

    request.subscribe({
      next: (items) => this.items.set(items),
      error: () => {
        this.failed.set(true);
        // 設成空陣列而不是留 null —— 留 null 的話跑動光條會一直轉，
        // 看起來像永遠載不完，而不是「失敗了，可以重試」。
        this.items.set([]);
      },
    });
  }

  /** 這一列右上角要顯示的標籤。gender 是 null 代表泰翻中，沒有性別概念。 */
  protected genderLabel(item: TranslationSummary): string {
    if (item.gender === 'MALE') {
      return '男';
    }

    if (item.gender === 'FEMALE') {
      return '女';
    }

    return '泰→中';
  }

  /** 這段文字正在合成中嗎（用來把那一顆播放鍵顯示成載入中）。 */
  protected isSynthesizing(speechText: string): boolean {
    return this.synthesizing().has(speechText);
  }

  /**
   * 點下某一列的播放鍵。
   *
   *   已經有音檔 → 直接播
   *   還沒有音檔 → 先跟後端要，拿到後播放並寫回那一列，下次點就是亮的
   */
  protected play(item: TranslationSummary): void {
    if (item.thaiAudioUrl) {
      this.audioPlayer.play(item.thaiAudioUrl);
      return;
    }

    if (this.isSynthesizing(item.thaiText)) {
      return;
    }

    this.markSynthesizing(item.thaiText, true);

    this.translationService.synthesize(item.thaiText, 'TH').subscribe({
      next: (response) => {
        this.markSynthesizing(item.thaiText, false);
        this.replaceItem({ ...item, thaiAudioUrl: response.audioUrl });
        this.audioPlayer.play(response.audioUrl);
      },
      error: () => {
        // 失敗就讓那顆鍵回到灰色，可以再點一次重試。
        this.markSynthesizing(item.thaiText, false);
      },
    });
  }

  /**
   * 點下某一列的愛心。
   *
   * ★ 失敗時什麼都不改 —— 愛心停在「看起來成功了」的樣子，
   *   使用者會以為收藏好了，下次打開收藏卻找不到。
   */
  protected toggleFavorite(item: TranslationSummary): void {
    if (this.togglingFavorite().has(item.queryId)) {
      return;
    }

    this.markToggling(item.queryId, true);

    const request = item.favorited
      ? this.translationService.removeFavorite(item.queryId)
      : this.translationService.addFavorite(item.queryId);

    request.subscribe({
      next: () => {
        this.markToggling(item.queryId, false);

        // 收藏分頁取消收藏 → 那一列直接消失；最近分頁只是換愛心的樣子。
        if (this.mode() === 'favorite' && item.favorited) {
          this.items.set((this.items() ?? [])
            .filter((current) => current.queryId !== item.queryId));
          return;
        }

        this.replaceItem({ ...item, favorited: !item.favorited });
      },
      error: () => this.markToggling(item.queryId, false),
    });
  }

  /** 點整列：把那一列交給外面，由 App 切到查詢分頁並還原。 */
  protected select(item: TranslationSummary): void {
    this.restore.emit(item);
  }

  /**
   * 換掉清單裡的某一列。
   *
   * ★ 訊號要換一個新陣列才會通知畫面重畫，直接改陣列裡那個物件的欄位沒有用
   *   （zoneless 模式，見 translation.ts 檔頭第 1 步）。
   */
  private replaceItem(updated: TranslationSummary): void {
    this.items.set((this.items() ?? [])
      .map((current) => current.queryId === updated.queryId ? updated : current));
  }

  /** 把某段文字標記成「合成中」或「不在合成中」，每次都換一個新的 Set。 */
  private markSynthesizing(speechText: string, running: boolean): void {
    const next = new Set(this.synthesizing());

    if (running) {
      next.add(speechText);
    } else {
      next.delete(speechText);
    }

    this.synthesizing.set(next);
  }

  /** 把某一列標記成「收藏切換中」或「已結束」，每次都換一個新的 Set。 */
  private markToggling(queryId: number, running: boolean): void {
    const next = new Set(this.togglingFavorite());

    if (running) {
      next.add(queryId);
    } else {
      next.delete(queryId);
    }

    this.togglingFavorite.set(next);
  }
}
