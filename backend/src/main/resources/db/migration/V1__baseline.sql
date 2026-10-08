-- V1__baseline.sql（review M7）：由 mysql/init/01-schema.sql 的 DDL 部分產生，DDL-only。
--
-- 為什麼是「寬鬆版」DDL 而非加固版：
--   正式機舊 volume 的表已存在，Flyway 以 baseline-on-migrate 跳過 V1（見
--   application.properties），只跑 V2；全新 volume 由 mysql entrypoint 的
--   01-schema.sql 先建表（同樣寬鬆），同樣跳過 V1 跑 V2。
--   V1 只在「真正的空庫」（本機直跑、createDatabaseIfNotExist 建的新庫）才執行。
--   因此三種流程最終都由 V2 收斂到加固狀態，V1 與 01 保持寬鬆一致即可。
--
-- 三條鐵律（不要「順手」違反）：
--   1. 本檔禁 INSERT：種子留在 mysql/init/03-seed-oils.sql、04-seed-oil-images.sql。
--      01 尾巴的 categories 種子也一樣不搬進來 —— 新 volume 會由 init 插一次、
--      Flyway 再插一次，UNIQUE(name) 直接炸。
--   2. 改表只加 V(n+1)，絕不改已執行的遷移檔（checksum 對不上會全環境拒絕啟動）。
--   3. 改了 01-schema.sql 就要檢查 V1 是否仍一致（新 volume 的 init 結果必須能被
--      baseline 跳過 + V2 收斂；兩邊分叉會讓新舊庫長出不同 schema）。

SET NAMES utf8mb4;

-- 1. Categories Table
CREATE TABLE IF NOT EXISTS categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    sort_order INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. Soaps Table
CREATE TABLE IF NOT EXISTS soaps (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    short_description VARCHAR(500),
    full_description TEXT,
    price DECIMAL(10, 2),
    weight VARCHAR(50),
    dimensions VARCHAR(100),
    usage_instructions TEXT,
    storage_instructions TEXT,
    ingredients TEXT,
    features JSON,
    is_featured BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

-- 3. Soap Images Table
CREATE TABLE IF NOT EXISTS soap_images (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    soap_id BIGINT NOT NULL,
    image_path VARCHAR(500) NOT NULL,
    sort_order INT DEFAULT 0,
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (soap_id) REFERENCES soaps(id) ON DELETE CASCADE
);

-- 4. Oils Table
CREATE TABLE IF NOT EXISTS oils (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    origin VARCHAR(200),
    benefits TEXT,
    suitable_for TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 5. Oil Images Table
CREATE TABLE IF NOT EXISTS oil_images (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    oil_id BIGINT NOT NULL,
    image_path VARCHAR(500) NOT NULL,
    sort_order INT DEFAULT 0,
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (oil_id) REFERENCES oils(id) ON DELETE CASCADE
);

-- 6. Essential Oils Table
CREATE TABLE IF NOT EXISTS essential_oils (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    scent_notes VARCHAR(200),
    benefits TEXT,
    precautions TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 7. Essential Oil Images Table
CREATE TABLE IF NOT EXISTS essential_oil_images (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    essential_oil_id BIGINT NOT NULL,
    image_path VARCHAR(500) NOT NULL,
    sort_order INT DEFAULT 0,
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (essential_oil_id) REFERENCES essential_oils(id) ON DELETE CASCADE
);

-- 8. Soap <-> Oils ManyToMany（後台手工皂成分多選油品）
CREATE TABLE IF NOT EXISTS soap_oils (
    soap_id BIGINT NOT NULL,
    oil_id BIGINT NOT NULL,
    PRIMARY KEY (soap_id, oil_id),
    FOREIGN KEY (soap_id) REFERENCES soaps(id) ON DELETE CASCADE,
    FOREIGN KEY (oil_id) REFERENCES oils(id) ON DELETE CASCADE
);

-- 9. Soap <-> Essential Oils ManyToMany（後台手工皂成分多選精油）
-- 與 mysql/init/01-schema.sql 保持一致。V2 另有 IF NOT EXISTS 補建
--（防舊庫曾因其他原因缺此表；無 Entity 映射，不影響 validate）。
CREATE TABLE IF NOT EXISTS soap_essential_oils (
    soap_id BIGINT NOT NULL,
    essential_oil_id BIGINT NOT NULL,
    PRIMARY KEY (soap_id, essential_oil_id),
    FOREIGN KEY (soap_id) REFERENCES soaps(id) ON DELETE CASCADE,
    FOREIGN KEY (essential_oil_id) REFERENCES essential_oils(id) ON DELETE CASCADE
);

-- 10. Admins Table
CREATE TABLE IF NOT EXISTS admins (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(200),
    role VARCHAR(50) DEFAULT 'ADMIN',
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 11. Contacts Table
CREATE TABLE IF NOT EXISTS contacts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(200) NOT NULL,
    phone VARCHAR(50),
    subject VARCHAR(200),
    message TEXT NOT NULL,
    is_replied BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
