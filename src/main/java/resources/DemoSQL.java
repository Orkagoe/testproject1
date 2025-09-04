package resourses;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DemoSQL {
    public static void main(String[] args) {
        // URL для подключения к базе данных H2, база будет создана в файле testdb.mv.db
        String url = "jdbc:h2:./testdb";
        String user = "sa";
        String password = "";

        try {
            Connection connection = DriverManager.getConnection(url, user, password);
            System.out.println("Соединение установлено успешно!");
            // Здесь можно выполнять SQL-запросы

            // Не забудьте закрыть соединение
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
