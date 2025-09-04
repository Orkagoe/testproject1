package ru.apache_maven.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.apache_maven.db.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PlayerCategory {
    // Добавляем поле logger
    private static final Logger logger = LoggerFactory.getLogger(PlayerCategory.class);

    private int id;
    private String name;
    private int weight;
    private int points;
    private int dollars; // Поле для хранения долларов

    public PlayerCategory() {}

    public PlayerCategory(int id, String name, int weight, int points, int dollars) {
        this.id = id;
        this.name = name;
        this.weight = weight;
        this.points = points;
        this.dollars = dollars;
    }

    // Геттеры
    public int getId() { return id; }
    public String getName() { return name; }
    public int getWeight() { return weight; }
    public int getPoints() { return points; }

    // Сеттеры
    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setWeight(int weight) { this.weight = weight; }
    public void setPoints(int points) { this.points = points; }

    // Метод для получения эмодзи категории
    public String getEmoji() {
        if (name == null) {
            return "";
        }
        switch (name.toLowerCase()) {
            case "bronze":
                return "🥉";
            case "silver":
                return "🥈";
            case "gold":
                return "🥇";
            case "diamond":
                return "💎";
            case "season":
                return "🎖️";
            case "legend":
                return "🏆";
            case "goat":
                return "🐐";
            default:
                return "";
        }
    }

    @Override
    public String toString() {
        return "PlayerCategory{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", weight=" + weight +
                ", points=" + points +
                '}';
    }

    public int getDollars() {
        try (DatabaseManager db = new DatabaseManager()) {
            String sql = "SELECT dollars FROM player_categories WHERE id = ?";
            try (PreparedStatement pstmt = db.getConnection().prepareStatement(sql)) {
                pstmt.setInt(1, this.id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("dollars");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка получения долларов для категории с ID {}: {}", id, e.getMessage(), e);
        }
        return 0;
    }

    public int getDollarsFromDb() {
        return dollars;
    }

    public void setDollars(int dollars) {
        this.dollars = dollars;
    }
}