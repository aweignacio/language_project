package com.tim.language_project.controller;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案負責什麼
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  逐詞對照裡的播放鍵，點下去打的就是這支 API。
 *
 *  為什麼逐詞的音檔不在查詢時就先做好：一句話拆成 4、5 個詞，
 *  每個詞各合成一次要多打 4、5 次 OpenAI，每次 1 到 2 秒。
 *  這樣每次查句子都要多等好幾秒，而那些詞你未必想聽。
 *  改成「想聽哪個就點哪個」，第一次點等一兩秒，之後永久免費。
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  流程：從你點下播放鍵到聽見聲音
 * ══════════════════════════════════════════════════════════════════════════
 *
 * ── 第 1 步｜你查了「我想喝酒」，逐詞區出現四個詞 ───────────────────────
 *
 *        我    ฉัน      chǎn      🔊（灰色，還沒有音檔）
 *        想    อยาก     yàak      🔊（灰色）
 *        喝    ดื่ม      dùuem     🔊（灰色）
 *        酒    เหล้า     lâo       🔊（亮的，你以前查過「酒」，現成的）
 *
 * ── 第 2 步｜你點了「喝」旁邊那個灰色的鍵 ───────────────────────────────
 *
 *        POST /api/v1/audio
 *        { "speechText": "ดื่ม", "language": "TH" }
 *
 * ── 第 3 步｜先過守門檢查 ───────────────────────────────────────────────
 *
 *        speechTextGuard.isKnown("ดื่ม", TH)
 *
 *        false → 丟 SPEECH_TEXT_UNKNOWN，回 400，★不花錢★
 *                （這道關卡防的是有人拿這支 API 燒我們的餘額）
 *        true  → 往下
 *
 * ── 第 4 步｜交給 AudioAssetService ─────────────────────────────────────
 *
 *        audioAssetService.resolveAudioUrl("ดื่ม", TH)
 *
 *        它會先查資料庫，沒有才真的合成。合成完會寫進 audio_asset，
 *        所以這個詞之後在「任何句子裡」出現都直接是亮的。
 *
 * ── 第 5 步｜回應 ───────────────────────────────────────────────────────
 *
 *        有拿到 → 200 { "audioUrl": "/audio/th/d4e5f6.mp3" }
 *        沒拿到 → 404 AUDIO_FILE_NOT_FOUND
 *
 *        ★ 為什麼失敗要回 404 而不是硬給一個網址？
 *          給了網址前端會顯示成「可以播」，使用者點下去卻沒聲音，
 *          會以為是自己的喇叭壞了。誠實回報找不到，前端才能維持灰色。
 *
 * ── 第 6 步｜前端把該行的播放鍵換成亮的並播放 ───────────────────────────
 *
 *  測試檔：src/test/java/com/tim/language_project/controller/AudioControllerTest.java
 */

import com.tim.language_project.dto.request.AudioRequestDto;
import com.tim.language_project.dto.response.AudioResponseDto;
import com.tim.language_project.enums.ErrorCodeEnum;
import com.tim.language_project.exception.BusinessException;
import com.tim.language_project.service.AudioAssetService;
import com.tim.language_project.service.SpeechTextGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 逐詞音檔的合成端點。
 * 使用 POST 是因為這個呼叫會產生檔案與資料，而且可能花錢。
 */
@RestController
@RequestMapping("/api/v1/audio")
@RequiredArgsConstructor
public class AudioController {

    private final SpeechTextGuard speechTextGuard;

    private final AudioAssetService audioAssetService;

    @PostMapping
    public ResponseEntity<AudioResponseDto> synthesize(@RequestBody AudioRequestDto request) {
        // ★ 這一行是防止帳戶被燒的關卡，不要為了「讓 API 快一點」把它拿掉。
        if (!speechTextGuard.isKnown(request.speechText(), request.language())) {
            throw new BusinessException(ErrorCodeEnum.SPEECH_TEXT_UNKNOWN);
        }

        Optional<String> audioUrl =
                audioAssetService.resolveAudioUrl(request.speechText(), request.language());

        return audioUrl
                .map(url -> ResponseEntity.ok(new AudioResponseDto(url)))
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.AUDIO_FILE_NOT_FOUND));
    }
}
