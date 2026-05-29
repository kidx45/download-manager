package download.manager.storage;

import download.manager.config.DB;
import download.manager.model.Download;
import download.manager.model.Chunk;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// STORAGE: All database operations for downloads table
public class DAO {

    private Connection connection;

    public DAO() {
        this.connection = DB.getConnection();
    }

    // INSERT a new download, returns the generated ID
    public int addDownload(Download download) {
        String sql = "INSERT INTO downloads (url, file_name, file_size, status, progress, save_path, bytes_downloaded, is_resumable) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, download.getUrl());
            stmt.setString(2, download.getFileName());
            stmt.setLong(3, download.getFileSize());
            stmt.setString(4, download.getStatus());
            stmt.setDouble(5, download.getProgress());
            stmt.setString(6, download.getSavePath());
            stmt.setLong(7, 0);
            stmt.setBoolean(8, false);
            stmt.executeUpdate();

            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) {
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println("✗ addDownload failed: " + e.getMessage());
        }
        return -1;
    }

    // UPDATE status
    public void updateStatus(int id, String status) {
        String sql = "UPDATE downloads SET status = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("✗ updateStatus failed: " + e.getMessage());
        }
    }

    // UPDATE progress
    public void updateProgress(int id, double progress) {
        String sql = "UPDATE downloads SET progress = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setDouble(1, progress);
            stmt.setInt(2, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("✗ updateProgress failed: " + e.getMessage());
        }
    }

    // SAVE bytes downloaded when paused so we can resume later
    public void updateBytesDownloaded(int id, long bytes) {
        String sql = "UPDATE downloads SET bytes_downloaded = ?, is_resumable = TRUE WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, bytes);
            stmt.setInt(2, id);
            stmt.executeUpdate();
            System.out.println("✓ Saved pause state at " + bytes + " bytes");
        } catch (SQLException e) {
            System.out.println("✗ updateBytesDownloaded failed: " + e.getMessage());
        }
    }

    // MARK as completed — disables resume
    public void markCompleted(int id) {
        String sql = "UPDATE downloads SET status = 'COMPLETED', progress = 100.0, " +
                     "is_resumable = FALSE WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
            System.out.println("✓ Download " + id + " marked as COMPLETED");
        } catch (SQLException e) {
            System.out.println("✗ markCompleted failed: " + e.getMessage());
        }
    }

    // SELECT all downloads ordered by newest first
    public List<Download> getAllDownloads() {
        List<Download> list = new ArrayList<>();
        String sql = "SELECT * FROM downloads ORDER BY created_at DESC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Download d = new Download(
                    rs.getString("url"),
                    rs.getString("file_name"),
                    rs.getLong("file_size"),
                    rs.getString("save_path")
                );
                d.setId(rs.getInt("id"));
                d.setStatus(rs.getString("status"));
                d.setProgress(rs.getDouble("progress"));
                d.setCreatedAt(rs.getTimestamp("created_at"));
                d.setBytesDownloaded(rs.getLong("bytes_downloaded")); // ← new
                d.setResumable(rs.getBoolean("is_resumable"));        // ← new
                list.add(d);
            }
        } catch (SQLException e) {
            System.out.println("✗ getAllDownloads failed: " + e.getMessage());
        }
        return list;
    }

    // DELETE a download
    public void deleteDownload(int id) {
        String sql = "DELETE FROM downloads WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
            System.out.println("✓ Download " + id + " deleted from DB");
        } catch (SQLException e) {
            System.out.println("✗ deleteDownload failed: " + e.getMessage());
        }
    }

    // --- CHUNK METHODS ---

    public void addChunk(Chunk chunk) {
        String sql = "INSERT INTO download_chunks (download_id, start_byte, end_byte, current_byte) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, chunk.getDownloadId());
            stmt.setLong(2, chunk.getStartByte());
            stmt.setLong(3, chunk.getEndByte());
            stmt.setLong(4, chunk.getCurrentByte());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("✗ addChunk failed: " + e.getMessage());
        }
    }

    public List<Chunk> getChunks(int downloadId) {
        List<Chunk> chunks = new ArrayList<>();
        String sql = "SELECT * FROM download_chunks WHERE download_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, downloadId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Chunk c = new Chunk(
                    rs.getInt("download_id"),
                    rs.getLong("start_byte"),
                    rs.getLong("end_byte"),
                    rs.getLong("current_byte")
                );
                c.setId(rs.getInt("id"));
                chunks.add(c);
            }
        } catch (SQLException e) {
            System.out.println("✗ getChunks failed: " + e.getMessage());
        }
        return chunks;
    }

    public void updateChunkProgress(int chunkId, long currentByte) {
        String sql = "UPDATE download_chunks SET current_byte = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, currentByte);
            stmt.setInt(2, chunkId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("✗ updateChunkProgress failed: " + e.getMessage());
        }
    }

    public void deleteChunks(int downloadId) {
        String sql = "DELETE FROM download_chunks WHERE download_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, downloadId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("✗ deleteChunks failed: " + e.getMessage());
        }
    }
}