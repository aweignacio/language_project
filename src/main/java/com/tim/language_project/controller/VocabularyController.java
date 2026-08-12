package com.tim.language_project.controller;

/*
 * ── 這個檔案負責什麼 ────────────────────────────────────────────────────
 *
 *  單字庫的瀏覽端點。跟翻譯那條路完全分開 ——
 *  這裡只讀資料庫，不會呼叫 OpenAI，永遠不花錢。
 *
 *  單字是怎麼進去的？使用者每查一句，句子被拆成的每個詞都會沉澱進單字庫
 *  （見 TranslationPersistenceService）。這裡只是把累積的成果拿出來看。
 *
 * ── 兩個端點 ────────────────────────────────────────────────────────────
 *
 *    GET /api/v1/vocabularies         列表，最新的排前面，一頁 20 筆
 *    GET /api/v1/vocabularies/7       查第 7 筆；不存在就回 404
 *
 * ── 分頁怎麼運作 ────────────────────────────────────────────────────────
 *
 *    GET /api/v1/vocabularies?page=0&size=20
 *                              ↑ 第幾頁（從 0 開始）  ↑ 一頁幾筆
 *
 *    Spring 會自動把這兩個參數收成一個 Pageable 物件，我們不用自己解析。
 *    @PageableDefault 是「前端沒指定時用這個」，避免有人不帶參數
 *    就把整張表撈出來。
 *
 *    回傳的 Page 除了那一頁的資料，還附帶總筆數與總頁數，
 *    前端要畫「第 3 / 12 頁」就是靠這些。
 *
 * ── 為什麼這裡是 GET ────────────────────────────────────────────────────
 *
 *    因為它只讀取、不改變任何東西 —— 這正是 GET 的用途。
 *    翻譯那支用 POST 是因為它會寫資料庫、生檔案。
 */

import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.service.VocabularyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 單字庫的瀏覽端點。
 */
@RestController
@RequestMapping("/api/v1/vocabularies")
@RequiredArgsConstructor
public class VocabularyController {

    private final VocabularyService vocabularyService;

    @GetMapping
    public ResponseEntity<Page<VocabularyDto>> findAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(vocabularyService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VocabularyDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(vocabularyService.findById(id));
    }
}
