
package com.focusguard;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TaskDAO {

    public static class Task {
        public int id;
        public String title;
        public String status;
        public String priority;
        public Date dueDate;

        public Task(int id, String title, String status,
                    String priority, Date dueDate) {
            this.id = id;
            this.title = title;
            this.status = status;
            this.priority = priority;
            this.dueDate = dueDate;
        }

        @Override
        public String toString() {
            return (status.equals("Completed")
                    ? "[Completed] " : "[ ] ")
                    + title;
        }
    }

    public List<Task> getAllTasks() throws SQLException {
        List<Task> tasks = new ArrayList<>();

        String sql = """
                SELECT task_id, title, status, priority, due_date
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
                        rs.getString("status"),
                        rs.getString("priority"),
                        rs.getDate("due_date")
                ));
            }
        }

        return tasks;
    }

    public void addTask(String title) throws SQLException {
        addTask(title, "Medium", null);
    }

    public void addTask(String title, String priority,
                        Date dueDate) throws SQLException {

        String sql = """
                INSERT INTO tasks (title, priority, due_date)
                VALUES (?, ?, ?)
                """;

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, title);
            ps.setString(2, priority);
            ps.setDate(3, dueDate);

            ps.executeUpdate();
        }
    }

    public void updateTaskStatus(int id, String status)
            throws SQLException {

        String sql =
                "UPDATE tasks SET status = ? WHERE task_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, id);

            ps.executeUpdate();
        }
    }

    public void updateTaskPriority(int id, String priority)
            throws SQLException {

        String sql =
                "UPDATE tasks SET priority = ? WHERE task_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, priority);
            ps.setInt(2, id);

            ps.executeUpdate();
        }
    }

    public void updateTaskDeadline(int id, Date dueDate)
            throws SQLException {

        String sql =
                "UPDATE tasks SET due_date = ? WHERE task_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setDate(1, dueDate);
            ps.setInt(2, id);

            ps.executeUpdate();
        }
    }
    public void updateTaskTitle(int id, String title) throws SQLException {
        String sql = "UPDATE tasks SET title = ? WHERE task_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, title);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }
    public void deleteTask(int id) throws SQLException {
        String sql =
                "DELETE FROM tasks WHERE task_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
