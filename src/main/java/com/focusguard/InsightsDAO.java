package com.focusguard;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Data access for the Database Insights screen: functions, procedures and the audit log. */
public class InsightsDAO {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss");

    private int currentUserId() throws SQLException {
        int userId = UserSession.getUserId();
        if (userId <= 0) {
            throw new SQLException("No user is logged in.");
        }
        return userId;
    }

    /** Uses the stored function fn_task_completion_rate. */
    public double getTaskCompletionRate() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT fn_task_completion_rate(?)")) {
            ps.setInt(1, currentUserId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    /** Uses the stored function fn_focus_minutes (both dates included). */
    public double getFocusMinutes(LocalDate from, LocalDate to) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT fn_focus_minutes(?, ?, ?)")) {
            ps.setInt(1, currentUserId());
            ps.setDate(2, Date.valueOf(from));
            ps.setDate(3, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    /** Calls the stored procedure sp_weekly_summary. Rows: day, sessions, focus minutes, completed. */
    public List<Object[]> getWeeklySummary(LocalDate weekStart) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             CallableStatement cs = con.prepareCall("{CALL sp_weekly_summary(?, ?)}")) {
            cs.setInt(1, currentUserId());
            cs.setDate(2, Date.valueOf(weekStart));
            try (ResultSet rs = cs.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[]{
                            rs.getDate("day").toString(),
                            rs.getInt("sessions"),
                            rs.getDouble("focus_minutes"),
                            rs.getInt("completed_sessions")
                    });
                }
            }
        }
        return rows;
    }

    /** Reads task_audit_log, which is filled automatically by the triggers on tasks. */
    public List<Object[]> getAuditLog(int limit) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        String sql = """
                SELECT changed_at, task_id, action,
                       old_status, new_status, old_priority, new_priority
                FROM task_audit_log
                WHERE user_id = ?
                ORDER BY log_id DESC
                LIMIT ?
                """;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, currentUserId());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[]{
                            rs.getTimestamp("changed_at").toLocalDateTime().format(WHEN),
                            "#" + rs.getInt("task_id"),
                            rs.getString("action"),
                            change(rs.getString("old_status"), rs.getString("new_status")),
                            change(rs.getString("old_priority"), rs.getString("new_priority"))
                    });
                }
            }
        }
        return rows;
    }

    /** Calls the stored procedure sp_close_stale_sessions and returns how many sessions it closed. */
    public int closeStaleSessions(int minutesOld) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             CallableStatement cs = con.prepareCall("{CALL sp_close_stale_sessions(?, ?, ?)}")) {
            cs.setInt(1, currentUserId());
            cs.setInt(2, minutesOld);
            cs.registerOutParameter(3, Types.INTEGER);
            cs.execute();
            return cs.getInt(3);
        }
    }

    private static String change(String oldValue, String newValue) {
        if (oldValue == null && newValue == null) return "—";
        if (oldValue == null) return newValue;
        if (newValue == null) return oldValue + " (removed)";
        return oldValue.equals(newValue) ? newValue : oldValue + " → " + newValue;
    }
}
