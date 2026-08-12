package com.tim.language_project.controller;

/*
 * ── 這個檔案在測什麼 ────────────────────────────────────────────────────
 *
 *  測 VocabularyController —— 單字庫的兩個瀏覽端點。
 *
 *  跟 TranslationControllerTest 是同一種做法：@WebMvcTest 只啟動網頁那一塊，
 *  Service 用 @MockitoBean 換成假的，不連資料庫。
 *
 * ── 流程（以測試二為例）────────────────────────────────────────────────
 *
 *    mockMvc.perform(get("/api/v1/vocabularies/999"))
 *      │
 *      ├→ Spring 依網址找到 findById 方法，把網址裡的 999 收成參數 id
 *      │
 *      ├→ 呼叫 vocabularyService.findById(999L)
 *      │     └─ 【假的】依照劇本丟出 BusinessException(VOCABULARY_NOT_FOUND)
 *      │
 *      ├→ 例外往上冒，GlobalExceptionHandler 接住
 *      │
 *      └→ MockMvc 收到 HTTP 404 與統一格式的錯誤 JSON
 *
 * ── 兩個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *    一  列表     防：分頁資料轉成 JSON 時出問題、欄位名稱不對
 *    二  查不到   防：★找不到卻回 500★
 *
 *    ★ 測試二特別重要。VOCABULARY_NOT_FOUND 這個錯誤碼定義很久了，
 *      但在這之前「從來沒有任何程式真的丟出過它」——
 *      也就是說，它到底能不能正確變成 404，從來沒被驗證過。
 *      這支測試是第一次真的走完那條路。
 */

import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.service.VocabularyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VocabularyController.class)
class VocabularyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VocabularyService vocabularyService;

    @Test
    @DisplayName("單字列表應回傳分頁內容")
    void shouldReturnVocabularyPage() throws Exception {
        Page<VocabularyDto> page = new PageImpl<>(
                List.of(new VocabularyDto(7L, "酒", "เหล้า", "lâo")),
                PageRequest.of(0, 20), 1);

        when(vocabularyService.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/vocabularies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].chineseText").value("酒"))
                .andExpect(jsonPath("$.content[0].thaiText").value("เหล้า"));
    }

    @Test
    @DisplayName("查不到單字應回 404 與 VOCABULARY_NOT_FOUND")
    void shouldReturnNotFoundForUnknownVocabulary() throws Exception {
        when(vocabularyService.findById(999L))
                .thenThrow(new BusinessException(ErrorCodeEnum.VOCABULARY_NOT_FOUND));

        mockMvc.perform(get("/api/v1/vocabularies/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCodeEnum.VOCABULARY_NOT_FOUND.name()))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
