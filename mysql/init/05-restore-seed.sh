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
# 還原來源優先順序：
#   1. SEED_FILE（預設相容別名 mysql/backup/05-latest-backup.sql，明文）
#   2. 兜底：最新的 mysql/backup/handsoap-*.sql.gz（日期檔名模式，需解壓）
#
# 為什麼需要兜底：backup-loop.sh 改用日期檔名保留多份後，相容別名若因故未產生
# （例如磁碟寫入失敗），此腳本不能就此「安靜地跳過還原」—— 那會讓
# `docker compose down -v` 後資料全空，日誌看起來卻像正常初始化。
# 有備份卻沒還原，是最難察覺的災難。
#
# 注意：MySQL 官方 entrypoint 對 initdb 目錄內不可執行的 .sh 會以「source」方式載入，
#      因此這裡絕對不能使用 exit，否則會中斷整個初始化流程。
#      同理不使用 set -e：entrypoint 自帶 errexit，還原失敗時改為警告而非中止建庫。

SEED_FILE="${SEED_FILE:-/seed/05-latest-backup.sql}"

if [ -f "$SEED_FILE" ]; then
    if mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" ${MYSQL_DATABASE} < "$SEED_FILE"; then
        echo "=> 已從 $SEED_FILE 還原本機資料庫內容"
    else
        echo "=> 警告：備份還原失敗，資料庫僅保留 01~04 的初始化內容（請檢查 $SEED_FILE 是否與 schema 相容）"
    fi
else
    # 兜底：取最新的 handsoap-*.sql.gz。ls 在無匹配時回傳錯誤，故擷取其輸出。
    LATEST_GZ="$(ls -t /seed/handsoap-*.sql.gz 2>/dev/null | head -1)"
    if [ -n "$LATEST_GZ" ] && [ -f "$LATEST_GZ" ]; then
        echo "=> 相容別名不存在，改用日期備份：$LATEST_GZ"
        if gzip -dc "$LATEST_GZ" | mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" ${MYSQL_DATABASE}; then
            echo "=> 已從 $LATEST_GZ 還原本機資料庫內容"
        else
            echo "=> 警告：備份還原失敗，資料庫僅保留 01~04 的初始化內容"
        fi
    else
        echo "=> 找不到任何備份（$SEED_FILE 與 handsoap-*.sql.gz），跳過還原（僅套用 01~04 初始化腳本）"
    fi
fi
