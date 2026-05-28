package download.manager.config;

import java.io.*;
import java.util.Properties;

import javafx.scene.Scene;

public class SettingsManager {

    // Loading the file name where the save settings will lay
    private static final String CONFIG_FILE = "settings.properties";

    // Think of it as map that has a key value pairing that will be suitable for *.properties files
    private static final Properties properties = new Properties();
    
    // Explicit public static string variable to store save location in-memory
    public static String currentSaveLocation = System.getProperty("user.home");
    
    // Will be immediately be called when the code is ran in jvm
    static {
        loadSettings();
    }
    
    public static void loadSettings() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (InputStream input = new FileInputStream(file)) {
                // Load it to our "custom" map
                properties.load(input);

                // Will get the last save path other wise will save it at the root of your home folder
                currentSaveLocation = properties.getProperty("save_path", System.getProperty("user.home"));
            } catch (IOException e) {
                System.out.println("✗ Failed to load settings: " + e.getMessage());
            }
        } else {

            // If the application is new, it will set the path by default to root of home folder
            currentSaveLocation = System.getProperty("user.home");
        }
    }
    
    // Once the app is closing it will save the path and theme
    public static void saveSettings(String savePath, String theme) {
        currentSaveLocation = savePath;
        properties.setProperty("save_path", savePath);
        properties.setProperty("theme", theme);

        // If the file exists it will overwrite it, otherwise it will create a new one with the current preferences saved
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

    // It will take the scene which may either be the download, about or settings and apply the light or dark theme
    public static void applyTheme(Scene scene) {
        String theme = getTheme();
        scene.getStylesheets().clear();
        try {
            if ("dark".equals(theme)) {
                String cssPath = SettingsManager.class.getResource("/css/dark.css").toExternalForm();
                scene.getStylesheets().add(cssPath);
            } else {
                String cssPath = SettingsManager.class.getResource("/css/light.css").toExternalForm();
                scene.getStylesheets().add(cssPath);
            }
        } catch (Exception e) {
            System.out.println("✗ Failed to apply stylesheet: " + e.getMessage());
        }
    }
}
