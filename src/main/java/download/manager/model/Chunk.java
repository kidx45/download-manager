package download.manager.model;

public class Chunk {
    private int id;
    private int downloadId;
    private long startByte;
    private long endByte;
    private long currentByte;

    public Chunk(int downloadId, long startByte, long endByte, long currentByte) {
        this.downloadId = downloadId;
        this.startByte = startByte;
        this.endByte = endByte;
        this.currentByte = currentByte;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getDownloadId() { return downloadId; }
    public void setDownloadId(int downloadId) { this.downloadId = downloadId; }

    public long getStartByte() { return startByte; }
    public void setStartByte(long startByte) { this.startByte = startByte; }

    public long getEndByte() { return endByte; }
    public void setEndByte(long endByte) { this.endByte = endByte; }

    public long getCurrentByte() { return currentByte; }
    public void setCurrentByte(long currentByte) { this.currentByte = currentByte; }
}
