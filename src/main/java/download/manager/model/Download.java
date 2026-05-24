package download.manager.model;

import java.sql.Timestamp;

// MODEL: Represents one download record
// This is a plain Java object (POJO) — just data, no logic
public class Download {

    // Fields — private because of encapsulation
    private int id;
    private String url;
    private String fileName;
    private long fileSize;
    private String status;   // PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED
    private double progress; // 0.0 to 100.0
    private String savePath;
    private Timestamp createdAt;

    // Constructor — called when creating a new download
    public Download(String url, String fileName, long fileSize, String savePath) {
        this.url = url;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.savePath = savePath;
        this.status = "PENDING";
        this.progress = 0.0;
    }

    // Getters
    public int getId()           { return id; }
    public String getUrl()       { return url; }
    public String getFileName()  { return fileName; }
    public long getFileSize()    { return fileSize; }
    public String getStatus()    { return status; }
    public double getProgress()  { return progress; }
    public String getSavePath()  { return savePath; }
    public Timestamp getCreatedAt() { return createdAt; }

    // Setters
    public void setId(int id)              { this.id = id; }
    public void setFileName(String n)      { this.fileName = n; }
    public void setStatus(String status)   { this.status = status; }
    public void setProgress(double p)      { this.progress = p; }
    public void setCreatedAt(Timestamp t)  { this.createdAt = t; }

    // toString — used when printing a Download object
    @Override
    public String toString() {
        return String.format("[%d] %s | %s | %.1f%%", id, fileName, status, progress);
    }
}