
package com.focusguard;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DistractionDAO {

    public static class DistractionRecord {
        public int id;
        public int sessionId;
        public String appName;
        public String websiteName;
        public Timestamp startTime;
        public Timestamp endTime;
        public int durationSeconds;

        public DistractionRecord(
                int id, int sessionId, String appName,
                String websiteName, Timestamp startTime,
                Timestamp endTime, int durationSeconds) {
            this.id = id;
            this.sessionId = sessionId;
            this.appName = appName;
            this.websiteName = websiteName;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationSeconds = durationSeconds;
        }
    }

    public int startDistraction(
            int sessionId, String appName, String websiteName)
            throws SQLException {

        String sql = """
                INSERT INTO distraction_logs
                (session_id, app_name, website_name, start_time,
                 duration_seconds, distraction_date)
                VALUES (?, ?, ?, NOW(), 0, CURDATE())
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            if (sessionId > 0) {
                ps.setInt(1, sessionId);
            } else {
                ps.setNull(1, Types.INTEGER);
            }

            ps.setString(2, appName);
            ps.setString(3, websiteName);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException("Could not save distraction.");
    }

    public void finishDistraction(
            int distractionId, int durationSeconds)
            throws SQLException {

        String sql = """
                UPDATE distraction_logs
                SET end_time = NOW(), duration_seconds = ?
                WHERE distraction_id = ?
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, Math.max(0, durationSeconds));
            ps.setInt(2, distractionId);
            ps.executeUpdate();
        }
    }

    public List<DistractionRecord> getTodayDistractions()
            throws SQLException {

        List<DistractionRecord> records = new ArrayList<>();

        String sql = """
                SELECT distraction_id, session_id, app_name,
                       website_name, start_time, end_time,
                       duration_seconds
                FROM distraction_logs
                WHERE distraction_date = CURDATE()
                ORDER BY start_time DESC
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                records.add(new DistractionRecord(
                        rs.getInt("distraction_id"),
                        rs.getInt("session_id"),
                        rs.getString("app_name"),
                        rs.getString("website_name"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"),
                        rs.getInt("duration_seconds")
                ));
            }
        }

        return records;
    }

    public int getTodayDistractionCount() throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM distraction_logs
                WHERE distraction_date = CURDATE()
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        }

        return 0;
    }

    public int getTodayDistractionSeconds() throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(
                    CASE
                        WHEN end_time IS NULL
                        THEN TIMESTAMPDIFF(SECOND, start_time, NOW())
                        ELSE duration_seconds
                    END
                ), 0)
                FROM distraction_logs
                WHERE distraction_date = CURDATE()
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        }

        return 0;
    }
}
