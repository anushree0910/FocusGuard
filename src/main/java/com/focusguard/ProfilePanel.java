package com.focusguard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/** Profile page embedded in the main CardLayout: live stats, activity, tasks, achievements and account settings. */
public class ProfilePanel extends JPanel {
    private static final Color BG = new Color(247, 244, 255), CARD = Color.WHITE,
            FIELD = new Color(250, 248, 255), BORDER = new Color(226, 217, 243),
            TEXT = new Color(48, 39, 71), MUTED = new Color(130, 122, 150),
            PURPLE = new Color(118, 85, 201), ACCENT = new Color(138, 106, 214),
            PALE = new Color(233, 223, 255), OK = new Color(46, 143, 97),
            ERROR = new Color(211, 68, 94), BLUE = new Color(91, 141, 220),
            GRID_EMPTY = new Color(246, 243, 252);

    private static final int GRID_WEEKS = 26;

    private final JTextField nameField = new JTextField(26);
    private final JTextField emailField = new JTextField(26);
    private final JPasswordField currentField = new JPasswordField(26);
    private final JPasswordField newField = new JPasswordField(26);
    private final JPasswordField confirmField = new JPasswordField(26);
    private final JLabel avatarInitials = new JLabel("", SwingConstants.CENTER);
    private final JLabel bannerInitials = new JLabel("FG", SwingConstants.CENTER);
    private final JLabel summaryName = new JLabel();
    private final JLabel summaryEmail = new JLabel();
    private final JLabel accountStatus = status();
    private final JLabel securityStatus = status();
    private JButton saveButton;
    private JButton changeButton;
    private final UserDAO dao = new UserDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final TaskDAO taskDAO = new TaskDAO();

    private final JPanel contributionHolder = holder();
    private final JPanel progressHolder = holder();
    private final JPanel activityHolder = holder();
    private final JPanel tasksHolder = holder();
    private final JPanel achievementsHolder = holder();

    private long lastLoadMillis = 0;

    public ProfilePanel() {
        setBackground(BG);
        setLayout(new BorderLayout(0, 18));
        setBorder(new EmptyBorder(24, 30, 24, 30));

        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);
        page.add(profileBanner(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        tabs.setForeground(TEXT);
        tabs.setBackground(BG);
        tabs.addTab("Overview", scrollable(overviewPage()));
        tabs.addTab("Activity", scrollable(activityPage()));
        tabs.addTab("Tasks", scrollable(tasksPage()));
        tabs.addTab("Achievements", scrollable(achievementsPage()));
        tabs.addTab("Account settings", scrollable(settingsPage()));
        page.add(tabs, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);

        loadSessionValues();
        refreshSummary();
        loadData();

        // CardLayout shows this panel when the Profile nav button is clicked:
        // refresh the data then, with a short cooldown to avoid re-querying on
        // rapid tab switching. The banner Refresh button bypasses this entirely.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) {
                if (System.currentTimeMillis() - lastLoadMillis > 15_000) {
                    loadData();
                }
            }
        });
    }

    // --------------------------------------------------
    // DATA LOADING
    // --------------------------------------------------

    private void loadData() {
        lastLoadMillis = System.currentTimeMillis();
        new SwingWorker<Data, String>() {
            @Override
            protected Data doInBackground() throws Exception {
                Data d = new Data();
                d.sessions = sessionDAO.getAllSessions();
                d.tasks = taskDAO.getAllTasks();
                return d;
            }

            @Override
            protected void done() {
                try {
                    Data d = get();
                    renderContribution(d);
                    renderProgress(d);
                    renderActivity(d);
                    renderTasks(d);
                    renderAchievements(d);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    renderError("Could not load your profile data.");
                } catch (ExecutionException ex) {
                    renderError("Could not load your profile data: "
                            + (ex.getCause() == null || ex.getCause().getMessage() == null
                                    ? "database unavailable" : ex.getCause().getMessage()));
                }
            }
        }.execute();
    }

    private static class Data {
        List<SessionDAO.FocusSession> sessions = new ArrayList<>();
        List<TaskDAO.Task> tasks = new ArrayList<>();

        List<SessionDAO.FocusSession> completedSessions() {
            List<SessionDAO.FocusSession> out = new ArrayList<>();
            for (SessionDAO.FocusSession s : sessions) {
                if (s.status != null && s.status.equalsIgnoreCase("Completed")) out.add(s);
            }
            return out;
        }

        static int minutesOf(SessionDAO.FocusSession s) {
            return s.elapsedSeconds > 0 ? s.elapsedSeconds / 60 : Math.max(s.durationMinutes, 0);
        }

        Map<LocalDate, Integer> minutesByDay() {
            Map<LocalDate, Integer> map = new HashMap<>();
            for (SessionDAO.FocusSession s : sessions) {
                if (s.startTime == null) continue;
                int minutes = minutesOf(s);
                if (minutes <= 0) continue;
                LocalDate day = s.startTime.toLocalDateTime().toLocalDate();
                map.merge(day, minutes, Integer::sum);
            }
            return map;
        }

        int completedTasks() {
            int n = 0;
            for (TaskDAO.Task t : tasks) {
                if (t.status != null && t.status.equalsIgnoreCase("Completed")) n++;
            }
            return n;
        }

        int currentStreak() {
            Map<LocalDate, Integer> map = minutesByDay();
            LocalDate cursor = LocalDate.now();
            if (!map.containsKey(cursor)) cursor = cursor.minusDays(1);
            int streak = 0;
            while (map.containsKey(cursor)) {
                streak++;
                cursor = cursor.minusDays(1);
            }
            return streak;
        }
    }

    // --------------------------------------------------
    // PAGE SHELL
    // --------------------------------------------------

    private JPanel profileBanner() {
        JPanel banner = new JPanel(new BorderLayout(18, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, new Color(138, 106, 214), getWidth(), getHeight(), new Color(86, 66, 157)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.dispose();
            }
        };
        banner.setOpaque(false);
        banner.setBorder(new EmptyBorder(22, 24, 22, 24));
        RoundedPanel avatar = new RoundedPanel(PURPLE, 82);
        avatar.setGradient(new Color(255, 255, 255, 70), new Color(255, 255, 255, 35));
        avatar.setPreferredSize(new Dimension(82, 82));
        avatar.setLayout(new BorderLayout());
        bannerInitials.setForeground(Color.WHITE);
        bannerInitials.setFont(new Font("SansSerif", Font.BOLD, 25));
        avatar.add(bannerInitials, BorderLayout.CENTER);
        JPanel details = new JPanel(); details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel name = new JLabel("My FocusGuard Profile");
        name.setFont(new Font("SansSerif", Font.BOLD, 25)); name.setForeground(Color.WHITE);
        JLabel email = new JLabel("Your focus journey, tasks and account activity");
        email.setFont(new Font("SansSerif", Font.PLAIN, 13)); email.setForeground(new Color(245, 240, 255));
        details.add(name); details.add(Box.createVerticalStrut(7)); details.add(email);
        JButton refresh = new JButton("↻  Refresh");
        refresh.setFont(new Font("SansSerif", Font.BOLD, 13));
        refresh.setForeground(PURPLE);
        refresh.setBackground(Color.WHITE);
        refresh.setFocusPainted(false);
        refresh.setBorderPainted(false);
        refresh.setOpaque(true);
        refresh.setBorder(new EmptyBorder(12, 18, 12, 18));
        refresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refresh.addActionListener(e -> loadData());
        JPanel refreshBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        refreshBox.setOpaque(false);
        refreshBox.add(refresh);
        banner.add(avatar, BorderLayout.WEST);
        banner.add(details, BorderLayout.CENTER);
        banner.add(refreshBox, BorderLayout.EAST);
        banner.setPreferredSize(new Dimension(700, 132));
        return banner;
    }

    private JScrollPane scrollable(JPanel panel) {
        // Pin the page to the top: inside a viewport, BoxLayout would otherwise
        // stretch holders with unbounded maximum height and leave empty gaps.
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(panel, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setBorder(null); scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel pageColumn() {
        JPanel p = new JPanel(); p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(18, 2, 22, 8));
        return p;
    }

    private static JPanel holder() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        p.add(label("Loading your data…", 13, false, MUTED));
        return p;
    }

    private void setContent(JPanel container, JComponent... cards) {
        container.removeAll();
        for (int i = 0; i < cards.length; i++) {
            container.add(cards[i]);
            if (i < cards.length - 1) container.add(Box.createVerticalStrut(16));
        }
        container.revalidate();
        container.repaint();
    }

    private void renderError(String message) {
        for (JPanel h : new JPanel[]{contributionHolder, progressHolder,
                activityHolder, tasksHolder, achievementsHolder}) {
            setContent(h, sectionCard("Data unavailable",
                    message + "\nCheck that MySQL is running, then open this page again."));
        }
    }

    // --------------------------------------------------
    // TAB PAGES
    // --------------------------------------------------

    private JPanel overviewPage() {
        JPanel p = pageColumn();
        p.add(summaryCard()); p.add(Box.createVerticalStrut(16));
        p.add(contributionHolder); p.add(Box.createVerticalStrut(16));
        p.add(progressHolder); p.add(Box.createVerticalStrut(16));
        p.add(pinnedProjectCard()); p.add(Box.createVerticalStrut(16));
        p.add(skillsCard());
        return p;
    }

    private JPanel activityPage() {
        JPanel p = pageColumn();
        p.add(activityHolder);
        return p;
    }

    private JPanel tasksPage() {
        JPanel p = pageColumn();
        p.add(tasksHolder);
        return p;
    }

    private JPanel achievementsPage() {
        JPanel p = pageColumn();
        p.add(achievementsHolder);
        return p;
    }

    private JPanel settingsPage() {
        JPanel p = pageColumn();
        p.add(accountCard()); p.add(Box.createVerticalStrut(16));
        p.add(securityCard()); p.add(Box.createVerticalStrut(16));
        p.add(sessionCard());
        return p;
    }

    // --------------------------------------------------
    // LIVE CARDS
    // --------------------------------------------------

    private void renderContribution(Data d) {
        RoundedPanel card = card(20);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(innerBorder(20, 22, 20, 22));
        card.add(label("Focus contributions", 18, true, TEXT));
        card.add(Box.createVerticalStrut(5));
        card.add(label("Your focus sessions over the last " + GRID_WEEKS + " weeks", 13, false, MUTED));
        card.add(Box.createVerticalStrut(16));

        Map<LocalDate, Integer> minutes = d.minutesByDay();
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(GRID_WEEKS * 7L - 1);
        DateTimeFormatter tip = DateTimeFormatter.ofPattern("EEE d MMM yyyy");

        JPanel grid = new JPanel(new GridLayout(7, GRID_WEEKS, 3, 3));
        grid.setOpaque(false);
        int totalMinutes = 0;
        int sessionCount = 0;
        for (int col = 0; col < GRID_WEEKS; col++) {
            for (int row = 0; row < 7; row++) {
                LocalDate day = start.plusDays((long) col * 7 + row);
                int dayMinutes = day.isAfter(today) ? -1 : minutes.getOrDefault(day, 0);
                JPanel cell = new JPanel();
                cell.setBackground(dayMinutes < 0 ? new Color(250, 248, 255) : cellColor(dayMinutes));
                cell.setBorder(BorderFactory.createLineBorder(BORDER, 1, true));
                if (dayMinutes > 0) {
                    cell.setToolTipText(day.format(tip) + " · " + formatMinutes(dayMinutes) + " focused");
                }
                grid.add(cell);
            }
        }
        for (SessionDAO.FocusSession s : d.sessions) {
            if (s.startTime == null) continue;
            LocalDate day = s.startTime.toLocalDateTime().toLocalDate();
            if (day.isBefore(start) || day.isAfter(today)) continue;
            totalMinutes += Data.minutesOf(s);
            sessionCount++;
        }

        grid.setPreferredSize(new Dimension(900, 116));
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 116));
        card.add(grid);
        card.add(Box.createVerticalStrut(10));

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        legend.setOpaque(false);
        legend.add(label(totalMinutes > 0
                ? formatMinutes(totalMinutes) + " focused across " + sessionCount + " session"
                        + (sessionCount == 1 ? "" : "s") + " in this period"
                : "No focus sessions yet — start the timer to fill your grid.", 12, false, MUTED));
        legend.add(Box.createHorizontalStrut(10));
        JLabel less = label("Less", 11, false, MUTED);
        legend.add(less);
        for (int m : new int[]{0, 10, 30, 60, 120}) {
            JPanel swatch = new JPanel();
            swatch.setPreferredSize(new Dimension(12, 12));
            swatch.setMaximumSize(new Dimension(12, 12));
            swatch.setBackground(cellColor(m));
            swatch.setBorder(BorderFactory.createLineBorder(BORDER, 1, true));
            legend.add(swatch);
        }
        JLabel more = label("More", 11, false, MUTED);
        legend.add(more);
        card.add(legend);

        setContent(contributionHolder, card);
    }

    private void renderProgress(Data d) {
        List<SessionDAO.FocusSession> completed = d.completedSessions();
        int totalMinutes = 0;
        int longest = 0;
        for (SessionDAO.FocusSession s : completed) {
            int m = Data.minutesOf(s);
            totalMinutes += m;
            longest = Math.max(longest, m);
        }
        LocalDate weekAgo = LocalDate.now().minusDays(6);
        int weekMinutes = 0, weekSessions = 0;
        for (SessionDAO.FocusSession s : d.sessions) {
            if (s.startTime == null) continue;
            LocalDate day = s.startTime.toLocalDateTime().toLocalDate();
            if (!day.isBefore(weekAgo)) {
                weekMinutes += Data.minutesOf(s);
                weekSessions++;
            }
        }

        JPanel stats = new JPanel(new GridLayout(3, 2, 12, 12));
        stats.setOpaque(false);
        stats.add(statTile(String.valueOf(completed.size()), "sessions completed"));
        stats.add(statTile(formatMinutes(totalMinutes), "total focus time"));
        stats.add(statTile(weekSessions == 0 ? "—" : formatMinutes(weekMinutes), "focused this week"));
        stats.add(statTile(longest == 0 ? "—" : formatMinutes(longest), "longest session"));
        stats.add(statTile(String.valueOf(d.completedTasks()), "tasks completed"));
        stats.add(statTile(d.currentStreak() + (d.currentStreak() == 1 ? " day" : " days"), "current focus streak"));
        fitHeight(stats);

        RoundedPanel card = card(20);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(innerBorder(20, 22, 20, 22));
        card.add(label("Your progress", 18, true, TEXT));
        card.add(Box.createVerticalStrut(5));
        card.add(label("Live numbers from your saved sessions and tasks", 13, false, MUTED));
        card.add(Box.createVerticalStrut(14));
        card.add(stats);

        setContent(progressHolder, card);
    }

    private void renderActivity(Data d) {
        RoundedPanel card = card(20);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(innerBorder(20, 22, 20, 22));
        card.add(label("Recent focus activity", 18, true, TEXT));
        card.add(Box.createVerticalStrut(5));
        card.add(label("Your latest sessions from the FocusGuard history", 13, false, MUTED));
        card.add(Box.createVerticalStrut(14));

        if (d.sessions.isEmpty()) {
            card.add(label("No sessions recorded yet. Run the focus timer and your history will appear here.",
                    13, false, MUTED));
        } else {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm");
            int shown = 0;
            for (SessionDAO.FocusSession s : d.sessions) {
                if (shown >= 15) break;
                boolean done = s.status != null && s.status.equalsIgnoreCase("Completed");
                String left = (done ? "● Completed" : "○ " + (s.status == null ? "Ended" : s.status));
                String middle = (s.mode == null ? "" : s.mode)
                        + (s.technique == null || s.technique.isEmpty() ? "" : " · " + s.technique)
                        + " · " + formatMinutes(Data.minutesOf(s));
                String when = s.startTime == null ? "" : s.startTime.toLocalDateTime().format(fmt);
                card.add(feedRow(left, done ? OK : MUTED, middle, when));
                card.add(Box.createVerticalStrut(8));
                shown++;
            }
        }

        setContent(activityHolder, card);
    }

    private void renderTasks(Data d) {
        int total = d.tasks.size();
        int completed = d.completedTasks();
        LocalDate today = LocalDate.now();
        List<TaskDAO.Task> pending = new ArrayList<>();
        List<TaskDAO.Task> overdue = new ArrayList<>();
        for (TaskDAO.Task t : d.tasks) {
            boolean done = t.status != null && t.status.equalsIgnoreCase("Completed");
            if (done) continue;
            if (t.dueDate != null && t.dueDate.toLocalDate().isBefore(today)) overdue.add(t);
            else pending.add(t);
        }

        RoundedPanel summary = card(20);
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setBorder(innerBorder(20, 22, 20, 22));
        summary.add(label("Task overview", 18, true, TEXT));
        summary.add(Box.createVerticalStrut(5));
        summary.add(label("Connected to your task list", 13, false, MUTED));
        summary.add(Box.createVerticalStrut(14));

        JPanel counts = new JPanel(new GridLayout(1, 3, 12, 0));
        counts.setOpaque(false);
        counts.add(statTile(String.valueOf(completed), "completed"));
        counts.add(statTile(String.valueOf(pending.size()), "pending"));
        counts.add(statTile(String.valueOf(overdue.size()), "overdue"));
        fitHeight(counts);
        summary.add(counts);
        summary.add(Box.createVerticalStrut(14));

        JProgressBar bar = new JProgressBar(0, Math.max(total, 1));
        bar.setValue(completed);
        bar.setStringPainted(true);
        bar.setString(total == 0 ? "No tasks yet" : completed + " of " + total + " done");
        bar.setForeground(PURPLE);
        bar.setBackground(PALE);
        bar.setBorderPainted(false);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));

        summary.add(bar);

        RoundedPanel list = card(20);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(innerBorder(20, 22, 20, 22));
        list.add(label("Open tasks", 18, true, TEXT));
        list.add(Box.createVerticalStrut(12));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM");
        List<TaskDAO.Task> open = new ArrayList<>();
        open.addAll(overdue);
        open.addAll(pending);
        if (open.isEmpty()) {
            list.add(label("Nothing open — every task is done. Nice work!", 13, false, OK));
        } else {
            int shown = 0;
            for (TaskDAO.Task t : open) {
                if (shown >= 10) break;
                boolean late = overdue.contains(t);
                String due = t.dueDate == null ? "no deadline" : (late ? "overdue · " : "due ") + t.dueDate.toLocalDate().format(fmt);
                list.add(feedRow(late ? "▲ Overdue" : "◆ " + (t.priority == null ? "Normal" : t.priority),
                        late ? ERROR : PURPLE, t.title, due));
                list.add(Box.createVerticalStrut(8));
                shown++;
            }
            if (open.size() > shown) {
                list.add(label("+ " + (open.size() - shown) + " more on the Tasks page", 12, false, MUTED));
            }
        }

        setContent(tasksHolder, summary, list);
    }

    private void renderAchievements(Data d) {
        List<SessionDAO.FocusSession> completed = d.completedSessions();
        int totalMinutes = 0, longest = 0;
        for (SessionDAO.FocusSession s : completed) {
            int m = Data.minutesOf(s);
            totalMinutes += m;
            longest = Math.max(longest, m);
        }

        Badge[] badges = {
                new Badge("✦", "First Focus", "Finish your first session", completed.size() >= 1),
                new Badge("◈", "Warm Up", "5 sessions completed", completed.size() >= 5),
                new Badge("◇", "Session Collector", "15 sessions completed", completed.size() >= 15),
                new Badge("⬤", "Deep Worker", "One session of 45+ minutes", longest >= 45),
                new Badge("⏱", "Marathoner", "5 hours of total focus time", totalMinutes >= 300),
                new Badge("✓", "Task Finisher", "5 tasks completed", d.completedTasks() >= 5),
                new Badge("☰", "Weekly Streak", "5 days in a row of focus", d.currentStreak() >= 5),
                new Badge("★", "Focus Legend", "25 sessions and 10 tasks done",
                        completed.size() >= 25 && d.completedTasks() >= 10),
        };

        int unlocked = 0;
        for (Badge b : badges) if (b.unlocked) unlocked++;

        RoundedPanel card = card(20);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(innerBorder(20, 22, 20, 22));
        card.add(label("Achievements", 18, true, TEXT));
        card.add(Box.createVerticalStrut(5));
        card.add(label(unlocked + " of " + badges.length + " unlocked from your real session and task history",
                13, false, MUTED));
        card.add(Box.createVerticalStrut(16));

        JPanel grid = new JPanel(new GridLayout(0, 2, 12, 12));
        grid.setOpaque(false);
        for (Badge b : badges) grid.add(badgeTile(b));
        setContent(achievementsHolder, card, gridWrap(grid));
    }

    private JPanel gridWrap(JPanel grid) {
        RoundedPanel card = card(20);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(innerBorder(20, 22, 20, 22));
        fitHeight(grid);
        card.add(grid);
        return card;
    }

    private JPanel badgeTile(Badge b) {
        RoundedPanel tile = new RoundedPanel(b.unlocked ? PALE : GRID_EMPTY, 16);
        tile.setLayout(new BorderLayout(10, 0));
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(b.unlocked ? PURPLE : BORDER, 1, true),
                new EmptyBorder(12, 14, 12, 14)));
        JLabel glyph = new JLabel(b.unlocked ? b.icon : "🔒", SwingConstants.CENTER);
        glyph.setFont(new Font("SansSerif", Font.BOLD, 18));
        glyph.setForeground(b.unlocked ? PURPLE : MUTED);
        glyph.setPreferredSize(new Dimension(34, 34));
        JPanel text = new JPanel(); text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(label(b.name, 14, true, b.unlocked ? PURPLE : MUTED));
        text.add(Box.createVerticalStrut(3));
        JLabel hint = label(b.unlocked ? "Unlocked" : b.rule, 12, false, b.unlocked ? OK : MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(hint);
        tile.add(glyph, BorderLayout.WEST);
        tile.add(text, BorderLayout.CENTER);
        return tile;
    }

    private static class Badge {
        final String icon, name, rule;
        final boolean unlocked;
        Badge(String icon, String name, String rule, boolean unlocked) {
            this.icon = icon; this.name = name; this.rule = rule; this.unlocked = unlocked;
        }
    }

    // --------------------------------------------------
    // SHARED CARD PIECES
    // --------------------------------------------------

    private JPanel statTile(String value, String caption) {
        RoundedPanel tile = new RoundedPanel(FIELD, 14);
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(12, 14, 12, 14)));
        JLabel v = label(value, 20, true, PURPLE);
        v.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel c = label(caption, 12, false, MUTED);
        c.setAlignmentX(Component.CENTER_ALIGNMENT);
        tile.add(v);
        tile.add(Box.createVerticalStrut(4));
        tile.add(c);
        return tile;
    }

    private JPanel feedRow(String leftText, Color leftColor, String middle, String right) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel left = label(leftText, 13, true, leftColor);
        left.setPreferredSize(new Dimension(120, 24));
        JLabel mid = label(middle, 13, false, TEXT);
        JLabel when = label(right, 12, false, MUTED);
        when.setHorizontalAlignment(SwingConstants.RIGHT);
        when.setPreferredSize(new Dimension(160, 24));
        row.add(left, BorderLayout.WEST);
        row.add(mid, BorderLayout.CENTER);
        row.add(when, BorderLayout.EAST);
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        return row;
    }

    /** Let a BoxLayout child keep its natural height instead of being clipped by a fixed cap. */
    private static void fitHeight(JComponent c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
    }

    private static Color cellColor(int minutes) {
        if (minutes <= 0) return GRID_EMPTY;
        if (minutes <= 20) return PALE;
        if (minutes <= 45) return new Color(206, 186, 245);
        if (minutes <= 90) return new Color(169, 135, 224);
        return PURPLE;
    }

    private static String formatMinutes(int minutes) {
        if (minutes < 60) return minutes + " min";
        int h = minutes / 60, m = minutes % 60;
        return m == 0 ? h + " hr" : h + " hr " + m + " min";
    }

    private JPanel summaryCard() {
        RoundedPanel card = card(20);
        card.setLayout(new BorderLayout(18, 0));
        card.setBorder(innerBorder(22, 24, 22, 24));

        RoundedPanel avatar = new RoundedPanel(PURPLE, 76);
        avatar.setGradient(new Color(138, 106, 214), new Color(104, 70, 184));
        avatar.setPreferredSize(new Dimension(76, 76));
        avatar.setLayout(new BorderLayout());
        avatarInitials.setForeground(Color.WHITE);
        avatarInitials.setFont(new Font("SansSerif", Font.BOLD, 24));
        avatar.add(avatarInitials, BorderLayout.CENTER);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        summaryName.setFont(new Font("SansSerif", Font.BOLD, 20));
        summaryName.setForeground(TEXT);
        summaryName.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel badge = label("FOCUSGUARD ACCOUNT", 11, true, PURPLE);
        summaryEmail.setFont(new Font("SansSerif", Font.PLAIN, 13));
        summaryEmail.setForeground(MUTED);
        summaryEmail.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(summaryName);
        text.add(Box.createVerticalStrut(6));
        text.add(badge);
        text.add(Box.createVerticalStrut(5));
        text.add(summaryEmail);

        card.add(avatar, BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private JPanel pinnedProjectCard() {
        RoundedPanel card = card(20); card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(innerBorder(20, 22, 20, 22));
        card.add(label("Pinned project", 18, true, TEXT)); card.add(Box.createVerticalStrut(12));
        JPanel tile = new JPanel(new BorderLayout(12, 0)); tile.setOpaque(false);
        RoundedPanel icon = new RoundedPanel(PALE, 14); icon.setPreferredSize(new Dimension(48, 48));
        icon.setLayout(new GridBagLayout()); JLabel glyph = new JLabel("FG"); glyph.setFont(new Font("SansSerif", Font.BOLD, 16)); glyph.setForeground(PURPLE); icon.add(glyph);
        JPanel text = new JPanel(); text.setOpaque(false); text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(label("FocusGuard", 16, true, PURPLE)); text.add(Box.createVerticalStrut(5));
        text.add(label("Focus timer, task tracking and productivity analytics", 13, false, MUTED));
        text.add(Box.createVerticalStrut(8)); text.add(label("Java  ·  Swing  ·  MySQL", 12, true, TEXT));
        tile.add(icon, BorderLayout.WEST); tile.add(text, BorderLayout.CENTER); card.add(tile);
        return card;
    }

    private JPanel skillsCard() {
        RoundedPanel card = card(20); card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(innerBorder(20, 22, 20, 22));
        card.add(label("Tech stack & interests", 18, true, TEXT)); card.add(Box.createVerticalStrut(12));
        JPanel tags = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5)); tags.setOpaque(false);
        for (String tag : new String[]{"Java", "Java Swing", "MySQL", "JDBC", "Productivity", "Time management"}) {
            JLabel chip = new JLabel(tag); chip.setOpaque(true); chip.setBackground(PALE); chip.setForeground(PURPLE);
            chip.setBorder(new EmptyBorder(7, 11, 7, 11)); chip.setFont(new Font("SansSerif", Font.BOLD, 12)); tags.add(chip);
        }
        card.add(tags); return card;
    }

    private JPanel sectionCard(String title, String description) {
        RoundedPanel card = card(20); card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(innerBorder(20, 22, 20, 22)); card.add(label(title, 18, true, TEXT));
        card.add(Box.createVerticalStrut(9));
        JTextArea text = new JTextArea(description); text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setOpaque(false); text.setForeground(MUTED); text.setFont(new Font("SansSerif", Font.PLAIN, 13));
        text.setAlignmentX(Component.LEFT_ALIGNMENT); text.setRows(3); text.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        card.add(text); return card;
    }

    private JPanel accountCard() {
        RoundedPanel card = card(20);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(innerBorder(22, 24, 24, 24));
        card.add(label("Account details", 19, true, TEXT));
        card.add(Box.createVerticalStrut(5));
        card.add(label("Keep your name and email up to date.", 13, false, MUTED));
        card.add(Box.createVerticalStrut(20));
        styleField(nameField);
        styleField(emailField);
        nameField.putClientProperty("JTextField.placeholderText", "Your full name");
        emailField.putClientProperty("JTextField.placeholderText", "you@example.com");
        addField(card, "FULL NAME", nameField);
        card.add(Box.createVerticalStrut(14));
        addField(card, "EMAIL ADDRESS", emailField);
        card.add(Box.createVerticalStrut(16));
        card.add(accountStatus);
        card.add(Box.createVerticalStrut(8));
        saveButton = button("Save changes", PURPLE, Color.WHITE);
        saveButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveButton.addActionListener(e -> saveProfile());
        card.add(saveButton);
        return card;
    }

    private JPanel securityCard() {
        RoundedPanel card = card(20);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(innerBorder(22, 24, 24, 24));
        card.add(label("Change password", 19, true, TEXT));
        card.add(Box.createVerticalStrut(5));
        card.add(label("New password must have at least 8 characters. You stay signed in.", 13, false, MUTED));
        card.add(Box.createVerticalStrut(20));
        styleField(currentField);
        styleField(newField);
        styleField(confirmField);
        currentField.putClientProperty("JPasswordField.placeholderText", "Current password");
        newField.putClientProperty("JPasswordField.placeholderText", "New password");
        confirmField.putClientProperty("JPasswordField.placeholderText", "Repeat new password");
        addField(card, "CURRENT PASSWORD", currentField);
        card.add(Box.createVerticalStrut(12));
        addField(card, "NEW PASSWORD", newField);
        card.add(Box.createVerticalStrut(12));
        addField(card, "CONFIRM NEW PASSWORD", confirmField);
        card.add(Box.createVerticalStrut(14));
        card.add(securityStatus);
        card.add(Box.createVerticalStrut(8));
        changeButton = button("Update password", BLUE, Color.WHITE);
        changeButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        changeButton.addActionListener(e -> changePassword());
        card.add(changeButton);
        return card;
    }

    private JPanel sessionCard() {
        RoundedPanel card = card(20);
        card.setLayout(new BorderLayout(16, 0));
        card.setBorder(innerBorder(20, 24, 20, 24));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(label("Session", 17, true, TEXT));
        text.add(Box.createVerticalStrut(5));
        JLabel hint = label("Logging out clears this device and returns to the sign-in screen.", 12, false, MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(hint);

        JButton logout = button("Log out", new Color(253, 235, 240), ERROR);
        logout.addActionListener(e -> confirmLogout());
        card.add(text, BorderLayout.CENTER);
        card.add(logout, BorderLayout.EAST);
        return card;
    }

    // --------------------------------------------------
    // HELPERS
    // --------------------------------------------------

    private RoundedPanel card(int arc) {
        RoundedPanel p = new RoundedPanel(CARD, arc);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(20, 22, 20, 22)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static javax.swing.border.Border innerBorder(int top, int left, int bottom, int right) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(top, left, bottom, right));
    }

    private static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel status() {
        JLabel l = new JLabel(" ");
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setForeground(MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        return l;
    }

    private static void setStatus(JLabel label, String text, Color color) {
        label.setText("<html><div style='text-align:center;'>" + text + "</div></html>");
        label.setForeground(color);
    }

    private static void addField(JPanel panel, String caption, JTextField field) {
        panel.add(label(caption, 11, true, MUTED));
        panel.add(Box.createVerticalStrut(7));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(field);
    }

    private static void styleField(JTextField f) {
        f.setMaximumSize(new Dimension(520, 44));
        f.setPreferredSize(new Dimension(420, 44));
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        f.setForeground(TEXT);
        f.setCaretColor(PURPLE);
        f.setBackground(FIELD);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(10, 12, 10, 12)));
    }

    private static JButton button(String text, Color background, Color foreground) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setForeground(foreground);
        b.setBackground(background);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setBorder(new EmptyBorder(12, 20, 12, 20));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // --------------------------------------------------
    // ACCOUNT ACTIONS
    // --------------------------------------------------

    private void loadSessionValues() {
        nameField.setText(UserSession.getFullName() == null ? "" : UserSession.getFullName());
        emailField.setText(UserSession.getEmail() == null ? "" : UserSession.getEmail());
    }

    private void refreshSummary() {
        String name = UserSession.getFullName() == null || UserSession.getFullName().isEmpty()
                ? "FocusGuard member" : UserSession.getFullName();
        String initials = UserSession.getInitials();
        avatarInitials.setText(initials.isEmpty() ? "FG" : initials);
        bannerInitials.setText(initials.isEmpty() ? "FG" : initials);
        summaryName.setText(name);
        summaryEmail.setText(UserSession.getEmail() == null ? "" : UserSession.getEmail());
    }

    private void saveProfile() {
        if (!UserSession.isLoggedIn()) {
            setStatus(accountStatus, "Please sign in again to update your profile.", ERROR);
            return;
        }
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        if (name.isEmpty() || name.length() > 100) {
            setStatus(accountStatus, "Name must contain 1-100 characters.", ERROR);
            return;
        }
        if (!UserDAO.isEmailValid(email)) {
            setStatus(accountStatus, "Enter a valid email address.", ERROR);
            return;
        }
        int userId = UserSession.getUserId();
        setStatus(accountStatus, " ", MUTED);
        saveButton.setEnabled(false);
        saveButton.setText("Saving…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                dao.updateProfile(userId, name, email);
                return null;
            }

            @Override
            protected void done() {
                saveButton.setEnabled(true);
                saveButton.setText("Save changes");
                try {
                    get();
                    setStatus(accountStatus, "Profile updated.", OK);
                    UserSession.setUser(userId, name, email);
                    loadSessionValues();
                    refreshSummary();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setStatus(accountStatus, "Save was interrupted. Please try again.", ERROR);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    setStatus(accountStatus, cause.getMessage() == null
                            ? "Could not update profile." : cause.getMessage(), ERROR);
                }
            }
        }.execute();
    }

    private void changePassword() {
        if (!UserSession.isLoggedIn()) {
            setStatus(securityStatus, "Please sign in again.", ERROR);
            return;
        }
        char[] current = currentField.getPassword();
        char[] fresh = newField.getPassword();
        char[] confirm = confirmField.getPassword();
        if (current.length == 0 || fresh.length == 0 || confirm.length == 0) {
            setStatus(securityStatus, "Fill in all password fields.", ERROR);
            wipe(current, fresh, confirm);
            return;
        }
        if (fresh.length < 8) {
            setStatus(securityStatus, "New password must be at least 8 characters.", ERROR);
            wipe(current, fresh, confirm);
            return;
        }
        if (!Arrays.equals(fresh, confirm)) {
            setStatus(securityStatus, "New passwords do not match.", ERROR);
            wipe(current, fresh, confirm);
            return;
        }
        wipe(confirm);
        int userId = UserSession.getUserId();
        setStatus(securityStatus, " ", MUTED);
        changeButton.setEnabled(false);
        changeButton.setText("Updating…");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                dao.changePassword(userId, current, fresh);
                return null;
            }

            @Override
            protected void done() {
                changeButton.setEnabled(true);
                changeButton.setText("Update password");
                currentField.setText("");
                newField.setText("");
                confirmField.setText("");
                try {
                    get();
                    setStatus(securityStatus, "Password updated.", OK);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    setStatus(securityStatus, "Update was interrupted. Please try again.", ERROR);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    setStatus(securityStatus, cause instanceof IllegalArgumentException
                            ? cause.getMessage()
                            : "Could not change password: " + (cause.getMessage() == null ? "database unavailable" : cause.getMessage()), ERROR);
                }
            }
        }.execute();
    }

    private static void wipe(char[]... arrays) {
        for (char[] array : arrays) Arrays.fill(array, '\0');
    }

    private void confirmLogout() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Log out of FocusGuard on this device?", "Log out",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            Main.logoutToLogin();
        }
    }
}
