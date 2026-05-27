package download.manager.view;

import download.manager.config.DB;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class View extends Application {

    @Override
    public void start(Stage window) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/View.fxml")
        );
        Scene scene = new Scene(loader.load());
        window.setTitle("Download Manager");
        window.setScene(scene);
        window.show();
    }

    @Override
    public void stop() {
        DB.closeConnection();
    }
}