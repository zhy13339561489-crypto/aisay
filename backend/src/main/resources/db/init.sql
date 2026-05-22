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
    story_id BIGINT,
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
    KEY idx_chat_sessions_story_id (story_id),
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
    status VARCHAR(50) NOT NULL DEFAULT 'draft',
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

ALTER TABLE chat_sessions
    ADD CONSTRAINT fk_chat_sessions_story
        FOREIGN KEY (story_id) REFERENCES stories(id)
        ON DELETE SET NULL;

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

CREATE TABLE IF NOT EXISTS story_volume_outlines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    story_id BIGINT NOT NULL,
    volume_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT,
    content LONGTEXT,
    ending_hook TEXT,
    detailed_content LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_story_volume_number (story_id, volume_number),
    KEY idx_story_volume_outlines_story_id (story_id),
    CONSTRAINT fk_story_volume_outlines_story
        FOREIGN KEY (story_id) REFERENCES stories(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS story_volume_sections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    story_id BIGINT NOT NULL,
    volume_id BIGINT NOT NULL,
    section_number INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary TEXT,
    content LONGTEXT,
    ending_hook TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_volume_section_number (volume_id, section_number),
    KEY idx_story_volume_sections_story_id (story_id),
    KEY idx_story_volume_sections_volume_id (volume_id),
    CONSTRAINT fk_story_volume_sections_story
        FOREIGN KEY (story_id) REFERENCES stories(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_story_volume_sections_volume
        FOREIGN KEY (volume_id) REFERENCES story_volume_outlines(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS story_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    story_id BIGINT NOT NULL,
    asset_type VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    image_prompt TEXT,
    image_path VARCHAR(500),
    audio_path VARCHAR(500),
    first_section_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_story_asset_name (story_id, asset_type, name),
    KEY idx_story_assets_story_id (story_id),
    KEY idx_story_assets_first_section_id (first_section_id),
    CONSTRAINT fk_story_assets_story
        FOREIGN KEY (story_id) REFERENCES stories(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_story_assets_first_section
        FOREIGN KEY (first_section_id) REFERENCES story_volume_sections(id)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS story_section_assets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    story_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_section_asset (section_id, asset_id),
    KEY idx_story_section_assets_story_id (story_id),
    KEY idx_story_section_assets_section_id (section_id),
    KEY idx_story_section_assets_asset_id (asset_id),
    CONSTRAINT fk_story_section_assets_story
        FOREIGN KEY (story_id) REFERENCES stories(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_story_section_assets_section
        FOREIGN KEY (section_id) REFERENCES story_volume_sections(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_story_section_assets_asset
        FOREIGN KEY (asset_id) REFERENCES story_assets(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS story_section_scripts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    story_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    shot_number INT NOT NULL,
    duration_seconds INT NOT NULL,
    shot_type VARCHAR(50) NOT NULL,
    camera_movement VARCHAR(100),
    action TEXT NOT NULL,
    dialogue TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_section_script_shot (section_id, shot_number),
    KEY idx_story_section_scripts_story_id (story_id),
    KEY idx_story_section_scripts_section_id (section_id),
    CONSTRAINT fk_story_section_scripts_story
        FOREIGN KEY (story_id) REFERENCES stories(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_story_section_scripts_section
        FOREIGN KEY (section_id) REFERENCES story_volume_sections(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS story_outline_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    option_type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_story_outline_option_type_name (option_type, name),
    KEY idx_story_outline_options_type_enabled (option_type, enabled),
    KEY idx_story_outline_options_sort (sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO story_outline_options (option_type, name, description, sort_order, enabled)
VALUES
    ('GENRE', '科幻', '科技、未来、人工智能、时间线等题材。', 10, 1),
    ('GENRE', '奇幻', '异世界、魔法、神话、幻想冒险等题材。', 20, 1),
    ('GENRE', '悬疑', '谜案、反转、心理博弈、隐秘真相等题材。', 30, 1),
    ('GENRE', '爱情', '情感成长、关系拉扯、命运羁绊等题材。', 40, 1),
    ('GENRE', '热血', '成长、战斗、团队、逆袭等题材。', 50, 1),
    ('GENRE', '都市', '现代城市、职场、家族、社会议题等题材。', 60, 1),
    ('GENRE', '校园', '校园生活、青春成长、社团与友情等题材。', 70, 1),
    ('STYLE', '国漫电影感', '高质感光影、电影级构图、细腻角色表演。', 10, 1),
    ('STYLE', '日系赛璐璐', '清晰线稿、平涂色块、动画番剧视觉。', 20, 1),
    ('STYLE', '厚涂奇幻', '浓厚笔触、奇幻氛围、强烈光影层次。', 30, 1),
    ('STYLE', '黑白悬疑漫画', '高对比黑白、压迫感构图、悬疑叙事氛围。', 40, 1),
    ('STYLE', '赛博朋克霓虹', '霓虹灯光、未来都市、冷暖强对比。', 50, 1),
    ('STYLE', '水彩治愈系', '柔和色彩、水彩肌理、温暖轻盈氛围。', 60, 1),
    ('STYLE', '复古港漫', '高饱和色彩、强烈动作张力、复古漫画气质。', 70, 1)
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    sort_order = VALUES(sort_order),
    enabled = VALUES(enabled);

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
