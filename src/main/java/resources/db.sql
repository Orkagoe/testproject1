-- Создаем базу данных flashcards, если она еще не существует
CREATE DATABASE IF NOT EXISTS flashcards
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Используем созданную базу данных
USE flashcards;

-- Лиги
CREATE TABLE IF NOT EXISTS leagues (
                                       id INT AUTO_INCREMENT PRIMARY KEY,
                                       name VARCHAR(255) NOT NULL UNIQUE
) ENGINE=InnoDB;

-- Категории игроков
CREATE TABLE IF NOT EXISTS player_categories (
                                                 id INT AUTO_INCREMENT PRIMARY KEY,
                                                 name VARCHAR(255) NOT NULL UNIQUE,
                                                 weight INT NOT NULL CHECK (weight > 0),  -- Вес категории для вероятности выпадения
                                                 points INT NOT NULL CHECK (points >= 0)  -- Очки за получение игрока этой категории
) ENGINE=InnoDB;

-- Команды (уникальность по названию и лиге)
CREATE TABLE IF NOT EXISTS teams (
                                     id INT AUTO_INCREMENT PRIMARY KEY,
                                     name VARCHAR(255) NOT NULL,
                                     league_id INT NOT NULL,
                                     FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE,
                                     UNIQUE KEY unique_team_in_league (name, league_id)
) ENGINE=InnoDB;

-- Игроки
CREATE TABLE IF NOT EXISTS players (
                                       id INT AUTO_INCREMENT PRIMARY KEY,
                                       name VARCHAR(255) NOT NULL,
                                       team_id INT NOT NULL,
                                       position VARCHAR(255) NOT NULL,
                                       rating INT NOT NULL CHECK (rating BETWEEN 0 AND 100),
                                       category_id INT NOT NULL,
                                       photo VARCHAR(255) NOT NULL,
                                       FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
                                       FOREIGN KEY (category_id) REFERENCES player_categories(id) ON DELETE CASCADE,
                                       INDEX idx_team_id (team_id),
                                       INDEX idx_category_id (category_id)
) ENGINE=InnoDB;

-- Пользователи
CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT PRIMARY KEY,
                                     username VARCHAR(255) NOT NULL,
                                     last_spin TIMESTAMP NULL DEFAULT NULL,
                                     points BIGINT DEFAULT 0 CHECK (points >= 0),
                                     gift_pack_claims INT DEFAULT 0 CHECK (gift_pack_claims >= 0)
) ENGINE=InnoDB;

-- Связь пользователь-игрок
CREATE TABLE IF NOT EXISTS user_players (
                                            user_id BIGINT NOT NULL,
                                            player_id INT NOT NULL,
                                            PRIMARY KEY (user_id, player_id),
                                            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                            FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
                                            INDEX idx_user_id (user_id),
                                            INDEX idx_player_id (player_id)
) ENGINE=InnoDB;

-- Таблица кланов
CREATE TABLE IF NOT EXISTS clans (
                                     id INT AUTO_INCREMENT PRIMARY KEY,
                                     name VARCHAR(255) NOT NULL UNIQUE,
                                     owner_id BIGINT NOT NULL,
                                     FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Связь клан-пользователь
CREATE TABLE IF NOT EXISTS clan_members (
                                            clan_id INT NOT NULL,
                                            user_id BIGINT NOT NULL,
                                            PRIMARY KEY (clan_id, user_id),
                                            FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE,
                                            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                            INDEX idx_clan_id (clan_id),
                                            INDEX idx_user_id (user_id)
) ENGINE=InnoDB;

-- Вставка тестовых данных

-- Добавляем лиги

-- Добавляем категории игроков
INSERT INTO player_categories (name, weight, points) VALUES ('Greatest', 1, 2500);  -- 1%
INSERT INTO player_categories (name, weight, points) VALUES ('Diamond', 5, 1500);   -- 5%
INSERT INTO player_categories (name, weight, points) VALUES ('Gold', 14, 700);     -- 14%
INSERT INTO player_categories (name, weight, points) VALUES ('Silver', 35, 250);   -- 35%
INSERT INTO player_categories (name, weight, points) VALUES ('Bronze', 45, 100);   -- 45%

-- Добавляем команды


-- Добавляем тестовый клан
INSERT INTO clans (name, owner_id) VALUES ('TestClan', 123456789);

-- Добавляем пользователя в клан
INSERT INTO clan_members (clan_id, user_id) VALUES (1, 123456789);