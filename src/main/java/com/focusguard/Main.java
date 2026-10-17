package com.focusguard;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Main {

    // --------------------------------------------------
    // TIMER VARIABLES
    // --------------------------------------------------

    private static int selectedDurationSeconds = 25 * 60;
    // Seconds already used before the currently running segment started.
    private static int elapsedBeforeSegment = 0;
    // System.nanoTime() at the start of the running segment (drift-free clock).
    private static long segmentStartNanos = 0;
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
    private static CircularTimerPanel timerRing;

    private static JLabel dashboardFocusValue;
    private static JLabel dashboardSessionsValue;
    private static JLabel dashboardTasksValue;
    private static JLabel dashboardNextTaskLabel;

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

    // Task page inputs
    private static JTextField taskPageInput;
    private static JComboBox<String> taskPriorityBox;
    private static JCheckBox taskDeadlineCheck;
    private static JSpinner taskDeadlineSpinner;
    private static JComboBox<String> taskSortBox;

    private static String taskFilter = "All";
    private static String taskSort = SORT_SMART();

    private static final Map<String, JButton> taskFilterButtons = new LinkedHashMap<>();

    private static final String[] PRIORITIES = {"High", "Medium", "Low"};

    private static String SORT_SMART() { return "Smart (priority + deadline)"; }
    private static final String SORT_DEADLINE = "Earliest deadline";
    private static final String SORT_PRIORITY = "Highest priority";
    private static final String SORT_NEWEST = "Newest first";

    // --------------------------------------------------
    // COLORS
    // --------------------------------------------------

    private static final Color BACKGROUND = new Color(247, 244, 255);
    private static final Color SIDEBAR = new Color(239, 233, 252);
    private static final Color CARD = new Color(255, 255, 255);
    private static final Color PURPLE = new Color(118, 85, 201);
    private static final Color TEXT = new Color(48, 39, 71);
    private static final Color MUTED = new Color(130, 122, 150);
    private static final Color GREEN = new Color(65, 160, 115);
    private static final Color BLUE = new Color(91, 141, 220);
    private static final Color ORANGE = new Color(230, 160, 75);
    private static final Color RED = new Color(225, 95, 115);
    private static final Color PALE_PURPLE = new Color(233, 223, 255);
    private static final Color BORDER = new Color(232, 225, 245);

    private static final TaskDAO taskDAO = new TaskDAO();
    private static final SessionDAO sessionDAO = new SessionDAO();

    // --------------------------------------------------
    // MAIN METHOD
    // --------------------------------------------------

    public static void main(String[] args) {
        FlatLightLaf.setup();

        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("OptionPane.background", CARD);
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("Component.focusColor", PALE_PURPLE);

        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }

    /** Opens the main productivity dashboard after successful login. */
    public static void openDashboard() {
        if (SwingUtilities.isEventDispatchThread()) {
            createGUI();
        } else {
            SwingUtilities.invokeLater(Main::createGUI);
        }
    }

    /**
     * Saves any unfinished session, clears the in-memory user, resets all
     * timer state and returns to the login window.
     */
    public static void logoutToLogin() {
        Runnable logout = () -> {
            if (currentSessionId != -1) {
                int elapsed = getElapsedSeconds();
                try {
                    sessionDAO.finishSession(currentSessionId, "Stopped", elapsed);
                    currentSessionId = -1;
                } catch (SQLException ex) {
                    // Stay signed in so the running session is not lost.
                    showDatabaseError(mainFrame, ex);
                    return;
                }
            }

            JFrame old = mainFrame;
            mainFrame = null;
            resetState();
            UserSession.clear();
            if (old != null) {
                old.dispose();
            }
            new LoginFrame().setVisible(true);
        };

        if (SwingUtilities.isEventDispatchThread()) {
            logout.run();
        } else {
            SwingUtilities.invokeLater(logout);
        }
    }

    /** Resets every piece of per-user / per-session static state. */
    private static void resetState() {
        if (timer != null) {
            timer.stop();
        }
        running = false;
        selectedDurationSeconds = 25 * 60;
        elapsedBeforeSegment = 0;
        segmentStartNanos = 0;
        currentSessionId = -1;
        taskFilter = "All";
        taskSort = SORT_SMART();
        taskFilterButtons.clear();
    }

    private static void createGUI() {
        resetState();

        mainFrame = new JFrame("FocusGuard | Productivity");
        // Closing the window must save an unfinished session first.
        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApplication();
            }
        });
        mainFrame.setMinimumSize(new Dimension(900, 620));
        mainFrame.setSize(1100, 720);
        mainFrame.setLocationRelativeTo(null);
        mainFrame.getContentPane().setBackground(BACKGROUND);
        mainFrame.setLayout(new BorderLayout());

        // Ticks twice a second; the real time comes from System.nanoTime(),
        // so the countdown cannot drift.
        timer = new Timer(500, e -> onTick());

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

        contentPanel.add(new FocusJournalPanel(), "Journal");
        contentPanel.add(new DataExportPanel(), "Export");
        contentPanel.add(new DbInsightsPanel(), "Insights");

        contentPanel.add(new ProfilePanel(), "Profile");

        mainFrame.add(sidebar, BorderLayout.WEST);
        mainFrame.add(contentPanel, BorderLayout.CENTER);

        refreshDashboard();
        updateTimerLabel();

        mainFrame.setVisible(true);
    }

    private static void exitApplication() {
        if (currentSessionId != -1) {
            int elapsed = getElapsedSeconds();
            try {
                sessionDAO.finishSession(currentSessionId, "Stopped", elapsed);
                currentSessionId = -1;
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
        if (timer != null) {
            timer.stop();
        }
        if (mainFrame != null) {
            mainFrame.dispose();
        }
        System.exit(0);
    }

    // --------------------------------------------------
    // TIME HELPERS
    // --------------------------------------------------

    private static int getElapsedSeconds() {
        int elapsed = elapsedBeforeSegment;
        if (running) {
            elapsed += (int) ((System.nanoTime() - segmentStartNanos) / 1_000_000_000L);
        }
        return Math.max(0, Math.min(elapsed, selectedDurationSeconds));
    }

    private static int getRemainingSeconds() {
        return Math.max(0, selectedDurationSeconds - getElapsedSeconds());
    }

    private static void onTick() {
        if (!running) {
            return;
        }
        updateTimerLabel();
        if (getRemainingSeconds() <= 0) {
            completeCurrentSession();
        }
    }

    // --------------------------------------------------
    // SIDEBAR AND NAVIGATION
    // --------------------------------------------------

    private static JButton createSidebarButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setForeground(TEXT);
        button.setBackground(SIDEBAR);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setPreferredSize(new Dimension(190, 46));
        button.setBorder(new EmptyBorder(10, 16, 10, 8));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(PALE_PURPLE);
                button.setForeground(PURPLE);
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(SIDEBAR);
                button.setForeground(TEXT);
            }
        });
        return button;
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
            } else if ("Journal".equals(page)) {
                // The journal panel saves entries locally for the signed-in user.
            } else if ("Export".equals(page)) {
                // Refresh session rows each time the export page is opened.
                for (Component component : contentPanel.getComponents()) {
                    if (component instanceof DataExportPanel
                            && "Export".equals(page)) {
                        ((DataExportPanel) component).refreshSessions();
                    }
                }
            }else if ("Insights".equals(page)) {
                for (Component component : contentPanel.getComponents()) {
                    if (component instanceof DbInsightsPanel) {
                        ((DbInsightsPanel) component).refreshAll();
                    }
                }
            }

            cardLayout.show(contentPanel, page);
        });

        return button;
    }

    private static JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(SIDEBAR);
        sidebar.setPreferredSize(new Dimension(238, 0));
        sidebar.setBorder(new EmptyBorder(28, 18, 22, 18));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        brand.setOpaque(false);
        JLabel mark = new JLabel("FG");
        mark.setOpaque(true);
        mark.setBackground(PURPLE);
        mark.setForeground(Color.WHITE);
        mark.setFont(new Font("SansSerif", Font.BOLD, 15));
        mark.setHorizontalAlignment(SwingConstants.CENTER);
        mark.setPreferredSize(new Dimension(38, 38));
        JLabel logo = new JLabel("  FocusGuard");
        logo.setFont(new Font("SansSerif", Font.BOLD, 21));
        logo.setForeground(TEXT);
        brand.add(mark);
        brand.add(logo);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tagline = new JLabel("YOUR PRODUCTIVITY SPACE");
        tagline.setFont(new Font("SansSerif", Font.BOLD, 10));
        tagline.setForeground(MUTED);
        tagline.setBorder(new EmptyBorder(12, 2, 0, 0));
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel navLabel = new JLabel("WORKSPACE");
        navLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        navLabel.setForeground(MUTED);
        navLabel.setBorder(new EmptyBorder(0, 10, 10, 0));
        navLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        sidebar.add(brand);
        sidebar.add(tagline);
        sidebar.add(Box.createVerticalStrut(38));
        sidebar.add(navLabel);
        sidebar.add(createNavButton("▦   Dashboard", "Dashboard"));
        sidebar.add(Box.createVerticalStrut(7));
        sidebar.add(createNavButton("◷   Focus Timer", "Focus"));
        sidebar.add(Box.createVerticalStrut(7));
        sidebar.add(createNavButton("✓   My Tasks", "Tasks"));
        sidebar.add(Box.createVerticalStrut(7));
        sidebar.add(createNavButton("↺   Focus History", "History"));
        sidebar.add(Box.createVerticalStrut(7));
        sidebar.add(createNavButton("▥   Weekly Analytics", "Analytics"));
        sidebar.add(Box.createVerticalStrut(7));
        sidebar.add(createNavButton("✎   Focus Journal", "Journal"));
        sidebar.add(Box.createVerticalStrut(7));
        sidebar.add(createNavButton("⇩   Data Export", "Export"));
        sidebar.add(Box.createVerticalStrut(7));
        sidebar.add(createNavButton("◈   DB Insights", "Insights"));
        sidebar.add(Box.createVerticalStrut(7));
        sidebar.add(createNavButton("☺   My Profile", "Profile"));
        sidebar.add(Box.createVerticalGlue());

        JPanel note = new JPanel();
        note.setLayout(new BoxLayout(note, BoxLayout.Y_AXIS));
        note.setBackground(new Color(248, 245, 255));
        note.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(14, 12, 14, 12)));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel noteTitle = new JLabel("A gentle reminder");
        noteTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        noteTitle.setForeground(PURPLE);
        JLabel noteText = new JLabel("<html>Progress over perfection.<br>One session at a time.</html>");
        noteText.setFont(new Font("SansSerif", Font.PLAIN, 11));
        noteText.setForeground(MUTED);
        note.add(noteTitle);
        note.add(Box.createVerticalStrut(7));
        note.add(noteText);
        sidebar.add(note);
        return sidebar;
    }

    // --------------------------------------------------
    // DASHBOARD
    // --------------------------------------------------

    private static JPanel createDashboard() {
        JPanel page = new JPanel(new BorderLayout(0, 24));
        page.setBackground(BACKGROUND);
        page.setBorder(new EmptyBorder(30, 34, 28, 34));

        // Header: greeting and date context
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JPanel greetingBlock = new JPanel();
        greetingBlock.setOpaque(false);
        greetingBlock.setLayout(new BoxLayout(greetingBlock, BoxLayout.Y_AXIS));
        JLabel eyebrow = new JLabel("YOUR PERSONAL WORKSPACE");
        eyebrow.setFont(new Font("SansSerif", Font.BOLD, 10));
        eyebrow.setForeground(PURPLE);
        String firstName = UserSession.getFirstName();
        JLabel greeting = new JLabel(firstName.isEmpty()
                ? "Let's make today count."
                : "Let's make today count, " + firstName + ".");
        greeting.setFont(new Font("SansSerif", Font.BOLD, 29));
        greeting.setForeground(TEXT);
        JLabel subtitle = new JLabel("Small steps today. Big progress tomorrow.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(MUTED);
        greetingBlock.add(eyebrow);
        greetingBlock.add(Box.createVerticalStrut(6));
        greetingBlock.add(greeting);
        greetingBlock.add(Box.createVerticalStrut(5));
        greetingBlock.add(subtitle);

        JLabel dateChip = new JLabel(LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEE, MMM d")));
        dateChip.setFont(new Font("SansSerif", Font.BOLD, 12));
        dateChip.setForeground(PURPLE);
        dateChip.setOpaque(true);
        dateChip.setBackground(PALE_PURPLE);
        dateChip.setBorder(new EmptyBorder(10, 13, 10, 13));
        header.add(greetingBlock, BorderLayout.CENTER);
        header.add(dateChip, BorderLayout.NORTH);

        // Three compact metric cards
        JPanel stats = new JPanel(new GridLayout(1, 3, 14, 0));
        stats.setOpaque(false);
        stats.add(createStatCard("FOCUS TIME TODAY", "00:00:00",
                "Time spent focusing", PURPLE, "focus"));
        stats.add(createStatCard("SESSIONS TODAY", "0",
                "Sessions started today", BLUE, "sessions"));
        stats.add(createStatCard("TASKS COMPLETED", "0",
                "Your completed tasks", GREEN, "tasks"));

        // Two-column feature area
        JPanel focusCard = new JPanel(new BorderLayout(0, 18));
        focusCard.setBackground(CARD);
        focusCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(24, 24, 24, 24)));

        JPanel focusCopy = new JPanel();
        focusCopy.setOpaque(false);
        focusCopy.setLayout(new BoxLayout(focusCopy, BoxLayout.Y_AXIS));
        JLabel focusEyebrow = new JLabel("READY WHEN YOU ARE");
        focusEyebrow.setFont(new Font("SansSerif", Font.BOLD, 11));
        focusEyebrow.setForeground(PURPLE);
        JLabel focusTitle = new JLabel("<html>Make space for<br>deep focus.</html>");
        focusTitle.setFont(new Font("SansSerif", Font.BOLD, 25));
        focusTitle.setForeground(TEXT);
        JLabel focusDescription = new JLabel(
                "<html>Choose one task, set a timer, and give it<br>your full attention.</html>");
        focusDescription.setFont(new Font("SansSerif", Font.PLAIN, 13));
        focusDescription.setForeground(MUTED);
        focusCopy.add(focusEyebrow);
        focusCopy.add(Box.createVerticalStrut(13));
        focusCopy.add(focusTitle);
        focusCopy.add(Box.createVerticalStrut(10));
        focusCopy.add(focusDescription);

        JPanel focusActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        focusActions.setOpaque(false);
        JButton focusButton = createPrimaryButton("Start a focus session  →");
        focusButton.addActionListener(e -> cardLayout.show(contentPanel, "Focus"));
        focusActions.add(focusButton);
        focusCard.add(focusCopy, BorderLayout.CENTER);
        focusCard.add(focusActions, BorderLayout.SOUTH);

        // "Next small win" card now shows the most urgent pending task.
        JPanel planCard = new JPanel();
        planCard.setBackground(new Color(245, 240, 255));
        planCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(22, 20, 22, 20)));
        planCard.setLayout(new BoxLayout(planCard, BoxLayout.Y_AXIS));
        JLabel planIcon = new JLabel("✦");
        planIcon.setFont(new Font("SansSerif", Font.BOLD, 26));
        planIcon.setForeground(PURPLE);
        JLabel planTitle = new JLabel("Your next small win");
        planTitle.setFont(new Font("SansSerif", Font.BOLD, 17));
        planTitle.setForeground(TEXT);
        dashboardNextTaskLabel = new JLabel(
                "<html>Keep it simple.<br>Pick one task and begin.</html>");
        dashboardNextTaskLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        dashboardNextTaskLabel.setForeground(MUTED);
        JButton tasksButton = createSecondaryButton("View my tasks  →");
        tasksButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        tasksButton.addActionListener(e -> {
            refreshTasksPage();
            cardLayout.show(contentPanel, "Tasks");
        });
        planCard.add(planIcon);
        planCard.add(Box.createVerticalStrut(14));
        planCard.add(planTitle);
        planCard.add(Box.createVerticalStrut(9));
        planCard.add(dashboardNextTaskLabel);
        planCard.add(Box.createVerticalGlue());
        planCard.add(Box.createVerticalStrut(18));
        planCard.add(tasksButton);

        JPanel featureRow = new JPanel(new GridLayout(1, 2, 16, 0));
        featureRow.setOpaque(false);
        featureRow.add(focusCard);
        featureRow.add(planCard);

        JPanel reminder = new JPanel(new BorderLayout(12, 0));
        reminder.setBackground(CARD);
        reminder.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(15, 18, 15, 18)));
        JLabel reminderIcon = new JLabel("✧");
        reminderIcon.setFont(new Font("SansSerif", Font.BOLD, 23));
        reminderIcon.setForeground(GREEN);
        JPanel reminderCopy = new JPanel();
        reminderCopy.setOpaque(false);
        reminderCopy.setLayout(new BoxLayout(reminderCopy, BoxLayout.Y_AXIS));
        JLabel reminderTitle = new JLabel("A little reminder");
        reminderTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        reminderTitle.setForeground(TEXT);
        JLabel reminderText = new JLabel("Consistency beats intensity. Focus on one thing at a time.");
        reminderText.setFont(new Font("SansSerif", Font.PLAIN, 12));
        reminderText.setForeground(MUTED);
        reminderCopy.add(reminderTitle);
        reminderCopy.add(Box.createVerticalStrut(4));
        reminderCopy.add(reminderText);
        reminder.add(reminderIcon, BorderLayout.WEST);
        reminder.add(reminderCopy, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 17));
        center.setOpaque(false);
        center.add(stats, BorderLayout.NORTH);
        center.add(featureRow, BorderLayout.CENTER);
        center.add(reminder, BorderLayout.SOUTH);

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
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(18, 16, 18, 12)));
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
        JPanel page = new JPanel(new BorderLayout(0, 22));
        page.setBackground(BACKGROUND);
        page.setBorder(new EmptyBorder(30, 30, 28, 30));

        // Page heading
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel headingText = new JPanel();
        headingText.setOpaque(false);
        headingText.setLayout(new BoxLayout(headingText, BoxLayout.Y_AXIS));

        JLabel eyebrow = new JLabel("YOUR PERSONAL FOCUS SPACE");
        eyebrow.setFont(new Font("SansSerif", Font.BOLD, 10));
        eyebrow.setForeground(PURPLE);

        JLabel heading = new JLabel("Focus Timer");
        heading.setFont(new Font("SansSerif", Font.BOLD, 29));
        heading.setForeground(TEXT);

        JLabel subtitle = new JLabel("Make room for deep work, one session at a time.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);

        headingText.add(eyebrow);
        headingText.add(Box.createVerticalStrut(5));
        headingText.add(heading);
        headingText.add(Box.createVerticalStrut(5));
        headingText.add(subtitle);

        JLabel modeBadge = new JLabel("  ◉  DEEP WORK  ");
        modeBadge.setFont(new Font("SansSerif", Font.BOLD, 11));
        modeBadge.setForeground(PURPLE);
        modeBadge.setOpaque(true);
        modeBadge.setBackground(new Color(238, 230, 255));
        modeBadge.setBorder(new EmptyBorder(9, 8, 9, 8));

        header.add(headingText, BorderLayout.CENTER);
        header.add(modeBadge, BorderLayout.EAST);

        // Main body: timer hero on the left, session setup on the right.
        JPanel body = new JPanel(new GridLayout(1, 2, 18, 0));
        body.setOpaque(false);

        JPanel timerCard = new JPanel(new BorderLayout(0, 18));
        timerCard.setBackground(CARD);
        timerCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(232, 225, 246), 1),
                new EmptyBorder(24, 20, 22, 20)
        ));

        JPanel timerTop = new JPanel();
        timerTop.setOpaque(false);
        timerTop.setLayout(new BoxLayout(timerTop, BoxLayout.Y_AXIS));

        JLabel sessionLabel = new JLabel("FOCUS SESSION");
        sessionLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        sessionLabel.setForeground(MUTED);
        sessionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel timerHint = new JLabel("One thing at a time");
        timerHint.setFont(new Font("SansSerif", Font.PLAIN, 13));
        timerHint.setForeground(MUTED);
        timerHint.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerTop.add(sessionLabel);
        timerTop.add(Box.createVerticalStrut(7));
        timerTop.add(timerHint);

        JPanel timerCenter = new JPanel();
        timerCenter.setOpaque(false);
        timerCenter.setLayout(new BoxLayout(timerCenter, BoxLayout.Y_AXIS));

        // Circular timer face with a progress ring.
        timerRing = new CircularTimerPanel();
        timerRing.setLayout(new BoxLayout(timerRing, BoxLayout.Y_AXIS));
        timerRing.setPreferredSize(new Dimension(300, 300));
        timerRing.setMinimumSize(new Dimension(260, 260));
        timerRing.setMaximumSize(new Dimension(300, 300));
        timerRing.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerRing.setBorder(new EmptyBorder(82, 12, 78, 12));

        timerLabel = new JLabel(formatClock(selectedDurationSeconds));
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 43));
        timerLabel.setForeground(TEXT);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerStatusLabel = new JLabel("Ready when you are");
        timerStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        timerStatusLabel.setForeground(MUTED);
        timerStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerRing.add(Box.createVerticalGlue());
        timerRing.add(timerLabel);
        timerRing.add(Box.createVerticalStrut(9));
        timerRing.add(timerStatusLabel);
        timerRing.add(Box.createVerticalGlue());

        JLabel encouragement = new JLabel("Small steps create meaningful progress.");
        encouragement.setFont(new Font("SansSerif", Font.ITALIC, 12));
        encouragement.setForeground(MUTED);
        encouragement.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerCenter.add(Box.createVerticalGlue());
        timerCenter.add(timerRing);
        timerCenter.add(Box.createVerticalStrut(18));
        timerCenter.add(encouragement);
        timerCenter.add(Box.createVerticalGlue());

        timerCard.add(timerTop, BorderLayout.NORTH);
        timerCard.add(timerCenter, BorderLayout.CENTER);

        // Session setup card
        JPanel setupCard = new JPanel();
        setupCard.setBackground(CARD);
        setupCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(232, 225, 246), 1),
                new EmptyBorder(22, 20, 20, 20)
        ));
        setupCard.setLayout(new BoxLayout(setupCard, BoxLayout.Y_AXIS));

        JLabel setupTitle = new JLabel("Plan your session");
        setupTitle.setFont(new Font("SansSerif", Font.BOLD, 19));
        setupTitle.setForeground(TEXT);
        setupTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel setupSubtitle = new JLabel("Choose how long you want to focus.");
        setupSubtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        setupSubtitle.setForeground(MUTED);
        setupSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JSeparator separator = new JSeparator();
        separator.setForeground(new Color(235, 229, 246));
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        hoursSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 23, 1));
        minutesSpinner = new JSpinner(new SpinnerNumberModel(25, 0, 59, 1));
        secondsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));

        hoursSpinner.setPreferredSize(new Dimension(68, 34));
        minutesSpinner.setPreferredSize(new Dimension(68, 34));
        secondsSpinner.setPreferredSize(new Dimension(68, 34));

        JPanel durationPanel = new JPanel(new GridLayout(1, 3, 8, 0));
        durationPanel.setOpaque(false);
        durationPanel.add(createDurationInput("Hours", hoursSpinner));
        durationPanel.add(createDurationInput("Minutes", minutesSpinner));
        durationPanel.add(createDurationInput("Seconds", secondsSpinner));
        durationPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        durationPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        applyDurationButton = createSecondaryButton("Apply custom duration");
        applyDurationButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        applyDurationButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        applyDurationButton.addActionListener(e -> applyCustomDuration(true));

        startButton = createPrimaryButton("▶   Start focus");
        startButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        startButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        pauseButton = createSecondaryButton("Pause session");
        pauseButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        pauseButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        pauseButton.setEnabled(false);

        JButton resetButton = createSecondaryButton("Stop and reset");
        resetButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        startButton.addActionListener(e -> startOrResumeSession());
        pauseButton.addActionListener(e -> {
            if (running) {
                pauseCurrentSession();
            } else if (currentSessionId != -1) {
                startOrResumeSession();
            }
        });
        resetButton.addActionListener(e -> stopAndResetSession());

        JPanel noteCard = new JPanel(new BorderLayout(10, 0));
        noteCard.setBackground(new Color(246, 242, 255));
        noteCard.setBorder(new EmptyBorder(12, 12, 12, 12));
        JLabel noteIcon = new JLabel("✦");
        noteIcon.setFont(new Font("SansSerif", Font.BOLD, 19));
        noteIcon.setForeground(PURPLE);
        JLabel noteText = new JLabel("<html><b>Focus tip</b><br>Silence notifications and keep just one task in mind.</html>");
        noteText.setFont(new Font("SansSerif", Font.PLAIN, 11));
        noteText.setForeground(TEXT);
        noteCard.add(noteIcon, BorderLayout.WEST);
        noteCard.add(noteText, BorderLayout.CENTER);
        noteCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        noteCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        setupCard.add(setupTitle);
        setupCard.add(Box.createVerticalStrut(6));
        setupCard.add(setupSubtitle);
        setupCard.add(Box.createVerticalStrut(18));
        setupCard.add(separator);
        setupCard.add(Box.createVerticalStrut(18));
        setupCard.add(durationPanel);
        setupCard.add(Box.createVerticalStrut(12));
        setupCard.add(applyDurationButton);
        setupCard.add(Box.createVerticalStrut(18));
        setupCard.add(startButton);
        setupCard.add(Box.createVerticalStrut(9));
        setupCard.add(pauseButton);
        setupCard.add(Box.createVerticalStrut(9));
        setupCard.add(resetButton);
        setupCard.add(Box.createVerticalGlue());
        setupCard.add(Box.createVerticalStrut(16));
        setupCard.add(noteCard);

        body.add(timerCard);
        body.add(setupCard);

        page.add(header, BorderLayout.NORTH);
        page.add(body, BorderLayout.CENTER);
        return page;
    }

    private static JPanel createDurationInput(String label, JSpinner spinner) {
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
            elapsedBeforeSegment = 0;

            updateTimerLabel();

            timerStatusLabel.setText(
                    "Duration applied: "
                            + formatClock(selectedDurationSeconds)
            );

            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
            pauseButton.setText("Pause session");
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
    // START OR RESUME SESSION
    // --------------------------------------------------

    private static void startOrResumeSession() {
        if (running) {
            return;
        }

        // For every NEW session, read the latest input values.
        if (currentSessionId == -1) {
            if (!applyCustomDuration(true)) {
                return;
            }
        }

        if (getRemainingSeconds() <= 0) {
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

                elapsedBeforeSegment = 0;

            } else {
                sessionDAO.updateSessionProgress(
                        currentSessionId,
                        "Active",
                        elapsedBeforeSegment
                );
            }

            segmentStartNanos = System.nanoTime();
            running = true;
            timer.start();

            startButton.setEnabled(false);
            pauseButton.setEnabled(true);
            pauseButton.setText("Pause session");
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
        if (!running) {
            return;
        }

        // Freeze elapsed time first, then stop the clock.
        elapsedBeforeSegment = getElapsedSeconds();
        running = false;
        timer.stop();
        updateTimerLabel();

        startButton.setEnabled(false);
        pauseButton.setEnabled(true);
        pauseButton.setText("Resume session");
        applyDurationButton.setEnabled(false);

        timerStatusLabel.setText("Session paused");

        try {
            if (currentSessionId != -1) {
                sessionDAO.updateSessionProgress(
                        currentSessionId,
                        "Paused",
                        elapsedBeforeSegment
                );
            }

            refreshDashboard();
            refreshHistoryPage();

        } catch (SQLException ex) {
            showDatabaseError(mainFrame, ex);
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
        elapsedBeforeSegment = selectedDurationSeconds;
        updateTimerLabel();

        boolean saved = finishCurrentSession("Completed");

        startButton.setEnabled(true);
        pauseButton.setEnabled(false);
        pauseButton.setText("Pause session");

        if (saved) {
            elapsedBeforeSegment = 0;

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

        elapsedBeforeSegment = getElapsedSeconds();
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
        elapsedBeforeSegment = 0;

        updateTimerLabel();

        timerStatusLabel.setText("Ready when you are");

        startButton.setEnabled(true);
        pauseButton.setEnabled(false);
        pauseButton.setText("Pause session");
        applyDurationButton.setEnabled(true);

        refreshDashboard();
        refreshHistoryPage();
        analyticsPanel.refreshAnalytics();
    }

    // --------------------------------------------------
    // TASK HELPERS (priority + deadline)
    // --------------------------------------------------

    private static boolean isDone(TaskDAO.Task task) {
        return "Completed".equalsIgnoreCase(task.status);
    }

    private static String normalizePriority(String priority) {
        if (priority != null) {
            for (String p : PRIORITIES) {
                if (p.equalsIgnoreCase(priority.trim())) {
                    return p;
                }
            }
        }
        return "Medium";
    }

    private static int priorityRank(String priority) {
        String p = normalizePriority(priority);
        if ("High".equals(p)) {
            return 0;
        }
        return "Medium".equals(p) ? 1 : 2;
    }

    private static Color priorityColor(String priority) {
        String p = normalizePriority(priority);
        if ("High".equals(p)) {
            return RED;
        }
        return "Medium".equals(p) ? ORANGE : BLUE;
    }

    private static LocalDate dueOf(TaskDAO.Task task) {
        return task.dueDate == null ? null : task.dueDate.toLocalDate();
    }

    private static boolean isOverdue(TaskDAO.Task task) {
        LocalDate due = dueOf(task);
        return due != null && !isDone(task) && due.isBefore(LocalDate.now());
    }

    private static String dueText(TaskDAO.Task task) {
        LocalDate due = dueOf(task);
        if (due == null) {
            return "No deadline";
        }

        String formatted = due.format(DateTimeFormatter.ofPattern("d MMM yyyy"));
        if (isDone(task)) {
            return "Due " + formatted;
        }

        long days = ChronoUnit.DAYS.between(LocalDate.now(), due);
        if (days < 0) {
            return "Overdue · " + formatted;
        }
        if (days == 0) {
            return "Due today";
        }
        if (days == 1) {
            return "Due tomorrow";
        }
        return "Due " + formatted;
    }

    private static Color dueColor(TaskDAO.Task task) {
        LocalDate due = dueOf(task);
        if (due == null || isDone(task)) {
            return MUTED;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), due);
        if (days < 0) {
            return RED;
        }
        return days <= 2 ? ORANGE : PURPLE;
    }

    private static Color tint(Color color, double amount) {
        int r = (int) (color.getRed() + (255 - color.getRed()) * amount);
        int g = (int) (color.getGreen() + (255 - color.getGreen()) * amount);
        int b = (int) (color.getBlue() + (255 - color.getBlue()) * amount);
        return new Color(r, g, b);
    }

    private static Color darken(Color color, double factor) {
        return new Color(
                (int) (color.getRed() * factor),
                (int) (color.getGreen() * factor),
                (int) (color.getBlue() * factor)
        );
    }

    private static JLabel createBadge(String text, Color base) {
        JLabel badge = new JLabel(text);
        badge.setFont(new Font("SansSerif", Font.BOLD, 10));
        badge.setOpaque(true);
        badge.setForeground(darken(base, 0.80));
        badge.setBackground(tint(base, 0.86));
        badge.setBorder(new EmptyBorder(5, 8, 5, 8));
        return badge;
    }

    private static java.sql.Date toSqlDate(Object spinnerValue) {
        if (!(spinnerValue instanceof java.util.Date)) {
            return null;
        }
        LocalDate local = ((java.util.Date) spinnerValue).toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
        return java.sql.Date.valueOf(local);
    }

    private static JSpinner createDateSpinner(java.util.Date initial) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(
                initial, null, null, Calendar.DAY_OF_MONTH));
        spinner.setEditor(new JSpinner.DateEditor(spinner, "dd MMM yyyy"));
        spinner.setPreferredSize(new Dimension(130, 34));
        return spinner;
    }

    private static void commitQuietly(JSpinner spinner) {
        try {
            spinner.commitEdit();
        } catch (ParseException ignored) {
            // The previous valid value is kept.
        }
    }

    /** Orders tasks according to the selected sort option. */
    private static void sortTasks(List<TaskDAO.Task> tasks) {
        Comparator<TaskDAO.Task> byDone =
                Comparator.comparing((TaskDAO.Task t) -> isDone(t));
        Comparator<TaskDAO.Task> byOverdue =
                Comparator.comparing((TaskDAO.Task t) -> !isOverdue(t));
        Comparator<TaskDAO.Task> byPriority =
                Comparator.comparingInt((TaskDAO.Task t) -> priorityRank(t.priority));
        Comparator<TaskDAO.Task> byDue = Comparator.comparing(
                (TaskDAO.Task t) -> dueOf(t),
                Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()));
        Comparator<TaskDAO.Task> byNewest =
                (a, b) -> Integer.compare(b.id, a.id);

        Comparator<TaskDAO.Task> chosen;
        if (SORT_DEADLINE.equals(taskSort)) {
            chosen = byDone.thenComparing(byDue).thenComparing(byPriority).thenComparing(byNewest);
        } else if (SORT_PRIORITY.equals(taskSort)) {
            chosen = byDone.thenComparing(byPriority).thenComparing(byDue).thenComparing(byNewest);
        } else if (SORT_NEWEST.equals(taskSort)) {
            chosen = byNewest;
        } else {
            chosen = byDone.thenComparing(byOverdue).thenComparing(byPriority)
                    .thenComparing(byDue).thenComparing(byNewest);
        }
        tasks.sort(chosen);
    }

    // --------------------------------------------------
    // TASKS PAGE
    // --------------------------------------------------

    private static JPanel createTasksPage() {
        JPanel page = new JPanel(new BorderLayout(0, 24));
        page.setBackground(BACKGROUND);
        page.setBorder(new EmptyBorder(32, 30, 25, 30));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel eyebrow = new JLabel("YOUR PERSONAL WORKSPACE");
        eyebrow.setFont(new Font("SansSerif", Font.BOLD, 10));
        eyebrow.setForeground(PURPLE);

        JLabel title = new JLabel("My Tasks");
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel(
                "Organize your priorities and celebrate every small win."
        );
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);

        header.add(eyebrow);
        header.add(Box.createVerticalStrut(7));
        header.add(title);
        header.add(Box.createVerticalStrut(5));
        header.add(subtitle);

        // ---- Create-task card ----
        JPanel addCard = new JPanel(new BorderLayout(12, 0));
        addCard.setBackground(CARD);
        addCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(18, 18, 18, 18)
        ));

        taskPageInput = new JTextField();
        taskPageInput.putClientProperty(
                "JTextField.placeholderText",
                "What do you want to accomplish?"
        );
        taskPageInput.setFont(new Font("SansSerif", Font.PLAIN, 14));
        taskPageInput.setPreferredSize(new Dimension(100, 42));

        JButton addButton = createPrimaryButton("+  Add Task");
        addButton.addActionListener(e -> addTaskFromPage());
        taskPageInput.addActionListener(e -> addTaskFromPage());

        JPanel addContent = new JPanel();
        addContent.setOpaque(false);
        addContent.setLayout(new BoxLayout(addContent, BoxLayout.Y_AXIS));

        JLabel addTitle = new JLabel("Create a new task");
        addTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        addTitle.setForeground(TEXT);
        addTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel addSubtitle = new JLabel("What would you like to work on?");
        addSubtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        addSubtitle.setForeground(MUTED);
        addSubtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setOpaque(false);
        inputRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        inputRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        inputRow.add(taskPageInput, BorderLayout.CENTER);
        inputRow.add(addButton, BorderLayout.EAST);

        // Priority + deadline options
        taskPriorityBox = new JComboBox<>(PRIORITIES);
        taskPriorityBox.setSelectedItem("Medium");
        taskPriorityBox.setPreferredSize(new Dimension(110, 34));

        taskDeadlineCheck = new JCheckBox("Set deadline");
        taskDeadlineCheck.setOpaque(false);
        taskDeadlineCheck.setFocusable(false);
        taskDeadlineCheck.setForeground(TEXT);

        taskDeadlineSpinner = createDateSpinner(new java.util.Date());
        taskDeadlineSpinner.setEnabled(false);
        taskDeadlineCheck.addActionListener(e ->
                taskDeadlineSpinner.setEnabled(taskDeadlineCheck.isSelected()));

        JLabel priorityCaption = new JLabel("Priority");
        priorityCaption.setFont(new Font("SansSerif", Font.BOLD, 12));
        priorityCaption.setForeground(MUTED);

        JPanel optionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        optionsRow.setOpaque(false);
        optionsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        optionsRow.add(priorityCaption);
        optionsRow.add(taskPriorityBox);
        optionsRow.add(Box.createHorizontalStrut(10));
        optionsRow.add(taskDeadlineCheck);
        optionsRow.add(taskDeadlineSpinner);

        addContent.add(addTitle);
        addContent.add(Box.createVerticalStrut(5));
        addContent.add(addSubtitle);
        addContent.add(Box.createVerticalStrut(12));
        addContent.add(inputRow);
        addContent.add(Box.createVerticalStrut(10));
        addContent.add(optionsRow);

        addCard.add(addContent, BorderLayout.CENTER);

        // ---- Filters and sorting ----
        taskFilterButtons.clear();

        JPanel filterPanel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 8, 0)
        );
        filterPanel.setOpaque(false);

        for (String filter : new String[]{"All", "Pending", "Completed", "Overdue"}) {
            JButton filterButton = new JButton(filter);
            filterButton.setFont(new Font("SansSerif", Font.BOLD, 12));
            filterButton.setPreferredSize(new Dimension(105, 38));
            filterButton.setFocusPainted(false);
            filterButton.setBorderPainted(false);
            filterButton.setOpaque(true);
            filterButton.setCursor(
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            );

            filterButton.addActionListener(e -> {
                taskFilter = filter;
                refreshTasksPage();
            });

            taskFilterButtons.put(filter, filterButton);
            filterPanel.add(filterButton);
        }

        taskSortBox = new JComboBox<>(new String[]{
                SORT_SMART(), SORT_DEADLINE, SORT_PRIORITY, SORT_NEWEST
        });
        taskSortBox.setSelectedItem(taskSort);
        taskSortBox.setPreferredSize(new Dimension(210, 36));
        taskSortBox.addActionListener(e -> {
            Object selected = taskSortBox.getSelectedItem();
            if (selected != null && !selected.equals(taskSort)) {
                taskSort = selected.toString();
                refreshTasksPage();
            }
        });

        JLabel sortCaption = new JLabel("Sort by");
        sortCaption.setFont(new Font("SansSerif", Font.BOLD, 12));
        sortCaption.setForeground(MUTED);

        JPanel sortPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        sortPanel.setOpaque(false);
        sortPanel.add(sortCaption);
        sortPanel.add(taskSortBox);

        JPanel filterRow = new JPanel(new BorderLayout(10, 0));
        filterRow.setOpaque(false);
        filterRow.add(filterPanel, BorderLayout.WEST);
        filterRow.add(sortPanel, BorderLayout.EAST);

        taskSummaryLabel = new JLabel("YOUR TASKS");
        taskSummaryLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        taskSummaryLabel.setForeground(TEXT);

        JPanel listHeader = new JPanel(new BorderLayout(0, 12));
        listHeader.setOpaque(false);
        listHeader.add(taskSummaryLabel, BorderLayout.NORTH);
        listHeader.add(filterRow, BorderLayout.SOUTH);

        taskCardsPanel = new JPanel();
        taskCardsPanel.setOpaque(false);
        taskCardsPanel.setLayout(
                new BoxLayout(taskCardsPanel, BoxLayout.Y_AXIS)
        );

        JPanel scrollWrap = new JPanel(new BorderLayout());
        scrollWrap.setOpaque(false);
        scrollWrap.add(taskCardsPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(scrollWrap);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel taskListArea = new JPanel(new BorderLayout(0, 14));
        taskListArea.setOpaque(false);
        taskListArea.add(listHeader, BorderLayout.NORTH);
        taskListArea.add(scrollPane, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 20));
        center.setOpaque(false);
        center.add(addCard, BorderLayout.NORTH);
        center.add(taskListArea, BorderLayout.CENTER);

        page.add(header, BorderLayout.NORTH);
        page.add(center, BorderLayout.CENTER);

        refreshTaskFilterStyles();

        return page;
    }

    private static void refreshTaskFilterStyles() {
        for (Map.Entry<String, JButton> entry : taskFilterButtons.entrySet()) {
            boolean selected = entry.getKey().equals(taskFilter);
            JButton button = entry.getValue();

            button.setBackground(selected ? PURPLE : CARD);
            button.setForeground(selected ? Color.WHITE : TEXT);
        }
    }

    private static void addTaskFromPage() {
        String title = taskPageInput.getText().trim();

        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "Please enter a task.");
            return;
        }

        String priority = normalizePriority((String) taskPriorityBox.getSelectedItem());

        java.sql.Date dueDate = null;
        if (taskDeadlineCheck.isSelected()) {
            commitQuietly(taskDeadlineSpinner);
            dueDate = toSqlDate(taskDeadlineSpinner.getValue());
        }

        try {
            taskDAO.addTask(title, priority, dueDate);

            // Reset the form for the next task.
            taskPageInput.setText("");
            taskPriorityBox.setSelectedItem("Medium");
            taskDeadlineCheck.setSelected(false);
            taskDeadlineSpinner.setValue(new java.util.Date());
            taskDeadlineSpinner.setEnabled(false);

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
            sortTasks(tasks);

            int total = tasks.size();
            int completed = 0;
            int pending = 0;
            int overdue = 0;
            int shown = 0;

            for (TaskDAO.Task task : tasks) {
                boolean isCompleted = isDone(task);
                boolean isLate = isOverdue(task);

                if (isCompleted) {
                    completed++;
                } else {
                    pending++;
                }
                if (isLate) {
                    overdue++;
                }

                if ("Pending".equals(taskFilter) && isCompleted) {
                    continue;
                }

                if ("Completed".equals(taskFilter) && !isCompleted) {
                    continue;
                }

                if ("Overdue".equals(taskFilter) && !isLate) {
                    continue;
                }

                taskCardsPanel.add(createTaskCard(task));
                taskCardsPanel.add(Box.createVerticalStrut(12));
                shown++;
            }

            taskSummaryLabel.setText(
                    total + " tasks     •     "
                            + pending + " pending     •     "
                            + completed + " completed"
                            + (overdue > 0 ? "     •     " + overdue + " overdue" : "")
            );

            if (shown == 0) {
                taskCardsPanel.add(createEmptyTasksCard());
            }

        } catch (SQLException ex) {
            showDatabaseError(mainFrame, ex);
        }

        refreshTaskFilterStyles();

        taskCardsPanel.revalidate();
        taskCardsPanel.repaint();
    }

    private static JPanel createEmptyTasksCard() {
        JPanel emptyCard = new JPanel();
        emptyCard.setOpaque(true);
        emptyCard.setBackground(CARD);
        emptyCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(30, 18, 30, 18)
        ));
        emptyCard.setLayout(new BoxLayout(emptyCard, BoxLayout.Y_AXIS));
        emptyCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        emptyCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JLabel emptyIcon = new JLabel(
                "Completed".equals(taskFilter) ? "✦" : "✓"
        );
        emptyIcon.setFont(new Font("SansSerif", Font.BOLD, 28));
        emptyIcon.setForeground(PURPLE);
        emptyIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        String emptyTitleText;
        String emptySubtitleText;

        if ("Completed".equals(taskFilter)) {
            emptyTitleText = "No completed tasks yet";
            emptySubtitleText = "Finish a task and your progress will appear here.";
        } else if ("Pending".equals(taskFilter)) {
            emptyTitleText = "You're all caught up!";
            emptySubtitleText = "There are no pending tasks right now.";
        } else if ("Overdue".equals(taskFilter)) {
            emptyTitleText = "Nothing is overdue";
            emptySubtitleText = "Great job staying on top of your deadlines.";
        } else {
            emptyTitleText = "Your task list is ready";
            emptySubtitleText = "Add your first task above to get started.";
        }

        JLabel emptyTitle = new JLabel(emptyTitleText);
        emptyTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        emptyTitle.setForeground(TEXT);
        emptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel emptySubtitle = new JLabel(emptySubtitleText);
        emptySubtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        emptySubtitle.setForeground(MUTED);
        emptySubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        emptyCard.add(Box.createVerticalGlue());
        emptyCard.add(emptyIcon);
        emptyCard.add(Box.createVerticalStrut(12));
        emptyCard.add(emptyTitle);
        emptyCard.add(Box.createVerticalStrut(7));
        emptyCard.add(emptySubtitle);
        emptyCard.add(Box.createVerticalGlue());
        return emptyCard;
    }

    private static JPanel createTaskCard(TaskDAO.Task task) {
        boolean completed = isDone(task);
        boolean late = isOverdue(task);

        JPanel card = new JPanel(new BorderLayout(13, 0));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(late ? tint(RED, 0.55) : BORDER),
                new EmptyBorder(15, 16, 15, 16)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
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

        String safeTitle = escapeHtml(task.title);
        JLabel taskTitle = new JLabel(
                "<html><div style='width:300px;'>"
                        + (completed ? "<s>" + safeTitle + "</s>" : safeTitle)
                        + "</div></html>"
        );
        taskTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        taskTitle.setForeground(completed ? MUTED : TEXT);
        taskTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel statusBadge = createBadge(
                completed ? "✓ COMPLETED" : "◷ PENDING",
                completed ? GREEN : ORANGE
        );

        String priority = normalizePriority(task.priority);
        JLabel priorityBadge = createBadge(
                "⚑ " + priority.toUpperCase() + " PRIORITY",
                completed ? MUTED : priorityColor(priority)
        );

        JLabel dueBadge = createBadge(
                "▣ " + dueText(task).toUpperCase(),
                dueColor(task)
        );

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeRow.add(statusBadge);
        badgeRow.add(priorityBadge);
        badgeRow.add(dueBadge);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(taskTitle);
        textPanel.add(Box.createVerticalStrut(8));
        textPanel.add(badgeRow);

        JButton editButton = createSecondaryButton("Edit");
        editButton.setToolTipText("Edit title, priority and deadline");
        editButton.addActionListener(e -> showEditTaskDialog(task));

        JButton deleteButton = createSecondaryButton("Delete");
        deleteButton.setForeground(RED);
        deleteButton.setToolTipText("Delete this task");

        deleteButton.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(
                    mainFrame,
                    "Delete \"" + task.title + "\"?",
                    "Delete Task",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
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

        JPanel actions = new JPanel(new GridLayout(2, 1, 0, 6));
        actions.setOpaque(false);
        actions.add(editButton);
        actions.add(deleteButton);

        JPanel actionWrap = new JPanel(new GridBagLayout());
        actionWrap.setOpaque(false);
        actionWrap.add(actions);

        card.add(checkBox, BorderLayout.WEST);
        card.add(textPanel, BorderLayout.CENTER);
        card.add(actionWrap, BorderLayout.EAST);

        return card;
    }

    /** Dialog for changing a task's title, priority and deadline. */
    private static void showEditTaskDialog(TaskDAO.Task task) {
        JTextField titleField = new JTextField(task.title, 26);

        JComboBox<String> priorityBox = new JComboBox<>(PRIORITIES);
        priorityBox.setSelectedItem(normalizePriority(task.priority));

        java.util.Date initialDate = task.dueDate != null
                ? new java.util.Date(task.dueDate.getTime())
                : new java.util.Date();
        JSpinner dateSpinner = createDateSpinner(initialDate);
        JCheckBox deadlineCheck = new JCheckBox("Set deadline", task.dueDate != null);
        dateSpinner.setEnabled(task.dueDate != null);
        deadlineCheck.addActionListener(e ->
                dateSpinner.setEnabled(deadlineCheck.isSelected()));

        JPanel deadlineRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        deadlineRow.add(deadlineCheck);
        deadlineRow.add(dateSpinner);

        JPanel form = new JPanel(new GridLayout(0, 2, 10, 12));
        form.add(new JLabel("Title"));
        form.add(titleField);
        form.add(new JLabel("Priority"));
        form.add(priorityBox);
        form.add(new JLabel("Deadline"));
        form.add(deadlineRow);

        int choice = JOptionPane.showConfirmDialog(
                mainFrame,
                form,
                "Edit Task",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        String newTitle = titleField.getText().trim();
        if (newTitle.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "A task needs a title.");
            return;
        }

        String newPriority = normalizePriority((String) priorityBox.getSelectedItem());

        java.sql.Date newDue = null;
        if (deadlineCheck.isSelected()) {
            commitQuietly(dateSpinner);
            newDue = toSqlDate(dateSpinner.getValue());
        }

        try {
            if (!newTitle.equals(task.title)) {
                taskDAO.updateTaskTitle(task.id, newTitle);
            }
            if (!newPriority.equals(normalizePriority(task.priority))) {
                taskDAO.updateTaskPriority(task.id, newPriority);
            }
            LocalDate oldDueDate = dueOf(task);
            LocalDate newDueDate = newDue == null ? null : newDue.toLocalDate();
            if (!Objects.equals(oldDueDate, newDueDate)) {
                taskDAO.updateTaskDeadline(task.id, newDue);
            }

            refreshTasksPage();
            refreshDashboard();
        } catch (SQLException ex) {
            showDatabaseError(mainFrame, ex);
        }
    }

    // --------------------------------------------------
    // HISTORY PAGE
    // --------------------------------------------------

    private static JPanel createHistoryPage() {
        JPanel page = new JPanel(new BorderLayout(0, 24));
        page.setBackground(BACKGROUND);
        page.setBorder(new EmptyBorder(32, 30, 25, 30));

        // Header
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel eyebrow = new JLabel("YOUR PRODUCTIVITY JOURNEY");
        eyebrow.setFont(new Font("SansSerif", Font.BOLD, 10));
        eyebrow.setForeground(PURPLE);

        JLabel title = new JLabel("Focus History");
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel(
                "Look back at your effort. Every focused minute counts."
        );
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);

        header.add(eyebrow);
        header.add(Box.createVerticalStrut(7));
        header.add(title);
        header.add(Box.createVerticalStrut(5));
        header.add(subtitle);

        // Refresh button
        JButton refreshButton = createPrimaryButton("↻  Refresh");
        refreshButton.addActionListener(e -> refreshHistoryPage());

        JPanel headerRow = new JPanel(new BorderLayout(10, 0));
        headerRow.setOpaque(false);
        headerRow.add(header, BorderLayout.CENTER);
        headerRow.add(refreshButton, BorderLayout.EAST);

        // Statistics
        JPanel stats = new JPanel(new GridLayout(1, 3, 12, 0));
        stats.setOpaque(false);
        stats.setPreferredSize(new Dimension(100, 120));

        historyFocusValue = new JLabel("00:00:00");
        historySessionsValue = new JLabel("0");
        historyCompletedValue = new JLabel("0");

        stats.add(createHistoryStatCard("TOTAL FOCUS TIME", historyFocusValue, PURPLE));
        stats.add(createHistoryStatCard("SESSIONS TODAY", historySessionsValue, BLUE));
        stats.add(createHistoryStatCard("COMPLETED SESSIONS", historyCompletedValue, GREEN));

        // Activity heading
        JLabel sectionTitle = new JLabel("YOUR SESSIONS");
        sectionTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        sectionTitle.setForeground(TEXT);

        JLabel sectionSubtitle = new JLabel(
                "A record of the time you've dedicated to focusing."
        );
        sectionSubtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sectionSubtitle.setForeground(MUTED);

        JPanel activityHeader = new JPanel();
        activityHeader.setOpaque(false);
        activityHeader.setLayout(new BoxLayout(activityHeader, BoxLayout.Y_AXIS));
        activityHeader.add(sectionTitle);
        activityHeader.add(Box.createVerticalStrut(5));
        activityHeader.add(sectionSubtitle);

        // Session cards
        historyCardsPanel = new JPanel();
        historyCardsPanel.setOpaque(false);
        historyCardsPanel.setLayout(
                new BoxLayout(historyCardsPanel, BoxLayout.Y_AXIS)
        );

        JPanel scrollWrap = new JPanel(new BorderLayout());
        scrollWrap.setOpaque(false);
        scrollWrap.add(historyCardsPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(scrollWrap);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel activityPanel = new JPanel(new BorderLayout(0, 14));
        activityPanel.setOpaque(false);
        activityPanel.add(activityHeader, BorderLayout.NORTH);
        activityPanel.add(scrollPane, BorderLayout.CENTER);

        // Main content
        JPanel center = new JPanel(new BorderLayout(0, 24));
        center.setOpaque(false);
        center.add(stats, BorderLayout.NORTH);
        center.add(activityPanel, BorderLayout.CENTER);

        page.add(headerRow, BorderLayout.NORTH);
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
            boolean currentSessionFound = false;

            for (SessionDAO.FocusSession session : sessions) {
                boolean isCurrent = currentSessionId != -1
                        && session.id == currentSessionId;
                if (isCurrent) {
                    currentSessionFound = true;
                }

                int seconds = isCurrent
                        ? getElapsedSeconds() : session.elapsedSeconds;

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

            if (currentSessionId != -1 && !currentSessionFound) {
                totalSeconds += getElapsedSeconds();
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
        boolean completed = "Completed".equalsIgnoreCase(statusText);
        boolean paused = "Paused".equalsIgnoreCase(statusText);
        boolean active = "Active".equalsIgnoreCase(statusText);

        Color statusColor = completed
                ? GREEN
                : paused ? ORANGE
                : active ? BLUE : MUTED;

        JPanel card = new JPanel(new BorderLayout(14, 0));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(16, 17, 16, 17)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 105));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Session icon
        JLabel icon = new JLabel(completed ? "✓" : "◷");
        icon.setFont(new Font("SansSerif", Font.BOLD, 23));
        icon.setForeground(statusColor);
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        icon.setPreferredSize(new Dimension(32, 40));

        // Start time
        String started = session.startTime == null
                ? "Unknown"
                : session.startTime.toLocalDateTime()
                .toLocalTime().withNano(0).toString();

        // End time
        String ended;

        if (session.endTime != null) {
            ended = session.endTime.toLocalDateTime()
                    .toLocalTime().withNano(0).toString();
        } else if (paused) {
            ended = "Paused";
        } else if (active) {
            ended = "In progress";
        } else {
            ended = "Not recorded";
        }

        // Session title (plain-text labels, so no HTML escaping here)
        String mode = session.mode == null || session.mode.isEmpty()
                ? "Focus" : session.mode;

        JLabel sessionTitle = new JLabel(mode + " Session");
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
        textPanel.add(Box.createVerticalStrut(9));
        textPanel.add(details);

        // Duration
        JLabel duration = new JLabel(formatDuration(seconds));
        duration.setFont(new Font("SansSerif", Font.BOLD, 16));
        duration.setForeground(PURPLE);
        duration.setAlignmentX(Component.RIGHT_ALIGNMENT);

        // Status label
        String displayStatus = statusText == null ? "Unknown" : statusText;

        JLabel status = new JLabel(displayStatus.toUpperCase());
        status.setFont(new Font("SansSerif", Font.BOLD, 10));
        status.setForeground(statusColor);
        status.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.add(duration);
        rightPanel.add(Box.createVerticalStrut(7));
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
                if (currentSessionId != -1 && session.id == currentSessionId) {
                    currentSessionFound = true;
                    totalSeconds += getElapsedSeconds();
                } else {
                    totalSeconds += session.elapsedSeconds;
                }
            }

            if (currentSessionId != -1 && !currentSessionFound) {
                totalSeconds += getElapsedSeconds();
                sessionCount++;
            }

            dashboardFocusValue.setText(formatDuration(totalSeconds));
            dashboardSessionsValue.setText(String.valueOf(sessionCount));

            List<TaskDAO.Task> tasks = taskDAO.getAllTasks();
            int completed = 0;
            int overdue = 0;

            for (TaskDAO.Task task : tasks) {
                if (isDone(task)) {
                    completed++;
                }
                if (isOverdue(task)) {
                    overdue++;
                }
            }

            dashboardTasksValue.setText(String.valueOf(completed));
            updateNextTaskCard(tasks, overdue);

        } catch (SQLException ex) {
            showDatabaseError(mainFrame, ex);
        }
    }

    /** Shows the most urgent pending task (overdue, then priority, then deadline). */
    private static void updateNextTaskCard(List<TaskDAO.Task> tasks, int overdueCount) {
        if (dashboardNextTaskLabel == null) {
            return;
        }

        String savedSort = taskSort;
        taskSort = SORT_SMART();
        sortTasks(tasks);
        taskSort = savedSort;

        TaskDAO.Task next = null;
        for (TaskDAO.Task task : tasks) {
            if (!isDone(task)) {
                next = task;
                break;
            }
        }

        if (next == null) {
            dashboardNextTaskLabel.setText(
                    "<html>You're all caught up.<br>Add a task to plan your next win.</html>");
            return;
        }

        String shownTitle = next.title == null ? "" : next.title;
        if (shownTitle.length() > 38) {
            shownTitle = shownTitle.substring(0, 37) + "…";
        }

        StringBuilder text = new StringBuilder("<html><b>")
                .append(escapeHtml(shownTitle))
                .append("</b><br>")
                .append(normalizePriority(next.priority))
                .append(" priority  •  ")
                .append(escapeHtml(dueText(next)));

        if (overdueCount > 0) {
            text.append("<br><font color='#E15F73'>")
                    .append(overdueCount)
                    .append(overdueCount == 1 ? " task is overdue" : " tasks are overdue")
                    .append("</font>");
        }
        text.append("</html>");

        dashboardNextTaskLabel.setText(text.toString());
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
                    getElapsedSeconds()
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
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setBorder(new EmptyBorder(12, 20, 12, 20));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static JButton createSecondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setForeground(PURPLE);
        button.setBackground(PALE_PURPLE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setBorder(new EmptyBorder(11, 16, 11, 16));
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

        timerLabel.setText(formatClock(getRemainingSeconds()));
        if (timerRing != null) {
            timerRing.repaint();
        }
    }

    /** Draws the timer progress ring behind the timer text. */
    private static class CircularTimerPanel extends JPanel {
        private static final int STROKE_WIDTH = 9;

        CircularTimerPanel() {
            setOpaque(false);
            setBackground(new Color(248, 245, 255));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int diameter = Math.min(getWidth(), getHeight()) - STROKE_WIDTH - 8;
                int x = (getWidth() - diameter) / 2;
                int y = (getHeight() - diameter) / 2;

                g2.setColor(new Color(244, 239, 253));
                g2.fillOval(x + 5, y + 5, diameter - 10, diameter - 10);

                g2.setStroke(new BasicStroke(STROKE_WIDTH,
                        BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(225, 215, 246));
                g2.drawOval(x, y, diameter, diameter);

                double progress = selectedDurationSeconds <= 0 ? 0.0
                        : Math.max(0.0, Math.min(1.0,
                        (double) getRemainingSeconds() / selectedDurationSeconds));
                g2.setColor(PURPLE);
                g2.drawArc(x, y, diameter, diameter, 90,
                        (int) Math.round(-360 * progress));
            } finally {
                g2.dispose();
            }
        }
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