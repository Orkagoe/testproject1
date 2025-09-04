package ru.apache_maven.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.apache_maven.db.DatabaseManager;
import ru.apache_maven.model.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Squad {
    private static final Logger logger = LoggerFactory.getLogger(Squad.class);
    private final Map<String, Player> players = new HashMap<>();
    private final DatabaseManager db;
    private final long userId;
    private String name;

    private static final List<String> POSITIONS = Arrays.asList(
            "GK", "CB1", "CB2", "CB3", "MID1", "MID2", "MID3", "FRW1", "FRW2", "FRW3", "EXTRA"
    );

    // Константы для сыгранности
    private static final double FULL_CHEMISTRY_BONUS = 1.75; // 75% бонус при 11 игроках из одной команды
    private static final double PARTIAL_CHEMISTRY_BONUS = 1.25; // 25% бонус при 6-10 игроках из одной команды
    private static final int MIN_PLAYERS_FOR_CHEMISTRY = 6; // Минимальное количество игроков для частичного бонуса

    public Squad(DatabaseManager db, long userId) throws SQLException {
        this.db = db;
        this.userId = userId;
        loadSquadFromDatabase();
        validateSquad();
    }

    private void loadSquadFromDatabase() throws SQLException {
        Map<String, Player> savedSquad = db.getUserSquad(userId);
        for (String position : POSITIONS) {
            players.put(position, savedSquad.getOrDefault(position, null));
        }
    }

    private void validateSquad() throws SQLException {
        for (String position : POSITIONS) {
            Player player = players.get(position);
            if (player == null) continue;

            String playerPosition = player.getPosition();
            boolean isValid = true;

            if (position.equals("GK") && !playerPosition.equals("GK")) {
                isValid = false;
            } else if (position.startsWith("CB") && !playerPosition.equals("CB")) {
                isValid = false;
            } else if (position.startsWith("MID") && !playerPosition.equals("MID")) {
                isValid = false;
            } else if (position.startsWith("FRW") && !playerPosition.equals("FRW")) {
                isValid = false;
            } else if (position.equals("EXTRA") && !Arrays.asList("CB", "MID", "FRW").contains(playerPosition)) {
                isValid = false;
            }

            if (!isValid) {
                logger.info("Removing player {} from position {}: invalid position {}", player.getName(), position, playerPosition);
                players.put(position, null);
                db.saveUserSquad(userId, position, null);
            }
        }
    }

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

    public void setName(String name) {
        this.name = name;
    }

    public Player getPlayer(String position) {
        return players.get(position);
    }

    public List<Player> getAllPlayers() {
        return new ArrayList<>(players.values());
    }

    public int calculateRating() {
        return (int) (getAllPlayers().stream()
                .filter(Objects::nonNull)
                .mapToInt(Player::getRating)
                .sum() * calculateChemistryBonus());
    }

    public static List<String> getPositions() {
        return POSITIONS;
    }

    public void setPlayer(String position, Player player) {
        if (!POSITIONS.contains(position)) {
            logger.warn("Invalid position: {}", position);
            return;
        }

        if (player != null) {
            String playerPosition = player.getPosition();
            if (position.equals("GK") && !playerPosition.equals("GK")) {
                logger.warn("Player {} cannot be set as GK: position is {}", player.getName(), playerPosition);
                return;
            }
            if (position.startsWith("CB") && !playerPosition.equals("CB")) {
                logger.warn("Player {} cannot be set as CB: position is {}", player.getName(), playerPosition);
                return;
            }
            if (position.startsWith("MID") && !playerPosition.equals("MID")) {
                logger.warn("Player {} cannot be set as MID: position is {}", player.getName(), playerPosition);
                return;
            }
            if (position.startsWith("FRW") && !playerPosition.equals("FRW")) {
                logger.warn("Player {} cannot be set as FRW: position is {}", player.getName(), playerPosition);
                return;
            }
            if (position.equals("EXTRA") && !Arrays.asList("CB", "MID", "FRW").contains(playerPosition)) {
                logger.warn("Player {} cannot be set as EXTRA: position is {}", player.getName(), playerPosition);
                return;
            }
        }

        players.put(position, player);
        try {
            db.saveUserSquad(userId, position, player);
        } catch (SQLException e) {
            logger.error("Failed to save squad: {}", e.getMessage());
        }
    }

    public double calculateTotalAttack() {
        double totalAttack = 0.0;
        for (String pos : Arrays.asList("MID1", "MID2", "MID3")) {
            Player p = players.get(pos);
            if (p != null) {
                totalAttack += p.getRating() * 0.05;
            }
        }
        for (String pos : Arrays.asList("FRW1", "FRW2", "FRW3")) {
            Player p = players.get(pos);
            if (p != null) {
                totalAttack += p.getRating() * (1.0 / 15.0);
            }
        }
        Player extra = players.get("EXTRA");
        if (extra != null) {
            if (extra.getPosition().equals("MID")) {
                totalAttack += extra.getRating() * 0.05;
            } else if (extra.getPosition().equals("FRW")) {
                totalAttack += extra.getRating() * 0.1;
            }
        }
        return totalAttack * calculateChemistryBonus();
    }

    public double calculateTotalDefense() {
        double totalDefense = 0.0;
        Player gk = players.get("GK");
        if (gk != null) {
            totalDefense += gk.getRating() * 0.1;
        }
        for (String pos : Arrays.asList("CB1", "CB2", "CB3")) {
            Player p = players.get(pos);
            if (p != null) {
                totalDefense += p.getRating() * (1.0 / 15.0);
            }
        }
        for (String pos : Arrays.asList("MID1", "MID2", "MID3")) {
            Player p = players.get(pos);
            if (p != null) {
                totalDefense += p.getRating() * 0.05;
            }
        }
        Player extra = players.get("EXTRA");
        if (extra != null) {
            if (extra.getPosition().equals("CB")) {
                totalDefense += extra.getRating() * 0.1;
            } else if (extra.getPosition().equals("MID")) {
                totalDefense += extra.getRating() * 0.05;
            }
        }
        return totalDefense * calculateChemistryBonus();
    }

    private double calculateChemistryBonus() {
        Map<Integer, Integer> teamCounts = new HashMap<>();
        int playerCount = 0;

        for (String position : POSITIONS) {
            Player player = players.get(position);
            if (player != null) {
                playerCount++;
                teamCounts.merge(player.getTeamId(), 1, Integer::sum);
            }
        }

        for (int count : teamCounts.values()) {
            if (count == 11) {
                logger.info("Full chemistry bonus applied for userId={}: +75%", userId);
                return FULL_CHEMISTRY_BONUS;
            }
            if (count >= MIN_PLAYERS_FOR_CHEMISTRY) {
                logger.info("Partial chemistry bonus applied for userId={}: +25%", userId);
                return PARTIAL_CHEMISTRY_BONUS;
            }
        }

        logger.info("No chemistry bonus for userId={}", userId);
        return 1.0;
    }

    public String getChemistryInfo() {
        Map<Integer, Integer> teamCounts = new HashMap<>();
        int playerCount = 0;

        for (String position : POSITIONS) {
            Player player = players.get(position);
            if (player != null) {
                playerCount++;
                teamCounts.merge(player.getTeamId(), 1, Integer::sum);
            }
        }

        for (int count : teamCounts.values()) {
            if (count == 11) {
                return "🔥 Полная сыгранность (+75%)";
            }
            if (count >= MIN_PLAYERS_FOR_CHEMISTRY) {
                return "⚡ Частичная сыгранность (+25%)";
            }
        }
        return "🛠 Нет сыгранности";
    }
}