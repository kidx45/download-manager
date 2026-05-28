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
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class DownloadsController implements Initializable {

    @FXML private TextField urlField;
    @FXML private Button startBtn;
    @FXML private Button refreshBtn;
    @FXML private ProgressBar progressBar;

    // Table
    @FXML private TableView<Download> table;
    @FXML private TableColumn<Download, Number> idCol;
    @FXML private TableColumn<Download, String> fileCol;
    @FXML private TableColumn<Download, String> statusCol;
    @FXML private TableColumn<Download, String> progressCol;
    @FXML private TableColumn<Download, String> urlCol;

    // <Row Type, Column Type>, so this row will display of type download while the column is of type Download since
    // The action button when pausing needs the Download row ID and other data to continue from the paused place 
    @FXML private TableColumn<Download, Download> actionsCol;

    private final DAO dao = new DAO();

    private final ObservableList<Download> downloadList = FXCollections.observableArrayList();

    private int activeDownloadId = -1;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTableColumns();
        setupActionsColumn();
        setupAutoRefresh();
        onRefreshClicked();
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

    private void updateDownloadList(List<Download> newDownloads) {
        java.util.Map<Integer, Download> existingMap = new java.util.HashMap<>();
        for (Download d : downloadList) {
            existingMap.put(d.getId(), d);
        }

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

        while (downloadList.size() > newDownloads.size()) {
            downloadList.remove(downloadList.size() - 1);
        }
    }

    private void resumeDownload(Download d) {
        setStatus("▶ Resuming: " + d.getFileName());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                DownloadInfo info = new DownloadInfo(
                        d.getUrl(), dao, d.getId(),
                        d.getBytesDownloaded(), d.getSavePath());
                info.start();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            setStatus("✓ Completed: " + d.getFileName());
            setProgress(1.0);
            onRefreshClicked();
        });

        task.setOnFailed(e -> {
            setStatus("✗ Resume failed!");
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void setupTableColumns() {

        // This will set each column in the table such that when we want something from the row 
        // It will first extract the download object which in the model definition has an __Property()
        // methods that will return the data requested
        idCol.setCellValueFactory(data -> data.getValue().idProperty());
        fileCol.setCellValueFactory(data -> data.getValue().fileNameProperty());
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        progressCol.setCellValueFactory(data -> Bindings.createStringBinding(
                () -> String.format("%.1f%%", data.getValue().getProgress()),
                data.getValue().progressProperty()));
        urlCol.setCellValueFactory(data -> data.getValue().urlProperty());
        actionsCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));

        // When the downloads controller was initialized it will call onRefreshClicked which will call the updateDownloadList
        // Which will set the downloadList with a list of all downloads in the db
        table.setItems(downloadList);

        // This is if say a person clicks on a row and it is not downloading it is for the bar to show the remaining 
        // progress left but if not then that means something is downloading hence whe don't show the bar of the clicked
        // row
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            if (selected == null)
                return;

            boolean hasActive = false;
            for (Download d : downloadList) {
                if ("DOWNLOADING".equals(d.getStatus())) {
                    hasActive = true;
                    break;
                }
            }

            if (!hasActive) {
                setProgress(selected.getProgress() / 100.0);
                setStatus(selected.getFileName() + " — " + selected.getStatus()
                        + " " + String.format("%.1f%%", selected.getProgress()));
            }
        });
    }

    private void setupActionsColumn() {
        actionsCol.setCellFactory(col -> new TableCell<Download, Download>() {
            private final Button actionBtn = new Button("Actions");
            private final ContextMenu menu = new ContextMenu();
            private final MenuItem pauseItem = new MenuItem("Pause");
            private final MenuItem resumeItem = new MenuItem("Resume");
            private final MenuItem cancelItem = new MenuItem("Cancel");
            private final MenuItem deleteItem = new MenuItem("Delete");

            {
                menu.getItems().addAll(pauseItem, resumeItem, cancelItem, deleteItem);
                actionBtn.getStyleClass().add("action-button");
                actionBtn.setFocusTraversable(false);
                actionBtn.setPrefWidth(100);
                actionBtn.setOnAction(e -> menu.show(actionBtn, Side.BOTTOM, 0, 0));

                // If you wonder why not IdProperty and getId or get status then it is because we only want the
                // the string or the int not the property object as that is an object and we can't directly compare it 
                // with basic data types but for setCellValueFactory it needs the property
                pauseItem.setOnAction(e -> {
                    Download d = getItem();
                    if (d == null)
                        return;
                    if (!"DOWNLOADING".equals(d.getStatus())) {
                        setStatus("Can only pause active downloads!");
                        return;
                    }

                    DownloadInfo info = DownloadInfo.activeDownloads.get(d.getId());
                    if (info != null) {
                        info.togglePause();
                        setStatus("Pausing: " + d.getFileName());
                    } else {
                        setStatus("Download not active!");
                    }
                });

                resumeItem.setOnAction(e -> {
                    Download d = getItem();
                    if (d == null)
                        return;
                    if (!"PAUSED".equals(d.getStatus())) {
                        setStatus("Can only resume paused downloads!");
                        return;
                    }
                    resumeDownload(d);
                });

                cancelItem.setOnAction(e -> {
                    Download d = getItem();
                    if (d == null)
                        return;
                    if ("COMPLETED".equals(d.getStatus())) {
                        setStatus("Cannot cancel completed download!");
                        return;
                    }

                    DownloadInfo info = DownloadInfo.activeDownloads.get(d.getId());
                    if (info != null)
                        info.togglePause();

                    dao.updateStatus(d.getId(), "FAILED");
                    setStatus("Cancelled: " + d.getFileName());
                    onRefreshClicked();
                });

                deleteItem.setOnAction(e -> {
                    Download d = getItem();
                    if (d == null)
                        return;

                    DownloadInfo info = DownloadInfo.activeDownloads.get(d.getId());
                    if (info != null)
                        info.togglePause();

                    dao.deleteDownload(d.getId());

                    File file = new File(d.getFileName());
                    if (file.exists())
                        file.delete();

                    setStatus("Deleted: " + d.getFileName());
                    onRefreshClicked();
                });
            }

            @Override
            protected void updateItem(Download d, boolean empty) {
                super.updateItem(d, empty);

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

                pauseItem.disableProperty().bind(
                        Bindings.createBooleanBinding(
                                () -> !"DOWNLOADING".equals(d.getStatus()),
                                d.statusProperty()));

                resumeItem.disableProperty().bind(
                        Bindings.createBooleanBinding(
                                () -> !"PAUSED".equals(d.getStatus()),
                                d.statusProperty()));

                cancelItem.disableProperty().bind(
                        Bindings.createBooleanBinding(
                                () -> !"DOWNLOADING".equals(d.getStatus())
                                        && !"PAUSED".equals(d.getStatus()),
                                d.statusProperty()));

                pauseItem.styleProperty().bind(
                        Bindings.createStringBinding(
                                () -> pauseItem.isDisable() ? "-fx-opacity: 0.45;" : "-fx-opacity: 1;",
                                pauseItem.disableProperty()));

                resumeItem.styleProperty().bind(
                        Bindings.createStringBinding(
                                () -> resumeItem.isDisable() ? "-fx-opacity: 0.45;" : "-fx-opacity: 1;",
                                resumeItem.disableProperty()));

                cancelItem.styleProperty().bind(
                        Bindings.createStringBinding(
                                () -> cancelItem.isDisable() ? "-fx-opacity: 0.45;" : "-fx-opacity: 1;",
                                cancelItem.disableProperty()));

                deleteItem.setDisable(false);
                setGraphic(actionBtn);
            }
        });
    }

    private void setupAutoRefresh() {
        refreshBtn.setVisible(false);
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> onRefreshClicked()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    // Update Progress Bar
    private void updateProgressBar(List<Download> downloads) {
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
                progressBar.progressProperty().bind(activeD.progressProperty().divide(100.0));
            }

        } else {
            if (activeDownloadId != -1) {
                activeDownloadId = -1;
                progressBar.progressProperty().unbind();
                progressBar.setProgress(0);
            }

            Download selected = table.getSelectionModel().getSelectedItem();

            if (selected != null) {
                progressBar.progressProperty().unbind();
                setProgress(selected.getProgress() / 100.0);
            }
        }
    }

    // =========================================================
    // STATUS HELPERS
    // =========================================================

    private void setStatus(String text) {
        // Will be called from parent MainController
    }

    private void setProgress(double value) {
        progressBar.progressProperty().unbind();
        progressBar.setProgress(value);
    }
}
