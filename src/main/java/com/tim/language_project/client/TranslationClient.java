package com.tim.language_project.client;

import com.tim.language_project.client.model.TranslationResult;

/**
 * 把中文翻成泰文，附帶羅馬拼音與逐詞拆解。
 * 抽成介面是為了隔離服務商，日後要換掉 OpenAI 只需新增一個實作。
 * 實作類別要自己負責記錄用量。
 */
public interface TranslationClient {

    TranslationResult translate(String sourceText);
}
