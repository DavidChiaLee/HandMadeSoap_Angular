-- MySQL Database Schema for Handmade Soap Website
-- Run automatically on first container start via docker-entrypoint-initdb.d

-- 確保以 UTF-8 讀取/寫入，避免中文 seed 資料被錯誤編碼
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
-- 與 backend 的 V1__baseline.sql 保持一致；改表只加 V(n+1)，不同步改 V1。
CREATE TABLE IF NOT EXISTS soap_essential_oils (
    soap_id BIGINT NOT NULL,
    essential_oil_id BIGINT NOT NULL,
    PRIMARY KEY (soap_id, essential_oil_id),
    FOREIGN KEY (soap_id) REFERENCES soaps(id) ON DELETE CASCADE,
    FOREIGN KEY (essential_oil_id) REFERENCES essential_oils(id) ON DELETE CASCADE
);

-- 10. Admins Table
-- 註：此為「MySQL 容器首次初始化」的寬鬆版，僅供 docker-entrypoint-initdb.d 建出基礎表。
--      最終約束（email UNIQUE、role CHECK (role IN ('ADMIN'))、各欄 NOT NULL）由 Flyway
--      V2__harden_constraints.sql 收緊，且後端以 ddl-auto=validate 對齊 Admin.java。
--      請勿以此檔判斷最終 schema；新增/修改約束一律走 Flyway migration。
CREATE TABLE IF NOT EXISTS admins (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    role VARCHAR(20) DEFAULT 'ADMIN',
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

-- Seed Categories
INSERT INTO categories (name, sort_order) VALUES
('一般家事皂', 1),
('功能性家事皂', 2),
('可愛造型手工皂', 3),
('其他手工皂', 4),
('其他', 5);
