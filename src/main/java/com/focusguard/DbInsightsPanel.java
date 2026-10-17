package com.focusguard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Shows what the database computes: functions, a stored procedure, the trigger-filled audit log. */
public class DbInsightsPanel extends JPanel {
    private static final Color BG = new Color(247, 244, 255);
    private static final Color CARD = Color.WHITE;
    private static final Color PURPLE = new Color(118, 85, 201);
    private static final Color PALE = new Color(233, 223, 255);
    private static final Color TEXT = new Color(48, 39, 71);
    private static final Color MUTED = new Color(130, 122, 150);
    private static final Color BORDER = new Color(232, 225, 245);

    private final InsightsDAO dao = new InsightsDAO();
    private LocalDate weekStart = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    private final JLabel completionValue = statValue();
    private final JLabel focusValue = statValue();
    private final JLabel weekLabel = new JLabel();
    private final JLabel statusLabel = new JLabel(" ");

    private final DefaultTableModel weeklyModel = readOnlyModel(
            "Day", "Sessions", "Focus (min)", "Completed");
    private final DefaultTableModel auditModel = readOnlyModel(
            "When", "Task", "Action", "Status", "Priority");

    public DbInsightsPanel() {
        setLayout(new BorderLayout(0, 16));
        setBackground(BG);
        setBorder(new EmptyBorder(28, 32, 28, 32));

        JLabel heading = new JLabel("Database Insights");
        heading.setFont(new Font("SansSerif", Font.BOLD, 28));
        heading.setForeground(TEXT);
        JLabel subtitle = new JLabel("Live results from MySQL functions, a stored procedure and trigger-written audit history.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(heading);
        header.add(Box.createVerticalStrut(6));
        header.add(subtitle);

        JButton refreshButton = button("Refresh", false);
        JButton cleanupButton = button("Close stale sessions", true);
        refreshButton.addActionListener(e -> refreshAll());
        cleanupButton.addActionListener(e -> closeStale());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(refreshButton);
        actions.add(cleanupButton);

        JPanel stats = new JPanel(new GridLayout(1, 2, 14, 0));
        stats.setOpaque(false);
        stats.add(statCard("TASK COMPLETION RATE  (fn_task_completion_rate)", completionValue));
        stats.add(statCard("FOCUS TIME, LAST 7 DAYS  (fn_focus_minutes)", focusValue));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(header);
        top.add(Box.createVerticalStrut(10));
        top.add(actions);
        top.add(Box.createVerticalStrut(14));
        top.add(stats);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(2, 1, 0, 16));
        center.setOpaque(false);
        center.add(buildWeeklyCard());
        center.add(tableCard("Task activity log  (filled automatically by triggers)", auditModel, null));
        add(center, BorderLayout.CENTER);

        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(MUTED);
        add(statusLabel, BorderLayout.SOUTH);

        refreshAll();
    }

    private JPanel buildWeeklyCard() {
        JButton prev = button("◀", false);
        JButton next = button("▶", false);
        prev.addActionListener(e -> { weekStart = weekStart.minusWeeks(1); refreshAll(); });
        next.addActionListener(e -> { weekStart = weekStart.plusWeeks(1); refreshAll(); });

        weekLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        weekLabel.setForeground(MUTED);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        controls.add(weekLabel);
        controls.add(prev);
        controls.add(next);
        return tableCard("Weekly summary  (sp_weekly_summary)", weeklyModel, controls);
    }

    private JPanel tableCard(String title, DefaultTableModel model, JComponent rightSide) {
        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.BOLD, 15));
        label.setForeground(TEXT);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(label, BorderLayout.WEST);
        if (rightSide != null) head.add(rightSide, BorderLayout.EAST);

        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.getTableHeader().setBackground(PALE);
        table.setSelectionBackground(PALE);
        table.setSelectionForeground(TEXT);
        table.setGridColor(BORDER);
        table.setFillsViewportHeight(true);

        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(14, 14, 14, 14)));
        card.add(head, BorderLayout.NORTH);
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        return card;
    }

    public void refreshAll() {
        try {
            completionValue.setText(String.format("%.1f%%", dao.getTaskCompletionRate()));

            LocalDate today = LocalDate.now();
            focusValue.setText(String.format("%.1f min", dao.getFocusMinutes(today.minusDays(6), today)));

            weekLabel.setText(weekStart + "  to  " + weekStart.plusDays(6));
            weeklyModel.setRowCount(0);
            for (Object[] row : dao.getWeeklySummary(weekStart)) weeklyModel.addRow(row);

            auditModel.setRowCount(0);
            List<Object[]> log = dao.getAuditLog(50);
            for (Object[] row : log) auditModel.addRow(row);

            statusLabel.setText("Updated just now.  Audit entries shown: " + log.size());
        } catch (SQLException ex) {
            statusLabel.setText("Could not load database insights.");
            JOptionPane.showMessageDialog(this,
                    "Could not load data from the database.\n" + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void closeStale() {
        String input = JOptionPane.showInputDialog(this,
                "Mark Active/Paused sessions older than how many minutes as Stopped?", "720");
        if (input == null) return;
        int minutes;
        try {
            minutes = Integer.parseInt(input.trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a whole number of minutes.",
                    "Invalid number", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            int closed = dao.closeStaleSessions(minutes);
            JOptionPane.showMessageDialog(this, closed + " stale session(s) were closed.",
                    "Cleanup complete", JOptionPane.INFORMATION_MESSAGE);
            refreshAll();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Cleanup failed.\n" + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private static JLabel statValue() {
        JLabel l = new JLabel("-");
        l.setFont(new Font("SansSerif", Font.BOLD, 30));
        l.setForeground(PURPLE);
        return l;
    }

    private JPanel statCard(String caption, JLabel value) {
        JLabel c = new JLabel(caption);
        c.setFont(new Font("SansSerif", Font.BOLD, 10));
        c.setForeground(MUTED);
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(14, 16, 14, 16)));
        card.add(c, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private JButton button(String text, boolean primary) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setForeground(primary ? Color.WHITE : PURPLE);
        b.setBackground(primary ? PURPLE : PALE);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(9, 16, 9, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Standalone preview: run this class directly to see the screen without wiring it into Main. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UserSession.setUser(1, "Preview User", "preview@local");
            JFrame frame = new JFrame("Database Insights preview");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new DbInsightsPanel());
            frame.setSize(1000, 740);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}