/*
 * ── 這個測試在防什麼 ────────────────────────────────────────────────────
 *
 *  收藏清單那顆「隨機播放」按下去時，「要播哪一句」的挑選邏輯。
 *
 *  這段被抽成一個獨立的檔案 shuffle-pick.ts，而不是寫在元件裡面，
 *  ★ 理由是「決定播哪一句」跟「真的發出聲音」是兩件事。
 *    分開之後，挑選這件事不需要 Angular、不需要瀏覽器、不需要音效裝置，
 *    純粹是「餵一份清單進去，拿一筆出來」——
 *    下面每一支測試都只是呼叫一個函式看它回什麼，沒有 TestBed、沒有假物件。
 *
 * ── ★ 怎麼跑這支測試 ────────────────────────────────────────────────────
 *
 *  一定要用 npm test（也就是 ng test），不要直接下 npx vitest。
 *
 *  直接跑 vitest 會繞過 Angular 的測試設定，症狀是
 *  「ReferenceError: describe is not defined」（少了 globals 設定），
 *  以及匯入元件時的「needs to be compiled using the JIT compiler」。
 *  兩個都是執行方式的問題，不是程式碼的問題 —— 2026-08-30 被騙過一次。
 *
 * ── 隨機的東西怎麼測 ────────────────────────────────────────────────────
 *
 *  ★ 關鍵在於「隨機」是從外面傳進去的，不是函式自己去呼叫 Math.random。
 *
 *      pickShuffleTarget(items, lastPlayedQueryId, random)
 *                                                  ↑ 這個參數
 *
 *  正式執行時不傳，它就用 Math.random。測試時餵一個固定值（例如永遠回 0），
 *  結果就變成可預測的，才能斷言「挑出來的是哪一筆」。
 *
 *  沒有這個設計的話，這幾個測試只能寫成「跑一百次看看有沒有壞」，
 *  那種測試會偶爾紅燈，久了大家就開始無視它。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  一：挑到沒有音檔的那一句 → 按下去沒聲音，看起來像功能壞了
 *  二：連續抽到同一句 → 收藏少的時候一直重複，也像壞了
 *  三：★ 只剩一句可播時被「不重複」規則擋掉 → 回傳 null，完全不出聲
 *  四：清單全部沒有音檔 → 不可以爆炸，要安靜地回 null
 */

import { describe, expect, it } from 'vitest';
import { TranslationSummary } from '../../models/translation';
import { pickShuffleTarget } from './shuffle-pick';

/** 組一筆清單資料。audioUrl 傳 null 代表這一句還沒有音檔。 */
function summary(queryId: number, chineseText: string, audioUrl: string | null): TranslationSummary {
  return {
    queryId,
    chineseText,
    thaiText: 'ทดสอบ',
    romanization: 'thot-sop',
    direction: 'ZH_TO_TH',
    gender: 'MALE',
    thaiAudioUrl: audioUrl,
    favorited: true,
  };
}

describe('pickShuffleTarget', () => {

  /*
   * ═══ 測試一：沒有音檔的那些列不可以被挑到 ═══════════════════════════
   *
   * ★ 這裡刻意把「沒有音檔」的那筆放在第一個，並讓 random 回 0
   *   （＝挑第一個）。挑選時如果沒有先過濾，就一定會挑到它。
   */
  it('應該跳過沒有音檔的那些列', () => {
    const items = [
      summary(1, '沒有音檔', null),
      summary(2, '有音檔', '/audio/th/aaa.mp3'),
    ];

    const picked = pickShuffleTarget(items, null, () => 0);

    expect(picked?.queryId).toBe(2);
  });

  /*
   * ═══ 測試二：不可以連續挑到同一句 ═══════════════════════════════════
   *
   * 上一次播的是 1，random 回 0（＝挑剩下的第一個）。
   * 沒有排除的話會挑回 1。
   */
  it('不應該挑到上一次播過的那一句', () => {
    const items = [
      summary(1, '剛播過', '/audio/th/aaa.mp3'),
      summary(2, '還沒播過', '/audio/th/bbb.mp3'),
    ];

    const picked = pickShuffleTarget(items, 1, () => 0);

    expect(picked?.queryId).toBe(2);
  });

  /*
   * ═══ 測試三：只剩一句可播時，仍然要播它 ═════════════════════════════
   *
   * ★ 這是「不重複」規則的例外，也是最容易寫錯的地方。
   *   只有一句有音檔、而它正好就是上一句時，如果死守不重複，
   *   結果會是回傳 null —— 按下去完全沒反應，使用者只會覺得壞了。
   *   重複播同一句雖然無聊，但至少是有作用的。
   */
  it('只有一句可播時即使剛播過也要回傳它', () => {
    const items = [
      summary(1, '唯一有音檔的', '/audio/th/aaa.mp3'),
      summary(2, '沒有音檔', null),
    ];

    const picked = pickShuffleTarget(items, 1, () => 0);

    expect(picked?.queryId).toBe(1);
  });

  /*
   * ═══ 測試四：全部都沒有音檔時安靜地回 null ══════════════════════════
   *
   * 不可以丟例外，也不可以回傳一個沒有音檔的項目。
   * 元件靠這個 null 把按鈕畫成不能按的樣子。
   */
  it('清單裡沒有任何音檔時應該回傳 null', () => {
    const items = [
      summary(1, '沒有音檔', null),
      summary(2, '也沒有', null),
    ];

    expect(pickShuffleTarget(items, null, () => 0)).toBeNull();
    expect(pickShuffleTarget([], null, () => 0)).toBeNull();
  });

  /*
   * ═══ 測試五：random 的值真的被用來決定挑哪一個 ═══════════════════════
   *
   * 三筆都可播，餵不同的隨機值要挑到不同的那一筆。
   * ★ 少了這支，「永遠回傳第一個」也能通過上面所有測試。
   */
  it('應該依照隨機值挑出對應的那一筆', () => {
    const items = [
      summary(1, '第一', '/audio/th/aaa.mp3'),
      summary(2, '第二', '/audio/th/bbb.mp3'),
      summary(3, '第三', '/audio/th/ccc.mp3'),
    ];

    expect(pickShuffleTarget(items, null, () => 0)?.queryId).toBe(1);
    expect(pickShuffleTarget(items, null, () => 0.5)?.queryId).toBe(2);
    // 0.99 而不是 1 —— Math.random 的範圍是 [0, 1)，永遠不會剛好是 1。
    expect(pickShuffleTarget(items, null, () => 0.99)?.queryId).toBe(3);
  });
});
