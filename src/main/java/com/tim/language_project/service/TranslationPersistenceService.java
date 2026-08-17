package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  翻譯完成之後，把結果寫進資料庫。每個寫入方法各自是一個交易 ——
 *  該方法要寫的表要嘛全部成功，要嘛全部當作沒發生。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  ★ 三個寫入方法，對應使用者的三個動作（2026-08-16 改的）
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  以前只有一個 persist，一口氣寫三張表 —— 因為當時一次 OpenAI 呼叫就會
 *  把翻譯、逐詞、各種說法全部拿回來。
 *
 *  現在那一次呼叫被拆成三次（理由見 TranslationService 第 9 步：
 *  一次要齊平均輸出 867 個 token，光產出就要 22 秒），寫入自然也跟著拆：
 *
 *      你按下查詢          → persist              → translation_query
 *      你點「逐詞拆解」    → persistSegmentation  → translation_segment ＋ vocabulary
 *      你點「各種說法」    → persistVariants      → vocabulary
 *
 *  ★ 為什麼不能合成一個交易？因為它們根本不同時發生 ——
 *    後兩個可能是你三分鐘後才點的，也可能一輩子不點。
 *
 *  ★ 因此「有泰文但沒有逐詞對照」現在是正常狀態，不是殘缺。
 *    那代表你還沒點開它。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：接續「我想喝酒」翻譯與語音都完成之後
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜TranslationService 呼叫 persist ────────────────────────────
 *
 *        persist("我想喝酒", ZH_TO_TH, MALE, result)
 *                 ↑原文     ↑方向     ↑性別  ↑翻譯結果（只有整句）
 *
 *    ★ 2026-08-14 起「不再收音檔檔名」。
 *      音檔改由 AudioAssetService 以文字內容為鍵統一管理，
 *      這張表只存文字。
 *
 * ── 第 2 步｜寫入 translation_query（快取），回傳 id ───────────────────
 *
 *        id  source_text  direction  gender  chinese_text  thai_text            romanization
 *        ──  ───────────  ─────────  ──────  ────────────  ──────────────────   ───────────────────
 *        42  我想喝酒     ZH_TO_TH   MALE    我想喝酒      ผมอยากดื่มเหล้าครับ    pǒm yàak dùuem lâo khráp
 *
 *    ★ 快取的鑰匙是「原文＋方向＋性別」三欄的組合，不是只有原文。
 *      同一句話的男版與女版泰文真的不一樣，必須各存一筆。
 *      泰翻中沒有性別概念，gender 存 null。
 *
 *    ★ 用 saveAndFlush 而不是 save，是為了「現在就拿到 id」。
 *      這個 id 會一路回傳給前端當 queryId，之後你點「逐詞拆解」或
 *      「各種說法」時就是拿它打回來的。
 *
 *    ★ persist 到這裡就結束了。下面兩步是你點了按鈕才發生的事。
 *
 * ── 第 3 步｜你點「逐詞拆解」→ persistSegmentation（逐詞，四筆）─────────
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
 * ── 第 4 步｜沉澱單字（新的寫進去，舊的補齊欄位）───────────────────────
 *
 *    要寫的東西有兩種來源，看是哪個方法叫進來的：
 *
 *        persistVariants → 你點了「各種說法」（你查的是一個詞）。
 *                 把每一種說法各寫一列，帶著性別、禮貌、說明：
 *
 *                     chinese_text  thai_text  gender_usage  politeness  note
 *                     ────────────  ─────────  ────────────  ──────────  ──────────
 *                     我            ผม          MALE          FORMAL      男生自稱
 *                     我            ฉัน          FEMALE        FORMAL      女生自稱
 *                     我            กู           BOTH          RUDE        很不客氣
 *
 *        persistSegmentation → 你點了「逐詞拆解」。把逐詞各寫一列，
 *                 那三個標籤是 null —— 拆解時我們不會去問模型
 *                 「每個詞各適合誰用」，那是「各種說法」那一支的工作。
 *
 *    接著一次問資料庫「這些詞裡面，哪些已經有了？」
 *
 *        List<Vocabulary> existing = findAllByChineseTextIn(["我"]);
 *
 *    ★ 撈的是「整個實體」不是只撈中文字，因為已存在的列不是單純跳過：
 *
 *        第 1 天  你查「我想喝酒」→「我 → ฉัน」被沉澱進來，三個標籤是空的
 *        第 3 天  你單獨查「我」  → 模型給了完整標籤
 *                                → ★ ฉัน 那一列要被「補齊」，不是跳過
 *
 *      漏掉這件事的話，「我」「你」「他」這些最常出現在句子裡的詞，
 *      會永遠停在沒有標籤的狀態 —— 而那正是這次改版最想解決的詞。
 *
 *    ★ 但已經「有值」的欄位一律不覆蓋。那是先前寫入的歷史，不該被改寫。
 *
 *    ★ sourceType 怎麼決定？
 *        使用者查的就是這個詞本身（查「水」）→ DIRECT
 *        這個詞是從句子拆出來的（查「我想喝酒」拆出「水」）→ SEGMENT
 *      已存在的列不更新這一欄，以首次寫入的值為準。
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
import com.tim.language_project.client.model.TranslationVariant;
import com.tim.language_project.client.model.TranslationWord;
import com.tim.language_project.entity.TranslationQuery;
import com.tim.language_project.entity.TranslationSegment;
import com.tim.language_project.entity.Vocabulary;
import com.tim.language_project.enums.SpeakerGenderEnum;
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
     * 寫入整句的翻譯結果，回傳快取那一筆的 id。
     * 音檔不在這裡處理 —— 全站的音檔由 AudioAssetService 統一管理。
     *
     * ★ 2026-08-16 起這裡「只寫整句那一列」。
     *   逐詞與各種說法是使用者點了才跑的獨立呼叫，各自有自己的寫入方法。
     */
    @Transactional
    public Long persist(String sourceText,
                        TranslationDirectionEnum direction,
                        SpeakerGenderEnum gender,
                        TranslationResult result) {
        TranslationQuery query = new TranslationQuery();
        query.setSourceText(sourceText);
        query.setDirection(direction);
        // ★ 泰翻中沒有性別概念，這裡會是 null，資料表允許。
        query.setGender(gender);
        query.setChineseText(result.chineseText());
        query.setThaiText(result.thaiText());
        query.setRomanization(result.romanization());
        // 模型沒給的話這裡是 null，代表「不知道是詞還是句子」，前端會照常顯示按鈕。
        query.setIsWord(result.isWord());

        // saveAndFlush 是為了立刻拿到資料庫產生的 id，下一步當外鍵用。
        return translationQueryRepository.saveAndFlush(query).getId();
    }

    /**
     * 寫入逐詞拆解：translation_segment 一列一個詞，同時把這些詞沉澱進單字庫。
     * 使用者點開「逐詞拆解」時才會走到這裡。
     *
     * @param queryId    這次拆解屬於哪一筆查詢，當外鍵用
     * @param sourceText 使用者當初輸入的原文，用來判斷單字庫的 source_type
     */
    @Transactional
    public void persistSegmentation(Long queryId, String sourceText, List<TranslationWord> words) {
        if (ObjectUtils.isEmpty(words)) {
            return;
        }

        persistSegments(queryId, words);
        persistVocabulary(fromWords(sourceText, words));
    }

    /**
     * 寫入各種說法：只沉澱進單字庫，沒有對應的 segment。
     * 使用者點開「各種說法」時才會走到這裡。
     *
     * @param chineseText 這個詞的中文面，單字庫的每一列都掛在它底下
     */
    @Transactional
    public void persistVariants(String sourceText,
                                String chineseText,
                                List<TranslationVariant> variants) {
        if (ObjectUtils.isEmpty(variants)) {
            return;
        }

        persistVocabulary(fromVariants(sourceText, chineseText, variants));
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
     * 把一批候選單字沉澱進單字庫。
     *
     * 兩種來源，由呼叫端先轉成 Vocabulary 再交進來：
     *   fromVariants → 各種說法，每一種各一列，帶性別／禮貌標籤
     *   fromWords    → 逐詞拆解，每個詞各一列，沒有標籤
     *
     * ★ 已經存在的說法不是單純「跳過」。
     *   如果它的性別／禮貌／說明是空的（代表它當初是從句子沉澱下來的），
     *   而這次拿到了有值的資料，就要補上去。漏掉這件事的話，
     *   「我」「你」「他」這些最常出現在句子裡的詞，
     *   會永遠停在沒有標籤的狀態 —— 而那正是這次改版最想解決的詞。
     */
    private void persistVocabulary(List<Vocabulary> candidates) {
        if (ObjectUtils.isEmpty(candidates)) {
            return;
        }

        List<String> chineseTexts = candidates.stream()
                .map(Vocabulary::getChineseText)
                .distinct()
                .toList();

        List<Vocabulary> existingEntries =
                vocabularyRepository.findAllByChineseTextIn(chineseTexts);

        List<Vocabulary> newEntries = new ArrayList<>();

        for (Vocabulary candidate : candidates) {
            Vocabulary existing = findExisting(existingEntries, candidate);

            if (Objects.isNull(existing)) {
                if (!containsSameWord(newEntries, candidate)) {
                    newEntries.add(candidate);
                }
                continue;
            }

            fillMissingLabels(existing, candidate);
        }

        if (!ObjectUtils.isEmpty(newEntries)) {
            vocabularyRepository.saveAll(newEntries);
        }
    }

    /**
     * 單字查詢：每一種說法各一列，帶著性別與禮貌標籤。
     */
    private List<Vocabulary> fromVariants(String sourceText,
                                          String chineseText,
                                          List<TranslationVariant> variants) {
        List<Vocabulary> entries = new ArrayList<>();

        for (TranslationVariant variant : variants) {
            Vocabulary vocabulary = new Vocabulary();
            vocabulary.setChineseText(chineseText);
            vocabulary.setThaiText(variant.thaiText());
            vocabulary.setRomanization(variant.romanization());
            vocabulary.setGenderUsage(variant.genderUsage());
            vocabulary.setPoliteness(variant.politeness());
            vocabulary.setNote(variant.note());
            vocabulary.setSourceType(Objects.equals(sourceText, chineseText)
                    ? VocabularySourceTypeEnum.DIRECT
                    : VocabularySourceTypeEnum.SEGMENT);
            entries.add(vocabulary);
        }

        return entries;
    }

    /**
     * 句子查詢：逐詞各一列，沒有標籤（翻句子時不會問模型要那些資訊）。
     */
    private List<Vocabulary> fromWords(String sourceText, List<TranslationWord> words) {
        if (ObjectUtils.isEmpty(words)) {
            return List.of();
        }

        List<Vocabulary> entries = new ArrayList<>();

        for (TranslationWord word : words) {
            Vocabulary vocabulary = new Vocabulary();
            vocabulary.setChineseText(word.chineseText());
            vocabulary.setThaiText(word.thaiText());
            vocabulary.setRomanization(word.romanization());
            vocabulary.setSourceType(Objects.equals(sourceText, word.chineseText())
                    ? VocabularySourceTypeEnum.DIRECT
                    : VocabularySourceTypeEnum.SEGMENT);
            entries.add(vocabulary);
        }

        return entries;
    }

    /**
     * 找出資料庫裡「同一個中文詞、同一個泰文」的那一列。
     * 唯一鍵是這兩欄的組合，所以比對也要用這兩欄。
     */
    private Vocabulary findExisting(List<Vocabulary> existingEntries, Vocabulary candidate) {
        return existingEntries.stream()
                .filter(entry -> Objects.equals(entry.getChineseText(),
                        candidate.getChineseText()))
                .filter(entry -> Objects.equals(entry.getThaiText(), candidate.getThaiText()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 只補空的欄位，已經有值的一律不動 ——
     * 那些是先前寫入的歷史，不該被後來的呼叫改寫。
     * source_type 也不更新，同樣的理由。
     *
     * ★ 這裡改的是從資料庫撈出來的 JPA 實體，而這個方法在 @Transactional 裡面，
     *   所以 Hibernate 會在交易結束時自動把變更寫回去，不需要再呼叫 save。
     *   這叫 dirty checking，是 JPA 的預設行為。
     */
    private void fillMissingLabels(Vocabulary existing, Vocabulary candidate) {
        if (Objects.isNull(existing.getGenderUsage())) {
            existing.setGenderUsage(candidate.getGenderUsage());
        }

        if (Objects.isNull(existing.getPoliteness())) {
            existing.setPoliteness(candidate.getPoliteness());
        }

        if (ObjectUtils.isEmpty(existing.getNote())) {
            existing.setNote(candidate.getNote());
        }
    }

    /**
     * 防止同一次寫入裡出現兩筆一樣的（例如句子裡重複的詞，或去重沒攔乾淨的說法）。
     */
    private boolean containsSameWord(List<Vocabulary> entries, Vocabulary candidate) {
        return entries.stream()
                .anyMatch(entry -> Objects.equals(entry.getChineseText(),
                        candidate.getChineseText())
                        && Objects.equals(entry.getThaiText(), candidate.getThaiText()));
    }
}
