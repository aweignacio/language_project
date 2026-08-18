/*
 * ── 這個測試在防什麼 ────────────────────────────────────────────────────
 *
 *  只有一件事：整個畫面「起得來」。
 *
 *  聽起來很廢，但它擋掉的是最常見也最難查的一種壞法 ——
 *  相依注入沒接好。例如 app.config.ts 忘了 provideHttpClient()，
 *  程式編譯完全正常，要等你在瀏覽器打開才發現整頁空白，
 *  主控台寫著「No provider for HttpClient」。
 *
 *  這個測試會在那種情況下直接紅燈。
 *
 * ── 什麼東西被換成假的 ──────────────────────────────────────────────────
 *
 *  provideHttpClientTesting() 把真正的 HttpClient 換成假的。
 *  測試裡不該有任何真的網路連線 —— 後端沒開測試就會壞，
 *  那測到的是「後端在不在」，不是「前端對不對」。
 *
 * ── 流程 ────────────────────────────────────────────────────────────────
 *
 *  1. TestBed 組出一個只有 App 的迷你 Angular 環境
 *  2. createComponent(App) 建立元件並渲染
 *  3. App 是分頁殼（查詢／最近／收藏），預設停在「查詢」，
 *     所以 Translation 也會跟著被建立，它注入的 TranslationService、
 *     再往下注入的 HttpClient 都要接得起來
 *  4. 檢查畫面上真的有三顆分頁鍵與查詢按鈕，代表整條路都通了
 *
 *  ★ 「最近」與「收藏」兩個分頁的 QueryList 不會在這裡被建立 ——
 *    它們是 @if 控制的，預設分頁是查詢。要測它們得先切分頁，
 *    那是另一支測試的事。
 */

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('應該可以建立根元件', () => {
    const fixture = TestBed.createComponent(App);

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('應該渲染出分頁列與查詢畫面', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;

    // 三顆分頁鍵都要在
    expect(compiled.querySelectorAll('.tabs__item').length).toBe(3);

    // 預設停在「查詢」，所以查詢畫面要看得到
    expect(compiled.querySelector('h1')?.textContent).toContain('中泰翻譯查詢');
    expect(compiled.querySelector('.search__button')?.textContent).toContain('查詢');
  });
});
