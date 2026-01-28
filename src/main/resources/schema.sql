-- Database 생성
CREATE DATABASE IF NOT EXISTS webflux_study;
USE webflux_study;

-- Client (고객사) 테이블
CREATE TABLE IF NOT EXISTS client (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Outbound (출고) 테이블
CREATE TABLE IF NOT EXISTS outbound (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    outbound_code VARCHAR(36) NOT NULL UNIQUE,  -- UUID
    outbound_id VARCHAR(50) NOT NULL,           -- 외부 API ID
    client_code VARCHAR(50) NOT NULL,
    order_no VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    shipped_at DATETIME NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_outbound_client_code (client_code),
    INDEX idx_outbound_outbound_id (outbound_id)
);

-- OutboundCost (출고 비용) 테이블
CREATE TABLE IF NOT EXISTS outbound_cost (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    outbound_code VARCHAR(36) NOT NULL,
    cost_type VARCHAR(20) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_outbound_cost_outbound_code (outbound_code),
    CONSTRAINT fk_outbound_cost_outbound
        FOREIGN KEY (outbound_code) REFERENCES outbound(outbound_code)
        ON DELETE CASCADE
);

-- 테스트용 고객사 데이터 (200개)
-- 실제로는 별도 스크립트나 애플리케이션에서 생성
