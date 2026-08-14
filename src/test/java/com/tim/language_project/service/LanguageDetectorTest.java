package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  LanguageDetector 決定「你打的這串字，要走中翻泰還是泰翻中」。
 *  判斷錯的後果不是報錯，是安靜地翻反方向 —— 你輸入泰文，
 *  系統以為你在講中文，把泰文原封不動再「翻」一次泰文給你。
 *
 *  這個檔案沒有換掉任何東西（沒有 mock）。LanguageDetector 只看字串本身，
 *  不連資料庫、不連網路，所以直接 new 一個出來測就好。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  測試一  純中文「我想喝酒」   → 要走中翻泰。這是最常見的用法，壞了就整個網站沒用
 *  測試二  純泰文「ผมอยากดื่มเหล้า」→ 要走泰翻中。新功能的入口
 *  測試三  中泰混合             → 要走泰翻中。使用者貼上一段有註解的泰文時會發生
 *  測試四  ★純數字「5」        → 要走中翻泰。
 *                                這一題最重要 —— 現有的提示詞明確支援數字輸入
 *                                （「5」會翻成「ห้า」）。如果有人把判斷邏輯改成
 *                                「不是中文也不是泰文就報錯」，這個現有功能會壞掉，
 *                                而且不會有人發現，因為沒人會特地去測數字。
 *  測試五  亂碼「asdfgh」       → 要走中翻泰，交給 AI 的 translatable 去擋，
 *                                不在這裡報錯（維持現有行為）
 */

import com.tim.language_project.enums.TranslationDirectionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageDetectorTest {

    private final LanguageDetector languageDetector = new LanguageDetector();

    @Test
    @DisplayName("純中文應判為中翻泰")
    void shouldDetectChineseAsZhToTh() {
        assertThat(languageDetector.detect("我想喝酒"))
                .isEqualTo(TranslationDirectionEnum.ZH_TO_TH);
    }

    @Test
    @DisplayName("純泰文應判為泰翻中")
    void shouldDetectThaiAsThToZh() {
        assertThat(languageDetector.detect("ผมอยากดื่มเหล้า"))
                .isEqualTo(TranslationDirectionEnum.TH_TO_ZH);
    }

    @Test
    @DisplayName("中泰混合時泰文優先，判為泰翻中")
    void shouldDetectMixedAsThToZh() {
        assertThat(languageDetector.detect("ผม（我）"))
                .isEqualTo(TranslationDirectionEnum.TH_TO_ZH);
    }

    /*
     * ★ 這個測試是為了保住一個現有功能。
     *   提示詞裡明確寫著「包含數字，例如『5』就是『ห้า』」，
     *   所以純數字必須能正常走完中翻泰的流程，不可以被判斷邏輯擋在門外。
     */
    @Test
    @DisplayName("純數字應判為中翻泰，不可被擋下")
    void shouldDetectDigitsAsZhToTh() {
        assertThat(languageDetector.detect("5"))
                .isEqualTo(TranslationDirectionEnum.ZH_TO_TH);
    }

    @Test
    @DisplayName("亂碼應判為中翻泰，由模型自行回報無法翻譯")
    void shouldDetectGibberishAsZhToTh() {
        assertThat(languageDetector.detect("asdfgh"))
                .isEqualTo(TranslationDirectionEnum.ZH_TO_TH);
    }
}
