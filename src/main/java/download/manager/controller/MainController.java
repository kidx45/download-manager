package download.manager.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML private Label Page_Name;
    @FXML private Label statusLabel;
    @FXML private StackPane contentPane;

    private DownloadsController downloadsController;
    private SettingsController settingsController;
    private AboutController aboutController;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadViews();
        onDownloadsNavClicked();
    }

    private void loadViews() {
        try {
            // Load Downloads
            FXMLLoader downloadsLoader = new FXMLLoader(
                    getClass().getResource("/fxml/Downloads.fxml")
            );
            contentPane.getChildren().add(downloadsLoader.load());
            downloadsController = downloadsLoader.getController();

            // Load Settings
            FXMLLoader settingsLoader = new FXMLLoader(
                    getClass().getResource("/fxml/Settings.fxml")
            );
            contentPane.getChildren().add(settingsLoader.load());
            settingsController = settingsLoader.getController();

            // Load About
            FXMLLoader aboutLoader = new FXMLLoader(
                    getClass().getResource("/fxml/About.fxml")
            );
            contentPane.getChildren().add(aboutLoader.load());
            aboutController = aboutLoader.getController();

        } catch (IOException e) {
            System.out.println("✗ Failed to load FXML files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void onDownloadsNavClicked() {
        hideAllViews();
        contentPane.getChildren().get(0).setVisible(true);
        contentPane.getChildren().get(0).setManaged(true);
        Page_Name.setText("Downloads");
    }

    @FXML
    public void onSettingsNavClicked() {
        hideAllViews();
        contentPane.getChildren().get(1).setVisible(true);
        contentPane.getChildren().get(1).setManaged(true);
        Page_Name.setText("Settings");
    }

    @FXML
    public void onAboutNavClicked() {
        hideAllViews();
        contentPane.getChildren().get(2).setVisible(true);
        contentPane.getChildren().get(2).setManaged(true);
        Page_Name.setText("About");
    }

    private void hideAllViews() {
        for (int i = 0; i < contentPane.getChildren().size(); i++) {
            contentPane.getChildren().get(i).setVisible(false);
            contentPane.getChildren().get(i).setManaged(false);
        }
    }

    public void setStatus(String text) {
        statusLabel.setText(text);
    }
}
