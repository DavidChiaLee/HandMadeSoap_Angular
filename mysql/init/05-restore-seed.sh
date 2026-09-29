#!/bin/bash
# 在全新初始化（mysql-data volume 為空）時，把本機最新的自動備份還原進資料庫。
#
# 為什麼需要這支腳本：
#   backup 服務每 5 分鐘會 dump 一份 05-latest-backup.sql。舊設定直接寫進
#   mysql/init/（= docker-entrypoint-initdb.d），讓「即時備份」變成版控裡的種子檔。
#   該 dump 內含 admins.password_hash（BCrypt 雜湊）與 contacts 的客戶姓名、
#   Email、電話，屬於機密與個資，因此備份輸出已改到 mysql/backup/（已被 .gitignore 排除）。
#   為了保留原本「down -v 後自動帶回本機內容」的開發流程，改由這支腳本從
#   /seed（唯讀掛載 mysql/backup/）讀取並套備份。
#
# 行為：
#   - 本機有 mysql/backup/05-latest-backup.sql  -> 還原最新內容（含管理員帳號）
#   - 全新 clone（無該檔）                      -> 跳過，僅套用 01~04 的 schema 與示範資料，
#                                                  管理員改由 README 的 bootstrap API 建立
#
# 注意：MySQL 官方 entrypoint 對 initdb 目錄內不可執行的 .sh 會以「source」方式載入，
#      因此這裡絕對不能使用 exit，否則會中斷整個初始化流程。
#      同理不使用 set -e：entrypoint 自帶 errexit，還原失敗時改為警告而非中止建庫。

SEED_FILE="${SEED_FILE:-/seed/05-latest-backup.sql}"

if [ ! -f "$SEED_FILE" ]; then
    echo "=> 找不到 $SEED_FILE，跳過備份還原（僅套用 01~04 初始化腳本）"
else
    if mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" ${MYSQL_DATABASE} < "$SEED_FILE"; then
        echo "=> 已從 $SEED_FILE 還原本機資料庫內容"
    else
        echo "=> 警告：備份還原失敗，資料庫僅保留 01~04 的初始化內容（請檢查 $SEED_FILE 是否與 schema 相容）"
    fi
fi
