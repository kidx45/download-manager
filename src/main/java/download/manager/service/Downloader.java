package download.manager.service;

import download.manager.storage.DAO;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicLong;

// SERVICE: One thread that downloads a single byte range (chunk)
// Implements Runnable so it can run in a thread pool
public class Downloader implements Runnable {

    private final URL downloadURL;
    private final long startByte;       // where this chunk starts
    private final long endByte;         // where this chunk ends
    private final int threadNum;        // just for logging
    private final RandomAccessFile outputFile;  // shared file all threads write to
    private final DAO dao;
    private final int downloadId;
    private final AtomicLong totalBytesDownloaded; // shared counter across all threads
    private final long totalFileSize;
    private final DownloadInfo downloadInfo;       // needed for pause/resume

    // Constructor — sets up everything this thread needs
    public Downloader(URL downloadURL, long startByte, long endByte, int threadNum,
                      RandomAccessFile outputFile, DAO dao, int downloadId,
                      AtomicLong totalBytesDownloaded, long totalFileSize,
                      DownloadInfo downloadInfo) {
        this.downloadURL = downloadURL;
        this.startByte = startByte;
        this.endByte = endByte;
        this.threadNum = threadNum;
        this.outputFile = outputFile;
        this.dao = dao;
        this.downloadId = downloadId;
        this.totalBytesDownloaded = totalBytesDownloaded;
        this.totalFileSize = totalFileSize;
        this.downloadInfo = downloadInfo;
    }

    // run() is called when the thread starts
    @Override
    public void run() {
        HttpURLConnection connection = null;
        try {
            System.out.printf("Thread %d starting: bytes %d → %d%n",
                    threadNum, startByte, endByte);

            // Open HTTP connection and request only our byte range
            connection = (HttpURLConnection) downloadURL.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setRequestProperty("Referer", "https://www.google.com");
            connection.setRequestProperty("Accept", "application/octet-stream,*/*");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            connection.setRequestProperty("Range", "bytes=" + startByte + "-" + endByte);

            // 206 = server supports range requests
            // 200 = server sends whole file (still works)
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_PARTIAL
                    && responseCode != HttpURLConnection.HTTP_OK) {
                System.out.println("✗ Thread " + threadNum + " got code: " + responseCode);
                return;
            }

            InputStream stream = connection.getInputStream();
            byte[] buffer = new byte[4096]; // read 4KB at a time
            int bytesRead;
            long currentPosition = startByte;

            if (responseCode == HttpURLConnection.HTTP_OK && startByte > 0) {
                System.out.println("⚠ Server did not return partial content (HTTP 206). Resetting to download from scratch.");
                currentPosition = 0;
                totalBytesDownloaded.set(0);
            }

            while ((bytesRead = stream.read(buffer)) != -1) {

                // ─── PAUSE CHECK ──────────────────────────────
                if (downloadInfo.isPaused()) {
                    System.out.println("Thread " + threadNum + " pausing (terminating cleanly)...");
                    return;
                }
                // ──────────────────────────────────────────────

                // synchronized write — seek + write must happen
                // together so threads don't interfere with each other
                synchronized (outputFile) {
                    outputFile.seek(currentPosition);
                    outputFile.write(buffer, 0, bytesRead);
                }
                currentPosition += bytesRead;

                // Update shared progress counter
                // AtomicLong is thread-safe — no synchronized needed
                long downloaded = totalBytesDownloaded.addAndGet(bytesRead);
                double progress = (downloaded * 100.0) / totalFileSize;
                dao.updateProgress(downloadId, Math.min(progress, 100.0));
            }

            System.out.printf("✓ Thread %d finished!%n", threadNum);

        } catch (IOException e) {
            System.out.println("✗ Thread " + threadNum + " error: " + e.getMessage());
        } finally {
            // always disconnect even if something went wrong
            if (connection != null) connection.disconnect();
        }
    }
}