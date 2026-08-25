import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {

    private static final String HOST = getRequired("DB_HOST");
    private static final String PORT = getRequired("DB_PORT");
    private static final String DATABASE = getRequired("DB_NAME");
    private static final String USERNAME = getRequired("DB_USER");
    private static final String PASSWORD = getRequired("DB_PASSWORD");

    private static String getRequired(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing environment variable: " + name
            );
        }

        return value;
    }

    public static Connection getConnection() throws SQLException {

        String url = "jdbc:mysql://" +
                HOST + ":" +
                PORT + "/" +
                DATABASE +
                "?sslMode=REQUIRED&serverTimezone=UTC";

        return DriverManager.getConnection(
                url,
                USERNAME,
                PASSWORD
        );
    }
}