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
 *  3. App 的樣板是 <app-translation />，所以 Translation 也會跟著被建立，
 *     它注入的 TranslationService、再往下注入的 HttpClient 都要接得起來
 *  4. 檢查畫面上真的有查詢按鈕，代表整條路都通了
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

  it('應該渲染出查詢畫面', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('h1')?.textContent).toContain('中泰翻譯查詢');
    expect(compiled.querySelector('.search__button')?.textContent).toContain('查詢');
  });
});
