/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  收藏清單上方那兩顆鍵「該播哪一句」的判斷，兩個函式：
 *
 *      pickShuffleTarget  隨機播放 → 挑一句，並算出這一輪聽過哪些了
 *      findReplayTarget   再聽一次 → 把剛剛那一句找回來
 *
 *  只有這些 —— 它不播放、不碰畫面、不知道 Angular 的存在，
 *  純粹是「餵一份清單進去，拿一筆出來」。
 *
 * ── ★ 為什麼要獨立成一個檔案 ────────────────────────────────────────────
 *
 *  「決定播哪一句」跟「真的發出聲音」是兩件事。
 *
 *  分開之後，挑選這件事完全沒有 Angular 相依 —— 測試不必啟動 TestBed、
 *  不必渲染元件、不必假造 AudioPlayerService，就只是呼叫一個函式看它回什麼。
 *  留在元件裡的話，測「一輪不可以重複」得先組出半個 Angular 環境。
 *
 * ── 一輪制：整份收藏聽過一遍才會重來 ────────────────────────────────────
 *
 *  你收藏了 12 句，按 12 次會把 12 句都聽過一遍，中間不會重複；
 *  第 12 句播完的當下，「已播」標記全部清空，第 13 次按就是新的一輪。
 *
 *  ★ 標記清空是靜悄悄發生的，畫面上不會跳任何提示。
 *    提示會變成「每輪都要看一次的雜訊」，而使用者從標記全消失就看得出來了。
 *
 * ── 流程：你按下收藏清單上的「隨機播放」 ────────────────────────────────
 *
 *  第 1 步｜元件把四樣東西交過來
 *
 *      pickShuffleTarget(目前清單, 這輪已播的 queryId, 上一句 queryId, 亂數來源)
 *
 *  第 2 步｜先篩掉沒有音檔的那些列
 *
 *      [ {137, 有音檔}, {88, thaiAudioUrl 是 null}, {42, 有音檔} ]
 *          ↓
 *      [ {137, 有音檔}, {42, 有音檔} ]
 *
 *    ★ 為什麼不現場合成沒有音檔的那幾句：
 *      隨機播放的意思是「聽已經有的東西」。讓它偷偷去呼叫 API 補合成，
 *      等於按一顆按鈕就默默花錢，而畫面上完全看不出來。
 *      這與清單組裝時用 findExistingAudioUrls（只查不生）是同一個立場。
 *
 *  第 3 步｜排除這一輪已經聽過的
 *
 *      已播 [137] → 這次只從 [ {42} ] 裡面挑
 *
 *    ★ 挑不出人選就代表這一輪走完了（或你把剩下沒聽的那幾句取消收藏了），
 *      這時把整份清單重新當成候選，開始新的一輪。
 *
 *      判斷用的是「還挑不挑得出人選」，不是「名單筆數 vs 清單筆數」。
 *      取消收藏會讓那個 queryId 留在名單裡卻不在清單上，數字永遠對不起來。
 *
 *  第 4 步｜再排除上一句
 *
 *      ★ 為什麼第 3 步不夠：新一輪剛開始時已播名單是空的，
 *        兩句都算「還沒聽過」，上一輪的最後一句會馬上又被抽到。
 *
 *      ★ 但候選只剩一句時要放棄這條規則，直接回那一句。
 *        死守不重複的話會回傳 null —— 按下去完全沒反應，那更像壞掉。
 *
 *  第 5 步｜用亂數挑一個
 *
 *      random() 回 0.5、候選有 2 筆 → floor(0.5 × 2) = 1 → 挑第二筆
 *
 *    ★ 亂數是從外面傳進來的，不是這裡直接呼叫 Math.random。
 *      正式執行時不傳，預設就是 Math.random；測試時餵固定值，
 *      結果才會是可預測的，才能斷言「挑出來的是哪一筆」。
 *      否則測試只能寫成「跑一百次看看有沒有壞」，那種測試會偶爾紅燈。
 *
 *  第 6 步｜算出播完之後的已播名單
 *
 *      { target: {42, ...}, playedQueryIds: [137, 42] }
 *
 *    ★ 如果加進去之後整份清單都聽過了，回傳的是空陣列而不是滿的名單 ——
 *      這一輪就在這一次結束。元件把它接回畫面，所有小標同時消失。
 *
 *  測試檔：shuffle-pick.spec.ts
 * ══════════════════════════════════════════════════════════════════════════
 */

import { TranslationSummary } from '../../models/translation';

/** 挑選的結果：播哪一句，以及播完之後這一輪的已播名單。 */
export interface ShufflePick {

  /** 要播的那一筆。沒有任何一句有音檔時是 null。 */
  target: TranslationSummary | null;

  /** 播完這一筆之後的已播名單。整輪剛好在這一次走完時會是空陣列。 */
  playedQueryIds: number[];
}

/**
 * 從清單裡挑一句來播，並算出播完之後的已播名單。
 *
 * @param items             目前畫面上的清單
 * @param playedQueryIds    這一輪已經播過的 queryId
 * @param lastPlayedQueryId 上一次隨機播過的 queryId，沒播過就傳 null
 * @param random            亂數來源，正式執行不必傳（測試時用來固定結果）
 * @returns 挑選結果；沒有任何一句有音檔時 target 是 null，名單原封不動
 */
export function pickShuffleTarget(
  items: TranslationSummary[],
  playedQueryIds: readonly number[],
  lastPlayedQueryId: number | null,
  random: () => number = Math.random,
): ShufflePick {
  const playable = items.filter((item) => item.thaiAudioUrl);

  if (!playable.length) {
    return { target: null, playedQueryIds: [...playedQueryIds] };
  }

  const unplayed = playable.filter((item) => !playedQueryIds.includes(item.queryId));

  // 挑不出沒聽過的 → 這一輪走完了，整份清單重新當候選，名單從頭算起。
  const startingNewRound = !unplayed.length;
  const played = startingNewRound ? [] : playedQueryIds;
  const pool = startingNewRound ? playable : unplayed;

  // ★ 候選只剩一句時放棄「不重複」，否則會回傳 null 變成按了沒反應。
  const candidates = pool.length > 1
    ? pool.filter((item) => item.queryId !== lastPlayedQueryId)
    : pool;

  const target = candidates[Math.floor(random() * candidates.length)];
  const next = [...played, target.queryId];

  // 加進去之後還有沒聽過的就留著名單，沒有了就代表這一輪在這一次走完。
  const roundFinished = playable.every((item) => next.includes(item.queryId));

  return { target, playedQueryIds: roundFinished ? [] : next };
}

/**
 * 找出「再聽一次」要播的那一筆，也就是剛剛隨機抽到的那一句。
 *
 * ★ 元件記的是 queryId 而不是音檔網址，播的時候才回頭到清單裡找。
 *   你中途把那一句取消收藏時，它就從清單上消失了 —— 存網址的話按鈕還是
 *   亮的，按下去會播一句已經不在清單上的話；回頭找就會找不到，
 *   按鈕自動變灰。後者才是誠實的。
 *
 * @param items             目前畫面上的清單
 * @param lastPlayedQueryId 上一次隨機播過的 queryId，沒播過就傳 null
 * @returns 可以重聽的那一筆；還沒播過、已經不在清單上、或沒有音檔時回 null
 */
export function findReplayTarget(
  items: TranslationSummary[],
  lastPlayedQueryId: number | null,
): TranslationSummary | null {
  const target = items.find((item) => item.queryId === lastPlayedQueryId);

  // 沒有音檔的也當作找不到 —— 元件靠這個 null 決定按鈕亮不亮，
  // 回傳一筆播不出聲音的東西會變成「亮著卻按了沒反應」。
  return target?.thaiAudioUrl ? target : null;
}
