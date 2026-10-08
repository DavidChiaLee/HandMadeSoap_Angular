-- V4__add_refresh_token.sql（M8 Phase 1）：refresh token 儲存與驗證。
--
-- 為什麼用 DB 的 refresh_token_hash：
--   ① 單一實例、admin 數量極少，DB 天然是單一事實來源；
--   ② 與 admin.token_version 在同一張表，查詢與撤銷可在同一條 SQL 內完成。
--
-- 機制：
--   refresh token = 不透明 UUID；寫入 DB 前必 SHA-256 雜湊（避免明文外洩）；
--   簽發時寫入 `refresh_token_hash` 與 `refresh_token_expires_at`。
--   /auth/refresh 端點從 HttpOnly Cookie 讀取 refresh_token，Hash 後找回 admin。
--
-- 撤銷：
--   目前先走「全清」＝ logout 時 token_version +1（相容 M8 Phase 1 前後清語意）。
--   單 session 撤銷（可挑某裝置的 refresh token）＝ Phase 3 再加 jti/blacklist。

ALTER TABLE admins ADD COLUMN refresh_token_hash VARCHAR(128) DEFAULT NULL;
ALTER TABLE admins ADD COLUMN refresh_token_expires_at DATETIME NULL;