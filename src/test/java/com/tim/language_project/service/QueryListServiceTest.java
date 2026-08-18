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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
        when(translationQueryRepository.markFavorite(eq(137L), any())).thenReturn(0);

        queryListService.addFavorite(137L);

        verify(translationQueryRepository).markFavorite(eq(137L), any());
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

    /** 組一列清單資料。音檔固定給 null —— 那正是待測方法要補上的東西。 */
    private TranslationSummaryDto summary(Long queryId, String chineseText, String thaiText) {
        return new TranslationSummaryDto(
                queryId, chineseText, thaiText, "chûai rîak tháek-sîi",
                TranslationDirectionEnum.ZH_TO_TH, SpeakerGenderEnum.MALE, null, true);
    }
}
