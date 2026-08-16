import { ApplicationConfig, provideBrowserGlobalErrorListeners, isDevMode } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideServiceWorker } from '@angular/service-worker';

/**
 * 整個應用程式共用的設定。
 * provideHttpClient() 一定要在這裡註冊，TranslationService 才注入得到 HttpClient；
 * 少了它，畫面會在啟動時就丟出「No provider for HttpClient」。
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(), provideServiceWorker('ngsw-worker.js', {
            enabled: !isDevMode(),
            registrationStrategy: 'registerWhenStable:30000'
          }),
  ]
};
