package com.tim.language_project.client;

import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;

/**
 * 中泰互譯，附帶羅馬拼音、逐詞拆解，以及單字的多種說法。
 * 抽成介面是為了隔離服務商，日後要換掉 OpenAI 只需新增一個實作。
 * 實作類別要自己負責記錄用量。
 */
public interface TranslationClient {

    /**
     * @param gender 說話者性別，影響泰文造句的自稱與句尾助詞。
     *               泰翻中沒有性別概念，該方向傳 null。
     */
    TranslationResult translate(String sourceText,
                                TranslationDirectionEnum direction,
                                SpeakerGenderEnum gender);
}
