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
 * ── 第 4.5 步｜你按住 ☰ 把某一列拖到別的位置（只有收藏分頁有）──────────
 *
 *    放開的瞬間先把畫面排好，同時送出整份新順序：
 *
 *        PUT /api/v1/translations/favorites/order
 *        { "queryIds": [88, 137, 42] }
 *
 *    ★ 為什麼先排畫面再送（樂觀更新）：等後端回來才動的話，手指放開之後
 *      那一列會先彈回原位再跳到新位置，看起來像沒拖成功。
 *
 *    ★ 失敗一定要退回拖之前的順序，並顯示一行提示。
 *      這與第 4 步愛心失敗要退回原樣是同一條規則 ——
 *      停在「看起來成功了」的話，下次打開收藏會是舊順序而你不知道為什麼。
 *
 *    ★ 只有把手能拖，整列不行。整列已經是一個 <button>（第 5 步的還原），
 *      整列可拖的話按住想拖跟想點分不出來，而且在手機上垂直拖曳會跟
 *      頁面捲動打架。把手的 CSS 有一行 touch-action: none 就是在處理這件事。
 *
 * ── 第 4.6 步｜你按下上方的「隨機播放」（只有收藏分頁有）────────────────
 *
 *    從「有音檔、而且這一輪還沒聽過」的句子裡抽一句播，抽中的那一列
 *    標上「已播」。收藏 12 句就按 12 次聽完一輪，中間不會重複；
 *    最後一句播完的當下，所有標記靜靜地一起消失，下一次按是新的一輪。
 *
 *    ★ 挑選與「這一輪走完了沒」的判斷都不在這裡，在 shuffle-pick.ts。
 *      這個元件只負責記住結果、畫上小標、真的發出聲音。
 *
 *    ★ 你自己點某一列的 ▶ 不算進這一輪 —— 那是「我現在就想聽這句」，
 *      跟隨機播放的進度是兩回事，混在一起會讓一輪莫名其妙提早結束。
 *
 *    旁邊那顆「再聽一次」把剛剛抽到的那一句再播一遍。它同樣不算進進度 ——
 *    重聽是「同一句再放一次」，不是新的一次抽選。
 *
 *    ★ 元件只記剛剛那一句的 queryId，播的時候才回頭到清單裡找（見
 *      shuffle-pick.ts 的 findReplayTarget）。你把那一句取消收藏之後
 *      就找不到了，按鈕自動變灰，不會播出清單上已經沒有的句子。
 *
 *    ★ 切到別的分頁再切回來，進度會歸零。這個元件被 @if 控制，
 *      切走就整個銷毀，signal 跟著沒了 —— 不必特地寫程式去清。
 *
 * ── 第 5 步｜你點的是整列（不是 ▶ 也不是 ♥ 也不是 ☰）────────────────────
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

import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { TranslationSummary } from '../../models/translation';
import { AudioPlayerService } from '../../services/audio-player';
import { TranslationService } from '../../services/translation-service';
import { findReplayTarget, pickShuffleTarget } from './shuffle-pick';

/** 這個清單是哪一種。決定打哪支 API、愛心的樣子，以及空清單時說什麼。 */
export type QueryListMode = 'recent' | 'favorite';

@Component({
  selector: 'app-query-list',
  imports: [CdkDrag, CdkDragHandle, CdkDropList],
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

  /** 拖曳排序存檔失敗。順序已經退回原樣，這個旗標只負責告訴使用者「沒存到」。 */
  protected readonly reorderFailed = signal(false);

  /** 正在合成中的文字，用來把那一顆播放鍵顯示成載入中。 */
  protected readonly synthesizing = signal<ReadonlySet<string>>(new Set());

  /** 正在切換收藏中的 queryId，避免連點兩下送出兩個請求。 */
  protected readonly togglingFavorite = signal<ReadonlySet<number>>(new Set());

  /**
   * 這一輪隨機播放已經聽過的 queryId。整輪聽完會自動變回空陣列。
   *
   * ★ 這個要用 signal —— 每一列的「已播」小標是看它畫的，
   *   換一份新陣列畫面才會跟著重畫（zoneless 模式）。
   */
  protected readonly playedQueryIds = signal<readonly number[]>([]);

  /**
   * 上一次隨機播過的 queryId。兩個用途：避免連續抽到同一句、「再聽一次」要播它。
   *
   * ★ 已經有 playedQueryIds 了為什麼還要這個：一輪播完時那份名單會被清空，
   *   清空的瞬間「剛剛播的是哪一句」就沒人記得了，新一輪第一句可能立刻
   *   又抽到它。這個欄位跨輪保存那一句。
   *
   * ★ 這個要用 signal —— 「再聽一次」那顆鍵的亮暗是看它算出來的，
   *   換值時畫面要跟著重畫（zoneless 模式）。
   */
  private readonly lastShuffledQueryId = signal<number | null>(null);

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
   * 這是不是收藏分頁。拖曳排序與隨機播放兩個功能都只在收藏分頁出現。
   *
   * ★ 最近清單不給拖：它的順序由「最後查看時間」決定，排了也留不住。
   * ★ 最近清單不給隨機播：那是「剛查過的東西」，隨機聽沒有練習意義。
   */
  protected get favoriteMode(): boolean {
    return this.mode() === 'favorite';
  }

  /** 清單裡有沒有任何一句真的可以播。沒有的話隨機播放鍵要畫成不能按。 */
  protected get hasPlayableAudio(): boolean {
    return (this.items() ?? []).some((item) => item.thaiAudioUrl);
  }

  /** 這一列這一輪被隨機抽到過了嗎（用來畫「已播」小標）。 */
  protected isPlayed(item: TranslationSummary): boolean {
    return this.playedQueryIds().includes(item.queryId);
  }

  /**
   * 隨機播放一句。
   *
   * ★ 挑選的邏輯不在這裡，在 shuffle-pick.ts —— 那是純運算，
   *   分開之後可以不必啟動 Angular 就測完（見該檔的說明）。
   *   這個方法只負責「記住結果」和「真的發出聲音」。
   *
   * ★ 已播名單整份接回來，不是自己 push 進去 —— 一輪走完時
   *   shuffle-pick 回的是空陣列，接回來畫面上的小標就一起消失了。
   */
  protected shufflePlay(): void {
    const picked = pickShuffleTarget(
      this.items() ?? [],
      this.playedQueryIds(),
      this.lastShuffledQueryId(),
    );

    // 一句可播的都沒有。按鈕本來就該是灰的，這裡是第二道保險。
    if (!picked.target) {
      return;
    }

    this.lastShuffledQueryId.set(picked.target.queryId);
    this.playedQueryIds.set(picked.playedQueryIds);
    this.audioPlayer.play(picked.target.thaiAudioUrl);
  }

  /** 現在有沒有「剛剛那一句」可以重聽。沒有的話「再聽一次」是灰的。 */
  protected get canReplay(): boolean {
    return findReplayTarget(this.items() ?? [], this.lastShuffledQueryId()) !== null;
  }

  /**
   * 把剛剛隨機抽到的那一句再播一遍。
   *
   * ★ 什麼都不記 —— 不加進已播名單、也不改 lastShuffledQueryId。
   *   重聽是「同一句再放一次」，不是新的一次抽選，
   *   算進進度的話這一輪會莫名其妙變短。
   */
  protected replay(): void {
    const target = findReplayTarget(this.items() ?? [], this.lastShuffledQueryId());

    // 還沒抽過，或那一句已經被取消收藏。按鈕本來就該是灰的，這裡是第二道保險。
    if (!target) {
      return;
    }

    this.audioPlayer.play(target.thaiAudioUrl);
  }

  /**
   * 放開拖曳中的那一列。
   *
   * ★ 先把畫面排好再送請求（樂觀更新）。等後端回來才動的話，
   *   手指放開之後那一列會先彈回原位再跳到新位置，看起來像沒拖成功。
   *
   * ★ 失敗一定要退回拖之前的順序。停在「看起來成功了」的話，
   *   你會以為排好了，下次打開收藏卻是舊的順序 ——
   *   這與愛心失敗要退回原樣是同一條規則。
   */
  protected drop(event: CdkDragDrop<TranslationSummary[]>): void {
    if (event.previousIndex === event.currentIndex) {
      return;
    }

    const before = this.items() ?? [];

    // moveItemInArray 會就地改動傳進去的陣列，所以先複製一份，
    // 這樣 before 才留得住拖曳前的順序可以回滾。
    const after = [...before];
    moveItemInArray(after, event.previousIndex, event.currentIndex);

    this.items.set(after);
    this.reorderFailed.set(false);

    this.translationService
      .reorderFavorites(after.map((item) => item.queryId))
      .subscribe({
        error: () => {
          this.items.set(before);
          this.reorderFailed.set(true);
        },
      });
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
