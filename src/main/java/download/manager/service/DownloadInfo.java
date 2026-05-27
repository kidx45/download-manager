package download.manager.service;

import download.manager.model.Download;
import download.manager.storage.DAO;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadInfo {

    private static final int NUM_THREADS = 1;

    // Static map — tracks all currently active downloads
    public static final Map<Integer, DownloadInfo> activeDownloads
            = new ConcurrentHashMap<>();

    private final String downloadUrl;
    private final DAO dao;

    // -1 means new download, anything else means resuming existing one
    private int downloadId;

    // How many bytes were already downloaded before this session
    // 0 for new downloads, >0 for resumed ones
    private final long startFromByte;

    // Pause/resume fields
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();

    // ─── Constructor for NEW download ─────────────────────────
    public DownloadInfo(String downloadUrl, DAO dao) {
        this.downloadUrl = downloadUrl;
        this.dao = dao;
        this.downloadId = -1;
        this.startFromByte = 0;
    }

    // ─── Constructor for RESUMING existing download ───────────
    public DownloadInfo(String downloadUrl, DAO dao, int existingId, long bytesAlreadyDownloaded) {
        this.downloadUrl = downloadUrl;
        this.dao = dao;
        this.downloadId = existingId;
        this.startFromByte = bytesAlreadyDownloaded;
    }

    // Getters for pause used by Downloader threads
    public boolean isPaused()    { return paused; }
    public Object getPauseLock() { return pauseLock; }

    // Toggle pause on/off
    public void togglePause() {
        paused = !paused;
    }

    public void start() {
        try {
            // STEP 1: Parse URL and file name
            URL url = new URL(downloadUrl);
            String fileName = downloadUrl.substring(
                    downloadUrl.lastIndexOf('/') + 1);
            System.out.println("File: " + fileName);

            // STEP 2: Get file size
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Referer", "https://www.google.com");
            conn.setRequestProperty("Accept", "application/octet-stream,*/*");
            conn.connect();

            String contentLength = conn.getHeaderField("Content-Length");
            long fileSize = contentLength != null
                    ? Long.parseLong(contentLength)
                    : conn.getContentLengthLong();
            conn.disconnect();

            if (fileSize <= 0) {
                System.out.println("✗ Could not get file size.");
                return;
            }
            System.out.printf("Size: %.2f MB%n", fileSize / (1024.0 * 1024.0));

            // STEP 3: Only insert to DB if this is a NEW download
            // If resuming, we already have an ID
            if (downloadId == -1) {
                Download download = new Download(downloadUrl, fileName, fileSize, fileName);
                downloadId = dao.addDownload(download);
                if (downloadId == -1) {
                    System.out.println("✗ Failed to save to DB.");
                    return;
                }
            }

            // STEP 4: Register as active so pause button can find us
            activeDownloads.put(downloadId, this);

            // STEP 5: Open output file
            // If resuming, don't reset the file — keep existing bytes
            RandomAccessFile outputFile = new RandomAccessFile(fileName, "rw");
            if (startFromByte == 0) {
                // New download — pre-allocate full file size
                outputFile.setLength(fileSize);
            }

            // STEP 6: Calculate chunks starting from where we left off
            // If new: startFromByte = 0, downloads everything
            // If resuming: startFromByte = 5000000, skips first 5MB
            long remainingBytes = fileSize - startFromByte;
            long chunkSize = remainingBytes / NUM_THREADS;
            long remainder = remainingBytes % NUM_THREADS;

            System.out.printf("Starting from byte %d (%.2f MB remaining)%n",
                    startFromByte, remainingBytes / (1024.0 * 1024.0));

            // STEP 7: Update DB to DOWNLOADING
            dao.updateStatus(downloadId, "DOWNLOADING");

            // STEP 8: Shared counter starts from bytes already downloaded
            AtomicLong totalBytesDownloaded = new AtomicLong(startFromByte);

            // STEP 9: Launch thread pool
            ExecutorService threadPool = Executors.newFixedThreadPool(NUM_THREADS);

            long start = startFromByte;
            for (int i = 0; i < NUM_THREADS; i++) {
                long end = (i == NUM_THREADS - 1)
                        ? start + chunkSize + remainder - 1
                        : start + chunkSize - 1;

                threadPool.submit(new Downloader(
                        url, start, end, i + 1,
                        outputFile, dao, downloadId,
                        totalBytesDownloaded, fileSize, this
                ));

                start = end + 1;
            }

            // STEP 10: Wait for all threads to finish
            threadPool.shutdown();
            boolean allDone = threadPool.awaitTermination(
                    Long.MAX_VALUE, TimeUnit.MILLISECONDS);

            // STEP 11: Update final status
            if (allDone && !paused) {
                // Fully completed — mark as done, disable resume
                dao.markCompleted(downloadId);
                System.out.println("✓ COMPLETED: " + fileName);
            } else if (paused) {
                // Paused — save exactly where we stopped
                long bytesSaved = totalBytesDownloaded.get();
                dao.updateBytesDownloaded(downloadId, bytesSaved);
                dao.updateStatus(downloadId, "PAUSED");
                System.out.printf("⏸ PAUSED: %s at %.2f MB%n",
                        fileName, bytesSaved / (1024.0 * 1024.0));
            }

            outputFile.close();

        } catch (IOException e) {
            System.out.println("✗ IO Error: " + e.getMessage());
            if (downloadId != -1) dao.updateStatus(downloadId, "FAILED");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (downloadId != -1) dao.updateStatus(downloadId, "FAILED");
        } finally {
            // Always remove from active map when done
            activeDownloads.remove(downloadId);
        }
    }
}