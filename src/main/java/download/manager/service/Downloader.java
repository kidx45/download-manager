package download.manager.service;

import download.manager.model.Chunk;
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
    private final Chunk chunk;
    private final int threadNum;        // just for logging
    private final RandomAccessFile outputFile;  // shared file all threads write to
    private final DAO dao;
    private final int downloadId;
    private final AtomicLong totalBytesDownloaded; // shared counter across all threads
    private final long totalFileSize;
    private final DownloadInfo downloadInfo;       // needed for pause/resume

    // Constructor — sets up everything this thread needs
    public Downloader(URL downloadURL, Chunk chunk, int threadNum,
                      RandomAccessFile outputFile, DAO dao, int downloadId,
                      AtomicLong totalBytesDownloaded, long totalFileSize,
                      DownloadInfo downloadInfo) {
        this.downloadURL = downloadURL;
        this.chunk = chunk;
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
            long startByte = chunk.getCurrentByte();
            long endByte = chunk.getEndByte();

            System.out.printf("Thread %d starting: bytes %d → %d%n",
                    threadNum, startByte, endByte);

            // Open HTTP connection and request only our byte range
            connection = (HttpURLConnection) downloadURL.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Referer", "https://www.google.com");
            connection.setRequestProperty("Accept", "application/octet-stream,*/*");
            connection.setRequestProperty("Range", "bytes=" + startByte + "-" + endByte);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_PARTIAL
                    && responseCode != HttpURLConnection.HTTP_OK) {
                System.out.println("✗ Thread " + threadNum + " got code: " + responseCode);
                return;
            }

            InputStream stream = connection.getInputStream();
            byte[] buffer = new byte[8192]; // read 8KB at a time
            int bytesRead;
            long currentPosition = startByte;
            long bytesSinceLastUpdate = 0;

            while ((bytesRead = stream.read(buffer)) != -1) {

                // ─── PAUSE CHECK ──────────────────────────────
                if (downloadInfo.isPaused()) {
                    System.out.println("Thread " + threadNum + " pausing...");
                    // Save current position before exiting
                    chunk.setCurrentByte(currentPosition);
                    dao.updateChunkProgress(chunk.getId(), currentPosition);
                    return;
                }
                // ──────────────────────────────────────────────

                synchronized (outputFile) {
                    outputFile.seek(currentPosition);
                    outputFile.write(buffer, 0, bytesRead);
                }
                currentPosition += bytesRead;
                bytesSinceLastUpdate += bytesRead;

                // Update shared progress counter
                totalBytesDownloaded.addAndGet(bytesRead);
                
                // Update DB every 512KB to avoid too many writes but keep progress safe
                if (bytesSinceLastUpdate > 512 * 1024) {
                    chunk.setCurrentByte(currentPosition);
                    dao.updateChunkProgress(chunk.getId(), currentPosition);
                    
                    // Also update global progress for UI
                    double progress = (totalBytesDownloaded.get() * 100.0) / totalFileSize;
                    dao.updateProgress(downloadId, Math.min(progress, 100.0));
                    bytesSinceLastUpdate = 0;
                }
            }

            // Final update when finished
            chunk.setCurrentByte(currentPosition);
            dao.updateChunkProgress(chunk.getId(), currentPosition);
            
            System.out.printf("✓ Thread %d finished!%n", threadNum);

        } catch (IOException e) {
            System.out.println("✗ Thread " + threadNum + " error: " + e.getMessage());
            // Save what we have even on error
            if (chunk != null) {
                dao.updateChunkProgress(chunk.getId(), chunk.getCurrentByte());
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}