
package com.focusguard;

import com.formdev.flatlaf.FlatDarkLaf;

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

    private static final Color BACKGROUND = new Color(17, 20, 31);
    private static final Color SIDEBAR = new Color(22, 26, 40);
    private static final Color CARD = new Color(29, 34, 51);
    private static final Color PURPLE = new Color(126, 94, 247);
    private static final Color TEXT = new Color(242, 244, 255);
    private static final Color MUTED = new Color(157, 166, 190);
    private static final Color GREEN = new Color(65, 207, 151);
    private static final Color BLUE = new Color(73, 164, 255);
    private static final Color ORANGE = new Color(255, 190, 90);
    private static final Color RED = new Color(255, 105, 120);

    private static final TaskDAO taskDAO = new TaskDAO();
    private static final SessionDAO sessionDAO = new SessionDAO();

    // --------------------------------------------------
    // MAIN METHOD
    // --------------------------------------------------

    public static void main(String[] args) {
        FlatDarkLaf.setup();

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

    private static JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(SIDEBAR);
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(new EmptyBorder(25, 15, 20, 15));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel("  ◉ FocusGuard");
        logo.setFont(new Font("SansSerif", Font.BOLD, 23));
        logo.setForeground(TEXT);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tagline = new JLabel("  YOUR PRODUCTIVITY SPACE");
        tagline.setFont(new Font("SansSerif", Font.PLAIN, 10));
        tagline.setForeground(MUTED);
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        sidebar.add(logo);
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(tagline);
        sidebar.add(Box.createVerticalStrut(35));

        sidebar.add(createNavButton("Dashboard", "Dashboard"));
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(createNavButton("Focus Timer", "Focus"));
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(createNavButton("My Tasks", "Tasks"));
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(createNavButton("Focus History", "History"));
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(createNavButton("Weekly Analytics", "Analytics"));

        sidebar.add(Box.createVerticalGlue());

        JLabel footer = new JLabel(
                "<html><font color='#9DA6BE'>"
                        + "BUILD BETTER HABITS<br>"
                        + "ONE SESSION AT A TIME"
                        + "</font></html>"
        );
        footer.setFont(new Font("SansSerif", Font.PLAIN, 10));
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(footer);

        return sidebar;
    }

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

    private static JButton createSidebarButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setForeground(TEXT);
        button.setBackground(SIDEBAR);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        button.setPreferredSize(new Dimension(190, 45));
        button.setBorder(new EmptyBorder(10, 14, 10, 8));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(CARD);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(SIDEBAR);
            }
        });

        return button;
    }

    // --------------------------------------------------
    // DASHBOARD
    // --------------------------------------------------

    private static JPanel createDashboard() {
        JPanel page = new JPanel(new BorderLayout(0, 25));
        page.setBackground(BACKGROUND);
        page.setBorder(new EmptyBorder(35, 35, 30, 35));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel greeting = new JLabel("Your Productivity Dashboard");
        greeting.setFont(new Font("SansSerif", Font.BOLD, 28));
        greeting.setForeground(TEXT);

        JLabel subtitle = new JLabel(
                "Small steps today. Big progress tomorrow."
        );
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);

        header.add(greeting);
        header.add(Box.createVerticalStrut(8));
        header.add(subtitle);

        JPanel stats = new JPanel(new GridLayout(1, 3, 15, 0));
        stats.setOpaque(false);
        stats.setPreferredSize(new Dimension(100, 130));

        stats.add(createStatCard(
                "FOCUS TIME TODAY", "00:00:00",
                "Time spent focusing", PURPLE, "focus"
        ));

        stats.add(createStatCard(
                "SESSIONS TODAY", "0",
                "Sessions started today", BLUE, "sessions"
        ));

        stats.add(createStatCard(
                "TASKS COMPLETED", "0",
                "Your completed tasks", GREEN, "tasks"
        ));

        JPanel focusCard = new JPanel();
        focusCard.setBackground(CARD);
        focusCard.setBorder(new EmptyBorder(25, 25, 25, 25));
        focusCard.setLayout(new BoxLayout(focusCard, BoxLayout.Y_AXIS));

        JLabel focusHeading = new JLabel("READY TO FOCUS?");
        focusHeading.setFont(new Font("SansSerif", Font.BOLD, 13));
        focusHeading.setForeground(MUTED);
        focusHeading.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel focusTitle = new JLabel("Make this session count");
        focusTitle.setFont(new Font("SansSerif", Font.BOLD, 22));
        focusTitle.setForeground(TEXT);
        focusTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel focusDescription = new JLabel(
                "Set aside distractions and work on what matters."
        );
        focusDescription.setFont(new Font("SansSerif", Font.PLAIN, 13));
        focusDescription.setForeground(MUTED);
        focusDescription.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton focusButton = createPrimaryButton("Set Up Focus Session");
        focusButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Open Focus page; do not start a session automatically.
        focusButton.addActionListener(e ->
                cardLayout.show(contentPanel, "Focus")
        );

        focusCard.add(focusHeading);
        focusCard.add(Box.createVerticalStrut(12));
        focusCard.add(focusTitle);
        focusCard.add(Box.createVerticalStrut(8));
        focusCard.add(focusDescription);
        focusCard.add(Box.createVerticalStrut(22));
        focusCard.add(focusButton);

        JPanel tipCard = new JPanel();
        tipCard.setBackground(CARD);
        tipCard.setBorder(new EmptyBorder(18, 20, 18, 20));
        tipCard.setLayout(new BoxLayout(tipCard, BoxLayout.Y_AXIS));

        JLabel tipTitle = new JLabel("YOUR DAILY REMINDER");
        tipTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        tipTitle.setForeground(GREEN);

        JLabel tipText = new JLabel(
                "<html>Consistency beats intensity.<br>"
                        + "Focus on one task at a time.</html>"
        );
        tipText.setFont(new Font("SansSerif", Font.PLAIN, 14));
        tipText.setForeground(TEXT);

        tipCard.add(tipTitle);
        tipCard.add(Box.createVerticalStrut(10));
        tipCard.add(tipText);

        JButton tasksButton = createSecondaryButton("Manage My Tasks");
        tasksButton.addActionListener(e -> {
            refreshTasksPage();
            cardLayout.show(contentPanel, "Tasks");
        });

        JPanel lower = new JPanel(new BorderLayout(15, 0));
        lower.setOpaque(false);
        lower.add(tipCard, BorderLayout.CENTER);
        lower.add(tasksButton, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout(0, 20));
        center.setOpaque(false);
        center.add(stats, BorderLayout.NORTH);
        center.add(focusCard, BorderLayout.CENTER);
        center.add(lower, BorderLayout.SOUTH);

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
        card.setBorder(new EmptyBorder(18, 16, 18, 12));
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
        JPanel page = new JPanel(new BorderLayout(0, 25));
        page.setBackground(BACKGROUND);
        page.setBorder(new EmptyBorder(35, 35, 30, 35));

        JLabel heading = new JLabel("Focus Timer");
        heading.setFont(new Font("SansSerif", Font.BOLD, 28));
        heading.setForeground(TEXT);

        JLabel subtitle = new JLabel(
                "Choose your duration and focus on one thing at a time."
        );
        subtitle.setForeground(MUTED);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(heading);
        header.add(Box.createVerticalStrut(8));
        header.add(subtitle);

        JPanel timerCard = new JPanel();
        timerCard.setBackground(CARD);
        timerCard.setBorder(new EmptyBorder(25, 20, 25, 20));
        timerCard.setLayout(new BoxLayout(timerCard, BoxLayout.Y_AXIS));

        JLabel sessionLabel = new JLabel("CUSTOM FOCUS SESSION");
        sessionLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        sessionLabel.setForeground(PURPLE);
        sessionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerLabel = new JLabel("25:00");
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 64));
        timerLabel.setForeground(TEXT);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerStatusLabel = new JLabel("Ready when you are");
        timerStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        timerStatusLabel.setForeground(MUTED);
        timerStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Custom duration inputs
        JPanel durationPanel = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 8, 5)
        );
        durationPanel.setOpaque(false);

        hoursSpinner = new JSpinner(
                new SpinnerNumberModel(0, 0, 23, 1)
        );
        minutesSpinner = new JSpinner(
                new SpinnerNumberModel(25, 0, 59, 1)
        );
        secondsSpinner = new JSpinner(
                new SpinnerNumberModel(0, 0, 59, 1)
        );

        hoursSpinner.setPreferredSize(new Dimension(75, 32));
        minutesSpinner.setPreferredSize(new Dimension(75, 32));
        secondsSpinner.setPreferredSize(new Dimension(75, 32));

        durationPanel.add(createDurationInput("Hours", hoursSpinner));
        durationPanel.add(createDurationInput("Minutes", minutesSpinner));
        durationPanel.add(createDurationInput("Seconds", secondsSpinner));

        applyDurationButton = createSecondaryButton("Apply Duration");
        applyDurationButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        applyDurationButton.addActionListener(
                e -> applyCustomDuration(true)
        );

        startButton = createPrimaryButton("Start Focus");
        pauseButton = createSecondaryButton("Pause");
        JButton resetButton = createSecondaryButton("Stop / Reset");

        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        pauseButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        resetButton.setAlignmentX(Component.CENTER_ALIGNMENT);

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

        timerCard.add(sessionLabel);
        timerCard.add(Box.createVerticalStrut(15));
        timerCard.add(timerLabel);
        timerCard.add(Box.createVerticalStrut(8));
        timerCard.add(timerStatusLabel);
        timerCard.add(Box.createVerticalStrut(20));
        timerCard.add(durationPanel);
        timerCard.add(Box.createVerticalStrut(8));
        timerCard.add(applyDurationButton);
        timerCard.add(Box.createVerticalStrut(20));
        timerCard.add(startButton);
        timerCard.add(Box.createVerticalStrut(10));
        timerCard.add(pauseButton);
        timerCard.add(Box.createVerticalStrut(10));
        timerCard.add(resetButton);

        page.add(header, BorderLayout.NORTH);
        page.add(timerCard, BorderLayout.CENTER);

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
        JPanel page = new JPanel(new BorderLayout(0, 22));
        page.setBackground(BACKGROUND);
        page.setBorder(new EmptyBorder(32, 30, 25, 30));

        JLabel title = new JLabel("My Tasks");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel(
                "Organize your work. Celebrate your progress."
        );
        subtitle.setForeground(MUTED);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(title);
        header.add(Box.createVerticalStrut(7));
        header.add(subtitle);

        JPanel addCard = new JPanel(new BorderLayout(10, 0));
        addCard.setBackground(CARD);
        addCard.setBorder(new EmptyBorder(16, 16, 16, 16));

        taskPageInput = new JTextField();
        taskPageInput.putClientProperty(
                "JTextField.placeholderText",
                "What do you want to accomplish?"
        );
        taskPageInput.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JButton addButton = createPrimaryButton("+  Add Task");
        addButton.addActionListener(e -> addTaskFromPage());
        taskPageInput.addActionListener(e -> addTaskFromPage());

        addCard.add(taskPageInput, BorderLayout.CENTER);
        addCard.add(addButton, BorderLayout.EAST);

        JPanel filterPanel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 8, 0)
        );
        filterPanel.setOpaque(false);

        for (String filter : new String[]{"All", "Pending", "Completed"}) {
            JButton filterButton = createSecondaryButton(filter);

            filterButton.addActionListener(e -> {
                taskFilter = filter;
                refreshTasksPage();
            });

            filterPanel.add(filterButton);
        }

        taskSummaryLabel = new JLabel("Your tasks");
        taskSummaryLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        taskSummaryLabel.setForeground(TEXT);

        JPanel listHeader = new JPanel(new BorderLayout(0, 10));
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
        scrollPane.getViewport().setBackground(BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel center = new JPanel(new BorderLayout(0, 15));
        center.setOpaque(false);
        center.add(addCard, BorderLayout.NORTH);

        JPanel taskListArea = new JPanel(new BorderLayout(0, 12));
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
                taskCardsPanel.add(Box.createVerticalStrut(10));
                shown++;
            }

            taskSummaryLabel.setText(
                    total + " tasks  •  " + completed + " completed"
            );

            if (shown == 0) {
                JLabel empty = new JLabel(
                        "<html><b>No " + taskFilter.toLowerCase()
                                + " tasks here.</b><br><br>"
                                + "Add a task to get started!</html>"
                );
                empty.setForeground(MUTED);
                empty.setFont(new Font("SansSerif", Font.PLAIN, 14));
                empty.setBorder(new EmptyBorder(25, 5, 25, 5));
                taskCardsPanel.add(empty);
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

        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 85));
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

        JLabel status = new JLabel(completed ? "COMPLETED" : "PENDING");
        status.setFont(new Font("SansSerif", Font.BOLD, 10));
        status.setForeground(completed ? GREEN : ORANGE);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(taskTitle);
        textPanel.add(Box.createVerticalStrut(5));
        textPanel.add(status);

        JButton deleteButton = createSecondaryButton("Delete");
        deleteButton.setForeground(RED);

        deleteButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                    mainFrame,
                    "Delete \"" + task.title + "\"?",
                    "Delete Task",
                    JOptionPane.YES_NO_OPTION
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

    // --------------------------------------------------
    // FOCUS HISTORY PAGE
    // --------------------------------------------------

    private static JPanel createHistoryPage() {
        JPanel page = new JPanel(new BorderLayout(0, 22));
        page.setBackground(BACKGROUND);
        page.setBorder(new EmptyBorder(32, 30, 25, 30));

        JLabel title = new JLabel("Focus History");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel(
                "Look back at your effort. Every focused minute counts."
        );
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);

        JButton refreshButton = createSecondaryButton("↻  Refresh");
        refreshButton.addActionListener(e -> refreshHistoryPage());

        JPanel titleRow = new JPanel(new BorderLayout(10, 0));
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(refreshButton, BorderLayout.EAST);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(titleRow);
        header.add(Box.createVerticalStrut(8));
        header.add(subtitle);

        JPanel stats = new JPanel(new GridLayout(1, 3, 12, 0));
        stats.setOpaque(false);
        stats.setPreferredSize(new Dimension(100, 115));

        historyFocusValue = new JLabel("00:00:00");
        historySessionsValue = new JLabel("0");
        historyCompletedValue = new JLabel("0");

        stats.add(createHistoryStatCard(
                "TOTAL FOCUS TIME", historyFocusValue, PURPLE
        ));

        stats.add(createHistoryStatCard(
                "SESSIONS TODAY", historySessionsValue, BLUE
        ));

        stats.add(createHistoryStatCard(
                "COMPLETED SESSIONS", historyCompletedValue, GREEN
        ));

        JLabel sectionTitle = new JLabel("TODAY'S ACTIVITY");
        sectionTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        sectionTitle.setForeground(TEXT);

        historyCardsPanel = new JPanel();
        historyCardsPanel.setOpaque(false);
        historyCardsPanel.setLayout(
                new BoxLayout(historyCardsPanel, BoxLayout.Y_AXIS)
        );

        JScrollPane scrollPane = new JScrollPane(historyCardsPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel activityPanel = new JPanel(new BorderLayout(0, 12));
        activityPanel.setOpaque(false);
        activityPanel.add(sectionTitle, BorderLayout.NORTH);
        activityPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 20));
        center.setOpaque(false);
        center.add(stats, BorderLayout.NORTH);
        center.add(activityPanel, BorderLayout.CENTER);

        page.add(header, BorderLayout.NORTH);
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
        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(15, 17, 15, 17));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        boolean completed = "Completed".equalsIgnoreCase(statusText);

        JLabel icon = new JLabel(completed ? "✓" : "◷");
        icon.setFont(new Font("SansSerif", Font.BOLD, 25));
        icon.setForeground(completed ? GREEN : PURPLE);

        String started = session.startTime == null
                ? "Unknown"
                : session.startTime.toLocalDateTime()
                .toLocalTime().withNano(0).toString();

        String ended;

        if (session.endTime != null) {
            ended = session.endTime.toLocalDateTime()
                    .toLocalTime().withNano(0).toString();
        } else if ("Paused".equalsIgnoreCase(statusText)) {
            ended = "Paused";
        } else if ("Active".equalsIgnoreCase(statusText)) {
            ended = "In progress";
        } else {
            ended = "Not recorded";
        }

        String mode = session.mode == null ? "Focus" : session.mode;

        JLabel sessionTitle = new JLabel(
                escapeHtml(mode) + " Session"
        );
        sessionTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        sessionTitle.setForeground(TEXT);

        JLabel details = new JLabel(
                "Started " + started + "   •   Ended " + ended
        );
        details.setFont(new Font("SansSerif", Font.PLAIN, 12));
        details.setForeground(MUTED);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(sessionTitle);
        textPanel.add(Box.createVerticalStrut(8));
        textPanel.add(details);

        JLabel duration = new JLabel(formatDuration(seconds));
        duration.setFont(new Font("SansSerif", Font.BOLD, 16));
        duration.setForeground(PURPLE);

        JLabel status = new JLabel(
                statusText == null ? "UNKNOWN" : statusText.toUpperCase()
        );
        status.setFont(new Font("SansSerif", Font.BOLD, 10));
        status.setForeground(
                completed ? GREEN
                        : "Paused".equalsIgnoreCase(statusText)
                        ? ORANGE : MUTED
        );

        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.add(duration);
        rightPanel.add(Box.createVerticalStrut(6));
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
        button.setBorder(new EmptyBorder(12, 25, 12, 25));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static JButton createSecondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setForeground(TEXT);
        button.setBackground(new Color(49, 56, 77));
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(11, 20, 11, 20));
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
