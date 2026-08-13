import { Component } from '@angular/core';
import { Translation } from './translation/translation';

/**
 * 根元件。這個專案只有一個畫面，所以它只負責把查詢元件放上去。
 */
@Component({
  selector: 'app-root',
  imports: [Translation],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
}
