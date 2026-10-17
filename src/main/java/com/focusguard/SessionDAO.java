package com.focusguard;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SessionDAO {

    /** The v2 schema has no modes table; the app only offers Study mode. */
    private static final String DEFAULT_MODE = "Study";

    public static class FocusSession {
        public int id;
        public String mode;
        public String technique;
        public Timestamp startTime;
        public Timestamp endTime;
        public int durationMinutes;   // planned minutes
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

    private int currentUserId() throws SQLException {
        int userId = UserSession.getUserId();
        if (userId <= 0) {
            throw new SQLException("No user is logged in.");
        }
        return userId;
    }

    // Calls the stored procedure sp_start_session (runs inside a transaction in MySQL).
    // The mode argument is kept so existing callers still compile.
    public int startSession(String mode, String technique,
                            int durationMinutes) throws SQLException {

        String call = "{CALL sp_start_session(?, ?, ?, ?, ?)}";

        try (Connection con = DBConnection.getConnection();
             CallableStatement cs = con.prepareCall(call)) {

            cs.setInt(1, currentUserId());
            cs.setString(2, technique);
            cs.setInt(3, durationMinutes);
            cs.setNull(4, Types.INTEGER);            // no linked task yet
            cs.registerOutParameter(5, Types.INTEGER);
            cs.execute();

            return cs.getInt(5);
        }
    }

    // Save elapsed time and change status without ending the record.
    public void updateSessionProgress(int sessionId, String status,
                                      int elapsedSeconds)
            throws SQLException {

        String sql = """
                UPDATE focus_sessions
                SET status = ?, elapsed_seconds = ?
                WHERE session_id = ? AND user_id = ?
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, elapsedSeconds);
            ps.setInt(3, sessionId);
            ps.setInt(4, currentUserId());
            ps.executeUpdate();
        }
    }

    // Calls sp_finish_session; the BEFORE UPDATE trigger fills in end_time.
    public void finishSession(int sessionId, String status,
                              int elapsedSeconds) throws SQLException {

        String call = "{CALL sp_finish_session(?, ?, ?)}";

        try (Connection con = DBConnection.getConnection();
             CallableStatement cs = con.prepareCall(call)) {

            cs.setInt(1, sessionId);
            cs.setString(2, status);
            cs.setInt(3, elapsedSeconds);
            cs.execute();
        }
    }

    // Reads from the v_session_history view, only for the logged-in user.
    public List<FocusSession> getAllSessions() throws SQLException {
        String sql = """
                SELECT session_id, technique_name, start_time, end_time,
                       planned_minutes, elapsed_seconds, status
                FROM v_session_history
                WHERE user_id = ?
                ORDER BY session_id DESC
                """;
        return query(sql);
    }

    // Retrieve only sessions started today.
    public List<FocusSession> getTodaySessions() throws SQLException {
        String sql = """
                SELECT session_id, technique_name, start_time, end_time,
                       planned_minutes, elapsed_seconds, status
                FROM v_session_history
                WHERE user_id = ?
                  AND start_time >= CURDATE()
                  AND start_time < CURDATE() + INTERVAL 1 DAY
                ORDER BY start_time DESC
                """;
        return query(sql);
    }

    private List<FocusSession> query(String sql) throws SQLException {
        List<FocusSession> sessions = new ArrayList<>();

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, currentUserId());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessions.add(new FocusSession(
                            rs.getInt("session_id"),
                            DEFAULT_MODE,
                            rs.getString("technique_name"),
                            rs.getTimestamp("start_time"),
                            rs.getTimestamp("end_time"),
                            rs.getInt("planned_minutes"),
                            rs.getInt("elapsed_seconds"),
                            rs.getString("status")
                    ));
                }
            }
        }

        return sessions;
    }
}