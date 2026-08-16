package com.tim.language_project.client;

import com.tim.language_project.client.model.SegmentationResult;
import com.tim.language_project.client.model.VariantResult;
import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;

/**
 * 中泰互譯，附帶羅馬拼音、單字的多種說法，以及（另外呼叫的）逐詞拆解。
 * 抽成介面是為了隔離服務商，日後要換掉 OpenAI 只需新增一個實作。
 * 實作類別要自己負責記錄用量。
 *
 * ── ★ 為什麼翻譯與逐詞拆解是兩個方法（2026-08-16 改的）─────────────────
 *
 *  原本一次呼叫就把翻譯、拼音、逐詞拆解、多重說法全部要齊，
 *  實測平均輸出 867 個 token。而大型語言模型是一個 token 一個 token 吐出來的，
 *  以 gpt-5.5 每秒約 40 個 token 計算，光是「產出」就要 22 秒 ——
 *  跟實際量到的等待時間完全吻合。
 *
 *  ★ 逐詞拆解佔了那 867 個 token 的一大半，但使用者按下查詢的那一刻
 *    最想看到的是泰文和拼音；逐詞拆解是看完之後才會慢慢研究的東西。
 *
 *  拆開之後：translate 只產出約 100～150 個 token（約 3～5 秒就看得到結果），
 *  segment 則等使用者真的點了「逐詞拆解」才呼叫 —— 沒點就完全不花那筆錢。
 */
public interface TranslationClient {

    /**
     * 翻譯整句，回傳泰文、中文與羅馬拼音。
     *
     * ★ 只有整句。逐詞拆解與多種說法都不在這裡，各自是獨立的呼叫。
     *
     * @param gender 說話者性別，影響泰文造句的自稱與句尾助詞。
     *               泰翻中沒有性別概念，該方向傳 null。
     */
    TranslationResult translate(String sourceText,
                                TranslationDirectionEnum direction,
                                SpeakerGenderEnum gender);

    /**
     * 把已經翻好的一組中泰對照，拆解成逐詞對照。
     *
     * ★ 傳入的是「翻譯完成後的兩面」而不是原始輸入，這樣模型不必重新翻譯一次，
     *   只要專心做切分，輸出量與出錯機會都比較小。
     *
     * @param chineseText 這句話的中文面
     * @param thaiText    這句話的泰文面
     */
    SegmentationResult segment(String chineseText, String thaiText);

    /**
     * 列出一個詞在泰文裡的各種說法（不同性別、不同禮貌程度）。
     *
     * ★ 只對「單一個詞」有意義。整句沒有「另一種說法」這種東西，
     *   呼叫端要自己判斷該不該問（見 TranslationService）。
     *
     * @param chineseText 這個詞的中文
     * @param thaiText    這個詞的泰文（其中一種說法，用來讓模型對齊語意）
     */
    VariantResult variants(String chineseText, String thaiText);
}
