package com.handsoap.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link KeywordSanitizer} 的單元測試（review L3）。
 *
 * <p>純字串轉換，不需要 DB —— 這在現況下是唯一能<b>真的</b>驗證跳釋結果的方式：
 * 全 repo 沒有 {@code @DataJpaTest}／H2（{@code application.properties} 指向 MySQL，
 * 見 {@code .github/workflows/ci.yml} 的說明），因此「{@code ESCAPE '\'} 在 MySQL 上
 * 是否正確解讀」無法在 CI 內驗證，只能靠這裡釘死「送進 JPQL 的字串長什麼樣」。
 * 上線前仍需對真實 MySQL 手動驗一次 {@code %}／{@code _} 的字面比對。</p>
 */
class KeywordSanitizerTest {

    @Test
    @DisplayName("一般中文關鍵字應原樣通過（不做無謂轉換）")
    void shouldPassThroughPlainKeyword() {
        assertThat(KeywordSanitizer.normalize("玫瑰")).isEqualTo("玫瑰");
        assertThat(KeywordSanitizer.normalize(" 玫瑰皂 ")).isEqualTo("玫瑰皂");
    }

    @ParameterizedTest
    @CsvSource({
            // 輸入,                       預期輸出
            "%,                            \\%",
            "_,                            \\_",
            "'\\',                         '\\\\'",
            "100%,                         100\\%",
            "a_b,                          a\\_b",
            "a%b_c\\d,                      a\\%b\\_c\\\\d",
    })
    @DisplayName("LIKE 萬用字元與跳脫字元本身都應被跳釋（review L3）")
    void shouldEscapeLikeMetacharacters(String input, String expected) {
        assertThat(KeywordSanitizer.normalize(input)).isEqualTo(expected);
    }

    /**
     * 釘死「單趟掃描」的行為，而非「三次 replace」的順序。
     *
     * <p>{@code a%b_c\d} 是最有鑑別力的案例：每個字元類別各出現一次，且原始輸入
     * 同時含 {@code \}。若日後有人改回 {@code replace} 鏈並把順序寫錯（先跳 {@code %}
     * 再跳 {@code \}），{@code \%} 會被再插一個 {@code \} 變成 {@code \\%}，
     * 萬用字元當場復活 —— 這支測試會失敗。</p>
     */
    @Test
    @DisplayName("混合輸入不得被二次跳釋（釘死跳釋順序）")
    void shouldNotDoubleEscape() {
        String result = KeywordSanitizer.normalize("a%b_c\\d");

        assertThat(result).isEqualTo("a\\%b\\_c\\\\d");
        // 明確斷言「沒有 \\%」這種二次跳釋的痕跡
        assertThat(result).doesNotContain("\\\\%").doesNotContain("\\\\_");
    }

    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", "　"})
    @DisplayName("null／空白／全形空格應回 null（代表無可搜尋內容）")
    void shouldReturnNullWhenNoSearchableContent(String input) {
        assertThat(KeywordSanitizer.normalize(input)).isNull();
    }

    @Test
    @DisplayName("全形空格（U+3000）也應被視為空白而剔除")
    void shouldStripFullWidthSpace() {
        assertThat(KeywordSanitizer.normalize("　玫瑰　")).isEqualTo("玫瑰");
    }

    @Test
    @DisplayName("超過長度上限應截斷而非拋例外或回 null")
    void shouldTruncateOverlongKeyword() {
        String overlong = "a".repeat(500);

        String result = KeywordSanitizer.normalize(overlong);

        assertThat(result).hasSize(KeywordSanitizer.MAX_LENGTH);
    }

    @Test
    @DisplayName("截斷發生在跳釋之前，故跳釋結果仍受上限約束")
    void shouldTruncateBeforeEscaping() {
        // 500 個 % 若先跳釋會變 1000 字元；先截到 100 個 % 再跳釋 → 200 字元
        String manyWildcards = "%".repeat(500);

        String result = KeywordSanitizer.normalize(manyWildcards);

        assertThat(result).hasSize(KeywordSanitizer.MAX_LENGTH * 2);
    }

    @Test
    @DisplayName("已跳釋的字串再送進來會被再次跳釋（輸入必須是原始使用者輸入）")
    void shouldDocumentThatInputMustBeRawNotPreEscaped() {
        // 呼叫端若誤把已跳釋的字串再送進來，每個 \ 會被再跳一次 —— 這是預期的
        // 「輸入必須是原始使用者輸入」契約，此測試把該契約寫成文件。
        assertThat(KeywordSanitizer.normalize("a\\%b")).isEqualTo("a\\\\\\%b");
    }
}
