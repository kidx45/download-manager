package download.manager.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {
    @FXML private TextField savePathField;
    @FXML private RadioButton lightThemeRadio;
    @FXML private RadioButton darkThemeRadio;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
            loadSettings();
    }

    private void loadSettings() {
            savePathField.setText(download.manager.config.SettingsManager.getSavePath());
            if ("dark".equals(download.manager.config.SettingsManager.getTheme())) {
                    darkThemeRadio.setSelected(true);
            } else {
                    lightThemeRadio.setSelected(true);
            }
    }

    @FXML public void onBrowseSavePathClicked() {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Default Save Location");
            File dir = chooser.showDialog(savePathField.getScene().getWindow());
            if (dir != null) {
                    savePathField.setText(dir.getAbsolutePath());
            }
    }
	
    @FXML public void onSaveSettingsClicked() {
            String savePath = savePathField.getText().trim();
            String theme = darkThemeRadio.isSelected() ? "dark" : "light";
            download.manager.config.SettingsManager.saveSettings(savePath, theme);
            javafx.scene.Scene scene = savePathField.getScene();
            if (scene != null) {
                    download.manager.config.SettingsManager.applyTheme(scene);
            }
            // Status message will be shown by parent controller
            System.out.println("✓ Settings saved!");
    }
}
