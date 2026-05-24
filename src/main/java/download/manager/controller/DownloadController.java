package download.manager.controller;

import download.manager.model.Download;
import download.manager.service.DownloadInfo;
import download.manager.storage.DAO;
import download.manager.view.View;
import javafx.scene.control.ProgressBar;
import javafx.concurrent.Task;

import java.util.List;

// CONTROLLER: Handles all button click logic
// Sits between View (UI) and Service (download logic)
public class DownloadController {

    private final View view;
    private final DAO dao;

    public DownloadController(View view) {
        this.view = view;
        this.dao  = new DAO();
    }

    // Called when Start button is clicked
    public void onStartClicked(String url) {
        if (url.isEmpty()) {
            view.setStatus("Please enter a URL!");
            return;
        }

        view.setStartButtonDisabled(true);
        view.setStatus("Starting...");
        view.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        // Run download in background thread so UI doesn't freeze
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                DownloadInfo info = new DownloadInfo(url, dao);
                info.start();
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            view.setStatus("✓ Completed!");
            view.setProgress(1.0);
            view.setStartButtonDisabled(false);
            onRefreshClicked();
        });

        task.setOnFailed(e -> {
            view.setStatus("✗ Failed!");
            view.setProgress(0);
            view.setStartButtonDisabled(false);
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // Called when Pause button is clicked
    public void onPauseClicked() {
        if (DownloadInfo.activeDownloads.isEmpty()) {
            view.setStatus("No active downloads!");
            return;
        }

        DownloadInfo.activeDownloads.forEach((id, info) -> {
            info.togglePause();
            if (info.isPaused()) {
                view.setStatus("⏸ Paused download " + id);
                view.setPauseButtonText("Resume");
            } else {
                view.setStatus("▶ Resumed download " + id);
                view.setPauseButtonText("Pause / Resume");
            }
        });
    }

    // Called when Refresh button is clicked
    public void onRefreshClicked() {
        Task<List<Download>> task = new Task<>() {
            @Override
            protected List<Download> call() {
                return dao.getAllDownloads();
            }
        };

        task.setOnSucceeded(e -> view.updateTable(task.getValue()));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }
}