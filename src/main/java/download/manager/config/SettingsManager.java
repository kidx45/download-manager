package download.manager.config;

import java.io.*;
import java.util.Properties;

public class SettingsManager {
    private static final String CONFIG_FILE = "settings.properties";
    private static final Properties properties = new Properties();
    
    // Explicit public static string variable to store save location in-memory
    public static String currentSaveLocation = System.getProperty("user.home");
    
    static {
        loadSettings();
    }
    
    public static void loadSettings() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (InputStream input = new FileInputStream(file)) {
                properties.load(input);
                currentSaveLocation = properties.getProperty("save_path", System.getProperty("user.home"));
            } catch (IOException e) {
                System.out.println("✗ Failed to load settings: " + e.getMessage());
            }
        } else {
            currentSaveLocation = System.getProperty("user.home");
        }
    }
    
    public static void saveSettings(String savePath, String theme) {
        currentSaveLocation = savePath;
        properties.setProperty("save_path", savePath);
        properties.setProperty("theme", theme);
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            properties.store(output, "Download Manager Settings");
            System.out.println("✓ Settings saved successfully, location is: " + currentSaveLocation);
        } catch (IOException e) {
            System.out.println("✗ Failed to save settings: " + e.getMessage());
        }
    }
    
    public static String getSavePath() {
        return currentSaveLocation;
    }
    
    public static String getTheme() {
        return properties.getProperty("theme", "light");
    }

    public static void applyTheme(javafx.scene.Scene scene) {
        String theme = getTheme();
        scene.getStylesheets().clear();

        try {
            String baseCssPath = SettingsManager.class
                    .getResource("/css/app.css")
                    .toExternalForm();
            scene.getStylesheets().add(baseCssPath);
        } catch (Exception e) {
            System.out.println("✗ Failed to apply base stylesheet: " + e.getMessage());
        }

        if ("dark".equals(theme)) {
            try {
                String cssPath = SettingsManager.class.getResource("/css/dark.css").toExternalForm();
                scene.getStylesheets().add(cssPath);
            } catch (Exception e) {
                System.out.println("✗ Failed to apply dark stylesheet: " + e.getMessage());
            }
        }
    }
}
