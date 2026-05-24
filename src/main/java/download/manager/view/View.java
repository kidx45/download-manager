package download.manager.view;

import download.manager.config.DB;
import download.manager.controller.DownloadController;
import download.manager.model.Download;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.List;

// VIEW: The JavaFX window — only handles UI, no business logic
// All button logic is delegated to DownloadController
public class View extends Application {

    // UI components
    private final TextField urlField      = new TextField();
    private final Button startBtn         = new Button("Start Download");
    private final Button pauseBtn         = new Button("Pause / Resume");
    private final Button refreshBtn       = new Button("Refresh");
    private final Label statusLabel       = new Label("Ready");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final TableView<Download> table = new TableView<>();

    // ObservableList — table auto-updates when this changes
    private final ObservableList<Download> downloadList
            = FXCollections.observableArrayList();

    // Controller handles all the logic
    private DownloadController controller;

    @Override
    public void start(Stage window) {
        window.setTitle("Download Manager");

        // Give controller a reference to update the UI
        controller = new DownloadController(this);

        // GridPane layout — rows and columns
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(15));
        grid.setHgap(10);
        grid.setVgap(10);

        // Row 0: URL input
        urlField.setPromptText("Paste download URL here...");
        urlField.setPrefWidth(500);
        grid.add(new Label("URL:"), 0, 0);
        grid.add(urlField, 1, 0, 3, 1);

        // Row 1: Buttons
        grid.add(startBtn,   0, 1);
        grid.add(pauseBtn,   1, 1);
        grid.add(refreshBtn, 2, 1);

        // Row 2: Progress bar + status label
        progressBar.setPrefWidth(500);
        grid.add(progressBar,  0, 2, 3, 1);
        grid.add(statusLabel,  3, 2);

        // Row 3: History table
        setupTable();
        grid.add(table, 0, 3, 4, 1);

        // Button actions — delegate to controller
        startBtn.setOnAction(e -> controller.onStartClicked(urlField.getText().trim()));
        pauseBtn.setOnAction(e -> controller.onPauseClicked());
        refreshBtn.setOnAction(e -> controller.onRefreshClicked());

        Scene scene = new Scene(grid, 820, 520);
        window.setScene(scene);
        window.show();

        // load history on startup
        controller.onRefreshClicked();
    }

    // ─── Methods the Controller calls to update UI ────────────
    // Controller never touches UI components directly
    // it calls these methods instead

    public void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    public void setProgress(double value) {
        Platform.runLater(() -> progressBar.setProgress(value));
    }

    public void setStartButtonDisabled(boolean disabled) {
        Platform.runLater(() -> startBtn.setDisable(disabled));
    }

    public void setPauseButtonText(String text) {
        Platform.runLater(() -> pauseBtn.setText(text));
    }

    public void updateTable(List<Download> downloads) {
        Platform.runLater(() -> {
            downloadList.clear();
            downloadList.addAll(downloads);
        });
    }

    // ─── Table setup ──────────────────────────────────────────
    private void setupTable() {
        TableColumn<Download, Number> idCol = new TableColumn<>("ID");
        idCol.setPrefWidth(40);
        idCol.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().getId()));

        TableColumn<Download, String> fileCol = new TableColumn<>("File Name");
        fileCol.setPrefWidth(220);
        fileCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getFileName()));

        TableColumn<Download, String> statusCol = new TableColumn<>("Status");
        statusCol.setPrefWidth(100);
        statusCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getStatus()));

        TableColumn<Download, Number> progressCol = new TableColumn<>("Progress");
        progressCol.setPrefWidth(80);
        progressCol.setCellValueFactory(data ->
                new SimpleDoubleProperty(data.getValue().getProgress()));

        TableColumn<Download, String> urlCol = new TableColumn<>("URL");
        urlCol.setPrefWidth(300);
        urlCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getUrl()));

        table.getColumns().addAll(idCol, fileCol, statusCol, progressCol, urlCol);
        table.setItems(downloadList);
        table.setPrefHeight(300);
    }

    // Close DB when window closes
    @Override
    public void stop() {
        DB.closeConnection();
    }
}