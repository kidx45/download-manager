package download.manager.controller;

import download.manager.config.SettingsManager;
import download.manager.model.Download;
import download.manager.service.DownloadInfo;
import download.manager.storage.DAO;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    // Navigation & Layout
    @FXML private Label Page_Name;
    @FXML private Label statusLabel;
    @FXML private VBox downloadsView;
    @FXML private VBox settingsView;
    @FXML private VBox aboutView;

    // Download Controls
    @FXML private TextField urlField;
    @FXML private Button startBtn;
    @FXML private ProgressBar progressBar;
    @FXML private TableView<Download> table;
    @FXML private TableColumn<Download, Number> idCol;
    @FXML private TableColumn<Download, String> fileCol;
    @FXML private TableColumn<Download, String> statusCol;
    @FXML private TableColumn<Download, String> progressCol;
    @FXML private TableColumn<Download, String> urlCol;
    @FXML private TableColumn<Download, Download> actionsCol;

    // Settings Controls
    @FXML private TextField savePathField;
    @FXML private RadioButton lightThemeRadio;
    @FXML private RadioButton darkThemeRadio;
    @FXML private ToggleGroup themeGroup;

    // Navigation Group
    @FXML private ToggleGroup navGroup;

    private final DAO dao = new DAO();
    private final ObservableList<Download> downloadList = FXCollections.observableArrayList();
    private volatile boolean actionsMenuOpen = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupDownloadsView();
        setupSettingsView();
        setupThemeControls();
        onDownloadsNavClicked();
    }

    private void setupDownloadsView() {
        setupTableColumns();
        setupActionsColumn();
        setupAutoRefresh();
        onRefreshClicked();
    }

    private void setupTableColumns() {
        idCol.setCellValueFactory(cellData -> cellData.getValue().idProperty());
        fileCol.setCellValueFactory(cellData -> cellData.getValue().fileNameProperty());
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        progressCol.setCellValueFactory(cellData -> Bindings.createStringBinding(
                () -> cellData.getValue().getProgress() + "%",
                cellData.getValue().progressProperty()
        ));
        urlCol.setCellValueFactory(cellData -> cellData.getValue().urlProperty());

        table.setItems(downloadList);
    }

    private void setupActionsColumn() {
        actionsCol.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        actionsCol.setCellFactory(column -> new TableCell<Download, Download>() {
            @Override
            protected void updateItem(Download item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    MenuButton actionsBtn = new MenuButton("Actions");
                    actionsBtn.setStyle("-fx-padding: 2 8; -fx-font-size: 10;");
                    actionsBtn.setOnShowing(e -> actionsMenuOpen = true);
                    actionsBtn.setOnHidden(e -> actionsMenuOpen = false);

                    MenuItem pauseItem = new MenuItem("Pause");
                    pauseItem.setOnAction(e -> {
                        table.getSelectionModel().select(item);
                        onPauseClicked();
                    });

                    MenuItem cancelItem = new MenuItem("Cancel");
                    cancelItem.setOnAction(e -> {
                        dao.deleteDownload(item.getId());
                        setStatus("✗ Canceled: " + item.getFileName());
                        onRefreshClicked();
                    });

                    MenuItem deleteItem = new MenuItem("Delete");
                    deleteItem.setOnAction(e -> {
                        dao.deleteDownload(item.getId());
                        onRefreshClicked();
                    });

                    String status = item.getStatus() == null ? "" : item.getStatus().trim().toUpperCase();
                    pauseItem.setDisable(!"DOWNLOADING".equals(status));
                    cancelItem.setDisable("COMPLETED".equals(status));

                    actionsBtn.getItems().setAll(pauseItem, cancelItem, deleteItem);
                    setGraphic(actionsBtn);
                }
            }
        });
    }

    private void setupAutoRefresh() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            if (!actionsMenuOpen) {
                onRefreshClicked();
            }
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    @FXML public void onStartClicked() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            setStatus("Please enter a URL!");
            return;
        }

        startBtn.setDisable(true);
        setStatus("Starting...");
        setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                DownloadInfo info = new DownloadInfo(url, dao);
                info.start();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setStatus("✓ Completed!");
            setProgress(1.0);
            startBtn.setDisable(false);
            urlField.clear();
            onRefreshClicked();
        });

        task.setOnFailed(e -> {
            setStatus("✗ Failed!");
            setProgress(0);
            startBtn.setDisable(false);
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML public void onPauseClicked() {
        Download selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select a download first!");
            return;
        }

        if (!"DOWNLOADING".equals(selected.getStatus())) {
            setStatus("Can only pause active downloads!");
            return;
        }

        DownloadInfo info = DownloadInfo.activeDownloads.get(selected.getId());
        if (info != null) {
            info.togglePause();
            setStatus("⏸ Pausing: " + selected.getFileName());
        } else {
            setStatus("Download not active in memory!");
        }
    }

    @FXML public void onResumeClicked() {
        Download selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select a download to resume!");
            return;
        }

        if (!"PAUSED".equals(selected.getStatus())) {
            setStatus("Can only resume paused downloads!");
            return;
        }

        resumeDownload(selected);
    }

    @FXML public void onRefreshClicked() {
        Task<List<Download>> task = new Task<>() {
            @Override
            protected List<Download> call() {
                return dao.getAllDownloads();
            }
        };

        task.setOnSucceeded(e -> {
            List<Download> downloads = task.getValue();
            updateDownloadList(downloads);
            updateProgressBar(downloads);
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void updateDownloadList(List<Download> downloads) {
        downloadList.clear();
        downloadList.addAll(downloads);
    }

    private void updateProgressBar(List<Download> downloads) {
        if (downloads.isEmpty()) {
            progressBar.setProgress(0);
            setStatus("No downloads");
            return;
        }

        int completed = (int) downloads.stream().filter(d -> "COMPLETED".equals(d.getStatus())).count();
        double progress = (double) completed / downloads.size();
        progressBar.setProgress(progress);

        setStatus(completed + "/" + downloads.size() + " downloads completed");
    }

    private void resumeDownload(Download download) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                // Use the correct constructor for resuming: (url, dao, id, bytesDownloaded, savePath)
                DownloadInfo info = new DownloadInfo(
                    download.getUrl(),
                    dao,
                    download.getId(),
                    download.getBytesDownloaded(),
                    SettingsManager.getSavePath()
                );
                info.start();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setStatus("✓ Download resumed!");
            onRefreshClicked();
        });

        task.setOnFailed(e -> setStatus("✗ Failed to resume!"));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void setupSettingsView() {
        String savedPath = SettingsManager.getSavePath();
        savePathField.setText(savedPath != null ? savedPath : "Downloads folder");
    }

    private void setupThemeControls() {
        String currentTheme = SettingsManager.getTheme();
        if ("dark".equalsIgnoreCase(currentTheme)) {
            darkThemeRadio.setSelected(true);
        } else {
            lightThemeRadio.setSelected(true);
        }

        lightThemeRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) applyThemeChange("light");
        });

        darkThemeRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) applyThemeChange("dark");
        });
    }

    private void applyThemeChange(String theme) {
        String savePath = savePathField.getText();
        if (savePath.isEmpty() || savePath.equals("Downloads folder")) {
            savePath = SettingsManager.getSavePath();
        }
        SettingsManager.saveSettings(savePath, theme);
        setStatus("Theme will change on next restart");
    }

    @FXML public void onBrowseSavePathClicked() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Download Folder");
        File selectedDir = chooser.showDialog(savePathField.getScene().getWindow());
        if (selectedDir != null) {
            savePathField.setText(selectedDir.getAbsolutePath());
        }
    }

    @FXML public void onSaveSettingsClicked() {
        String path = savePathField.getText();
        String theme = darkThemeRadio.isSelected() ? "dark" : "light";
        if (!path.isEmpty() && !path.equals("Downloads folder")) {
            SettingsManager.saveSettings(path, theme);
            SettingsManager.currentSaveLocation = path;
        } else {
            SettingsManager.saveSettings(SettingsManager.getSavePath(), theme);
        }
        setStatus("✓ Settings saved!");
    }

    @FXML
    public void onDownloadsNavClicked() {
        hideAllViews();
        downloadsView.setVisible(true);
        downloadsView.setManaged(true);
        Page_Name.setText("Downloads");
    }

    @FXML
    public void onSettingsNavClicked() {
        hideAllViews();
        settingsView.setVisible(true);
        settingsView.setManaged(true);
        Page_Name.setText("Settings");
    }

    @FXML
    public void onAboutNavClicked() {
        hideAllViews();
        aboutView.setVisible(true);
        aboutView.setManaged(true);
        Page_Name.setText("About");
    }

    private void hideAllViews() {
        downloadsView.setVisible(false);
        downloadsView.setManaged(false);
        settingsView.setVisible(false);
        settingsView.setManaged(false);
        aboutView.setVisible(false);
        aboutView.setManaged(false);
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void setProgress(double progress) {
        progressBar.setProgress(progress);
    }
}
