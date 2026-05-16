-- AI漫剧生成系统数据库初始化脚本
-- 默认库名按 config.txt/application.yml 使用 springcloud。
-- 如需使用设计文档中的 aisay_manga，可将下方 springcloud 替换为 aisay_manga 后执行。

CREATE DATABASE IF NOT EXISTS springcloud
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'aisay'@'localhost' IDENTIFIED BY 'aisay_pass';
CREATE USER IF NOT EXISTS 'aisay'@'%' IDENTIFIED BY 'aisay_pass';
GRANT ALL PRIVILEGES ON springcloud.* TO 'aisay'@'localhost';
GRANT ALL PRIVILEGES ON springcloud.* TO 'aisay'@'%';
FLUSH PRIVILEGES;

USE springcloud;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar_path VARCHAR(500),
    preferences JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_key VARCHAR(100) NOT NULL,
    title VARCHAR(200),
    context_data JSON,
    current_stage VARCHAR(50) DEFAULT 'INITIAL',
    progress_percentage INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chat_sessions_session_key (session_key),
    KEY idx_chat_sessions_user_id (user_id),
    KEY idx_chat_sessions_status (status),
    KEY idx_chat_sessions_last_active (last_active),
    CONSTRAINT fk_chat_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    metadata JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_messages_session_id (session_id),
    KEY idx_messages_created_at (created_at),
    CONSTRAINT fk_messages_session
        FOREIGN KEY (session_id) REFERENCES chat_sessions(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    genre VARCHAR(100),
    style VARCHAR(100),
    synopsis TEXT,
    full_content LONGTEXT,
    cover_image_path VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    view_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_stories_user_id (user_id),
    KEY idx_stories_status (status),
    FULLTEXT KEY ft_stories_title_synopsis (title, synopsis),
    CONSTRAINT fk_stories_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS characters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    story_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    role VARCHAR(100),
    description TEXT,
    personality TEXT,
    appearance JSON,
    KEY idx_characters_story_id (story_id),
    CONSTRAINT fk_characters_story
        FOREIGN KEY (story_id) REFERENCES stories(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS scenes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    story_id BIGINT NOT NULL,
    scene_number INT NOT NULL,
    setting VARCHAR(500),
    description TEXT,
    visual_elements JSON,
    KEY idx_scenes_story_id (story_id),
    KEY idx_scenes_story_scene_number (story_id, scene_number),
    CONSTRAINT fk_scenes_story
        FOREIGN KEY (story_id) REFERENCES stories(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dialogues (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    scene_id BIGINT NOT NULL,
    character_id BIGINT,
    content TEXT NOT NULL,
    `order` INT NOT NULL DEFAULT 0,
    KEY idx_dialogues_scene_id (scene_id),
    KEY idx_dialogues_character_id (character_id),
    KEY idx_dialogues_scene_order (scene_id, `order`),
    CONSTRAINT fk_dialogues_scene
        FOREIGN KEY (scene_id) REFERENCES scenes(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_dialogues_character
        FOREIGN KEY (character_id) REFERENCES characters(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
