package download.manager.storage;

import download.manager.config.DB;
import download.manager.model.Download;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// STORAGE: All database operations for downloads table
// DAO = Data Access Object — one place for all SQL queries
public class DAO {

    private Connection connection;

    public DAO() {
        this.connection = DB.getConnection();
    }

    // INSERT a new download, returns the generated ID
    public int addDownload(Download download) {
        String sql = "INSERT INTO downloads (url, file_name, file_size, status, progress, save_path) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, download.getUrl());
            stmt.setString(2, download.getFileName());
            stmt.setLong(3, download.getFileSize());
            stmt.setString(4, download.getStatus());
            stmt.setDouble(5, download.getProgress());
            stmt.setString(6, download.getSavePath());
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

    // UPDATE status (PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED)
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

    // UPDATE progress (0.0 to 100.0)
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
                list.add(d);
            }
        } catch (SQLException e) {
            System.out.println("✗ getAllDownloads failed: " + e.getMessage());
        }
        return list;
    }
}