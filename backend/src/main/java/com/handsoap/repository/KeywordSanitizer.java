package com.handsoap.repository;

/**
 * 商品搜尋 keyword 的正規化（review L3）。
 *
 * <p>三個 repository 的 {@code LIKE LOWER(CONCAT('%', :keyword, '%'))} 都靠這個類別
 * 產出 {@code :keyword}。把轉義集中在這裡而非各 repository 各寫一份，是因為
 * 「跳脫順序寫錯」不會有任何編譯或執行期警訊 —— 只會靜靜地讓萬用字元復活，
 * 而這種 bug 要等有人真的打出 {@code %} 才發現。</p>
 *
 * <h2>為什麼必須跳脫 {@code %}、{@code _}、{@code \}}</h2>
 *
 * <p>三者都是 LIKE 的模式字元。未跳釋時使用者輸入 {@code %} 等於「任意長度任意字元」、
 * {@code _} 等於「任意單一字元」—— 原本要找「100%」會變成匹配幾乎所有商品；
 * 輸入 {@code \} 則會吃掉後面我們插入的跳脫字元，把轉義整個作廢。
 * 配合 JPQL 的 {@code ESCAPE '\'} 子句才有意義（見各 repository 的 {@code @Query}）。</p>
 *
 * <h2>為什麼用單趟掃描而不是三次 {@code replace}</h2>
 *
 * <p>{@code replace("\\","\\\\")} 再 {@code replace("%","\\%")} 的寫法有個隱藏的順序陷阱：
 * 若把跳 {@code \%} 排在跳 {@code \} 之前，第一步產生的 {@code \%} 會被第二步再插一個
 * {@code \} 變成 {@code \\%}，萬用字元當場復活。單趟掃描只看「原始字元是什麼」，
 * 結構上不可能發生這種二次跳釋，也不需要靠註解提醒後人別調換順序。</p>
 */
public final class KeywordSanitizer {

    /**
     * keyword 長度上限。
     *
     * <p>截斷而非拒絕：搜尋框貼入長篇內文是常見誤操作（例如把整段商品描述貼進來），
     * 回 400 會讓人以為系統壞了；截斷後搜尋前 100 字元仍是合理行為。
     * 真正的理由是成本 —— {@code %kw%} 無法用索引，每個字元都會讓全表掃多比一次。</p>
     */
    public static final int MAX_LENGTH = 100;

    /** 跳脫字元；必須與各 repository {@code @Query} 的 {@code ESCAPE '\'} 一致。 */
    public static final char ESCAPE_CHAR = '\\';

    private KeywordSanitizer() {
    }

    /**
     * 正規化 keyword：去頭尾空白 → 截斷 → 跳釋 LIKE 模式字元。
     *
     * @param raw 使用者輸入；可為 null
     * @return 可直接傳給 {@code @Query} 的 keyword；{@code null} 代表「沒有可搜尋的內容」
     *         （null 或去除空白後為空）。呼叫端收到 null 時應回空結果，
     *         <b>不可</b>把 null 傳進 {@code CONCAT} —— MySQL 會把 {@code CONCAT('%', null, '%')}
     *         算成 {@code "%%"}，等於「匹配所有列」。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }

        // strip() 而非 trim()：額外處理 Unicode 空白（如全形空格 U+3000）。
        // 中文輸入法容易帶出全形空格，trim() 抓不到，會留下一個「看起來空白」的 keyword。
        String value = raw.strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > MAX_LENGTH) {
            value = value.substring(0, MAX_LENGTH);
        }

        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // 只看原始字元是否為特殊字元，不看輸出結果 —— 故不會二次跳釋。
            if (c == ESCAPE_CHAR || c == '%' || c == '_') {
                escaped.append(ESCAPE_CHAR);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
