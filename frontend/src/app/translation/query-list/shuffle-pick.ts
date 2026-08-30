/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  收藏清單那顆「隨機播放」按下去時，決定「要播哪一句」。
 *
 *  只有這一件事 —— 它不播放、不碰畫面、不知道 Angular 的存在，
 *  純粹是「餵一份清單進去，拿一筆出來」。
 *
 * ── ★ 為什麼要獨立成一個檔案 ────────────────────────────────────────────
 *
 *  「決定播哪一句」跟「真的發出聲音」是兩件事。
 *
 *  分開之後，挑選這件事完全沒有 Angular 相依 —— 測試不必啟動 TestBed、
 *  不必渲染元件、不必假造 AudioPlayerService，就只是呼叫一個函式看它回什麼。
 *  留在元件裡的話，測「不可以連續挑到同一句」得先組出半個 Angular 環境。
 *
 * ── 流程：你按下收藏清單上的 🔀 ─────────────────────────────────────────
 *
 *  第 1 步｜元件把三樣東西交過來
 *
 *      pickShuffleTarget(目前清單, 上一次播過的 queryId, 亂數來源)
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
 *  第 3 步｜排除上一次播過的那一句
 *
 *      上次播 137 → 這次只從 [ {42} ] 裡面挑
 *
 *    ★ 不排除的話，收藏只有兩三句時會一直重複同一句，感覺像壞掉。
 *
 *    ★ 但只剩一句可播時要放棄這條規則，直接回那一句。
 *      死守不重複的話會回傳 null —— 按下去完全沒反應，那更像壞掉。
 *
 *  第 4 步｜用亂數挑一個
 *
 *      random() 回 0.5、候選有 2 筆 → floor(0.5 × 2) = 1 → 挑第二筆
 *
 *    ★ 亂數是從外面傳進來的，不是這裡直接呼叫 Math.random。
 *      正式執行時不傳，預設就是 Math.random；測試時餵固定值，
 *      結果才會是可預測的，才能斷言「挑出來的是哪一筆」。
 *      否則測試只能寫成「跑一百次看看有沒有壞」，那種測試會偶爾紅燈。
 *
 *  測試檔：shuffle-pick.spec.ts
 * ══════════════════════════════════════════════════════════════════════════
 */

import { TranslationSummary } from '../../models/translation';

/**
 * 從清單裡挑一句來播。
 *
 * @param items             目前畫面上的清單
 * @param lastPlayedQueryId 上一次隨機播過的 queryId，沒播過就傳 null
 * @param random            亂數來源，正式執行不必傳（測試時用來固定結果）
 * @returns 要播的那一筆；沒有任何一句有音檔時回傳 null
 */
export function pickShuffleTarget(
  items: TranslationSummary[],
  lastPlayedQueryId: number | null,
  random: () => number = Math.random,
): TranslationSummary | null {
  const playable = items.filter((item) => item.thaiAudioUrl);

  if (!playable.length) {
    return null;
  }

  // ★ 只剩一句可播時放棄「不重複」，否則會回傳 null 變成按了沒反應。
  const candidates = playable.length > 1
    ? playable.filter((item) => item.queryId !== lastPlayedQueryId)
    : playable;

  return candidates[Math.floor(random() * candidates.length)];
}
