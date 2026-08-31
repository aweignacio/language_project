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
 * ── 一輪制是什麼意思 ────────────────────────────────────────────────────
 *
 *  你收藏了 12 句，按 12 次「隨機播放」會把 12 句都聽過一遍，不會重複。
 *  第 12 句播完的當下，「已播」標記全部清空，第 13 次按就是新的一輪。
 *
 *  ★ 所以這個函式除了回傳「播哪一句」，還要回傳「播完之後已播名單長怎樣」：
 *
 *      pickShuffleTarget(清單, ['已播過的 queryId'], 上一句 queryId, 亂數)
 *          ↓
 *      { target: {queryId:42, ...}, playedQueryIds: [137, 42] }
 *                                                   ↑ 這是播完之後的名單
 *
 *    整輪剛好在這一次播完時，playedQueryIds 會是空陣列 —— 元件把它接回
 *    畫面上，所有「已播」小標就同時消失。使用者不會看到任何提示訊息，
 *    標記自己歸零，這是刻意的（提示會變成每輪都要關掉的雜訊）。
 *
 * ── 隨機的東西怎麼測 ────────────────────────────────────────────────────
 *
 *  ★ 關鍵在於「隨機」是從外面傳進去的，不是函式自己去呼叫 Math.random。
 *
 *      pickShuffleTarget(items, playedQueryIds, lastPlayedQueryId, random)
 *                                                                 ↑ 這個參數
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
 *  二：挑到這一輪已經聽過的 → 一輪制失效，變回會重複的舊行為
 *  三：挑完沒有記進名單 → 下一次又抽到它，而且畫面上不會標「已播」
 *  四：★ 一輪播完沒有清空 → 從此挑不出任何人選，按鈕變成按了沒反應
 *  五：★ 新一輪第一句剛好是上一輪最後一句 → 同一句連著播兩次
 *  六：★ 只剩一句可播時被「不重複」規則擋掉 → 回傳 null，完全不出聲
 *  七：★ 名單裡有已經被取消收藏的 queryId → 挑不出人選而卡死
 *  八：清單全部沒有音檔 → 不可以爆炸，要安靜地回 null
 *  九：亂數沒有真的被用到 → 「永遠回傳第一個」也會通過其他測試
 *
 * ── 另一半：findReplayTarget（「再聽一次」那顆鍵）────────────────────────
 *
 *  你按了隨機播放，聽到一句泰文想再聽一遍，按「再聽一次」。
 *
 *  ★ 元件只記得剛剛那一句的 queryId，不記音檔網址 —— 要播的時候
 *    拿 queryId 回頭去清單裡找。這個函式做的就是「找回來」那一步。
 *
 *    為什麼不直接把網址存起來：你可能中途把那一句取消收藏，它就從清單上
 *    消失了。存網址的話按鈕還是亮的，按下去會播一句已經不在清單上的話；
 *    回頭找就會找不到，按鈕自動變灰。後者才是誠實的。
 *
 *  十：找不回剛剛那一句 → 「再聽一次」按了沒反應
 *  十一：一句都還沒抽過時卻回傳了東西 → 按鈕一開始就是亮的，按了沒聲音
 *  十二：★ 那一筆已經被取消收藏卻還回傳 → 播出清單上根本沒有的句子
 */

import { describe, expect, it } from 'vitest';
import { TranslationSummary } from '../../models/translation';
import { findReplayTarget, pickShuffleTarget } from './shuffle-pick';

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

    const picked = pickShuffleTarget(items, [], null, () => 0);

    expect(picked.target?.queryId).toBe(2);
  });

  /*
   * ═══ 測試二：這一輪已經聽過的不可以再被挑到 ═════════════════════════
   *
   * 已播名單是 [1]，random 回 0（＝挑剩下的第一個）。
   * 沒有排除的話會挑回 1。
   */
  it('不應該挑到這一輪已經播過的那些句', () => {
    const items = [
      summary(1, '這輪聽過了', '/audio/th/aaa.mp3'),
      summary(2, '還沒聽過', '/audio/th/bbb.mp3'),
      summary(3, '也還沒聽過', '/audio/th/ccc.mp3'),
    ];

    const picked = pickShuffleTarget(items, [1], 1, () => 0);

    expect(picked.target?.queryId).toBe(2);
  });

  /*
   * ═══ 測試三：挑中的那一句要被記進已播名單 ═══════════════════════════
   *
   * 元件拿這份名單做兩件事：畫「已播」小標、下次挑選時排除。
   * 沒記進去的話兩件事都會失效。
   */
  it('應該把挑中的那一句加進已播名單', () => {
    const items = [
      summary(1, '第一', '/audio/th/aaa.mp3'),
      summary(2, '第二', '/audio/th/bbb.mp3'),
      summary(3, '第三', '/audio/th/ccc.mp3'),
    ];

    const picked = pickShuffleTarget(items, [1], 1, () => 0);

    expect(picked.playedQueryIds).toEqual([1, 2]);
  });

  /*
   * ═══ 測試四：★ 一輪的最後一句播完時名單要清空 ═══════════════════════
   *
   * 三句可播，已經聽過 1 和 2，這一次挑走 3 —— 整輪結束。
   *
   * ★ 不清空的話下一次按會發生什麼：三句全都在已播名單裡，
   *   挑不出任何人選，按鈕從此按了沒反應。這是這個功能最容易死掉的地方。
   */
  it('挑走這一輪最後一句時應該把已播名單清空', () => {
    const items = [
      summary(1, '聽過了', '/audio/th/aaa.mp3'),
      summary(2, '也聽過了', '/audio/th/bbb.mp3'),
      summary(3, '最後一句', '/audio/th/ccc.mp3'),
    ];

    const picked = pickShuffleTarget(items, [1, 2], 2, () => 0);

    expect(picked.target?.queryId).toBe(3);
    expect(picked.playedQueryIds).toEqual([]);
  });

  /*
   * ═══ 測試五：★ 新一輪的第一句不可以是上一輪的最後一句 ═══════════════
   *
   * 上一輪剛結束（已播名單已經是空的），最後播的是 2。
   * 這時已播名單幫不上忙 —— 它是空的，兩句都算「還沒聽過」。
   * 要靠 lastPlayedQueryId 才擋得住，random 回 0.99（＝挑最後一個）也一樣。
   */
  it('新一輪的第一句不應該是上一輪最後播的那一句', () => {
    const items = [
      summary(1, '第一', '/audio/th/aaa.mp3'),
      summary(2, '上一輪最後播的', '/audio/th/bbb.mp3'),
    ];

    const picked = pickShuffleTarget(items, [], 2, () => 0.99);

    expect(picked.target?.queryId).toBe(1);
  });

  /*
   * ═══ 測試六：★ 只剩一句可播時，仍然要播它 ═══════════════════════════
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

    const picked = pickShuffleTarget(items, [], 1, () => 0);

    expect(picked.target?.queryId).toBe(1);
    // 一句就是一輪，播完立刻結束，所以名單維持空的。
    expect(picked.playedQueryIds).toEqual([]);
  });

  /*
   * ═══ 測試七：★ 名單裡有已經不在清單上的句子時不可以卡死 ═════════════
   *
   * 情境：這一輪聽過 1 和 2，還剩 3 沒聽。這時你把 3 取消收藏，
   * 它就從清單上消失了 —— 剩下的兩句都在已播名單裡，挑不出人選。
   *
   * ★ 判斷「這輪播完了沒」如果是用數字比大小（名單筆數 vs 清單筆數），
   *   這裡就會算錯。要看的是「還挑不挑得出人選」。
   */
  it('已播名單含有已經被移除的句子時應該直接開新的一輪', () => {
    const items = [
      summary(1, '聽過了', '/audio/th/aaa.mp3'),
      summary(2, '也聽過了', '/audio/th/bbb.mp3'),
    ];

    const picked = pickShuffleTarget(items, [1, 2], 2, () => 0);

    // 開了新的一輪，而且避開上一句 2。
    expect(picked.target?.queryId).toBe(1);
    expect(picked.playedQueryIds).toEqual([1]);
  });

  /*
   * ═══ 測試八：全部都沒有音檔時安靜地回 null ══════════════════════════
   *
   * 不可以丟例外，也不可以回傳一個沒有音檔的項目。
   * 元件靠這個 null 把按鈕畫成不能按的樣子。
   * ★ 名單也要原封不動 —— 什麼都沒播，已播進度沒有理由被動到。
   */
  it('清單裡沒有任何音檔時應該回傳 null 且不動已播名單', () => {
    const items = [
      summary(1, '沒有音檔', null),
      summary(2, '也沒有', null),
    ];

    expect(pickShuffleTarget(items, [7], null, () => 0).target).toBeNull();
    expect(pickShuffleTarget(items, [7], null, () => 0).playedQueryIds).toEqual([7]);
    expect(pickShuffleTarget([], [], null, () => 0).target).toBeNull();
  });

  /*
   * ═══ 測試九：random 的值真的被用來決定挑哪一個 ═══════════════════════
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

    expect(pickShuffleTarget(items, [], null, () => 0).target?.queryId).toBe(1);
    expect(pickShuffleTarget(items, [], null, () => 0.5).target?.queryId).toBe(2);
    // 0.99 而不是 1 —— Math.random 的範圍是 [0, 1)，永遠不會剛好是 1。
    expect(pickShuffleTarget(items, [], null, () => 0.99).target?.queryId).toBe(3);
  });
});

describe('findReplayTarget', () => {

  /*
   * ═══ 測試十：找出剛剛隨機播的那一筆 ═════════════════════════════════
   *
   * 「再聽一次」整顆鍵就靠這個。找不回來的話按了不會有聲音。
   */
  it('應該找出剛剛播過的那一筆', () => {
    const items = [
      summary(1, '第一', '/audio/th/aaa.mp3'),
      summary(2, '剛剛播的', '/audio/th/bbb.mp3'),
    ];

    expect(findReplayTarget(items, 2)?.queryId).toBe(2);
  });

  /*
   * ═══ 測試十一：一句都還沒抽過時要回 null ════════════════════════════
   *
   * 你剛切進收藏分頁，還沒按過隨機播放。
   * 元件靠這個 null 把「再聽一次」畫成不能按的樣子。
   */
  it('還沒隨機播過任何一句時應該回傳 null', () => {
    const items = [summary(1, '第一', '/audio/th/aaa.mp3')];

    expect(findReplayTarget(items, null)).toBeNull();
  });

  /*
   * ═══ 測試十二：★ 那一筆已經不在清單上時要回 null ═════════════════════
   *
   * 情境：隨機播到「這個多少錢」，你聽完覺得不需要了，按愛心取消收藏 ——
   * 那一列從清單上消失。這時「再聽一次」必須變灰。
   *
   * ★ 這正是「只記 queryId、播的時候回頭找」換來的好處。
   *   如果當初存的是音檔網址，這裡就攔不住。
   */
  it('剛剛那一筆已經不在清單上時應該回傳 null', () => {
    const items = [summary(1, '剩下這句', '/audio/th/aaa.mp3')];

    expect(findReplayTarget(items, 2)).toBeNull();
  });

  /*
   * ═══ 測試十三：那一筆沒有音檔時要回 null ════════════════════════════
   *
   * 很少見（隨機播放本來就只挑得到有音檔的），但按鈕的亮暗是看這個函式，
   * 回傳一筆沒有音檔的東西就會變成「亮著卻按不出聲音」。
   */
  it('剛剛那一筆沒有音檔時應該回傳 null', () => {
    const items = [summary(1, '音檔不見了', null)];

    expect(findReplayTarget(items, 1)).toBeNull();
  });
});
