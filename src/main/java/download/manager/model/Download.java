package download.manager.model;

import javafx.beans.property.*;
import java.sql.Timestamp;

public class Download {

    private final IntegerProperty id = new SimpleIntegerProperty();
    private final StringProperty url = new SimpleStringProperty();
    private final StringProperty fileName = new SimpleStringProperty();
    private final LongProperty fileSize = new SimpleLongProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final DoubleProperty progress = new SimpleDoubleProperty();
    private final StringProperty savePath = new SimpleStringProperty();
    private Timestamp createdAt;
    private final LongProperty bytesDownloaded = new SimpleLongProperty();
    private final BooleanProperty resumable = new SimpleBooleanProperty();

    public Download(String url, String fileName, long fileSize, String savePath) {
        setUrl(url);
        setFileName(fileName);
        setFileSize(fileSize);
        setSavePath(savePath);
        setStatus("PENDING");
        setProgress(0.0);
        setBytesDownloaded(0);
        setResumable(false);
    }

    // Property getters
    public IntegerProperty idProperty() { return id; }
    public StringProperty urlProperty() { return url; }
    public StringProperty fileNameProperty() { return fileName; }
    public LongProperty fileSizeProperty() { return fileSize; }
    public StringProperty statusProperty() { return status; }
    public DoubleProperty progressProperty() { return progress; }
    public StringProperty savePathProperty() { return savePath; }
    public LongProperty bytesDownloadedProperty() { return bytesDownloaded; }
    public BooleanProperty resumableProperty() { return resumable; }

    // Getters
    public int getId()                  { return id.get(); }
    public String getUrl()              { return url.get(); }
    public String getFileName()         { return fileName.get(); }
    public long getFileSize()           { return fileSize.get(); }
    public String getStatus()           { return status.get(); }
    public double getProgress()         { return progress.get(); }
    public String getSavePath()         { return savePath.get(); }
    public Timestamp getCreatedAt()     { return createdAt; }
    public long getBytesDownloaded()    { return bytesDownloaded.get(); }
    public boolean isResumable()        { return resumable.get(); }

    // Setters
    public void setId(int id)                       { this.id.set(id); }
    public void setUrl(String url)                  { this.url.set(url); }
    public void setFileName(String n)               { this.fileName.set(n); }
    public void setFileSize(long s)                 { this.fileSize.set(s); }
    public void setStatus(String status)            { this.status.set(status); }
    public void setProgress(double p)               { this.progress.set(p); }
    public void setSavePath(String p)               { this.savePath.set(p); }
    public void setCreatedAt(Timestamp t)           { this.createdAt = t; }
    public void setBytesDownloaded(long b)          { this.bytesDownloaded.set(b); }
    public void setResumable(boolean r)             { this.resumable.set(r); }

    @Override
    public String toString() {
        return String.format("[%d] %s | %s | %.1f%%", getId(), getFileName(), getStatus(), getProgress());
    }
}