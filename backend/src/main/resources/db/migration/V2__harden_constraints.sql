-- V2__harden_constraints.sql（review M7）：把寬鬆的 V1/01 收斂到與 Entity 一致的加固狀態。
--
-- 為什麼要「先回填、再收緊」：
--   現有備份裡很可能已有 price NULL / short_description NULL 的列，
--   直接 ALTER ... NOT NULL 會讓 migrate 當場失敗。更陰險的是
--   05-restore-seed.sh 的還原是 INSERT IGNORE 到新 schema：NULL 違反 NOT NULL
--   只會變 warning 被吞掉，列被靜默跳過、看起來還原成功。
--   因此每個收緊前都先 UPDATE ... WHERE NULL 回填。
--
-- 為什麼本檔全部冪等（IF NOT EXISTS / 先查後改）：
--   三種流程都會跑 V2 —— 舊 volume（表全在）、新 volume（init 已建寬鬆表）、
--   真正空庫（V1 剛建寬鬆表）。V2 必須在三者上都跑得過。
--   MySQL 沒有 ADD CONSTRAINT IF NOT EXISTS，因此 UNIQUE / CHECK 用
--   information_schema 預查；重複跑（repair 後）也不會炸。
--
-- admins.email / oils.name 重複資料警告：
--   V2 會建 UNIQUE。若舊庫已有重複，migrate 會在此處失敗 —— 這是刻意讓它失敗：
--   併發寫入的重複（L11）必須人工確認哪筆是真的，不能自動刪。
--   先 SELECT email, COUNT(*) ... HAVING COUNT(*) > 1 查，人工清完再跑。

-- 0. soap_essential_oils 的補建（防舊庫曾因其他原因缺此表）。
--    IF NOT EXISTS 故重複跑無害（join table 無 Entity 映射，不影響 validate；
--    但缺表會讓後台成分多選寫入失敗）。
CREATE TABLE IF NOT EXISTS soap_essential_oils (
    soap_id BIGINT NOT NULL,
    essential_oil_id BIGINT NOT NULL,
    PRIMARY KEY (soap_id, essential_oil_id),
    FOREIGN KEY (soap_id) REFERENCES soaps(id) ON DELETE CASCADE,
    FOREIGN KEY (essential_oil_id) REFERENCES essential_oils(id) ON DELETE CASCADE
);

-- 1. soaps：回填後收緊（對應 Soap.java:34-41 nullable=false）
UPDATE soaps SET price = 0 WHERE price IS NULL;
UPDATE soaps SET short_description = '' WHERE short_description IS NULL;
ALTER TABLE soaps MODIFY price DECIMAL(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE soaps MODIFY short_description VARCHAR(500) NOT NULL DEFAULT '';

-- 2. soaps 價格非負（應用層 @Min 之外的最後防線；負價多半是髒資料）。
--    MySQL 8.0.16+ 才 enforce CHECK；舊版只解析不執行，加了無害。
--    必須預查：本檔曾在 5b 失敗後重跑（見檔頭），chk_soaps_price_non_negative
--    第一次就已建立 —— 沒有這層預查，重跑會以「重複 constraint」炸在這裡。
SET @has_chk_price := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'soaps' AND constraint_name = 'chk_soaps_price_non_negative'
);
SET @add_chk_price := IF(@has_chk_price = 0,
    'ALTER TABLE soaps ADD CONSTRAINT chk_soaps_price_non_negative CHECK (price >= 0)', 'SELECT 1');
PREPARE stmt_chk_price FROM @add_chk_price;
EXECUTE stmt_chk_price;
DEALLOCATE PREPARE stmt_chk_price;

-- 3. admins：email 收緊 + 唯一 + role 鎖集合（對應 Admin.java:31-35）
--    username 已是 UNIQUE（01 即有），此處不動；只補 email 的。
--    先回填：舊 email NULL 的列給佔位（UNIQUE 允許多 NULL，但 Entity 說 nullable=false，
--    validate 會卡 nullable；且 NULL 遲早要在應用層填真值，佔位讓問題可見而非靜默）。
UPDATE admins SET email = CONCAT('unknown-', id, '@placeholder.local') WHERE email IS NULL;
ALTER TABLE admins MODIFY email VARCHAR(100) NOT NULL;
ALTER TABLE admins MODIFY username VARCHAR(50) NOT NULL;
ALTER TABLE admins MODIFY role VARCHAR(20) NOT NULL DEFAULT 'ADMIN';

SET @has_uk_email := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'admins' AND index_name = 'uk_admins_email'
);
SET @add_uk_email := IF(@has_uk_email = 0,
    'ALTER TABLE admins ADD CONSTRAINT uk_admins_email UNIQUE KEY (email)', 'SELECT 1');
PREPARE stmt_uk_email FROM @add_uk_email;
EXECUTE stmt_uk_email;
DEALLOCATE PREPARE stmt_uk_email;

SET @has_chk_role := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'admins' AND constraint_name = 'chk_admins_role'
);
SET @add_chk_role := IF(@has_chk_role = 0,
    'ALTER TABLE admins ADD CONSTRAINT chk_admins_role CHECK (role IN (''ADMIN''))', 'SELECT 1');
PREPARE stmt_chk_role FROM @add_chk_role;
EXECUTE stmt_chk_role;
DEALLOCATE PREPARE stmt_chk_role;

-- 4. contacts：subject 收緊（Contact.java:33 nullable=false）、phone 加寬到 50
--    phone 是反向漂移：SQL 50 vs Entity length=20。此處以 SQL 為準加寬到 50
--   （國際碼 + 分機 20 字元不夠用是真實場景），Entity 側同步改 length=50。
--    message 維持可空（Entity 沒寫 nullable=false，預設可空；SQL 的 NOT NULL 反而要放寬，
--    否則 validate 在 message 上報錯 —— 以 Entity 為準）。
UPDATE contacts SET subject = '' WHERE subject IS NULL;
ALTER TABLE contacts MODIFY subject VARCHAR(200) NOT NULL DEFAULT '';
ALTER TABLE contacts MODIFY phone VARCHAR(50);
ALTER TABLE contacts MODIFY message TEXT NULL;
-- email 寬度漂移（DDL 預掃實測：DB varchar(100) vs Contact.email length=200）。
-- 100→200 放寬不損資料；不修則 ddl-auto=validate 以「長度不符」擋啟動。
ALTER TABLE contacts MODIFY email VARCHAR(200) NOT NULL;

-- 5. oils / essential_oils：name 補 UNIQUE（Oil.java:24 / EssentialOil.java:24 unique=true，
--    validate 會因缺 unique index 報錯）。重複 name 同 admins.email：讓 migrate 失敗，
--    人工確認後再跑，不自動刪。
--
-- 5a 之前的必修（本檔第一次執行的死因，實測 Error 1089 "Incorrect prefix key"）：
--    舊 volume 的 essential_oils.name 是 varchar(150)（01-schema 寫 200，DB 卻是 150 ——
--    典型的 init 檔與既有 volume 漂移），而下面前綴索引用 (name(191))，
--    191 > 150 直接被 MySQL 拒絕。先 MODIFY 拉回 200：
--      ① 對齊 EssentialOil.name 的 length=200（否則 ddl-auto=validate 接著炸）
--      ② 讓 (name(191)) 合法
--    MODIFY 是冪等的：已是 200 的庫重跑無副作用，且是放寬方向不損資料。
ALTER TABLE essential_oils MODIFY name VARCHAR(200) NOT NULL;

-- 前綴 (191) 保留：utf8mb4 下 200 字元 = 800 bytes < 3072 上限，其實全長也放得下，
-- 但 oils 已以 (191) 建立（sub_part=191），兩表保持一致，且日後若調大欄寬也仍安全。
SET @has_uk_oils := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'oils' AND index_name = 'uk_oils_name'
);
SET @add_uk_oils := IF(@has_uk_oils = 0,
    'ALTER TABLE oils ADD CONSTRAINT uk_oils_name UNIQUE KEY (name(191))', 'SELECT 1');
PREPARE stmt_uk_oils FROM @add_uk_oils;
EXECUTE stmt_uk_oils;
DEALLOCATE PREPARE stmt_uk_oils;

SET @has_uk_eo := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'essential_oils' AND index_name = 'uk_essential_oils_name'
);
SET @add_uk_eo := IF(@has_uk_eo = 0,
    'ALTER TABLE essential_oils ADD CONSTRAINT uk_essential_oils_name UNIQUE KEY (name(191))', 'SELECT 1');
PREPARE stmt_uk_eo FROM @add_uk_eo;
EXECUTE stmt_uk_eo;
DEALLOCATE PREPARE stmt_uk_eo;

-- 6. 布林欄收緊（Entity 全是 nullable=false，SQL 全是可空 + DEFAULT）。
--    MySQL BOOLEAN = TINYINT(1)，MODIFY 保留 DEFAULT 以免既有 INSERT 依賴預設值。
--    回填值取各 Entity 的 @Builder.Default（is_active 多半 true；其餘 false）。
-- 6a. categories.description 型別漂移（DDL 預掃實測：DB varchar(500) vs
--     Category.description 的 columnDefinition="TEXT"）→ 改成 TEXT 對齊。
--     放寬方向（500 字元 → 64KB），既有資料原樣保留。
ALTER TABLE categories MODIFY description TEXT NULL;

UPDATE categories SET is_active = TRUE WHERE is_active IS NULL;
ALTER TABLE categories MODIFY is_active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE soaps SET is_featured = FALSE WHERE is_featured IS NULL;
ALTER TABLE soaps MODIFY is_featured BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE soaps SET is_active = TRUE WHERE is_active IS NULL;
ALTER TABLE soaps MODIFY is_active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE soap_images SET is_primary = FALSE WHERE is_primary IS NULL;
ALTER TABLE soap_images MODIFY is_primary BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE oils SET is_active = TRUE WHERE is_active IS NULL;
ALTER TABLE oils MODIFY is_active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE oil_images SET is_primary = FALSE WHERE is_primary IS NULL;
ALTER TABLE oil_images MODIFY is_primary BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE essential_oils SET is_active = TRUE WHERE is_active IS NULL;
ALTER TABLE essential_oils MODIFY is_active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE essential_oil_images SET is_primary = FALSE WHERE is_primary IS NULL;
ALTER TABLE essential_oil_images MODIFY is_primary BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE contacts SET is_replied = FALSE WHERE is_replied IS NULL;
ALTER TABLE contacts MODIFY is_replied BOOLEAN NOT NULL DEFAULT FALSE;

-- 7. 高頻查詢複合索引（review L9；放 V2 而非 V1，讓舊庫升級也吃到）。
--    索引名寫死以便冪等預查；重複跑不炸。
SET @has_idx_soaps := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'soaps' AND index_name = 'idx_soaps_active_featured'
);
SET @add_idx_soaps := IF(@has_idx_soaps = 0,
    'CREATE INDEX idx_soaps_active_featured ON soaps (is_active, is_featured, created_at)', 'SELECT 1');
PREPARE stmt_idx_soaps FROM @add_idx_soaps;
EXECUTE stmt_idx_soaps;
DEALLOCATE PREPARE stmt_idx_soaps;

SET @has_idx_contacts := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'contacts' AND index_name = 'idx_contacts_replied_created'
);
SET @add_idx_contacts := IF(@has_idx_contacts = 0,
    'CREATE INDEX idx_contacts_replied_created ON contacts (is_replied, created_at)', 'SELECT 1');
PREPARE stmt_idx_contacts FROM @add_idx_contacts;
EXECUTE stmt_idx_contacts;
DEALLOCATE PREPARE stmt_idx_contacts;
