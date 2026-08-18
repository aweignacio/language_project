/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  分頁殼。整個 App 由三個分頁組成，這個檔案決定現在顯示哪一個。
 *
 *      查詢   Translation 元件（原本唯一的那個畫面）
 *      最近   QueryList 元件，mode 是 'recent'
 *      收藏   QueryList 元件，mode 是 'favorite'
 *
 * ── 流程：你點了「收藏」，再點其中一句 ──────────────────────────────────
 *
 *  第 1 步｜你點「收藏」分頁 → switchTo('favorite') → tab 訊號變成 'favorite'
 *
 *  第 2 步｜app.html 的 @if 讓 QueryList 被建立，它一出生就去要資料
 *
 *  第 3 步｜你點清單的某一列 → QueryList 發出 restore 事件，把整列交出來
 *
 *  第 4 步｜這裡的 restore() 切回「查詢」分頁，
 *          再呼叫 Translation 的 restoreQuery(queryId, favorited) 把結果長出來
 *
 *    ★ 為什麼要把 favorited 一起傳？
 *      還原用的 API 只回翻譯內容，沒有收藏狀態。清單那一列剛從後端拿到，
 *      它身上的 favorited 是準的 —— 直接拿來當愛心的初始樣子，
 *      就不必為了一顆愛心再多打一支 API。
 *
 * ── ★ 兩個刻意的決定 ────────────────────────────────────────────────────
 *
 *  (1) 查詢分頁用 hidden 藏起來，不用 @if 拆掉。
 *      拆掉的話，切走再切回來，剛剛查的結果與展開的逐詞拆解會整個消失。
 *
 *  (2) 兩份清單相反，用 @if。
 *      這樣每次切過去都會重新建立、重新去要資料 ——
 *      你在查詢分頁按了愛心，切到收藏就看得到它。
 *
 * ── ★ 為什麼不用 Angular Router ─────────────────────────────────────────
 *
 *  只有三個分頁、不需要分享網址。加 router 要多一套設定，
 *  還要顧到 service worker 的路由 fallback。
 *
 *  代價要知道：手機的返回手勢會直接離開 App，而不是退回上一個分頁。
 */

import { Component, ViewChild, signal } from '@angular/core';
import { TranslationSummary } from './models/translation';
import { QueryList } from './translation/query-list/query-list';
import { Translation } from './translation/translation';

/** 三個分頁。 */
export type AppTab = 'search' | 'recent' | 'favorite';

/**
 * 根元件，同時是分頁殼。
 */
@Component({
  selector: 'app-root',
  imports: [Translation, QueryList],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {

  /** 目前在哪一個分頁。 */
  protected readonly tab = signal<AppTab>('search');

  /**
   * 查詢分頁的元件實例，用來在使用者點清單時把結果還原進去。
   *
   * ★ 拿得到它的前提是查詢分頁一直存在（用 hidden 而不是 @if）——
   *   被拆掉的話這裡會是 undefined，點清單就完全沒有反應。
   */
  @ViewChild(Translation) private translation?: Translation;

  /** 點分頁鍵。 */
  protected switchTo(tab: AppTab): void {
    this.tab.set(tab);
  }

  /**
   * 使用者點了清單的某一列：切到查詢分頁並把那一筆還原出來。
   *
   * ★ 還原走的是「用 id 取回」，不是「拿文字重查一次」——
   *   重查可能因為性別不同而變成一筆全新的查詢，那是會真的花錢的。
   */
  protected restore(item: TranslationSummary): void {
    this.tab.set('search');
    this.translation?.restoreQuery(item.queryId, item.favorited);
  }
}
