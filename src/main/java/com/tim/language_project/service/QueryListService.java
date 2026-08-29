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
 * ══════════════════════════════════════════════════════════════════════════
 *  第三件事：收藏清單的拖曳排序（2026-08-29）
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你按住某一列的 ☰ 把它拖到別的位置，放開 ────────────────────
 *
 *        PUT /api/v1/translations/favorites/order
 *        { "queryIds": [88, 137, 42] }
 *              ↓
 *        queryListService.reorderFavorites([88, 137, 42])
 *
 *    ★ 前端送的是「排好的完整清單」，不是「把 88 移到第 1 位」。
 *      前端本來就握有整個陣列，整份送是冪等的 —— 重送一次結果一樣。
 *
 * ── 第 2 步｜先把整批驗完，才准開始寫 ───────────────────────────────────
 *
 *        countFavoritedIn([88, 137, 42]) → 3
 *
 *    數字與送來的 id 數量不符就整批丟 BusinessException。
 *
 *    ★ 為什麼驗證不能邊寫邊做：寫了兩列才發現第三個 id 有問題的話，
 *      前端收到失敗會把畫面退回原順序，但資料庫裡已經有兩列被改掉 ——
 *      下次打開收藏是一個誰都沒看過的順序，而且查不出怎麼來的。
 *
 * ── 第 3 步｜照陣列順序把索引寫進 favorite_order ───────────────────────
 *
 *        88 → 0        137 → 1        42 → 2
 *
 *    ★ 這裡是「一列呼叫一次 update」，看起來像上面禁止的 N+1，但兩者不同：
 *      清單組裝那邊是「每次打開清單都跑」，這邊只在放開手指那一瞬間跑一次，
 *      而且整批在同一個交易裡，筆數就是收藏的數量。
 *
 * ── ★ 新加入的收藏怎麼排到最上面 ───────────────────────────────────────
 *
 *    addFavorite 給它「目前最小序號減一」（都還沒排過時從 -1 開始）。
 *
 *    ★ 用負數是刻意的：這樣新增一筆不必把其他每一列重新編號。
 *
 *    ★ favorite_order 為 null 代表「還沒被手動排過」，查詢時排在最後面，
 *      並沿用原本的「收藏時間新的在前」—— 所以這個功能上線那一刻，
 *      既有的收藏清單順序不會有任何變化。
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
import java.util.Objects;
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

        translationQueryRepository.markFavorite(queryId, LocalDateTime.now(), nextTopOrder());
    }

    /**
     * 依照送來的順序寫入收藏的排序位置，第一筆是 0、第二筆是 1，依此類推。
     *
     * 收到的是「排好的完整 id 陣列」而不是「把 A 移到第 3 位」——
     * 前端本來就握有整個陣列，這樣做是冪等的，重送一次結果一樣。
     *
     * @throws BusinessException 陣列裡有任何一個 id 不在收藏中時
     */
    @Transactional
    public void reorderFavorites(List<Long> orderedQueryIds) {
        // ★ 驗證一定要在開始寫之前全部做完，見下面 requireAllFavorited 的說明。
        requireAllFavorited(orderedQueryIds);

        for (int position = 0; position < orderedQueryIds.size(); position++) {
            translationQueryRepository.updateFavoriteOrder(orderedQueryIds.get(position), position);
        }
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

    /**
     * 新收藏該拿的序號：目前最小值減一，所以它會排在最上面。
     *
     * ★ 全部都還沒排過時 findMinFavoriteOrder 回 null，那時從 -1 開始。
     *   這正是本功能上線那一刻的狀態 —— 少了這個判斷會是 NullPointerException，
     *   症狀是「按愛心就 500」。
     */
    private int nextTopOrder() {
        Integer minOrder = translationQueryRepository.findMinFavoriteOrder();

        if (Objects.isNull(minOrder)) {
            return -1;
        }

        return minOrder - 1;
    }

    /**
     * 確認這批 id 全部都在收藏中，只要有一個不是就整批拒絕。
     *
     * ★ 為什麼要先驗證完才開始寫：逐列寫入時如果寫了兩列才發現不對，
     *   前端收到失敗會把畫面退回原本的順序，但資料庫裡已經有兩列被改掉了 ——
     *   下次打開收藏就是一個誰都沒看過的順序，而且完全不知道怎麼來的。
     *
     * ★ 這裡不可以用 Objects.equals 比對數量。count 回的是 long、size() 是 int，
     *   Objects.equals 會把它們裝箱成 Long 與 Integer，那兩個型別永遠不相等 ——
     *   結果是「每一次排序都被拒絕」。基本型別直接用 != 才是對的。
     */
    private void requireAllFavorited(List<Long> queryIds) {
        if (translationQueryRepository.countFavoritedIn(queryIds) != queryIds.size()) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND);
        }
    }

    /** 確認這筆查詢真的存在，不存在就丟 404，不可以默默當成成功。 */
    private void requireExists(Long queryId) {
        if (!translationQueryRepository.existsById(queryId)) {
            throw new BusinessException(ErrorCodeEnum.RESOURCE_NOT_FOUND);
        }
    }
}
