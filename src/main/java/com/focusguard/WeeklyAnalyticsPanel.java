
package com.focusguard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class WeeklyAnalyticsPanel extends JPanel {

    private final SessionDAO sessionDAO = new SessionDAO();

    private final Color backgroundColor = new Color(247, 244, 255);
    private final Color cardColor = Color.WHITE;
    private final Color accentColor = new Color(118, 85, 201);
    private final Color textColor = new Color(48, 39, 71);
    private final Color mutedColor = new Color(130, 122, 150);
    private final Color gridColor = new Color(233, 223, 255);
    private final Color borderColor = new Color(232, 225, 245);

    private JLabel totalFocusLabel;
    private JLabel totalSessionsLabel;
    private JLabel dailyAverageLabel;
    private JLabel bestDayLabel;
    private JLabel statusLabel;

    private WeeklyBarChart chart;

    public WeeklyAnalyticsPanel() {
        setLayout(new BorderLayout(0, 20));
        setBackground(backgroundColor);
        setBorder(new EmptyBorder(28, 30, 28, 30));

        JLabel heading = new JLabel("Weekly Analytics");
        heading.setForeground(textColor);
        heading.setFont(new Font("SansSerif", Font.BOLD, 28));

        JLabel subtitle = new JLabel(
                "Your focus performance over the last 7 days, including today"
        );
        subtitle.setForeground(mutedColor);
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(heading);
        header.add(Box.createVerticalStrut(8));
        header.add(subtitle);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.setForeground(accentColor);
        refreshButton.setBackground(Color.WHITE);
        refreshButton.setFocusPainted(false);
        refreshButton.setCursor(Cursor.getPredefinedCursor(
                Cursor.HAND_CURSOR
        ));
        refreshButton.addActionListener(e -> refreshAnalytics());

        JPanel topRow = new JPanel(new BorderLayout(12, 0));
        topRow.setOpaque(false);
        topRow.add(header, BorderLayout.CENTER);
        topRow.add(refreshButton, BorderLayout.EAST);

        totalFocusLabel = createValueLabel("0s");
        totalSessionsLabel = createValueLabel("0");
        dailyAverageLabel = createValueLabel("0s");
        bestDayLabel = createValueLabel("—");

        JPanel stats = new JPanel(new GridLayout(1, 4, 14, 0));
        stats.setOpaque(false);
        stats.add(createStatCard("TOTAL FOCUS TIME", totalFocusLabel));
        stats.add(createStatCard("COMPLETED SESSIONS", totalSessionsLabel));
        stats.add(createStatCard("DAILY AVERAGE", dailyAverageLabel));
        stats.add(createStatCard("MOST PRODUCTIVE DAY", bestDayLabel));

        chart = new WeeklyBarChart();
        chart.setPreferredSize(new Dimension(700, 340));

        JPanel chartCard = new JPanel(new BorderLayout(0, 15));
        chartCard.setBackground(cardColor);
        chartCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JLabel chartTitle = new JLabel("Daily Focus Time");
        chartTitle.setForeground(textColor);
        chartTitle.setFont(new Font("SansSerif", Font.BOLD, 18));

        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setForeground(mutedColor);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        content.add(stats, BorderLayout.NORTH);
        content.add(chartCard, BorderLayout.CENTER);
        content.add(statusLabel, BorderLayout.SOUTH);

        add(topRow, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);

        refreshAnalytics();
    }

    private JLabel createValueLabel(String value) {
        JLabel label = new JLabel(value);
        label.setForeground(textColor);
        label.setFont(new Font("SansSerif", Font.BOLD, 22));
        label.setToolTipText(value);
        return label;
    }

    private JPanel createStatCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(cardColor);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                new EmptyBorder(18, 16, 18, 16)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(mutedColor);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 10));

        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(14));
        card.add(valueLabel);

        return card;
    }

    public void refreshAnalytics() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusDays(6);

        long[] secondsByDay = new long[7];
        int completedSessions = 0;

        statusLabel.setText("Loading weekly analytics...");

        try {
            List<SessionDAO.FocusSession> sessions =
                    sessionDAO.getAllSessions();

            if (sessions != null) {
                for (SessionDAO.FocusSession session : sessions) {
                    if (session == null || session.startTime == null) {
                        continue;
                    }

                    LocalDateTime startDateTime =
                            session.startTime.toLocalDateTime();

                    LocalDate sessionDate = startDateTime.toLocalDate();

                    // Include only the last six days and today.
                    if (sessionDate.isBefore(firstDay)
                            || sessionDate.isAfter(today)) {
                        continue;
                    }

                    int dayIndex = (int) (
                            sessionDate.toEpochDay()
                                    - firstDay.toEpochDay()
                    );

                    if (dayIndex < 0 || dayIndex >= 7) {
                        continue;
                    }

                    // Add saved focus time for sessions in this week.
                    secondsByDay[dayIndex] +=
                            Math.max(0L, session.elapsedSeconds);

                    // Count completed sessions in this week only.
                    if (session.status != null
                            && session.status.trim()
                            .equalsIgnoreCase("Completed")) {
                        completedSessions++;
                    }
                }
            }

            long totalSeconds = 0;
            long bestDaySeconds = 0;
            int bestDayIndex = -1;

            for (int i = 0; i < secondsByDay.length; i++) {
                totalSeconds += secondsByDay[i];

                // Keep the first day when multiple days tie.
                if (secondsByDay[i] > bestDaySeconds) {
                    bestDaySeconds = secondsByDay[i];
                    bestDayIndex = i;
                }
            }

            totalFocusLabel.setText(formatDuration(totalSeconds));
            totalFocusLabel.setToolTipText(
                    formatDuration(totalSeconds)
            );

            totalSessionsLabel.setText(
                    String.valueOf(completedSessions)
            );

            // Average focus time across all seven calendar days.
            dailyAverageLabel.setText(
                    formatDuration(totalSeconds / 7)
            );
            dailyAverageLabel.setToolTipText(
                    formatDuration(totalSeconds / 7)
                            + " per day"
            );

            if (bestDayIndex >= 0 && bestDaySeconds > 0) {
                LocalDate bestDate = firstDay.plusDays(bestDayIndex);

                String dayName = bestDate.getDayOfWeek()
                        .getDisplayName(
                                TextStyle.SHORT,
                                Locale.ENGLISH
                        );

                bestDayLabel.setText(dayName);
                bestDayLabel.setToolTipText(
                        bestDate + " — "
                                + formatDuration(bestDaySeconds)
                );
            } else {
                bestDayLabel.setText("—");
                bestDayLabel.setToolTipText(
                        "No focus time recorded this week"
                );
            }

            chart.setDailySeconds(secondsByDay, firstDay, today);
            statusLabel.setText(
                    totalSeconds == 0
                            ? "No focus time recorded in the last 7 days yet."
                            : "Showing data from "
                            + firstDay + " to " + today
            );

        } catch (Exception ex) {
            ex.printStackTrace();

            // Clear stale values if loading fails.
            totalFocusLabel.setText("—");
            totalSessionsLabel.setText("—");
            dailyAverageLabel.setText("—");
            bestDayLabel.setText("—");

            chart.setDailySeconds(
                    new long[7], firstDay, today
            );

            statusLabel.setText("Unable to load analytics.");

            JOptionPane.showMessageDialog(
                    this,
                    "Could not load weekly analytics.\n"
                            + "Please check your database connection "
                            + "and try refreshing.\n\n"
                            + (ex.getMessage() == null
                            ? "Unknown error."
                            : ex.getMessage()),
                    "Analytics Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private String formatDuration(long seconds) {
        seconds = Math.max(0L, seconds);

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }

        if (minutes > 0) {
            return minutes + "m " + remainingSeconds + "s";
        }

        return seconds + "s";
    }

    private class WeeklyBarChart extends JPanel {

        private long[] dailySeconds = new long[7];
        private LocalDate firstDay = LocalDate.now().minusDays(6);
        private LocalDate today = LocalDate.now();

        WeeklyBarChart() {
            setBackground(cardColor);
            setOpaque(true);
            setToolTipText("Hover over a bar to see its date and duration");
        }

        void setDailySeconds(
                long[] values,
                LocalDate startDate,
                LocalDate currentDate
        ) {
            dailySeconds = values.clone();
            firstDay = startDate;
            today = currentDate;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();

            try {
                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                int left = 76;
                int right = 15;
                int top = 38;
                int bottom = 55;

                int chartWidth = getWidth() - left - right;
                int chartHeight = getHeight() - top - bottom;

                if (chartWidth <= 0 || chartHeight <= 0) {
                    return;
                }

                long maxSeconds = 0;

                for (long seconds : dailySeconds) {
                    maxSeconds = Math.max(maxSeconds, seconds);
                }

                // Keep the chart readable for short sessions.
                long step = 15 * 60L;
                long scale = Math.max(step, maxSeconds);
                scale = ((scale + step - 1) / step) * step;

                // Four equal intervals, five Y-axis labels.
                long tickStep = Math.max(1L, (scale + 3) / 4);
                scale = tickStep * 4;

                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));

                FontMetrics axisMetrics = g2.getFontMetrics();

                for (int i = 0; i <= 4; i++) {
                    long value = tickStep * i;

                    int y = top + chartHeight
                            - (int) (
                            (double) value / scale * chartHeight
                    );

                    g2.setColor(gridColor);
                    g2.drawLine(
                            left, y, left + chartWidth, y
                    );

                    String label = formatDuration(value);
                    int labelX = left
                            - axisMetrics.stringWidth(label) - 10;

                    g2.setColor(mutedColor);
                    g2.drawString(label, labelX, y + 4);
                }

                int slotWidth = Math.max(1, chartWidth / 7);
                int barWidth = Math.max(
                        6, Math.min(34, slotWidth / 2)
                );

                for (int i = 0; i < 7; i++) {
                    int x = left + i * slotWidth
                            + (slotWidth - barWidth) / 2;

                    int barHeight = (int) (
                            (double) dailySeconds[i]
                                    / scale * chartHeight
                    );

                    if (dailySeconds[i] > 0) {
                        barHeight = Math.max(4, barHeight);
                        barHeight = Math.min(chartHeight, barHeight);
                    }

                    int y = top + chartHeight - barHeight;

                    if (dailySeconds[i] > 0) {
                        g2.setColor(accentColor);
                        g2.fillRoundRect(
                                x, y, barWidth, barHeight, 8, 8
                        );

                        String duration =
                                formatDuration(dailySeconds[i]);

                        g2.setFont(new Font(
                                "SansSerif", Font.PLAIN, 9
                        ));

                        FontMetrics durationMetrics =
                                g2.getFontMetrics();

                        int durationX = left + i * slotWidth
                                + (slotWidth
                                - durationMetrics.stringWidth(duration))
                                / 2;

                        int durationY = Math.max(top + 12, y - 6);

                        g2.setColor(textColor);
                        g2.drawString(
                                duration, durationX, durationY
                        );
                    }

                    LocalDate date = firstDay.plusDays(i);

                    String day = date.getDayOfWeek()
                            .getDisplayName(
                                    TextStyle.SHORT,
                                    Locale.ENGLISH
                            );

                    g2.setFont(new Font(
                            "SansSerif",
                            date.equals(today)
                                    ? Font.BOLD : Font.PLAIN,
                            10
                    ));

                    g2.setColor(
                            date.equals(today)
                                    ? accentColor : mutedColor
                    );

                    FontMetrics dayMetrics = g2.getFontMetrics();

                    int labelX = left + i * slotWidth
                            + (slotWidth
                            - dayMetrics.stringWidth(day)) / 2;

                    g2.drawString(
                            day, labelX, top + chartHeight + 22
                    );
                }
            } finally {
                g2.dispose();
            }
        }

        @Override
        public String getToolTipText(
                java.awt.event.MouseEvent event
        ) {
            int left = 76;
            int right = 15;
            int chartWidth = getWidth() - left - right;

            if (chartWidth <= 0
                    || event.getX() < left
                    || event.getX() >= left + chartWidth) {
                return null;
            }

            int slotWidth = Math.max(1, chartWidth / 7);
            int index = (event.getX() - left) / slotWidth;

            if (index < 0 || index >= 7) {
                return null;
            }

            LocalDate date = firstDay.plusDays(index);

            return date + ": "
                    + formatDuration(dailySeconds[index])
                    + (date.equals(today) ? " (Today)" : "");
        }
    }
}
