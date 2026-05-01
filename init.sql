-- URL Shortener Database Schema
-- Run once on first startup (docker-entrypoint-initdb.d)

CREATE DATABASE IF NOT EXISTS urlshortener
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE urlshortener;

-- URLs table (primary data store)
CREATE TABLE IF NOT EXISTS urls (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_code   VARCHAR(10)   NOT NULL,
    original_url VARCHAR(2048) NOT NULL,
    title        VARCHAR(255),
    user_id      VARCHAR(100),
    click_count  BIGINT        NOT NULL DEFAULT 0,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    custom_alias BOOLEAN       NOT NULL DEFAULT FALSE,
    expires_at   DATETIME,
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE INDEX idx_short_code (short_code),
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    INDEX idx_expires_at (expires_at),
    INDEX idx_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC;

-- Click events table (analytics, append-only)
CREATE TABLE IF NOT EXISTS click_events (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_code   VARCHAR(10)  NOT NULL,
    ip_address   VARCHAR(45),
    user_agent   VARCHAR(500),
    referer      VARCHAR(100),
    country      VARCHAR(50),
    city         VARCHAR(50),
    device_type  VARCHAR(20),
    clicked_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_click_short_code (short_code),
    INDEX idx_click_created_at (clicked_at),
    INDEX idx_click_country (country)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  ROW_FORMAT=DYNAMIC;

-- Partitioning click_events by month (optional, for very high volume)
-- Uncomment if you expect >100M events:
-- ALTER TABLE click_events PARTITION BY RANGE (YEAR(clicked_at) * 100 + MONTH(clicked_at)) (
--     PARTITION p_2024_01 VALUES LESS THAN (202402),
--     PARTITION p_2024_02 VALUES LESS THAN (202403),
--     PARTITION p_future VALUES LESS THAN MAXVALUE
-- );
