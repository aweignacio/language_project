package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  看一眼你打進來的字，決定這次要「中翻泰」還是「泰翻中」。
 *
 *  為什麼需要它：這個網站兩個方向都支援，但畫面上「沒有」切換方向的按鈕。
 *  你打什麼，系統就自己知道你要什麼。這個檔案就是那個「自己知道」。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你打字到決定方向
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在網頁輸入「ผมอยากดื่มเหล้า」，按下查詢 ──────────────────
 *
 *    TranslationService 拿到這串字之後，第一件事就是問這裡：
 *
 *        languageDetector.detect("ผมอยากดื่มเหล้า");
 *
 * ── 第 2 步｜看這串字裡面有沒有泰文字 ───────────────────────────────────
 *
 *    每一個字在電腦裡都是一個編號（叫做 Unicode 碼位）。
 *    泰文字的編號全部落在 0E00 到 0E7F 這一段，中文則在 4E00 到 9FFF。
 *    這不是猜的，是查表 —— 所以判斷結果是確定的，不會有模糊地帶。
 *
 *        "ผมอยากดื่มเหล้า" 的第一個字 ผ 編號是 0E1C  → 落在泰文區間 → 有泰文
 *
 *    有泰文  → TH_TO_ZH（泰翻中）
 *    沒泰文  → ZH_TO_TH（中翻泰）
 *
 * ── 第 3 步｜回傳方向，後面的流程照這個方向走 ───────────────────────────
 *
 *    TranslationService 會用它決定：查快取時 direction 欄位填什麼、
 *    呼叫 AI 時要用哪一套提示詞、gender 要不要存。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  ★ 兩個最容易被改壞的地方
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  ★ 一：這個方法「永遠不會失敗」，不要幫它加錯誤處理。
 *
 *    看起來很自然的一個「改進」是：
 *
 *        既不是中文也不是泰文 → 丟一個「不支援的語言」錯誤
 *
 *    那是錯的，會弄壞一個現有功能。提示詞裡明確支援數字輸入
 *    （見 OpenAiTranslationClient 的 SYSTEM_PROMPT：「包含數字，例如『5』就是『ห้า』」）。
 *    你輸入「5」，字元既不是中文也不是泰文，加了那個判斷就會被擋在門外。
 *
 *    亂碼要怎麼辦？交給 AI。它會回 translatable = false，
 *    TranslationService 就會擋下來。那條路本來就存在，不需要在這裡再擋一次。
 *
 *  ★ 二：為什麼是「有泰文就算泰文」，而不是「哪種字多算哪種」？
 *
 *    因為使用者貼上泰文時，常常會連著中文註解一起貼，例如「ผม（我）」。
 *    這種情況他要的是「幫我看懂這段泰文」，不是「幫我把『我』翻成泰文」。
 *
 *  測試檔：src/test/java/com/tim/language_project/service/LanguageDetectorTest.java
 */

import com.tim.language_project.enums.TranslationDirectionEnum;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 依輸入的字元範圍判斷翻譯方向。
 * 這個判斷不會失敗，理由見檔案開頭的★一。
 */
@Component
public class LanguageDetector {

    /** 泰文的 Unicode 區間。 */
    private static final Pattern THAI_PATTERN = Pattern.compile("[\\u0E00-\\u0E7F]");

    /**
     * 判斷翻譯方向。含泰文字就是泰翻中，其餘一律中翻泰（包含純數字）。
     */
    public TranslationDirectionEnum detect(String sourceText) {
        if (THAI_PATTERN.matcher(sourceText).find()) {
            return TranslationDirectionEnum.TH_TO_ZH;
        }

        return TranslationDirectionEnum.ZH_TO_TH;
    }
}
