package ru.apache_maven;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.apache_maven.bot.TelegramBot;
import ru.apache_maven.db.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;

public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final String LOCK_FILE = "bot.lock";

    public static void main(String[] args) {
        File lockFile = new File(LOCK_FILE);
        DatabaseManager dbManager = null;

        try (FileChannel channel = FileChannel.open(
                lockFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {

            if (lock == null) {
                logger.error("Другой экземпляр бота уже запущен! Завершаю работу.");
                System.exit(1);
            }

            String dbUrl = "jdbc:mysql://localhost:3306/flashcards"
                    + "?useSSL=false"
                    + "&serverTimezone=UTC"
                    + "&allowPublicKeyRetrieval=true";

            String dbUser = "root";
            String dbPassword = "Oryn0basar";
            String botToken = "7543104393:AAGBa66jRlvvrogXpeIMS30I7CI4_I-rzpk";
            String botUsername = "@YourBotUsername"; // Добавьте имя вашего бота, например "@FlashcardsBot"

            dbManager = new DatabaseManager(dbUrl, dbUser, dbPassword);

            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(new TelegramBot(botToken, botUsername, dbManager)); // Исправлено
            logger.info("Бот успешно запущен!");

            DatabaseManager finalDbManager = dbManager;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (finalDbManager != null) {
                        finalDbManager.close();
                        logger.info("Соединение с БД закрыто");
                    }
                } catch (SQLException e) {
                    logger.error("Ошибка закрытия соединения: {}", e.getMessage());
                }

                if (lockFile.exists() && !lockFile.delete()) {
                    logger.warn("Не удалось удалить lock-файл");
                }
            }));

            while (true) {
                Thread.sleep(Long.MAX_VALUE);
            }

        } catch (IOException e) {
            logger.error("Ошибка работы с lock-файлом: {}", e.getMessage());
            System.exit(1);
        } catch (TelegramApiException | InterruptedException e) {
            logger.error("Ошибка запуска: {}", e.getMessage());
            System.exit(1);
        }
    }
}