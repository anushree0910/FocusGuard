package com.focusguard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Focus Journal with a searchable history list and readable entry details. */
public class FocusJournalPanel extends JPanel {
    private static final Color BG = new Color(247, 244, 255);
    private static final Color CARD = Color.WHITE;
    private static final Color PURPLE = new Color(118, 85, 201);
    private static final Color LIGHT_PURPLE = new Color(239, 233, 252);
    private static final Color TEXT = new Color(48, 39, 71);
    private static final Color MUTED = new Color(130, 122, 150);
    private static final Color BORDER = new Color(232, 225, 245);
    private static final String SEPARATOR = "========================================";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy  •  hh:mm a");

    private final JTextField titleField = new JTextField();
    private final JTextArea notesArea = new JTextArea(12, 35);
    private final JTextField searchField = new JTextField();
    private final JPanel historyCards = new JPanel();
    private final JLabel statusLabel = new JLabel("Your notes are saved locally on this device.");
    private List<JournalEntry> allEntries = new ArrayList<>();

    public FocusJournalPanel() {
        setLayout(new BorderLayout(0, 18));
        setBackground(BG);
        setBorder(new EmptyBorder(26, 28, 26, 28));

        JLabel heading = new JLabel("Focus Journal");
        heading.setFont(new Font("SansSerif", Font.BOLD, 28));
        heading.setForeground(TEXT);
        JLabel subtitle = new JLabel("Keep track of what you learned, how your session went, and what comes next.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(heading);
        header.add(Box.createVerticalStrut(6));
        header.add(subtitle);
        add(header, BorderLayout.NORTH);

        JPanel historyPanel = buildHistoryPanel();
        JPanel editorPanel = buildEditorPanel();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, historyPanel, editorPanel);
        splitPane.setResizeWeight(0.43);
        splitPane.setDividerLocation(360);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        splitPane.setContinuousLayout(true);
        add(splitPane, BorderLayout.CENTER);

        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(MUTED);
        add(statusLabel, BorderLayout.SOUTH);
        refreshHistory();
    }

    private JPanel buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(CARD);
        panel.setBorder(cardBorder(16));

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        JLabel title = new JLabel("Your entries");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(TEXT);
        JLabel hint = new JLabel("Select a note to read the full entry");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 12));
        hint.setForeground(MUTED);
        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        labels.add(title);
        labels.add(Box.createVerticalStrut(4));
        labels.add(hint);
        top.add(labels, BorderLayout.NORTH);

        styleField(searchField);
        searchField.setToolTipText("Search titles and note text");
        searchField.addActionListener(e -> renderHistory(searchField.getText()));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { renderHistory(searchField.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { renderHistory(searchField.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { renderHistory(searchField.getText()); }
        });
        top.add(searchField, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);

        historyCards.setLayout(new BoxLayout(historyCards, BoxLayout.Y_AXIS));
        historyCards.setBackground(CARD);
        JScrollPane scroll = new JScrollPane(historyCards);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildEditorPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(CARD);
        panel.setBorder(cardBorder(18));

        JPanel intro = new JPanel(new BorderLayout(0, 5));
        intro.setOpaque(false);
        JLabel title = new JLabel("Write a new note");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(TEXT);
        JLabel hint = new JLabel("A quick reflection helps you remember your progress.");
        hint.setFont(new Font("SansSerif", Font.PLAIN, 12));
        hint.setForeground(MUTED);
        intro.add(title, BorderLayout.NORTH);
        intro.add(hint, BorderLayout.SOUTH);
        panel.add(intro, BorderLayout.NORTH);

        JPanel fields = new JPanel(new BorderLayout(0, 14));
        fields.setOpaque(false);
        JPanel titlePanel = new JPanel(new BorderLayout(0, 7));
        titlePanel.setOpaque(false);
        titlePanel.add(fieldLabel("ENTRY TITLE"), BorderLayout.NORTH);
        styleField(titleField);
        titleField.setToolTipText("Example: Java constructors revision");
        titlePanel.add(titleField, BorderLayout.CENTER);
        fields.add(titlePanel, BorderLayout.NORTH);

        JPanel notePanel = new JPanel(new BorderLayout(0, 7));
        notePanel.setOpaque(false);
        notePanel.add(fieldLabel("YOUR NOTES"), BorderLayout.NORTH);
        notesArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        notesArea.setForeground(TEXT);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setMargin(new Insets(12, 12, 12, 12));
        notesArea.setBackground(new Color(250, 248, 255));
        notesArea.setText("What did I work on?\n\nWhat went well?\n\nWhat should I focus on next?");
        JScrollPane noteScroll = new JScrollPane(notesArea);
        noteScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        notePanel.add(noteScroll, BorderLayout.CENTER);
        fields.add(notePanel, BorderLayout.CENTER);
        panel.add(fields, BorderLayout.CENTER);

        JButton save = primaryButton("Save note");
        JButton clear = secondaryButton("Clear");
        save.addActionListener(e -> saveEntry());
        clear.addActionListener(e -> clearEditor());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(save);
        buttons.add(clear);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void saveEntry() {
        String title = titleField.getText().trim().replace("\n", " ");
        String notes = notesArea.getText().trim();
        if (title.isEmpty() || notes.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please add both a title and some notes.",
                    "Missing information", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String timestamp = LocalDateTime.now().format(FILE_DATE);
        String entryText = SEPARATOR + "\nDate: " + timestamp + "\nTitle: " + title + "\n" + notes + "\n\n";
        try {
            Path path = journalPath();
            Files.createDirectories(path.getParent());
            Files.write(path, entryText.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            clearEditor();
            refreshHistory();
            statusLabel.setText("Saved successfully • " + LocalDateTime.now().format(DISPLAY_DATE));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save the note.\n" + ex.getMessage(),
                    "File error", JOptionPane.ERROR_MESSAGE);
            statusLabel.setText("The note could not be saved.");
        }
    }

    private void clearEditor() {
        titleField.setText("");
        notesArea.setText("");
        titleField.requestFocusInWindow();
    }

    private void refreshHistory() {
        allEntries = readEntries();
        renderHistory(searchField.getText());
    }

    private List<JournalEntry> readEntries() {
        Path path = journalPath();
        if (!Files.exists(path)) return new ArrayList<>();
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String[] blocks = content.split("(?m)^={10,}\\s*$");
            List<JournalEntry> entries = new ArrayList<>();
            for (String block : blocks) {
                String trimmed = block.trim();
                if (trimmed.isEmpty()) continue;
                String date = "Date not recorded";
                String title = "Untitled entry";
                List<String> body = new ArrayList<>();
                boolean bodyStarted = false;
                for (String line : trimmed.split("\\R", -1)) {
                    if (!bodyStarted && line.startsWith("Date: ")) {
                        date = line.substring(6).trim();
                    } else if (!bodyStarted && line.startsWith("Title: ")) {
                        title = line.substring(7).trim();
                        bodyStarted = true;
                    } else if (bodyStarted) {
                        body.add(line);
                    } else if (!line.trim().isEmpty()) {
                        // Allows older or slightly different journal text to remain readable.
                        bodyStarted = true;
                        body.add(line);
                    }
                }
                while (!body.isEmpty() && body.get(body.size() - 1).trim().isEmpty()) body.remove(body.size() - 1);
                entries.add(new JournalEntry(title, date, String.join("\n", body)));
            }
            Collections.reverse(entries);
            return entries;
        } catch (IOException ex) {
            statusLabel.setText("Could not read saved notes: " + ex.getMessage());
            return new ArrayList<>();
        }
    }

    private void renderHistory(String query) {
        if (historyCards == null) return;
        historyCards.removeAll();
        String q = query == null ? "" : query.trim().toLowerCase();
        int count = 0;
        for (JournalEntry entry : allEntries) {
            if (!q.isEmpty() && !(entry.title.toLowerCase().contains(q)
                    || entry.notes.toLowerCase().contains(q) || entry.date.toLowerCase().contains(q))) continue;
            historyCards.add(createEntryCard(entry));
            historyCards.add(Box.createVerticalStrut(9));
            count++;
        }
        if (count == 0) {
            JLabel empty = new JLabel(allEntries.isEmpty()
                    ? "No notes yet. Save your first reflection →"
                    : "No notes match your search.");
            empty.setFont(new Font("SansSerif", Font.PLAIN, 13));
            empty.setForeground(MUTED);
            empty.setBorder(new EmptyBorder(16, 4, 16, 4));
            historyCards.add(empty);
        }
        historyCards.revalidate();
        historyCards.repaint();
    }

    private JPanel createEntryCard(JournalEntry entry) {
        JPanel card = new JPanel(new BorderLayout(0, 7));
        card.setBackground(new Color(250, 248, 255));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(12, 12, 12, 12)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        JLabel title = new JLabel("<html><b>" + escapeHtml(entry.title) + "</b></html>");
        title.setFont(new Font("SansSerif", Font.PLAIN, 14));
        title.setForeground(TEXT);
        JLabel date = new JLabel(formatDate(entry.date));
        date.setFont(new Font("SansSerif", Font.PLAIN, 11));
        date.setForeground(MUTED);
        String preview = entry.notes.replaceAll("\\s+", " ").trim();
        if (preview.length() > 95) preview = preview.substring(0, 92) + "…";
        JLabel excerpt = new JLabel("<html><div style='width:230px;color:#827a96'>" + escapeHtml(preview) + "</div></html>");
        excerpt.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(title);
        content.add(Box.createVerticalStrut(5));
        content.add(date);
        content.add(Box.createVerticalStrut(7));
        content.add(excerpt);
        card.add(content, BorderLayout.CENTER);
        JLabel arrow = new JLabel("›");
        arrow.setFont(new Font("SansSerif", Font.BOLD, 23));
        arrow.setForeground(PURPLE);
        card.add(arrow, BorderLayout.EAST);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        java.awt.event.MouseAdapter open = new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { showEntry(entry); }
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { card.setBackground(LIGHT_PURPLE); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { card.setBackground(new Color(250, 248, 255)); }
        };
        card.addMouseListener(open);
        for (Component c : content.getComponents()) c.addMouseListener(open);
        arrow.addMouseListener(open);
        return card;
    }

    private void showEntry(JournalEntry entry) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), entry.title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(0, 14));
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(CARD);
        content.setBorder(new EmptyBorder(22, 24, 18, 24));
        JLabel title = new JLabel("<html>" + escapeHtml(entry.title) + "</html>");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(TEXT);
        JLabel date = new JLabel(formatDate(entry.date));
        date.setFont(new Font("SansSerif", Font.PLAIN, 12));
        date.setForeground(MUTED);
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(title);
        heading.add(Box.createVerticalStrut(7));
        heading.add(date);
        content.add(heading, BorderLayout.NORTH);

        JTextArea body = new JTextArea(entry.notes);
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(new Font("SansSerif", Font.PLAIN, 15));
        body.setForeground(TEXT);
        body.setBackground(new Color(250, 248, 255));
        body.setMargin(new Insets(16, 16, 16, 16));
        body.setCaretPosition(0);
        JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        content.add(bodyScroll, BorderLayout.CENTER);
        JButton close = secondaryButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);
        bottom.add(close);
        content.add(bottom, BorderLayout.SOUTH);
        dialog.add(content, BorderLayout.CENTER);
        dialog.setSize(620, 520);
        dialog.setMinimumSize(new Dimension(480, 360));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private String formatDate(String raw) {
        try { return LocalDateTime.parse(raw, FILE_DATE).format(DISPLAY_DATE); }
        catch (Exception ignored) { return raw; }
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private Path journalPath() {
        int userId = UserSession.getUserId();
        String userPart = userId > 0 ? String.valueOf(userId) : "guest";
        return Paths.get("data", "focus-journal", "journal_user_" + userPart + ".txt");
    }

    private javax.swing.border.Border cardBorder(int padding) {
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(padding, padding, padding, padding));
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setForeground(MUTED);
        return label;
    }

    private void styleField(JTextField field) {
        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setForeground(TEXT);
        field.setBackground(new Color(250, 248, 255));
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(10, 11, 10, 11)));
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

    private static class JournalEntry {
        final String title;
        final String date;
        final String notes;
        JournalEntry(String title, String date, String notes) {
            this.title = title;
            this.date = date;
            this.notes = notes;
        }
    }
}
