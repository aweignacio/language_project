package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案在測什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  測 QueryListService —— 「最近」與「收藏」兩份清單的組裝，
 *  以及愛心的加入與取消。
 *
 * ── 第 1 步｜你在手機上點了「收藏」分頁 ─────────────────────────────────
 *
 *    前端送出 GET /api/v1/translations/favorites，最後打到 favorites()。
 *
 * ── 第 2 步｜先去資料庫撈出那些列 ───────────────────────────────────────
 *
 *    translationQueryRepository.findFavorites() 回傳的每一列長這樣：
 *
 *        TranslationSummaryDto[
 *            queryId=137, chineseText=幫我叫計程車,
 *            thaiText=ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ,
 *            romanization=chûai rîak tháek-sîi hâi pǒm nòi khráp,
 *            direction=ZH_TO_TH, gender=MALE,
 *            thaiAudioUrl=null,        ← ★ 這裡是空的
 *            favorited=true]
 *
 *    ★ 為什麼 thaiAudioUrl 是空的？
 *      音檔不存在 translation_query，存在另一張表 audio_asset
 *      （同一段泰文全站只合成一次，所以音檔以文字內容為鍵獨立存放）。
 *      JPQL 的建構子投影跨不了表，所以資料庫那一層只能先留一個洞。
 *
 * ── 第 3 步｜把那個洞補起來（本檔最重要的一段）───────────────────────────
 *
 *    把整批的泰文收成一個集合，「一次」去問音檔：
 *
 *        findExistingAudioUrls([ช่วยเรียก..., ไม่ใส่ผักชี...], TH)
 *            → { "ช่วยเรียก...": "/audio/th/a3f9c2.mp3" }
 *
 *    然後逐列從這個 Map 取出網址填回去。
 *
 *    ★ 為什麼不能在迴圈裡一列查一次？
 *      那是 N+1：收藏一百筆就是一百趟資料庫往返。
 *      資料只有十幾筆時完全看不出來 —— 這正是它難查的原因。
 *      所以下面的測試不只驗結果，還 verify「只查了一次」。
 *
 * ── 第 4 步｜你按下某一列的愛心把它取消 ─────────────────────────────────
 *
 *    removeFavorite(137) → clearFavorite 把 favorited_at 設回 null。
 *
 *    ★ 找不到那個 id 時要丟 BusinessException，不可以默默當成成功 ——
 *      前端會把愛心變成已取消的樣子，但資料庫其實什麼都沒發生。
 *
 * ── 第 5 步｜你按住 ☰ 把某一列拖到別的位置（2026-08-29 新增）────────────
 *
 *    reorderFavorites([88, 137, 42]) 照陣列順序寫入 0、1、2。
 *
 *    ★ 驗證要在開始寫之前全部做完。寫了兩列才發現第三個 id 有問題的話，
 *      前端收到失敗會退回原順序，資料庫卻已經有兩列被改掉 ——
 *      下次打開收藏是一個誰都沒看過的順序。測試八的 never() 就在守這件事。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *    一：清單組裝時的 N+1（收藏一百筆就是一百趟往返，資料少時看不出來）
 *    二：最近清單的 20 筆上限跑掉
 *    三：連按兩下愛心跳出沒道理的紅字
 *    四：對不存在的 id 收藏卻默默成功，愛心變實心但收藏清單裡沒有
 *    五：新收藏沒有排到最上面
 *    六：功能上線那一刻所有序號都還是 null，沒處理會是「按愛心就 500」
 *    七：拖曳後順序沒有正確寫進去
 *    八：★ 排序寫到一半失敗，留下半新半舊的順序
 *
 * ── 什麼東西被換成假的 ──────────────────────────────────────────────────
 *
 *    Repository 與 AudioAssetService 都是 @Mock（假的）。
 *    這支測的是「組裝的邏輯」，不是資料庫本身 ——
 *    資料庫的行為由 TranslationQueryListRepositoryTest 負責。
 */

import com.tim.language_project.dto.response.TranslationSummaryDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.repository.TranslationQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryListServiceTest {

    @Mock
    private TranslationQueryRepository translationQueryRepository;

    @Mock
    private AudioAssetService audioAssetService;

    @InjectMocks
    private QueryListService queryListService;

    /*
     * ═══ 測試一：清單的音檔用一次批次查詢補上 ═══════════════════════════
     */
    @Test
    @DisplayName("收藏清單的音檔應以單次批次查詢補上")
    void shouldFillAudioUrlsWithSingleBatchQuery() {
        when(translationQueryRepository.findFavorites()).thenReturn(List.of(
                summary(137L, "幫我叫計程車", "ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ"),
                summary(138L, "不要放香菜", "ไม่ใส่ผักชีครับ")));

        when(audioAssetService.findExistingAudioUrls(any(), eq(SpeechLanguageEnum.TH)))
                .thenReturn(Map.of("ช่วยเรียกแท็กซี่ให้ผมหน่อยครับ", "/audio/th/a3f9c2.mp3"));

        List<TranslationSummaryDto> favorites = queryListService.favorites();

        // 我主張：有音檔的那一列被補上了網址。
        assertThat(favorites.get(0).thaiAudioUrl()).isEqualTo("/audio/th/a3f9c2.mp3");

        // 我主張：沒有音檔的那一列是 null，而不是整支失敗或空字串 ——
        // 前端看到 null 才會把播放鍵畫成灰的，點下去補生。
        assertThat(favorites.get(1).thaiAudioUrl()).isNull();

        // ★ 我主張：整批只問了一次音檔。這一行就是在防 N+1。
        verify(audioAssetService, times(1))
                .findExistingAudioUrls(any(), eq(SpeechLanguageEnum.TH));
    }

    /*
     * ═══ 測試二：最近清單最多 20 筆 ═════════════════════════════════════
     *
     * 上限寫在 Service（產品決策），不寫在 Repository（資料存取）。
     */
    @Test
    @DisplayName("最近清單應以 20 筆為上限查詢")
    void shouldLimitRecentToTwenty() {
        when(translationQueryRepository.findRecent(any())).thenReturn(List.of());

        queryListService.recent();

        verify(translationQueryRepository).findRecent(PageRequest.of(0, 20));
    }

    /*
     * ═══ 測試三：加入收藏 ═══════════════════════════════════════════════
     *
     * ★ markFavorite 回 0 代表「本來就已經收藏了」，那不是錯誤，
     *   不可以丟例外 —— 使用者連按兩下愛心會看到一個沒有道理的紅字。
     */
    @Test
    @DisplayName("重複加入收藏不應視為錯誤")
    void shouldTreatRepeatedFavoriteAsSuccess() {
        when(translationQueryRepository.existsById(137L)).thenReturn(true);
        when(translationQueryRepository.markFavorite(eq(137L), any(), anyInt())).thenReturn(0);

        queryListService.addFavorite(137L);

        verify(translationQueryRepository).markFavorite(eq(137L), any(), anyInt());
    }

    /*
     * ═══ 測試四：對不存在的 id 收藏要丟錯 ═══════════════════════════════
     *
     * ★ 默默當成成功的話，前端的愛心會變成實心，
     *   但下次打開收藏清單裡什麼都沒有 —— 使用者只會覺得功能壞了。
     */
    @Test
    @DisplayName("查詢不存在時加入收藏應丟出 RESOURCE_NOT_FOUND")
    void shouldRejectFavoriteForUnknownQuery() {
        when(translationQueryRepository.existsById(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> queryListService.addFavorite(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCodeEnum.RESOURCE_NOT_FOUND);
    }

    /*
     * ═══ 測試五：新收藏排在最上面 ═══════════════════════════════════════
     *
     * 手動排序之後，序號小的排前面。新按的收藏要出現在最上面，
     * 所以它拿的是「目前最小值減一」——★ 負數是刻意的：
     * 這樣新增一筆不必把其他每一列重新編號。
     */
    @Test
    @DisplayName("新加入的收藏應取得比現有最小序號還小的序號")
    void shouldPlaceNewFavoriteAboveExistingOnes() {
        when(translationQueryRepository.existsById(137L)).thenReturn(true);
        when(translationQueryRepository.findMinFavoriteOrder()).thenReturn(-3);

        queryListService.addFavorite(137L);

        verify(translationQueryRepository).markFavorite(eq(137L), any(), eq(-4));
    }

    /*
     * ═══ 測試六：還沒有任何序號時從 -1 開始 ═════════════════════════════
     *
     * ★ 這是本功能上線那一刻的狀態：所有既有收藏的 favorite_order 都是 null，
     *   findMinFavoriteOrder 因此回 null。沒處理的話會是 NullPointerException，
     *   症狀是「按愛心就 500」。
     */
    @Test
    @DisplayName("尚無任何收藏序號時新收藏應取得 -1")
    void shouldStartOrderAtMinusOneWhenNoFavoriteHasOrder() {
        when(translationQueryRepository.existsById(137L)).thenReturn(true);
        when(translationQueryRepository.findMinFavoriteOrder()).thenReturn(null);

        queryListService.addFavorite(137L);

        verify(translationQueryRepository).markFavorite(eq(137L), any(), eq(-1));
    }

    /*
     * ═══ 測試七：拖曳排序依陣列順序寫入 0、1、2 ═════════════════════════
     *
     * 前端送來的是「排好的完整 id 陣列」，不是「把 A 移到第 3 位」。
     * 所以這裡要做的就是照順序把索引寫進去。
     */
    @Test
    @DisplayName("重新排序應依陣列順序寫入連續序號")
    void shouldWriteSequentialOrderWhenReordering() {
        List<Long> ordered = List.of(88L, 137L, 42L);

        when(translationQueryRepository.countFavoritedIn(ordered)).thenReturn(3L);

        queryListService.reorderFavorites(ordered);

        verify(translationQueryRepository).updateFavoriteOrder(88L, 0);
        verify(translationQueryRepository).updateFavoriteOrder(137L, 1);
        verify(translationQueryRepository).updateFavoriteOrder(42L, 2);
    }

    /*
     * ═══ 測試八：id 對不上時整批不寫 ════════════════════════════════════
     *
     * ★ 這一題防的是「寫到一半才發現不對」。
     *   逐列寫入時如果寫了兩列才丟例外，前端收到失敗會把畫面退回原順序，
     *   但資料庫裡已經有兩列被改掉了 —— 下次打開收藏就是一個
     *   誰都沒看過的順序，而且完全不知道怎麼來的。
     *
     *   所以驗證要在「開始寫之前」全部做完。never() 那一行就是在守這件事。
     */
    @Test
    @DisplayName("重新排序含有非收藏的 id 時應整批拒絕且不寫入任何一列")
    void shouldRejectReorderContainingUnknownQuery() {
        List<Long> ordered = List.of(88L, 137L, 999L);

        // 三個 id 裡只有兩個真的在收藏中。
        when(translationQueryRepository.countFavoritedIn(ordered)).thenReturn(2L);

        assertThatThrownBy(() -> queryListService.reorderFavorites(ordered))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCodeEnum.RESOURCE_NOT_FOUND);

        verify(translationQueryRepository, never()).updateFavoriteOrder(anyLong(), anyInt());
    }

    /** 組一列清單資料。音檔固定給 null —— 那正是待測方法要補上的東西。 */
    private TranslationSummaryDto summary(Long queryId, String chineseText, String thaiText) {
        return new TranslationSummaryDto(
                queryId, chineseText, thaiText, "chûai rîak tháek-sîi",
                TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE, null, true);
    }
}
