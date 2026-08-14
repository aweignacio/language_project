package com.tim.language_project.service;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個測試在防什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  AudioAssetService 是所有語音合成的唯一入口。它的工作只有一句話：
 *
 *      這段文字以前合成過嗎？合成過就把舊檔案給你，沒有才去花錢。
 *
 *  ★ 這是整個專案「用越久越省錢」的核心。
 *    如果它壞了，程式「不會出錯」—— 畫面照常、聲音照常，
 *    只是每一次都在重新付語音費用，而且沒有任何跡象。
 *    所以這個測試要盯得很緊。
 *
 * ── 哪些東西被換成假的 ──────────────────────────────────────────────────
 *
 *  AudioAssetRepository  換成假的。真的要連資料庫，太慢，而且這裡要測的是
 *                        「有沒有去查」「查到之後做什麼」，不是資料庫本身。
 *  SpeechClient          換成假的。★ 這個最重要 —— 它是真正會花錢的那一個。
 *                        換成假的之後，我們就能用 verify(...) 檢查
 *                        「它到底有沒有被呼叫」，也就是「這次有沒有花錢」。
 *
 * ── 每個測試各自在防什麼 ────────────────────────────────────────────────
 *
 *  測試一  資料庫已經有了 → ★絕對不可以呼叫 SpeechClient★（省錢的命脈）
 *  測試二  資料庫沒有     → 要合成、要寫進資料庫、要回傳網址
 *  測試三  合成失敗       → 回傳空的 Optional，不可以寫進資料庫
 *                          （寫進去的話，那筆假紀錄會永遠擋住之後的重試）
 */

import com.tim.language_project.client.SpeechClient;
import com.tim.language_project.dto.response.AudioAssetDto;
import com.tim.language_project.entity.AudioAsset;
import com.tim.language_project.enums.SpeechLanguageEnum;
import com.tim.language_project.repository.AudioAssetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioAssetServiceTest {

    @Mock
    private AudioAssetRepository audioAssetRepository;

    @Mock
    private SpeechClient speechClient;

    @InjectMocks
    private AudioAssetService audioAssetService;

    /*
     * ★ 這個測試是整個省錢機制的命脈。
     *   最後那一行 verify(..., never()) 才是重點 —— 它在確認「這次沒有花錢」。
     */
    @Test
    @DisplayName("音檔已存在時不得再次合成")
    void shouldNotSynthesizeWhenAudioAlreadyExists() {
        when(audioAssetRepository.findBySpeechTextAndLanguage("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.of(new AudioAssetDto(
                        1L, "เหล้า", SpeechLanguageEnum.TH, "th/a1b2c3.mp3")));

        Optional<String> audioUrl =
                audioAssetService.resolveAudioUrl("เหล้า", SpeechLanguageEnum.TH);

        assertThat(audioUrl).contains("/audio/th/a1b2c3.mp3");

        // ★ 一毛錢都不能花
        verify(speechClient, never()).synthesize(anyString(), any());
        verify(audioAssetRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("音檔不存在時應合成並寫入資料庫")
    void shouldSynthesizeAndPersistWhenAudioMissing() {
        when(audioAssetRepository.findBySpeechTextAndLanguage("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.empty());
        when(speechClient.synthesize("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.of("th/d4e5f6.mp3"));

        Optional<String> audioUrl =
                audioAssetService.resolveAudioUrl("เหล้า", SpeechLanguageEnum.TH);

        assertThat(audioUrl).contains("/audio/th/d4e5f6.mp3");
        verify(audioAssetRepository).saveAndFlush(any(AudioAsset.class));
    }

    /*
     * 合成失敗時如果照樣寫一筆進資料庫，那筆紀錄會永遠命中，
     * 使用者之後再怎麼點都不會重試 —— 這個詞就永遠沒有聲音了。
     */
    @Test
    @DisplayName("合成失敗時不得寫入資料庫")
    void shouldNotPersistWhenSynthesisFails() {
        when(audioAssetRepository.findBySpeechTextAndLanguage("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.empty());
        when(speechClient.synthesize("เหล้า", SpeechLanguageEnum.TH))
                .thenReturn(Optional.empty());

        Optional<String> audioUrl =
                audioAssetService.resolveAudioUrl("เหล้า", SpeechLanguageEnum.TH);

        assertThat(audioUrl).isEmpty();
        verify(audioAssetRepository, never()).saveAndFlush(any());
    }
}
