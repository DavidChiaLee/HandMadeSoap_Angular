-- V5__contact_spam_protection.sql（review 728：公開表單防灌水）
--
-- ContactService.saveContact 的內容去重查詢：
--   同 email + 同 subject + 同 message 於去重窗口內是否已存在。
-- 查詢以 email 為前導欄位，若無索引會在 contacts 表成長後全表掃 TEXT 欄位。
-- (email, created_at) 亦覆蓋「依 email 查近期紀錄」的常見後續需求。
CREATE INDEX idx_contacts_email_created_at ON contacts (email, created_at);