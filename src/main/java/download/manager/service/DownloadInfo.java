package download.manager.service;

import download.manager.model.Download;
import download.manager.model.Chunk;
import download.manager.storage.DAO;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadInfo {

    private static final int NUM_THREADS = 5;

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

    private String savePath;

    // ─── Constructor for NEW download ─────────────────────────
    public DownloadInfo(String downloadUrl, DAO dao) {
        this.downloadUrl = downloadUrl;
        this.dao = dao;
        this.downloadId = -1;
        this.startFromByte = 0;
        this.savePath = null;
    }

    // ─── Constructor for RESUMING existing download ───────────
    public DownloadInfo(String downloadUrl, DAO dao, int existingId, long bytesAlreadyDownloaded, String savePath) {
        this.downloadUrl = downloadUrl;
        this.dao = dao;
        this.downloadId = existingId;
        this.startFromByte = bytesAlreadyDownloaded;
        this.savePath = savePath;
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

            // STEP 2: Get file from the url and this is done by
            // since the links expect a browser to send a request to the download link
            // we would have to configure our request so it simulates it being sent from
            // a browser
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Referer", "https://www.google.com");
            conn.setRequestProperty("Accept", "application/octet-stream,*/*");
            conn.connect();

            // Get the file size from the connection with server storing the data we want
            String contentLength = conn.getHeaderField("Content-Length");
            long fileSize = contentLength != null
                    ? Long.parseLong(contentLength)
                    : conn.getContentLengthLong();
            // Remember to disconnect after getting the file size since we will reconnect in each thread to download byte ranges
            // This is not the connection we will use for downloading, just a quick one to get the file size and check if the URL is valid
            conn.disconnect();


            if (fileSize <= 0) {
                System.out.println("✗ Could not get file size.");
                return;
            }

            System.out.printf("Size: %.2f MB%n", fileSize / (1024.0 * 1024.0));

            // Determine save path if it is new
            // Then it won't have one hence the info associated with the download file
            // it will be given a savePath on the first condition else if it was already
            // being downloaded but didn't finish then the path will be be retrieved
            // in case thought if it doesn't have one it will be assigned the current savePath
            // given by the user
            String saveDir = download.manager.config.SettingsManager.getSavePath();
            File file;
            if (downloadId == -1) {
                file = new File(saveDir, fileName);
                savePath = file.getAbsolutePath();
            } else {
                if (savePath == null || savePath.isEmpty()) {
                    file = new File(saveDir, fileName);
                    savePath = file.getAbsolutePath();
                } else {
                    file = new File(savePath);
                }
            }

            // STEP 3: Only insert to DB if this is a NEW download
            // If resuming, we already have an ID
            if (downloadId == -1) {
                Download download = new Download(downloadUrl, fileName, fileSize, savePath);
                downloadId = dao.addDownload(download);
                if (downloadId == -1) {
                    System.out.println("✗ Failed to save to DB.");
                    return;
                }
            }

            // STEP 4: Register as active so pause button can find us
            activeDownloads.put(downloadId, this);

            // STEP 5: Open output file
            // So a RandomAccessFile is a type of file that you can read and write at a specific
            // byte position meaning
            // If resuming, don't reset the file — keep existing bytes
            RandomAccessFile outputFile = new RandomAccessFile(file, "rw");
            if (startFromByte == 0) {
                // New download — pre-allocate full file size
                outputFile.setLength(fileSize);
            }

            // STEP 6: Manage Chunks (New or Resume)
            List<Chunk> chunks;

            chunks = dao.getChunks(downloadId);
            if (chunks.isEmpty()) {
                // Create chunks if they don't exist (New download or corrupted state)
                chunks = new ArrayList<>();
                long chunkSize = fileSize / NUM_THREADS;
                long remainder = fileSize % NUM_THREADS;
                long start = 0;

                for (int i = 0; i < NUM_THREADS; i++) {
                    long end = (i == NUM_THREADS - 1)
                            ? start + chunkSize + remainder - 1
                            : start + chunkSize - 1;

                    Chunk chunk = new Chunk(
                            downloadId, start, end, start
                    );
                    dao.addChunk(chunk);
                    chunks.add(chunk);
                    start = end + 1;
                }
                // Refresh to get DB IDs
                chunks = dao.getChunks(downloadId);
            }

            // Calculate total bytes already downloaded across all chunks
            long totalDownloadedSoFar = 0;
            for (Chunk c : chunks) {
                totalDownloadedSoFar += (c.getCurrentByte() - c.getStartByte());
            }

            System.out.printf("Resuming from cumulative %.2f MB%n", totalDownloadedSoFar / (1024.0 * 1024.0));

            // STEP 7: Update DB to DOWNLOADING
            dao.updateStatus(downloadId, "DOWNLOADING");

            // STEP 8: Shared counter starts from bytes already downloaded
            // It is such that the threads will not overlap the bytes each is downloading and locking it in their places 
            AtomicLong totalBytesDownloaded = new AtomicLong(totalDownloadedSoFar);

            // STEP 9: Launch thread pool
            ExecutorService threadPool = Executors.newFixedThreadPool(chunks.size());

            for (int i = 0; i < chunks.size(); i++) {
                Chunk chunk = chunks.get(i);
                
                // Only start thread if chunk isn't finished
                // This check is crucial for resuming downloads, so we don't waste threads on already completed chunks
                if (chunk.getCurrentByte() <= chunk.getEndByte()) {
                    threadPool.submit(new Downloader(
                            url, chunk, i + 1,
                            outputFile, dao, downloadId,
                            totalBytesDownloaded, fileSize, this
                    ));
                }
            }

            // STEP 10: Wait for all threads to finish
            threadPool.shutdown();
            boolean allDone = threadPool.awaitTermination(
                    Long.MAX_VALUE, TimeUnit.MILLISECONDS);

            // STEP 11: Update final status
            if (allDone && !paused) {
                dao.markCompleted(downloadId);
                dao.deleteChunks(downloadId); // Clean up chunks
                System.out.println("✓ COMPLETED: " + fileName);
            } else if (paused) {
                dao.updateStatus(downloadId, "PAUSED");
                dao.updateBytesDownloaded(downloadId, totalBytesDownloaded.get());
                // Individual chunk progress is already saved by Downloader threads
                System.out.println("⏸ PAUSED: " + fileName);
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