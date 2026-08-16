/**
 * 後端回傳的資料形狀。
 * 這裡只有型別宣告、沒有任何執行流程，所以不加流程註解。
 *
 * ★ 欄位名稱必須與後端的 record 完全一致（含大小寫）。
 *   對不上不會有任何錯誤訊息，只會安靜地拿到 undefined。
 */

/** 說話者性別，對應後端 SpeakerGenderEnum。 */
export type SpeakerGender = 'MALE' | 'FEMALE';

/** 翻譯方向，對應後端 TranslationDirectionEnum。由後端依輸入自動判斷。 */
export type TranslationDirection = 'ZH_TO_TH' | 'TH_TO_ZH';

/** 一個說法適合誰用，對應後端 GenderUsageEnum。 */
export type GenderUsage = 'MALE' | 'FEMALE' | 'BOTH';

/** 禮貌程度，對應後端 PolitenessEnum。 */
export type Politeness = 'FORMAL' | 'NEUTRAL' | 'CASUAL' | 'RUDE';

/** 音檔語言，對應後端 SpeechLanguageEnum。 */
export type SpeechLanguage = 'TH' | 'ZH';

/** 對應後端 TranslationSegmentDto，一列逐詞對照。 */
export interface TranslationSegment {
  seqNo: number;
  chineseText: string;
  thaiText: string;
  romanization: string;
  /** null 代表音檔還沒產生，顯示成灰色的播放鍵，點擊才會產生。 */
  thaiAudioUrl: string | null;
  chineseAudioUrl: string | null;
}

/** 對應後端 TranslationVariantDto，一個詞的其中一種說法。 */
export interface TranslationVariant {
  thaiText: string;
  romanization: string;
  genderUsage: GenderUsage;
  politeness: Politeness;
  note: string;
  thaiAudioUrl: string | null;
}

/**
 * 對應後端 TranslationResponseDto，一次查詢的整句結果。
 *
 * ★ 2026-08-16 起這裡「只有整句」。
 *   逐詞拆解與各種說法拿掉了，改由使用者點按鈕後各自呼叫：
 *
 *       POST /api/v1/translations/{queryId}/segments
 *       POST /api/v1/translations/{queryId}/variants
 *
 *   理由：原本一次呼叫要模型產出這三樣，平均輸出 867 個 token，
 *   光是產出就要二十幾秒。而按下查詢的那一刻最想看到的只有泰文和拼音。
 */
export interface TranslationResponse {
  /** 這筆查詢在資料庫的 id，點「逐詞拆解」「各種說法」時要帶回去。 */
  queryId: number;
  sourceText: string;
  direction: TranslationDirection;
  gender: SpeakerGender | null;
  chineseText: string;
  thaiText: string;
  romanization: string;
  /** 語音合成失敗或還沒產生時是 null，此時播放鍵是灰的。 */
  thaiAudioUrl: string | null;
  chineseAudioUrl: string | null;
  /** true 代表這次讀快取、沒有呼叫 OpenAI，也就是沒有花錢。 */
  fromCache: boolean;
}

/** 對應後端 AudioResponseDto。 */
export interface AudioResponse {
  audioUrl: string;
}

/** 對應後端 ErrorResponseDto，所有錯誤都是這個格式。 */
export interface ErrorResponse {
  code: string;
  message: string;
  traceId: string;
}
