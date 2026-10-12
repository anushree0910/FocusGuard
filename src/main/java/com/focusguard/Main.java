package com.focusguard;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;

public class Main {

    // --------------------------------------------------
    // TIMER VARIABLES
    // --------------------------------------------------

    private static int selectedDurationSeconds = 25 * 60;
    private static int remainingSeconds = selectedDurationSeconds;
    private static int elapsedSeconds = 0;
    private static int currentSessionId = -1;

    private static Timer timer;
    private static boolean running = false;

    // --------------------------------------------------
    // MAIN UI VARIABLES
    // --------------------------------------------------

    private static JFrame mainFrame;
    private static CardLayout cardLayout;
    private static JPanel contentPanel;
    private static WeeklyAnalyticsPanel analyticsPanel;

    private static JLabel timerLabel;
    private static JLabel timerStatusLabel;

    private static JLabel dashboardFocusValue;
    private static JLabel dashboardSessionsValue;
    private static JLabel dashboardTasksValue;

    private static JButton startButton;
    private static JButton pauseButton;
    private static JButton applyDurationButton;

    private static JSpinner hoursSpinner;
    private static JSpinner minutesSpinner;
    private static JSpinner secondsSpinner;

    private static JPanel taskCardsPanel;
    private static JPanel historyCardsPanel;

    private static JLabel taskSummaryLabel;
    private static JLabel historyFocusValue;
    private static JLabel historySessionsValue;
    private static JLabel historyCompletedValue;

    private static JTextField taskPageInput;
    private static String taskFilter = "All";

    // --------------------------------------------------
    // COLORS
    // --------------------------------------------------


    private static final TaskDAO taskDAO = new TaskDAO();
    private static final SessionDAO sessionDAO = new SessionDAO();

    // --------------------------------------------------
    // MAIN METHOD
    // --------------------------------------------------

    public static void main(String[] args) {

        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 10);

        SwingUtilities.invokeLater(Main::createGUI);
    }

    private static void createGUI() {
        mainFrame = new JFrame("FocusGuard | Productivity");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setMinimumSize(new Dimension(900, 620));
        mainFrame.setSize(1100, 720);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.getContentPane().setBackground(BACKGROUND);
        mainFrame.setLayout(new BorderLayout());

        JPanel sidebar = createSidebar();

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(BACKGROUND);

        contentPanel.add(createDashboard(), "Dashboard");
        contentPanel.add(createFocusPage(), "Focus");
        contentPanel.add(createTasksPage(), "Tasks");
        contentPanel.add(createHistoryPage(), "History");

        analyticsPanel = new WeeklyAnalyticsPanel();
        contentPanel.add(analyticsPanel, "Analytics");

        mainFrame.add(sidebar, BorderLayout.WEST);
        mainFrame.add(contentPanel, BorderLayout.CENTER);

        // Countdown timer
        timer = new Timer(1000, e -> {
            if (!running || remainingSeconds <= 0) {
                return;
            }
            remainingSeconds--;
            elapsedSeconds++;

            updateTimerLabel();

            if (remainingSeconds == 0) {
                completeCurrentSession();
            }
        });
        refreshDashboard();
        updateTimerLabel();

        mainFrame.setVisible(true);
    }
    // --------------------------------------------------
    // SIDEBAR AND NAVIGATION
    // --------------------------------------------------




    private static JButton createNavButton(String text, String page) {
        JButton button = createSidebarButton(text);

        button.addActionListener(e -> {
            if ("Dashboard".equals(page)) {
                refreshDashboard();
            } else if ("Tasks".equals(page)) {
                refreshTasksPage();
            } else if ("History".equals(page)) {
                refreshHistoryPage();
            } else if ("Analytics".equals(page)) {
                analyticsPanel.refreshAnalytics();
            }

            cardLayout.show(contentPanel, page);
        });

        return button;
    }


    // --------------------------------------------------
    // DASHBOARD
    // --------------------------------------------------

    private static JPanel createDashboard() {
        page.setBackground(BACKGROUND);

        header.setOpaque(false);
        greeting.setForeground(TEXT);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);


        stats.setOpaque(false);

        focusCard.setBackground(CARD);

        focusTitle.setForeground(TEXT);
        JLabel focusDescription = new JLabel(
        focusDescription.setFont(new Font("SansSerif", Font.PLAIN, 13));
        focusDescription.setForeground(MUTED);


        tasksButton.addActionListener(e -> {
            refreshTasksPage();
            cardLayout.show(contentPanel, "Tasks");
        });


        center.setOpaque(false);
        center.add(stats, BorderLayout.NORTH);

        page.add(header, BorderLayout.NORTH);
        page.add(center, BorderLayout.CENTER);
        return page;
    }

    private static JPanel createStatCard(
            String heading,
            String value,
            String description,
            Color accent,
            String type
    ) {
        JPanel card = new JPanel();
        card.setBackground(CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel headingLabel = new JLabel(heading);
        headingLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        headingLabel.setForeground(MUTED);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 23));
        valueLabel.setForeground(accent);

        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        descriptionLabel.setForeground(MUTED);

        card.add(headingLabel);
        card.add(Box.createVerticalStrut(12));
        card.add(valueLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(descriptionLabel);

        if ("focus".equals(type)) {
            dashboardFocusValue = valueLabel;
        } else if ("sessions".equals(type)) {
            dashboardSessionsValue = valueLabel;
        } else if ("tasks".equals(type)) {
            dashboardTasksValue = valueLabel;
        }

        return card;
    }

    // --------------------------------------------------
    // FOCUS TIMER PAGE
    // --------------------------------------------------

    private static JPanel createFocusPage() {
        page.setBackground(BACKGROUND);

        JLabel heading = new JLabel("Focus Timer");
        heading.setForeground(TEXT);

        subtitle.setForeground(MUTED);


        timerCard.setBackground(CARD);

        sessionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerLabel = new JLabel("25:00");
        timerLabel.setForeground(TEXT);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerStatusLabel = new JLabel("Ready when you are");
        timerStatusLabel.setForeground(MUTED);
        timerStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        durationPanel.setOpaque(false);
        durationPanel.add(createDurationInput("Hours", hoursSpinner));
        durationPanel.add(createDurationInput("Minutes", minutesSpinner));
        durationPanel.add(createDurationInput("Seconds", secondsSpinner));



        pauseButton.setEnabled(false);

        startButton.addActionListener(e -> startOrResumeSession());
        pauseButton.addActionListener(e -> {
            if (running) {
                pauseCurrentSession();
            } else if (currentSessionId != -1) {
                startOrResumeSession();
            }
        });
        resetButton.addActionListener(e -> stopAndResetSession());


        page.add(header, BorderLayout.NORTH);
        return page;
    }

    private static JPanel createDurationInput(
            String label,
            JSpinner spinner
    ) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel text = new JLabel(label);
        text.setForeground(MUTED);
        text.setFont(new Font("SansSerif", Font.PLAIN, 12));
        text.setAlignmentX(Component.CENTER_ALIGNMENT);

        spinner.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(text);
        panel.add(Box.createVerticalStrut(5));
        panel.add(spinner);

        return panel;
    }

    // --------------------------------------------------
    // APPLY CUSTOM DURATION
    // --------------------------------------------------

    private static boolean applyCustomDuration(boolean showMessage) {
        if (hoursSpinner == null
                || minutesSpinner == null
                || secondsSpinner == null) {
            return false;
        }

        // Do not change the duration during an existing session.
        if (running || currentSessionId != -1) {
            if (showMessage) {
                JOptionPane.showMessageDialog(
                        mainFrame,
                        "Stop / Reset the current session before "
                                + "changing the duration.",
                        "Session in Progress",
                        JOptionPane.WARNING_MESSAGE
                );
            }
            return false;
        }

        try {
            // Commit values if the user typed directly into a spinner.
            hoursSpinner.commitEdit();
            minutesSpinner.commitEdit();
            secondsSpinner.commitEdit();

            int hours = (Integer) hoursSpinner.getValue();
            int minutes = (Integer) minutesSpinner.getValue();
            int seconds = (Integer) secondsSpinner.getValue();

            long total = (long) hours * 3600
                    + (long) minutes * 60
                    + seconds;

            if (total <= 0 || total > Integer.MAX_VALUE) {
                if (showMessage) {
                    JOptionPane.showMessageDialog(
                            mainFrame,
                            "Choose a duration greater than zero.",
                            "Invalid Duration",
                            JOptionPane.WARNING_MESSAGE
                    );
                }
                return false;
            }

            selectedDurationSeconds = (int) total;
            remainingSeconds = selectedDurationSeconds;
            elapsedSeconds = 0;

            updateTimerLabel();

            timerStatusLabel.setText(
                    "Duration applied: "
                            + formatClock(selectedDurationSeconds)
            );

            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
            pauseButton.setText("Pause");
            applyDurationButton.setEnabled(true);

            return true;

        } catch (ParseException | NumberFormatException ex) {
            if (showMessage) {
                JOptionPane.showMessageDialog(
                        mainFrame,
                        "Enter valid whole numbers for the duration.",
                        "Invalid Input",
                        JOptionPane.WARNING_MESSAGE
                );
            }
            return false;
        }
    }

    // --------------------------------------------------
    // COMPLETE SESSION
    // --------------------------------------------------

    private static void completeCurrentSession() {
        if (currentSessionId == -1) {
            return;
        }

        timer.stop();
        running = false;
        remainingSeconds = 0;
        updateTimerLabel();

        boolean saved = finishCurrentSession("Completed");

        startButton.setEnabled(true);
        pauseButton.setEnabled(false);
        pauseButton.setText("Pause");

        if (saved) {
            elapsedSeconds = 0;
            remainingSeconds = selectedDurationSeconds;

            updateTimerLabel();

            applyDurationButton.setEnabled(true);
            timerStatusLabel.setText("Session complete!");

            refreshDashboard();
            refreshHistoryPage();
            analyticsPanel.refreshAnalytics();

            JOptionPane.showMessageDialog(
                    mainFrame,
                    "Great work! Your focus session is complete.",
                    "FocusGuard",
                    JOptionPane.INFORMATION_MESSAGE
            );

            timerStatusLabel.setText("Ready when you are");

        } else {
            // Keep the zero timer visible if the database save failed.
            remainingSeconds = 0;
            timerStatusLabel.setText(
                    "Saving failed. Try Stop / Reset to retry."
            );
            startButton.setEnabled(false);
            pauseButton.setEnabled(false);
        }
    }

    // --------------------------------------------------
    // STOP AND RESET
    // --------------------------------------------------

    private static void stopAndResetSession() {
        if (timer != null) {
            timer.stop();
        }

        running = false;

        boolean saved = finishCurrentSession("Stopped");

        if (!saved) {
            // Preserve the session ID so the user can retry saving.
            timerStatusLabel.setText(
                    "Could not save session. Please try Stop / Reset again."
            );
            startButton.setEnabled(false);
            pauseButton.setEnabled(false);
            return;
        }

        currentSessionId = -1;
        elapsedSeconds = 0;
        remainingSeconds = selectedDurationSeconds;

        updateTimerLabel();

        timerStatusLabel.setText("Ready when you are");

        startButton.setEnabled(true);
        pauseButton.setEnabled(false);
        pauseButton.setText("Pause");
        applyDurationButton.setEnabled(true);

        refreshDashboard();
        refreshHistoryPage();
        analyticsPanel.refreshAnalytics();
    }

    // --------------------------------------------------
    // TASKS PAGE
    // --------------------------------------------------

    private static JPanel createTasksPage() {
        page.setBackground(BACKGROUND);
        page.setBorder(new EmptyBorder(32, 30, 25, 30));

        JLabel title = new JLabel("My Tasks");
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel(
        );
        subtitle.setForeground(MUTED);

        header.add(title);
        header.add(subtitle);

        addCard.setBackground(CARD);

        taskPageInput = new JTextField();
        taskPageInput.putClientProperty(
                "JTextField.placeholderText",
                "What do you want to accomplish?"
        );
        taskPageInput.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JButton addButton = createPrimaryButton("+  Add Task");
        addButton.addActionListener(e -> addTaskFromPage());
        taskPageInput.addActionListener(e -> addTaskFromPage());


        JPanel filterPanel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 8, 0)
        );
        filterPanel.setOpaque(false);

        for (String filter : new String[]{"All", "Pending", "Completed"}) {

            filterButton.addActionListener(e -> {
                taskFilter = filter;
                refreshTasksPage();
            });

            filterPanel.add(filterButton);
        }

        taskSummaryLabel.setForeground(TEXT);

        listHeader.setOpaque(false);
        listHeader.add(taskSummaryLabel, BorderLayout.NORTH);
        listHeader.add(filterPanel, BorderLayout.SOUTH);

        taskCardsPanel = new JPanel();
        taskCardsPanel.setOpaque(false);
        taskCardsPanel.setLayout(
                new BoxLayout(taskCardsPanel, BoxLayout.Y_AXIS)
        );

        JScrollPane scrollPane = new JScrollPane(taskCardsPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        taskListArea.setOpaque(false);
        taskListArea.add(listHeader, BorderLayout.NORTH);
        taskListArea.add(scrollPane, BorderLayout.CENTER);

        center.add(taskListArea, BorderLayout.CENTER);

        page.add(header, BorderLayout.NORTH);
        page.add(center, BorderLayout.CENTER);

        return page;
    }

    private static void addTaskFromPage() {
        String title = taskPageInput.getText().trim();

        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "Please enter a task.");
            return;
        }

        try {
            taskDAO.addTask(title);
            taskPageInput.setText("");
            taskFilter = "All";
            refreshTasksPage();
            refreshDashboard();
        } catch (SQLException ex) {
            showDatabaseError(mainFrame, ex);
        }
    }

    private static void refreshTasksPage() {
        if (taskCardsPanel == null) {
            return;
        }

        taskCardsPanel.removeAll();

        try {
            List<TaskDAO.Task> tasks = taskDAO.getAllTasks();

            int total = tasks.size();
            int completed = 0;
            int shown = 0;

            for (TaskDAO.Task task : tasks) {
                boolean isCompleted =
                        "Completed".equalsIgnoreCase(task.status);

                if (isCompleted) {
                    completed++;
                }

                if ("Pending".equals(taskFilter) && isCompleted) {
                    continue;
                }

                if ("Completed".equals(taskFilter) && !isCompleted) {
                    continue;
                }

                taskCardsPanel.add(createTaskCard(task));
                shown++;
            }

            taskSummaryLabel.setText(
            );

            if (shown == 0) {
                );
            }

        } catch (SQLException ex) {
            showDatabaseError(mainFrame, ex);
        }

        taskCardsPanel.revalidate();
        taskCardsPanel.repaint();
    }

    private static JPanel createTaskCard(TaskDAO.Task task) {
        boolean completed =
                "Completed".equalsIgnoreCase(task.status);

        card.setBackground(CARD);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(completed);
        checkBox.setOpaque(false);
        checkBox.setFocusable(false);
        checkBox.setToolTipText("Mark task complete or pending");

        checkBox.addActionListener(e -> {
            String newStatus = checkBox.isSelected()
                    ? "Completed" : "Pending";

            try {
                taskDAO.updateTaskStatus(task.id, newStatus);
                refreshTasksPage();
                refreshDashboard();
            } catch (SQLException ex) {
                showDatabaseError(mainFrame, ex);
            }
        });

        JLabel taskTitle = new JLabel(
                "<html><div style='width:220px;'>"
                        + escapeHtml(task.title)
                        + "</div></html>"
        );
        taskTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        taskTitle.setForeground(completed ? MUTED : TEXT);

        status.setFont(new Font("SansSerif", Font.BOLD, 10));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(taskTitle);
        textPanel.add(status);

        JButton deleteButton = createSecondaryButton("Delete");
        deleteButton.setForeground(RED);

        deleteButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                    mainFrame,
                    "Delete \"" + task.title + "\"?",
                    "Delete Task",
            );

            if (choice == JOptionPane.YES_OPTION) {
                try {
                    taskDAO.deleteTask(task.id);
                    refreshTasksPage();
                    refreshDashboard();
                } catch (SQLException ex) {
                    showDatabaseError(mainFrame, ex);
                }
            }
        });

        card.add(checkBox, BorderLayout.WEST);
        card.add(textPanel, BorderLayout.CENTER);
        card.add(deleteButton, BorderLayout.EAST);

        return card;
    }


    private static JPanel createHistoryPage() {
        page.setBackground(BACKGROUND);
        page.setBorder(new EmptyBorder(32, 30, 25, 30));

        JLabel title = new JLabel("Focus History");
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel(
                "Look back at your effort. Every focused minute counts."
        );
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);

        header.add(subtitle);

        JPanel stats = new JPanel(new GridLayout(1, 3, 12, 0));
        stats.setOpaque(false);

        historyFocusValue = new JLabel("00:00:00");
        historySessionsValue = new JLabel("0");
        historyCompletedValue = new JLabel("0");

        stats.add(createHistoryStatCard(
        ));

        stats.add(createHistoryStatCard(
        ));

        stats.add(createHistoryStatCard(
        ));

        sectionTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        sectionTitle.setForeground(TEXT);

        historyCardsPanel = new JPanel();
        historyCardsPanel.setOpaque(false);
        historyCardsPanel.setLayout(
                new BoxLayout(historyCardsPanel, BoxLayout.Y_AXIS)
        );

        JScrollPane scrollPane = new JScrollPane(historyCardsPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        activityPanel.setOpaque(false);
        activityPanel.add(scrollPane, BorderLayout.CENTER);

        center.setOpaque(false);
        center.add(stats, BorderLayout.NORTH);
        center.add(activityPanel, BorderLayout.CENTER);

        page.add(center, BorderLayout.CENTER);

        return page;
    }

    private static JPanel createHistoryStatCard(
            String heading,
            JLabel value,
            Color accent
    ) {
        JPanel card = new JPanel();
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(16, 14, 14, 10));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(heading);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setForeground(MUTED);

        value.setFont(new Font("SansSerif", Font.BOLD, 22));
        value.setForeground(accent);

        card.add(label);
        card.add(Box.createVerticalStrut(12));
        card.add(value);

        return card;
    }

    private static void refreshHistoryPage() {
        if (historyCardsPanel == null) {
            return;
        }

        historyCardsPanel.removeAll();

        try {
            List<SessionDAO.FocusSession> sessions =
                    sessionDAO.getTodaySessions();

            long totalSeconds = 0;
            int completedCount = 0;

            for (SessionDAO.FocusSession session : sessions) {
                boolean isCurrent = session.id == currentSessionId;

                int seconds = isCurrent
                        ? elapsedSeconds : session.elapsedSeconds;

                String displayStatus = isCurrent
                        ? (running ? "Active" : "Paused")
                        : session.status;

                totalSeconds += seconds;

                if ("Completed".equalsIgnoreCase(displayStatus)) {
                    completedCount++;
                }

                historyCardsPanel.add(
                        createHistoryCard(session, seconds, displayStatus)
                );
                historyCardsPanel.add(Box.createVerticalStrut(10));
            }

            boolean currentSessionFound = false;

            for (SessionDAO.FocusSession session : sessions) {
                if (session.id == currentSessionId) {
                    currentSessionFound = true;
                    break;
                }
            }

            if (currentSessionId != -1 && !currentSessionFound) {
                totalSeconds += elapsedSeconds;
            }

            historyFocusValue.setText(formatDuration(totalSeconds));
            historySessionsValue.setText(String.valueOf(sessions.size()));
            historyCompletedValue.setText(String.valueOf(completedCount));

            if (sessions.isEmpty()) {
                JLabel empty = new JLabel(
                        "<html><b>No sessions yet today</b><br><br>"
                                + "Start a focus session and your activity "
                                + "will appear here.</html>"
                );
                empty.setFont(new Font("SansSerif", Font.PLAIN, 14));
                empty.setForeground(MUTED);
                empty.setBorder(new EmptyBorder(25, 10, 25, 10));
                historyCardsPanel.add(empty);
            }

        } catch (SQLException ex) {
            showDatabaseError(mainFrame, ex);
        }

        historyCardsPanel.revalidate();
        historyCardsPanel.repaint();
    }

    private static JPanel createHistoryCard(
            SessionDAO.FocusSession session,
            int seconds,
            String statusText
    ) {
        card.setBackground(CARD);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel icon = new JLabel(completed ? "✓" : "◷");

        String started = session.startTime == null
                ? "Unknown"
                : session.startTime.toLocalDateTime()
                .toLocalTime().withNano(0).toString();

        String ended;

        if (session.endTime != null) {
            ended = session.endTime.toLocalDateTime()
                    .toLocalTime().withNano(0).toString();
            ended = "Paused";
            ended = "In progress";
        } else {
            ended = "Not recorded";
        }


        JLabel sessionTitle = new JLabel(
                escapeHtml(mode) + " Session"
        );
        sessionTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        sessionTitle.setForeground(TEXT);

        JLabel details = new JLabel(
        );
        details.setFont(new Font("SansSerif", Font.PLAIN, 12));
        details.setForeground(MUTED);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.add(sessionTitle);
        textPanel.add(details);

        JLabel duration = new JLabel(formatDuration(seconds));
        duration.setFont(new Font("SansSerif", Font.BOLD, 16));
        duration.setForeground(PURPLE);

        JLabel status = new JLabel(
        );
        status.setFont(new Font("SansSerif", Font.BOLD, 10));

        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.add(duration);
        rightPanel.add(status);

        card.add(icon, BorderLayout.WEST);
        card.add(textPanel, BorderLayout.CENTER);
        card.add(rightPanel, BorderLayout.EAST);

        return card;
    }

    // --------------------------------------------------
    // DASHBOARD DATA
    // --------------------------------------------------

    private static void refreshDashboard() {
        if (dashboardFocusValue == null) {
            return;
        }

        try {
            List<SessionDAO.FocusSession> sessions =
                    sessionDAO.getTodaySessions();

            long totalSeconds = 0;
            int sessionCount = sessions.size();
            boolean currentSessionFound = false;

            for (SessionDAO.FocusSession session : sessions) {
                if (session.id == currentSessionId && currentSessionId != -1) {
                    currentSessionFound = true;
                    totalSeconds += elapsedSeconds;
                } else {
                    totalSeconds += session.elapsedSeconds;
                }
            }

            if (currentSessionId != -1 && !currentSessionFound) {
                totalSeconds += elapsedSeconds;
                sessionCount++;
            }

            dashboardFocusValue.setText(formatDuration(totalSeconds));
            dashboardSessionsValue.setText(String.valueOf(sessionCount));

            List<TaskDAO.Task> tasks = taskDAO.getAllTasks();
            int completed = 0;

            for (TaskDAO.Task task : tasks) {
                if ("Completed".equalsIgnoreCase(task.status)) {
                    completed++;
                }
            }

            dashboardTasksValue.setText(String.valueOf(completed));

        } catch (SQLException ex) {
            showDatabaseError(mainFrame, ex);
        }
    }

    // --------------------------------------------------
    // START OR RESUME SESSION
    // --------------------------------------------------

    private static void startOrResumeSession() {
        if (running) {
            return;
        }

        // For every NEW session, read the latest input values.
        // This prevents the default 25 minutes from overriding the user's choice.
        if (currentSessionId == -1) {
            if (!applyCustomDuration(true)) {
                return;
            }
        }

        if (remainingSeconds <= 0) {
            JOptionPane.showMessageDialog(
                    mainFrame,
                    "Please apply a valid duration before starting.",
                    "Invalid Duration",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        try {
            if (currentSessionId == -1) {
                int durationMinutes =
                        Math.max(1, (selectedDurationSeconds + 59) / 60);

                currentSessionId = sessionDAO.startSession(
                        "Study",
                        "Custom",
                        durationMinutes
                );

                elapsedSeconds = 0;

            } else {
                sessionDAO.updateSessionProgress(
                        currentSessionId,
                        "Active",
                        elapsedSeconds
                );
            }

            running = true;
            timer.start();

            startButton.setEnabled(false);
            pauseButton.setEnabled(true);
            pauseButton.setText("Pause");
            applyDurationButton.setEnabled(false);

            timerStatusLabel.setText("Stay focused. You've got this!");

            refreshDashboard();
            refreshHistoryPage();

        } catch (SQLException ex) {
            showDatabaseError(mainFrame, ex);
        }
    }

    // --------------------------------------------------
    // PAUSE SESSION
    // --------------------------------------------------

    private static void pauseCurrentSession() {
        timer.stop();
        running = false;

        try {
            if (currentSessionId != -1) {
                sessionDAO.updateSessionProgress(
                        currentSessionId,
                        "Paused",
                        elapsedSeconds
                );
            }

            startButton.setEnabled(false);
            pauseButton.setEnabled(true);
            pauseButton.setText("Resume");
            applyDurationButton.setEnabled(false);

            timerStatusLabel.setText("Session paused");

            refreshDashboard();
            refreshHistoryPage();

        } catch (SQLException ex) {
            showDatabaseError(mainFrame, ex);
            startButton.setEnabled(false);
            pauseButton.setEnabled(true);
            pauseButton.setText("Resume");
        }
    }

    // --------------------------------------------------
    // SAVE SESSION STATUS
    // --------------------------------------------------

    private static boolean finishCurrentSession(String status) {
        if (currentSessionId == -1) {
            return true;
        }

        try {
            sessionDAO.finishSession(
                    currentSessionId,
                    status,
                    elapsedSeconds
            );

            currentSessionId = -1;
            return true;

        } catch (SQLException ex) {
            showDatabaseError(mainFrame, ex);
            return false;
        }
    }

    // --------------------------------------------------
    // SHARED UI HELPERS
    // --------------------------------------------------

    private static JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(PURPLE);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static JButton createSecondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static String formatDuration(long seconds) {
        return String.format(
                "%02d:%02d:%02d",
                seconds / 3600,
                (seconds % 3600) / 60,
                seconds % 60
        );
    }

    private static String formatClock(int seconds) {
        if (seconds >= 3600) {
            return String.format(
                    "%02d:%02d:%02d",
                    seconds / 3600,
                    (seconds % 3600) / 60,
                    seconds % 60
            );
        }

        return String.format(
                "%02d:%02d",
                seconds / 60,
                seconds % 60
        );
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "Focus";
        }

        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static void updateTimerLabel() {
        if (timerLabel == null) {
            return;
        }

        timerLabel.setText(formatClock(remainingSeconds));
    }

    private static void showDatabaseError(
            Component parent,
            SQLException ex
    ) {
        ex.printStackTrace();

        JOptionPane.showMessageDialog(
                parent,
                "Could not access the FocusGuard database.\n"
                        + ex.getMessage(),
                "Database Error",
                JOptionPane.ERROR_MESSAGE
        );
    }
}