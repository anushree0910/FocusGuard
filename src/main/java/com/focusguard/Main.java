
package com.focusguard;

import com.formdev.flatlaf.FlatLightLaf;

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
        SwingUtilities.invokeLater(Main::createGUI);
    }

    /** Closes the dashboard and returns to login, clearing the in-memory user. */
    public static void logoutToLogin() {
        SwingUtilities.invokeLater(() -> {
            if (timer != null) {
                timer.stop();
            }
            running = false;
            if (mainFrame != null) {
                mainFrame.dispose();
                mainFrame = null;
            }
            UserSession.clear();
            new LoginFrame().setVisible(true);
        });
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

        contentPanel.add(new ProfilePanel(), "Profile");

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
            }

            cardLayout.show(contentPanel, page);
        });

        return button;
    }



    // --------------------------------------------------
    // DASHBOARD
    // --------------------------------------------------


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

        JLabel dateChip = new JLabel(java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")));
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

        // Main feature area is now a two-column layout rather than a single stacked card.
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
        JLabel planText = new JLabel("<html>Keep it simple.<br>Pick one task and begin.</html>");
        planText.setFont(new Font("SansSerif", Font.PLAIN, 13));
        planText.setForeground(MUTED);
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
        planCard.add(planText);
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

        // Circular timer face with a progress ring that updates every second.
        CircularTimerPanel timerFace = new CircularTimerPanel();
        timerFace.setLayout(new BoxLayout(timerFace, BoxLayout.Y_AXIS));
        timerFace.setPreferredSize(new Dimension(300, 300));
        timerFace.setMinimumSize(new Dimension(260, 260));
        timerFace.setMaximumSize(new Dimension(300, 300));
        timerFace.setAlignmentX(Component.CENTER_ALIGNMENT);
        timerFace.setBorder(new EmptyBorder(82, 12, 78, 12));

        timerLabel = new JLabel("25:00");
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 43));
        timerLabel.setForeground(TEXT);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerStatusLabel = new JLabel("Ready when you are");
        timerStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        timerStatusLabel.setForeground(MUTED);
        timerStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerFace.add(Box.createVerticalGlue());
        timerFace.add(timerLabel);
        timerFace.add(Box.createVerticalStrut(9));
        timerFace.add(timerStatusLabel);
        timerFace.add(Box.createVerticalGlue());

        JLabel encouragement = new JLabel("Small steps create meaningful progress.");
        encouragement.setFont(new Font("SansSerif", Font.ITALIC, 12));
        encouragement.setForeground(MUTED);
        encouragement.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerCenter.add(Box.createVerticalGlue());
        timerCenter.add(timerFace);
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

    private static java.util.Map<String, JButton> taskFilterButtons =
            new java.util.LinkedHashMap<>();


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

        JLabel addSubtitle = new JLabel("What would you like to work on?");
        addSubtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        addSubtitle.setForeground(MUTED);

        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setOpaque(false);
        inputRow.add(taskPageInput, BorderLayout.CENTER);
        inputRow.add(addButton, BorderLayout.EAST);

        addContent.add(addTitle);
        addContent.add(Box.createVerticalStrut(5));
        addContent.add(addSubtitle);
        addContent.add(Box.createVerticalStrut(12));
        addContent.add(inputRow);

        addCard.add(addContent, BorderLayout.CENTER);

        taskFilterButtons.clear();

        JPanel filterPanel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 8, 0)
        );
        filterPanel.setOpaque(false);

        for (String filter : new String[]{"All", "Pending", "Completed"}) {
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

        taskSummaryLabel = new JLabel("YOUR TASKS");
        taskSummaryLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        taskSummaryLabel.setForeground(TEXT);

        JPanel listHeader = new JPanel(new BorderLayout(0, 12));
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
        for (java.util.Map.Entry<String, JButton> entry
                : taskFilterButtons.entrySet()) {

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
            int pending = 0;
            int shown = 0;

            for (TaskDAO.Task task : tasks) {
                boolean isCompleted =
                        "Completed".equalsIgnoreCase(task.status);

                if (isCompleted) {
                    completed++;
                } else {
                    pending++;
                }

                if ("Pending".equals(taskFilter) && isCompleted) {
                    continue;
                }

                if ("Completed".equals(taskFilter) && !isCompleted) {
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
            );

            if (shown == 0) {
                JPanel emptyCard = new JPanel();
                emptyCard.setOpaque(true);
                emptyCard.setBackground(CARD);
                emptyCard.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        new EmptyBorder(30, 18, 30, 18)
                ));
                emptyCard.setLayout(new BoxLayout(
                        emptyCard, BoxLayout.Y_AXIS
                ));
                emptyCard.setAlignmentX(Component.LEFT_ALIGNMENT);
                emptyCard.setMaximumSize(
                        new Dimension(Integer.MAX_VALUE, 180)
                );

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
                    emptySubtitleText =
                            "Finish a task and your progress will appear here.";
                } else if ("Pending".equals(taskFilter)) {
                    emptyTitleText = "You're all caught up!";
                    emptySubtitleText =
                            "There are no pending tasks right now.";
                } else {
                    emptyTitleText = "Your task list is ready";
                    emptySubtitleText =
                            "Add your first task above to get started.";
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

                taskCardsPanel.add(emptyCard);
            }

        } catch (SQLException ex) {
            showDatabaseError(mainFrame, ex);
        }

        // Rebuild the page so the selected filter style updates too.
        if (contentPanel != null) {
            contentPanel.revalidate();
            contentPanel.repaint();
        }

        taskCardsPanel.revalidate();
        taskCardsPanel.repaint();
    }



    private static JPanel createTaskCard(TaskDAO.Task task) {
        boolean completed =
                "Completed".equalsIgnoreCase(task.status);

        JPanel card = new JPanel(new BorderLayout(13, 0));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(15, 16, 15, 16)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
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

        JLabel status = new JLabel(
                completed ? "✓ COMPLETED" : "◷ PENDING"
        );
        status.setFont(new Font("SansSerif", Font.BOLD, 10));
        status.setOpaque(true);
        status.setForeground(completed ? GREEN : new Color(175, 112, 35));
        status.setBackground(
                completed
                        ? new Color(229, 246, 236)
                        : new Color(255, 243, 222)
        );
        status.setBorder(new EmptyBorder(5, 8, 5, 8));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.add(taskTitle);
        textPanel.add(Box.createVerticalStrut(8));
        textPanel.add(status);

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

        card.add(checkBox, BorderLayout.WEST);
        card.add(textPanel, BorderLayout.CENTER);
        card.add(deleteButton, BorderLayout.EAST);

        return card;
    }


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

        stats.add(createHistoryStatCard(
                "TOTAL FOCUS TIME",
                historyFocusValue,
                PURPLE
        ));

        stats.add(createHistoryStatCard(
                "SESSIONS TODAY",
                historySessionsValue,
                BLUE
        ));

        stats.add(createHistoryStatCard(
                "COMPLETED SESSIONS",
                historyCompletedValue,
                GREEN
        ));

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
        activityHeader.setLayout(
                new BoxLayout(activityHeader, BoxLayout.Y_AXIS)
        );
        activityHeader.add(sectionTitle);
        activityHeader.add(Box.createVerticalStrut(5));
        activityHeader.add(sectionSubtitle);

        // Session cards
        historyCardsPanel = new JPanel();
        historyCardsPanel.setOpaque(false);
        historyCardsPanel.setLayout(
                new BoxLayout(historyCardsPanel, BoxLayout.Y_AXIS)
        );

        JScrollPane scrollPane = new JScrollPane(historyCardsPanel);
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
        boolean completed =
                "Completed".equalsIgnoreCase(statusText);
        boolean paused =
                "Paused".equalsIgnoreCase(statusText);
        boolean active =
                "Active".equalsIgnoreCase(statusText);

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

        // Session title
        String mode = session.mode == null
                ? "Focus" : session.mode;

        JLabel sessionTitle = new JLabel(
                escapeHtml(mode) + " Session"
        );
        sessionTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        sessionTitle.setForeground(TEXT);

        JLabel details = new JLabel(
                "Started " + escapeHtml(started)
                        + "   •   Ended " + escapeHtml(ended)
        );
        details.setFont(new Font("SansSerif", Font.PLAIN, 12));
        details.setForeground(MUTED);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(
                new BoxLayout(textPanel, BoxLayout.Y_AXIS)
        );
        textPanel.add(sessionTitle);
        textPanel.add(Box.createVerticalStrut(9));
        textPanel.add(details);

        // Duration
        JLabel duration = new JLabel(formatDuration(seconds));
        duration.setFont(new Font("SansSerif", Font.BOLD, 16));
        duration.setForeground(PURPLE);
        duration.setAlignmentX(Component.RIGHT_ALIGNMENT);

        // Status label
        String displayStatus = statusText == null
                ? "Unknown" : statusText;

        JLabel status = new JLabel(
                displayStatus.toUpperCase()
        );
        status.setFont(new Font("SansSerif", Font.BOLD, 10));
        status.setForeground(statusColor);
        status.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JPanel rightPanel = new JPanel();
        rightPanel.setOpaque(false);
        rightPanel.setLayout(
                new BoxLayout(rightPanel, BoxLayout.Y_AXIS)
        );
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

        timerLabel.setText(formatClock(remainingSeconds));
        // Refresh the ring whenever the displayed countdown changes.
        Container parent = timerLabel.getParent();
        while (parent != null && !(parent instanceof CircularTimerPanel)) {
            parent = parent.getParent();
        }
        if (parent instanceof CircularTimerPanel circularTimerPanel) {
            circularTimerPanel.repaint();
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
                        (double) remainingSeconds / selectedDurationSeconds));
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