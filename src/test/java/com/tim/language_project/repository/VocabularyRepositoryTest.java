package com.tim.language_project.repository;

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  這個檔案是什麼？
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  如果沒有測試程式，你想確認 VocabularyRepository 能不能用，得這樣做：
 *
 *      1. 啟動整個網站
 *      2. 打開瀏覽器
 *      3. 手動輸入「酒」
 *      4. 用眼睛看畫面上有沒有出現泰文 เหล้า
 *      5. 再打開資料庫工具，看資料有沒有存進去
 *
 *  每改一次程式就要重做一遍，很花時間，久了就會偷懶不做。
 *
 *  「測試程式」就是把上面這五個步驟寫成程式碼，讓電腦自動做。
 *  打一行 mvnw test，八秒鐘跑完，而且以後每次改程式都會自動重跑。
 *
 * ── 這個檔案為什麼會被執行？ ────────────────────────────────────────────
 *
 *  兩個條件同時成立，Maven 才會把它當測試跑：
 *    (1) 放在 src/test/java 底下
 *    (2) 類別名稱以 Test 結尾（VocabularyRepositoryTest ✓）
 *
 *  src/test/java 底下的程式不會被打包進最終的 jar，
 *  測試程式碼永遠不會跟著上線。
 *
 * ── 「@」開頭的東西是什麼？ ──────────────────────────────────────────────
 *
 *  叫做「註解（annotation）」，可以想成一張「貼紙」。
 *  貼紙本身不做任何事，它是貼給別人看的 ——
 *  這個檔案裡的貼紙是貼給 JUnit（跑測試的工具）和 Spring 看的。
 */

import com.tim.language_project.dto.response.VocabularyDto;
import com.tim.language_project.entity.Vocabulary;
import com.tim.language_project.enums.GenderUsageEnum;
import com.tim.language_project.enums.PolitenessEnum;
import com.tim.language_project.enums.VocabularySourceTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

// assertThat 是「檢查結果對不對」的工具，來自 AssertJ 這套函式庫。
// 前面加 static，是為了等一下可以直接寫 assertThat(...)，
// 不用每次都寫成 Assertions.assertThat(...)。
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * ── 貼紙一：@DataJpaTest ─────────────────────────────────────────────────
 *
 *  在跟 Spring 說：「我要測資料庫相關的東西，麻煩你先幫我準備好。」
 *
 *  Spring 收到後，會在測試開始前自動做這些事：
 *      讀取 application.yml 的設定
 *   →  連上 SQL Server
 *   →  把 Vocabulary 這些 entity 認一遍
 *   →  生出 VocabularyRepository 的實體
 *
 *  最後一項特別重要：VocabularyRepository 你只寫了 interface，
 *  裡面「一行實作都沒有」。真正會執行的程式碼，是 Spring 在啟動時
 *  讀了你寫的 @Query 之後動態生出來的。
 *  所以沒有 Spring 幫忙，你根本拿不到一個能用的 Repository。
 *
 *  另外，@DataJpaTest 是「切片測試」——
 *  它只啟動 Spring 的一部分（資料庫那一塊）就停了，
 *  你的 @Service、@Controller、@Component 一個都不會載入。
 *  所以這支測試不需要 OpenAI 金鑰、不需要 Web 伺服器，啟動也快得多。
 *
 * ── 貼紙二：@AutoConfigureTestDatabase(replace = NONE) ───────────────────
 *
 *  這張貼紙是在「擋掉一個預設行為」。
 *
 *  @DataJpaTest 有個自作主張的預設：它會偷偷把你的資料庫換成 H2
 *  （一個跑在記憶體裡、關掉就消失的假資料庫）。很多專案喜歡這樣，因為快。
 *
 *  但這個專案不行。SQL Server 有 NVARCHAR 和 VARCHAR 兩種文字欄位，
 *  泰文只有存進 NVARCHAR 才不會壞掉；存進 VARCHAR 會全部變成「?」，
 *  而且不會拋任何錯誤，你完全不會發現（這叫「靜默損毀」）。
 *
 *  H2 沒有這個區別，怎麼存都對 ——
 *  用 H2 測，等於這個專案最危險的問題永遠測不到。
 *
 *  replace = NONE 就是：「不要換，給我連真的 SQL Server。」
 *  代價：跑測試前 Docker 的 sqlserver 容器必須是開著的。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VocabularyRepositoryTest {

    /*
     * @Autowired 是在跟 Spring 說：
     * 「把你剛才準備好的那個 VocabularyRepository 塞給我。」
     *
     * 注意這裡「沒有」寫 new VocabularyRepository()。
     * 你也不能寫 —— 它是 interface，new 不出來。
     *
     * 這行的效果是：測試開始執行時，
     * vocabularyRepository 這個變數裡已經裝著一個可以直接用的東西了。
     */
    @Autowired
    private VocabularyRepository vocabularyRepository;

    /*
     * ═══ 測試一：查得到、而且查出來的泰文沒有壞掉 ═══════════════════════
     *
     * @Test       ← 有貼這張紙的方法才會被執行。沒貼的會被完全忽略。
     * @DisplayName← 給這個測試取一個中文名字，純粹是報表好看用的，
     *               失敗時看得懂是哪個情境掛掉。刪掉不影響執行。
     *
     * 方法名稱 shouldFindByChineseText 就是這個測試的名字，
     * 取得越白話越好，這樣失敗時光看名字就知道壞了什麼。
     */
    @Test
    @DisplayName("依中文詞查詢應回傳對應泰文")
    void shouldFindByChineseText() {
        /*
         * ── 第一段：準備資料（Arrange）──────────────────────────────
         *
         * 存一筆「酒 / เหล้า / lâo」進資料庫。
         * 這相當於手動流程的「輸入資料」那一步。
         *
         * 為什麼用 saveAndFlush 而不是 save？
         *   JPA 預設會把寫入先累積在記憶體裡、延後才真正送出。
         *   flush 是強迫它「現在就把 INSERT 真的送到資料庫」。
         *   如果不 flush，這個測試可能整段都在記憶體裡完成，
         *   那 NVARCHAR 到底標對了沒就驗不到了 —— 而那正是這支測試唯一的目的。
         */
        vocabularyRepository.saveAndFlush(
                buildVocabulary("測試勿刪酒", "เหล้า", "lâo", VocabularySourceTypeEnum.SEGMENT));

        /*
         * ── 第二段：執行要測的動作（Act）────────────────────────────
         *
         * 這一行就是這支測試真正想驗的東西：
         * findByChineseText 這個查詢方法，到底能不能正常運作。
         *
         * ★ 2026-08-14 起它回傳的是 List 不是 Optional。
         *   因為一個中文詞可以有好幾種泰文說法（「我」有 ผม / ฉัน / กู），
         *   那正是「多重說法」這個功能的全部意義，見下面的測試三。
         *   查不到就是空的 List，不會是 null。
         */
        List<VocabularyDto> found = vocabularyRepository.findByChineseText("測試勿刪酒");

        /*
         * ── 第三段：檢查結果（Assert）───────────────────────────────
         *
         * 這一段是整個測試的重點。
         * assertThat 白話翻譯就是「我主張……」：
         *   條件成立 → 什麼事都不會發生，測試通過
         *   條件不成立 → 當場丟出錯誤，這個測試被標記為「失敗」，
         *                並印出「我期望 X，但實際拿到 Y」
         *
         * 一個測試會不會過，完全取決於這幾行。
         * 沒有 assertThat 的測試只是跑一跑，永遠不會失敗，等於沒測。
         */

        // 我主張：剛好查到一筆
        assertThat(found).hasSize(1);

        // 我主張：查到的泰文「一字不差」等於 เหล้า
        //
        // 這一行就是這整支測試存在的理由。
        // 如果 Vocabulary 上的 columnDefinition = "NVARCHAR(100)" 標錯成 VARCHAR，
        // 資料庫存進去會變成「?????」，讀回來就不等於 เหล้า，
        // 這一行會立刻失敗並告訴你差在哪。
        assertThat(found.get(0).thaiText()).isEqualTo("เหล้า");

        // 我主張：拼音也沒壞。lâo 的 â 一樣是非 ASCII 字元，同樣會被 VARCHAR 吃掉。
        assertThat(found.get(0).romanization()).isEqualTo("lâo");
    }

    /*
     * ═══ 測試二：一次查多個詞，用來過濾重複 ═════════════════════════════
     *
     * 這個查詢是給 Task 8「沉澱單字」用的：
     * 一句話拆成好幾個詞之後，要先問資料庫「這些詞裡面，哪些已經有了？」
     * 已經有的就跳過，只寫入新的。
     *
     * 一次問全部，比一個一個問資料庫快非常多。
     */
    @Test
    @DisplayName("批次查詢已存在的中文詞，供沉澱單字時過濾重複")
    void shouldFindExistingChineseTexts() {
        // ── 第一段：準備資料 ──
        // 先塞兩個詞進資料庫，等一下要驗證只有這兩個會被查出來。
        vocabularyRepository.saveAndFlush(
                buildVocabulary("測試勿刪我", "ฉัน", "chǎn", VocabularySourceTypeEnum.SEGMENT));
        vocabularyRepository.saveAndFlush(
                buildVocabulary("測試勿刪水", "น้ำ", "náam", VocabularySourceTypeEnum.SEGMENT));

        /*
         * ── 第二段：執行要測的動作 ──
         *
         * 故意問三個詞，其中「沒有這個詞」是資料庫裡不存在的。
         * 這是刻意設計的：如果只問存在的詞，就驗不出這個查詢會不會
         * 把不存在的東西也一起回傳。
         *
         * 測試要涵蓋「該出現的有出現」和「不該出現的沒出現」兩面。
         */
        List<Vocabulary> existing = vocabularyRepository.findAllByChineseTextIn(
                List.of("測試勿刪我", "測試勿刪水", "測試勿刪不存在的詞"));

        /*
         * ── 第三段：檢查結果 ──
         *
         * containsExactlyInAnyOrder 的意思是：
         *   「我主張這個清單裡『剛好』就是這些東西，順序不管。」
         *
         *   ✓ 通過：["測試勿刪我", "測試勿刪水"] 或 ["測試勿刪水", "測試勿刪我"]
         *   ✗ 失敗：["測試勿刪我"]                  → 少了「水」
         *   ✗ 失敗：["測試勿刪我", "測試勿刪水", "測試勿刪不存在的詞"] → 多了不該有的
         *
         * 用「剛好等於」而不是「有包含」，才能同時驗證上面兩種錯誤。
         */
        assertThat(existing).extracting(Vocabulary::getChineseText)
                .containsExactlyInAnyOrder("測試勿刪我", "測試勿刪水");
    }

    /*
     * ═══ 測試三：同一個中文詞可以有多種泰文說法 ═════════════════════════
     *
     * ★ 這是整個「多重說法」功能的地基。
     *
     *   舊的唯一鍵是「一個中文詞只能一列」，那條規則存在的話，
     *   「我」的第二種說法根本寫不進去 —— 前面所有的工作都會白做。
     *
     *   唯一鍵改成「中文＋泰文」之後，同一個詞就能存好幾種說法，
     *   而同一種說法仍然不會重複（那是測試四在守的）。
     */
    @Test
    @DisplayName("同一個中文詞可以有多個泰文說法")
    void shouldAllowMultipleThaiVariantsForSameChineseText() {
        vocabularyRepository.saveAndFlush(newVocabulary(
                "測試勿刪我", "ผม", "pǒm", GenderUsageEnum.MALE, PolitenessEnum.FORMAL,
                "男生自稱，正式或對不熟的人使用"));
        vocabularyRepository.saveAndFlush(newVocabulary(
                "測試勿刪我", "ฉัน", "chǎn", GenderUsageEnum.FEMALE, PolitenessEnum.FORMAL,
                "女生自稱"));
        vocabularyRepository.saveAndFlush(newVocabulary(
                "測試勿刪我", "กู", "guu", GenderUsageEnum.BOTH, PolitenessEnum.RUDE,
                "很不客氣，只能對很熟的朋友使用"));

        List<VocabularyDto> variants = vocabularyRepository.findByChineseText("測試勿刪我");

        assertThat(variants).hasSize(3);
        assertThat(variants).extracting(VocabularyDto::thaiText)
                .containsExactlyInAnyOrder("ผม", "ฉัน", "กู");
    }

    /*
     * ═══ 測試四：同一組中文與泰文只能有一列 ═════════════════════════════
     *
     * 唯一鍵的另一半。少了它，同一個說法會被重複寫入 ——
     * 畫面上會出現兩個看起來一模一樣的說法，使用者以為那是兩個不同的詞。
     */
    @Test
    @DisplayName("同一組中文與泰文不可重複寫入")
    void shouldRejectDuplicateChineseAndThai() {
        vocabularyRepository.saveAndFlush(newVocabulary(
                "測試勿刪酒", "เหล้า", "lâo", null, null, null));

        assertThatThrownBy(() -> vocabularyRepository.saveAndFlush(newVocabulary(
                "測試勿刪酒", "เหล้า", "lao", null, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /*
     * ═══ 測試五：性別、禮貌、說明三欄可以是 null ═══════════════════════
     *
     * 從句子拆解沉澱下來的詞沒有這三項資訊 ——
     * 翻一整句時我們不會去問模型「這句話裡的每個詞各適合誰用」。
     *
     * 所以那三欄必須允許 null。不允許的話，翻一句話就會整個寫入失敗。
     * 那些空欄位日後單獨查詢該詞時才會被補上（見 TranslationPersistenceService）。
     */
    @Test
    @DisplayName("性別、禮貌、說明三欄可以是 null")
    void shouldAllowNullLabels() {
        vocabularyRepository.saveAndFlush(newVocabulary(
                "測試勿刪水", "น้ำ", "náam", null, null, null));

        List<VocabularyDto> found = vocabularyRepository.findByChineseText("測試勿刪水");

        assertThat(found).hasSize(1);
        assertThat(found.get(0).genderUsage()).isNull();
        assertThat(found.get(0).politeness()).isNull();
        assertThat(found.get(0).note()).isNull();
    }

    /**
     * 建一個帶著標籤的說法。上面三個新測試共用。
     */
    private Vocabulary newVocabulary(String chineseText,
                                     String thaiText,
                                     String romanization,
                                     GenderUsageEnum genderUsage,
                                     PolitenessEnum politeness,
                                     String note) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setChineseText(chineseText);
        vocabulary.setThaiText(thaiText);
        vocabulary.setRomanization(romanization);
        vocabulary.setGenderUsage(genderUsage);
        vocabulary.setPoliteness(politeness);
        vocabulary.setNote(note);
        vocabulary.setSourceType(VocabularySourceTypeEnum.DIRECT);

        return vocabulary;
    }

    /*
     * ═══ 這個方法「不是」測試 ═══════════════════════════════════════════
     *
     * 它沒有貼 @Test，所以不會被自動執行。
     *
     * 它純粹是為了少打字：上面兩個測試都要建 Vocabulary 物件，
     * 與其每次寫五行，不如包成一個方法，用的時候一行搞定。
     *
     * private 表示「只有這個檔案裡面能用」，外面看不到它。
     */
    private Vocabulary buildVocabulary(String chineseText, String thaiText,
                                       String romanization, VocabularySourceTypeEnum sourceType) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.setChineseText(chineseText);
        vocabulary.setThaiText(thaiText);
        vocabulary.setRomanization(romanization);
        vocabulary.setSourceType(sourceType);
        return vocabulary;
    }
}

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  按下 mvnw test 之後，實際發生的順序
 * ══════════════════════════════════════════════════════════════════════════
 *
 *   1. Maven 掃 src/test/java，找到名字以 Test 結尾的檔案
 *   2. 看到 @DataJpaTest → 啟動 Spring、連上 SQL Server、準備好 Repository
 *   3. 跑第一個 @Test 方法 shouldFindByChineseText：
 *        ├─ 開一個資料庫交易
 *        ├─ 執行方法內容（存資料 → 查資料 → 檢查）
 *        └─ 把交易「回滾」，剛才存的資料全部撤銷
 *   4. 跑第二個 @Test 方法 shouldFindExistingChineseTexts，同樣流程
 *   5. 統計並印出：Tests run: 2, Failures: 0
 *
 * ── 第 3 步最後的「回滾」是怎麼回事？ ──────────────────────────────────
 *
 *  實際跑測試時，log 印出來是這樣的：
 *
 *      Began transaction ... test method [shouldFindByChineseText]
 *      Rolled back transaction ... test method [shouldFindByChineseText]
 *
 *  「酒 / เหล้า」確實被寫進了真的 SQL Server，也確實被讀出來比對過，
 *  然後整筆被撤銷，資料庫回到測試前的樣子。
 *
 *  這是 @DataJpaTest 自動做的，你不用寫任何程式。
 *
 *  好處有兩個：
 *    (1) 測試可以跑一千次都不會把資料庫塞滿垃圾
 *    (2) 不會因為「酒」這個詞上次已經存過了，這次撞到 UNIQUE 限制而失敗
 *
 * ── 順帶一提：每個測試都是獨立的 ────────────────────────────────────────
 *
 *  JUnit 預設「每個測試方法都會建立一個全新的類別實例」。
 *  所以測試之間不會互相污染，執行順序也不該被依賴 ——
 *  每個測試都必須能單獨跑起來。
 *
 *  想單獨跑一個測試類別：
 *      .\mvnw.cmd -B test -Dtest=VocabularyRepositoryTest
 */
