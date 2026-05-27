package download.manager.controller;

import download.manager.model.Download;
import download.manager.service.DownloadInfo;
import download.manager.storage.DAO;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class DownloadController implements Initializable {

    // ─── @FXML fields must match fx:id in View.fxml exactly ──
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

    private final DAO dao = new DAO();
    private final ObservableList<Download> downloadList
            = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTableColumns();
        setupActionsColumn();
        setupAutoRefresh();
        onRefreshClicked();


    }

    // ─── Button actions ───────────────────────────────────────

    @FXML
    public void onStartClicked() {
        String url = urlField.getText().trim();

        if (url.isEmpty()) {
            statusLabel.setText("Please enter a URL!");
            return;
        }

        startBtn.setDisable(true);
        statusLabel.setText("Starting...");
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                DownloadInfo info = new DownloadInfo(url, dao);
                info.start();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            statusLabel.setText("✓ Completed!");
            progressBar.setProgress(1.0);
            startBtn.setDisable(false);
            onRefreshClicked();
        });

        task.setOnFailed(e -> {
            statusLabel.setText("✗ Failed!");
            progressBar.setProgress(0);
            startBtn.setDisable(false);
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    public void onPauseClicked() {
        Download selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a download first!");
            return;
        }
        if (!"DOWNLOADING".equals(selected.getStatus())) {
            statusLabel.setText("Can only pause active downloads!");
            return;
        }
        DownloadInfo info = DownloadInfo.activeDownloads.get(selected.getId());
        if (info != null) {
            info.togglePause();
            statusLabel.setText("⏸ Pausing: " + selected.getFileName());
        } else {
            statusLabel.setText("Download not active in memory!");
        }
    }

    @FXML
    public void onResumeClicked() {
        Download selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a download to resume!");
            return;
        }
        if (!"PAUSED".equals(selected.getStatus())) {
            statusLabel.setText("Can only resume paused downloads!");
            return;
        }
        resumeDownload(selected);
    }

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

    private void updateDownloadList(List<Download> newDownloads) {
        // Map existing items by ID for quick lookup
        java.util.Map<Integer, Download> existingMap = new java.util.HashMap<>();
        for (Download d : downloadList) {
            existingMap.put(d.getId(), d);
        }

        // Update fields of existing items in place
        for (Download newD : newDownloads) {
            Download existing = existingMap.get(newD.getId());
            if (existing != null) {
                existing.setStatus(newD.getStatus());
                existing.setProgress(newD.getProgress());
                existing.setBytesDownloaded(newD.getBytesDownloaded());
                existing.setResumable(newD.isResumable());
                existing.setFileName(newD.getFileName());
            }
        }

        // Rebuild and align the order of downloadList with newDownloads in-place
        for (int i = 0; i < newDownloads.size(); i++) {
            Download newD = newDownloads.get(i);
            int currentIndex = -1;
            for (int j = i; j < downloadList.size(); j++) {
                if (downloadList.get(j).getId() == newD.getId()) {
                    currentIndex = j;
                    break;
                }
            }
            if (currentIndex != -1) {
                if (currentIndex != i) {
                    Download temp = downloadList.remove(currentIndex);
                    downloadList.add(i, temp);
                }
            } else {
                downloadList.add(i, newD);
            }
        }

        // Remove trailing items
        while (downloadList.size() > newDownloads.size()) {
            downloadList.remove(downloadList.size() - 1);
        }
    }

    // ─── Private helpers ──────────────────────────────────────

    // Extracted resume logic so both the button and
    // the actions dropdown can call the same method
    private void resumeDownload(Download d) {
        statusLabel.setText("▶ Resuming: " + d.getFileName());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                DownloadInfo info = new DownloadInfo(
                    d.getUrl(), dao,
                    d.getId(),
                    d.getBytesDownloaded()
                );
                info.start();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            statusLabel.setText("✓ Completed: " + d.getFileName());
            progressBar.setProgress(1.0);
            onRefreshClicked();
        });

        task.setOnFailed(e -> statusLabel.setText("✗ Resume failed!"));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void setupTableColumns() {
        idCol.setCellValueFactory(data -> data.getValue().idProperty());
        fileCol.setCellValueFactory(data -> data.getValue().fileNameProperty());
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());

        // Bind progress percentage reactively using StringBinding
        progressCol.setCellValueFactory(data -> 
            javafx.beans.binding.Bindings.createStringBinding(
                () -> String.format("%.1f%%", data.getValue().getProgress()),
                data.getValue().progressProperty()
            )
        );

        urlCol.setCellValueFactory(data -> data.getValue().urlProperty());
        actionsCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));

        table.setItems(downloadList);

        // Update pause/resume buttons when row is clicked
        table.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, selected) -> {
                if (selected != null) {
                    progressBar.setProgress(selected.getProgress() / 100.0);
                    statusLabel.setText(selected.getFileName()
                            + " — " + selected.getStatus()
                            + " " + String.format("%.1f%%", selected.getProgress()));
                }
            }
        );
    }

    private void setupActionsColumn() {
        actionsCol.setCellFactory(col -> new TableCell<Download, Download>() {

            // ContextMenu renders as a popup ABOVE everything
            // unlike MenuButton which gets clipped by the table
            private final Button actionBtn    = new Button("Actions ▾");
            private final ContextMenu menu    = new ContextMenu();
            private final MenuItem pauseItem  = new MenuItem("⏸ Pause");
            private final MenuItem resumeItem = new MenuItem("▶ Resume");
            private final MenuItem cancelItem = new MenuItem("✗ Cancel");
            private final MenuItem deleteItem = new MenuItem("🗑 Delete");

            {
                menu.getItems().addAll(pauseItem, resumeItem, cancelItem, deleteItem);
                actionBtn.setFocusTraversable(false);
                actionBtn.setPrefWidth(100);

                // Show popup BELOW the button
                // Side.BOTTOM ensures it always appears on top of table
                actionBtn.setOnAction(e ->
                    menu.show(actionBtn, Side.BOTTOM, 0, 0)
                );

                // ─── Pause ────────────────────────────────
                pauseItem.setOnAction(e -> {
                    Download d = getItem();
                    if (d == null || !"DOWNLOADING".equals(d.getStatus())) {
                        statusLabel.setText("Can only pause active downloads!");
                        return;
                    }
                    DownloadInfo info = DownloadInfo.activeDownloads.get(d.getId());
                    if (info != null) {
                        info.togglePause();
                        statusLabel.setText("⏸ Pausing: " + d.getFileName());
                    } else {
                        statusLabel.setText("Download not active!");
                    }
                });

                // ─── Resume ───────────────────────────────
                resumeItem.setOnAction(e -> {
                    Download d = getItem();
                    if (d == null || !"PAUSED".equals(d.getStatus())) {
                        statusLabel.setText("Can only resume paused downloads!");
                        return;
                    }
                    resumeDownload(d);
                });

                // ─── Cancel ───────────────────────────────
                cancelItem.setOnAction(e -> {
                    Download d = getItem();
                    if (d == null) return;
                    if ("COMPLETED".equals(d.getStatus())) {
                        statusLabel.setText("Cannot cancel completed download!");
                        return;
                    }
                    DownloadInfo info = DownloadInfo.activeDownloads.get(d.getId());
                    if (info != null) info.togglePause();
                    dao.updateStatus(d.getId(), "FAILED");
                    statusLabel.setText("✗ Cancelled: " + d.getFileName());
                    onRefreshClicked();
                });

                // ─── Delete ───────────────────────────────
                deleteItem.setOnAction(e -> {
                    Download d = getItem();
                    if (d == null) return;
                    DownloadInfo info = DownloadInfo.activeDownloads.get(d.getId());
                    if (info != null) {
                        info.togglePause();
                    }
                    dao.deleteDownload(d.getId());
                    // Delete local file
                    java.io.File file = new java.io.File(d.getFileName());
                    if (file.exists()) {
                        file.delete();
                    }
                    statusLabel.setText("🗑 Deleted: " + d.getFileName());
                    onRefreshClicked();
                });
            }

            @Override
            protected void updateItem(Download d, boolean empty) {
                super.updateItem(d, empty);

                // Unbind first to prevent memory leaks or incorrect updates when cells are recycled
                pauseItem.disableProperty().unbind();
                resumeItem.disableProperty().unbind();
                cancelItem.disableProperty().unbind();

                if (empty || d == null) {
                    setGraphic(null);
                    return;
                }

                // Bind disabled properties reactively to the Download's status property.
                // This means the context menu items update their states instantly in real-time,
                // even while the menu is currently open, with absolutely zero flickering!
                pauseItem.disableProperty().bind(
                    javafx.beans.binding.Bindings.createBooleanBinding(
                        () -> !"DOWNLOADING".equals(d.getStatus()),
                        d.statusProperty()
                    )
                );
                resumeItem.disableProperty().bind(
                    javafx.beans.binding.Bindings.createBooleanBinding(
                        () -> !"PAUSED".equals(d.getStatus()),
                        d.statusProperty()
                    )
                );
                cancelItem.disableProperty().bind(
                    javafx.beans.binding.Bindings.createBooleanBinding(
                        () -> !"DOWNLOADING".equals(d.getStatus()) && !"PAUSED".equals(d.getStatus()),
                        d.statusProperty()
                    )
                );
                deleteItem.setDisable(false);

                setGraphic(actionBtn);
            }
        });
    }

    private void setupAutoRefresh() {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> onRefreshClicked())
        );
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void updateProgressBar(List<Download> downloads) {
        for (Download d : downloads) {
            if ("DOWNLOADING".equals(d.getStatus())) {
                progressBar.setProgress(d.getProgress() / 100.0);
                statusLabel.setText("Downloading: " + d.getFileName()
                        + " " + String.format("%.1f%%", d.getProgress()));
                return;
            }
            if ("PAUSED".equals(d.getStatus())) {
                progressBar.setProgress(d.getProgress() / 100.0);
                statusLabel.setText("⏸ Paused: " + d.getFileName());
                return;
            }
        }
        progressBar.setProgress(0);
        statusLabel.setText("Ready");
    }
}