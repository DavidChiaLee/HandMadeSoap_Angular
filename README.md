# 手工皂前後端分離網站

單一 Angular 專案 + Spring Boot 後端 + MySQL + Nginx 反向代理，使用 Docker Compose 一鍵啟動。

## 技術架構

- **前端**：Angular 18+ (Standalone) + Tailwind CSS
- **後端**：Spring Boot 3.x + Spring Security + JWT
- **資料庫**：MySQL 8.0
- **反向代理**：Nginx 1.25
- **部署**：Docker Compose

## 專案結構

```
handSoap/
├── docker-compose.yml          # Docker 編排
├── .env.example                # 環境變數範例
├── .gitignore
├── nginx/
│   └── nginx.conf              # Nginx 反向代理設定
├── mysql/
│   ├── init/                   # 容器「首次初始化」時依檔名順序執行
│   │   ├── 01-schema.sql       # 資料庫結構
│   │   ├── 02-auth-native-password.sh
│   │   ├── 03-seed-oils.sql
│   │   ├── 04-seed-oil-images.sql
│   │   └── 05-restore-seed.sh  # 本機有備份時自動還原內容
│   └── backup/                 # 自動備份輸出（.gitignore 已排除，絕不進版控）
├── backend/                    # Spring Boot 後端
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── pom.xml
│   └── src/main/java/com/handsoap/
│       ├── HandSoapApplication.java
│       ├── config/
│       ├── controller/
│       ├── dto/
│       ├── entity/
│       ├── exception/
│       ├── repository/
│       └── service/
├── frontend/                   # Angular 前端
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── package.json
│   ├── angular.json
│   ├── tailwind.config.js
│   ├── postcss.config.js
│   ├── tsconfig.json
│   └── src/
│       ├── index.html
│       ├── main.ts
│       ├── styles.css
│       ├── assets/
│       ├── app/
│       │   ├── app.config.ts
│       │   ├── app.routes.ts
│       │   ├── app.component.ts
│       │   ├── shared/
│       │   │   ├── guards/
│       │   │   ├── interceptors/
│       │   │   ├── layout/
│       │   │   ├── models/
│       │   │   └── services/
│       │   ├── front/          # 前台頁面
│       │   └── admin/          # 後台頁面
│       └── environments/
└── uploads/                    # 圖片上傳目錄（Docker volume）
```

## 前置需求

- Docker Desktop (建議最新版本)
- Docker Compose
- Node.js 20+（本地開發用）
- Java 17+（本地開發用）

## 快速開始

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

### 改完程式，需要產生dist

```bash
cd frontend
npm install
npm run build
cd ..
docker-compose up --build
```

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

### 4. 初始化後台管理員

首次啟動後，需要手動建立管理員帳號（`admins` 資料表預設為空）。有兩種方式：

**方式 A：透過 API（bootstrap，只適用於第一個管理員）**

```bash
# 等後端完成啟動（docker compose logs -f backend 看到 Started HandSoapApplication）再執行，
# 否則 Nginx 會回 502 Bad Gateway
curl -X POST http://localhost/api/v1/admin/admins \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin","email":"admin@example.com","role":"ADMIN"}'
```

> 此端點僅在 `admins` 表為空時可匿名呼叫（建立第一個管理員）；
> 之後再新增管理員必須使用後台登入取得的 JWT（`Authorization: Bearer <token>`）。

**方式 B：直接連線 MySQL 插入**

```sql
INSERT INTO admins (username, password_hash, email, role)
VALUES ('admin', '$2a$10$...', 'admin@example.com', 'ADMIN');
```

密碼需使用 BCrypt 編碼。

### 5. 訪問網站

- **前台**：http://localhost
- **後台登入**：http://localhost/admin/login
- **MySQL**：localhost:3307

## 前台頁面

| 路徑                  | 說明                                   |
| --------------------- | -------------------------------------- |
| `/`                   | 首頁（Banner、最新商品、分類快速連結） |
| `/soaps`              | 手工皂列表（分類篩選、搜尋、排序）     |
| `/soaps/:id`          | 手工皂明細                             |
| `/oils`               | 油品列表                               |
| `/oils/:id`           | 油品明細                               |
| `/essential-oils`     | 精油列表                               |
| `/essential-oils/:id` | 精油明細                               |
| `/about`              | 關於我們                               |
| `/contact`            | 聯絡我們                               |

## 後台功能

| 路徑                    | 說明                       |
| ----------------------- | -------------------------- |
| `/admin/login`          | 後台登入                   |
| `/admin/dashboard`      | 數據總覽                   |
| `/admin/categories`     | 分類管理（新增/編輯/刪除） |
| `/admin/soaps`          | 手工皂管理（含圖片上傳）   |
| `/admin/oils`           | 油品管理（含圖片上傳）     |
| `/admin/essential-oils` | 精油管理（含圖片上傳）     |
| `/admin/contacts`       | 聯絡表單管理               |

## API 規格

### 前台（公開）

```
GET    /api/v1/categories
GET    /api/v1/soaps
GET    /api/v1/soaps/{id}
GET    /api/v1/soaps/category/{categoryId}
GET    /api/v1/soaps/search?keyword={keyword}
GET    /api/v1/soaps/featured
GET    /api/v1/oils
GET    /api/v1/oils/{id}
GET    /api/v1/essential-oils
GET    /api/v1/essential-oils/{id}
POST   /api/v1/contacts
```

### 後台（需 JWT）

```
POST   /api/v1/auth/login

POST   /api/v1/admin/admins
       （第一個管理員為 bootstrap；之後需 ADMIN JWT）

GET    /api/v1/admin/categories
POST   /api/v1/admin/categories
PUT    /api/v1/admin/categories/{id}
DELETE /api/v1/admin/categories/{id}

GET    /api/v1/admin/soaps?keyword={keyword}
POST   /api/v1/admin/soaps
PUT    /api/v1/admin/soaps/{id}
DELETE /api/v1/admin/soaps/{id}
POST   /api/v1/admin/soaps/{soapId}/images

GET    /api/v1/admin/oils?keyword={keyword}
POST   /api/v1/admin/oils
PUT    /api/v1/admin/oils/{id}
DELETE /api/v1/admin/oils/{id}

GET    /api/v1/admin/essential-oils?keyword={keyword}
POST   /api/v1/admin/essential-oils
PUT    /api/v1/admin/essential-oils/{id}
DELETE /api/v1/admin/essential-oils/{id}

GET    /api/v1/admin/contacts
PUT    /api/v1/admin/contacts/{id}
```

## 資料庫表結構

- `categories` - 產品分類
- `soaps` - 手工皂主表
- `soap_images` - 手工皂圖片
- `oils` - 油品主表
- `oil_images` - 油品圖片
- `essential_oils` - 精油主表
- `essential_oil_images` - 精油圖片
- `admins` - 後台管理員
- `contacts` - 聯絡表單

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

> `docker compose` 將 MySQL 對應到本機 **3307** 埠（不是 3306），所以本機連線要寫 3307。
> 若只想改 schema 行為，可另設 `HIBERNATE_DDL=update`；預設為 `none`，
> 因為資料表由 `mysql/init/01-schema.sql` 建立，`update` 會與其互相漂移。

### 前端

```bash
cd frontend
npm install
npm start
```

## 環境變數說明

| 變數                | 使用情境                     | 說明               | 預設值                                      |
| ------------------- | ---------------------------- | ------------------ | ------------------------------------------- |
| `DB_ROOT_PASS`      | docker compose（MySQL）      | MySQL root 密碼    | 無，必要                                    |
| `DB_USER`           | docker compose               | 資料庫使用者       | 無，必要                                    |
| `DB_PASS`           | docker compose               | 資料庫密碼         | 無，必要                                    |
| `JWT_SECRET`        | compose 與本機直跑           | JWT 簽章金鑰       | **無**；未設定會啟動失敗                    |
| `DATABASE_URL`      | 本機直跑 Spring Boot         | 資料庫連線字串     | `jdbc:mysql://localhost:3306/handsoap?...`  |
| `DATABASE_USERNAME` | 本機直跑 Spring Boot         | 資料庫使用者       | `root`                                      |
| `DATABASE_PASSWORD` | 本機直跑 Spring Boot         | 資料庫密碼         | **無**；未設定會啟動失敗                    |
| `HIBERNATE_DDL`     | 本機直跑 Spring Boot         | Hibernate schema 策略 | `none`                                   |
| `SHOW_SQL`          | 本機直跑 Spring Boot         | 是否印出 SQL       | `false`                                     |
| `FILE_UPLOAD_DIR`   | 容器／本機                   | 圖片上傳目錄       | `/app/uploads`                              |
| `SERVER_PORT`       | 容器／本機                   | 後端埠號           | `8080`                                      |

> 容器內不需要 `DATABASE_PASSWORD`：`docker-compose.yml` 直接注入 `SPRING_DATASOURCE_*`，
> 會覆蓋 `application.properties` 的同名屬性（Spring 的 relaxed binding）。
> 完整可抄的鍵名清單請看 `.env.example`。

## 安全性備忘

- 請勿將 `.env` 加入版本控制
- `application.properties` 只准寫 `${ENV_VAR}` 引用，**不准寫機密預設值**
  （`${VAR:某某某}` 的「某某某」一樣會被 `.githooks/pre-commit` 擋下，並會進版控與 Docker image）
- 需要本機便利設定時，改放 `application-local.properties`（已被 `.gitignore` 排除）
- `JWT_SECRET` 請使用至少 256 bits 的強亂數
- 生產環境建議啟用 HTTPS
- 定期更新 Docker base image

## 機密與備份（推送至 GitHub 前必讀）

### 資料庫備份不進版控

`backup` 服務每 5 分鐘會 `mysqldump` 一份到 `mysql/backup/05-latest-backup.sql`。
這份 dump 是**即時資料**，內含：

- `admins.password_hash`：管理員密碼的 BCrypt 雜湊（可離線爆破）
- `contacts`：客戶姓名、Email、電話（個人隱私）

因此 `mysql/backup/` 已列入 `.gitignore`。**過去此機制曾把 dump 直接寫進
`mysql/init/`（即 `docker-entrypoint-initdb.d`），使備份變成版控裡的種子檔，
等於把密碼雜湊與客戶個資推到公開倉庫**；現已修正。

### 全新環境的行為

| 情境 | 結果 |
| --- | --- |
| 本機有 `mysql/backup/05-latest-backup.sql` | `05-restore-seed.sh` 自動還原最新內容（含管理員帳號） |
| 全新 clone（無備份檔） | 跳過還原，僅套用 `01`~`04` 的 schema 與示範資料，管理員以 bootstrap API 建立 |

手動還原既有備份：

```bash
docker compose exec -T mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" handsoap' < mysql/backup/05-latest-backup.sql
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
