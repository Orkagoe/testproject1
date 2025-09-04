package ru.apache_maven.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.apache_maven.db.DatabaseManager;
import ru.apache_maven.model.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static ru.apache_maven.bot.Utils.editMessage;
import static ru.apache_maven.bot.Utils.sendMessage;

public class CallbackHandler {
    private static final Logger logger = LoggerFactory.getLogger(CallbackHandler.class);
    private final DatabaseManager db;
    private final TelegramBot bot;

    public CallbackHandler(DatabaseManager db, TelegramBot bot) {
        this.db = db;
        this.bot = bot;
    }

    public void handleCallback(CallbackQuery callbackQuery) throws SQLException {
        long userId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();

        if (!(callbackQuery.getMessage() instanceof Message)) {
            logger.error("Inaccessible message in CallbackQuery from userId: {}", userId);
            sendMessage(bot, userId, "Сообщение недоступно.", null);
            return;
        }

        Message message = (Message) callbackQuery.getMessage();
        long chatId = message.getChatId();
        int messageId = message.getMessageId();

        logger.info("Callback: data={}, chatId={}, messageId={}, userId={}", data, chatId, messageId, userId);

        String[] parts = data.split("_");
        if (parts.length < 2) {
            logger.error("Invalid CallbackData: {}", data);
            editMessage(bot, chatId, messageId, "Ошибка: Неверный формат.", null);
            return;
        }

        long callbackUserId;
        try {
            callbackUserId = Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            logger.error("Invalid userId in CallbackData: {}", data, e);
            editMessage(bot, chatId, messageId, "Ошибка: Неверный userId.", null);
            return;
        }

        if (userId != callbackUserId) {
            logger.warn("User {} tried button for {}", userId, callbackUserId);
            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer = new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            answer.setText("Эта кнопка не для вас!");
            answer.setShowAlert(true);
            try {
                bot.execute(answer);
            } catch (Exception e) {
                logger.error("Failed to send alert to {}: {}", userId, e.getMessage(), e);
            }
            return;
        }

        switch (parts[0]) {
            case "start":
                if (parts[1].equals("game")) {
                    handleStartGame(chatId, messageId, userId);
                }
                break;
            case "open":
                if (parts[1].equals("pack")) {
                    try {
                        int packId = Integer.parseInt(parts[2]);
                        handleOpenPack(chatId, messageId, userId, packId);
                    } catch (NumberFormatException e) {
                        logger.error("Invalid packId: {}", data, e);
                        editMessage(bot, chatId, messageId, "Ошибка: Неверный ID пака.", null);
                    }
                }
                break;
            case "penalty":
                if (parts[1].equals("kick") && parts.length >= 3) {
                    Utils.handlePenaltyKick(bot, db, chatId, messageId, userId, parts[2], callbackQuery);
                } else {
                    logger.error("Invalid penalty_kick data: {}", data);
                    editMessage(bot, chatId, messageId, "Ошибка: Неверный формат.", null);
                }
                break;
            case "trade":
                switch (parts[1]) {
                    case "market":
                        if (parts.length > 2 && parts[2].equals("page")) {
                            try {
                                int page = Integer.parseInt(parts[3]);
                                handleGlobalMarket(chatId, messageId, userId, page);
                            } catch (NumberFormatException e) {
                                logger.error("Invalid page number: {}", data, e);
                                editMessage(bot, chatId, messageId, "Ошибка: Неверный номер страницы.", null);
                            }
                        } else {
                            handleGlobalMarket(chatId, messageId, userId, 1);
                        }
                        break;
                    case "sell":
                        handleSellPlayers(chatId, messageId, userId);
                        break;
                    case "my":
                        if (parts[2].equals("sales")) {
                            handleMySales(chatId, messageId, userId);
                        }
                        break;
                    case "buy":
                        try {
                            long marketId = Long.parseLong(parts[2]);
                            handleBuyPlayer(chatId, messageId, userId, marketId);
                        } catch (NumberFormatException e) {
                            logger.error("Invalid marketId: {}", data, e);
                            editMessage(bot, chatId, messageId, "Ошибка: Неверный ID маркета.", null);
                        }
                        break;
                    case "list":
                        try {
                            int playerId = Integer.parseInt(parts[2]);
                            handleListPlayer(chatId, messageId, userId, playerId);
                        } catch (NumberFormatException e) {
                            logger.error("Invalid playerId: {}", data, e);
                            editMessage(bot, chatId, messageId, "Ошибка: Неверный ID игрока.", null);
                        }
                        break;
                    case "remove":
                        try {
                            long marketId = Long.parseLong(parts[2]);
                            handleRemovePlayer(chatId, messageId, userId, marketId);
                        } catch (NumberFormatException e) {
                            logger.error("Invalid marketId: {}", data, e);
                            editMessage(bot, chatId, messageId, "Ошибка: Неверный ID маркета.", null);
                        }
                        break;
                    case "confirm":
                        handleConfirmSale(chatId, messageId, userId);
                        break;
                    default:
                        logger.warn("Unknown trade callback: {}", data);
                        editMessage(bot, chatId, messageId, "Неизвестная команда.", null);
                        break;
                }
                break;
            case "packs":
                switch (parts[1]) {
                    case "menu":
                        handlePacksMenu(chatId, messageId, userId);
                        break;
                    case "my":
                        handleMyPacks(chatId, messageId, userId);
                        break;
                    case "money":
                        handleMoneyPacks(chatId, messageId, userId);
                        break;
                    case "buy":
                        try {
                            int packId = Integer.parseInt(parts[2]);
                            handleBuyPack(chatId, messageId, userId, packId);
                        } catch (NumberFormatException e) {
                            logger.error("Invalid packId: {}", data, e);
                            editMessage(bot, chatId, messageId, "Ошибка: Неверный ID пака.", null);
                        }
                        break;
                    default:
                        logger.warn("Unknown packs callback: {}", data);
                        editMessage(bot, chatId, messageId, "Неизвестная команда.", null);
                        break;
                }
                break;
            case "squad":
                switch (parts[1]) {
                    case "menu":
                        handleSquadMenu(chatId, messageId, userId);
                        break;
                    case "position":
                        if (parts.length >= 3) {
                            handleSelectPosition(chatId, messageId, userId, parts[2]);
                        } else {
                            logger.error("Invalid squad_position data: {}", data);
                            editMessage(bot, chatId, messageId, "Ошибка: Неверный формат.", null);
                        }
                        break;
                    case "select":
                        try {
                            int playerId = Integer.parseInt(parts[2]);
                            String position = parts[3];
                            handleSelectPlayer(chatId, messageId, userId, position, playerId);
                        } catch (NumberFormatException e) {
                            logger.error("Invalid playerId: {}", data, e);
                            editMessage(bot, chatId, messageId, "Ошибка: Неверный ID игрока.", null);
                        }
                        break;
                    default:
                        logger.warn("Unknown squad callback: {}", data);
                        editMessage(bot, chatId, messageId, "Неизвестная команда.", null);
                        break;
                }
                break;
            case "gift":
                if (parts[1].equals("select")) {
                    try {
                        int playerId = Integer.parseInt(parts[2]);
                        long targetUserId = Long.parseLong(parts[3]);
                        handleGiftPlayer(chatId, messageId, userId, playerId, targetUserId);
                    } catch (NumberFormatException e) {
                        logger.error("Invalid gift data: {}", data, e);
                        editMessage(bot, chatId, messageId, "Ошибка: Неверный формат.", null);
                    }
                }
                break;
            case "friends":
                switch (parts[1]) {
                    case "list":
                        if (parts.length > 2 && parts[2].equals("page")) {
                            try {
                                int page = Integer.parseInt(parts[3]);
                                handleFriendsList(chatId, messageId, userId, page);
                            } catch (NumberFormatException e) {
                                logger.error("Invalid page number: {}", data, e);
                                editMessage(bot, chatId, messageId, "Ошибка: Неверный номер страницы.", null);
                            }
                        } else {
                            handleFriendsList(chatId, messageId, userId, 1);
                        }
                        break;
                    case "remove":
                        try {
                            long friendId = Long.parseLong(parts[2]);
                            handleRemoveFriend(chatId, messageId, userId, friendId);
                        } catch (NumberFormatException e) {
                            logger.error("Invalid friendId: {}", data, e);
                            editMessage(bot, chatId, messageId, "Ошибка: Неверный ID друга.", null);
                        }
                        break;
                    default:
                        logger.warn("Unknown friends callback: {}", data);
                        editMessage(bot, chatId, messageId, "Неизвестная команда.", null);
                        break;
                }
                break;
            default:
                logger.warn("Unknown callback: {}", data);
                editMessage(bot, chatId, messageId, "Неизвестная команда.", null);
                break;
        }
    }

    private void handleStartGame(long chatId, int messageId, long userId) {
        String text = "🎮 Главное меню:\nВыберите действие:";
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("Мой профиль", "profile_" + userId),
                createButton("Мои паки", "packs_my_" + userId)
        ));
        rows.add(List.of(
                createButton("Получить карточку", "get_card_" + userId),
                createButton("Глобальный рынок", "trade_market_" + userId)
        ));
        rows.add(List.of(
                createButton("Мой состав", "squad_menu_" + userId),
                createButton("Друзья", "friends_list_" + userId)
        ));
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(rows);
        editMessage(bot, chatId, messageId, text, keyboard);
    }

    private void handleOpenPack(long chatId, int messageId, long userId, int packId) throws SQLException {
        Pack pack = db.getPackById(packId);
        if (pack == null || pack.getId() == 0) {
            logger.error("Pack not found: packId={}", packId);
            editMessage(bot, chatId, messageId, "Ошибка: Пак не найден.", null);
            return;
        }

        int packQuantity = db.getUserPackQuantity(userId, packId);
        if (packQuantity <= 0) {
            logger.warn("User {} does not have packId={}", userId, packId);
            editMessage(bot, chatId, messageId, "У вас нет этого пака.", null);
            return;
        }

        db.decrementUserPackQuantity(userId, packId);
        String packName = pack.getName() != null ? pack.getName() : "Неизвестный пак";
        StringBuilder text = new StringBuilder("🎉 Вы открыли пак " + packName + "!\nПолученные игроки:\n");
        for (int i = 0; i < pack.getPlayerCount(); i++) {
            Player player = pack.getCategory() != null ? db.getRandomPlayerByCategory(pack.getCategory()) : db.getRandomPlayer();
            if (player != null) {
                db.addPlayerToUser(userId, player);
                text.append(String.format("- %s (%s, %d)\n", player.getName(), player.getPosition(), player.getRating()));
            }
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(List.of(createButton("Назад", "packs_my_" + userId))));
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    private void handlePacksMenu(long chatId, int messageId, long userId) {
        String text = "📦 Меню паков\nВыберите действие:";
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                createButton("Мои паки", "packs_my_" + userId),
                createButton("Паки за деньги", "packs_money_" + userId)
        ));
        rows.add(List.of(createButton("Назад", "start_game_" + userId)));
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(rows);
        editMessage(bot, chatId, messageId, text, keyboard);
    }

    private void handleMyPacks(long chatId, int messageId, long userId) throws SQLException {
        List<Pack> userPacks = db.getUserPacks(userId);
        StringBuilder text = new StringBuilder("📦 Ваши паки:\n\n");
        if (userPacks.isEmpty()) {
            text.append("У вас пока нет паков.");
        } else {
            for (Pack pack : userPacks) {
                String packName = pack.getName() != null ? pack.getName() : "Неизвестный пак";
                text.append(String.format("%s - %d шт.\n", packName, pack.getQuantity()));
            }
        }
        editMessage(bot, chatId, messageId, text.toString(), createPackKeyboard(userPacks, userId));
    }

    private void handleMoneyPacks(long chatId, int messageId, long userId) throws SQLException {
        List<Pack> availablePacks = db.getAvailablePacks();
        int userDollars = db.getUserDollars(userId);
        StringBuilder text = new StringBuilder(String.format("💰 Доступные паки (Баланс: %d 💵):\n\n", userDollars));
        if (availablePacks.isEmpty()) {
            text.append("Нет паков для покупки.");
        } else {
            for (Pack pack : availablePacks) {
                if (pack.getId() == 0 || pack.getName() == null || pack.getPrice() == 0) {
                    logger.error("Invalid pack: id={}, name={}, price={}", pack.getId(), pack.getName(), pack.getPrice());
                    continue;
                }
                text.append(String.format("📦 %s\nЦена: %d 💵 | Игроков: %d | Категория: %s\n%s\n\n",
                        pack.getName(), pack.getPrice(), pack.getPlayerCount(), pack.getCategory(), pack.getDescription() != null ? pack.getDescription() : ""));
            }
        }
        editMessage(bot, chatId, messageId, text.toString(), createBuyPackKeyboard(availablePacks, userId));
    }

    private void handleBuyPack(long chatId, int messageId, long userId, int packId) throws SQLException {
        Pack pack = db.getPackById(packId);
        if (pack == null || pack.getId() == 0) {
            logger.error("Pack not found: packId={}", packId);
            editMessage(bot, chatId, messageId, "Ошибка: Пак не найден.", null);
            return;
        }

        int userDollars = db.getUserDollars(userId);
        if (userDollars < pack.getPrice()) {
            String errorMessage = String.format("Недостаточно долларов. Баланс: %d 💵, нужно: %d 💵", userDollars, pack.getPrice());
            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(List.of(createButton("Назад", "packs_money_" + userId))));
            editMessage(bot, chatId, messageId, errorMessage, keyboard);
            return;
        }

        db.buyPack(userId, packId);
        String packName = pack.getName() != null ? pack.getName() : "Неизвестный пак";
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(List.of(createButton("Назад", "packs_money_" + userId))));
        editMessage(bot, chatId, messageId, "✅ Куплен пак " + packName + "!", keyboard);
    }

    private void handleGlobalMarket(long chatId, int messageId, long userId, int page) throws SQLException {
        int pageSize = 10; // 10 игроков на страницу
        List<MarketEntry> marketEntries = db.getMarketPlayers(page, pageSize);
        StringBuilder text = new StringBuilder("🏪 Глобальный рынок игроков:\n\n");
        if (marketEntries.isEmpty()) {
            text.append("На рынке нет игроков.");
        } else {
            for (MarketEntry entry : marketEntries) {
                Player player = db.getPlayerById(entry.playerId);
                if (player == null) continue;
                boolean hasPlayer = db.checkUserHasPlayer(userId, player.getId());
                text.append(String.format("🃏 %s (%s %s, %d) - %d $\nПродавец: %s%s\n\n",
                        player.getName(),
                        player.getCategory().getName(),
                        player.getCategory().getEmoji(),
                        player.getRating(),
                        entry.price,
                        entry.sellerUsername,
                        hasPlayer ? " (У вас уже есть)" : ""));
            }
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (MarketEntry entry : marketEntries) {
            Player player = db.getPlayerById(entry.playerId);
            if (player == null) continue;
            rows.add(List.of(createButton(
                    String.format("Купить %s (%d $)", player.getName(), entry.price),
                    "trade_buy_" + entry.marketId + "_" + userId
            )));
        }

        // Кнопки пагинации
        List<InlineKeyboardButton> paginationRow = new ArrayList<>();
        if (page > 1) {
            paginationRow.add(createButton("⬅ Назад", "trade_market_page_" + (page - 1) + "_" + userId));
        }
        if (marketEntries.size() == pageSize) {
            paginationRow.add(createButton("Вперёд ➡", "trade_market_page_" + (page + 1) + "_" + userId));
        }
        if (!paginationRow.isEmpty()) {
            rows.add(paginationRow);
        }

        rows.add(List.of(createButton("Назад", "start_game_" + userId)));
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(rows);
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    private void handleSellPlayers(long chatId, int messageId, long userId) throws SQLException {
        List<Player> userPlayers = db.getUserPlayers(userId).stream()
                .filter(p -> List.of("Gold", "Diamond", "Legend", "GOAT").contains(p.getCategory().getName()))
                .toList();
        StringBuilder text = new StringBuilder("🃏 Ваши игроки для продажи (Gold и выше):\n\n");
        if (userPlayers.isEmpty()) {
            text.append("У вас нет игроков Gold или выше для продажи.");
        } else {
            for (Player player : userPlayers) {
                text.append(String.format("- %s (%s %s, %d)\n",
                        player.getName(),
                        player.getCategory().getName(),
                        player.getCategory().getEmoji(),
                        player.getRating()));
            }
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Player player : userPlayers) {
            rows.add(List.of(createButton(
                    String.format("Продать %s", player.getName()),
                    "trade_list_" + player.getId() + "_" + userId
            )));
        }
        rows.add(List.of(createButton("Назад", "trade_market_" + userId)));
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(rows);
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    private void handleMySales(long chatId, int messageId, long userId) throws SQLException {
        List<MarketEntry> sales = db.getUserMarketSales(userId);
        StringBuilder text = new StringBuilder("📋 Ваши продажи на рынке:\n\n");
        if (sales.isEmpty()) {
            text.append("У вас нет игроков на рынке.");
        } else {
            for (MarketEntry entry : sales) {
                Player player = db.getPlayerById(entry.playerId);
                if (player == null) continue;
                text.append(String.format("🃏 %s (%s %s, %d) - %d $\n",
                        player.getName(),
                        player.getCategory().getName(),
                        player.getCategory().getEmoji(),
                        player.getRating(),
                        entry.price));
            }
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (MarketEntry entry : sales) {
            Player player = db.getPlayerById(entry.playerId);
            if (player == null) continue;
            rows.add(List.of(createButton(
                    String.format("Убрать %s", player.getName()),
                    "trade_remove_" + entry.marketId + "_" + userId
            )));
        }
        rows.add(List.of(createButton("Назад", "trade_market_" + userId)));
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(rows);
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    private void handleBuyPlayer(long chatId, int messageId, long userId, long marketId) throws SQLException {
        MarketEntry entry = db.getMarketEntry(marketId);
        if (entry == null) {
            editMessage(bot, chatId, messageId, "Ошибка: Игрок не найден на рынке.", null);
            return;
        }

        Player player = db.getPlayerById(entry.playerId);
        if (player == null) {
            editMessage(bot, chatId, messageId, "Ошибка: Игрок не найден.", null);
            return;
        }

        if (entry.sellerId == userId) {
            editMessage(bot, chatId, messageId, "Ошибка: Нельзя купить своего игрока.", null);
            return;
        }

        if (db.checkUserHasPlayer(userId, player.getId())) {
            editMessage(bot, chatId, messageId, "У вас уже есть такой игрок!", null);
            return;
        }

        int userDollars = db.getUserDollars(userId);
        if (userDollars < entry.price) {
            editMessage(bot, chatId, messageId, String.format("Недостаточно долларов. Нужно: %d $, у вас: %d $", entry.price, userDollars), null);
            return;
        }

        db.buyPlayerFromMarket(userId, marketId);
        String text = String.format("🎉 Вы купили игрока %s за %d $!", player.getName(), entry.price);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(List.of(createButton("Назад", "trade_market_" + userId))));
        editMessage(bot, chatId, messageId, text, keyboard);

        User seller = db.getUserById(entry.sellerId);
        if (seller != null) {
            sendMessage(bot, entry.sellerId, String.format("Ваш игрок %s продан за %d $!", player.getName(), entry.price), null);
        }
    }

    private void handleListPlayer(long chatId, int messageId, long userId, int playerId) throws SQLException {
        Player player = db.getPlayerById(playerId);
        if (player == null || !List.of("Gold", "Diamond", "Legend", "GOAT").contains(player.getCategory().getName())) {
            editMessage(bot, chatId, messageId, "Ошибка: Игрок не найден или не подходит для продажи.", null);
            return;
        }

        if (!db.checkUserHasPlayer(userId, playerId)) {
            editMessage(bot, chatId, messageId, "Ошибка: У вас нет этого игрока.", null);
            return;
        }

        if (db.isPlayerOnMarket(playerId)) {
            editMessage(bot, chatId, messageId, "Ошибка: Этот игрок уже выставлен на рынок.", null);
            return;
        }

        if (db.isPlayerInSquad(userId, playerId)) {
            editMessage(bot, chatId, messageId, "Ошибка: Игрок находится в вашем составе. Уберите его из состава перед продажей.", null);
            return;
        }

        bot.addPendingSale(userId, playerId);
        String text = String.format("🃏 Напишите сумму, за которую хотите продать игрока %s (%s %s):",
                player.getName(),
                player.getCategory().getName(),
                player.getCategory().getEmoji());
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(
                List.of(createButton("Подтвердить", "trade_confirm_" + userId)),
                List.of(createButton("Назад", "trade_sell_" + userId))
        ));
        editMessage(bot, chatId, messageId, text, keyboard);
    }

    private void handleConfirmSale(long chatId, int messageId, long userId) throws SQLException {
        TelegramBot.PendingSale pendingSale = bot.getPendingSale(userId);
        if (pendingSale == null) {
            editMessage(bot, chatId, messageId, "Ошибка: Нет активной продажи.", null);
            return;
        }

        if (pendingSale.price == null) {
            editMessage(bot, chatId, messageId, "Ошибка: Вы не ввели сумму.", null);
            return;
        }

        int playerId = pendingSale.playerId;
        int price = pendingSale.price;
        bot.removePendingSale(userId);

        db.addPlayerToMarket(userId, playerId, price);
        Player player = db.getPlayerById(playerId);
        String text = String.format("✅ Ваш игрок %s (%s %s) успешно поставлен на рынок за %d $!",
                player.getName(),
                player.getCategory().getName(),
                player.getCategory().getEmoji(),
                price);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(List.of(createButton("Назад", "trade_sell_" + userId))));
        editMessage(bot, chatId, messageId, text, keyboard);
    }

    private void handleRemovePlayer(long chatId, int messageId, long userId, long marketId) throws SQLException {
        MarketEntry entry = db.getMarketEntry(marketId);
        if (entry == null || entry.sellerId != userId) {
            editMessage(bot, chatId, messageId, "Ошибка: Продажа не найдена или не ваша.", null);
            return;
        }

        Player player = db.getPlayerById(entry.playerId);
        if (player == null) {
            editMessage(bot, chatId, messageId, "Ошибка: Игрок не найден.", null);
            return;
        }

        db.removePlayerFromMarket(marketId);
        String text = String.format("🃏 Игрок %s убран с рынка и возвращён в ваш инвентарь.", player.getName());
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(List.of(createButton("Назад", "trade_my_sales_" + userId))));
        editMessage(bot, chatId, messageId, text, keyboard);
    }

    private void handleSquadMenu(long chatId, int messageId, long userId) throws SQLException {
        Squad squad = new Squad(db, userId);
        StringBuilder text = new StringBuilder("⚽ Ваш состав:\n\n");

        for (String position : Squad.getPositions()) {
            Player player = squad.getPlayer(position);
            String positionDisplay = switch (position) {
                case "GK" -> "GK (GK)";
                case "CB1", "CB2", "CB3" -> "DEF (" + position + ")";
                case "MID1", "MID2", "MID3" -> "MID (" + position + ")";
                case "FRW1", "FRW2", "FRW3" -> "FRW (" + position + ")";
                case "EXTRA" -> "EXTRA (EXTRA)";
                default -> position;
            };
            text.append(String.format("%s: %s\n",
                    positionDisplay,
                    player != null ? String.format("%s (%s %s, %d)",
                            player.getName(),
                            player.getCategory().getName(),
                            player.getCategory().getEmoji(),
                            player.getRating()) : "Пусто"));
        }

        int totalRating = squad.calculateRating();
        String chemistryInfo = squad.getChemistryInfo();
        text.append(String.format("\nОбщий рейтинг состава: %d\n%s", totalRating, chemistryInfo));

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(createButton("GK", "squad_position_GK_" + userId)));
        rows.add(List.of(
                createButton("DEF", "squad_position_CB1_" + userId),
                createButton("DEF", "squad_position_CB2_" + userId),
                createButton("DEF", "squad_position_CB3_" + userId)
        ));
        rows.add(List.of(
                createButton("MID", "squad_position_MID1_" + userId),
                createButton("MID", "squad_position_MID2_" + userId),
                createButton("MID", "squad_position_MID3_" + userId)
        ));
        rows.add(List.of(
                createButton("FRW", "squad_position_FRW1_" + userId),
                createButton("FRW", "squad_position_FRW2_" + userId),
                createButton("FRW", "squad_position_FRW3_" + userId)
        ));
        rows.add(List.of(createButton("EXTRA", "squad_position_EXTRA_" + userId)));
        rows.add(List.of(createButton("Назад", "start_game_" + userId)));

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(rows);
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    private void handleSelectPosition(long chatId, int messageId, long userId, String position) throws SQLException {
        if (!List.of("GK", "CB1", "CB2", "CB3", "MID1", "MID2", "MID3", "FRW1", "FRW2", "FRW3", "EXTRA").contains(position)) {
            editMessage(bot, chatId, messageId, "Ошибка: Неверная позиция.", null);
            return;
        }

        String requiredPosition = switch (position) {
            case "GK" -> "GK";
            case "CB1", "CB2", "CB3" -> "DEF";
            case "MID1", "MID2", "MID3" -> "MID";
            case "FRW1", "FRW2", "FRW3" -> "FRW";
            case "EXTRA" -> null; // Любой игрок
            default -> null;
        };

        List<Player> userPlayers = db.getUserPlayers(userId).stream()
                .filter(p -> requiredPosition == null || p.getPosition().equals(requiredPosition))
                .toList();
        StringBuilder text = new StringBuilder(String.format("🃏 Выберите игрока для позиции %s:\n\n", position));
        if (userPlayers.isEmpty()) {
            text.append("У вас нет подходящих игроков.");
        } else {
            for (Player player : userPlayers) {
                text.append(String.format("- %s (%s %s, %d)\n",
                        player.getName(),
                        player.getCategory().getName(),
                        player.getCategory().getEmoji(),
                        player.getRating()));
            }
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Player player : userPlayers) {
            rows.add(List.of(createButton(
                    player.getName(),
                    "squad_select_" + player.getId() + "_" + position + "_" + userId
            )));
        }
        rows.add(List.of(createButton("Очистить позицию", "squad_select_0_" + position + "_" + userId)));
        rows.add(List.of(createButton("Назад", "squad_menu_" + userId)));
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(rows);
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    private void handleSelectPlayer(long chatId, int messageId, long userId, String position, int playerId) throws SQLException {
        if (!List.of("GK", "CB1", "CB2", "CB3", "MID1", "MID2", "MID3", "FRW1", "FRW2", "FRW3", "EXTRA").contains(position)) {
            editMessage(bot, chatId, messageId, "Ошибка: Неверная позиция.", null);
            return;
        }

        if (playerId != 0) {
            Player player = db.getPlayerById(playerId);
            if (player == null || !db.checkUserHasPlayer(userId, playerId)) {
                editMessage(bot, chatId, messageId, "Ошибка: Игрок не найден или не ваш.", null);
                return;
            }

            String requiredPosition = switch (position) {
                case "GK" -> "GK";
                case "CB1", "CB2", "CB3" -> "DEF";
                case "MID1", "MID2", "MID3" -> "MID";
                case "FRW1", "FRW2", "FRW3" -> "FRW";
                case "EXTRA" -> null;
                default -> null;
            };

            if (requiredPosition != null && !player.getPosition().equals(requiredPosition)) {
                editMessage(bot, chatId, messageId, String.format("Ошибка: Игрок %s не подходит для позиции %s.", player.getName(), position), null);
                return;
            }

            if (db.isPlayerInSquad(userId, playerId)) {
                editMessage(bot, chatId, messageId, "Ошибка: Игрок уже находится в вашем составе на другой позиции.", null);
                return;
            }
        }

        db.updateSquadPosition(userId, position, playerId);
        String text = playerId == 0 ? String.format("✅ Позиция %s очищена.", position)
                : String.format("✅ Игрок %s установлен на позицию %s.", db.getPlayerById(playerId).getName(), position);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(List.of(createButton("Назад", "squad_menu_" + userId))));
        editMessage(bot, chatId, messageId, text, keyboard);
    }

    private void handleGiftPlayer(long chatId, int messageId, long userId, int playerId, long targetUserId) throws SQLException {
        if (userId == targetUserId) {
            editMessage(bot, chatId, messageId, "Ошибка: Нельзя подарить игрока самому себе.", null);
            return;
        }

        Player player = db.getPlayerById(playerId);
        if (player == null || !db.checkUserHasPlayer(userId, playerId)) {
            editMessage(bot, chatId, messageId, "Ошибка: Игрок не найден или не ваш.", null);
            return;
        }

        if (db.isPlayerInSquad(userId, playerId)) {
            editMessage(bot, chatId, messageId, "Ошибка: Игрок находится в вашем составе. Уберите его из состава перед отправкой.", null);
            return;
        }

        User targetUser = db.getUserById(targetUserId);
        if (targetUser == null) {
            editMessage(bot, chatId, messageId, "Ошибка: Получатель не найден.", null);
            return;
        }

        if (db.checkUserHasPlayer(targetUserId, playerId)) {
            editMessage(bot, chatId, messageId, "Ошибка: У получателя уже есть этот игрок.", null);
            return;
        }

        db.giftPlayer(userId, targetUserId, playerId);
        String text = String.format("🎁 Вы подарили игрока %s пользователю %s!", player.getName(), targetUser.getUsername());
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(List.of(createButton("Назад", "start_game_" + userId))));
        editMessage(bot, chatId, messageId, text, keyboard);

        sendMessage(bot, targetUserId, String.format("🎁 Вам подарили игрока %s от %s!", player.getName(), db.getUserById(userId).getUsername()), null);
    }

    private void handleFriendsList(long chatId, int messageId, long userId, int page) throws SQLException {
        int pageSize = 10; // 10 друзей на страницу
        List<User> friends = db.getUserFriends(userId, page, pageSize);
        StringBuilder text = new StringBuilder("👥 Ваши друзья:\n\n");
        if (friends.isEmpty()) {
            text.append("У вас пока нет друзей.");
        } else {
            for (User friend : friends) {
                text.append(String.format("- %s\n", friend.getUsername()));
            }
        }

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
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(rows);
        editMessage(bot, chatId, messageId, text.toString(), keyboard);
    }

    private void handleRemoveFriend(long chatId, int messageId, long userId, long friendId) throws SQLException {
        User friend = db.getUserById(friendId);
        if (friend == null) {
            editMessage(bot, chatId, messageId, "Ошибка: Пользователь не найден.", null);
            return;
        }

        db.removeFriend(userId, friendId);
        String text = String.format("✅ %s удалён из ваших друзей.", friend.getUsername());
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(List.of(List.of(createButton("Назад", "friends_list_" + userId))));
        editMessage(bot, chatId, messageId, text, keyboard);
    }

    private InlineKeyboardMarkup createPackKeyboard(List<Pack> packs, long userId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Pack pack : packs) {
            if (pack.getId() == 0) continue;
            String packName = pack.getName() != null ? pack.getName() : "Неизвестный пак";
            rows.add(List.of(createButton("Открыть " + packName, "open_pack_" + pack.getId() + "_" + userId)));
        }
        rows.add(List.of(createButton("Назад", "packs_menu_" + userId)));
        return new InlineKeyboardMarkup(rows);
    }

    private InlineKeyboardMarkup createBuyPackKeyboard(List<Pack> packs, long userId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Pack pack : packs) {
            if (pack.getId() == 0 || pack.getPrice() == 0) continue;
            String packName = pack.getName() != null ? pack.getName() : "Неизвестный пак";
            rows.add(List.of(createButton(String.format("Купить %s (%d 💵)", packName, pack.getPrice()), "packs_buy_" + pack.getId() + "_" + userId)));
        }
        rows.add(List.of(createButton("Назад", "packs_menu_" + userId)));
        return new InlineKeyboardMarkup(rows);
    }

    private static InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}