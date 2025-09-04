package ru.apache_maven.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.apache_maven.db.DatabaseManager;
import ru.apache_maven.model.*;

import java.io.File;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);
    private static final long SPIN_COOLDOWN = 2 * 60 * 60 * 1000; // 2 часа
    private static final long GIFT_COOLDOWN = 24 * 60 * 60 * 1000; // 24 часа

    // Отправка текстового сообщения
    public static void sendMessage(TelegramBot bot, long chatId, String text, InlineKeyboardMarkup keyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        if (keyboard != null) {
            message.setReplyMarkup(keyboard);
        }
        message.setParseMode("HTML");
        try {
            bot.execute(message);
        } catch (Exception e) {
            logger.error("Failed to send message to chatId {}: {}", chatId, e.getMessage(), e);
        }
    }

    // Перегруженный метод без клавиатуры
    public static void sendMessage(TelegramBot bot, long chatId, String text) {
        sendMessage(bot, chatId, text, null);
    }

    // Редактирование существующего сообщения
    public static void editMessage(TelegramBot bot, long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(String.valueOf(chatId));
        editMessage.setMessageId(messageId);
        editMessage.setText(text);
        if (keyboard != null) {
            editMessage.setReplyMarkup(keyboard);
        }
        editMessage.setParseMode("HTML");
        try {
            bot.execute(editMessage);
        } catch (TelegramApiException e) {
            logger.error("Failed to edit message with ID {} in chat {}: {}", messageId, chatId, e.getMessage(), e);
            sendMessage(bot, chatId, "Не удалось отредактировать сообщение. Пожалуйста, попробуйте снова.");
        }
    }

    // Отправка профиля пользователя
    public static void sendProfileMessage(TelegramBot bot, long userId, long chatId) {
        try (DatabaseManager db = new DatabaseManager()) {
            User user = db.getUserById(userId);
            if (user == null) {
                sendMessage(bot, chatId, "Пользователь не найден. Попробуйте зарегистрироваться с помощью команды /start.");
                return;
            }

            long points = db.getUserPoints(userId);
            int playerCount = db.getUserPlayers(userId).size();
            int dollars = db.getUserDollars(userId);

            StringBuilder profileText = new StringBuilder();
            profileText.append("<b>Профиль игрока:</b>\n\n")
                    .append("👤 <b>").append(user.getUsername()).append("</b>\n")
                    .append("🎯 Очки: ").append(points).append("\n")
                    .append("💵 Доллары: ").append(dollars).append("\n")
                    .append("👥 Игроков в коллекции: ").append(playerCount).append("\n");

            Clan clan = db.getUserClan(userId);
            if (clan != null) {
                profileText.append("🏰 Клан: ").append(clan.getName()).append("\n");
            }

            sendMessage(bot, chatId, profileText.toString());
        } catch (SQLException e) {
            logger.error("Ошибка при получении профиля пользователя {}: {}", userId, e.getMessage(), e);
            sendMessage(bot, chatId, "Произошла ошибка при получении профиля.");
        }
    }

    // Отправка карточки игрока с фото и информацией
    public static void sendPlayerCard(TelegramBot bot, DatabaseManager db, long chatId, Player player, int pointsAwarded) {
        try {
            String teamName = db.getTeamName(player.getTeamId());
            String leagueName = db.getLeagueName(db.getTeamLeagueId(player.getTeamId()));
            StringBuilder cardText = new StringBuilder();
            cardText.append("<b>Карточка игрока:</b>\n\n")
                    .append(player.getName()).append(" ").append(player.getCategory().getEmoji()).append("\n")
                    .append("Команда: ").append(teamName).append("\n")
                    .append("Лига: ").append(leagueName).append("\n")
                    .append("Позиция: ").append(player.getPosition()).append("\n")
                    .append("Рейтинг: ").append(player.getRating()).append("\n")
                    .append("Категория: ").append(player.getCategory().getName()).append("\n");

            if (pointsAwarded > 0) {
                cardText.append("Очки: +").append(pointsAwarded).append("\n");
            }

            SendPhoto photoMessage = new SendPhoto();
            photoMessage.setChatId(chatId);

            String photoPath = "resources/photos/" + player.getPhoto();
            logger.info("Попытка загрузить фото: {}. Полный путь в classpath: {}", player.getPhoto(), photoPath);
            java.net.URL photoUrl = Utils.class.getClassLoader().getResource(photoPath);
            if (photoUrl == null) {
                logger.error("Ресурс фото {} не найден в classpath. Ожидаемый путь: {}. Текущая рабочая директория: {}",
                        player.getPhoto(), photoPath, System.getProperty("user.dir"));
                sendMessage(bot, chatId, cardText.toString());
                return;
            }

            File photoFile;
            try {
                logger.info("URL ресурса: {}", photoUrl);
                photoFile = new File(photoUrl.toURI());
            } catch (Exception e) {
                logger.error("Ошибка преобразования URL ресурса в файл для {}: {}", photoPath, e.getMessage(), e);
                sendMessage(bot, chatId, cardText.toString());
                return;
            }

            logger.info("Путь к файлу: {}", photoFile.getAbsolutePath());
            if (!photoFile.exists()) {
                logger.error("Файл фото {} не найден по пути: {}", photoPath, photoFile.getAbsolutePath());
                sendMessage(bot, chatId, cardText.toString());
                return;
            }

            photoMessage.setPhoto(new InputFile(photoFile));
            photoMessage.setCaption(cardText.toString());
            photoMessage.setParseMode("HTML");

            try {
                bot.execute(photoMessage);
            } catch (Exception e) {
                logger.error("Ошибка отправки фото игрока {}: {}", player.getName(), e.getMessage(), e);
                sendMessage(bot, chatId, cardText.toString());
            }
        } catch (SQLException e) {
            logger.error("Ошибка при отправке карточки игрока: {}", e.getMessage(), e);
            sendMessage(bot, chatId, "Произошла ошибка при отправке карточки игрока.");
        }
    }

    // Отправка карточки игрока с проверкой кулдауна и начислением долларов
    public static void sendPlayerCardWithCooldown(TelegramBot bot, DatabaseManager db, long userId, long chatId) {
        try {
            int claims = db.checkAndResetGiftPackClaims(userId);
            if (claims >= 3) {
                sendMessage(bot, chatId, "Вы достигли лимита в 3 подарочных набора. Подождите 24 часа для сброса лимита.");
                return;
            }

            Timestamp lastSpin = db.getLastSpin(userId);
            long currentTime = System.currentTimeMillis();
            if (lastSpin != null && (currentTime - lastSpin.getTime()) < SPIN_COOLDOWN) {
                long remainingTime = SPIN_COOLDOWN - (currentTime - lastSpin.getTime());
                sendMessage(bot, chatId, "Следующий спин будет доступен через " + formatTime(remainingTime) + ".");
                return;
            }

            Player player = db.getRandomPlayer();
            if (player == null) {
                sendMessage(bot, chatId, "Не удалось найти карточку игрока.");
                return;
            }

            boolean isDuplicate = db.checkUserHasPlayer(userId, player.getId());
            db.addPlayerToUser(userId, player);
            db.addPoints(userId, player.getCategory().getPoints());

            int dollarsAwarded = 0;
            if (isDuplicate) {
                dollarsAwarded = player.getCategory().getDollars();
                db.addDollars(userId, dollarsAwarded);
            }

            db.updateLastSpin(userId);
            db.incrementGiftPackClaims(userId);

            String teamName = db.getTeamName(player.getTeamId());
            String leagueName = db.getLeagueName(db.getTeamLeagueId(player.getTeamId()));
            StringBuilder cardText = new StringBuilder();
            cardText.append("<b>⚽ Карточка игрока:</b>\n\n")
                    .append(player.getName()).append(" ").append(player.getCategory().getEmoji()).append("\n")
                    .append("\uD83C\uDFC6 Команда: ").append(teamName).append("\n")
                    .append("Лига: ").append(leagueName).append("\n")
                    .append("Позиция: ").append(player.getPosition()).append("\n")
                    .append("Рейтинг: ").append(player.getRating()).append("\n")
                    .append("Категория: ").append(player.getCategory().getName()).append("\n")
                    .append("Очки: +").append(player.getCategory().getPoints()).append("\n");

            if (isDuplicate) {
                cardText.append("\n<b>Реплика, карточка у вас уже есть, получите доллары: +").append(dollarsAwarded).append(" 💵</b>");
            }

            SendPhoto photoMessage = new SendPhoto();
            photoMessage.setChatId(chatId);
            photoMessage.setPhoto(new InputFile(new File(player.getPhoto())));
            photoMessage.setCaption(cardText.toString());
            photoMessage.setParseMode("HTML");

            try {
                bot.execute(photoMessage);
            } catch (Exception e) {
                logger.error("Ошибка отправки фото игрока {}: {}", player.getName(), e.getMessage(), e);
                sendMessage(bot, chatId, cardText.toString());
            }
        } catch (SQLException e) {
            logger.error("Ошибка при получении карточки игрока для пользователя {}: {}", userId, e.getMessage(), e);
            sendMessage(bot, chatId, "Произошла ошибка при получении карточки игрока.");
        }
    }

    // Создание клавиатуры с лигами
    public static InlineKeyboardMarkup createLeagueKeyboard(List<League> leagues, String prefix, long userId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (League league : leagues) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(league.getName());
            button.setCallbackData(prefix + "_league_" + league.getId() + "_" + userId);
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rows.add(row);
        }
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    // Создание клавиатуры с командами
    public static InlineKeyboardMarkup createTeamKeyboard(List<Team> teams, String prefix, long userId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Team team : teams) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(team.getName());
            button.setCallbackData(prefix + "_team_" + team.getId() + "_" + userId);
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rows.add(row);
        }
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    // Создание клавиатуры с игроками (с пагинацией)
    public static InlineKeyboardMarkup createPlayerKeyboard(DatabaseManager db, List<Player> players, String prefix, long userId, int page, int pageSize, String position) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, players.size());
        List<Player> playersOnPage = players.subList(startIndex, endIndex);

        for (Player player : playersOnPage) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(player.getName() + " " + player.getCategory().getEmoji() + " (" + player.getRating() + ")");
            button.setCallbackData(prefix + "_player_" + player.getId() + "_" + player.getTeamId() + "_" + userId);
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rows.add(row);
        }

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (page > 1) {
            InlineKeyboardButton prevButton = new InlineKeyboardButton();
            prevButton.setText("⬅️ Предыдущая");
            prevButton.setCallbackData(prefix + "_" + position + "_" + userId + "_page_" + (page - 1));
            navRow.add(prevButton);
        }
        if (endIndex < players.size()) {
            InlineKeyboardButton nextButton = new InlineKeyboardButton();
            nextButton.setText("Следующая ➡️");
            nextButton.setCallbackData(prefix + "_" + position + "_" + userId + "_page_" + (page + 1));
            navRow.add(nextButton);
        }
        if (!navRow.isEmpty()) {
            rows.add(navRow);
        }

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        if (prefix.startsWith("squad_select_")) {
            backButton.setCallbackData("squad_menu_" + userId);
        } else if (prefix.startsWith("inventory")) {
            int teamId = -1;
            if (position.contains("_")) {
                try {
                    teamId = Integer.parseInt(position.split("_")[1]);
                } catch (NumberFormatException e) {
                    logger.error("Failed to parse teamId from position: {}", position, e);
                }
            }
            int leagueId = -1;
            if (teamId != -1) {
                try {
                    leagueId = db.getTeamLeagueId(teamId);
                } catch (SQLException e) {
                    logger.error("Failed to fetch leagueId for teamId {}: {}", teamId, e.getMessage(), e);
                }
            }
            if (leagueId == -1) {
                backButton.setCallbackData("inventory_start_" + userId);
            } else {
                backButton.setCallbackData("inventory_league_" + leagueId + "_" + userId);
            }
        } else if (prefix.startsWith("teams")) {
            int teamId = -1;
            if (position.contains("_")) {
                try {
                    teamId = Integer.parseInt(position.split("_")[1]);
                } catch (NumberFormatException e) {
                    logger.error("Failed to parse teamId from position: {}", position, e);
                }
            }
            int leagueId = -1;
            if (teamId != -1) {
                try {
                    leagueId = db.getTeamLeagueId(teamId);
                } catch (SQLException e) {
                    logger.error("Failed to fetch leagueId for teamId {}: {}", teamId, e.getMessage(), e);
                }
            }
            if (leagueId == -1) {
                backButton.setCallbackData("teams_start_" + userId);
            } else {
                backButton.setCallbackData("teams_league_" + leagueId + "_" + userId);
            }
        }
        backRow.add(backButton);
        rows.add(backRow);

        keyboard.setKeyboard(rows);
        return keyboard;
    }

    // Перегруженный метод без пагинации
    public static InlineKeyboardMarkup createPlayerKeyboard(DatabaseManager db, List<Player> players, String prefix, long userId) {
        return createPlayerKeyboard(db, players, prefix, userId, 1, Integer.MAX_VALUE, "");
    }

    // Обработка нажатия на кнопку "Начать игру!" и отображение главного меню
    public static void handleStartGame(TelegramBot bot, CallbackQuery callbackQuery, int messageId, long userId) {
        long chatId = callbackQuery.getMessage().getChatId();
        String text = "Главное меню:\n\n" +
                "🎒 Инвентарь - Просмотрите своих игроков.\n" +
                "🎁 Паки - Откройте свои паки.\n" +
                "🏆 Рейтинг - Топ игроков по очкам.\n" +
                "🎁 Подарочный набор - Получите до 3 игроков.\n" +
                "🏰 Кланы - Создайте или вступите в клан.\n" +
                "⚽ Мой состав - Создайте свой состав.";

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton inventoryButton = new InlineKeyboardButton();
        inventoryButton.setText("🎒 Инвентарь");
        inventoryButton.setCallbackData("inventory_start_" + userId);
        row1.add(inventoryButton);

        InlineKeyboardButton packsButton = new InlineKeyboardButton();
        packsButton.setText("🎁 Паки");
        packsButton.setCallbackData("packs_menu_" + userId);
        row1.add(packsButton);
        rows.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton ratingsButton = new InlineKeyboardButton();
        ratingsButton.setText("🏆 Рейтинг");
        ratingsButton.setCallbackData("ratings_" + userId);
        row2.add(ratingsButton);

        InlineKeyboardButton giftButton = new InlineKeyboardButton();
        giftButton.setText("🎁 Подарочный набор");
        giftButton.setCallbackData("gift_" + userId);
        row2.add(giftButton);
        rows.add(row2);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton clansButton = new InlineKeyboardButton();
        clansButton.setText("🏰 Кланы");
        clansButton.setCallbackData("clans_menu_" + userId);
        row3.add(clansButton);

        InlineKeyboardButton squadButton = new InlineKeyboardButton();
        squadButton.setText("⚽ Мой состав");
        squadButton.setCallbackData("squad_menu_" + userId);
        row3.add(squadButton);
        rows.add(row3);

        keyboard.setKeyboard(rows);
        editMessage(bot, chatId, messageId, text, keyboard);
    }

    // Отображение меню паков
    public static void handlePacksMenu(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId) {
        String text = "🎁 Меню паков:\n\n" +
                "Выберите действие:\n\n" +
                "🎁 Мои паки - Посмотреть ваши паки.\n" +
                "💵 Паки за 💵 - Купить паки за деньги.\n" +
                "💎 Паки за 💎 - Купить паки за алмазы.";

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton myPacksButton = new InlineKeyboardButton();
        myPacksButton.setText("🎁 Мои паки");
        myPacksButton.setCallbackData("packs_my_" + userId);
        row1.add(myPacksButton);
        rows.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton moneyPacksButton = new InlineKeyboardButton();
        moneyPacksButton.setText("💵 Паки за 💵");
        moneyPacksButton.setCallbackData("packs_money_" + userId);
        row2.add(moneyPacksButton);
        rows.add(row2);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton diamondPacksButton = new InlineKeyboardButton();
        diamondPacksButton.setText("💎 Паки за 💎");
        diamondPacksButton.setCallbackData("packs_diamond_" + userId);
        row3.add(diamondPacksButton);
        rows.add(row3);

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        backButton.setCallbackData("start_game_" + userId);
        backRow.add(backButton);
        rows.add(backRow);

        keyboard.setKeyboard(rows);
        editMessage(bot, chatId, messageId, text, keyboard);
    }

    // Обработка покупки пака
    public static void handleBuyPack(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId, int packId) {
        try {
            Pack pack = db.getPackById(packId);
            if (pack == null) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                List<InlineKeyboardButton> backRow = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("Назад");
                backButton.setCallbackData("packs_menu_" + userId);
                backRow.add(backButton);
                rows.add(backRow);
                keyboard.setKeyboard(rows);
                editMessage(bot, chatId, messageId, "Пак не найден.", keyboard);
                return;
            }

            int userDollars = db.getUserDollars(userId);
            if (userDollars < pack.getPrice()) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                List<InlineKeyboardButton> backRow = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("Назад");
                backButton.setCallbackData("packs_menu_" + userId);
                backRow.add(backButton);
                rows.add(backRow);
                keyboard.setKeyboard(rows);
                editMessage(bot, chatId, messageId, "Недостаточно долларов для покупки пака " + pack.getName() + ".", keyboard);
                return;
            }

            db.deductDollars(userId, pack.getPrice());
            db.addPackToUser(userId, packId);

            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("packs_menu_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Вы успешно купили пак " + pack.getName() + "!", keyboard);
        } catch (SQLException e) {
            logger.error("Ошибка при покупке пака {} для пользователя {}: {}", packId, userId, e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при покупке пака.", null);
        }
    }

    // Обработка открытия пака
    public static void openPack(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId) {
        try {
            // 1) Получаем все паки пользователя
            List<Pack> packs = db.getUserPacks(userId);
            if (packs == null || packs.isEmpty()) {
                Utils.sendMessage(bot, chatId, "У вас нет паков для открытия.");
                return;
            }

            // 2) Берём первый пак из списка
            Pack pack = packs.get(0);
            int packId = pack.getId();

            // 3) Подтягиваем полные данные пака
            Pack fullPack = db.getPackById(packId);
            if (fullPack == null || fullPack.getId() == 0) {
                logger.error("Pack not found or invalid: packId={}", packId);
                Utils.editMessage(bot, chatId, messageId, "Ошибка: Пак не найден.", null);
                return;
            }

            // 4) Проверяем, действительно ли пользователь владеет этим паком
            if (!db.checkUserHasPack(userId, packId)) {
                logger.warn("User {} does not have packId={}", userId, packId);
                Utils.editMessage(bot, chatId, messageId, "У вас нет этого пака.", null);
                return;
            }

            // 5) Удаляем связь пака и пользователя
            db.removePackFromUser(userId, packId);

            // 6) Формируем текст ответа
            String packName = fullPack.getName() != null ? fullPack.getName() : "Неизвестный пак";
            StringBuilder text = new StringBuilder("🎉 Вы открыли пак «")
                    .append(packName)
                    .append("»!\nПолученные игроки:\n");

            for (int i = 0; i < fullPack.getPlayerCount(); i++) {
                Player player = fullPack.getCategory() != null
                        ? db.getRandomPlayerByCategory(fullPack.getCategory())
                        : db.getRandomPlayer();

                if (player != null) {
                    db.addPlayerToUser(userId, player);
                    text.append(String.format("- %s (%s, %d)\n",
                            player.getName(),
                            player.getPosition(),
                            player.getRating()
                    ));
                } else {
                    logger.warn("No player received when opening packId={} for userId={}", packId, userId);
                }
            }

            // 7) Кнопка «Назад» для возвращения в меню паков
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("packs_my_" + userId);
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                    List.of(List.of(backButton))
            );

            // 8) Отправляем результат через editMessage
            Utils.editMessage(bot, chatId, messageId, text.toString(), keyboard);

        } catch (SQLException e) {
            logger.error("Ошибка при открытии пака для пользователя {}: {}", userId, e.getMessage(), e);
            Utils.editMessage(bot, chatId, messageId, "Произошла ошибка при открытии пака.", null);
        }
    }


    // Навигация по инвентарю пользователя
    public static void handleInventoryNavigation(TelegramBot bot, DatabaseManager db, CallbackQuery callbackQuery, int messageId, long userId) {
        String data = callbackQuery.getData();
        long chatId = callbackQuery.getMessage().getChatId();

        logger.info("handleInventoryNavigation called with chatId: {}, messageId: {}, userId: {}, data: {}", chatId, messageId, userId, data);

        String[] parts = data.split("_");
        if (parts.length < 3) {
            logger.error("Invalid CallbackData format: {}", data);
            editMessage(bot, chatId, messageId, "Ошибка: Неверный формат данных.", null);
            return;
        }

        if (parts[1].equals("start")) {
            List<League> leagues;
            try {
                leagues = db.getLeagues();
            } catch (SQLException e) {
                logger.error("Failed to fetch leagues: {}", e.getMessage(), e);
                editMessage(bot, chatId, messageId, "Ошибка при получении списка лиг.", null);
                return;
            }
            if (leagues == null || leagues.isEmpty()) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("Назад");
                backButton.setCallbackData("start_game_" + userId);
                rows.add(Collections.singletonList(backButton));
                keyboard.setKeyboard(rows);
                editMessage(bot, chatId, messageId, "Лиги отсутствуют в базе данных.", keyboard);
                return;
            }
            String text = "Выберите Лигу:";
            InlineKeyboardMarkup keyboard = createLeagueKeyboard(leagues, "inventory", userId);
            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("start_game_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, text, keyboard);
        } else if (parts[1].equals("league")) {
            int leagueId;
            try {
                leagueId = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                logger.error("Failed to parse leagueId from CallbackData: {}", data, e);
                editMessage(bot, chatId, messageId, "Ошибка: Неверный ID лиги.", null);
                return;
            }

            List<Team> teams;
            try {
                teams = db.getTeamsByLeague(leagueId);
            } catch (SQLException e) {
                logger.error("Failed to fetch teams for leagueId {}: {}", leagueId, e.getMessage(), e);
                editMessage(bot, chatId, messageId, "Ошибка при получении команд из лиги.", null);
                return;
            }

            if (teams == null || teams.isEmpty()) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("Назад");
                backButton.setCallbackData("inventory_start_" + userId);
                rows.add(Collections.singletonList(backButton));
                keyboard.setKeyboard(rows);
                String leagueName;
                try {
                    leagueName = db.getLeagueName(leagueId);
                } catch (SQLException e) {
                    logger.error("Failed to fetch league name for leagueId {}: {}", leagueId, e.getMessage(), e);
                    leagueName = "ID " + leagueId;
                }
                editMessage(bot, chatId, messageId, "Команды в лиге " + leagueName + " отсутствуют.", keyboard);
                return;
            }

            String leagueName;
            try {
                leagueName = db.getLeagueName(leagueId);
            } catch (SQLException e) {
                logger.error("Failed to fetch league name for leagueId {}: {}", leagueId, e.getMessage(), e);
                leagueName = "ID " + leagueId;
            }
            String text = "Выберите команду из Лиги: " + leagueName;
            InlineKeyboardMarkup keyboard = createTeamKeyboard(teams, "inventory", userId);
            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("inventory_start_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, text, keyboard);
        } else if (parts[1].equals("team")) {
            int teamId;
            try {
                teamId = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                logger.error("Failed to parse teamId from CallbackData: {}", data, e);
                editMessage(bot, chatId, messageId, "Ошибка: Неверный ID команды.", null);
                return;
            }

            int page = 1;
            if (parts.length > 4 && parts[parts.length - 2].equals("page")) {
                try {
                    page = Integer.parseInt(parts[parts.length - 1]);
                } catch (NumberFormatException e) {
                    logger.error("Failed to parse page number from CallbackData: {}", data, e);
                    page = 1;
                }
            }

            List<Player> players;
            try {
                players = db.getUserPlayersByTeam(userId, teamId);
            } catch (SQLException e) {
                logger.error("Failed to fetch players for userId {} and teamId {}: {}", userId, teamId, e.getMessage(), e);
                editMessage(bot, chatId, messageId, "Ошибка при получении игроков команды.", null);
                return;
            }

            String teamName;
            try {
                teamName = db.getTeamName(teamId);
            } catch (SQLException e) {
                logger.error("Failed to fetch team name for teamId {}: {}", teamId, e.getMessage(), e);
                teamName = "ID " + teamId;
            }

            if (players == null || players.isEmpty()) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                int leagueId;
                try {
                    leagueId = db.getTeamLeagueId(teamId);
                } catch (SQLException e) {
                    logger.error("Failed to fetch leagueId for teamId {}: {}", teamId, e.getMessage(), e);
                    editMessage(bot, chatId, messageId, "Ошибка при получении ID лиги команды.", null);
                    return;
                }
                backButton.setText("Назад");
                backButton.setCallbackData("inventory_league_" + leagueId + "_" + userId);
                rows.add(Collections.singletonList(backButton));
                keyboard.setKeyboard(rows);
                editMessage(bot, chatId, messageId, "У вас нет игроков из команды " + teamName + "!", keyboard);
            } else {
                int pageSize = 5;
                String text = "Выберите игрока из команды " + teamName + " (Страница " + page + "):";
                InlineKeyboardMarkup keyboard = createPlayerKeyboard(db, players, "inventory", userId, page, pageSize, "team_" + teamId);
                editMessage(bot, chatId, messageId, text, keyboard);
            }
        } else if (parts[1].equals("player")) {
            int playerId;
            try {
                playerId = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                logger.error("Failed to parse playerId from CallbackData: {}", data, e);
                editMessage(bot, chatId, messageId, "Ошибка: Неверный ID игрока.", null);
                return;
            }

            Player player;
            try {
                player = db.getPlayerById(playerId);
            } catch (SQLException e) {
                logger.error("Failed to fetch player with playerId {}: {}", playerId, e.getMessage(), e);
                editMessage(bot, chatId, messageId, "Ошибка при получении данных игрока.", null);
                return;
            }

            if (player == null) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("Назад");
                backButton.setCallbackData("inventory_team_" + parts[3] + "_" + userId);
                rows.add(Collections.singletonList(backButton));
                keyboard.setKeyboard(rows);
                editMessage(bot, chatId, messageId, "Игрок не найден.", keyboard);
                return;
            }
            sendPlayerCard(bot, db, chatId, player, 0);
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("inventory_team_" + player.getTeamId() + "_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Вернуться к выбору игрока:", keyboard);
        }
    }

    // Навигация по командам пользователя
    public static void handleTeamsNavigation(TelegramBot bot, DatabaseManager db, CallbackQuery callbackQuery, long userId, int messageId) {
        String data = callbackQuery.getData();
        long chatId = callbackQuery.getMessage().getChatId();

        String[] parts = data.split("_");

        if (parts[1].equals("start")) {
            List<League> leagues;
            try {
                leagues = db.getLeagues();
            } catch (SQLException e) {
                logger.error("Failed to fetch leagues: {}", e.getMessage(), e);
                editMessage(bot, chatId, messageId, "Ошибка при получении списка лиг.", null);
                return;
            }
            if (leagues == null || leagues.isEmpty()) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("Назад");
                backButton.setCallbackData("start_game_" + userId);
                rows.add(Collections.singletonList(backButton));
                keyboard.setKeyboard(rows);
                editMessage(bot, chatId, messageId, "Лиги отсутствуют в базе данных.", keyboard);
                return;
            }
            String text = "Выберите Лигу для просмотра команд:";
            InlineKeyboardMarkup keyboard = createLeagueKeyboard(leagues, "teams", userId);
            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("start_game_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, text, keyboard);
        } else if (parts[1].equals("league")) {
            int leagueId;
            try {
                leagueId = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                logger.error("Failed to parse leagueId from CallbackData: {}", data, e);
                editMessage(bot, chatId, messageId, "Ошибка: Неверный ID лиги.", null);
                return;
            }
            List<Team> teams;
            try {
                teams = db.getTeamsByLeague(leagueId);
            } catch (SQLException e) {
                logger.error("Failed to fetch teams for leagueId {}: {}", leagueId, e.getMessage(), e);
                editMessage(bot, chatId, messageId, "Ошибка при получении команд из лиги.", null);
                return;
            }
            if (teams == null || teams.isEmpty()) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("Назад");
                backButton.setCallbackData("teams_start_" + userId);
                rows.add(Collections.singletonList(backButton));
                keyboard.setKeyboard(rows);
                String leagueName;
                try {
                    leagueName = db.getLeagueName(leagueId);
                } catch (SQLException e) {
                    logger.error("Failed to fetch league name for leagueId {}: {}", leagueId, e.getMessage(), e);
                    leagueName = "ID " + leagueId;
                }
                editMessage(bot, chatId, messageId, "Команды в лиге " + leagueName + " отсутствуют.", keyboard);
                return;
            }

            List<Team> userTeams = new ArrayList<>();
            for (Team team : teams) {
                List<Player> players;
                try {
                    players = db.getUserPlayersByTeam(userId, team.getId());
                } catch (SQLException e) {
                    logger.error("Failed to fetch players for userId {} and teamId {}: {}", userId, team.getId(), e.getMessage(), e);
                    editMessage(bot, chatId, messageId, "Ошибка при получении игроков команды.", null);
                    return;
                }
                if (!players.isEmpty()) {
                    userTeams.add(team);
                }
            }

            String leagueName;
            try {
                leagueName = db.getLeagueName(leagueId);
            } catch (SQLException e) {
                logger.error("Failed to fetch league name for leagueId {}: {}", leagueId, e.getMessage(), e);
                leagueName = "ID " + leagueId;
            }

            if (userTeams.isEmpty()) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("Назад");
                backButton.setCallbackData("teams_start_" + userId);
                rows.add(Collections.singletonList(backButton));
                keyboard.setKeyboard(rows);
                editMessage(bot, chatId, messageId, "У вас нет игроков в командах лиги " + leagueName + ".", keyboard);
                return;
            }

            String text = "Ваши команды в лиге " + leagueName + ":";
            InlineKeyboardMarkup keyboard = createTeamKeyboard(userTeams, "teams", userId);
            List<List<InlineKeyboardButton>> rows = keyboard.getKeyboard();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("teams_start_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, text, keyboard);
        } else if (parts[1].equals("team")) {
            int teamId;
            try {
                teamId = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                logger.error("Failed to parse teamId from CallbackData: {}", data, e);
                editMessage(bot, chatId, messageId, "Ошибка: Неверный ID команды.", null);
                return;
            }

            int page = 1;
            if (parts.length > 4 && parts[parts.length - 2].equals("page")) {
                try {
                    page = Integer.parseInt(parts[parts.length - 1]);
                } catch (NumberFormatException e) {
                    logger.error("Failed to parse page number from CallbackData: {}", data, e);
                    page = 1;
                }
            }

            List<Player> players;
            try {
                players = db.getUserPlayersByTeam(userId, teamId);
            } catch (SQLException e) {
                logger.error("Failed to fetch players for userId {} and teamId {}: {}", userId, teamId, e.getMessage(), e);
                editMessage(bot, chatId, messageId, "Ошибка при получении игроков команды.", null);
                return;
            }

            String teamName;
            try {
                teamName = db.getTeamName(teamId);
            } catch (SQLException e) {
                logger.error("Failed to fetch team name for teamId {}: {}", teamId, e.getMessage(), e);
                teamName = "ID " + teamId;
            }

            int leagueId;
            try {
                leagueId = db.getTeamLeagueId(teamId);
            } catch (SQLException e) {
                logger.error("Failed to fetch leagueId for teamId {}: {}", teamId, e.getMessage(), e);
                editMessage(bot, chatId, messageId, "Ошибка при получении ID лиги команды.", null);
                return;
            }

            String leagueName;
            try {
                leagueName = db.getLeagueName(leagueId);
            } catch (SQLException e) {
                logger.error("Failed to fetch league name for leagueId {}: {}", leagueId, e.getMessage(), e);
                leagueName = "ID " + leagueId;
            }

            if (players == null || players.isEmpty()) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("Назад");
                backButton.setCallbackData("teams_league_" + leagueId + "_" + userId);
                rows.add(Collections.singletonList(backButton));
                keyboard.setKeyboard(rows);
                editMessage(bot, chatId, messageId, "У вас нет игроков из команды " + teamName + "!", keyboard);
            } else {
                int pageSize = 5;
                String text = "Ваши игроки из команды " + teamName + " (" + leagueName + ") (Страница " + page + "):";
                InlineKeyboardMarkup keyboard = createPlayerKeyboard(db, players, "teams", userId, page, pageSize, "team_" + teamId);
                editMessage(bot, chatId, messageId, text, keyboard);
            }
        } else if (parts[1].equals("player")) {
            int playerId;
            try {
                playerId = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                logger.error("Failed to parse playerId from CallbackData: {}", data, e);
                editMessage(bot, chatId, messageId, "Ошибка: Неверный ID игрока.", null);
                return;
            }
            Player player;
            try {
                player = db.getPlayerById(playerId);
            } catch (SQLException e) {
                logger.error("Failed to fetch player with playerId {}: {}", playerId, e.getMessage(), e);
                editMessage(bot, chatId, messageId, "Ошибка при получении данных игрока.", null);
                return;
            }
            if (player == null) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("Назад");
                backButton.setCallbackData("teams_team_" + parts[3] + "_" + userId);
                rows.add(Collections.singletonList(backButton));
                keyboard.setKeyboard(rows);
                editMessage(bot, chatId, messageId, "Игрок не найден.", keyboard);
                return;
            }
            sendPlayerCard(bot, db, chatId, player, 0);
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("teams_team_" + player.getTeamId() + "_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Вернуться к выбору игрока:", keyboard);
        }
    }

    // Отображение рейтинга топ-10 пользователей
    public static void handleRatings(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId) {
        List<User> topUsers;
        try {
            topUsers = db.getTopUsersByPoints(10);
        } catch (SQLException e) {
            logger.error("Failed to fetch top users: {}", e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при получении рейтинга пользователей.", null);
            return;
        }
        StringBuilder text = new StringBuilder("🏆 Топ-10 игроков по очкам:\n\n");
        int rank = 1;
        for (User user : topUsers) {
            text.append(rank).append(". ").append(user.getUsername()).append(": ").append(user.getPoints()).append(" очков\n");
            rank++;
        }
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        backButton.setCallbackData("start_game_" + userId);
        backRow.add(backButton);
        rows.add(backRow);
        keyboard.setKeyboard(rows);
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    // Обработка получения подарочного набора
    public static void handleGiftPack(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId) throws SQLException {
        Pack dailyPack = db.getPackById(1); // Предполагаем, что Daily Award Pack имеет ID=1
        if (dailyPack != null && dailyPack.isDaily()) {
            Timestamp lastGift = db.getLastGift(userId);
            long currentTime = System.currentTimeMillis();
            long cooldownMillis = dailyPack.getCooldownHours() * 60 * 60 * 1000L;
            if (lastGift != null && (currentTime - lastGift.getTime()) < cooldownMillis) {
                long timeLeft = (cooldownMillis - (currentTime - lastGift.getTime())) / 1000;
                String errorMessage = String.format("Подарочный пак доступен через %d:%02d:%02d", timeLeft / 3600, (timeLeft % 3600) / 60, timeLeft % 60);
                InlineKeyboardMarkup keyboard = createBackKeyboard("packs_menu_" + userId);
                editMessage(bot, chatId, messageId, errorMessage, keyboard);
                return;
            }
        }
        // Логика получения подарочного пака
        int claims = db.checkAndResetGiftPackClaims(userId);
        if (claims >= 3) {
            editMessage(bot, chatId, messageId, "Вы исчерпали лимит подарочных паков на сегодня.", null);
            return;
        }
        db.incrementGiftPackClaims(userId);
        db.updateLastGift(userId);
        db.addPackToUser(userId, 1); // Добавляем Daily Award Pack
        String text = "🎁 Вы получили подарочный пак!";
        InlineKeyboardMarkup keyboard = createBackKeyboard("packs_my_" + userId);
        editMessage(bot, chatId, messageId, text, keyboard);
    }

    // Обработка следующего игрока в подарочном наборе
    public static void handleNextGiftPlayer(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId, int claims) {
        if (claims >= 3) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("start_game_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Вы уже получили максимальное количество подарочных наборов (3)!", keyboard);
            return;
        }

        Player player;
        try {
            player = db.getRandomPlayerByCategory("Bronze");
        } catch (SQLException e) {
            logger.error("Failed to fetch random player for gift pack: {}", e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при получении игрока для подарочного набора.", null);
            return;
        }

        if (player == null) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("start_game_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Не удалось найти игрока для подарочного набора.", keyboard);
            return;
        }

        try {
            db.addPlayerToUser(userId, player);
            db.addPoints(userId, player.getCategory().getPoints());
            db.incrementGiftPackClaims(userId);
            db.updateLastGift(userId);
        } catch (SQLException e) {
            logger.error("Failed to add player or points to userId {}: {}", userId, e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при добавлении игрока в ваш инвентарь.", null);
            return;
        }

        sendPlayerCard(bot, db, chatId, player, player.getCategory().getPoints());

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        if (claims < 2) {
            InlineKeyboardButton nextButton = new InlineKeyboardButton();
            nextButton.setText("Получить еще одного игрока");
            nextButton.setCallbackData("next_gift_player_" + (claims + 1) + "_" + userId);
            row.add(nextButton);
        }
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        backButton.setCallbackData("start_game_" + userId);
        row.add(backButton);
        rows.add(row);
        keyboard.setKeyboard(rows);
        editMessage(bot, chatId, messageId, "Вы получили игрока из подарочного набора! (" + (claims + 1) + "/3)", keyboard);
    }

    // Отображение меню кланов
    public static void handleClansMenu(TelegramBot bot, long chatId, int messageId, long userId) {
        String text = "🏰 Меню кланов:\n\n" +
                "📊 Топ кланов - Посмотрите рейтинг кланов.\n" +
                "👥 Мой клан - Информация о вашем клане.\n" +
                "➕ Создать клан - Создайте свой клан.";
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton topClansButton = new InlineKeyboardButton();
        topClansButton.setText("📊 Топ кланов");
        topClansButton.setCallbackData("top_clans_1_" + userId);
        row1.add(topClansButton);
        InlineKeyboardButton myClanButton = new InlineKeyboardButton();
        myClanButton.setText("👥 Мой клан");
        myClanButton.setCallbackData("my_clan_" + userId);
        row1.add(myClanButton);
        rows.add(row1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton createClanButton = new InlineKeyboardButton();
        createClanButton.setText("➕ Создать клан");
        createClanButton.setCallbackData("create_clan_" + userId);
        row2.add(createClanButton);
        rows.add(row2);

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        backButton.setCallbackData("start_game_" + userId);
        backRow.add(backButton);
        rows.add(backRow);

        keyboard.setKeyboard(rows);
        editMessage(bot, chatId, messageId, text, keyboard);
    }

    // Отображение топа кланов с пагинацией
    public static void handleTopClans(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId, int page) {
        List<Clan> clans;
        try {
            clans = db.getTopClans(page, 5);
        } catch (SQLException e) {
            logger.error("Failed to fetch top clans for page {}: {}", page, e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при получении списка кланов.", null);
            return;
        }

        if (clans == null || clans.isEmpty()) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("clans_menu_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Кланы отсутствуют.", keyboard);
            return;
        }

        StringBuilder text = new StringBuilder("🏆 Топ кланов (Страница " + page + "):\n\n");
        int rank = (page - 1) * 5 + 1;
        for (Clan clan : clans) {
            text.append(rank).append(". ").append(clan.getName()).append(": ").append(clan.getTotalPoints()).append(" очков\n");
            rank++;
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Clan clan : clans) {
            InlineKeyboardButton viewButton = new InlineKeyboardButton();
            viewButton.setText("👀 " + clan.getName());
            viewButton.setCallbackData("view_clan_" + clan.getId() + "_" + userId);
            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(viewButton);
            rows.add(row);
        }

        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (page > 1) {
            InlineKeyboardButton prevButton = new InlineKeyboardButton();
            prevButton.setText("⬅️ Предыдущая");
            prevButton.setCallbackData("top_clans_" + (page - 1) + "_" + userId);
            navRow.add(prevButton);
        }
        try {
            if (db.getTopClans(page + 1, 5).size() > 0) {
                InlineKeyboardButton nextButton = new InlineKeyboardButton();
                nextButton.setText("Следующая ➡️");
                nextButton.setCallbackData("top_clans_" + (page + 1) + "_" + userId);
                navRow.add(nextButton);
            }
        } catch (SQLException e) {
            logger.error("Failed to check next page of clans: {}", e.getMessage(), e);
        }
        if (!navRow.isEmpty()) {
            rows.add(navRow);
        }

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        backButton.setCallbackData("clans_menu_" + userId);
        backRow.add(backButton);
        rows.add(backRow);

        keyboard.setKeyboard(rows);
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    // Вступление в клан
    public static void handleJoinClan(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId, int clanId, int page) throws SQLException {
        Clan clan = db.getClanById(clanId);
        if (clan == null) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("top_clans_" + page + "_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Клан не найден.", keyboard);
            return;
        }

        Clan existingClan = db.getUserClan(userId);
        if (existingClan != null) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("top_clans_" + page + "_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Вы уже состоите в клане " + existingClan.getName() + ".", keyboard);
            return;
        }

        try {
            db.joinClan(userId, clanId);
        } catch (SQLException e) {
            logger.error("Failed to join clan with clanId {} for userId {}: {}", clanId, userId, e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при вступлении в клан.", null);
            return;
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        backButton.setCallbackData("top_clans_" + page + "_" + userId);
        backRow.add(backButton);
        rows.add(backRow);
        keyboard.setKeyboard(rows);
        editMessage(bot, chatId, messageId, "Вы успешно вступили в клан " + clan.getName() + "!", keyboard);
    }

    // Отображение информации о клане пользователя
    public static void handleMyClan(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId) throws SQLException {
        Clan clan;
        try {
            clan = db.getUserClan(userId);
        } catch (SQLException e) {
            logger.error("Failed to fetch clan for userId {}: {}", userId, e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при получении информации о вашем клане.", null);
            return;
        }
        if (clan == null) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("clans_menu_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Вы не состоите в клане.", keyboard);
            return;
        }

        List<User> members = db.getClanMembers(clan.getId());
        if (members == null) {
            editMessage(bot, chatId, messageId, "Ошибка при получении списка участников клана.", null);
            return;
        }

        StringBuilder text = new StringBuilder("🏰 Ваш клан: " + clan.getName() + "\n\n");
        text.append("👑 Владелец: ");
        User owner = db.getUserById(clan.getOwnerId());
        text.append(owner != null ? owner.getUsername() : "Неизвестно").append("\n");
        text.append("👥 Участники (").append(members.size()).append("):\n");
        for (User member : members) {
            text.append("- ").append(member.getUsername()).append(": ").append(member.getPoints()).append(" очков\n");
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        backButton.setCallbackData("clans_menu_" + userId);
        backRow.add(backButton);
        rows.add(backRow);
        keyboard.setKeyboard(rows);
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    // Создание нового клана
    public static void handleCreateClan(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId) {
        Clan existingClan;
        try {
            existingClan = db.getUserClan(userId);
        } catch (SQLException e) {
            logger.error("Failed to check existing clan for userId {}: {}", userId, e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при проверке вашего клана.", null);
            return;
        }
        if (existingClan != null) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("clans_menu_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Вы уже состоите в клане " + existingClan.getName() + ". Покиньте текущий клан, чтобы создать новый.", keyboard);
            return;
        }

        String clanName = "Клан_" + userId + "_" + ThreadLocalRandom.current().nextInt(1000);
        int clanId;
        try {
            clanId = db.createClan(clanName, userId);
        } catch (SQLException e) {
            logger.error("Failed to create clan for userId {}: {}", userId, e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при создании клана.", null);
            return;
        }

        try {
            db.joinClan(userId, clanId);
        } catch (SQLException e) {
            logger.error("Failed to join created clan with clanId {} for userId {}: {}", clanId, userId, e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при вступлении в созданный клан.", null);
            return;
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        backButton.setCallbackData("clans_menu_" + userId);
        backRow.add(backButton);
        rows.add(backRow);
        keyboard.setKeyboard(rows);
        editMessage(bot, chatId, messageId, "Клан " + clanName + " успешно создан! Вы стали его владельцем.", keyboard);
    }

    // Просмотр информации о клане
    public static void handleViewClan(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId, int clanId, int page) throws SQLException {
        Clan clan = db.getClanById(clanId);
        if (clan == null) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("top_clans_" + page + "_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Клан не найден.", keyboard);
            return;
        }

        List<User> members = db.getClanMembers(clanId);
        if (members == null) {
            editMessage(bot, chatId, messageId, "Ошибка при получении списка участников клана.", null);
            return;
        }

        StringBuilder text = new StringBuilder("🏰 Клан: " + clan.getName() + "\n\n");
        text.append("👑 Владелец: ");
        User owner = db.getUserById(clan.getOwnerId());
        text.append(owner != null ? owner.getUsername() : "Неизвестно").append("\n");
        text.append("📊 Очки: ").append(clan.getTotalPoints()).append("\n");
        text.append("👥 Участники (").append(members.size()).append("):\n");
        for (User member : members) {
            text.append("- ").append(member.getUsername()).append(": ").append(member.getPoints()).append(" очков\n");
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> joinRow = new ArrayList<>();
        InlineKeyboardButton joinButton = new InlineKeyboardButton();
        joinButton.setText("➡️ Вступить в клан");
        joinButton.setCallbackData("join_clan_" + clanId + "_" + page + "_" + userId);
        joinRow.add(joinButton);
        rows.add(joinRow);
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        backButton.setCallbackData("top_clans_" + page + "_" + userId);
        backRow.add(backButton);
        rows.add(backRow);
        keyboard.setKeyboard(rows);
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    // Обработка удара в серии пенальти
    public static void handlePenaltyKick(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId, String direction, CallbackQuery callbackQuery) {
        TelegramBot.PenaltyGame currentGame = bot.getCurrentGame();
        if (currentGame == null) {
            sendMessage(bot, chatId, "Игра не найдена. Начните новую серию пенальти.");
            return;
        }

        long kickerId = currentGame.kicker == 1 ? currentGame.challengerId : currentGame.opponentId;
        long keeperId = currentGame.kicker == 1 ? currentGame.opponentId : currentGame.challengerId;
        String kickerUsername = currentGame.kicker == 1 ? currentGame.challengerUsername : currentGame.opponentUsername;
        String keeperUsername = currentGame.kicker == 1 ? currentGame.opponentUsername : currentGame.challengerUsername;

        if (currentGame.currentRound == 1 && userId != kickerId) {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            answer.setText("Сейчас не ваш ход!");
            answer.setShowAlert(true);
            try {
                bot.execute(answer);
            } catch (Exception e) {
                logger.error("Failed to send alert to user {}: {}", userId, e.getMessage(), e);
            }
            return;
        }
        if (currentGame.currentRound == 2 && userId != keeperId) {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            answer.setText("Сейчас не ваш ход!");
            answer.setShowAlert(true);
            try {
                bot.execute(answer);
            } catch (Exception e) {
                logger.error("Failed to send alert to user {}: {}", userId, e.getMessage(), e);
            }
            return;
        }

        if (currentGame.currentRound == 1) {
            currentGame.kickDirection = direction;
            currentGame.currentRound = 2;
            sendSaveDirectionMessage(bot, chatId, keeperId, keeperUsername);
        } else {
            String keeperDirection = direction;
            String kickDirection = currentGame.kickDirection;
            boolean isGoal = !kickDirection.equals(keeperDirection);
            String resultText = isGoal ? "⚽ ГОЛ! 🥅" : "🥅 Вратарь поймал мяч!";

            if (currentGame.kicker == 1) {
                currentGame.challengerGoals.add(isGoal);
                currentGame.challengerKicks++;
            } else {
                currentGame.opponentGoals.add(isGoal);
                currentGame.opponentKicks++;
            }

            sendMessage(bot, chatId, resultText, null);
            currentGame.kickDirection = null;
            currentGame.currentRound = 1;
            currentGame.kicker = (currentGame.kicker == 1) ? 2 : 1;

            if (currentGame.challengerKicks >= 5 && currentGame.opponentKicks >= 5) {
                int challengerScore = currentGame.getChallengerScore();
                int opponentScore = currentGame.getOpponentScore();
                if (challengerScore != opponentScore) {
                    showPenaltyResults(bot, chatId);
                    return;
                } else {
                    sendMessage(bot, chatId, "Счёт равный после 5 ударов! Переходим к внезапной смерти!", null);
                }
            }

            if (currentGame == null) return;

            kickerId = currentGame.kicker == 1 ? currentGame.challengerId : currentGame.opponentId;
            kickerUsername = currentGame.kicker == 1 ? currentGame.challengerUsername : currentGame.opponentUsername;

            if (currentGame.challengerKicks < 5 || currentGame.opponentKicks < 5) {
                sendKickDirectionMessage(bot, chatId, kickerId, kickerUsername);
            } else {
                sendKickDirectionMessage(bot, chatId, kickerId, kickerUsername);
                if (currentGame.challengerKicks > 5 && currentGame.opponentKicks > 5 && currentGame.challengerKicks == currentGame.opponentKicks) {
                    int challengerScore = currentGame.getChallengerScore();
                    int opponentScore = currentGame.getOpponentScore();
                    if (challengerScore != opponentScore) {
                        showPenaltyResults(bot, chatId);
                    }
                }
            }
        }
    }

    // Отправка сообщения для выбора стороны удара
    public static void sendSaveDirectionMessage(TelegramBot bot, long chatId, long userId, String username) {
        TelegramBot.PenaltyGame currentGame = bot.getCurrentGame();
        int kickNumber = currentGame.kicker == 1 ? currentGame.challengerKicks + 1 : currentGame.opponentKicks + 1;
        String text = username + ", выберите сторону для защиты (Удар " + kickNumber + "):";

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton leftButton = new InlineKeyboardButton();
        leftButton.setText("Левый угол");
        leftButton.setCallbackData("penalty_kick_left_" + userId);
        row.add(leftButton);

        InlineKeyboardButton centerButton = new InlineKeyboardButton();
        centerButton.setText("Центр");
        centerButton.setCallbackData("penalty_kick_center_" + userId);
        row.add(centerButton);

        InlineKeyboardButton rightButton = new InlineKeyboardButton();
        rightButton.setText("Правый угол");
        rightButton.setCallbackData("penalty_kick_right_" + userId);
        row.add(rightButton);

        rows.add(row);
        keyboard.setKeyboard(rows);

        sendMessage(bot, chatId, text, keyboard);
    }

    // Отображение результатов серии пенальти
    public static void showPenaltyResults(TelegramBot bot, long chatId) {
        TelegramBot.PenaltyGame currentGame = bot.getCurrentGame();
        if (currentGame == null) {
            sendMessage(bot, chatId, "Игра не найдена.");
            return;
        }

        int challengerScore = currentGame.getChallengerScore();
        int opponentScore = currentGame.getOpponentScore();

        StringBuilder text = new StringBuilder("🏆 Результаты серии пенальти:\n\n");
        text.append(currentGame.challengerUsername).append(": ").append(challengerScore).append(" гола\n");
        text.append(currentGame.opponentUsername).append(": ").append(opponentScore).append(" гола\n\n");

        if (challengerScore > opponentScore) {
            text.append("🎉 Победитель: ").append(currentGame.challengerUsername).append("!");
            try (DatabaseManager db = new DatabaseManager()) {
                db.addPoints(currentGame.challengerId, 10);
                db.addDollars(currentGame.challengerId, 50);
                text.append("\nНаграда: +10 очков, +50 долларов");
            } catch (SQLException e) {
                logger.error("Failed to award points/dollars to challenger {}: {}", currentGame.challengerId, e.getMessage(), e);
            }
        } else if (opponentScore > challengerScore) {
            text.append("🎉 Победитель: ").append(currentGame.opponentUsername).append("!");
            try (DatabaseManager db = new DatabaseManager()) {
                db.addPoints(currentGame.opponentId, 10);
                db.addDollars(currentGame.opponentId, 50);
                text.append("\nНаграда: +10 очков, +50 долларов");
            } catch (SQLException e) {
                logger.error("Failed to award points/dollars to opponent {}: {}", currentGame.opponentId, e.getMessage(), e);
            }
        } else {
            text.append("🤝 Ничья!");
        }

        bot.setCurrentGame(null); // Завершаем игру
        sendMessage(bot, chatId, text.toString());
    }

    // Форматирование времени для отображения кулдауна
    public static String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        minutes = minutes % 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static void sendKickDirectionMessage(TelegramBot bot, long chatId, long userId, String username) {
        TelegramBot.PenaltyGame currentGame = bot.getCurrentGame();
        int kickNumber = currentGame.kicker == 1 ? currentGame.challengerKicks + 1 : currentGame.opponentKicks + 1;
        String text = username + ", выберите сторону удара (Удар " + kickNumber + "):";

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton leftButton = new InlineKeyboardButton();
        leftButton.setText("Левый угол");
        leftButton.setCallbackData("penalty_kick_left_" + userId);
        row.add(leftButton);

        InlineKeyboardButton centerButton = new InlineKeyboardButton();
        centerButton.setText("Центр");
        centerButton.setCallbackData("penalty_kick_center_" + userId);
        row.add(centerButton);

        InlineKeyboardButton rightButton = new InlineKeyboardButton();
        rightButton.setText("Правый угол");
        rightButton.setCallbackData("penalty_kick_right_" + userId);
        row.add(rightButton);

        rows.add(row);
        keyboard.setKeyboard(rows);

        sendMessage(bot, chatId, text, keyboard);
    }

    // Отображение меню состава команды
    public static void handleSquadMenu(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId) {
        Squad squad = bot.getUserSquad(userId);
        StringBuilder text = new StringBuilder("⚽ Ваш состав:\n\n");

        // Отображаем игроков на каждой позиции
        text.append("1. GK (GK): ").append(squad.getPlayer("GK") != null ?
                squad.getPlayer("GK").getName() + " " + squad.getPlayer("GK").getCategory().getEmoji() + squad.getPlayer("GK").getRating() : "Пусто").append("\n");
        text.append("2. DEF (CB): ").append(squad.getPlayer("CB1") != null ?
                squad.getPlayer("CB1").getName() + " " + squad.getPlayer("CB1").getCategory().getEmoji() +  squad.getPlayer("CB1").getRating() : "Пусто").append("\n");
        text.append("3. DEF (CB): ").append(squad.getPlayer("CB2") != null ?
                squad.getPlayer("CB2").getName() + " " + squad.getPlayer("CB2").getCategory().getEmoji() +  squad.getPlayer("CB2").getRating() : "Пусто").append("\n");
        text.append("4. DEF (CB): ").append(squad.getPlayer("CB3") != null ?
                squad.getPlayer("CB3").getName() + " " + squad.getPlayer("CB3").getCategory().getEmoji() +  squad.getPlayer("CB3").getRating() : "Пусто").append("\n");
        text.append("5. MID (MID): ").append(squad.getPlayer("MID1") != null ?
                squad.getPlayer("MID1").getName() + " " + squad.getPlayer("MID1").getCategory().getEmoji() +  squad.getPlayer("MID1").getRating() : "Пусто").append("\n");
        text.append("6. MID (MID): ").append(squad.getPlayer("MID2") != null ?
                squad.getPlayer("MID2").getName() + " " + squad.getPlayer("MID2").getCategory().getEmoji() +  squad.getPlayer("MID2").getRating() : "Пусто").append("\n");
        text.append("7. MID (MID): ").append(squad.getPlayer("MID3") != null ?
                squad.getPlayer("MID3").getName() + " " + squad.getPlayer("MID3").getCategory().getEmoji() +  squad.getPlayer("MID3").getRating() : "Пусто").append("\n");
        text.append("8. FRW (FRW): ").append(squad.getPlayer("FRW1") != null ?
                squad.getPlayer("FRW1").getName() + " " + squad.getPlayer("FRW1").getCategory().getEmoji() +  squad.getPlayer("FRW1").getRating() : "Пусто").append("\n");
        text.append("9. FRW (FRW): ").append(squad.getPlayer("FRW2") != null ?
                squad.getPlayer("FRW2").getName() + " " + squad.getPlayer("FRW2").getCategory().getEmoji() +  squad.getPlayer("FRW2").getRating() : "Пусто").append("\n");
        text.append("10. FRW (FRW): ").append(squad.getPlayer("FRW3") != null ?
                squad.getPlayer("FRW3").getName() + " " + squad.getPlayer("FRW3").getCategory().getEmoji() +  squad.getPlayer("FRW3").getRating() : "Пусто").append("\n");
        text.append("11. EXTRA (EXTRA): ").append(squad.getPlayer("EXTRA") != null ?
                squad.getPlayer("EXTRA").getName() + " " + squad.getPlayer("EXTRA").getCategory().getEmoji()  + squad.getPlayer("EXTRA").getRating() : "Пусто").append("\n");

        // Общий рейтинг состава
        int totalRating = squad.calculateRating();
        text.append("\nОбщий рейтинг состава: ").append(totalRating);

        // Создаем клавиатуру с кнопками для выбора позиций
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопка для вратаря
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton gkButton = new InlineKeyboardButton();
        gkButton.setText("GK");
        gkButton.setCallbackData("squad_position_GK_" + userId);
        row1.add(gkButton);
        rows.add(row1);

        // Кнопки для защитников
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton cb1Button = new InlineKeyboardButton();
        cb1Button.setText("DEF");
        cb1Button.setCallbackData("squad_position_CB1_" + userId);
        row2.add(cb1Button);
        InlineKeyboardButton cb2Button = new InlineKeyboardButton();
        cb2Button.setText("DEF");
        cb2Button.setCallbackData("squad_position_CB2_" + userId);
        row2.add(cb2Button);
        InlineKeyboardButton cb3Button = new InlineKeyboardButton();
        cb3Button.setText("DEF");
        cb3Button.setCallbackData("squad_position_CB3_" + userId);
        row2.add(cb3Button);
        rows.add(row2);

        // Кнопки для полузащитников
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton mid1Button = new InlineKeyboardButton();
        mid1Button.setText("MID");
        mid1Button.setCallbackData("squad_position_MID1_" + userId);
        row3.add(mid1Button);
        InlineKeyboardButton mid2Button = new InlineKeyboardButton();
        mid2Button.setText("MID");
        mid2Button.setCallbackData("squad_position_MID2_" + userId);
        row3.add(mid2Button);
        InlineKeyboardButton mid3Button = new InlineKeyboardButton();
        mid3Button.setText("MID");
        mid3Button.setCallbackData("squad_position_MID3_" + userId);
        row3.add(mid3Button);
        rows.add(row3);

        // Кнопки для нападающих
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton frw1Button = new InlineKeyboardButton();
        frw1Button.setText("FRW");
        frw1Button.setCallbackData("squad_position_FRW1_" + userId);
        row4.add(frw1Button);
        InlineKeyboardButton frw2Button = new InlineKeyboardButton();
        frw2Button.setText("FRW");
        frw2Button.setCallbackData("squad_position_FRW2_" + userId);
        row4.add(frw2Button);
        InlineKeyboardButton frw3Button = new InlineKeyboardButton();
        frw3Button.setText("FRW");
        frw3Button.setCallbackData("squad_position_FRW3_" + userId);
        row4.add(frw3Button);
        rows.add(row4);

        // Кнопка для запасного
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        InlineKeyboardButton extraButton = new InlineKeyboardButton();
        extraButton.setText("EXTRA");
        extraButton.setCallbackData("squad_position_EXTRA_" + userId);
        row5.add(extraButton);
        rows.add(row5);

        // Кнопка "Назад"
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        backButton.setCallbackData("start_game_" + userId);
        backRow.add(backButton);
        rows.add(backRow);

        keyboard.setKeyboard(rows);
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    // Обработка выбора позиции в составе
    public static void handleSquadPosition(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId, String position) {
        int page = 1;
        String[] parts = position.split("_");
        if (parts.length > 1 && parts[parts.length - 2].equals("page")) {
            try {
                page = Integer.parseInt(parts[parts.length - 1]);
                position = position.substring(0, position.lastIndexOf("_page_"));
            } catch (NumberFormatException e) {
                logger.error("Ошибка при разборе номера страницы из позиции: {}", position, e);
                page = 1;
            }
        }

        // Получаем список игроков пользователя
        List<Player> players;
        try {
            players = db.getUserPlayers(userId);
        } catch (SQLException e) {
            logger.error("Ошибка при получении игроков для пользователя {}: {}", userId, e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при получении списка игроков.", null);
            return;
        }

        if (players == null || players.isEmpty()) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("squad_menu_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "У вас нет игроков для выбора.", keyboard);
            return;
        }

        // Фильтруем игроков, подходящих для выбранной позиции
        List<Player> availablePlayers = new ArrayList<>();
        for (Player player : players) {
            if (position.startsWith("GK") && player.getPosition().equals("GK")) {
                availablePlayers.add(player);
            } else if (position.startsWith("CB") && player.getPosition().equals("CB")) {
                availablePlayers.add(player);
            } else if (position.startsWith("MID") && player.getPosition().equals("MID")) {
                availablePlayers.add(player);
            } else if (position.startsWith("FRW") && player.getPosition().equals("FRW")) {
                availablePlayers.add(player);
            } else if (position.equals("EXTRA") && !player.getPosition().equals("GK")) {
                availablePlayers.add(player);
            }
        }

        if (availablePlayers.isEmpty()) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("squad_menu_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "У вас нет подходящих игроков для позиции " + position + ".", keyboard);
            return;
        }

        // Отображаем список игроков с пагинацией
        int pageSize = 5;
        String text = "Выберите игрока для позиции " + position + " (Страница " + page + "):";
        InlineKeyboardMarkup keyboard = createPlayerKeyboard(db, availablePlayers, "squad_select_" + position, userId, page, pageSize, position);
        editMessage(bot, chatId, messageId, text, keyboard);
    }

    // Обработка выбора игрока для позиции
    public static void handleSquadSelectPlayer(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId, String position, int playerId) {
        // Получаем данные игрока
        Player player;
        try {
            player = db.getPlayerById(playerId);
        } catch (SQLException e) {
            logger.error("Ошибка при получении игрока с ID {}: {}", playerId, e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при получении данных игрока.", null);
            return;
        }

        if (player == null) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("squad_menu_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Игрок не найден.", keyboard);
            return;
        }

        // Проверяем, подходит ли игрок для выбранной позиции
        boolean isValid = false;
        if (position.startsWith("GK") && player.getPosition().equals("GK")) {
            isValid = true;
        } else if (position.startsWith("CB") && player.getPosition().equals("CB")) {
            isValid = true;
        } else if (position.startsWith("MID") && player.getPosition().equals("MID")) {
            isValid = true;
        } else if (position.startsWith("FRW") && player.getPosition().equals("FRW")) {
            isValid = true;
        } else if (position.equals("EXTRA") && !player.getPosition().equals("GK")) {
            isValid = true;
        }

        if (!isValid) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("squad_menu_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Этот игрок не подходит для позиции " + position + ".", keyboard);
            return;
        }

        // Проверяем, не находится ли игрок уже в составе на другой позиции
        Squad squad = bot.getUserSquad(userId);
        boolean playerAlreadyInSquad = squad.getAllPlayers().stream()
                .anyMatch(p -> p != null && p.getId() == playerId && !squad.getPlayer(position).equals(p));

        if (playerAlreadyInSquad) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("squad_menu_" + userId);
            backRow.add(backButton);
            rows.add(backRow);
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Этот игрок уже есть в вашем составе на другой позиции!", keyboard);
            return;
        }

        // Устанавливаем игрока на позицию
        squad.setPlayer(position, player);
        handleSquadMenu(bot, db, chatId, messageId, userId);
    }

    public static void handleSquadEdit(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId) throws SQLException {
        // Создаем объект Squad для пользователя
        Squad squad = new Squad(db, userId);

        // Загружаем текущий состав из базы данных (предполагается, что db.saveUserSquad сохраняет состав, и он загружается в Squad при создании)
        List<Player> players = squad.getAllPlayers();
        List<String> positions = Squad.getPositions();

        // Формируем текст сообщения
        StringBuilder text = new StringBuilder("⚽ Ваш состав:\n\n");
        int totalRating = 0;
        int playerCount = 0;

        // Отображаем игроков по позициям
        for (int i = 0; i < positions.size(); i++) {
            String position = positions.get(i);
            Player player = players.get(i);
            if (player != null) {
                String displayPosition = position.startsWith("CB") ? "CB" :
                        position.startsWith("MID") ? "MID" :
                                position.startsWith("FRW") ? "FRW" : position;
                text.append(String.format("%d. %s: %s %d", i + 1, displayPosition, player.getName(), player.getRating()));

                // Добавляем эмодзи наград
                int rating = player.getRating();
                if (rating >= 90) {
                    text.append(" 💎");
                } else if (rating >= 85) {
                    text.append(" 🏆");
                }
                text.append("\n");

                totalRating += rating;
                playerCount++;
            }
        }

        // Вычисляем и добавляем итоговый рейтинг
        int averageRating = playerCount > 0 ? totalRating / playerCount : 0;
        text.append(String.format("\nИтоговый рейтинг: %d", averageRating));

        // Формируем клавиатуру
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопка для GK
        InlineKeyboardButton gkButton = new InlineKeyboardButton();
        gkButton.setText("GK");
        gkButton.setCallbackData("squad_position_goalkeeper_" + userId);
        rows.add(List.of(gkButton));

        // Кнопки для CB (CB1, CB2, CB3)
        List<InlineKeyboardButton> cbRow = new ArrayList<>();
        for (String pos : Arrays.asList("CB1", "CB2", "CB3")) {
            InlineKeyboardButton cbButton = new InlineKeyboardButton();
            cbButton.setText("CB");
            cbButton.setCallbackData("squad_position_defender_" + userId);
            cbRow.add(cbButton);
        }
        rows.add(cbRow);

        // Кнопки для MID (MID1, MID2, MID3)
        List<InlineKeyboardButton> midRow = new ArrayList<>();
        for (String pos : Arrays.asList("MID1", "MID2", "MID3")) {
            InlineKeyboardButton midButton = new InlineKeyboardButton();
            midButton.setText("MID");
            midButton.setCallbackData("squad_position_midfielder_" + userId);
            midRow.add(midButton);
        }
        rows.add(midRow);

        // Кнопки для FRW (FRW1, FRW2, FRW3)
        List<InlineKeyboardButton> frwRow = new ArrayList<>();
        for (String pos : Arrays.asList("FRW1", "FRW2", "FRW3")) {
            InlineKeyboardButton frwButton = new InlineKeyboardButton();
            frwButton.setText("FRW");
            frwButton.setCallbackData("squad_position_forward_" + userId);
            frwRow.add(frwButton);
        }
        rows.add(frwRow);

        // Кнопка для EXTRA
        InlineKeyboardButton extraButton = new InlineKeyboardButton();
        extraButton.setText("EXTRA");
        extraButton.setCallbackData("squad_position_extra_" + userId);
        rows.add(List.of(extraButton));

        // Кнопка "Назад"
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("Назад");
        backButton.setCallbackData("start_game_" + userId);
        rows.add(List.of(backButton));

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(rows);
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    public static void handleMyPacks(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId) {
        try {
            List<Pack> userPacks = db.getUserPacks(userId);
            StringBuilder text = new StringBuilder("📦 **Мои паки**:\n\n");
            if (userPacks.isEmpty()) {
                text.append("У вас пока нет паков.");
            } else {
                for (Pack pack : userPacks) {
                    text.append(String.format("📦 %s — %d игроков\n", pack.getName(), pack.getPlayerCount()));
                }
            }

            InlineKeyboardMarkup keyboard = createBackKeyboard("packs_menu_" + userId);
            editMessage(bot, chatId, messageId, text.toString(), keyboard);
        } catch (SQLException e) {
            logger.error("Ошибка получения паков пользователя {}: {}", userId, e.getMessage(), e);
            InlineKeyboardMarkup keyboard = createBackKeyboard("packs_menu_" + userId);
            editMessage(bot, chatId, messageId, "Ошибка при загрузке паков.", keyboard);
        }
    }

    public static void handleMoneyPacks(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId) {
        try {
            // Получаем паки, доступные за доллары (предполагаю, что в Pack есть поле для валюты или фильтр)
            List<Pack> availablePacks = db.getAvailablePacks(); // Нужно реализовать в DatabaseManager
            StringBuilder text = new StringBuilder("💵 **Паки за доллары**:\n\n");
            if (availablePacks.isEmpty()) {
                text.append("Нет доступных паков.");
            } else {
                for (Pack pack : availablePacks) {
                    text.append(String.format("📦 %s — %d долларов\n", pack.getName(), pack.getPrice()));
                }
            }

            InlineKeyboardMarkup keyboard = createMoneyPacksKeyboard(availablePacks, userId);
            editMessage(bot, chatId, messageId, text.toString(), keyboard);
        } catch (SQLException e) {
            logger.error("Ошибка получения паков за доллары: {}", e.getMessage(), e);
            InlineKeyboardMarkup keyboard = createBackKeyboard("packs_menu_" + userId);
            editMessage(bot, chatId, messageId, "Ошибка при загрузке паков.", keyboard);
        }
    }

    private static InlineKeyboardMarkup createMoneyPacksKeyboard(List<Pack> packs, long userId) {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        for (Pack pack : packs) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(pack.getName() + " (" + pack.getPrice() + "💵)");
            button.setCallbackData("buy_pack_" + pack.getId() + "_" + userId);
            keyboard.add(List.of(button));
        }
        // Добавляем кнопку "Назад"
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅ Назад");
        backButton.setCallbackData("packs_menu_" + userId);
        keyboard.add(List.of(backButton));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        return markup;
    }


    // Вспомогательные методы (предполагаю, что они уже есть, но для полноты)
    private static InlineKeyboardMarkup createBackKeyboard(String callbackData) {
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅ Назад");
        backButton.setCallbackData(callbackData);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(backButton)));
        return markup;
    }

    public static void sendClanNotification(TelegramBot bot, DatabaseManager db, int clanId, String message) {
        try {
            List<User> members = db.getClanMembers(clanId);
            if (members == null || members.isEmpty()) {
                logger.warn("Клан с ID {} не имеет участников для уведомления.", clanId);
                return;
            }

            for (User member : members) {
                SendMessage notification = new SendMessage();
                notification.setChatId(String.valueOf(member.getId()));
                notification.setText("<b>Уведомление от клана:</b>\n" + message);
                notification.setParseMode("HTML");
                try {
                    bot.execute(notification);
                } catch (Exception e) {
                    logger.error("Не удалось отправить уведомление пользователю {}: {}", member.getId(), e.getMessage(), e);
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка при получении членов клана {}: {}", clanId, e.getMessage(), e);
        }
    }

    // Проверка прав доступа в клане
    public static boolean checkClanPermission(DatabaseManager db, long userId, int clanId, boolean ownerOnly) {
        try {
            Clan clan = db.getClanById(clanId);
            if (clan == null) {
                logger.error("Клан с ID {} не найден.", clanId);
                return false;
            }
            if (ownerOnly && clan.getOwnerId() != userId) {
                logger.warn("Пользователь {} не является владельцем клана {}.", userId, clanId);
                return false;
            }
            // Проверяем, состоит ли пользователь в клане
            List<User> members = db.getClanMembers(clanId);
            return members.stream().anyMatch(member -> member.getId() == userId);
        } catch (SQLException e) {
            logger.error("Ошибка при проверке прав доступа для пользователя {} в клане {}: {}", userId, clanId, e.getMessage(), e);
            return false;
        }
    }

    // Форматирование списка игроков
    public static String formatPlayerList(List<Player> players, boolean includeRating) {
        StringBuilder text = new StringBuilder();
        if (players == null || players.isEmpty()) {
            text.append("Игроки отсутствуют.");
        } else {
            for (Player player : players) {
                text.append("- ").append(player.getName()).append(" (").append(player.getPosition()).append(")");
                if (includeRating) {
                    text.append(" - ").append(player.getRating());
                }
                text.append("\n");
            }
        }
        return text.toString();
    }

    // Обработка выхода из клана
    public static void handleLeaveClan(TelegramBot bot, DatabaseManager db, long chatId, int messageId, long userId) {
        try {
            Clan clan = db.getUserClan(userId);
            if (clan == null) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("Назад");
                backButton.setCallbackData("clans_menu_" + userId);
                rows.add(List.of(backButton));
                keyboard.setKeyboard(rows);
                editMessage(bot, chatId, messageId, "Вы не состоите в клане.", keyboard);
                return;
            }

            if (clan.getOwnerId() == userId) {
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                InlineKeyboardButton backButton = new InlineKeyboardButton();
                backButton.setText("Назад");
                backButton.setCallbackData("clans_menu_" + userId);
                rows.add(List.of(backButton));
                keyboard.setKeyboard(rows);
                editMessage(bot, chatId, messageId, "Вы являетесь владельцем клана. Назначьте нового владельца или расформируйте клан.", keyboard);
                return;
            }

            db.leaveClan(userId, clan.getId());
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("Назад");
            backButton.setCallbackData("clans_menu_" + userId);
            rows.add(List.of(backButton));
            keyboard.setKeyboard(rows);
            editMessage(bot, chatId, messageId, "Вы успешно покинули клан " + clan.getName() + ".", keyboard);

            // Уведомляем членов клана
            sendClanNotification(bot, db, clan.getId(), "Пользователь " + db.getUserById(userId).getUsername() + " покинул клан.");
        } catch (SQLException e) {
            logger.error("Ошибка при выходе пользователя {} из клана: {}", userId, e.getMessage(), e);
            editMessage(bot, chatId, messageId, "Ошибка при выходе из клана.", null);
        }
    }
    
}