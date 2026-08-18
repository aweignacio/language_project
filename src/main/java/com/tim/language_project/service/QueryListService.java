package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  「最近搜尋」與「收藏」兩份清單的組裝，以及愛心的加入與取消。
 *
 *  ★ 這個檔案「絕對不會呼叫 OpenAI」。它只讀資料庫、只改時間欄位。
 *    這也是為什麼對應的五支 API 用 GET / PUT / DELETE 而不是 POST ——
 *    這個專案用動詞區分「會花錢」與「不會花錢」。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你點開「收藏」分頁到清單出現在畫面上
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你在手機上點了「收藏」分頁 ─────────────────────────────────
 *
 *        GET /api/v1/translations/favorites
 *              ↓
 *        TranslationController.favorites()
 *              ↓
 *        queryListService.favorites()
 *
 * ── 第 2 步｜先去資料庫撈出被收藏的那些列 ───────────────────────────────
 *
 *        translationQueryRepository.findFavorites()
 *
 *    拿回來的每一列長這樣：
 *
 *        TranslationSummaryDto[
 *            queryId=137, chineseText=幫我叫計程車,
 *            thaiText=ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ,
 *            romanization=chûai rîak tháek-sîi hâi pǒm nòi khráp,
 *            direction=ZH_TO_TH, gender=MALE,
 *            thaiAudioUrl=null,        ← ★ 這裡是空的，第 3 步才補
 *            favorited=true]
 *
 *    ★ 為什麼音檔是空的？
 *      音檔不在 translation_query 這張表，在另一張 audio_asset。
 *      同一段泰文全站只合成一次，所以音檔以「文字內容」為鍵獨立存放 ——
 *      這是這個專案「用越久越省錢」的核心。
 *      代價是查詢時跨不了表，只能先留一個洞。
 *
 * ── 第 3 步｜把音檔的洞補起來（★ 本檔最容易寫錯的地方）──────────────────
 *
 *    先把整批的泰文收成一個集合：
 *
 *        ["ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ", "ไม่ใส่ผักชีครับ"]
 *
 *    「一次」去問音檔：
 *
 *        audioAssetService.findExistingAudioUrls(那個集合, TH)
 *            → { "ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ": "/audio/th/a3f9c2.mp3" }
 *
 *    再逐列把網址填回去。查不到的那一句就維持 null。
 *
 *    ★ 絕對不可以改成「在迴圈裡一列查一次」。
 *      那是 N+1：收藏一百筆就是一百趟資料庫往返。
 *      資料只有十幾筆的時候完全看不出來，累積之後每次打開收藏都慢一拍，
 *      而且不會有任何錯誤訊息 —— 這正是它難查的原因。
 *
 *    ★ 用的是 findExistingAudioUrls（只查不生），不是 resolveAudioUrl。
 *      打開一次收藏清單就把一百句沒音檔的全部合成一遍，是會真的付錢的。
 *
 * ── 第 4 步｜回傳，Controller 轉成 JSON ─────────────────────────────────
 *
 *        [ { "queryId":137, ..., "thaiAudioUrl":"/audio/th/a3f9c2.mp3",
 *            "favorited":true }, ... ]
 *
 *    thaiAudioUrl 為 null 的那一列，前端會把播放鍵畫成灰的，點了才補生。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  另一件事：愛心
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  加入收藏 = 把 favorited_at 設成現在時間；取消 = 設回 null。
 *  ★ favorited_at 為 null 就代表「沒收藏」，所以不需要另一個 boolean 欄位。
 *
 *  ★ 已經收藏過的再按一次，markFavorite 會影響 0 列（SQL 條件擋下來的），
 *    那不是錯誤 —— 收藏時間因此不會被覆寫，清單排序才不會莫名其妙跳動。
 *
 *  ★ 但「查詢根本不存在」要丟 BusinessException。默默當成成功的話，
 *    前端的愛心會變成實心，下次打開收藏卻什麼都沒有。
 *
 *  測試檔：src/test/java/com/tim/language_project/service/QueryListServiceTest.java
 */

import com.tim.language_project.dto.response.TranslationSummaryDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.repository.TranslationQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 最近搜尋與收藏兩份清單的組裝，以及收藏的加入與取消。
 * 這一層只讀資料庫與改時間欄位，不會呼叫任何外部服務。
 */
@Service
@RequiredArgsConstructor
public class QueryListService {

    /**
     * 最近搜尋顯示幾筆。
     *
     * 這是產品決策（「短期回溯」而不是「完整歷史」），所以放在 Service，
     * 不放在 Repository —— 那一層只管怎麼取資料，不管取多少才合理。
     */
    private static final int RECENT_LIMIT = 20;

    private final TranslationQueryRepository translationQueryRepository;

    private final AudioAssetService audioAssetService;

    /** 最近搜尋，去重後最多 20 筆，最後查看的時間新的在前。 */
    public List<TranslationSummaryDto> recent() {
        return withAudioUrls(
                translationQueryRepository.findRecent(PageRequest.of(0, RECENT_LIMIT)));
    }

    /** 收藏清單，加入收藏的時間新的在前，沒有筆數上限。 */
    public List<TranslationSummaryDto> favorites() {
        return withAudioUrls(translationQueryRepository.findFavorites());
    }

    /**
     * 加入收藏。已經收藏過的再呼叫一次不會覆寫收藏時間，也不算錯誤。
     *
     * @throws BusinessException 查詢不存在時
     */
    @Transactional
    public void addFavorite(Long queryId) {
        requireExists(queryId);

        translationQueryRepository.markFavorite(queryId, LocalDateTime.now());
    }

    /**
     * 取消收藏。沒有收藏過的再呼叫一次不算錯誤。
     *
     * @throws BusinessException 查詢不存在時
     */
    @Transactional
    public void removeFavorite(Long queryId) {
        requireExists(queryId);

        translationQueryRepository.clearFavorite(queryId);
    }

    /**
     * 把整批清單缺少的泰文音檔網址一次補上。
     *
     * ★ 一定要批次。在迴圈裡一列查一次是 N+1，收藏一百筆就是一百趟往返，
     *   而且資料少的時候完全看不出來。
     */
    private List<TranslationSummaryDto> withAudioUrls(List<TranslationSummaryDto> summaries) {
        Set<String> thaiTexts = summaries.stream()
                .map(TranslationSummaryDto::thaiText)
                .collect(Collectors.toSet());

        Map<String, String> audioUrls =
                audioAssetService.findExistingAudioUrls(thaiTexts, SpeechLanguageEnum.TH);

        return summaries.stream()
                .map(summary -> new TranslationSummaryDto(
                        summary.queryId(),
                        summary.chineseText(),
                        summary.thaiText(),
                        summary.romanization(),
                        summary.direction(),
                        summary.gender(),
                        // 查不到就是 null，前端據此把播放鍵畫成灰的。
                        audioUrls.get(summary.thaiText()),
                        summary.favorited()))
                .toList();
    }

    /** 確認這筆查詢真的存在，不存在就丟 404，不可以默默當成成功。 */
    private void requireExists(Long queryId) {
        if (!translationQueryRepository.existsById(queryId)) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND);
        }
    }
}
