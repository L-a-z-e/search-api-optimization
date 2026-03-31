CREATE DATABASE IF NOT EXISTS search_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE search_db;

-- CDC를 위한 binlog 설정은 docker-compose command에서 처리

CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    brand VARCHAR(100) NOT NULL,
    category VARCHAR(100) NOT NULL,
    price BIGINT NOT NULL,
    sales_count BIGINT NOT NULL DEFAULT 0,
    promoted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category),
    INDEX idx_brand (brand),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- FULLTEXT index (N-gram parser for Korean)
ALTER TABLE product ADD FULLTEXT INDEX ft_name (name) WITH PARSER ngram;
