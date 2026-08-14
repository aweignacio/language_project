package com.tim.language_project.service;

import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.repository.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 單字庫的讀取。
 * 這裡只有讀，沒有寫 —— 單字是查詢流程自己沉澱進去的，
 * 使用者不能手動新增或修改。
 */
@Service
@RequiredArgsConstructor
public class VocabularyService {

    private final VocabularyRepository vocabularyRepository;

    /**
     * 單字列表，最新的排前面。
     * 用分頁而非全部撈出來，因為這張表會隨著使用越長越大。
     */
    public Page<VocabularyDto> findAll(Pageable pageable) {
        return vocabularyRepository.findAllOrderByIdDesc(pageable);
    }

    /**
     * 查單一個單字。找不到就丟 VOCABULARY_NOT_FOUND，由全域處理器轉成 404。
     */
    public VocabularyDto findById(Long id) {
        return vocabularyRepository.findById(id)
                .map(vocabulary -> new VocabularyDto(
                        vocabulary.getId(),
                        vocabulary.getChineseText(),
                        vocabulary.getThaiText(),
                        vocabulary.getRomanization(),
                        vocabulary.getGenderUsage(),
                        vocabulary.getPoliteness(),
                        vocabulary.getNote()))
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.VOCABULARY_NOT_FOUND));
    }
}
