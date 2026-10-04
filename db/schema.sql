CREATE DATABASE IF NOT EXISTS minesweeper
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE minesweeper;

CREATE TABLE IF NOT EXISTS users (
                                     id            BIGINT PRIMARY KEY AUTO_INCREMENT,
                                     username      VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS game_records (
                                            id               BIGINT PRIMARY KEY AUTO_INCREMENT,
                                            user_id          BIGINT NOT NULL,
                                            difficulty       VARCHAR(10) NOT NULL,
    duration_seconds INT NOT NULL,
    win              BOOLEAN NOT NULL,
    played_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_records_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_records_user (user_id),
    INDEX idx_records_diff (difficulty)
    );

CREATE TABLE IF NOT EXISTS leaderboard (
                                           id                BIGINT PRIMARY KEY AUTO_INCREMENT,
                                           user_id           BIGINT NOT NULL,
                                           difficulty        VARCHAR(10) NOT NULL,
    best_time_seconds INT NOT NULL,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_lb_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_diff UNIQUE (user_id, difficulty)
    );

CREATE TABLE IF NOT EXISTS admin_actions (
                                             id         BIGINT PRIMARY KEY AUTO_INCREMENT,
                                             action     VARCHAR(50)  NOT NULL,
    target     VARCHAR(100),
    details    VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin_actions_time (created_at)
    );