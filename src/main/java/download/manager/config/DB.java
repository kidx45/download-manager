package download.manager.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import io.github.cdimascio.dotenv.Dotenv;

// CONFIG: Manages the database connection
// Uses Singleton pattern — only one connection shared across the whole app
public class DB {

    // Connection details
    private static final Dotenv dotenv = Dotenv.load();

    // Read values from .env
    private static final String URL  = dotenv.get("DB_URL");
    private static final String USER = dotenv.get("DB_USER");
    private static final String PASS = dotenv.get("DB_PASSWORD");

    // The single shared connection
    private static Connection connection = null;

    // Private constructor — nobody can do "new DBConnection()"
    private DB() {}

    // Get the connection — creates it only once
    public static Connection getConnection() {
        if (connection == null) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(URL, USER, PASS);
                System.out.println("✓ Connected to MySQL!");
                createTableIfNotExists();
            } catch (ClassNotFoundException e) {
                System.out.println("✗ Driver not found: " + e.getMessage());
            } catch (SQLException e) {
                System.out.println("✗ Connection failed: " + e.getMessage());
            }
        }
        return connection;
    }

    // Creates the table if it doesn't already exist
    private static void createTableIfNotExists() {
        String sql = "CREATE TABLE IF NOT EXISTS downloads (" +
                     "id INT PRIMARY KEY AUTO_INCREMENT," +
                     "url TEXT NOT NULL," +
                     "file_name VARCHAR(255)," +
                     "file_size BIGINT," +
                     "status VARCHAR(20) DEFAULT 'PENDING'," +
                     "progress DOUBLE DEFAULT 0.0," +
                     "save_path VARCHAR(255)," +
                     "bytes_downloaded BIGINT DEFAULT 0," +
                     "is_resumable BOOLEAN DEFAULT FALSE," +
                     "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                     ")";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("✓ Table ready!");

            // Run migration for pre-existing tables
            try {
                stmt.execute("ALTER TABLE downloads ADD COLUMN bytes_downloaded BIGINT DEFAULT 0");
                System.out.println("✓ Added bytes_downloaded column to existing table");
            } catch (SQLException e) {
                // Ignore: column already exists
            }

            try {
                stmt.execute("ALTER TABLE downloads ADD COLUMN is_resumable BOOLEAN DEFAULT FALSE");
                System.out.println("✓ Added is_resumable column to existing table");
            } catch (SQLException e) {
                // Ignore: column already exists
            }
        } catch (SQLException e) {
            System.out.println("✗ Table creation failed: " + e.getMessage());
        }
    }

    // Call this when the app closes
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
                System.out.println("✓ Connection closed.");
            } catch (SQLException e) {
                System.out.println("✗ Failed to close: " + e.getMessage());
            }
        }
    }
}