package download.manager.controller;

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
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class DownloadController implements Initializable {

    @FXML private TextField urlField;
    @FXML private Button startBtn;
    @FXML private Button refreshBtn;
    @FXML private ProgressBar progressBar;
    @FXML private Label statusLabel;

    @FXML private TableView<Download> table;
    @FXML private TableColumn<Download, Number> idCol;
    @FXML private TableColumn<Download, String> fileCol;
    @FXML private TableColumn<Download, String> statusCol;
    @FXML private TableColumn<Download, String> progressCol;
    @FXML private TableColumn<Download, String> urlCol;
    @FXML private TableColumn<Download, Download> actionsCol;

    @FXML private Label Page_Name;
    @FXML private VBox downloadsView;
    @FXML private VBox settingsView;

    @FXML private TextField savePathField;
    @FXML private RadioButton lightThemeRadio;
    @FXML private RadioButton darkThemeRadio;

    private final DAO dao = new DAO();

    private final ObservableList<Download> downloadList =
            FXCollections.observableArrayList();

    private int activeDownloadId = -1;

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        setupTableColumns();
        setupActionsColumn();
        setupAutoRefresh();

        onRefreshClicked();
        onDownloadsNavClicked();
    }

    // =========================================================
    // SAFE UI HELPERS
    // =========================================================

    private void clearBindings() {
        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
    }

    private void setStatus(String text) {
        statusLabel.textProperty().unbind();
        statusLabel.setText(text);
    }

    private void setProgress(double value) {
        progressBar.progressProperty().unbind();
        progressBar.setProgress(value);
    }

    // =========================================================
    // START DOWNLOAD
    // =========================================================

    @FXML
    public void onStartClicked() {

        String url = urlField.getText().trim();

        if (url.isEmpty()) {
            setStatus("Please enter a URL!");
            return;
        }

        startBtn.setDisable(true);

        clearBindings();

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

            clearBindings();

            setStatus("✓ Completed!");
            setProgress(1.0);

            startBtn.setDisable(false);

            onRefreshClicked();
        });

        task.setOnFailed(e -> {

            clearBindings();

            setStatus("✗ Failed!");
            setProgress(0);

            startBtn.setDisable(false);
        });

        Thread t = new Thread(task);

        t.setDaemon(true);
        t.start();
    }

    // =========================================================
    // PAUSE DOWNLOAD
    // =========================================================

    @FXML
    public void onPauseClicked() {

        Download selected =
                table.getSelectionModel().getSelectedItem();

        if (selected == null) {
            setStatus("Select a download first!");
            return;
        }

        if (!"DOWNLOADING".equals(selected.getStatus())) {
            setStatus("Can only pause active downloads!");
            return;
        }

        DownloadInfo info =
                DownloadInfo.activeDownloads.get(selected.getId());

        if (info != null) {

            info.togglePause();

            clearBindings();

            setStatus("⏸ Pausing: " + selected.getFileName());

        } else {

            setStatus("Download not active in memory!");
        }
    }

    // =========================================================
    // RESUME DOWNLOAD
    // =========================================================

    @FXML
    public void onResumeClicked() {

        Download selected =
                table.getSelectionModel().getSelectedItem();

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

    // =========================================================
    // REFRESH
    // =========================================================

    @FXML
    public void onRefreshClicked() {

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

    // =========================================================
    // UPDATE DOWNLOAD LIST
    // =========================================================

    private void updateDownloadList(List<Download> newDownloads) {

        java.util.Map<Integer, Download> existingMap =
                new java.util.HashMap<>();

        for (Download d : downloadList) {
            existingMap.put(d.getId(), d);
        }

        for (Download newD : newDownloads) {

            Download existing =
                    existingMap.get(newD.getId());

            if (existing != null) {

                existing.setStatus(newD.getStatus());
                existing.setProgress(newD.getProgress());
                existing.setBytesDownloaded(
                        newD.getBytesDownloaded()
                );
                existing.setResumable(
                        newD.isResumable()
                );
                existing.setFileName(
                        newD.getFileName()
                );
            }
        }

        for (int i = 0; i < newDownloads.size(); i++) {

            Download newD = newDownloads.get(i);

            int currentIndex = -1;

            for (int j = i; j < downloadList.size(); j++) {

                if (downloadList.get(j).getId()
                        == newD.getId()) {

                    currentIndex = j;
                    break;
                }
            }

            if (currentIndex != -1) {

                if (currentIndex != i) {

                    Download temp =
                            downloadList.remove(currentIndex);

                    downloadList.add(i, temp);
                }

            } else {

                downloadList.add(i, newD);
            }
        }

        while (downloadList.size() > newDownloads.size()) {

            downloadList.remove(
                    downloadList.size() - 1
            );
        }
    }

    // =========================================================
    // RESUME LOGIC
    // =========================================================

    private void resumeDownload(Download d) {

        clearBindings();

        setStatus("▶ Resuming: " + d.getFileName());

        Task<Void> task = new Task<>() {

            @Override
            protected Void call() {

                DownloadInfo info =
                        new DownloadInfo(
                                d.getUrl(),
                                dao,
                                d.getId(),
                                d.getBytesDownloaded(),
                                d.getSavePath()
                        );

                info.start();

                return null;
            }
        };

        task.setOnSucceeded(e -> {

            clearBindings();

            setStatus("✓ Completed: " + d.getFileName());
            setProgress(1.0);

            onRefreshClicked();
        });

        task.setOnFailed(e -> {

            clearBindings();

            setStatus("✗ Resume failed!");
        });

        Thread t = new Thread(task);

        t.setDaemon(true);
        t.start();
    }

    // =========================================================
    // TABLE COLUMNS
    // =========================================================

    private void setupTableColumns() {

        idCol.setCellValueFactory(
                data -> data.getValue().idProperty()
        );

        fileCol.setCellValueFactory(
                data -> data.getValue().fileNameProperty()
        );

        statusCol.setCellValueFactory(
                data -> data.getValue().statusProperty()
        );

        progressCol.setCellValueFactory(data ->
                Bindings.createStringBinding(
                        () -> String.format(
                                "%.1f%%",
                                data.getValue().getProgress()
                        ),
                        data.getValue().progressProperty()
                )
        );

        urlCol.setCellValueFactory(
                data -> data.getValue().urlProperty()
        );

        actionsCol.setCellValueFactory(
                data -> new SimpleObjectProperty<>(
                        data.getValue()
                )
        );

        table.setItems(downloadList);

        table.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, selected) -> {

                    if (selected == null) return;

                    boolean hasActive = false;

                    for (Download d : downloadList) {

                        if ("DOWNLOADING".equals(
                                d.getStatus())) {

                            hasActive = true;
                            break;
                        }
                    }

                    if (!hasActive) {

                        clearBindings();

                        setProgress(
                                selected.getProgress() / 100.0
                        );

                        setStatus(
                                selected.getFileName()
                                        + " — "
                                        + selected.getStatus()
                                        + " "
                                        + String.format(
                                        "%.1f%%",
                                        selected.getProgress()
                                )
                        );
                    }
                });
    }

    // =========================================================
    // ACTIONS COLUMN
    // =========================================================

    private void setupActionsColumn() {

    actionsCol.setCellFactory(col -> new TableCell<Download, Download>() {

        // =====================================================
        // UI COMPONENTS
        // =====================================================

        private final Button actionBtn = new Button("Actions ▾");

        private final ContextMenu menu = new ContextMenu();

        private final MenuItem pauseItem  = new MenuItem("⏸ Pause");
        private final MenuItem resumeItem = new MenuItem("▶ Resume");
        private final MenuItem cancelItem = new MenuItem("✗ Cancel");
        private final MenuItem deleteItem = new MenuItem("🗑 Delete");

        {
            // =================================================
            // MENU SETUP
            // =================================================

            menu.getItems().addAll(
                pauseItem,
                resumeItem,
                cancelItem,
                deleteItem
            );

            actionBtn.setFocusTraversable(false);
            actionBtn.setPrefWidth(100);

            actionBtn.setOnAction(e ->
                menu.show(actionBtn, Side.BOTTOM, 0, 0)
            );

            // =================================================
            // PAUSE ACTION
            // =================================================

            pauseItem.setOnAction(e -> {

                Download d = getItem();

                if (d == null) {
                    return;
                }

                if (!"DOWNLOADING".equals(d.getStatus())) {

                    clearBindings();

                    setStatus("Can only pause active downloads!");

                    return;
                }

                DownloadInfo info =
                    DownloadInfo.activeDownloads.get(d.getId());

                if (info != null) {

                    info.togglePause();

                    clearBindings();

                    setStatus("⏸ Pausing: " + d.getFileName());

                } else {

                    clearBindings();

                    setStatus("Download not active!");
                }
            });

            // =================================================
            // RESUME ACTION
            // =================================================

            resumeItem.setOnAction(e -> {

                Download d = getItem();

                if (d == null) {
                    return;
                }

                if (!"PAUSED".equals(d.getStatus())) {

                    clearBindings();

                    setStatus("Can only resume paused downloads!");

                    return;
                }

                resumeDownload(d);
            });

            // =================================================
            // CANCEL ACTION
            // =================================================

            cancelItem.setOnAction(e -> {

                Download d = getItem();

                if (d == null) {
                    return;
                }

                if ("COMPLETED".equals(d.getStatus())) {

                    clearBindings();

                    setStatus("Cannot cancel completed download!");

                    return;
                }

                DownloadInfo info =
                    DownloadInfo.activeDownloads.get(d.getId());

                if (info != null) {
                    info.togglePause();
                }

                dao.updateStatus(d.getId(), "FAILED");

                clearBindings();

                setStatus("✗ Cancelled: " + d.getFileName());

                onRefreshClicked();
            });

            // =================================================
            // DELETE ACTION
            // =================================================

            deleteItem.setOnAction(e -> {

                Download d = getItem();

                if (d == null) {
                    return;
                }

                DownloadInfo info =
                    DownloadInfo.activeDownloads.get(d.getId());

                if (info != null) {
                    info.togglePause();
                }

                dao.deleteDownload(d.getId());

                // Delete local file
                java.io.File file =
                    new java.io.File(d.getFileName());

                if (file.exists()) {
                    file.delete();
                }

                clearBindings();

                setStatus("🗑 Deleted: " + d.getFileName());

                onRefreshClicked();
            });
        }

        // =====================================================
        // CELL UPDATE
        // =====================================================

        @Override
        protected void updateItem(Download d, boolean empty) {

            super.updateItem(d, empty);

            // IMPORTANT:
            // Unbind old recycled bindings first
            pauseItem.disableProperty().unbind();
            resumeItem.disableProperty().unbind();
            cancelItem.disableProperty().unbind();

            pauseItem.styleProperty().unbind();
            resumeItem.styleProperty().unbind();
            cancelItem.styleProperty().unbind();

            if (empty || d == null) {

                setGraphic(null);

                return;
            }

            // =================================================
            // PAUSE BUTTON LOGIC
            // Enabled ONLY when DOWNLOADING
            // =================================================

            pauseItem.disableProperty().bind(

                Bindings.createBooleanBinding(

                    () ->
                        !"DOWNLOADING".equals(d.getStatus()),

                    d.statusProperty()
                )
            );

            // =================================================
            // RESUME BUTTON LOGIC
            // Enabled ONLY when PAUSED
            // =================================================

            resumeItem.disableProperty().bind(

                Bindings.createBooleanBinding(

                    () ->
                        !"PAUSED".equals(d.getStatus()),

                    d.statusProperty()
                )
            );

            // =================================================
            // CANCEL BUTTON LOGIC
            // Enabled when DOWNLOADING or PAUSED
            // =================================================

            cancelItem.disableProperty().bind(

                Bindings.createBooleanBinding(

                    () ->
                        !"DOWNLOADING".equals(d.getStatus())
                        &&
                        !"PAUSED".equals(d.getStatus()),

                    d.statusProperty()
                )
            );

            // =================================================
            // VISUAL FADE / BLUR EFFECT
            // =================================================

            pauseItem.styleProperty().bind(

                Bindings.createStringBinding(

                    () ->
                        pauseItem.isDisable()
                            ? "-fx-opacity: 0.45;"
                            : "-fx-opacity: 1;",

                    pauseItem.disableProperty()
                )
            );

            resumeItem.styleProperty().bind(

                Bindings.createStringBinding(

                    () ->
                        resumeItem.isDisable()
                            ? "-fx-opacity: 0.45;"
                            : "-fx-opacity: 1;",

                    resumeItem.disableProperty()
                )
            );

            cancelItem.styleProperty().bind(

                Bindings.createStringBinding(

                    () ->
                        cancelItem.isDisable()
                            ? "-fx-opacity: 0.45;"
                            : "-fx-opacity: 1;",

                    cancelItem.disableProperty()
                )
            );

            // Delete always enabled
            deleteItem.setDisable(false);

            setGraphic(actionBtn);
        }
    });
}

    // =========================================================
    // AUTO REFRESH
    // =========================================================

    private void setupAutoRefresh() {

        Timeline timeline = new Timeline(
                new KeyFrame(
                        Duration.seconds(1),
                        e -> onRefreshClicked()
                )
        );

        timeline.setCycleCount(Animation.INDEFINITE);

        timeline.play();
    }

    // =========================================================
    // PROGRESS BAR UPDATE
    // =========================================================

    private void updateProgressBar(
            List<Download> downloads
    ) {

        Download active = null;

        for (Download d : downloadList) {

            if ("DOWNLOADING".equals(d.getStatus())) {

                active = d;
                break;
            }
        }

        if (active != null) {

            if (activeDownloadId != active.getId()) {

                activeDownloadId = active.getId();

                Download activeD = active;

                clearBindings();

                progressBar.progressProperty().bind(
                        activeD.progressProperty()
                                .divide(100.0)
                );

                statusLabel.textProperty().bind(
                        Bindings.createStringBinding(
                                () ->
                                        "Downloading: "
                                                + activeD.getFileName()
                                                + " "
                                                + String.format(
                                                "%.1f%%",
                                                activeD.getProgress()
                                        ),
                                activeD.progressProperty()
                        )
                );
            }

        } else {

            if (activeDownloadId != -1) {

                activeDownloadId = -1;

                clearBindings();
            }

            Download selected =
                    table.getSelectionModel()
                            .getSelectedItem();

            if (selected != null) {

                clearBindings();

                setProgress(
                        selected.getProgress() / 100.0
                );

                setStatus(
                        selected.getFileName()
                                + " — "
                                + selected.getStatus()
                                + " "
                                + String.format(
                                "%.1f%%",
                                selected.getProgress()
                        )
                );

            } else {

                clearBindings();

                setProgress(0);

                setStatus("Ready");
            }
        }
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    @FXML
    public void onDownloadsNavClicked() {

        downloadsView.setVisible(true);
        downloadsView.setManaged(true);

        settingsView.setVisible(false);
        settingsView.setManaged(false);

        Page_Name.setText("Downloads");
    }

    @FXML
    public void onSettingsNavClicked() {

        downloadsView.setVisible(false);
        downloadsView.setManaged(false);

        settingsView.setVisible(true);
        settingsView.setManaged(true);

        Page_Name.setText("Settings");

        savePathField.setText(
                download.manager.config.SettingsManager
                        .getSavePath()
        );

        if ("dark".equals(
                download.manager.config.SettingsManager
                        .getTheme()
        )) {

            darkThemeRadio.setSelected(true);

        } else {

            lightThemeRadio.setSelected(true);
        }
    }

    // =========================================================
    // SETTINGS
    // =========================================================

    @FXML
    public void onBrowseSavePathClicked() {

        javafx.stage.DirectoryChooser chooser =
                new javafx.stage.DirectoryChooser();

        chooser.setTitle(
                "Select Default Save Location"
        );

        java.io.File dir =
                chooser.showDialog(
                        table.getScene().getWindow()
                );

        if (dir != null) {

            savePathField.setText(
                    dir.getAbsolutePath()
            );
        }
    }

    @FXML
    public void onSaveSettingsClicked() {

        String savePath =
                savePathField.getText().trim();

        String theme =
                darkThemeRadio.isSelected()
                        ? "dark"
                        : "light";

        download.manager.config.SettingsManager
                .saveSettings(savePath, theme);

        javafx.scene.Scene scene =
                table.getScene();

        if (scene != null) {

            download.manager.config.SettingsManager
                    .applyTheme(scene);
        }

        setStatus("✓ Settings saved!");
    }
}