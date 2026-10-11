-- verify-indexes.sql（review L4）：驗證列表查詢的複合索引確實被 optimizer 選用。
--
-- ── 這兩支索引是誰建的 ──
-- 由 V2__harden_constraints.sql 第 7 節建立（冪等寫法，舊庫升級也吃到）：
--   idx_soaps_active_featured   ON soaps   (is_active, is_featured, created_at)
--   idx_contacts_replied_created ON contacts (is_replied, created_at)
-- 索引定義與欄位順序已符合 L4 的需求，無需新增遷移。
--
-- ── 為什麼需要手動跑 ──
-- 專案沒有 H2、也沒有 @DataJpaTest（見 .github/workflows/ci.yml 的說明），因此 CI 內
-- 無法執行 EXPLAIN。H2 的 EXPLAIN 語法與 optimizer 都和 MySQL 不同，在 H2 上驗證對
-- MySQL 的索引設計沒有參考價值，反而給人「已驗證」的錯覺。
--
-- 用法（docker 環境）：
--   docker compose exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" handsoap < scripts/verify-indexes.sql
-- 或互動式：
--   docker compose exec mysql mysql -uroot -p handsoap
--   mysql> source scripts/verify-indexes.sql
--
-- 判讀重點看兩件事：
--   1. possible_keys / key：key 必須是目標索引（不是 NULL、也不是主鍵）
--   2. Extra：不得出現 Using filesort（出現即代表排序沒走索引，第三欄白加了）

-- ── 0. 索引是否存在 ──────────────────────────────────────────────
-- 預期：soaps 有 idx_soaps_active_featured；contacts 有 idx_contacts_replied_created。
-- 兩者都應由 V2 建立；若這裡查不到，代表 V2 沒跑成功（先看 flyway_schema_history）。
--
-- 順便確認 category_id 的隱含 FK 索引：soaps 的 FOREIGN KEY (category_id) 在 InnoDB
-- 會自動建立索引（Key_name = 'category_id'，未顯式命名），因此刻意不再另建一支
-- 同目的的索引 —— 那只會讓每次 INSERT/UPDATE 多維護一份 B-tree。
SHOW INDEX FROM soaps WHERE Key_name IN ('PRIMARY', 'category_id', 'idx_soaps_active_featured');
SHOW INDEX FROM contacts WHERE Key_name IN ('PRIMARY', 'idx_contacts_email_created_at', 'idx_contacts_replied_created');

-- ── 1. 首頁精選 ─────────────────────────────────────────────────
-- 對應 SoapRepository.findByIsFeaturedTrueAndIsActiveTrue（SoapService.getFeaturedSoaps）
-- 預期：
--   key   = idx_soaps_active_featured
--   Extra = 不得出現 Using filesort
--
-- 誠實的侷限（避免過度解讀結果）：is_active／is_featured 各只有 true/false 兩值，
-- 選擇性極差。這支索引的價值幾乎全部來自第三欄 created_at 提供的「有序存取」，
-- 而非前兩欄的過濾。因此 optimizer 在資料量小時改選全表掃是正確決定，不是索引失效。
--
-- 若 Extra 出現 Using filesort：先確認 ORDER BY 的欄位與方向是否為 created_at DESC。
-- 前端若改送 sort=price 等其他欄位，本就不該由這支索引服務 —— 屬預期行為。
EXPLAIN
SELECT * FROM soaps
WHERE is_featured = true AND is_active = true
ORDER BY created_at DESC
LIMIT 20;

-- ── 2. 未回覆清單 ───────────────────────────────────────────────
-- 對應 ContactRepository.findByIsReplied(false, pageable)
-- （AdminContactController 兩端點皆為 @PageableDefault(sort="createdAt", direction=DESC, size=20)）
-- 預期：
--   key   = idx_contacts_replied_created
--   Extra = 不得出現 Using filesort
--
-- 這是兩支中效益最明確的一支：equality 過濾 + 排序欄位緊接在後 + 只需一頁。
-- 若這裡沒走到索引，優先懷疑統計資料過期（見第 5 節的 ANALYZE TABLE）。
EXPLAIN
SELECT * FROM contacts
WHERE is_replied = false
ORDER BY created_at DESC
LIMIT 20;

-- ── 3. 對照組：證明索引真的帶來差別 ────────────────────────────
-- 這兩條刻意「繞過」索引（用函式包住欄位 / 完全無 WHERE），預期 type=ALL、key=NULL。
-- 用途：證明第 1、2 節的 key 不是 optimizer 隨便挑的。若連這兩條都走到索引，
-- 代表判讀方式有誤，上面的結論也不可信。
EXPLAIN SELECT * FROM soaps WHERE YEAR(created_at) = 2026 ORDER BY created_at DESC LIMIT 20;
EXPLAIN SELECT * FROM contacts ORDER BY created_at DESC LIMIT 20;

-- ── 4. Flyway 狀態確認 ──────────────────────────────────────────
-- 確認沒有 success=0 的失敗遷移（有的話 Flyway 會拒絕啟動整個應用）。
-- 預期：全部 success = 1。
SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;

-- ── 5. 統計資料過期時 ───────────────────────────────────────────
-- 若第 1 或第 2 節的 key 是 NULL 且表內確有資料，先刷新統計再重跑：
-- ANALYZE TABLE soaps;
-- ANALYZE TABLE contacts;
