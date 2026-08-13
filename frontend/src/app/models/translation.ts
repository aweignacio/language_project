/**
 * 後端回傳的資料形狀。
 * 這裡只有型別宣告、沒有任何執行流程，所以不加流程註解。
 *
 * ★ 欄位名稱必須與後端的 record 完全一致（含大小寫）。
 *   對不上不會有任何錯誤訊息，只會安靜地拿到 undefined。
 */

/** 對應後端 TranslationSegmentDto，一列逐詞對照。 */
export interface TranslationSegment {
  seqNo: number;
  chineseText: string;
  thaiText: string;
  romanization: string;
}

/** 對應後端 TranslationResponseDto，一次查詢的完整結果。 */
export interface TranslationResponse {
  sourceText: string;
  thaiText: string;
  romanization: string;
  /** 語音合成失敗時是 null，此時不顯示播放鍵。 */
  audioUrl: string | null;
  /** true 代表這次讀快取、沒有呼叫 OpenAI，也就是沒有花錢。 */
  fromCache: boolean;
  segments: TranslationSegment[];
}

/** 對應後端 ErrorResponseDto，所有錯誤都是這個格式。 */
export interface ErrorResponse {
  code: string;
  message: string;
  traceId: string;
}
