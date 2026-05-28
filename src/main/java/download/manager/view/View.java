package download.manager.view;

import download.manager.config.DB;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class View extends Application {

    @Override
    public void start(Stage window) throws Exception {
        // Load custom font - capture the font to get correct family name
        Font customFont = Font.loadFont(getClass().getResourceAsStream("/fonts/ProductSans-Regular.ttf"), 14);
        String fontFamily = customFont != null ? customFont.getFamily() : "System";
        System.out.println("Loaded font family: " + fontFamily);

        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/View.fxml")
        );
        Scene scene = new Scene(loader.load());
        window.setTitle("Download Manager");
        window.setScene(scene);
        
        // Apply saved theme
        download.manager.config.SettingsManager.applyTheme(scene);
        
        window.show();
    }

    @Override
    public void stop() {
        DB.closeConnection();
    }
}