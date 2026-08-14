package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  一次翻譯成功之後，把結果寫進三張表。全部綁在同一個交易裡 ——
 *  要嘛三張都成功，要嘛全部當作沒發生。
 *
 *  不能只寫一半：留下一筆沒有逐詞資料的翻譯，畫面上就會出現
 *  「有泰文但沒有逐詞對照」的殘缺結果。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：接續「我想喝酒」翻譯與語音都完成之後
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜TranslationService 呼叫 persist ────────────────────────────
 *
 *        persist("我想喝酒", result, "a3f9c2b81e47.mp3")
 *                 ↑原文      ↑翻譯結果  ↑音檔檔名（語音失敗時是 null）
 *
 * ── 第 2 步｜寫入 translation_query（快取）─────────────────────────────
 *
 *        id  source_text  thai_text          romanization          audio_file
 *        ──  ───────────  ────────────────   ──────────────────    ──────────────
 *        42  我想喝酒     ฉันอยากดื่มเหล้า      chǎn yàak dùuem lâo   a3f9c2b81e47.mp3
 *
 *    ★ 用 saveAndFlush 而不是 save，是為了「現在就拿到 id」。
 *      下一步的逐詞資料要用這個 id 當外鍵，不先送出去就拿不到編號。
 *
 * ── 第 3 步｜寫入 translation_segment（逐詞，四筆）──────────────────────
 *
 *        query_id  seq_no  chinese_text  thai_text  romanization
 *        ────────  ──────  ────────────  ─────────  ────────────
 *        42        1       我            ฉัน         chǎn
 *        42        2       想            อยาก        yàak
 *        42        3       喝            ดื่ม         dùuem
 *        42        4       酒            เหล้า        lâo
 *
 *    seq_no 從 1 開始遞增，前端就是照這個順序排版的。
 *
 * ── 第 4 步｜沉澱單字（只寫沒有的）─────────────────────────────────────
 *
 *    先一次問資料庫「這四個詞裡面，哪些已經有了？」
 *
 *        List<String> existing = findExistingChineseTexts(["我","想","喝","酒"]);
 *        // 假設回傳 ["我", "水"] 之外的都沒有 → 只寫入沒有的那些
 *
 *    ★ 為什麼要一次問四個，而不是一個一個問？
 *      一次來回 vs 四次來回，資料庫往返的成本差很多。
 *
 *    ★ sourceType 怎麼決定？
 *        使用者查的就是這個詞本身（查「水」）→ DIRECT
 *        這個詞是從句子拆出來的（查「我想喝酒」拆出「水」）→ SEGMENT
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  為什麼寫入要獨立成一個 Service，不寫在 TranslationService 裡
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  跟 ApiUsageLogWriter 是同一個原因：
 *  @Transactional 靠「代理」實作，同一個類別內部呼叫自己的方法會跳過代理，
 *  交易根本不會啟動，而且不會有任何錯誤訊息。
 *
 *  ★ 另一個更重要的理由：呼叫 OpenAI 要好幾秒。
 *
 *    如果整個 translate 流程都包在交易裡，那幾秒鐘會一直佔著一條資料庫連線。
 *    連線是有限的資源，幾個人同時查就會把連線池吃光，整個網站卡住。
 *
 *    所以設計成：外部呼叫在交易外面跑完，最後才進來這裡「快速寫入、快速結束」。
 */

import com.tim.language_project.client.model.TranslationResult;
import com.tim.language_project.client.model.TranslationWord;
import com.tim.language_project.entity.TranslationQuery;
import com.tim.language_project.entity.TranslationSegment;
import com.tim.language_project.entity.Vocabulary;
import com.tim.language_project.enums.TranslationDirectionEnum;
import com.tim.language_project.enums.VocabularySourceTypeEnum;
import com.tim.language_project.repository.TranslationQueryRepository;
import com.tim.language_project.repository.TranslationSegmentRepository;
import com.tim.language_project.repository.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 把一次完成的翻譯寫進快取、逐詞、單字庫三張表，全部在同一個交易裡。
 * 刻意與 TranslationService 分開，同類別內部呼叫會跳過交易代理。
 */
@Service
@RequiredArgsConstructor
public class TranslationPersistenceService {

    private final TranslationQueryRepository translationQueryRepository;

    private final TranslationSegmentRepository translationSegmentRepository;

    private final VocabularyRepository vocabularyRepository;

    /**
     * 寫入一次完整的查詢結果，回傳快取那一筆的 id。
     */
    @Transactional
    public Long persist(String sourceText, TranslationResult result, String audioFile) {
        TranslationQuery query = new TranslationQuery();
        // 這幾行是暫時的：方向、性別、中文面與多重說法在 Task 12 才會正式接進來。
        query.setSourceText(sourceText);
        query.setDirection(TranslationDirectionEnum.ZH_TO_TH);
        query.setChineseText(sourceText);
        query.setThaiText(result.thaiText());
        query.setRomanization(result.romanization());

        // saveAndFlush 是為了立刻拿到資料庫產生的 id，下一步當外鍵用。
        TranslationQuery savedQuery = translationQueryRepository.saveAndFlush(query);

        persistSegments(savedQuery.getId(), result.words());
        persistNewVocabulary(sourceText, result.words());

        return savedQuery.getId();
    }

    private void persistSegments(Long queryId, List<TranslationWord> words) {
        if (ObjectUtils.isEmpty(words)) {
            return;
        }

        List<TranslationSegment> segments = new ArrayList<>();
        int seqNo = 1;

        for (TranslationWord word : words) {
            TranslationSegment segment = new TranslationSegment();
            segment.setQueryId(queryId);
            segment.setSeqNo(seqNo++);
            segment.setChineseText(word.chineseText());
            segment.setThaiText(word.thaiText());
            segment.setRomanization(word.romanization());
            segments.add(segment);
        }

        translationSegmentRepository.saveAll(segments);
    }

    /**
     * 把拆解出來的詞沉澱進單字庫，已經存在的略過。
     * 已存在的不覆蓋 —— 那個詞當初是怎麼進來的屬於歷史，不該被改寫。
     */
    private void persistNewVocabulary(String sourceText, List<TranslationWord> words) {
        if (ObjectUtils.isEmpty(words)) {
            return;
        }

        List<String> chineseTexts = words.stream()
                .map(TranslationWord::chineseText)
                .distinct()
                .toList();

        if (ObjectUtils.isEmpty(chineseTexts)) {
            return;
        }

        List<String> existing = vocabularyRepository.findExistingChineseTexts(chineseTexts);

        List<Vocabulary> newEntries = new ArrayList<>();

        for (TranslationWord word : words) {
            if (existing.contains(word.chineseText())
                    || containsChineseText(newEntries, word.chineseText())) {
                continue;
            }

            Vocabulary vocabulary = new Vocabulary();
            vocabulary.setChineseText(word.chineseText());
            vocabulary.setThaiText(word.thaiText());
            vocabulary.setRomanization(word.romanization());
            vocabulary.setSourceType(Objects.equals(sourceText, word.chineseText())
                    ? VocabularySourceTypeEnum.DIRECT
                    : VocabularySourceTypeEnum.SEGMENT);
            newEntries.add(vocabulary);
        }

        if (!ObjectUtils.isEmpty(newEntries)) {
            vocabularyRepository.saveAll(newEntries);
        }
    }

    /**
     * 防止同一句話裡重複出現的詞被寫入兩次（例如「喝酒喝酒」）。
     */
    private boolean containsChineseText(List<Vocabulary> entries, String chineseText) {
        return entries.stream()
                .anyMatch(entry -> Objects.equals(entry.getChineseText(), chineseText));
    }
}
