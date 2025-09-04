package ru.apache_maven.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.apache_maven.db.DatabaseManager;
import ru.apache_maven.model.Player;
import ru.apache_maven.bot.Squad;
import ru.apache_maven.model.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class FootballPvP {
    private static final Logger logger = LoggerFactory.getLogger(FootballPvP.class);
    private final TelegramBot bot;
    private final DatabaseManager db;
    private final Map<Long, FootballPvPMatch> activeMatches = new ConcurrentHashMap<>();
    private final Map<Long, Integer> challengeMessageIds = new ConcurrentHashMap<>();
    private final Map<Long, Integer> matchMessageIds = new ConcurrentHashMap<>();

    private static final String CALLBACK_PVP_ACCEPT = "pvp_accept_";
    private static final String CALLBACK_PVP_DECLINE = "pvp_decline_";
    private static final String CALLBACK_PVP_ROUND = "pvp_round_";
    private static final String CALLBACK_PVP_AI = "pvp_ai_"; // Добавлено для ИИ
    private static final long CHALLENGE_TIMEOUT_MS = 60_000; // 1 минута

    public FootballPvP(TelegramBot bot, DatabaseManager db) {
        this.bot = bot;
        this.db = db;
    }

    // Твой метод initiatePvP — без изменений
    public void initiatePvP(long chatId, long challengerId, long opponentId) {
        logger.info("Инициируется PvP: chatId={}, challengerId={}, opponentId={}", chatId, challengerId, opponentId);

        synchronized (getLock(chatId)) {
            if (activeMatches.containsKey(chatId)) {
                logger.warn("PvP-матч уже идёт в чате chatId={}", chatId);
                Utils.sendMessage(bot, chatId, "В этом чате уже идёт PvP-матч!", null);
                return;
            }

            if (challengerId == opponentId) {
                logger.warn("Игрок и оппонент совпадают: userId={}", challengerId);
                Utils.sendMessage(bot, chatId, "Нельзя бросить вызов самому себе!", null);
                return;
            }

            try {
                User challenger = db.getUserById(challengerId);
                User opponent = db.getUserById(opponentId);
                if (challenger == null || opponent == null) {
                    logger.error("Пользователь не найден: challengerId={}, opponentId={}", challengerId, opponentId);
                    Utils.sendMessage(bot, chatId, "Один из пользователей не найден.", null);
                    return;
                }

                FootballPvPMatch match = new FootballPvPMatch(challengerId, opponentId, challenger.getUsername(), opponent.getUsername(), chatId, 0, 0, 1, false);
                activeMatches.put(chatId, match);

                String text = String.format("*%s*, вы вызваны на PvP-матч пользователем *%s*! Принять? ⏳ (60 сек)", opponent.getUsername(), challenger.getUsername());
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<InlineKeyboardButton> row = new ArrayList<>();

                InlineKeyboardButton acceptButton = new InlineKeyboardButton();
                acceptButton.setText("✅ Принять");
                acceptButton.setCallbackData(CALLBACK_PVP_ACCEPT + challengerId + "_" + opponentId);

                InlineKeyboardButton declineButton = new InlineKeyboardButton();
                declineButton.setText("❌ Отказать");
                declineButton.setCallbackData(CALLBACK_PVP_DECLINE + challengerId + "_" + opponentId);

                row.add(acceptButton);
                row.add(declineButton);
                keyboard.setKeyboard(List.of(row));

                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(String.valueOf(chatId));
                sendMessage.setText(text);
                sendMessage.setReplyMarkup(keyboard);
                sendMessage.enableMarkdown(true);

                logger.info("Отправляется сообщение с вызовом PvP в чат chatId={}", chatId);
                Message sentMessage = bot.execute(sendMessage);
                challengeMessageIds.put(chatId, sentMessage.getMessageId());
                logger.info("Сообщение с вызовом PvP отправлено, challengeMessageId={}", sentMessage.getMessageId());

                new Thread(() -> {
                    try {
                        Thread.sleep(CHALLENGE_TIMEOUT_MS);
                        synchronized (getLock(chatId)) {
                            FootballPvPMatch currentMatch = activeMatches.get(chatId);
                            if (currentMatch != null && !currentMatch.isStarted.get()) {
                                logger.info("Таймер истёк, матч не начат, отменяется для chatId={}", chatId);
                                Utils.sendMessage(bot, chatId, "Вызов на PvP отклонён из-за таймаута.", null);
                                cancelMatch(chatId);
                            } else {
                                logger.info("Матч уже начат или завершён для chatId={}, отмена не требуется", chatId);
                            }
                        }
                    } catch (InterruptedException e) {
                        logger.error("Таймер вызова прерван для chatId={}: {}", chatId, e.getMessage(), e);
                    }
                }).start();
            } catch (SQLException e) {
                logger.error("Ошибка SQL при инициации PvP: {}", e.getMessage(), e);
                Utils.sendMessage(bot, chatId, "Ошибка при запуске PvP (база данных).", null);
                cancelMatch(chatId);
            } catch (Exception e) {
                logger.error("Ошибка при отправке сообщения вызова PvP: {}", e.getMessage(), e);
                Utils.sendMessage(bot, chatId, "Ошибка при запуске PvP.", null);
                cancelMatch(chatId);
            }
        }
    }

    // Новый метод для PvP с ИИ
    public void initiatePvPWithAI(long chatId, long userId, String difficulty) {
        logger.info("Инициируется PvP с ИИ: chatId={}, userId={}, difficulty={}", chatId, userId, difficulty);

        synchronized (getLock(chatId)) {
            if (activeMatches.containsKey(chatId)) {
                logger.warn("PvP-матч уже идёт в чате chatId={}", chatId);
                Utils.sendMessage(bot, chatId, "В этом чате уже идёт PvP-матч!", null);
                return;
            }

            try {
                User user = db.getUserById(userId);
                if (user == null) {
                    logger.error("Пользователь не найден: userId={}", userId);
                    Utils.sendMessage(bot, chatId, "Пользователь не найден.", null);
                    return;
                }

                // Загружаем состав ИИ из базы данных
                Map<String, Player> aiSquadMap = db.getRandomAISquad(difficulty);
                if (aiSquadMap == null || aiSquadMap.isEmpty()) {
                    logger.error("Не удалось загрузить состав ИИ для сложности {}", difficulty);
                    Utils.sendMessage(bot, chatId, "Не удалось сформировать состав ИИ.", null);
                    return;
                }

                Squad aiSquad = new Squad(db, -1); // ID -1 для ИИ
                aiSquad.setPlayer("GK", aiSquadMap.get("GK"));
                aiSquad.setPlayer("CB1", aiSquadMap.get("CB1"));
                aiSquad.setPlayer("CB2", aiSquadMap.get("CB2"));
                aiSquad.setPlayer("CB3", aiSquadMap.get("CB3"));
                aiSquad.setPlayer("MID1", aiSquadMap.get("MID1"));
                aiSquad.setPlayer("MID2", aiSquadMap.get("MID2"));
                aiSquad.setPlayer("MID3", aiSquadMap.get("MID3"));
                aiSquad.setPlayer("FRW1", aiSquadMap.get("FRW1"));
                aiSquad.setPlayer("FRW2", aiSquadMap.get("FRW2"));
                aiSquad.setPlayer("FRW3", aiSquadMap.get("FRW3"));
                aiSquad.setPlayer("EXTRA", aiSquadMap.get("EXTRA"));

                FootballPvPMatch match = new FootballPvPMatch(userId, -1, user.getUsername(), "ИИ (" + difficulty + ")", chatId, 0, 0, 1, true);
                match.aiSquad = aiSquad;
                activeMatches.put(chatId, match);

                startMatchWithAI(chatId); // Сразу начинаем матч с ИИ
            } catch (SQLException e) {
                logger.error("Ошибка SQL при инициации PvP с ИИ: {}", e.getMessage(), e);
                Utils.sendMessage(bot, chatId, "Ошибка базы данных при запуске PvP с ИИ.", null);
            } catch (Exception e) {
                logger.error("Ошибка при запуске PvP с ИИ: {}", e.getMessage(), e);
                Utils.sendMessage(bot, chatId, "Ошибка при запуске PvP с ИИ.", null);
            }
        }
    }

    // Твой метод startMatch — без изменений
    public void startMatch(long chatId) {
        synchronized (getLock(chatId)) {
            FootballPvPMatch match = activeMatches.get(chatId);
            if (match == null || !challengeMessageIds.containsKey(chatId)) {
                logger.error("Матч или сообщение вызова не найдены для chatId={}", chatId);
                Utils.sendMessage(bot, chatId, "Матч не найден или вызов не инициализирован!", null);
                return;
            }

            try {
                match.isStarted.set(true);
                Squad challengerSquad = bot.getUserSquad(match.challengerId);
                Squad opponentSquad = bot.getUserSquad(match.opponentId);
                String text = String.format(
                        "⚽ *Матч между %s и %s*\n" +
                                "📊 %s: рейтинг %d, %s\n" +
                                "📊 %s: рейтинг %d, %s\n" +
                                "🔢 Счёт: 0 : 0\n" +
                                "⏱ Раунд 1 начинается! 🚀",
                        match.challengerUsername, match.opponentUsername,
                        match.challengerUsername, challengerSquad.calculateRating(), challengerSquad.getChemistryInfo(),
                        match.opponentUsername, opponentSquad.calculateRating(), opponentSquad.getChemistryInfo()
                );
                InlineKeyboardMarkup keyboard = createRoundButton(1, match.challengerId, match.opponentId);

                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(String.valueOf(chatId));
                sendMessage.setText(text);
                sendMessage.setReplyMarkup(keyboard);
                sendMessage.enableMarkdown(true);

                logger.info("Запускается PvP-матч, отправляется начальное сообщение в chatId={}", chatId);
                Message sentMessage = bot.execute(sendMessage);
                matchMessageIds.put(chatId, sentMessage.getMessageId());
                logger.info("PvP-матч начат, matchMessageId={}", sentMessage.getMessageId());
            } catch (Exception e) {
                logger.error("Ошибка при запуске PvP-матча для chatId={}: {}", chatId, e.getMessage(), e);
                Utils.sendMessage(bot, chatId, "Ошибка при запуске матча.", null);
                cancelMatch(chatId);
            }
        }
    }

    // Новый метод для старта матча с ИИ
    private void startMatchWithAI(long chatId) {
        synchronized (getLock(chatId)) {
            FootballPvPMatch match = activeMatches.get(chatId);
            if (match == null) {
                logger.error("Матч не найден для chatId={}", chatId);
                Utils.sendMessage(bot, chatId, "Матч не найден!", null);
                return;
            }

            try {
                match.isStarted.set(true);
                Squad userSquad = bot.getUserSquad(match.challengerId);
                Squad aiSquad = match.aiSquad;

                String text = String.format(
                        "⚽ *Матч между %s и %s*\n" +
                                "📊 %s: рейтинг %d, %s\n" +
                                "📊 %s: рейтинг %d\n" +
                                "🔢 Счёт: 0 : 0\n" +
                                "⏱ Раунд 1 начинается! 🚀",
                        match.challengerUsername, match.opponentUsername,
                        match.challengerUsername, userSquad.calculateRating(), userSquad.getChemistryInfo(),
                        match.opponentUsername, aiSquad.calculateRating()
                );
                InlineKeyboardMarkup keyboard = createRoundButton(1, match.challengerId, -1); // -1 для ИИ

                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(String.valueOf(chatId));
                sendMessage.setText(text);
                sendMessage.setReplyMarkup(keyboard);
                sendMessage.enableMarkdown(true);

                logger.info("Запускается PvP-матч с ИИ в chatId={}", chatId);
                Message sentMessage = bot.execute(sendMessage);
                matchMessageIds.put(chatId, sentMessage.getMessageId());
                logger.info("PvP-матч с ИИ начат, matchMessageId={}", sentMessage.getMessageId());
            } catch (Exception e) {
                logger.error("Ошибка при запуске PvP-матча с ИИ для chatId={}: {}", chatId, e.getMessage(), e);
                Utils.sendMessage(bot, chatId, "Ошибка при запуске матча с ИИ.", null);
                cancelMatch(chatId);
            }
        }
    }

    // Твой метод simulateRound, адаптированный для ИИ
    public void simulateRound(long chatId, int round, long userId) {
        synchronized (getLock(chatId)) {
            FootballPvPMatch match = activeMatches.get(chatId);
            Integer matchMessageId = matchMessageIds.get(chatId);
            if (match == null || matchMessageId == null) {
                logger.error("Матч или matchMessageId не найдены для chatId={}", chatId);
                Utils.sendMessage(bot, chatId, "Матч не найден или уже завершён!", null);
                return;
            }

            if (!match.isAI && (userId != match.challengerId && userId != match.opponentId)) {
                logger.warn("Пользователь {} пытался взаимодействовать с матчем, не предназначенным для него", userId);
                return;
            }

            if (match.isProcessing.get()) {
                logger.info("Раунд {} для chatId={} уже обрабатывается, игнорируем повторный callback", round, chatId);
                return;
            }

            match.isProcessing.set(true);
            try {
                Squad squadA = bot.getUserSquad(match.challengerId);
                Squad squadB = match.isAI ? match.aiSquad : bot.getUserSquad(match.opponentId);

                double attackA = squadA.calculateTotalAttack();
                double defenseA = squadA.calculateTotalDefense();
                double attackB = squadB.calculateTotalAttack();
                double defenseB = squadB.calculateTotalDefense();

                StringBuilder matchText = new StringBuilder(String.format(
                        "⚽ *Матч между %s и %s*\n" +
                                "🔢 Счёт: *%d : %d*\n" +
                                "%s\n%s\n",
                        match.challengerUsername, match.opponentUsername,
                        match.goalsA, match.goalsB,
                        squadA.getChemistryInfo(), match.isAI ? "ИИ не имеет химии" : squadB.getChemistryInfo()
                ));

                matchText.append("⏱ Раунд ").append(round).append(":\n");

                double chanceA = (attackA / (attackA + defenseB)) * 100;
                boolean goalA = Math.random() * 100 < chanceA && match.goalsA < 10;
                if (goalA) {
                    match.goalsA++;
                    matchText.append("⚽ ").append(match.challengerUsername).append(" забивает гол!\n");
                } else {
                    matchText.append("🧤 ").append(match.opponentUsername).append(" блокирует удар!\n");
                }

                double chanceB = (attackB / (attackB + defenseA)) * 100;
                boolean goalB = Math.random() * 100 < chanceB && match.goalsB < 10;
                if (goalB) {
                    match.goalsB++;
                    matchText.append("⚽ ").append(match.opponentUsername).append(" забивает гол!\n");
                } else {
                    matchText.append("🧤 ").append(match.challengerUsername).append(" блокирует удар!\n");
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("Задержка прервана для chatId={}: {}", chatId, e.getMessage(), e);
                    Thread.currentThread().interrupt();
                    Utils.sendMessage(bot, chatId, "Матч прерван.", null);
                    cancelMatch(chatId);
                    return;
                }

                InlineKeyboardMarkup keyboard;
                if (round >= 5 || match.goalsA >= 10 || match.goalsB >= 10) {
                    matchText.append("\n🏆 Матч окончен!\n");
                    if (match.goalsA > match.goalsB) {
                        matchText.append(match.challengerUsername).append(" победил! 🎉");
                        try {
                            db.addPoints(match.challengerId, 100);
                            db.addDollars(match.challengerId, 50);
                            matchText.append("\n🏅 Награда: 100 очков, 50 долларов");
                        } catch (SQLException e) {
                            logger.error("Не удалось наградить победителя userId={}: {}", match.challengerId, e.getMessage(), e);
                        }
                    } else if (match.goalsB > match.goalsA) {
                        matchText.append(match.opponentUsername).append(" победил! 🎉");
                        if (!match.isAI) {
                            try {
                                db.addPoints(match.opponentId, 100);
                                db.addDollars(match.opponentId, 50);
                                matchText.append("\n🏅 Награда: 100 очков, 50 долларов");
                            } catch (SQLException e) {
                                logger.error("Не удалось наградить победителя userId={}: {}", match.opponentId, e.getMessage(), e);
                            }
                        }
                    } else {
                        matchText.append("Ничья! 🤝");
                        try {
                            db.addPoints(match.challengerId, 50);
                            if (!match.isAI) db.addPoints(match.opponentId, 50);
                            matchText.append("\n🏅 Награда: по 50 очков каждому");
                        } catch (SQLException e) {
                            logger.error("Не удалось наградить за ничью для chatId={}: {}", chatId, e.getMessage(), e);
                        }
                    }
                    keyboard = null;
                    cancelMatch(chatId);
                    logger.info("PvP-матч завершён для chatId={}", chatId);
                } else {
                    matchText.append("\nНажмите для следующего раунда:");
                    keyboard = createRoundButton(round + 1, match.challengerId, match.isAI ? -1 : match.opponentId);
                }

                try {
                    logger.info("Обновляется сообщение матча для раунда {} в chatId={}, messageId={}", round, chatId, matchMessageId);
                    Utils.editMessage(bot, chatId, matchMessageId, matchText.toString(), keyboard);
                } catch (Exception e) {
                    logger.error("Ошибка обновления сообщения матча для chatId={} с messageId={}: {}", chatId, matchMessageId, e.getMessage(), e);
                    Utils.sendMessage(bot, chatId, "Ошибка при обновлении матча.", null);
                }
            } finally {
                match.isProcessing.set(false);
            }
        }
    }

    // Твой метод createRoundButton, адаптированный для ИИ
    private InlineKeyboardMarkup createRoundButton(int round, long challengerId, long opponentId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton roundButton = new InlineKeyboardButton();
        roundButton.setText("Раунд " + round);
        roundButton.setCallbackData(CALLBACK_PVP_ROUND + round + "_" + challengerId + "_" + opponentId);
        row.add(roundButton);
        keyboard.setKeyboard(List.of(row));
        return keyboard;
    }

    // Твой метод showSquad — без изменений
    public void showSquad(long chatId, long targetUserId) {
        try {
            User targetUser = db.getUserById(targetUserId);
            if (targetUser == null) {
                logger.warn("Пользователь не найден для targetUserId={}", targetUserId);
                Utils.sendMessage(bot, chatId, "Пользователь не найден.", null);
                return;
            }
            Squad squad = bot.getUserSquad(targetUserId);
            StringBuilder text = new StringBuilder("⚽ *Состав " + targetUser.getUsername() + "*:\n\n");
            text.append("1. GK: ").append(formatPlayer(squad.getPlayer("GK"))).append("\n");
            text.append("2. CB: ").append(formatPlayer(squad.getPlayer("CB1"))).append("\n");
            text.append("3. CB: ").append(formatPlayer(squad.getPlayer("CB2"))).append("\n");
            text.append("4. CB: ").append(formatPlayer(squad.getPlayer("CB3"))).append("\n");
            text.append("5. MID: ").append(formatPlayer(squad.getPlayer("MID1"))).append("\n");
            text.append("6. MID: ").append(formatPlayer(squad.getPlayer("MID2"))).append("\n");
            text.append("7. MID: ").append(formatPlayer(squad.getPlayer("MID3"))).append("\n");
            text.append("8. FRW: ").append(formatPlayer(squad.getPlayer("FRW1"))).append("\n");
            text.append("9. FRW: ").append(formatPlayer(squad.getPlayer("FRW2"))).append("\n");
            text.append("10. FRW: ").append(formatPlayer(squad.getPlayer("FRW3"))).append("\n");
            text.append("11. EXTRA: ").append(formatPlayer(squad.getPlayer("EXTRA"))).append("\n");
            text.append("\n📊 *Итоговый рейтинг*: ").append(squad.calculateRating());
            text.append("\n").append(squad.getChemistryInfo());
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(String.valueOf(chatId));
            sendMessage.setText(text.toString());
            sendMessage.enableMarkdown(true);
            bot.execute(sendMessage);
        } catch (SQLException e) {
            logger.error("Ошибка SQL при отображении состава для userId {}: {}", targetUserId, e.getMessage(), e);
            Utils.sendMessage(bot, chatId, "Ошибка при получении состава.", null);
        } catch (Exception e) {
            logger.error("Ошибка при отправке сообщения состава для userId {}: {}", targetUserId, e.getMessage(), e);
            Utils.sendMessage(bot, chatId, "Ошибка при отображении состава.", null);
        }
    }

    // Твой метод formatPlayer — без изменений
    private String formatPlayer(Player player) {
        return player != null ? player.getName() + " " + player.getCategory().getEmoji() + " " + player.getRating() : "Пусто";
    }

    // Твой метод cancelMatch — без изменений
    public void cancelMatch(long chatId) {
        synchronized (getLock(chatId)) {
            logger.info("Отмена PvP-матча для chatId={}", chatId);
            activeMatches.remove(chatId);
            challengeMessageIds.remove(chatId);
            matchMessageIds.remove(chatId);
        }
    }

    // Твой метод getCurrentMatch — без изменений
    public FootballPvPMatch getCurrentMatch(long chatId) {
        synchronized (getLock(chatId)) {
            return activeMatches.get(chatId);
        }
    }

    // Твой метод getLock — без изменений
    private Object getLock(long chatId) {
        return ("lock_" + chatId).intern();
    }

    // Класс FootballPvPMatch, дополненный для ИИ
    public static class FootballPvPMatch {
        long challengerId;
        long opponentId;
        String challengerUsername;
        String opponentUsername;
        long chatId;
        int goalsA;
        int goalsB;
        int currentRound;
        AtomicBoolean isStarted = new AtomicBoolean(false);
        AtomicBoolean isProcessing = new AtomicBoolean(false);
        boolean isAI; // Флаг для матчей с ИИ
        Squad aiSquad; // Состав ИИ

        FootballPvPMatch(long challengerId, long opponentId, String challengerUsername, String opponentUsername, long chatId, int goalsA, int goalsB, int currentRound, boolean isAI) {
            this.challengerId = challengerId;
            this.opponentId = opponentId;
            this.challengerUsername = challengerUsername;
            this.opponentUsername = opponentUsername;
            this.chatId = chatId;
            this.goalsA = goalsA;
            this.goalsB = goalsB;
            this.currentRound = currentRound;
            this.isAI = isAI;
        }
    }
}