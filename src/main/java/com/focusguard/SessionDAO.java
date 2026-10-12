
package com.focusguard;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SessionDAO {

    public static class FocusSession {
        public int id;
        public String mode;
        public String technique;
        public Timestamp startTime;
        public Timestamp endTime;
        public int durationMinutes;
        public int elapsedSeconds;
        public String status;

        public FocusSession(int id, String mode, String technique,
                            Timestamp startTime, Timestamp endTime,
                            int durationMinutes, int elapsedSeconds,
                            String status) {
            this.id = id;
            this.mode = mode;
            this.technique = technique;
            this.startTime = startTime;
            this.endTime = endTime;
            this.durationMinutes = durationMinutes;
            this.elapsedSeconds = elapsedSeconds;
            this.status = status;
        }

        @Override
        public String toString() {
            int minutes = elapsedSeconds / 60;
            int seconds = elapsedSeconds % 60;

            return "#" + id + " | " + mode + " | " + technique
                    + " | " + String.format("%02d:%02d", minutes, seconds)
                    + " elapsed | " + status + " | Started: " + startTime;
        }
    }

    // Create one database record when a new session starts.
    public int startSession(String mode, String technique,
                            int durationMinutes) throws SQLException {

        String sql = """
                INSERT INTO focus_sessions
                (mode, technique, start_time, duration_minutes,
                 elapsed_seconds, status)
                VALUES (?, ?, NOW(), ?, 0, 'Active')
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, mode);
            ps.setString(2, technique);
            ps.setInt(3, durationMinutes);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException("Could not create focus session.");
    }

    // Save elapsed time and change status without ending the record.
    public void updateSessionProgress(int sessionId, String status,
                                      int elapsedSeconds)
            throws SQLException {

        String sql = """
                UPDATE focus_sessions
                SET status = ?, elapsed_seconds = ?,
                    duration_minutes = ?
                WHERE session_id = ?
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, elapsedSeconds);
            ps.setInt(3, (elapsedSeconds + 59) / 60);
            ps.setInt(4, sessionId);
            ps.executeUpdate();
        }
    }

    // Finalise the same record only when completed or stopped.
    public void finishSession(int sessionId, String status,
                              int elapsedSeconds) throws SQLException {

        String sql = """
                UPDATE focus_sessions
                SET end_time = NOW(), status = ?,
                    elapsed_seconds = ?, duration_minutes = ?
                WHERE session_id = ?
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, elapsedSeconds);
            ps.setInt(3, (elapsedSeconds + 59) / 60);
            ps.setInt(4, sessionId);
            ps.executeUpdate();
        }
    }

    public List<FocusSession> getAllSessions() throws SQLException {
        List<FocusSession> sessions = new ArrayList<>();

        String sql = """
                SELECT session_id, mode, technique, start_time,
                       end_time, duration_minutes, elapsed_seconds, status
                FROM focus_sessions
                ORDER BY session_id DESC
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                sessions.add(new FocusSession(
                        rs.getInt("session_id"),
                        rs.getString("mode"),
                        rs.getString("technique"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"),
                        rs.getInt("duration_minutes"),
                        rs.getInt("elapsed_seconds"),
                        rs.getString("status")
                ));
            }
        }

        return sessions;
    }


    // Retrieve only sessions started today.
    public List<FocusSession> getTodaySessions() throws SQLException {
        List<FocusSession> sessions = new ArrayList<>();

        String sql = """
                SELECT session_id, mode, technique, start_time,
                       end_time, duration_minutes, elapsed_seconds, status
                FROM focus_sessions
                WHERE start_time >= CURDATE()
                  AND start_time < CURDATE() + INTERVAL 1 DAY
                ORDER BY start_time DESC
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                sessions.add(new FocusSession(
                        rs.getInt("session_id"),
                        rs.getString("mode"),
                        rs.getString("technique"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"),
                        rs.getInt("duration_minutes"),
                        rs.getInt("elapsed_seconds"),
                        rs.getString("status")
                ));
            }
        }

        return sessions;
    }

}
