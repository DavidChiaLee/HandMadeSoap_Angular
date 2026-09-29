#!/bin/bash
# 讓 DBeaver 等外部工具可用非 SSL 連線，避免「Public key retrieval is not allowed」。
# caching_sha2_password 在非 SSL 連線下需要 client 端開啟 allowPublicKeyRetrieval；
# 改為 mysql_native_password 後，任何 client 都能以帳號密碼直接連線，且不影響後端 JDBC。
#
# 此腳本由 MySQL 官方 docker-entrypoint 於資料庫初始化階段執行（需重建空 volume 時才跑）。
# 帳號密碼取自容器環境變數，與 docker-compose.yml 的 MYSQL_USER/MYSQL_PASSWORD 一致。
set -e

echo "=> 調整 MySQL 帳號為 mysql_native_password (相容外部 GUI 連線)"

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" ${MYSQL_DATABASE} <<-EOSQL
    ALTER USER '${MYSQL_USER}'@'%' IDENTIFIED WITH mysql_native_password BY '${MYSQL_PASSWORD}';
    ALTER USER 'root'@'%' IDENTIFIED WITH mysql_native_password BY '${MYSQL_ROOT_PASSWORD}';
    FLUSH PRIVILEGES;
EOSQL

echo "=> MySQL 帳號認證調整完成"