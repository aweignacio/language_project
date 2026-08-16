import {
  ApplicationConfig,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  inject,
  isDevMode
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideServiceWorker } from '@angular/service-worker';
import { authInterceptor } from './auth-interceptor';
import { ServiceWorkerUpdater } from './service-worker-updater';

/**
 * 整個應用程式共用的設定。
 * provideHttpClient() 一定要在這裡註冊，TranslationService 才注入得到 HttpClient；
 * 少了它，畫面會在啟動時就丟出「No provider for HttpClient」。
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // withInterceptors 掛上 authInterceptor：登入狀態過期時（後端回 401）
    // 把畫面帶去登入頁，而不是讓使用者看到「無法連線到伺服器」那句誤導訊息。
    provideHttpClient(withInterceptors([authInterceptor])),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000'
    }),
    // ★ 啟動時就開始監聽版本更新。
    //   少了這一段，每次部署新版本，所有已經用過的裝置都會因為快取指向
    //   已不存在的檔案而打不開，使用者得自己去開發者工具清 service worker
    //   才能救回來 —— 一般人根本不會做那件事。
    provideAppInitializer(() => inject(ServiceWorkerUpdater).start()),
  ]
};
