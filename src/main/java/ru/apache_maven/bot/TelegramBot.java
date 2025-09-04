package ru.apache_maven.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import ru.apache_maven.db.DatabaseManager;
import ru.apache_maven.model.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TelegramBot extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBot.class);
    private static final long SPIN_COOLDOWN = 24 * 60 * 60 * 1000; // 24 часа в миллисекундах

    private final DatabaseManager db;
    private final String botUsername;
    private final String botToken;
    private PenaltyGame currentGame = null;
    private final CommandHandler commandHandler;
    private final CallbackHandler callbackHandler;
    private final Map<Long, Squad> userSquads = new HashMap<>();
    private final FootballPvP footballPvP;

    public TelegramBot(String botToken, String botUsername, DatabaseManager db) {
        super(botToken);
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.db = db;
        this.commandHandler = new CommandHandler(db, this);
        this.callbackHandler = new CallbackHandler(db, this);
        this.footballPvP = new FootballPvP(this, db);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            long userId = update.getMessage().getFrom().getId();
            String username = update.getMessage().getFrom().getFirstName();
            String text = update.getMessage().getText();

            // Проверка на ожидающую продажу
            PendingSale pendingSale = getPendingSale(userId);
            if (pendingSale != null && !text.startsWith(".")) { // Игнорируем команды
                try {
                    int price = Integer.parseInt(text);
                    if (price < 0 || price > 1000000) {
                        Utils.sendMessage(this, chatId, "Сумма должна быть от 0 до 1000000.", null);
                        return;
                    }
                    setPendingSalePrice(userId, price);
                    Utils.sendMessage(this, chatId, "Сумма " + price + " $ сохранена. Нажмите 'Подтвердить'.", null);
                    return;
                } catch (NumberFormatException e) {
                    Utils.sendMessage(this, chatId, "Пожалуйста, введите корректное число.", null);
                    return;
                }
            }

            // Существующая логика обработки команд
            try {
                if (!db.userExists(userId)) {
                    db.addUser(userId, username);
                }

                String command = text.split(" ")[0];
                if (command.contains("@")) {
                    command = command.substring(0, command.indexOf("@"));
                }

                // Примеры команд, которые у тебя уже есть
                if (text.equalsIgnoreCase(".футпвп") && update.getMessage().getReplyToMessage() != null) {
                    long opponentId = update.getMessage().getReplyToMessage().getFrom().getId();
                    footballPvP.initiatePvP(chatId, userId, opponentId);
                } else {
                    commandHandler.handleCommand(update, command, chatId, userId, text);
                }
            } catch (SQLException e) {
                Utils.sendMessage(this, chatId, "Ошибка при обработке команды.", null);
            }
        } else if (update.hasCallbackQuery()) {
            try {
                callbackHandler.handleCallback(update.getCallbackQuery());
            } catch (SQLException e) {
                logger.error("Ошибка обработки callback: {}", e.getMessage(), e);
            }
        }
    }

    public Squad getUserSquad(long userId) {
        return userSquads.computeIfAbsent(userId, id -> {
            try {
                Map<String, Player> squadData = db.getUserSquad(id);
                Squad squad = new Squad(db, id);
                squadData.forEach((pos, player) -> {
                    if (Squad.getPositions().contains(pos)) {
                        squad.setPlayer(pos, player);
                    }
                });
                return squad;
            } catch (SQLException e) {
                logger.error("Failed to load squad: {}", e.getMessage());
                try {
                    return new Squad(db, id);
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public PenaltyGame getCurrentGame() {
        return currentGame;
    }

    public void setCurrentGame(PenaltyGame game) {
        this.currentGame = game;
    }

    public FootballPvP getFootballPvP() {
        return footballPvP;
    }

    static class PenaltyGame {
        long challengerId;
        long opponentId;
        String challengerUsername;
        String opponentUsername;
        long chatId;
        List<Boolean> challengerGoals;
        List<Boolean> opponentGoals;
        int currentRound;
        int challengerKicks;
        int opponentKicks;
        String kickDirection;
        int kicker;

        public PenaltyGame(long challengerId, long opponentId, String challengerUsername, String opponentUsername, long chatId) {
            this.challengerId = challengerId;
            this.opponentId = opponentId;
            this.challengerUsername = challengerUsername;
            this.opponentUsername = opponentUsername;
            this.chatId = chatId;
            this.challengerGoals = new ArrayList<>();
            this.opponentGoals = new ArrayList<>();
            this.currentRound = 1;
            this.challengerKicks = 0;
            this.opponentKicks = 0;
            this.kickDirection = null;
            this.kicker = 1;
        }

        public int getChallengerScore() {
            return (int) challengerGoals.stream().filter(goal -> goal).count();
        }

        public int getOpponentScore() {
            return (int) opponentGoals.stream().filter(goal -> goal).count();
        }

        // Геттеры
        public long getChallengerId() { return challengerId; }
        public long getOpponentId() { return opponentId; }
        public String getChallengerUsername() { return challengerUsername; }
        public String getOpponentUsername() { return opponentUsername; }
        public long getChatId() { return chatId; }
        public int getCurrentRound() { return currentRound; }
        public String getKickDirection() { return kickDirection; }
        public int getKicker() { return kicker; }
        public int getChallengerKicks() { return challengerKicks; }
        public int getOpponentKicks() { return opponentKicks; }

        // Сеттеры
        public void setCurrentRound(int round) { this.currentRound = round; }
        public void setKickDirection(String direction) { this.kickDirection = direction; }
        public void setKicker(int kicker) { this.kicker = kicker; }
        public void incrementChallengerKicks() { this.challengerKicks++; }
        public void incrementOpponentKicks() { this.opponentKicks++; }
        public void addChallengerGoal(boolean scored) { this.challengerGoals.add(scored); }
        public void addOpponentGoal(boolean scored) { this.opponentGoals.add(scored); }
    }

    public static long getSpinCooldown() {
        return SPIN_COOLDOWN;
    }

    public DatabaseManager getDb() {
        return db;
    }

    private final Map<Long, PendingSale> pendingSales = new HashMap<>();

    public void addPendingSale(long userId, int playerId) {
        pendingSales.put(userId, new PendingSale(playerId));
    }

    public PendingSale getPendingSale(long userId) {
        return pendingSales.get(userId);
    }

    public void removePendingSale(long userId) {
        pendingSales.remove(userId);
    }

    public void setPendingSalePrice(long userId, int price) {
        PendingSale sale = pendingSales.get(userId);
        if (sale != null) {
            sale.price = price;
        }
    }

    public static class PendingSale {
        int playerId;
        Integer price; // null, пока сумма не введена

        public PendingSale(int playerId) {
            this.playerId = playerId;
            this.price = null;
        }
    }

}