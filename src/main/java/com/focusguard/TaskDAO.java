
package com.focusguard;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TaskDAO {

    public static class Task {
        public int id;
        public String title;
        public String status;

        public Task(int id, String title, String status) {
            this.id = id;
            this.title = title;
            this.status = status;
        }

        @Override
        public String toString() {
            return (status.equals("Completed") ? "[Completed] " : "[ ] ")
                    + title;
        }
    }

    public List<Task> getAllTasks() throws SQLException {
        List<Task> tasks = new ArrayList<>();

        String sql = """
                SELECT task_id, title, status
                FROM tasks
                ORDER BY task_id DESC
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                tasks.add(new Task(
                        rs.getInt("task_id"),
                        rs.getString("title"),
                        rs.getString("status")
                ));
            }
        }

        return tasks;
    }

    public void addTask(String title) throws SQLException {
        String sql = "INSERT INTO tasks (title) VALUES (?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, title);
            ps.executeUpdate();
        }
    }

    public void updateTaskStatus(int id, String status)
            throws SQLException {

        String sql = "UPDATE tasks SET status = ? WHERE task_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void deleteTask(int id) throws SQLException {
        String sql = "DELETE FROM tasks WHERE task_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
