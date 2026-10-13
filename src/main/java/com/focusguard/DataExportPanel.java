package com.focusguard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;

/** Displays real FocusGuard session history and exports it as CSV. */
public class DataExportPanel extends JPanel {
    private static final Color BG = new Color(247, 244, 255);
    private static final Color CARD = Color.WHITE;
    private static final Color PURPLE = new Color(118, 85, 201);
    private static final Color TEXT = new Color(48, 39, 71);
    private static final Color MUTED = new Color(130, 122, 150);
    private static final Color BORDER = new Color(232, 225, 245);

    private final SessionDAO sessionDAO = new SessionDAO();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Session ID", "Mode", "Technique", "Started", "Ended",
                    "Planned Minutes", "Elapsed", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel("Load session history to prepare a CSV export.");

    public DataExportPanel() {
        setLayout(new BorderLayout(0, 18));
        setBackground(BG);
        setBorder(new EmptyBorder(28, 32, 28, 32));

        JLabel heading = new JLabel("Data Export");
        heading.setFont(new Font("SansSerif", Font.BOLD, 28));
        heading.setForeground(TEXT);
        JLabel subtitle = new JLabel("Review your saved focus sessions and export them to a CSV file.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(heading);
        header.add(Box.createVerticalStrut(6));
        header.add(subtitle);

        JButton refreshButton = secondaryButton("Refresh Sessions");
        JButton exportButton = primaryButton("Export CSV");
        refreshButton.addActionListener(e -> refreshSessions());
        exportButton.addActionListener(e -> exportCsv());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(refreshButton);
        actions.add(exportButton);

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        top.add(header, BorderLayout.CENTER);
        top.add(actions, BorderLayout.SOUTH);

        table.setRowHeight(28);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(233, 223, 255));
        table.setSelectionBackground(new Color(233, 223, 255));
        table.setSelectionForeground(TEXT);
        table.setGridColor(BORDER);
        table.setFillsViewportHeight(true);

        JPanel tableCard = new JPanel(new BorderLayout());
        tableCard.setBackground(CARD);
        tableCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(10, 10, 10, 10)));
        tableCard.add(new JScrollPane(table), BorderLayout.CENTER);

        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(MUTED);

        add(top, BorderLayout.NORTH);
        add(tableCard, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        refreshSessions();
    }

    public void refreshSessions() {
        tableModel.setRowCount(0);
        try {
            List<SessionDAO.FocusSession> sessions = sessionDAO.getAllSessions();
            for (SessionDAO.FocusSession session : sessions) {
                tableModel.addRow(new Object[]{
                        session.id,
                        safe(session.mode),
                        safe(session.technique),
                        formatTimestamp(session.startTime),
                        formatTimestamp(session.endTime),
                        session.durationMinutes,
                        formatElapsed(session.elapsedSeconds),
                        safe(session.status)
                });
            }
            statusLabel.setText("Loaded " + sessions.size() + " session record(s).");
        } catch (Exception ex) {
            statusLabel.setText("Could not load session records.");
            JOptionPane.showMessageDialog(this,
                    "Could not load focus sessions from the database.\n" + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportCsv() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "There are no session records to export. Refresh the list first.",
                    "No Data", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save FocusGuard Session Report");
        chooser.setSelectedFile(new java.io.File("focusguard_sessions.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path destination = chooser.getSelectedFile().toPath();
        if (!destination.getFileName().toString().toLowerCase().endsWith(".csv")) {
            destination = destination.resolveSibling(destination.getFileName() + ".csv");
        }

        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            for (int column = 0; column < tableModel.getColumnCount(); column++) {
                if (column > 0) writer.write(",");
                writer.write(csv(tableModel.getColumnName(column)));
            }
            writer.newLine();

            for (int row = 0; row < tableModel.getRowCount(); row++) {
                for (int column = 0; column < tableModel.getColumnCount(); column++) {
                    if (column > 0) writer.write(",");
                    Object value = tableModel.getValueAt(row, column);
                    writer.write(csv(value == null ? "" : value.toString()));
                }
                writer.newLine();
            }

            statusLabel.setText("Exported " + tableModel.getRowCount() + " session record(s).");
            JOptionPane.showMessageDialog(this,
                    "CSV export completed:\n" + destination.toAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            statusLabel.setText("CSV export failed.");
            JOptionPane.showMessageDialog(this,
                    "Could not export CSV:\n" + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String csv(String value) {
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"")
                || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toString();
    }

    private String formatElapsed(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        return String.format("%02d:%02d:%02d",
                safeSeconds / 3600, (safeSeconds % 3600) / 60, safeSeconds % 60);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setBackground(PURPLE);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(10, 16, 10, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setForeground(PURPLE);
        button.setBackground(new Color(233, 223, 255));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(10, 16, 10, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
}
