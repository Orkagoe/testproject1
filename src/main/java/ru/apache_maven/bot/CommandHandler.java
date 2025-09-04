package ru.apache_maven.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.apache_maven.db.DatabaseManager;
import ru.apache_maven.model.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(CommandHandler.class);
    private final DatabaseManager db;
    private final TelegramBot bot;

    public CommandHandler(DatabaseManager db, TelegramBot bot) {
        this.db = db;
        this.bot = bot;
    }

    public void handleCommand(Update update, String command, long chatId, long userId, String text) {
        logger.info("Handling command: {} from userId={}", command, userId);
        try {
            // Проверяем команды, которые требуют наличия reply-сообщения
            if (text.equalsIgnoreCase(".футпенки") && update.getMessage().getReplyToMessage() != null) {
                handlePenaltyCommand(update, chatId, userId);
            } else if (text.equalsIgnoreCase(".футминуспенка") && userId == 5029600728L) {
                handleCancelPenaltyCommand(chatId);
            } else if (text.equalsIgnoreCase(".футжоб") && update.getMessage().getReplyToMessage() != null) {
                handleFootjob(update, chatId, userId);
            } else if (text.equalsIgnoreCase(".футподарок") && update.getMessage().getReplyToMessage() != null) {
                handleGiftCommand(update, chatId, userId);
            } else if (text.equalsIgnoreCase(".футдрузья") && update.getMessage().getReplyToMessage() != null) {
                handleAddFriendCommand(update, chatId, userId);
            } else {
                // Обрабатываем остальные команды через switch
                switch (command.toLowerCase()) {
                    case "футстарт":
                        sendStartMessage(chatId, userId);
                        break;
                    case "/getcard":
                        handleGetCard(chatId, userId);
                        break;
                    case ".футпрофиль":
                        handleProfile(chatId, userId);
                        break;
                    case ".футпаки":
                        handleUserPacks(chatId, userId);
                        break;
                    case ".футтрейд":
                        handleTradeCommand(chatId, userId);
                        break;
                    case ".футсостав":
                        handleSquadCommand(chatId, userId);
                        break;
                    case ".футдрузья":
                        handleFriendsCommand(chatId, userId);
                        break;
                    default:
                        // Игнорируем неизвестные команды
                        break;
                }
            }
        } catch (SQLException e) {
            logger.error("SQL error while handling command {}: {}", command, e.getMessage(), e);
            Utils.sendMessage(bot, chatId, "Ошибка базы данных при выполнении команды.", null);
        }
    }

    private void handleProfile(long chatId, long userId) throws SQLException {
        User user = db.getUserById(userId);
        if (user == null) {
            Utils.sendMessage(bot, chatId, "Пользователь не найден.", null);
            return;
        }

        int dollars = db.getUserDollars(userId);
        int userCardsCount = db.getUserPlayers(userId).size();
        int totalCardsCount = db.getTotalCardsCount();
        Player favoriteCard = db.getFavoriteCard(userId);
        String favoriteCardText = favoriteCard != null ? favoriteCard.getName() + " #" + favoriteCard.getId() : "Не выбрана";
        long points = user.getPoints();
        String title = user.getTitle();
        String premiumStatus = db.getGiftPackClaims(userId) > 0 ? "АКТИВЕН" : "НЕ АКТИВЕН";

        StringBuilder profileText = new StringBuilder("👤 Твой профиль:\n\n");
        profileText.append("💰 Баланс: ").append(dollars).append(" $\n");
        profileText.append("📜 Коллекция: ").append(userCardsCount).append(" / ").append(totalCardsCount).append(" карточек\n");
        profileText.append("❤️ Любимая карта: ").append(favoriteCardText).append("\n\n");
        profileText.append("📊 Прогресс:\n");
        profileText.append("🏆 Очки: ").append(points).append("\n");
        profileText.append("🎖️ Титул: ").append(title).append("\n");
        profileText.append("💎 ПРЕМИУМ: ").append(premiumStatus);

        Utils.sendMessage(bot, chatId, profileText.toString(), null);
    }

    private void handleFootjob(Update update, long chatId, long userId) throws SQLException {
        Message message = update.getMessage();
        if (message.getReplyToMessage() == null) {
            Utils.sendMessage(bot, chatId, "Эта команда работает только в ответ на сообщение другого пользователя!", null);
            return;
        }

        long targetUserId = message.getReplyToMessage().getFrom().getId();
        if (targetUserId == userId) {
            Utils.sendMessage(bot, chatId, "Нельзя сделать футжоб самому себе! 😅", null);
            return;
        }

        User user = db.getUserById(userId);
        User targetUser = db.getUserById(targetUserId);
        if (user == null || targetUser == null) {
            Utils.sendMessage(bot, chatId, "Один из пользователей не найден.", null);
            return;
        }

        db.addFootjob(userId, targetUserId);
        int totalFootjobs = db.getTotalFootjobsCount();
        String messageText = String.format("%s сделал футжоб для %s и он кончил 😈\nФутжобов всего - %d",
                user.getUsername(), targetUser.getUsername(), totalFootjobs);
        Utils.sendMessage(bot, chatId, messageText, null);
    }

    private void sendStartMessage(long chatId, long userId) {
        String text = "Добро пожаловать в игру! 🎮\n\n" +
                "Нажмите 'Начать игру!', чтобы открыть главное меню.";
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton("Начать игру!", "start_game_" + userId)));
        keyboard.setKeyboard(rows);
        Utils.sendMessage(bot, chatId, text, keyboard);
    }

    private void handleGetCard(long chatId, long userId) throws SQLException {
        Player player = db.getRandomPlayer();
        if (player == null) {
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            rows.add(List.of(createButton("Назад", "start_game_" + userId)));
            keyboard.setKeyboard(rows);
            Utils.sendMessage(bot, chatId, "Не удалось найти игрока.", keyboard);
            return;
        }

        boolean isDuplicate = db.checkUserHasPlayer(userId, player.getId());
        db.addPlayerToUser(userId, player);
        db.addPoints(userId, player.getCategory().getPoints());

        if (isDuplicate) {
            int dollarsToAdd = player.getCategory().getDollars();
            db.addDollars(userId, dollarsToAdd);
            String messageText = "Реплика! 🃏\n" +
                    "Ты уже имеешь эту карточку. Получено: " + dollarsToAdd + " долларов.";
            Utils.sendMessage(bot, chatId, messageText, null);
        }

        Utils.sendPlayerCard(bot, db, chatId, player, player.getCategory().getPoints());
    }

    private void handlePenaltyCommand(Update update, long chatId, long userId) {
        if (bot.getCurrentGame() != null) {
            Utils.sendMessage(bot, chatId, "Уже играем!", null);
            return;
        }

        long opponentId = update.getMessage().getReplyToMessage().getFrom().getId();
        String opponentUsername = update.getMessage().getReplyToMessage().getFrom().getFirstName();
        String challengerUsername = update.getMessage().getFrom().getFirstName();

        if (userId == opponentId) {
            Utils.sendMessage(bot, chatId, "Нельзя бросить вызов самому себе!", null);
            return;
        }

        TelegramBot.PenaltyGame game = new TelegramBot.PenaltyGame(userId, opponentId, challengerUsername, opponentUsername, chatId);
        bot.setCurrentGame(game);
        Utils.sendMessage(bot, chatId, "Ставки приняты! Начинаем матч!", null);
        Utils.sendKickDirectionMessage(bot, chatId, userId, challengerUsername);
    }

    private void handleCancelPenaltyCommand(long chatId) {
        if (bot.getCurrentGame() == null) {
            Utils.sendMessage(bot, chatId, "В данный момент никто не играет в серии пенальти", null);
        } else {
            bot.setCurrentGame(null);
            Utils.sendMessage(bot, chatId, "Игра остановлена администратором", null);
        }
    }

    private void handleUserPacks(long chatId, long userId) throws SQLException {
        List<Pack> userPacks = db.getUserPacks(userId);
        if (userPacks.isEmpty()) {
            Utils.sendMessage(bot, chatId, "У вас нет паков. Купите их в магазине!", null);
            return;
        }

        StringBuilder packsText = new StringBuilder("🎒 Ваши паки:\n\n");
        for (Pack pack : userPacks) {
            packsText.append(String.format("📦 %s (x%d)\n", pack.getName(), pack.getQuantity()));
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Pack pack : userPacks) {
            rows.add(List.of(createButton("Открыть " + pack.getName(), "open_pack_" + pack.getId() + "_" + userId)));
        }
        rows.add(List.of(createButton("Назад", "start_game_" + userId)));
        keyboard.setKeyboard(rows);

        Utils.sendMessage(bot, chatId, packsText.toString(), keyboard);
    }

    private void handleTradeCommand(long chatId, long userId) throws SQLException {
        User user = db.getUserById(userId);
        if (user == null) {
            Utils.sendMessage(bot, chatId, "Пользователь не найден.", null);
            return;
        }

        List<Player> userPlayers = db.getUserPlayers(userId);
        if (userPlayers.isEmpty()) {
            Utils.sendMessage(bot, chatId, "У вас нет карточек для продажи.", null);
            return;
        }

        String text = "🏪 Глобальный рынок игроков\nВыберите действие:";
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("Глобальный маркет", "trade_market_" + userId),
                createButton("Продать игроков", "trade_sell_" + userId)
        ));
        rows.add(List.of(
                createButton("Мои продажи", "trade_my_sales_" + userId)
        ));
        rows.add(List.of(
                createButton("Назад", "start_game_" + userId)
        ));
        keyboard.setKeyboard(rows);

        Utils.sendMessage(bot, chatId, text, keyboard);
    }

    private void handleSquadCommand(long chatId, long userId) throws SQLException {
        User user = db.getUserById(userId);
        if (user == null) {
            Utils.sendMessage(bot, chatId, "Пользователь не найден.", null);
            return;
        }

        String text = "⚽ Загрузка состава...";
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton("Показать состав", "squad_menu_" + userId)));
        keyboard.setKeyboard(rows);
        Utils.sendMessage(bot, chatId, text, keyboard);
    }

    private void handleGiftCommand(Update update, long chatId, long userId) throws SQLException {
        Message message = update.getMessage();
        long targetUserId = message.getReplyToMessage().getFrom().getId();
        if (targetUserId == userId) {
            Utils.sendMessage(bot, chatId, "Нельзя подарить игрока самому себе!", null);
            return;
        }

        User user = db.getUserById(userId);
        User targetUser = db.getUserById(targetUserId);
        if (user == null || targetUser == null) {
            Utils.sendMessage(bot, chatId, "Один из пользователей не найден.", null);
            return;
        }

        List<Player> userPlayers = db.getUserPlayers(userId);
        if (userPlayers.isEmpty()) {
            Utils.sendMessage(bot, chatId, "У вас нет игроков для подарка.", null);
            return;
        }

        StringBuilder text = new StringBuilder(String.format("🎁 Выберите игрока для подарка пользователю %s:\n\n", targetUser.getUsername()));
        for (Player player : userPlayers) {
            text.append(String.format("- %s (%s, %d)\n", player.getName(), player.getCategory(), player.getRating()));
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Player player : userPlayers) {
            rows.add(List.of(createButton(
                    player.getName(),
                    "gift_select_" + player.getId() + "_" + targetUserId + "_" + userId
            )));
        }
        rows.add(List.of(createButton("Назад", "start_game_" + userId)));
        keyboard.setKeyboard(rows);

        Utils.sendMessage(bot, chatId, text.toString(), keyboard);
    }

    private void handleFriendsCommand(long chatId, long userId) throws SQLException {
        User user = db.getUserById(userId);
        if (user == null) {
            Utils.sendMessage(bot, chatId, "Пользователь не найден.", null);
            return;
        }

        int page = 1; // Начальная страница
        int pageSize = 10; // 10 друзей на страницу
        List<User> friends = db.getUserFriends(userId, page, pageSize);
        StringBuilder text = new StringBuilder("👥 Ваши друзья:\n\n");
        if (friends.isEmpty()) {
            text.append("У вас пока нет друзей. Ответьте на сообщение пользователя командой .футдрузья, чтобы добавить его.");
        } else {
            for (User friend : friends) {
                text.append(String.format("- %s\n", friend.getUsername()));
            }
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (User friend : friends) {
            rows.add(List.of(createButton(
                    String.format("Удалить %s", friend.getUsername()),
                    "friends_remove_" + friend.getId() + "_" + userId
            )));
        }

        // Кнопки пагинации
        List<InlineKeyboardButton> paginationRow = new ArrayList<>();
        if (page > 1) {
            paginationRow.add(createButton("⬅ Назад", "friends_list_page_" + (page - 1) + "_" + userId));
        }
        if (friends.size() == pageSize) {
            paginationRow.add(createButton("Вперёд ➡", "friends_list_page_" + (page + 1) + "_" + userId));
        }
        if (!paginationRow.isEmpty()) {
            rows.add(paginationRow);
        }

        rows.add(List.of(createButton("Назад", "start_game_" + userId)));
        keyboard.setKeyboard(rows);

        Utils.sendMessage(bot, chatId, text.toString(), keyboard);
    }

    private void handleAddFriendCommand(Update update, long chatId, long userId) throws SQLException {
        Message message = update.getMessage();
        if (message.getReplyToMessage() == null || message.getReplyToMessage().getFrom() == null) {
            Utils.sendMessage(bot, chatId, "Ответьте на сообщение пользователя, чтобы добавить его в друзья.", null);
            return;
        }
        long friendId = message.getReplyToMessage().getFrom().getId();
        if (friendId == userId) {
            Utils.sendMessage(bot, chatId, "Нельзя добавить себя в друзья.", null);
            return;
        }

        User user = db.getUserById(userId);
        User friend = db.getUserById(friendId);
        if (user == null || friend == null) {
            Utils.sendMessage(bot, chatId, "Пользователь не найден.", null);
            return;
        }

        // Проверяем, существует ли входящий запрос на дружбу от friendId к userId
        boolean hasPendingRequest = db.hasPendingFriendRequest(userId, friendId);
        if (hasPendingRequest) {
            // Принимаем запрос на дружбу
            try {
                db.acceptFriend(userId, friendId);
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                rows.add(List.of(createButton("Назад", "friends_list_" + userId)));
                keyboard.setKeyboard(rows);
                Utils.sendMessage(bot, chatId, "✅ Вы приняли запрос дружбы от @" + friend.getUsername() + "!", keyboard);
                Utils.sendMessage(bot, friendId, "🎉 Пользователь @" + user.getUsername() + " принял ваш запрос дружбы!", null);
            } catch (SQLException e) {
                logger.error("Failed to accept friend request: userId={}, friendId={}", userId, friendId, e);
                Utils.sendMessage(bot, chatId, "Ошибка при принятии запроса дружбы.", null);
            }
        } else {
            // Проверяем, не являются ли пользователи уже друзьями или не отправлен ли запрос


            // Отправляем новый запрос на дружбу
            try {
                db.addFriend(userId, friendId);
                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                rows.add(List.of(createButton("Назад", "friends_list_" + userId)));
                keyboard.setKeyboard(rows);
                Utils.sendMessage(bot, chatId, "📩 Запрос дружбы отправлен пользователю @" + friend.getUsername() + ".", keyboard);
                Utils.sendMessage(bot, friendId, "📩 Пользователь @" + user.getUsername() + " хочет добавить вас в друзья. Ответьте .футдрузья на его сообщение для принятия.", null);
            } catch (SQLException e) {
                logger.error("Failed to add friend: userId={}, friendId={}", userId, friendId, e);
                Utils.sendMessage(bot, chatId, "Ошибка при отправке запроса дружбы.", null);
            }
        }
    }

    private static InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}