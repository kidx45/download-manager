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

// SERVICE: Orchestrates a full file download
// Splits file into chunks and manages the thread pool
public class DownloadInfo {

    private static final int NUM_THREADS = 8;

    // Static map so pause/resume can find active downloads by ID
    // ConcurrentHashMap is thread-safe version of HashMap
    public static final Map<Integer, DownloadInfo> activeDownloads
            = new ConcurrentHashMap<>();

    private final String downloadUrl;
    private final DAO dao;

    // Pause/resume fields
    // volatile = all threads always see the latest value
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();

    private int downloadId = -1;

    public DownloadInfo(String downloadUrl, DAO dao) {
        this.downloadUrl = downloadUrl;
        this.dao = dao;
    }

    // Getters for pause/resume — used by Downloader threads
    public boolean isPaused()      { return paused; }
    public Object getPauseLock()   { return pauseLock; }

    // Toggle pause on/off
    public void togglePause() {
        paused = !paused;
        if (!paused) {
            // wake up all waiting threads
            synchronized (pauseLock) {
                pauseLock.notifyAll();
            }
        }
    }

    // Main method — call this to start the download
    public void start() {
        try {
            // STEP 1: Parse URL and get file name
            URL url = new URL(downloadUrl);
            String fileName = downloadUrl.substring(
                    downloadUrl.lastIndexOf('/') + 1);
            System.out.println("File: " + fileName);

            // STEP 2: Get file size using HEAD request
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
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

            // STEP 3: Save to DB with PENDING status
            Download download = new Download(downloadUrl, fileName, fileSize, fileName);
            downloadId = dao.addDownload(download);
            if (downloadId == -1) {
                System.out.println("✗ Failed to save to DB.");
                return;
            }

            // STEP 4: Register as active so pause button can find us
            activeDownloads.put(downloadId, this);

            // STEP 5: Create output file, pre-allocate exact size
            RandomAccessFile outputFile = new RandomAccessFile(fileName, "rw");
            outputFile.setLength(fileSize);

            // STEP 6: Calculate chunk sizes
            long chunkSize = fileSize / NUM_THREADS;
            long remainder = fileSize % NUM_THREADS;

            // STEP 7: Update DB to DOWNLOADING
            dao.updateStatus(downloadId, "DOWNLOADING");

            // STEP 8: Shared counter — all threads update this
            AtomicLong totalBytesDownloaded = new AtomicLong(0);

            // STEP 9: Create thread pool and submit chunks
            ExecutorService threadPool = Executors.newFixedThreadPool(NUM_THREADS);

            long startByte = 0;
            for (int i = 0; i < NUM_THREADS; i++) {
                long endByte;

                // last thread gets the leftover bytes
                if (i == NUM_THREADS - 1) {
                    endByte = startByte + chunkSize + remainder - 1;
                } else {
                    endByte = startByte + chunkSize - 1;
                }

                // submit one Downloader thread per chunk
                threadPool.submit(new Downloader(
                        url, startByte, endByte, i + 1,
                        outputFile, dao, downloadId,
                        totalBytesDownloaded, fileSize, this
                ));

                startByte = endByte + 1;
            }

            // STEP 10: Wait for all threads to finish
            threadPool.shutdown();
            boolean allDone = threadPool.awaitTermination(
                    Long.MAX_VALUE, TimeUnit.MILLISECONDS);

            // STEP 11: Update final status
            if (allDone) {
                dao.updateProgress(downloadId, 100.0);
                dao.updateStatus(downloadId, "COMPLETED");
                System.out.println("✓ COMPLETED: " + fileName);
            } else {
                dao.updateStatus(downloadId, "FAILED");
                System.out.println("✗ FAILED: " + fileName);
            }

            outputFile.close();

        } catch (IOException e) {
            System.out.println("✗ IO Error: " + e.getMessage());
            if (downloadId != -1) dao.updateStatus(downloadId, "FAILED");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (downloadId != -1) dao.updateStatus(downloadId, "FAILED");
        } finally {
            // always remove from active map when done
            if (downloadId != -1) activeDownloads.remove(downloadId);
        }
    }
}