package ru.apache_maven.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.apache_maven.model.*;

import java.sql.*;
import java.util.*;


/**
 * Класс для управления соединением с базой данных и выполнения операций CRUD.
 */
public class DatabaseManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private final Connection connection;

    private static final String DB_URL = "jdbc:mysql://localhost:3306/flashcards?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASS = "Oryn0basar";
    private static final long SPIN_COOLDOWN = 24 * 60 * 60 * 1000; // 24 часа в миллисекундах

    public DatabaseManager() {
        try {
            this.connection = DriverManager.getConnection(DB_URL, USER, PASS);
            initializeDatabase();
            logger.info("Соединение с базой данных установлено");
        } catch (SQLException e) {
            logger.error("Ошибка при создании соединения: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось создать соединение", e);
        }
    }

    public DatabaseManager(String dbUrl, String dbUser, String dbPassword) {
        if (!dbUrl.contains("allowPublicKeyRetrieval=true")) {
            dbUrl += "&allowPublicKeyRetrieval=true";
        }
        try {
            this.connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            initializeDatabase();
            logger.info("Соединение с базой данных установлено (конструктор с параметрами)");
        } catch (SQLException e) {
            logger.error("Ошибка при создании соединения: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось создать соединение", e);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            logger.info("Соединение с базой данных закрыто");
        }
    }

    /**
     * Инициализация таблиц базы данных.
     */
    private void initializeDatabase() {
        try (Statement stmt = connection.createStatement()) {
            // Таблица лиг
            stmt.execute("CREATE TABLE IF NOT EXISTS leagues (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL UNIQUE) ENGINE=InnoDB");

            // Таблица команд
            stmt.execute("CREATE TABLE IF NOT EXISTS teams (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, league_id INT NOT NULL, FOREIGN KEY (league_id) REFERENCES leagues(id) ON DELETE CASCADE, UNIQUE KEY unique_team_in_league (name, league_id)) ENGINE=InnoDB");

            // Таблица категорий игроков
            stmt.execute("CREATE TABLE IF NOT EXISTS player_categories (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) NOT NULL UNIQUE, weight INT NOT NULL CHECK (weight >= 0), points INT NOT NULL CHECK (points >= 0), dollars INT NOT NULL CHECK (dollars >= 0)) ENGINE=InnoDB");

            // Таблица игроков
            stmt.execute("CREATE TABLE IF NOT EXISTS players (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, team_id INT NOT NULL, position VARCHAR(255) NOT NULL, rating INT NOT NULL CHECK (rating BETWEEN 0 AND 100), category_id INT NOT NULL, photo VARCHAR(255) NOT NULL, FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE, FOREIGN KEY (category_id) REFERENCES player_categories(id) ON DELETE RESTRICT, INDEX idx_team_id (team_id), INDEX idx_category_id (category_id)) ENGINE=InnoDB");

            // Таблица пользователей
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id BIGINT PRIMARY KEY, username VARCHAR(255) NOT NULL, last_spin TIMESTAMP NULL DEFAULT NULL, points BIGINT DEFAULT 0 CHECK (points >= 0), dollars INT DEFAULT 0 CHECK (dollars >= 0), gift_pack_claims INT DEFAULT 0 CHECK (gift_pack_claims >= 0), favorite_card_id INT DEFAULT NULL, title VARCHAR(255) DEFAULT 'Новичок', last_gift TIMESTAMP NULL DEFAULT NULL, FOREIGN KEY (favorite_card_id) REFERENCES players(id) ON DELETE SET NULL) ENGINE=InnoDB");

            // Связь пользователь-игрок
            stmt.execute("CREATE TABLE IF NOT EXISTS user_players (user_id BIGINT NOT NULL, player_id INT NOT NULL, PRIMARY KEY (user_id, player_id), FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE, INDEX idx_user_id (user_id), INDEX idx_player_id (player_id)) ENGINE=InnoDB");

            // Таблица кланов
            stmt.execute("CREATE TABLE IF NOT EXISTS clans (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL UNIQUE, owner_id BIGINT NOT NULL, FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE) ENGINE=InnoDB");

            // Связь клан-пользователь
            stmt.execute("CREATE TABLE IF NOT EXISTS clan_members (clan_id INT NOT NULL, user_id BIGINT NOT NULL, PRIMARY KEY (clan_id, user_id), FOREIGN KEY (clan_id) REFERENCES clans(id) ON DELETE CASCADE, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, INDEX idx_clan_id (clan_id), INDEX idx_user_id (user_id)) ENGINE=InnoDB");

            // Таблица составов пользователей
            stmt.execute("CREATE TABLE IF NOT EXISTS user_squads (user_id BIGINT NOT NULL, position VARCHAR(10) NOT NULL, player_id INT, PRIMARY KEY (user_id, position), FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE SET NULL) ENGINE=InnoDB");

            // Таблица для футжобов
            stmt.execute("CREATE TABLE IF NOT EXISTS footjobs (id INT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, target_user_id BIGINT NOT NULL, timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE CASCADE) ENGINE=InnoDB");

            // Таблица паков
            stmt.execute("CREATE TABLE IF NOT EXISTS packs (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL, description TEXT, price INT NOT NULL, player_count INT NOT NULL, category VARCHAR(50), is_daily BOOLEAN DEFAULT FALSE, cooldown_hours INT DEFAULT 0) ENGINE=InnoDB");

            // Таблица паков пользователей
            stmt.execute("CREATE TABLE IF NOT EXISTS user_packs (id INT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, pack_id INT NOT NULL, quantity INT DEFAULT 1, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, FOREIGN KEY (pack_id) REFERENCES packs(id) ON DELETE CASCADE) ENGINE=InnoDB");

            // Таблица категорий сложности ИИ
            stmt.execute("CREATE TABLE IF NOT EXISTS ai_difficulty_categories (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) NOT NULL UNIQUE, description VARCHAR(255)) ENGINE=InnoDB");

            // Таблица составов ИИ
            stmt.execute("CREATE TABLE IF NOT EXISTS ai_squads (id INT AUTO_INCREMENT PRIMARY KEY, difficulty_id INT NOT NULL, name VARCHAR(255) NOT NULL, gk INT, cb1 INT, cb2 INT, cb3 INT, mid1 INT, mid2 INT, mid3 INT, frw1 INT, frw2 INT, frw3 INT, extra INT, FOREIGN KEY (difficulty_id) REFERENCES ai_difficulty_categories(id) ON DELETE CASCADE, FOREIGN KEY (gk) REFERENCES players(id) ON DELETE SET NULL, FOREIGN KEY (cb1) REFERENCES players(id) ON DELETE SET NULL, FOREIGN KEY (cb2) REFERENCES players(id) ON DELETE SET NULL, FOREIGN KEY (cb3) REFERENCES players(id) ON DELETE SET NULL, FOREIGN KEY (mid1) REFERENCES players(id) ON DELETE SET NULL, FOREIGN KEY (mid2) REFERENCES players(id) ON DELETE SET NULL, FOREIGN KEY (mid3) REFERENCES players(id) ON DELETE SET NULL, FOREIGN KEY (frw1) REFERENCES players(id) ON DELETE SET NULL, FOREIGN KEY (frw2) REFERENCES players(id) ON DELETE SET NULL, FOREIGN KEY (frw3) REFERENCES players(id) ON DELETE SET NULL, FOREIGN KEY (extra) REFERENCES players(id) ON DELETE SET NULL, UNIQUE KEY unique_squad_name (name, difficulty_id)) ENGINE=InnoDB");

            // Добавляем начальные категории сложности
            stmt.execute("INSERT IGNORE INTO ai_difficulty_categories (name, description) VALUES ('easy', 'Лёгкий бот'), ('medium', 'Средний бот'), ('hard', 'Сложный бот')");

            // Добавляем индекс для таблицы user_packs

            logger.info("Таблицы базы данных успешно инициализированы.");
        } catch (SQLException e) {
            logger.error("Ошибка инициализации базы данных: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось инициализировать базу данных", e);
        }
    }

    /**
     * Получение списка категорий сложности ИИ.
     */
    public List<Map<String, String>> getAIDifficultyCategories() throws SQLException {
        List<Map<String, String>> categories = new ArrayList<>();
        String sql = "SELECT name, description FROM ai_difficulty_categories";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, String> category = new HashMap<>();
                category.put("name", rs.getString("name"));
                category.put("description", rs.getString("description"));
                categories.add(category);
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения категорий сложности ИИ: {}", e.getMessage(), e);
            throw e;
        }
        return categories;
    }

    /**
     * Получение случайного состава ИИ для заданной категории сложности.
     */
    public Map<String, Player> getRandomAISquad(String difficulty) throws SQLException {
        String sql = "SELECT * FROM ai_squads WHERE difficulty_id = (SELECT id FROM ai_difficulty_categories WHERE name = ?) ORDER BY RAND() LIMIT 1";
        Map<String, Player> squad = new HashMap<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, difficulty);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String[] positions = {"gk", "cb1", "cb2", "cb3", "mid1", "mid2", "mid3", "frw1", "frw2", "frw3", "extra"};
                    for (String pos : positions) {
                        int playerId = rs.getInt(pos);
                        Player player = rs.wasNull() ? null : getPlayerById(playerId);
                        squad.put(pos.toUpperCase(), player);
                    }
                    logger.info("Выбран состав ИИ: {} для сложности {}", rs.getString("name"), difficulty);
                } else {
                    logger.warn("Составы для сложности {} не найдены", difficulty);
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения состава ИИ для сложности {}: {}", difficulty, e.getMessage(), e);
            throw e;
        }
        return squad;
    }

    /**
     * Получение списка всех категорий игроков.
     */
    public List<PlayerCategory> getPlayerCategories() throws SQLException {
        List<PlayerCategory> categories = new ArrayList<>();
        String sql = "SELECT * FROM player_categories";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                categories.add(new PlayerCategory(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("weight"),
                        rs.getInt("points"),
                        rs.getInt("dollars")
                ));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения категорий игроков: {}", e.getMessage(), e);
            throw e;
        }
        return categories;
    }

    /**
     * Получение категории игрока по ID.
     */
    public PlayerCategory getPlayerCategoryById(int categoryId) throws SQLException {
        String sql = "SELECT * FROM player_categories WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, categoryId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerCategory(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("weight"),
                            rs.getInt("points"),
                            rs.getInt("dollars")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения категории игрока по ID {}: {}", categoryId, e.getMessage(), e);
            throw e;
        }
        return null;
    }

    /**
     * Получение категории игрока по имени.
     */
    public PlayerCategory getPlayerCategoryByName(String categoryName) throws SQLException {
        String sql = "SELECT * FROM player_categories WHERE name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, categoryName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerCategory(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("weight"),
                            rs.getInt("points"),
                            rs.getInt("dollars")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения категории игрока по имени {}: {}", categoryName, e.getMessage(), e);
            throw e;
        }
        throw new SQLException("Категория с именем " + categoryName + " не найдена");
    }

    /**
     * Получение списка всех лиг.
     */
    public List<League> getLeagues() throws SQLException {
        List<League> leagues = new ArrayList<>();
        String sql = "SELECT * FROM leagues";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                leagues.add(new League(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения лиг: {}", e.getMessage(), e);
            throw e;
        }
        return leagues;
    }

    /**
     * Получение названия лиги по ID.
     */
    public String getLeagueName(int leagueId) throws SQLException {
        String sql = "SELECT name FROM leagues WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, leagueId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения названия лиги с ID {}: {}", leagueId, e.getMessage(), e);
            throw e;
        }
        logger.warn("Лига с ID {} не найдена", leagueId);
        return "Unknown League (ID: " + leagueId + ")";
    }

    /**
     * Получение списка команд по ID лиги.
     */
    public List<Team> getTeamsByLeague(int leagueId) throws SQLException {
        List<Team> teams = new ArrayList<>();
        String sql = "SELECT * FROM teams WHERE league_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, leagueId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    teams.add(new Team(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("league_id")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения команд для лиги {}: {}", leagueId, e.getMessage(), e);
            throw e;
        }
        return teams;
    }

    /**
     * Получение названия команды по ID.
     */
    public String getTeamName(int teamId) throws SQLException {
        String sql = "SELECT name FROM teams WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, teamId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("name");
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения названия команды с ID {}: {}", teamId, e.getMessage(), e);
            throw e;
        }
        logger.warn("Команда с ID {} не найдена", teamId);
        return "Unknown Team (ID: " + teamId + ")";
    }

    /**
     * Получение ID лиги команды.
     */
    public int getTeamLeagueId(int teamId) throws SQLException {
        String sql = "SELECT league_id FROM teams WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, teamId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("league_id");
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения ID лиги команды с ID {}: {}", teamId, e.getMessage(), e);
            throw e;
        }
        throw new SQLException("Команда с ID " + teamId + " не найдена");
    }

    /**
     * Получение случайного игрока.
     */
    public Player getRandomPlayer() throws SQLException {
        List<PlayerCategory> categories = new ArrayList<>();
        String sqlCategories = "SELECT * FROM player_categories WHERE weight > 0";
        try (PreparedStatement stmt = connection.prepareStatement(sqlCategories);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                categories.add(new PlayerCategory(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("weight"),
                        rs.getInt("points"),
                        rs.getInt("dollars")
                ));
            }
        }

        if (categories.isEmpty()) {
            logger.error("Нет категорий игроков с весом > 0");
            return null;
        }

        int totalWeight = categories.stream().mapToInt(PlayerCategory::getWeight).sum();
        if (totalWeight <= 0) {
            logger.error("Общий вес категорий равен 0");
            return null;
        }

        double randomValue = Math.random() * totalWeight;
        int cumulativeWeight = 0;
        PlayerCategory selectedCategory = null;

        for (PlayerCategory category : categories) {
            cumulativeWeight += category.getWeight();
            if (randomValue < cumulativeWeight) {
                selectedCategory = category;
                break;
            }
        }

        if (selectedCategory == null) {
            selectedCategory = categories.get(categories.size() - 1);
            logger.warn("Не удалось выбрать категорию на основе весов, выбираем последнюю категорию: {}", selectedCategory.getName());
        }

        logger.info("Выбрана категория: {}", selectedCategory.getName());

        Player player = getRandomPlayerByCategory(selectedCategory.getName());
        if (player != null) {
            return player;
        }

        for (PlayerCategory fallbackCategory : categories) {
            if (fallbackCategory.getId() == selectedCategory.getId()) continue;
            player = getRandomPlayerByCategory(fallbackCategory.getName());
            if (player != null) {
                return player;
            }
        }

        logger.error("Игроки не найдены ни в одной категории");
        return null;
    }

    /**
     * Получение случайного игрока по категории (по имени категории).
     */
    public Player getRandomPlayerByCategory(String categoryName) throws SQLException {
        String sql = "SELECT p.*, pc.id as pc_id, pc.name as pc_name, pc.weight as pc_weight, pc.points as pc_points, pc.dollars as pc_dollars FROM players p JOIN player_categories pc ON p.category_id = pc.id WHERE pc.name = ? ORDER BY RAND() LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, categoryName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    PlayerCategory category = new PlayerCategory(
                            rs.getInt("pc_id"),
                            rs.getString("pc_name"),
                            rs.getInt("pc_weight"),
                            rs.getInt("pc_points"),
                            rs.getInt("pc_dollars")
                    );
                    return new Player(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("team_id"),
                            rs.getString("position"),
                            rs.getInt("rating"),
                            category,
                            rs.getString("photo")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка при получении случайного игрока из категории {}: {}", categoryName, e.getMessage(), e);
            throw e;
        }
        return null;
    }

    /**
     * Получение игрока по ID.
     */
    public Player getPlayerById(int playerId) throws SQLException {
        String sql = "SELECT p.*, pc.id as pc_id, pc.name as pc_name, pc.weight as pc_weight, pc.points as pc_points, pc.dollars as pc_dollars FROM players p JOIN player_categories pc ON p.category_id = pc.id WHERE p.id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    PlayerCategory category = new PlayerCategory(
                            rs.getInt("pc_id"),
                            rs.getString("pc_name"),
                            rs.getInt("pc_weight"),
                            rs.getInt("pc_points"),
                            rs.getInt("pc_dollars")
                    );
                    return new Player(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("team_id"),
                            rs.getString("position"),
                            rs.getInt("rating"),
                            category,
                            rs.getString("photo")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения игрока по ID {}: {}", playerId, e.getMessage(), e);
            throw e;
        }
        return null;
    }

    /**
     * Получение всех игроков пользователя.
     */
    public List<Player> getUserPlayers(long userId) throws SQLException {
        List<Player> players = new ArrayList<>();
        String sql = "SELECT p.*, pc.id as pc_id, pc.name as pc_name, pc.weight as pc_weight, pc.points as pc_points, pc.dollars as pc_dollars FROM players p JOIN player_categories pc ON p.category_id = pc.id JOIN user_players up ON p.id = up.player_id WHERE up.user_id = ? ORDER BY p.rating DESC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    PlayerCategory category = new PlayerCategory(
                            rs.getInt("pc_id"),
                            rs.getString("pc_name"),
                            rs.getInt("pc_weight"),
                            rs.getInt("pc_points"),
                            rs.getInt("pc_dollars")
                    );
                    players.add(new Player(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("team_id"),
                            rs.getString("position"),
                            rs.getInt("rating"),
                            category,
                            rs.getString("photo")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения игроков пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        return players;
    }

    /**
     * Получение игроков пользователя по ID команды.
     */
    public List<Player> getUserPlayersByTeam(long userId, int teamId) throws SQLException {
        List<Player> players = new ArrayList<>();
        String sql = "SELECT p.*, pc.id as pc_id, pc.name as pc_name, pc.weight as pc_weight, pc.points as pc_points, pc.dollars as pc_dollars FROM players p JOIN player_categories pc ON p.category_id = pc.id JOIN user_players up ON p.id = up.player_id WHERE up.user_id = ? AND p.team_id = ? ORDER BY p.rating DESC";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setInt(2, teamId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    PlayerCategory category = new PlayerCategory(
                            rs.getInt("pc_id"),
                            rs.getString("pc_name"),
                            rs.getInt("pc_weight"),
                            rs.getInt("pc_points"),
                            rs.getInt("pc_dollars")
                    );
                    players.add(new Player(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("team_id"),
                            rs.getString("position"),
                            rs.getInt("rating"),
                            category,
                            rs.getString("photo")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения игроков пользователя {} из команды {}: {}", userId, teamId, e.getMessage(), e);
            throw e;
        }
        return players;
    }

    /**
     * Добавление нового игрока в таблицу players.
     */
    private int addPlayer(Player player) throws SQLException {
        String sql = "INSERT INTO players (name, team_id, position, rating, category_id, photo) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, player.getName());
            pstmt.setInt(2, player.getTeamId());
            pstmt.setString(3, player.getPosition());
            pstmt.setInt(4, player.getRating());
            pstmt.setInt(5, player.getCategory().getId());
            pstmt.setString(6, player.getPhoto());
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Создание игрока не удалось, нет затронутых строк.");
            }

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    logger.info("Добавлен новый игрок: {} (ID: {})", player.getName(), id);
                    return id;
                } else {
                    throw new SQLException("Не удалось получить ID созданного игрока.");
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка добавления игрока {}: {}", player.getName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Поиск ID игрока по его данным.
     */
    private Integer getPlayerId(Player player) throws SQLException {
        String sql = "SELECT id FROM players WHERE name = ? AND team_id = ? AND position = ? AND rating = ? AND category_id = ? AND photo = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getName());
            pstmt.setInt(2, player.getTeamId());
            pstmt.setString(3, player.getPosition());
            pstmt.setInt(4, player.getRating());
            pstmt.setInt(5, player.getCategory().getId());
            pstmt.setString(6, player.getPhoto());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка поиска игрока {}: {}", player.getName(), e.getMessage(), e);
            throw e;
        }
        return null;
    }

    /**
     * Получение или создание ID игрока.
     */
    private int getOrCreatePlayerId(Player player) throws SQLException {
        Integer playerId = getPlayerId(player);
        if (playerId == null) {
            return addPlayer(player);
        }
        return playerId;
    }

    /**
     * Добавление игрока пользователю.
     */
    public void addPlayerToUser(long userId, Player player) throws SQLException {
        connection.setAutoCommit(false);
        try {
            if (!userExists(userId)) {
                throw new SQLException("Пользователь с ID " + userId + " не найден");
            }

            int playerId = getOrCreatePlayerId(player);

            String sql = "INSERT IGNORE INTO user_players (user_id, player_id) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                pstmt.setInt(2, playerId);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    logger.info("Игрок {} (ID: {}) добавлен пользователю {}", player.getName(), playerId, userId);
                } else {
                    logger.info("Игрок {} (ID: {}) уже принадлежит пользователю {}", player.getName(), playerId, userId);
                }
            }

            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Ошибка при добавлении игрока пользователю {}: {}", userId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Проверка существования пользователя.
     */
    public boolean userExists(long userId) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.error("Ошибка проверки пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Добавление или обновление пользователя.
     */
    public void addUser(long userId, String username) throws SQLException {
        connection.setAutoCommit(false);
        try {
            String sql = "INSERT INTO users (id, username, points, dollars, gift_pack_claims, last_spin) VALUES (?, ?, 0, 0, 0, NULL) ON DUPLICATE KEY UPDATE username = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                pstmt.setString(2, username);
                pstmt.setString(3, username);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    logger.info("Добавлен пользователь {}: {}", userId, username);
                } else {
                    logger.info("Обновлено имя пользователя {}: {}", userId, username);
                }
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Ошибка добавления пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Получение пользователя по ID.
     */
    public User getUserById(long userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int favoriteCardId = rs.getInt("favorite_card_id");
                    return new User(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getLong("points"),
                            rs.wasNull() ? null : favoriteCardId,
                            rs.getString("title")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения пользователя по ID {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        return null;
    }

    /**
     * Начисление очков пользователю.
     */
    public void addPoints(long userId, int points) throws SQLException {
        connection.setAutoCommit(false);
        try {
            if (!userExists(userId)) {
                throw new SQLException("Пользователь с ID " + userId + " не найден");
            }

            String sql = "UPDATE users SET points = points + ? WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, points);
                pstmt.setLong(2, userId);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Не удалось начислить очки: пользователь с ID " + userId + " не найден");
                }
                logger.info("Начислено {} очков пользователю {}", points, userId);
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Ошибка начисления очков пользователю {}: {}", userId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Начисление долларов пользователю.
     */
    public void addDollars(long userId, int dollars) throws SQLException {
        connection.setAutoCommit(false);
        try {
            if (!userExists(userId)) {
                throw new SQLException("Пользователь с ID " + userId + " не найден");
            }

            String sql = "UPDATE users SET dollars = dollars + ? WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, dollars);
                pstmt.setLong(2, userId);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Не удалось начислить доллары: пользователь с ID " + userId + " не найден");
                }
                logger.info("Начислено {} долларов пользователю {}", dollars, userId);
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Ошибка начисления долларов пользователю {}: {}", userId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Получение количества долларов пользователя.
     */
    public int getUserDollars(long userId) throws SQLException {
        if (!userExists(userId)) {
            throw new SQLException("Пользователь с ID " + userId + " не найден");
        }
        String sql = "SELECT dollars FROM users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("dollars");
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения долларов пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        return 0;
    }

    /**
     * Получение времени последнего спина пользователя.
     */
    public Timestamp getLastSpin(long userId) throws SQLException {
        if (!userExists(userId)) {
            throw new SQLException("Пользователь с ID " + userId + " не найден");
        }
        String sql = "SELECT last_spin FROM users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("last_spin");
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения last_spin для пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        return null;
    }

    /**
     * Обновление времени последнего спина пользователя.
     */
    public void updateLastSpin(long userId) throws SQLException {
        if (!userExists(userId)) {
            throw new SQLException("Пользователь с ID " + userId + " не найден");
        }
        String sql = "UPDATE users SET last_spin = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            pstmt.setTimestamp(1, now);
            pstmt.setLong(2, userId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Не удалось обновить время спина: пользователь с ID " + userId + " не найден");
            }
            logger.info("Обновлено время последнего спина для пользователя {}", userId);
        } catch (SQLException e) {
            logger.error("Ошибка обновления last_spin для пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Получение количества полученных подарочных наборов пользователем.
     */
    public int getGiftPackClaims(long userId) throws SQLException {
        if (!userExists(userId)) {
            throw new SQLException("Пользователь с ID " + userId + " не найден");
        }
        String sql = "SELECT gift_pack_claims FROM users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("gift_pack_claims");
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения gift_pack_claims для пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        return 0;
    }

    /**
     * Проверка и сброс счётчика подарочных наборов, если прошло более 24 часов.
     */
    public int checkAndResetGiftPackClaims(long userId) throws SQLException {
        Timestamp lastSpin = getLastSpin(userId);
        long currentTime = System.currentTimeMillis();
        if (lastSpin != null && (currentTime - lastSpin.getTime()) >= SPIN_COOLDOWN) {
            resetGiftPackClaims(userId);
            return 0;
        }
        return getGiftPackClaims(userId);
    }

    /**
     * Увеличение счетчика полученных подарочных наборов.
     */
    public void incrementGiftPackClaims(long userId) throws SQLException {
        connection.setAutoCommit(false);
        try {
            if (!userExists(userId)) {
                throw new SQLException("Пользователь с ID " + userId + " не найден");
            }

            String sql = "UPDATE users SET gift_pack_claims = gift_pack_claims + 1 WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Не удалось увеличить gift_pack_claims: пользователь с ID " + userId + " не найден");
                }
                logger.info("Увеличено gift_pack_claims для пользователя {}", userId);
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Ошибка увеличения gift_pack_claims для пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Сброс количества полученных подарочных наборов.
     */
    public void resetGiftPackClaims(long userId) throws SQLException {
        connection.setAutoCommit(false);
        try {
            if (!userExists(userId)) {
                throw new SQLException("Пользователь с ID " + userId + " не найден");
            }

            String sql = "UPDATE users SET gift_pack_claims = 0 WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Не удалось сбросить gift_pack_claims: пользователь с ID " + userId + " не найден");
                }
                logger.info("Сброшено gift_pack_claims для пользователя {}", userId);
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Ошибка сброса gift_pack_claims для пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Получение топ пользователей по очкам.
     */
    public List<User> getTopUsersByPoints(int limit) throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY points DESC LIMIT ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int favoriteCardId = rs.getInt("favorite_card_id");
                    users.add(new User(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getLong("points"),
                            rs.wasNull() ? null : favoriteCardId,
                            rs.getString("title")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения топ пользователей: {}", e.getMessage(), e);
            throw e;
        }
        return users;
    }

    /**
     * Получение очков пользователя.
     */
    public long getUserPoints(long userId) throws SQLException {
        if (!userExists(userId)) {
            throw new SQLException("Пользователь с ID " + userId + " не найден");
        }
        String sql = "SELECT points FROM users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("points");
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения очков пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        return 0;
    }

    /**
     * Установка любимой карты.
     */
    public void setFavoriteCard(long userId, int cardId) throws SQLException {
        String sql = "UPDATE users SET favorite_card_id = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, cardId);
            pstmt.setLong(2, userId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Не удалось установить любимую карту: пользователь с ID " + userId + " не найден");
            }
            logger.info("Установлена любимая карта ID {} для пользователя {}", cardId, userId);
        } catch (SQLException e) {
            logger.error("Ошибка установки любимой карты для пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Получение любимой карты.
     */
    public Player getFavoriteCard(long userId) throws SQLException {
        String sql = "SELECT favorite_card_id FROM users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int cardId = rs.getInt("favorite_card_id");
                    if (!rs.wasNull()) {
                        return getPlayerById(cardId);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения любимой карты пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        return null;
    }

    /**
     * Установка титула.
     */
    public void setTitle(long userId, String title) throws SQLException {
        String sql = "UPDATE users SET title = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, title);
            pstmt.setLong(2, userId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Не удалось установить титул: пользователь с ID " + userId + " не найден");
            }
            logger.info("Установлен титул '{}' для пользователя {}", title, userId);
        } catch (SQLException e) {
            logger.error("Ошибка установки титула для пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Получение титула.
     */
    public String getTitle(long userId) throws SQLException {
        String sql = "SELECT title FROM users WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("title");
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения титула пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        return "Новичок";
    }

    /**
     * Подсчёт общего количества карточек в игре.
     */
    public int getTotalCardsCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM players";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Ошибка подсчёта общего количества карточек: {}", e.getMessage(), e);
            throw e;
        }
        return 0;
    }

    /**
     * Добавление футжоба.
     */
    public void addFootjob(long userId, long targetUserId) throws SQLException {
        String sql = "INSERT INTO footjobs (user_id, target_user_id) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, targetUserId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Не удалось добавить футжоб для пользователя " + userId);
            }
            logger.info("Добавлен футжоб: пользователь {} -> {}", userId, targetUserId);
        } catch (SQLException e) {
            logger.error("Ошибка добавления футжоба для пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Подсчёт общего количества футжобов.
     */
    public int getTotalFootjobsCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM footjobs";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Ошибка подсчёта общего количества футжобов: {}", e.getMessage(), e);
            throw e;
        }
        return 0;
    }

    /**
     * Получение топ кланов по суммарным очкам участников.
     */
    public List<Clan> getTopClans(int page, int limit) throws SQLException {
        List<Clan> clans = new ArrayList<>();
        String sql = "SELECT c.id, c.name, c.owner_id, COALESCE(SUM(u.points), 0) as total_points FROM clans c LEFT JOIN clan_members cm ON c.id = cm.clan_id LEFT JOIN users u ON cm.user_id = u.id GROUP BY c.id, c.name, c.owner_id ORDER BY total_points DESC LIMIT ? OFFSET ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            pstmt.setInt(2, (page - 1) * limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    clans.add(new Clan(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getLong("owner_id"),
                            rs.getLong("total_points")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения топ кланов: {}", e.getMessage(), e);
            throw e;
        }
        return clans;
    }

    /**
     * Получение клана пользователя.
     */
    public Clan getUserClan(long userId) throws SQLException {
        String sql = "SELECT c.*, COALESCE(SUM(u.points), 0) as total_points FROM clans c JOIN clan_members cm ON c.id = cm.clan_id LEFT JOIN users u ON cm.user_id = u.id WHERE cm.user_id = ? GROUP BY c.id, c.name, c.owner_id";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Clan(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getLong("owner_id"),
                            rs.getLong("total_points")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения клана пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        return null;
    }

    /**
     * Получение клана по ID.
     */
    public Clan getClanById(int clanId) throws SQLException {
        String sql = "SELECT c.id, c.name, c.owner_id, COALESCE(SUM(u.points), 0) as total_points FROM clans c LEFT JOIN clan_members cm ON c.id = cm.clan_id LEFT JOIN users u ON cm.user_id = u.id WHERE c.id = ? GROUP BY c.id, c.name, c.owner_id";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, clanId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Clan(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getLong("owner_id"),
                            rs.getLong("total_points")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения клана по ID {}: {}", clanId, e.getMessage(), e);
            throw e;
        }
        return null;
    }

    /**
     * Получение клана по имени.
     */
    public Clan getClanByName(String name) throws SQLException {
        String sql = "SELECT c.id, c.name, c.owner_id, COALESCE(SUM(u.points), 0) as total_points FROM clans c LEFT JOIN clan_members cm ON c.id = cm.clan_id LEFT JOIN users u ON cm.user_id = u.id WHERE c.name = ? GROUP BY c.id, c.name, c.owner_id";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Clan(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getLong("owner_id"),
                            rs.getLong("total_points")
                    );
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения клана по имени {}: {}", name, e.getMessage(), e);
            throw e;
        }
        return null;
    }

    /**
     * Получение списка участников клана.
     */
    public List<User> getClanMembers(int clanId) throws SQLException {
        List<User> members = new ArrayList<>();
        String sql = "SELECT u.* FROM users u JOIN clan_members cm ON u.id = cm.user_id WHERE cm.clan_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, clanId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int favoriteCardId = rs.getInt("favorite_card_id");
                    members.add(new User(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getLong("points"),
                            rs.wasNull() ? null : favoriteCardId,
                            rs.getString("title")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения участников клана {}: {}", clanId, e.getMessage(), e);
            throw e;
        }
        return members;
    }

    /**
     * Присоединение пользователя к клану.
     */
    public void joinClan(long userId, int clanId) throws SQLException {
        connection.setAutoCommit(false);
        try {
            Clan clan = getClanById(clanId);
            if (clan == null) {
                throw new SQLException("Клан с ID " + clanId + " не найден");
            }

            String checkSql = "SELECT 1 FROM clan_members WHERE clan_id = ? AND user_id = ?";
            try (PreparedStatement checkPstmt = connection.prepareStatement(checkSql)) {
                checkPstmt.setInt(1, clanId);
                checkPstmt.setLong(2, userId);
                try (ResultSet rs = checkPstmt.executeQuery()) {
                    if (rs.next()) {
                        logger.info("Пользователь {} уже состоит в клане {}", userId, clanId);
                        return;
                    }
                }
            }

            Clan currentClan = getUserClan(userId);
            if (currentClan != null) {
                String sql = "DELETE FROM clan_members WHERE user_id = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setLong(1, userId);
                    pstmt.executeUpdate();
                    logger.info("Пользователь {} покинул клан {} перед вступлением в новый", userId, currentClan.getId());
                }
            }

            String sql = "INSERT INTO clan_members (clan_id, user_id) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, clanId);
                pstmt.setLong(2, userId);
                pstmt.executeUpdate();
            }

            connection.commit();
            logger.info("Пользователь {} присоединился к клану {}", userId, clanId);
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Ошибка присоединения пользователя {} к клану {}: {}", userId, clanId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Создание клана.
     */
    public int createClan(String name, long ownerId) throws SQLException {
        connection.setAutoCommit(false);
        try {
            String sql = "INSERT INTO clans (name, owner_id) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, name);
                pstmt.setLong(2, ownerId);
                int affectedRows = pstmt.executeUpdate();

                if (affectedRows == 0) {
                    throw new SQLException("Создание клана не удалось, нет затронутых строк.");
                }

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int clanId = rs.getInt(1);
                        logger.info("Создан клан: {} (ID: {})", name, clanId);
                        return clanId;
                    } else {
                        throw new SQLException("Не удалось получить ID созданного клана.");
                    }
                }
            }
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Ошибка создания клана {}: {}", name, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Покидание клана пользователем.
     */
    public void leaveClan(long id, long userId) throws SQLException {
        connection.setAutoCommit(false);
        try {
            Clan currentClan = getUserClan(userId);
            if (currentClan == null) {
                logger.info("Пользователь {} не состоит в клане", userId);
                return;
            }

            if (currentClan.getOwnerId() == userId) {
                String deleteClanSql = "DELETE FROM clans WHERE id = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(deleteClanSql)) {
                    pstmt.setInt(1, currentClan.getId());
                    pstmt.executeUpdate();
                    logger.info("Клан {} удалён, так как пользователь {} был его владельцем", currentClan.getId(), userId);
                }
            } else {
                String sql = "DELETE FROM clan_members WHERE user_id = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setLong(1, userId);
                    int affectedRows = pstmt.executeUpdate();
                    if (affectedRows > 0) {
                        logger.info("Пользователь {} покинул клан {}", userId, currentClan.getId());
                    }
                }
            }

            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Ошибка выхода пользователя {} из клана: {}", userId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Удаление клана.
     */
    public void deleteClan(int clanId) throws SQLException {
        connection.setAutoCommit(false);
        try {
            Clan clan = getClanById(clanId);
            if (clan == null) {
                throw new SQLException("Клан с ID " + clanId + " не найден");
            }

            String sql = "DELETE FROM clans WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, clanId);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Не удалось удалить клан: клан с ID " + clanId + " не найден");
                }
                logger.info("Клан {} удалён", clanId);
            }

            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Ошибка удаления клана {}: {}", clanId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Получение пользователей клана.
     */
    public List<User> getUserClanMembers(long userId) throws SQLException {
        Clan clan = getUserClan(userId);
        if (clan == null) {
            return new ArrayList<>();
        }
        return getClanMembers(clan.getId());
    }

    /**
     * Получение количества участников клана.
     */
    public int getClanMemberCount(int clanId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM clan_members WHERE clan_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, clanId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения количества участников клана {}: {}", clanId, e.getMessage(), e);
            throw e;
        }
        return 0;
    }

    /**
     * Получение состава пользователя.
     */
    public Map<String, Player> getUserSquad(long userId) throws SQLException {
        Map<String, Player> squad = new HashMap<>();
        String query = "SELECT position, player_id FROM user_squads WHERE user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String position = rs.getString("position");
                    int playerId = rs.getInt("player_id");
                    Player player = rs.wasNull() ? null : getPlayerById(playerId);
                    squad.put(position, player);
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения состава пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        Arrays.asList("GK", "CB1", "CB2", "CB3", "MID1", "MID2", "MID3", "FRW1", "FRW2", "FRW3", "EXTRA")
                .forEach(pos -> squad.putIfAbsent(pos, null));
        return squad;
    }

    /**
     * Сохранение игрока в составе пользователя.
     */
    public void saveUserSquad(long userId, String position, Player player) throws SQLException {
        String query = "INSERT INTO user_squads (user_id, position, player_id) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE player_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId);
            stmt.setString(2, position);
            if (player != null) {
                stmt.setInt(3, player.getId());
                stmt.setInt(4, player.getId());
            } else {
                stmt.setNull(3, Types.INTEGER);
                stmt.setNull(4, Types.INTEGER);
            }
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Сохранён игрок на позицию {} для пользователя {}", position, userId);
            }
        } catch (SQLException e) {
            logger.error("Ошибка сохранения состава пользователя {} на позицию {}: {}", userId, position, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Проверка, есть ли у пользователя определённый игрок.
     */
    public boolean checkUserHasPlayer(long userId, int playerId) throws SQLException {
        String query = "SELECT COUNT(*) FROM user_players WHERE user_id = ? AND player_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, playerId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }
    }

    /**
     * Получение списка всех доступных паков.
     */
    public List<Pack> getAvailablePacks() throws SQLException {
        List<Pack> packs = new ArrayList<>();
        String query = "SELECT * FROM packs WHERE price > 0";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Pack pack = new Pack();
                    pack.setId(rs.getInt("id"));
                    pack.setName(rs.getString("name"));
                    pack.setDescription(rs.getString("description"));
                    pack.setPrice(rs.getInt("price"));
                    pack.setPlayerCount(rs.getInt("player_count"));
                    pack.setCategory(rs.getString("category"));
                    pack.setDaily(rs.getBoolean("is_daily"));
                    pack.setCooldownHours(rs.getInt("cooldown_hours"));
                    packs.add(pack);
                    logger.info("Pack loaded: id={}, name={}, price={}, category={}",
                            pack.getId(), pack.getName(), pack.getPrice(), pack.getCategory());
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения доступных паков: {}", e.getMessage(), e);
            throw e;
        }
        if (packs.isEmpty()) {
            logger.warn("Список доступных паков пуст. Проверьте таблицу packs в базе данных.");
        }
        return packs;
    }

    /**
     * Получение пака по ID.
     */
    public Pack getPackById(int packId) throws SQLException {
        String query = "SELECT * FROM packs WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, packId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Pack pack = new Pack();
                    pack.setId(rs.getInt("id"));
                    pack.setName(rs.getString("name"));
                    pack.setDescription(rs.getString("description"));
                    pack.setPrice(rs.getInt("price"));
                    pack.setPlayerCount(rs.getInt("player_count"));
                    pack.setCategory(rs.getString("category"));
                    pack.setDaily(rs.getBoolean("is_daily"));
                    pack.setCooldownHours(rs.getInt("cooldown_hours"));
                    return pack;
                }
            }
        }
        return null;
    }

    /**
     * Покупка пака пользователем.
     */
    public void buyPack(long userId, int packId) throws SQLException {
        Pack pack = getPackById(packId);
        if (pack == null) {
            logger.error("Pack not found: packId={}", packId);
            throw new SQLException("Пак не найден: ID " + packId);
        }

        int price = pack.getPrice();
        if (price <= 0) {
            logger.error("Invalid pack price: packId={}, price={}", packId, price);
            throw new SQLException("Неверная цена пака: " + price);
        }

        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            logger.info("Starting transaction for buying pack: userId={}, packId={}, price={}", userId, packId, price);

            int userDollars = getUserDollars(userId);
            if (userDollars < price) {
                logger.warn("Insufficient dollars for userId={}: required={}, available={}", userId, price, userDollars);
                throw new SQLException("Недостаточно долларов для покупки пака.");
            }

            String updateDollarsQuery = "UPDATE users SET dollars = dollars - ? WHERE id = ? AND dollars >= ?";
            try (PreparedStatement stmt = connection.prepareStatement(updateDollarsQuery)) {
                stmt.setInt(1, price);
                stmt.setLong(2, userId);
                stmt.setInt(3, price);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    logger.warn("Failed to deduct dollars for userId={}: insufficient funds", userId);
                    throw new SQLException("Недостаточно долларов для покупки пака.");
                }
            }

            String insertPackQuery = "INSERT INTO user_packs (user_id, pack_id, quantity) VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE quantity = quantity + 1";
            try (PreparedStatement stmt = connection.prepareStatement(insertPackQuery)) {
                stmt.setLong(1, userId);
                stmt.setInt(2, packId);
                stmt.executeUpdate();
            }

            connection.commit();
            logger.info("Successfully bought pack: userId={}, packId={}", userId, packId);
        } catch (SQLException e) {
            logger.error("Error during pack purchase: userId={}, packId={}, error={}", userId, packId, e.getMessage(), e);
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                logger.error("Failed to rollback transaction: userId={}, packId={}, error={}", userId, packId, rollbackEx.getMessage(), rollbackEx);
            }
            throw e;
        } finally {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                logger.error("Failed to restore autocommit state: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Получение списка паков пользователя.
     */
    public List<Pack> getUserPacks(long userId) throws SQLException {
        List<Pack> packs = new ArrayList<>();
        String sql = "SELECT p.*, up.quantity FROM packs p JOIN user_packs up ON p.id = up.pack_id WHERE up.user_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Pack pack = new Pack(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getInt("price"),
                            rs.getInt("player_count"),
                            rs.getString("category"),
                            rs.getBoolean("is_daily"),
                            rs.getInt("cooldown_hours")
                    );
                    pack.setQuantity(rs.getInt("quantity"));
                    packs.add(pack);
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения паков пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        return packs;
    }

    /**
     * Добавление пака пользователю.
     */
    public void addPackToUser(long userId, int packId) throws SQLException {
        String sql = "INSERT INTO user_packs (user_id, pack_id, quantity) VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE quantity = quantity + 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, packId);
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Пак с ID {} добавлен пользователю {}", packId, userId);
            }
        } catch (SQLException e) {
            logger.error("Ошибка добавления пака пользователю {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Удаление пака у пользователя.
     */
    public void removePackFromUser(long userId, int packId) throws SQLException {
        String sql = "DELETE FROM user_packs WHERE user_id = ? AND pack_id = ? LIMIT 1";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setInt(2, packId);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Пак с ID {} удалён у пользователя {}", packId, userId);
            }
        } catch (SQLException e) {
            logger.error("Ошибка удаления пака у пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Проверка, есть ли у пользователя определённый пак.
     */
    public boolean checkUserHasPack(long userId, int packId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM user_packs WHERE user_id = ? AND pack_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, packId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка проверки наличия пака у пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        return false;
    }

    /**
     * Получение времени последнего подарка.
     */
    public Timestamp getLastGift(long userId) throws SQLException {
        String query = "SELECT last_gift FROM users WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("last_gift");
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения last_gift для пользователя {}: {}", userId, e.getMessage(), e);
            throw e;
        }
        return null;
    }

    /**
     * Получение количества паков у пользователя.
     */
    public int getUserPackQuantity(long userId, int packId) throws SQLException {
        String query = "SELECT quantity FROM user_packs WHERE user_id = ? AND pack_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, packId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("quantity");
            }
            return 0;
        } catch (SQLException e) {
            logger.error("Ошибка получения количества паков для userId={} и packId={}: {}", userId, packId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Уменьшает количество паков у пользователя на 1. Если количество становится 0, запись удаляется.
     *
     * @param userId ID пользователя
     * @param packId ID пака
     * @throws SQLException если пак не найден, количество уже 0 или произошла ошибка базы данных
     */
    public void decrementUserPackQuantity(long userId, int packId) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            logger.info("Starting transaction to decrement pack quantity: userId={}, packId={}", userId, packId);

            // Уменьшаем количество паков
            String updateQuery = "UPDATE user_packs SET quantity = quantity - 1 WHERE user_id = ? AND pack_id = ? AND quantity > 0";
            int rowsAffected;
            try (PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
                stmt.setLong(1, userId);
                stmt.setInt(2, packId);
                rowsAffected = stmt.executeUpdate();
            }

            if (rowsAffected == 0) {
                logger.warn("No pack found to decrement: userId={}, packId={}", userId, packId);
                throw new SQLException("Пак не найден или его количество уже равно 0 для userId=" + userId + ", packId=" + packId);
            }

            // Проверяем, стало ли количество паков равным 0, и удаляем запись, если это так
            String checkQuantityQuery = "SELECT quantity FROM user_packs WHERE user_id = ? AND pack_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(checkQuantityQuery)) {
                stmt.setLong(1, userId);
                stmt.setInt(2, packId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && rs.getInt("quantity") == 0) {
                        String deleteQuery = "DELETE FROM user_packs WHERE user_id = ? AND pack_id = ?";
                        try (PreparedStatement deleteStmt = connection.prepareStatement(deleteQuery)) {
                            deleteStmt.setLong(1, userId);
                            deleteStmt.setInt(2, packId);
                            deleteStmt.executeUpdate();
                            logger.info("Removed pack entry as quantity reached 0: userId={}, packId={}", userId, packId);
                        }
                    }
                }
            }

            // Фиксируем транзакцию
            connection.commit();
            logger.info("Successfully decremented pack quantity: userId={}, packId={}", userId, packId);
        } catch (SQLException e) {
            // Откатываем транзакцию в случае ошибки
            try {
                connection.rollback();
                logger.error("Rolled back transaction due to error: userId={}, packId={}, error={}", userId, packId, e.getMessage(), e);
            } catch (SQLException rollbackEx) {
                logger.error("Failed to rollback transaction: userId={}, packId={}, error={}", userId, packId, rollbackEx.getMessage(), rollbackEx);
            }
            throw e;
        } finally {
            // Восстанавливаем исходное состояние автокоммита
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                logger.error("Failed to restore autocommit state: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Вычитает указанное количество долларов из баланса пользователя.
     * @param userId ID пользователя
     * @param amount Количество долларов для вычитания
     * @throws SQLException Если недостаточно долларов или произошла ошибка базы данных
     */
    public void deductDollars(long userId, int amount) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            logger.info("Starting transaction to deduct dollars: userId={}, amount={}", userId, amount);

            // Проверяем текущий баланс
            String checkQuery = "SELECT dollars FROM users WHERE id = ? FOR UPDATE";
            int currentDollars;
            try (PreparedStatement stmt = connection.prepareStatement(checkQuery)) {
                stmt.setLong(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        logger.warn("User not found: userId={}", userId);
                        throw new SQLException("Пользователь с ID " + userId + " не найден");
                    }
                    currentDollars = rs.getInt("dollars");
                }
            }

            // Проверяем, достаточно ли долларов
            if (currentDollars < amount) {
                logger.warn("Insufficient dollars: userId={}, currentDollars={}, required={}", userId, currentDollars, amount);
                throw new SQLException("Недостаточно долларов для списания: текущий баланс " + currentDollars + ", требуется " + amount);
            }

            // Вычитаем доллары
            String updateQuery = "UPDATE users SET dollars = dollars - ? WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
                stmt.setInt(1, amount);
                stmt.setLong(2, userId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    logger.warn("Failed to deduct dollars: userId={}, amount={}", userId, amount);
                    throw new SQLException("Не удалось списать доллары для пользователя с ID " + userId);
                }
            }

            connection.commit();
            logger.info("Successfully deducted {} dollars from userId={}", amount, userId);
        } catch (SQLException e) {
            try {
                connection.rollback();
                logger.error("Rolled back transaction due to error: userId={}, amount={}, error={}", userId, amount, e.getMessage(), e);
            } catch (SQLException rollbackEx) {
                logger.error("Failed to rollback transaction: userId={}, amount={}, error={}", userId, amount, rollbackEx.getMessage(), rollbackEx);
            }
            throw e;
        } finally {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                logger.error("Failed to restore autocommit state: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Обновляет временную метку последнего получения подарочного пака для пользователя.
     * @param userId ID пользователя
     * @throws SQLException Если пользователь не найден или произошла ошибка базы данных
     */
    public void updateLastGift(long userId) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            logger.info("Starting transaction to update last gift timestamp: userId={}", userId);

            // Обновляем поле last_gift
            String updateQuery = "UPDATE users SET last_gift = ? WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
                stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                stmt.setLong(2, userId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    logger.warn("User not found for updating last gift: userId={}", userId);
                    throw new SQLException("Пользователь с ID " + userId + " не найден");
                }
            }

            connection.commit();
            logger.info("Successfully updated last gift timestamp for userId={}", userId);
        } catch (SQLException e) {
            try {
                connection.rollback();
                logger.error("Rolled back transaction due to error: userId={}, error={}", userId, e.getMessage(), e);
            } catch (SQLException rollbackEx) {
                logger.error("Failed to rollback transaction: userId={}, error={}", userId, rollbackEx.getMessage(), rollbackEx);
            }
            throw e;
        } finally {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                logger.error("Failed to restore autocommit state: {}", e.getMessage(), e);
            }
        }
    }
    // Методы дружбы
    public void sendFriendRequest(long senderId, String targetUsername) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            logger.info("Sending friend request: senderId={}, targetUsername={}", senderId, targetUsername);

            String findUserQuery = "SELECT id FROM users WHERE username = ?";
            long targetId;
            try (PreparedStatement stmt = connection.prepareStatement(findUserQuery)) {
                stmt.setString(1, targetUsername);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        logger.warn("Target user not found: username={}", targetUsername);
                        throw new SQLException("Пользователь с именем " + targetUsername + " не найден");
                    }
                    targetId = rs.getLong("id");
                }
            }

            if (senderId == targetId) {
                logger.warn("User attempted to add themselves as friend: userId={}", senderId);
                throw new SQLException("Нельзя добавить себя в друзья");
            }

            String checkQuery = "SELECT id FROM friends WHERE (user_id_1 = ? AND user_id_2 = ?) OR (user_id_1 = ? AND user_id_2 = ?)";
            try (PreparedStatement stmt = connection.prepareStatement(checkQuery)) {
                long minId = Math.min(senderId, targetId);
                long maxId = Math.max(senderId, targetId);
                stmt.setLong(1, minId);
                stmt.setLong(2, maxId);
                stmt.setLong(3, maxId);
                stmt.setLong(4, minId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        logger.warn("Friendship or request already exists: senderId={}, targetId={}", senderId, targetId);
                        throw new SQLException("Запрос на дружбу уже отправлен или вы уже друзья");
                    }
                }
            }

            String insertQuery = "INSERT INTO friends (user_id_1, user_id_2, status) VALUES (?, ?, 'pending')";
            try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
                stmt.setLong(1, Math.min(senderId, targetId));
                stmt.setLong(2, Math.max(senderId, targetId));
                stmt.executeUpdate();
            }

            connection.commit();
            logger.info("Friend request sent: senderId={}, targetId={}", senderId, targetId);
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Failed to send friend request: senderId={}, targetUsername={}, error={}", senderId, targetUsername, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    public void acceptFriendRequest(long userId, long friendId) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            logger.info("Accepting friend request: userId={}, friendId={}", userId, friendId);

            String updateQuery = "UPDATE friends SET status = 'accepted' WHERE (user_id_1 = ? AND user_id_2 = ?) OR (user_id_1 = ? AND user_id_2 = ?)";
            try (PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
                long minId = Math.min(userId, friendId);
                long maxId = Math.max(userId, friendId);
                stmt.setLong(1, minId);
                stmt.setLong(2, maxId);
                stmt.setLong(3, maxId);
                stmt.setLong(4, minId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    logger.warn("No pending request found: userId={}, friendId={}", userId, friendId);
                    throw new SQLException("Запрос на дружбу не найден");
                }
            }

            connection.commit();
            logger.info("Friend request accepted: userId={}, friendId={}", userId, friendId);
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Failed to accept friend request: userId={}, friendId={}, error={}", userId, friendId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    public void rejectFriendRequest(long userId, long friendId) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            logger.info("Rejecting friend request: userId={}, friendId={}", userId, friendId);

            String deleteQuery = "DELETE FROM friends WHERE (user_id_1 = ? AND user_id_2 = ?) OR (user_id_1 = ? AND user_id_2 = ?)";
            try (PreparedStatement stmt = connection.prepareStatement(deleteQuery)) {
                long minId = Math.min(userId, friendId);
                long maxId = Math.max(userId, friendId);
                stmt.setLong(1, minId);
                stmt.setLong(2, maxId);
                stmt.setLong(3, maxId);
                stmt.setLong(4, minId);
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected == 0) {
                    logger.warn("No pending request found: userId={}, friendId={}", userId, friendId);
                    throw new SQLException("Запрос на дружбу не найден");
                }
            }

            connection.commit();
            logger.info("Friend request rejected: userId={}, friendId={}", userId, friendId);
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Failed to reject friend request: userId={}, friendId={}, error={}", userId, friendId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    public List<Friend> getFriendsList(long userId, int page, int pageSize) throws SQLException {
        List<Friend> friends = new ArrayList<>();
        String query = "SELECT u.id, u.username, f.friendship_points " +
                "FROM friends f " +
                "JOIN users u ON (u.id = f.user_id_1 OR u.id = f.user_id_2) " +
                "WHERE f.status = 'accepted' AND (f.user_id_1 = ? OR f.user_id_2 = ?) AND u.id != ? " +
                "ORDER BY f.friendship_points DESC " +
                "LIMIT ? OFFSET ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            stmt.setLong(3, userId);
            stmt.setInt(4, pageSize);
            stmt.setInt(5, page * pageSize);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    friends.add(new Friend(rs.getLong("id"), rs.getString("username"), rs.getInt("friendship_points")));
                }
            }
        }
        return friends;
    }

    public int getFriendsCount(long userId) throws SQLException {
        String query = "SELECT COUNT(*) FROM friends WHERE status = 'accepted' AND (user_id_1 = ? OR user_id_2 = ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    public List<Friend> getTopFriendsByPoints(long userId, int page, int pageSize) throws SQLException {
        return getFriendsList(userId, page, pageSize);
    }

    // Методы подарков
    public boolean canGiftPlayer(long userId, int playerId) throws SQLException {
        String query = "SELECT gift_lock_until, pc.name " +
                "FROM user_players up " +
                "JOIN players p ON up.player_id = p.id " +
                "JOIN player_categories pc ON p.category_id = pc.id " +
                "WHERE up.user_id = ? AND up.player_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    logger.warn("Player not found in user's collection: userId={}, playerId={}", userId, playerId);
                    return false;
                }
                String category = rs.getString("name");
                if (!List.of("Gold", "Diamond", "Legend", "Goat", "TOTY").contains(category)) {
                    logger.warn("Invalid category for gift: userId={}, playerId={}, category={}", userId, playerId, category);
                    return false;
                }
                Timestamp lockUntil = rs.getTimestamp("gift_lock_until");
                return lockUntil == null || lockUntil.before(new Timestamp(System.currentTimeMillis()));
            }
        }
    }

    public void giftPlayer(long fromUserId, long toUserId, int playerId) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            logger.info("Processing gift: fromUserId={}, toUserId={}, playerId={}", fromUserId, toUserId, playerId);

            if (!canGiftPlayer(fromUserId, playerId)) {
                throw new SQLException("Карточка недоступна для подарка");
            }

            String categoryQuery = "SELECT pc.name FROM players p JOIN player_categories pc ON p.category_id = pc.id WHERE p.id = ?";
            String category;
            try (PreparedStatement stmt = connection.prepareStatement(categoryQuery)) {
                stmt.setInt(1, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Игрок не найден");
                    }
                    category = rs.getString("name");
                }
            }

            String deleteQuery = "DELETE FROM user_players WHERE user_id = ? AND player_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(deleteQuery)) {
                stmt.setLong(1, fromUserId);
                stmt.setInt(2, playerId);
                if (stmt.executeUpdate() == 0) {
                    throw new SQLException("Карточка не найдена в коллекции отправителя");
                }
            }

            String insertQuery = "INSERT INTO user_players (user_id, player_id, gift_lock_until) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
                stmt.setLong(1, toUserId);
                stmt.setInt(2, playerId);
                stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000));
                stmt.executeUpdate();
            }

            String historyQuery = "INSERT INTO gift_history (player_id, from_user_id, to_user_id) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(historyQuery)) {
                stmt.setInt(1, playerId);
                stmt.setLong(2, fromUserId);
                stmt.setLong(3, toUserId);
                stmt.executeUpdate();
            }

            int points = switch (category) {
                case "Gold" -> 500;
                case "Diamond" -> 750;
                case "Legend" -> 1000;
                case "Goat" -> 1500;
                case "TOTY" -> 2000;
                default -> 0;
            };
            if (points > 0) {
                String pointsQuery = "UPDATE friends SET friendship_points = friendship_points + ? " +
                        "WHERE status = 'accepted' AND ((user_id_1 = ? AND user_id_2 = ?) OR (user_id_1 = ? AND user_id_2 = ?))";
                try (PreparedStatement stmt = connection.prepareStatement(pointsQuery)) {
                    long minId = Math.min(fromUserId, toUserId);
                    long maxId = Math.max(fromUserId, toUserId);
                    stmt.setInt(1, points);
                    stmt.setLong(2, minId);
                    stmt.setLong(3, maxId);
                    stmt.setLong(4, maxId);
                    stmt.setLong(5, minId);
                    stmt.executeUpdate();
                }
            }

            connection.commit();
            logger.info("Gift processed: fromUserId={}, toUserId={}, playerId={}, points={}", fromUserId, toUserId, playerId, points);
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Failed to process gift: fromUserId={}, toUserId={}, playerId={}, error={}", fromUserId, toUserId, playerId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    // Методы рынка
    public void createTrade(long userId, int playerId, int price) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            logger.info("Creating trade: userId={}, playerId={}, price={}", userId, playerId, price);

            String checkQuery = "SELECT gift_lock_until FROM user_players WHERE user_id = ? AND player_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(checkQuery)) {
                stmt.setLong(1, userId);
                stmt.setInt(2, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        logger.warn("Player not found in user's collection: userId={}, playerId={}", userId, playerId);
                        throw new SQLException("Карточка не найдена в вашей коллекции");
                    }
                    Timestamp lockUntil = rs.getTimestamp("gift_lock_until");
                    if (lockUntil != null && lockUntil.after(new Timestamp(System.currentTimeMillis()))) {
                        logger.warn("Player is locked for trade: userId={}, playerId={}", userId, playerId);
                        throw new SQLException("Карточка заблокирована для трейда");
                    }
                }
            }

            String marketCheckQuery = "SELECT id FROM market WHERE player_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(marketCheckQuery)) {
                stmt.setInt(1, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        logger.warn("Player already on market: playerId={}", playerId);
                        throw new SQLException("Карточка уже выставлена на рынок");
                    }
                }
            }

            String deleteQuery = "DELETE FROM user_players WHERE user_id = ? AND player_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(deleteQuery)) {
                stmt.setLong(1, userId);
                stmt.setInt(2, playerId);
                stmt.executeUpdate();
            }

            String insertQuery = "INSERT INTO market (user_id, player_id, price, created_at) VALUES (?, ?, ?, NOW())";
            try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
                stmt.setLong(1, userId);
                stmt.setInt(2, playerId);
                stmt.setInt(3, price);
                stmt.executeUpdate();
            }

            connection.commit();
            logger.info("Trade created: userId={}, playerId={}, price={}", userId, playerId, price);
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Failed to create trade: userId={}, playerId={}, price={}, error={}", userId, playerId, price, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    public void buyFromMarket(long buyerId, long marketId) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            logger.info("Processing market purchase: buyerId={}, marketId={}", buyerId, marketId);

            String marketQuery = "SELECT user_id, player_id, price FROM market WHERE id = ? FOR UPDATE";
            long sellerId;
            int playerId;
            int price;
            try (PreparedStatement stmt = connection.prepareStatement(marketQuery)) {
                stmt.setLong(1, marketId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        logger.warn("Market item not found: marketId={}", marketId);
                        throw new SQLException("Карточка не найдена на рынке");
                    }
                    sellerId = rs.getLong("user_id");
                    playerId = rs.getInt("player_id");
                    price = rs.getInt("price");
                }
            }

            if (buyerId == sellerId) {
                logger.warn("User attempted to buy own item: buyerId={}, marketId={}", buyerId, marketId);
                throw new SQLException("Нельзя купить свою собственную карточку");
            }

            String balanceQuery = "SELECT dollars FROM users WHERE id = ? FOR UPDATE";
            int buyerDollars;
            try (PreparedStatement stmt = connection.prepareStatement(balanceQuery)) {
                stmt.setLong(1, buyerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Покупатель не найден");
                    }
                    buyerDollars = rs.getInt("dollars");
                }
            }
            if (buyerDollars < price) {
                logger.warn("Insufficient dollars: buyerId={}, dollars={}, required={}", buyerId, buyerDollars, price);
                throw new SQLException("Недостаточно долларов для покупки");
            }

            String deductQuery = "UPDATE users SET dollars = dollars - ? WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(deductQuery)) {
                stmt.setInt(1, price);
                stmt.setLong(2, buyerId);
                stmt.executeUpdate();
            }

            String creditQuery = "UPDATE users SET dollars = dollars + ? WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(creditQuery)) {
                stmt.setInt(1, price);
                stmt.setLong(2, sellerId);
                stmt.executeUpdate();
            }

            String insertPlayerQuery = "INSERT INTO user_players (user_id, player_id) VALUES (?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(insertPlayerQuery)) {
                stmt.setLong(1, buyerId);
                stmt.setInt(2, playerId);
                stmt.executeUpdate();
            }

            String deleteMarketQuery = "DELETE FROM market WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(deleteMarketQuery)) {
                stmt.setLong(1, marketId);
                stmt.executeUpdate();
            }

            connection.commit();
            logger.info("Market purchase completed: buyerId={}, marketId={}, playerId={}, price={}", buyerId, marketId, playerId, price);
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Failed to process market purchase: buyerId={}, marketId={}, error={}", buyerId, marketId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }


    public void removePlayerFromMarket(long marketId) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            logger.info("Removing player from market: marketId={}", marketId);

            String marketQuery = "SELECT user_id, player_id FROM market WHERE id = ?";
            long userId;
            int playerId;
            try (PreparedStatement stmt = connection.prepareStatement(marketQuery)) {
                stmt.setLong(1, marketId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        logger.warn("Market item not found: marketId={}", marketId);
                        throw new SQLException("Карточка не найдена на рынке");
                    }
                    userId = rs.getLong("user_id");
                    playerId = rs.getInt("player_id");
                }
            }

            String insertQuery = "INSERT INTO user_players (user_id, player_id) VALUES (?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
                stmt.setLong(1, userId);
                stmt.setInt(2, playerId);
                stmt.executeUpdate();
            }

            String deleteQuery = "DELETE FROM market WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(deleteQuery)) {
                stmt.setLong(1, marketId);
                stmt.executeUpdate();
            }

            connection.commit();
            logger.info("Player removed from market: marketId={}, userId={}, playerId={}", marketId, userId, playerId);
        } catch (SQLException e) {
            connection.rollback();
            logger.error("Failed to remove player from market: marketId={}, error={}", marketId, e.getMessage(), e);
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    public List<MarketEntry> getUserMarketSales(long userId) throws SQLException {
        String sql = "SELECT m.id, m.user_id, m.player_id, m.price, u.username " +
                "FROM market m JOIN users u ON m.user_id = u.id WHERE m.user_id = ?";
        List<MarketEntry> entries = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    entries.add(new MarketEntry(
                            (int) rs.getLong("id"),
                            rs.getLong("user_id"),
                            rs.getString("username"),
                            rs.getInt("player_id"),
                            rs.getInt("price")
                    ));
                }
            }
        }
        return entries;
    }

    public MarketEntry getMarketEntry(long marketId) throws SQLException {
        String sql = "SELECT m.id, m.user_id, m.player_id, m.price, u.username " +
                "FROM market m JOIN users u ON m.user_id = u.id WHERE m.id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, marketId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new MarketEntry(
                            (int) rs.getLong("id"),
                            rs.getLong("user_id"),
                            rs.getString("username"),
                            rs.getInt("player_id"),
                            rs.getInt("price")
                    );
                }
            }
        }
        return null;
    }

    public boolean isPlayerOnMarket(int playerId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM market WHERE player_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public List<User> getUserFriends(long userId, int page, int pageSize) throws SQLException {
        List<User> friends = new ArrayList<>();
        String query = "SELECT u.id, u.username, u.points, u.favorite_card_id, u.title " +
                "FROM friends f " +
                "JOIN users u ON u.id = CASE WHEN f.user_id_1 = ? THEN f.user_id_2 ELSE f.user_id_1 END " +
                "WHERE (f.user_id_1 = ? OR f.user_id_2 = ?) AND f.status = 'accepted' " +
                "LIMIT ? OFFSET ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            stmt.setLong(3, userId);
            stmt.setInt(4, pageSize);
            stmt.setInt(5, (page - 1) * pageSize);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                User friend = new User(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getLong("points"),
                        rs.getInt("favorite_card_id") != 0 ? rs.getInt("favorite_card_id") : null,
                        rs.getString("title")
                );
                friends.add(friend);
            }
        }
        return friends;
    }

    /**
     * Проверяет, являются ли два пользователя друзьями.
     * @param userId1 ID первого пользователя
     * @param userId2 ID второго пользователя
     * @return true, если друзья
     */
    public boolean areFriends(long userId1, long userId2) throws SQLException {
        String query = "SELECT 1 FROM friends WHERE status = 'accepted' AND " +
                "((user_id_1 = ? AND user_id_2 = ?) OR (user_id_1 = ? AND user_id_2 = ?))";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId1);
            stmt.setLong(2, userId2);
            stmt.setLong(3, userId2);
            stmt.setLong(4, userId1);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    /**
     * Отправляет запрос на дружбу.
     * @param userId1 ID отправителя
     * @param userId2 ID получателя
     */
    public void addFriend(long userId1, long userId2) throws SQLException {
        if (userId1 == userId2) {
            throw new SQLException("Нельзя добавить себя в друзья");
        }
        // Гарантируем, что user_id_1 < user_id_2
        long smallerId = Math.min(userId1, userId2);
        long largerId = Math.max(userId1, userId2);

        // Проверяем, не существует ли уже запрос или дружба
        String checkQuery = "SELECT COUNT(*) FROM friends WHERE user_id_1 = ? AND user_id_2 = ?";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkQuery)) {
            checkStmt.setLong(1, smallerId);
            checkStmt.setLong(2, largerId);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                throw new SQLException("Запрос на дружбу уже отправлен или дружба существует");
            }
        }

        String query = "INSERT INTO friends (user_id_1, user_id_2, status) VALUES (?, ?, 'pending')";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, smallerId);
            stmt.setLong(2, largerId);
            stmt.executeUpdate();
        }
    }
    /**
     * Принимает запрос на дружбу.
     * @param userId1 ID одного пользователя
     * @param userId2 ID другого пользователя
     */
    public void acceptFriend(long userId1, long userId2) throws SQLException {
        // Гарантируем, что user_id_1 < user_id_2 для соответствия ограничению
        long smallerId = Math.min(userId1, userId2);
        long largerId = Math.max(userId1, userId2);

        String query = "UPDATE friends SET status = 'accepted', last_points_update = CURRENT_TIMESTAMP " +
                "WHERE user_id_1 = ? AND user_id_2 = ? AND status = 'pending'";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, smallerId);
            stmt.setLong(2, largerId);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Запрос на дружбу не найден или уже принят");
            }
        }
    }

    /**
     * Удаляет дружбу между пользователями.
     * @param userId1 ID одного пользователя
     * @param userId2 ID другого пользователя
     */
    public void removeFriend(long userId1, long userId2) throws SQLException {
        String query = "DELETE FROM friends WHERE " +
                "(user_id_1 = ? AND user_id_2 = ?) OR (user_id_1 = ? AND user_id_2 = ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId1);
            stmt.setLong(2, userId2);
            stmt.setLong(3, userId2);
            stmt.setLong(4, userId1);
            stmt.executeUpdate();
        }
    }

    /**
     * Начисляет 100 очков дружбы за день, если они ещё не начислены.
     * @param userId ID пользователя
     */
    public void addDailyFriendshipPoints(long userId) throws SQLException {
        String query = "UPDATE friends SET friendship_points = friendship_points + 100, " +
                "last_points_update = CURRENT_TIMESTAMP " +
                "WHERE (user_id_1 = ? OR user_id_2 = ?) AND status = 'accepted' " +
                "AND DATE(last_points_update) < CURDATE()";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, userId);
            stmt.executeUpdate();
        }
    }

    /**
     * Начисляет очки дружбы за подарок.
     * @param userId1 ID дарителя
     * @param userId2 ID получателя
     * @param category Категория карточки
     */
    public void addGiftFriendshipPoints(long userId1, long userId2, String category) throws SQLException {
        int points = switch (category) {
            case "Gold" -> 500;
            case "Diamond" -> 750;
            case "Legend" -> 1000;
            case "GOAT" -> 1250;
            case "TOTY" -> 1500;
            default -> throw new SQLException("Недопустимая категория карточки");
        };
        String query = "UPDATE friends SET friendship_points = friendship_points + ?, " +
                "last_points_update = CURRENT_TIMESTAMP " +
                "WHERE ((user_id_1 = ? AND user_id_2 = ?) OR (user_id_1 = ? AND user_id_2 = ?)) " +
                "AND status = 'accepted'";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, points);
            stmt.setLong(2, userId1);
            stmt.setLong(3, userId2);
            stmt.setLong(4, userId2);
            stmt.setLong(5, userId1);
            stmt.executeUpdate();
        }
    }

    // --- Методы для глобального рынка ---

    /**
     * Получает список игроков на рынке с пагинацией.
     * @param page Номер страницы
     * @param pageSize Количество записей на странице
     * @return Список игроков на рынке
     */
    public List<MarketEntry> getMarketPlayers(int page, int pageSize) throws SQLException {
        List<MarketEntry> marketEntries = new ArrayList<>();
        String query = "SELECT m.id, m.user_id, u.username, m.player_id, m.price " +
                "FROM market m " +
                "JOIN users u ON u.id = m.user_id " +
                "LIMIT ? OFFSET ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, pageSize);
            stmt.setInt(2, (page - 1) * pageSize);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                MarketEntry entry = new MarketEntry(
                        (int) rs.getLong("id"), // Приведение long к int
                        rs.getLong("user_id"),
                        rs.getString("username"),
                        rs.getInt("player_id"),
                        rs.getInt("price")
                );
                marketEntries.add(entry);
            }
        }
        return marketEntries;
    }

    /**
     * Добавляет игрока на рынок.
     * @param userId ID пользователя
     * @param playerId ID игрока
     * @param price Цена
     * @throws SQLException если превышен лимит продаж (10) или категория неподходящая
     */
    public void addPlayerToMarket(long userId, int playerId, int price) throws SQLException {
        // Проверка лимита продаж
        String countQuery = "SELECT COUNT(*) FROM market WHERE user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(countQuery)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt(1) >= 10) {
                throw new SQLException("Превышен лимит продаж (10 игроков)");
            }
        }

        // Проверка категории игрока
        String categoryQuery = "SELECT pc.name FROM players p JOIN player_categories pc ON pc.id = p.category_id WHERE p.id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(categoryQuery)) {
            stmt.setInt(1, playerId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next() || !List.of("Gold", "Diamond", "Legend", "GOAT", "TOTY").contains(rs.getString("name"))) {
                throw new SQLException("Игрок не подходит для продажи (требуется Gold или выше)");
            }
        }

        // Проверка владения игроком
        String ownershipQuery = "SELECT 1 FROM user_players WHERE user_id = ? AND player_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(ownershipQuery)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, playerId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                throw new SQLException("Игрок не находится в вашем инвентаре");
            }
        }

        // Добавление на рынок
        String insertQuery = "INSERT INTO market (user_id, player_id, price) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, playerId);
            stmt.setInt(3, price);
            stmt.executeUpdate();
        }

        // Удаление игрока из инвентаря
        String deleteQuery = "DELETE FROM user_players WHERE user_id = ? AND player_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(deleteQuery)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, playerId);
            stmt.executeUpdate();
        }
    }

    /**
     * Покупает игрока с рынка.
     * @param buyerId ID покупателя
     * @param marketId ID записи на рынке
     */
    public void buyPlayerFromMarket(long buyerId, long marketId) throws SQLException {
        // Получение данных о продаже
        String selectQuery = "SELECT m.user_id, m.player_id, m.price " +
                "FROM market m WHERE m.id = ?";
        long sellerId;
        int playerId, price;
        try (PreparedStatement stmt = connection.prepareStatement(selectQuery)) {
            stmt.setLong(1, marketId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                throw new SQLException("Игрок не найден на рынке");
            }
            sellerId = rs.getLong("user_id");
            playerId = rs.getInt("player_id");
            price = rs.getInt("price");
        }

        // Проверка баланса покупателя
        String balanceQuery = "SELECT dollars FROM users WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(balanceQuery)) {
            stmt.setLong(1, buyerId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next() || rs.getInt("dollars") < price) {
                throw new SQLException("Недостаточно долларов");
            }
        }

        // Обновление баланса покупателя
        String updateBuyerQuery = "UPDATE users SET dollars = dollars - ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(updateBuyerQuery)) {
            stmt.setInt(1, price);
            stmt.setLong(2, buyerId);
            stmt.executeUpdate();
        }

        // Обновление баланса продавца
        String updateSellerQuery = "UPDATE users SET dollars = dollars + ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(updateSellerQuery)) {
            stmt.setInt(1, price);
            stmt.setLong(2, sellerId);
            stmt.executeUpdate();
        }

        // Добавление игрока покупателю
        String insertPlayerQuery = "INSERT INTO user_players (user_id, player_id, gift_lock_until) VALUES (?, ?, NULL)";
        try (PreparedStatement stmt = connection.prepareStatement(insertPlayerQuery)) {
            stmt.setLong(1, buyerId);
            stmt.setInt(2, playerId);
            stmt.executeUpdate();
        }

        // Удаление с рынка
        String deleteQuery = "DELETE FROM market WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(deleteQuery)) {
            stmt.setLong(1, marketId);
            stmt.executeUpdate();
        }
    }

    // --- Методы для состава (Squad) ---

    /**
     * Проверяет, есть ли игрок в составе пользователя.
     * @param userId ID пользователя
     * @param playerId ID игрока
     * @return true, если игрок в составе
     */
    public boolean isPlayerInSquad(long userId, int playerId) throws SQLException {
        String query = "SELECT 1 FROM user_squads WHERE user_id = ? AND player_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, playerId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    /**
     * Обновляет позицию игрока в составе.
     * @param userId ID пользователя
     * @param position Позиция (например, "ST")
     * @param playerId ID игрока
     */
    public void updateSquadPosition(long userId, String position, int playerId) throws SQLException {
        String query = "INSERT INTO user_squads (user_id, player_id, position) " +
                "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE position = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, playerId);
            stmt.setString(3, position);
            stmt.setString(4, position);
            stmt.executeUpdate();
        }
    }

    /**
     * Отправляет подарок (передача карточки другу).
     * @param senderId ID отправителя
     * @param receiverId ID получателя
     * @param playerId ID игрока
     */
    public void sendGift(long senderId, long receiverId, int playerId) throws SQLException {
        // Проверка дружбы
        if (!areFriends(senderId, receiverId)) {
            throw new SQLException("Пользователи не являются друзьями");
        }

        // Проверка категории игрока
        String categoryQuery = "SELECT pc.name FROM players p JOIN player_categories pc ON pc.id = p.category_id WHERE p.id = ?";
        String category;
        try (PreparedStatement stmt = connection.prepareStatement(categoryQuery)) {
            stmt.setInt(1, playerId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next() || !List.of("Gold", "Diamond", "Legend", "GOAT", "TOTY").contains(rs.getString("name"))) {
                throw new SQLException("Подарок возможен только для карточек Gold и выше");
            }
            category = rs.getString("name");
        }

        // Проверка владения игроком
        String ownershipQuery = "SELECT gift_lock_until FROM user_players WHERE user_id = ? AND player_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(ownershipQuery)) {
            stmt.setLong(1, senderId);
            stmt.setInt(2, playerId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                throw new SQLException("Игрок не находится в вашем инвентаре");
            }
            Timestamp lockUntil = rs.getTimestamp("gift_lock_until");
            if (lockUntil != null && lockUntil.after(new Timestamp(System.currentTimeMillis()))) {
                throw new SQLException("Карточка заблокирована для подарка");
            }
        }

        // Удаление игрока у отправителя
        String deleteQuery = "DELETE FROM user_players WHERE user_id = ? AND player_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(deleteQuery)) {
            stmt.setLong(1, senderId);
            stmt.setInt(2, playerId);
            stmt.executeUpdate();
        }

        // Добавление игрока получателю с блокировкой на 3 дня
        String insertQuery = "INSERT INTO user_players (user_id, player_id, gift_lock_until) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
            stmt.setLong(1, receiverId);
            stmt.setInt(2, playerId);
            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000)); // 3 дня
            stmt.executeUpdate();
        }

        // Начисление очков дружбы
        addGiftFriendshipPoints(senderId, receiverId, category);
    }

    public boolean hasPendingFriendRequest(long userId1, long userId2) throws SQLException {
        long smallerId = Math.min(userId1, userId2);
        long largerId = Math.max(userId1, userId2);
        String query = "SELECT COUNT(*) FROM friends WHERE user_id_1 = ? AND user_id_2 = ? AND status = 'pending'";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, Math.min(userId2, userId1));
            stmt.setLong(2, Math.max(userId2, userId1));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }
    }

    public boolean hasFriendshipOrRequest(long userId1, long userId2) throws SQLException {
        long smallerId = Math.min(userId1, userId2);
        long largerId = Math.max(userId1, userId2);
        String query = "SELECT COUNT(*) FROM friends WHERE user_id_1 = ? AND user_id_2 = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, smallerId);
            stmt.setLong(2, largerId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }
    }



}
