# 維運手冊（OPERATIONS）

本文件收錄 handSoap 的部署、本機開發、資料庫、備份還原、安全與 TLS 上線等維運細節。
門面與快速啟動請見專案根目錄 [`README.md`](./README.md)。

> 說明：此檔由原 README 拆分而來，內容完整保留、未刪減。

## 目錄

- [前置需求](#前置需求)
- [快速開始（完整）](#快速開始完整)
- [初始化後台管理員](#初始化後台管理員)
- [本地開發](#本地開發)
- [前端 API／圖片基址](#前端-api圖片基址)
- [環境變數說明](#環境變數說明)
- [安全性備忘](#安全性備忘)
- [尚未啟用 TLS](#尚未啟用-tls)
- [機密與備份（推送至 GitHub 前必讀）](#機密與備份推送至-github-前必讀)

## 前置需求

- Docker Desktop (建議最新版本)
- Docker Compose
- Node.js 20+（本地開發用）
- Java 17+（本地開發用）

## 快速開始（完整）

### 1. 複製環境變數

```bash
cp .env.example .env
```

### 2. 修改 `.env`

請至少修改以下欄位：

- `DB_ROOT_PASS`：MySQL root 密碼
- `DB_USER`：資料庫使用者名稱
- `DB_PASS`：資料庫使用者密碼
- `JWT_SECRET`：至少 256 bits 的隨機金鑰

### 3. 建置前端並啟動全部服務

改完程式，需要產生 `dist`：

```bash
cd frontend
npm install
npm run build
cd ..

# 本機開發：帶 docker-compose.dev.yml，MySQL 才會發布在 127.0.0.1:3307
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build

# 正式主機部署：不要帶 dev 檔，MySQL 不發布任何對外埠
docker compose up --build
```

> 兩個指令的差別只有 `-f docker-compose.dev.yml`。`docker-compose.dev.yml` **刻意不叫**
> `docker-compose.override.yml` —— 後者會被 `docker compose up` 自動套用，部署時會在不知情的
> 狀況下把資料庫埠重新開到 `0.0.0.0:3307`（review H6）。詳見該檔頭部註解。

或使用開發模式分別啟動：

```bash
# 終端機 1：啟動後端（backend 沒有 Maven wrapper，需本機 Maven 3.9+ 與 JDK 17）
# 啟動前必須先提供 DATABASE_PASSWORD 與 JWT_SECRET，否則會啟動失敗（設計如此，詳見「本地開發」）
cd backend
mvn spring-boot:run

# 終端機 2：啟動前端
cd frontend
npm install
npm start
```

## 初始化後台管理員

> ⚠️ **安全性變更**：早期版本是「`admins` 資料表為空時，任何人都能匿名呼叫
> `POST /api/v1/admin/admins` 建立第一個管理員」。這等於把「部署完成、owner 還沒建立
> 帳號」的時間窗完全開放給公網，任何人都能搶先建立 `ADMIN` 帳號接管後台。
> **該端點現已全面要求 ADMIN 權限**，首位管理員改由後端啟動時自動建立。

**方式 A：啟動時自動建立（建議）**

在專案根目錄的 `.env` 設定（值可參考 `.env.example`）：

```bash
BOOTSTRAP_ENABLED=true
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=<請填一組夠強的密碼>
BOOTSTRAP_ADMIN_EMAIL=admin@example.com
```

接著啟動：

```bash
docker compose up -d backend
docker compose logs -f backend   # 看到 "[bootstrap] 已建立首位管理員" 即完成
```

完成後請務必：

1. 登入後台（http://localhost/admin/login）並**立即更換密碼**
2. 把 `.env` 的 `BOOTSTRAP_ENABLED` 改回 `false`

> 後端啟動時會檢查 `admins` 表：**已有任何帳號就會跳過**，絕不會覆蓋既有管理員。
> 因此本機若有 `mysql/backup/` 備份（`05-restore-seed.sh` 會自動還原），bootstrap 會直接跳過，
> 此時不需要設定上面三個變數。

**方式 B：直接連線 MySQL 插入**

```sql
INSERT INTO admins (username, password_hash, email, role)
VALUES ('admin', '$2a$10$...', 'admin@example.com', 'ADMIN');
```

密碼需使用 BCrypt 編碼。

### 訪問網站

- **前台**：http://localhost
- **後台登入**：http://localhost/admin/login
- **MySQL**：正式部署刻意不發布對外埠。本機開發請見「本地開發 › 資料庫連線」

## 本地開發

### 後端

`application.properties` 刻意**不含任何機密預設值**，所以直接啟動前必須先提供連線資訊，
否則 Spring Boot 會明確報 `Could not resolve placeholder 'DATABASE_PASSWORD'` 而失敗。
這是設計行為——避免用寫死的 `root/root` 或公開的 JWT 金鑰悄悄跑起來。

```bash
# Linux / macOS：鍵名與可抄的範例值見專案根目錄 .env.example
export DATABASE_URL='jdbc:mysql://localhost:3307/handsoap?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true'
export DATABASE_USERNAME=handsoap_user
export DATABASE_PASSWORD='與 .env 的 DB_PASS 相同'
export JWT_SECRET='與 .env 的 JWT_SECRET 相同'
cd backend
mvn spring-boot:run        # backend 無 Maven wrapper，需本機 Maven 3.9+ / JDK 17
```

Windows PowerShell 用 `$env:DATABASE_PASSWORD='...'` 設定，或把這些鍵值寫進專案根目錄的
`application-local.properties`（此檔名已被 `.gitignore` 排除；`application.properties`
已用 `spring.config.import=optional:file:./application-local.properties` 自動載入它）。

> ⚠️ 下列指令中的 **3307 只在本機有啟動 `docker-compose.dev.yml` 時才存在**。
> 正式部署（`docker compose up`）刻意不發布 MySQL 對外埠，此時請改用容器內
> `docker compose exec` 查詢，或走 SSH tunnel —— 詳見下方「資料庫連線」。
>
> 若只想改 schema 行為，可另設 `HIBERNATE_DDL=update`；預設為 `validate`，
> 因為 schema 由 Flyway 版本控管（`backend/.../db/migration/V1/V2`），啟動時會做漂移偵測；
> `update` 會讓 Hibernate 自動改表、與遷移腳本互相漂移，僅供本機除錯。


### 資料庫連線

`docker-compose.yml` 刻意**不發布 MySQL 對外埠**（review H6）：雲主機公網 IP 下發布
3307 等於資料庫裸奔，root 密碼強度會是唯一防線。容器之間改走 `handsoap-net` 內部 DNS
（`backend` → `mysql:3306`、`backup` → `mysql`）。

本機開發因此用 `-f docker-compose.dev.yml` 補回直連能力，**只綁 `127.0.0.1`**：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
```

| 情境 | MySQL 可連線位址 |
| --- | --- |
| 本機開發（帶 dev 檔） | `localhost:3307`（僅 loopback） |
| 本機 / 正式部署（不帶 dev 檔） | 無對外埠；僅容器網路內的 `mysql:3306` |

**容器內查詢**（bash 與 PowerShell 通用）：

```bash
# 不帶 -uroot 會讓 MySQL 拿 OS 使用者名去登入，直接 ERROR 1045；
# 密碼走環境變數，不要打進指令（會留在 shell history）
docker compose exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" handsoap'
```

PowerShell 下還原備份要用管線（`<` 在 PowerShell 是保留字）：

```powershell
Get-Content -Raw mysql/backup/05-latest-backup.sql |
  docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" handsoap'
```

> `-T` 不可省略：`docker compose exec` 預設配置 TTY，會截斷 stdin 上的備份內容。

**正式主機要用 GUI 工具（DBeaver / MySQL Workbench）連線** —— 不要用 dev 檔，
改走 SSH tunnel：

```bash
ssh -N -L 3307:127.0.0.1:3307 root@<your-vps-ip>
```

> ⚠️ 由於正式部署時宿主上**沒有**任何 MySQL 埠，上面的 tunnel 目的地抓不到東西。
> 實際可行的兩種做法：(1) 在 VPS 上以 `docker exec -it handsoap-mysql-1 mysql` 直接查詢；
> (2) 若確實需要 GUI，改為在 VPS 加載 `docker-compose.dev.yml` 啟動（此時埠只綁
> `127.0.0.1`，公網仍連不到，tunnel 才有意義）—— 但**代價是正式機多了一個本機埠**，
> 是否接受請自行評估。

`mysql/init/02-auth-native-password.sh` 把帳號改為 `mysql_native_password`，
正是為了讓這類 GUI 工具能走非 SSL 連線，**刪除 ports 後該需求依然存在，不應刪除**。

### 前端

```bash
cd frontend
npm install
npm start
```

## 前端 API／圖片基址

前端有兩份環境設定，由 `frontend/angular.json` 的 `configurations.production.fileReplacements` 在建置期切換：

| 檔案 | 使用時機 | `apiUrl` | `uploadUrl` |
| --- | --- | --- | --- |
| `src/environments/environment.ts` | `npm start`（ng serve）、`npm test` | `http://localhost:8080/api/v1` | `http://localhost:8080/uploads/` |
| `src/environments/environment.prod.ts` | `npm run build`（production） | `/api/v1` | `/uploads/` |

- 正式版走**同源相對路徑**，由 nginx 反代 `/api/` 與 `/uploads/` 到 `backend:8080`，因此不受網域／連接埠影響，也不會有 mixed content。
- 圖片網址只能透過 `FileUrlService` 產生（模板用 `[src]="fileUrl(images)"`），不要在模板硬編 `localhost:8080`；沒有圖片時會顯示 `src/assets/placeholder.svg`。
- 只改 `environment.prod.ts` 不會有任何效果——它必須靠 `fileReplacements` 才會取代 `environment.ts`。
- 正式版 API 是相對路徑，所以 `ng serve --configuration production` 會失效（相對路徑指向 4200）；要驗證正式版請用 `docker compose up --build`。
- 產物檢查（CI 可用）：

```powershell
cd frontend; npm run build; cd ..
pwsh ./scripts/verify-frontend-dist.ps1
```


## 環境變數說明

| 變數 | 使用情境 | 說明 | 預設值 |
| --- | --- | --- | --- |
| `DB_ROOT_PASS` | docker compose（MySQL） | MySQL root 密碼 | 無，必要 |
| `DB_USER` | docker compose | 資料庫使用者 | 無，必要 |
| `DB_PASS` | docker compose | 資料庫密碼 | 無，必要 |
| `JWT_SECRET` | compose 與本機直跑 | JWT 簽章金鑰 | **無**；未設定會啟動失敗 |
| `DATABASE_URL` | 本機直跑 Spring Boot | 資料庫連線字串 | `jdbc:mysql://localhost:3307/handsoap?...`（3307 需帶 `docker-compose.dev.yml`；正式部署無對外埠） |
| `DATABASE_USERNAME` | 本機直跑 Spring Boot | 資料庫使用者 | `root` |
| `DATABASE_PASSWORD` | 本機直跑 Spring Boot | 資料庫密碼 | **無**；未設定會啟動失敗 |
| `HIBERNATE_DDL` | 本機直跑 Spring Boot | Hibernate schema 策略 | `validate` |
| `SHOW_SQL` | 本機直跑 Spring Boot | 是否印出 SQL | `false` |
| `FILE_UPLOAD_DIR` | 容器／本機 | 圖片上傳目錄 | `/app/uploads` |
| `SERVER_PORT` | 容器／本機 | 後端埠號 | `8080` |

> 容器內不需要 `DATABASE_PASSWORD`：`docker-compose.yml` 直接注入 `SPRING_DATASOURCE_*`，
> 會覆蓋 `application.properties` 的同名屬性（Spring 的 relaxed binding）。
> 完整可抄的鍵名清單請看 `.env.example`。
>
> ⚠️ 本機直跑後端必須**同時**具備：MySQL 已用 `-f docker-compose.dev.yml` 啟動，
> 否則 `localhost:3307` 無人監聽，後端會以 `Communications link failure` 啟動失敗。

## 安全性備忘

- 請勿將 `.env` 加入版本控制
- `application.properties` 只准寫 `${ENV_VAR}` 引用，**不准寫機密預設值**
  （`${VAR:某某某}` 的「某某某」一樣會被 `.githooks/pre-commit` 擋下，並會進版控與 Docker image）
- 需要本機便利設定時，改放 `application-local.properties`（已被 `.gitignore` 排除）
- `JWT_SECRET` 請使用至少 256 bits 的強亂數
- **本專案目前僅提供 HTTP，尚未啟用 TLS**（`docker-compose.yml` 刻意不發布 443）。
  正式環境請改用 HTTPS 部署，詳見下方「尚未啟用 TLS」；在 TLS 上線前，
  登入密碼與 Bearer token 皆以明碼通過網路。
- 定期更新 Docker base image

## 尚未啟用 TLS

`nginx/nginx.conf` 目前只有 `listen 80`，`docker-compose.yml` 也**刻意不發布 443**
（review H8 階段 1）。這代表：

- 站台目前**僅提供 HTTP**，登入密碼與 JWT token 以明碼傳輸
- `docker-compose.yml` 的 `443:443` 是註解狀態 —— 發布一個容器內無人監聽的埠，
  症狀會是「TCP 建立後無回應」而非 `connection refused`，容易誤判成防火牆問題

要啟用 TLS，建議流程：

1. 準備正式網域，把 DNS A 記錄指向主機（Let's Encrypt 需要）
2. 在 `.env` 設定 `DOMAIN` 與 `ACME_EMAIL`
3. 加 `certbot` sidecar 與憑證 volume，並發布 443
4. 將 `nginx.conf` 拆成 `listen 80`（ACME 驗證路徑 + `return 301`）與 `listen 443 ssl` 兩個 server block
5. **同步**把 `CORS_ALLOWED_ORIGINS` 改成 `https://<網域>`，否則全站 403

> ⚠️ 上 TLS 前務必讀 `docker-compose.yml` 中 nginx service 的註解，
> 內含三個容易造成死鎖或全站故障的坑（ACME 301 死鎖、`listen ... ssl http2` 過時、
> CORS 白名單必須同步），並記錄在 `docs/review/2026-09-29-full-code-review.md` §0.6。
>
> 建議 **HSTS 先用 `max-age=300` 觀察一週**，確認憑證自動續期正常後再拉長。
> HSTS 一旦被瀏覽器記住，憑證過期且續期失敗時使用者端會卡死，且清除快取救不回來。


## 機密與備份（推送至 GitHub 前必讀）

### 資料庫與圖片備份

`backup` 服務每 5 分鐘產生一組備份（review H7）：

| 產物 | 位置 | 說明 |
| --- | --- | --- |
| 資料庫 | `mysql/backup/handsoap-<YYYYmmdd-HHMM>.sql.gz` | `mysqldump`（gzip 壓縮） |
| 圖片 | `backups/handsoap-uploads-<YYYYmmdd-HHMM>.tar.gz` | `uploads/` **整目錄**封存 |
| 校驗值 | `backups/checksums/<YYYYmmdd-HHMM>.sha256` | 用於驗證備份未損毀 |
| 相容別名 | `mysql/backup/05-latest-backup.sql` | 明文副本，供自動還原使用 |

**圖片為什麼是整目錄封存**：過去只備份 `essential_oil_images` 參照的檔案，實測 82 張圖中只備到 33 張
（**漏了 72%**），災後還原會得到「資料庫有紀錄、但圖片 404」的結果。改備整個 `uploads/` 後，
連「資料庫已刪除但磁碟殘留」的孤兒檔也會一併保留。腳本另會比對 DB 三張圖片表
（`soap_images` / `oil_images` / `essential_oil_images`）與磁碟，發現缺檔時在 log 提出警告
（**只警告、不中斷備份** —— 不會因為一張圖不見就讓整組備份停擺）。

#### 磁碟用量（部署前務必評估）

`uploads/` 目前約 **164MB**，每 5 分鐘一份 = 每日 288 份：

| `BACKUP_INTERVAL` | `BACKUP_KEEP_DAYS` | 約需空間 |
| --- | --- | --- |
| 300（5 分鐘，預設） | 7 | **約 60GB** ⚠️ |
| 1800（30 分鐘） | 7 | 約 10GB |
| 3600（1 小時） | 7 | 約 4.2GB |
| 3600（1 小時） | 30 | 約 18GB |

在 `.env` 設定 `BACKUP_KEEP_DAYS` 即可調整保留天數。**常見 20–40GB 的 VPS 磁碟無法使用預設值**，
請依實際容量調大 `BACKUP_INTERVAL`（`docker-compose.yml` 的 `backup.environment`）。

#### 驗證備份完整性

```powershell
# 1. 比對 sha256（會印出 OK 即代表未損毀）
cd mysql\backup
Get-FileHash -Algorithm SHA256 (Get-ChildItem handsoap-*.sql.gz | Sort-Object LastWriteTime | Select-Object -Last 1).Name |
  ForEach-Object { "$($_.Hash.ToLower())  $($_.Path | Split-Path -Leaf)" }

# 與 backups/checksums/ 內同時間戳的檔案內容相同即正確
```

```bash
# Linux / macOS
cd mysql/backup
sha256sum -c "$(ls -t handsoap-*.sql.gz | head -1 | sed 's|^|../../backups/checksums/|; s|\.sql\.gz$|.sha256|')"
```

### 還原演練（建議每月實際做一次）

**沒有實際還原過的備份，不能算備份。** 完整流程：

```powershell
# 0. 確認目前有備份（否則以下步驟會清空資料且無從還原）
Get-ChildItem mysql\backup\handsoap-*.sql.gz, backups\handsoap-uploads-*.tar.gz

# 1. 備份現有資料庫（演練後若要保留現況可再還原回來）
#    —— 這一步不能跳過，down -v 會刪除整個 volume
docker compose -f docker-compose.yml -f docker-compose.dev.yml exec -T mysql sh -c `
  'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction handsoap | gzip' > before-drill.sql.gz

# 2. 清空 volume，模擬災難（資料會全部消失）
docker compose -f docker-compose.yml -f docker-compose.dev.yml down -v

# 3. 重新啟動 —— 05-restore-seed.sh 會自動還原備份
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

# 4. 確認還原成功
docker compose -f docker-compose.yml -f docker-compose.dev.yml logs mysql | Select-String '=> 已從'
docker compose -f docker-compose.yml -f docker-compose.dev.yml exec -T mysql sh -c `
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" handsoap -e "SELECT COUNT(*) AS soaps FROM soaps; SELECT COUNT(*) AS images FROM soap_images;"'

# 5. 圖片也要能存取
#    DB 有紀錄 + uploads/ 有檔案  →  兩者數量應一致
docker compose -f docker-compose.yml -f docker-compose.dev.yml exec -T mysql sh -c `
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" handsoap -e "SELECT COUNT(*) FROM essential_oil_images;"'
(Get-ChildItem uploads -File).Count
```

> ⚠️ 步驟 2 會**真的刪除資料**。請先確認步驟 1 已備份，且不要在正式環境的尖峰時段執行。
>
> 演練後若要回到演練前的狀態，還原步驟 1 的檔案即可。

### 資料庫備份不進版控

所有備份產物都是**即時資料**，內含：

- `admins.password_hash`：管理員密碼的 BCrypt 雜湊（可離線爆破）
- `contacts`：客戶姓名、Email、電話（個人隱私）

因此 `mysql/backup/`、`backups/`、`backups/checksums/` 全部列入 `.gitignore`。
**過去此機制曾把 dump 直接寫進 `mysql/init/`（即 `docker-entrypoint-initdb.d`），
使備份變成版控裡的種子檔，等於把密碼雜湊與客戶個資推到公開倉庫**；現已修正。

### 全新環境的行為

| 情境 | 結果 |
| --- | --- |
| 有 `05-latest-backup.sql`（相容別名） | `05-restore-seed.sh` 優先還原它 |
| 僅有 `handsoap-*.sql.gz`（相容別名遺失） | 腳本自動改用最新的日期備份（gzip 解壓後還原） |
| 完全沒有備份 | 跳過還原，僅套用 `01`~`04` 的 schema 與示範資料，管理員以 bootstrap 建立 |

手動還原既有備份：

```bash
# 相容別名（明文）
docker compose exec -T mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" handsoap' < mysql/backup/05-latest-backup.sql

# 日期備份（gzip，記得加 -T 關閉 TTY 否則 stdin 會被截斷）
docker compose exec -T mysql sh -c \
  'gzip -dc /dev/stdin | mysql -uroot -p"$MYSQL_ROOT_PASSWORD" handsoap' < mysql/backup/handsoap-20261005-1000.sql.gz
```

### 推送前的檢查

```powershell
# 確認沒有機密檔被納入版控、且無硬編碼金鑰
pwsh ./scripts/verify-no-secrets.ps1
# Windows PowerShell 亦可
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/verify-no-secrets.ps1
```

腳本會先比對「會被提交的檔案清單」（直接採用 `.gitignore` 結果），再掃描 BCrypt 雜湊、
私鑰、AWS/GitHub/Slack token 與硬編碼密碼；`${ENV_VAR}` 佔位與測試檔假金鑰不會誤報。
發現問題時回傳 exit code 1。

建議同時啟用 pre-commit 阻擋（**每個 clone 都要跑一次**，`core.hooksPath` 不會被 clone 複制）：

```bash
git config core.hooksPath .githooks
```

啟用後每次 `git commit` 會自動掃描（約 30 秒）；緊急情況下可用 `git commit --no-verify` 跳過。

### 若機密已經被推送過

單純刪除檔案**無效**，Git 歷史仍留檔。需執行：

```bash
git filter-repo --path mysql/backup/05-latest-backup.sql --invert-paths
git push --force --all
```

並**立即輪替**：MySQL `root` / `DB_USER` 密碼、`JWT_SECRET`、以及所有管理員密碼。

