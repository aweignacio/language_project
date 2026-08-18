package com.tim.language_project.repository;

import com.tim.language_project.dto.response.TranslationQueryDto;
import com.tim.language_project.dto.response.TranslationSummaryDto;
import com.tim.language_project.entity.TranslationQuery;
import com.tim.language_project.enums.SpeakerGenderEnum;
import com.tim.language_project.enums.TranslationDirectionEnum;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 查詢結果快取的資料存取。
 */
public interface TranslationQueryRepository extends JpaRepository<TranslationQuery, Long> {

    /*
     * ★ gender 可能是 null（泰翻中）。JPQL 的 = 比不到 null，
     *   所以要寫成「(:gender IS NULL AND ... IS NULL) OR ... = :gender」的形式。
     *   直接寫 gender = :gender 的話，泰翻中的快取永遠不會命中，
     *   每次查同一句泰文都會重新付費 —— 而且不會有任何錯誤訊息。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationQueryDto(
                translationQuery.id,
                translationQuery.sourceText,
                translationQuery.direction,
                translationQuery.gender,
                translationQuery.chineseText,
                translationQuery.thaiText,
                translationQuery.romanization,
                translationQuery.isWord
            )

            FROM TranslationQuery translationQuery

            WHERE translationQuery.sourceText = :sourceText
              AND translationQuery.direction = :direction
              AND ((:gender IS NULL AND translationQuery.gender IS NULL)
                   OR translationQuery.gender = :gender)
            """)
    Optional<TranslationQueryDto> findByKey(
            @Param("sourceText") String sourceText,
            @Param("direction") TranslationDirectionEnum direction,
            @Param("gender") SpeakerGenderEnum gender);

    /*
     * ★ 逐詞拆解與各種說法是「點了才跑」的獨立呼叫，前端只帶得回一個 queryId，
     *   所以要能單獨用 id 把那筆查詢撈回來。用投影而不是 findById 拿實體，
     *   是因為這兩支 API 都在交易外面跑，拿實體回來只會多一份用不到的狀態。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationQueryDto(
                translationQuery.id,
                translationQuery.sourceText,
                translationQuery.direction,
                translationQuery.gender,
                translationQuery.chineseText,
                translationQuery.thaiText,
                translationQuery.romanization,
                translationQuery.isWord
            )

            FROM TranslationQuery translationQuery

            WHERE translationQuery.id = :id
            """)
    Optional<TranslationQueryDto> findDtoById(@Param("id") Long id);

    /*
     * ★ 建構子投影裡的音檔欄位固定給 null，由 Service 事後補上。
     *
     *   音檔不在這張表，在 audio_asset。JPQL 的建構子投影跨不了表，
     *   所以只能先留一個洞。這與 TranslationService.withSegmentAudio()
     *   遇到的是同一件事。
     *
     * ★ 條件裡的 lastViewedAt IS NOT NULL 不可省略。
     *   省了的話，2026-08-18 以前的舊資料會全部混進來排在最後面，
     *   而且它們的時間是 null，排序結果依資料庫實作而定 ——
     *   每次打開順序都不一樣。
     *
     * 筆數上限由呼叫端傳 Pageable 決定（PageRequest.of(0, 20)），
     * 不寫死在這裡 —— 上限是產品決策，屬於 Service。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationSummaryDto(
                translationQuery.id,
                translationQuery.chineseText,
                translationQuery.thaiText,
                translationQuery.romanization,
                translationQuery.direction,
                translationQuery.gender,
                CAST(NULL AS string),
                CASE WHEN translationQuery.favoritedAt IS NOT NULL THEN TRUE ELSE FALSE END
            )

            FROM TranslationQuery translationQuery

            WHERE translationQuery.lastViewedAt IS NOT NULL

            ORDER BY translationQuery.lastViewedAt DESC
            """)
    List<TranslationSummaryDto> findRecent(Pageable pageable);

    /*
     * 收藏清單。沒有筆數上限 —— 使用者自己按進去的東西不該被系統丟掉。
     * favorited 恆為 true（條件已經保證了），仍照樣給值，
     * 讓前端不必為兩份清單寫兩種判斷。
     */
    @Query("""
            SELECT new com.tim.language_project.dto.response.TranslationSummaryDto(
                translationQuery.id,
                translationQuery.chineseText,
                translationQuery.thaiText,
                translationQuery.romanization,
                translationQuery.direction,
                translationQuery.gender,
                CAST(NULL AS string),
                TRUE
            )

            FROM TranslationQuery translationQuery

            WHERE translationQuery.favoritedAt IS NOT NULL

            ORDER BY translationQuery.favoritedAt DESC
            """)
    List<TranslationSummaryDto> findFavorites();

    /*
     * 更新「最後查看時間」。只在使用者真的按下查詢時呼叫。
     *
     * 用 update 而不是把整個實體讀出來改再存，是因為這件事發生在每一次查詢，
     * 讀一次寫一次等於每次查詢多一趟資料庫往返，而我們只想改一個欄位。
     *
     * ★ @Transactional 不可以拿掉（2026-08-18 踩過）。
     *
     *   @Modifying 的 update 一定要在交易裡跑，而呼叫端 TranslationService
     *   的 translate() 沒有 @Transactional —— 它把寫入交給
     *   TranslationPersistenceService，自己是在交易外面的。
     *
     *   少了這一行的症狀是：一按查詢就回 500，訊息是
     *   「No active transaction for update or delete query」。
     *
     *   ★ 而且單元測試抓不到 —— Repository 在那裡是假的；
     *     @DataJpaTest 也抓不到，它會自動把每個測試包在交易裡。
     *     守門的是 TranslationQueryListRepositoryTest 那個標了
     *     NOT_SUPPORTED 的測試，它把那份免費的交易關掉。
     */
    @Transactional
    @Modifying
    @Query("""
            UPDATE TranslationQuery translationQuery
               SET translationQuery.lastViewedAt = :viewedAt
             WHERE translationQuery.id = :id
            """)
    int touchLastViewedAt(@Param("id") Long id, @Param("viewedAt") LocalDateTime viewedAt);

    /*
     * 加入收藏。
     *
     * ★ 條件裡的 favoritedAt IS NULL 是重點：已經收藏過的再按一次，
     *   這句 update 會影響 0 列，收藏時間因此不會被覆寫。
     *   改成先讀出來判斷再寫的話，讀與寫中間會有空隙，而且多一趟往返。
     *
     * 回傳受影響的列數。0 代表「本來就已經收藏了」，不是錯誤。
     *
     * ★ 這裡的 @Transactional 是保險。目前的呼叫端 QueryListService.addFavorite
     *   自己標了 @Transactional，所以本來就有交易；但少了這一行的話，
     *   日後有人從沒有交易的地方呼叫就會踩到上面 touchLastViewedAt 的同一個坑。
     */
    @Transactional
    @Modifying
    @Query("""
            UPDATE TranslationQuery translationQuery
               SET translationQuery.favoritedAt = :favoritedAt
             WHERE translationQuery.id = :id
               AND translationQuery.favoritedAt IS NULL
            """)
    int markFavorite(@Param("id") Long id, @Param("favoritedAt") LocalDateTime favoritedAt);

    /** 取消收藏。把 favoritedAt 設回 null，該列就自動退出收藏清單。@Transactional 的理由同上。 */
    @Transactional
    @Modifying
    @Query("""
            UPDATE TranslationQuery translationQuery
               SET translationQuery.favoritedAt = NULL
             WHERE translationQuery.id = :id
            """)
    int clearFavorite(@Param("id") Long id);

    /*
     * 這兩個是 SpeechTextGuard 在用的：判斷一段文字是不是系統自己產生過的，
     * 用來擋掉會花錢的任意合成請求。方法名稱照 Spring Data 的規則寫，
     * 查詢語句由它自動產生，不需要 @Query。
     */
    boolean existsByThaiText(String thaiText);

    boolean existsByChineseText(String chineseText);
}
