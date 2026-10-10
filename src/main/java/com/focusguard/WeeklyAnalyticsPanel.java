
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

    private final Color backgroundColor = new Color(20, 24, 35);
    private final Color cardColor = new Color(30, 36, 50);
    private final Color accentColor = new Color(100, 120, 255);
    private final Color textColor = new Color(235, 238, 248);
    private final Color mutedColor = new Color(155, 164, 185);
    private final Color gridColor = new Color(55, 62, 80);

    private JLabel totalFocusLabel;
    private JLabel totalSessionsLabel;
    private JLabel dailyAverageLabel;
    private JLabel bestDayLabel;

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
        refreshButton.setFocusPainted(false);
        refreshButton.addActionListener(e -> refreshAnalytics());

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(header, BorderLayout.CENTER);
        topRow.add(refreshButton, BorderLayout.EAST);

        totalFocusLabel = createValueLabel("0m 0s");
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
        chartCard.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel chartTitle = new JLabel("Daily Focus Time");
        chartTitle.setForeground(textColor);
        chartTitle.setFont(new Font("SansSerif", Font.BOLD, 18));

        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setOpaque(false);
        content.add(stats, BorderLayout.NORTH);
        content.add(chartCard, BorderLayout.CENTER);

        add(topRow, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);

        refreshAnalytics();
    }

    private JLabel createValueLabel(String value) {
        JLabel label = new JLabel(value);
        label.setForeground(textColor);
        label.setFont(new Font("SansSerif", Font.BOLD, 22));
        return label;
    }

    private JPanel createStatCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(cardColor);
        card.setBorder(new EmptyBorder(18, 16, 18, 16));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(mutedColor);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 10));

        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

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

        try {
            List<SessionDAO.FocusSession> sessions =
                    sessionDAO.getAllSessions();

            for (SessionDAO.FocusSession session : sessions) {

                if (session.startTime == null) {
                    continue;
                }

                LocalDateTime startDateTime =
                        session.startTime.toLocalDateTime();

                LocalDate sessionDate = startDateTime.toLocalDate();

                // Include the last six days and today.
                if (sessionDate.isBefore(firstDay)
                        || sessionDate.isAfter(today)) {
                    continue;
                }

                int dayIndex = (int) (
                        sessionDate.toEpochDay() - firstDay.toEpochDay()
                );

                if (dayIndex < 0 || dayIndex >= 7) {
                    continue;
                }

                // Add saved focus time regardless of session status.
                secondsByDay[dayIndex] +=
                        Math.max(0, session.elapsedSeconds);

                // Count completed sessions separately.
                if (session.status != null
                        && session.status.trim()
                        .equalsIgnoreCase("Completed")) {
                    completedSessions++;
                }
            }

            long totalSeconds = 0;
            long bestDaySeconds = 0;
            int bestDayIndex = -1;

            for (int i = 0; i < secondsByDay.length; i++) {
                totalSeconds += secondsByDay[i];

                if (secondsByDay[i] > bestDaySeconds) {
                    bestDaySeconds = secondsByDay[i];
                    bestDayIndex = i;
                }
            }

            totalFocusLabel.setText(formatDuration(totalSeconds));
            totalSessionsLabel.setText(
                    String.valueOf(completedSessions)
            );

            // Display seconds when the daily average is under one minute.
            dailyAverageLabel.setText(
                    formatDuration(totalSeconds / 7)
            );

            if (bestDayIndex >= 0 && bestDaySeconds > 0) {
                LocalDate bestDate = firstDay.plusDays(bestDayIndex);

                bestDayLabel.setText(
                        bestDate.getDayOfWeek().getDisplayName(
                                TextStyle.SHORT,
                                Locale.ENGLISH
                        )
                );
            } else {
                bestDayLabel.setText("—");
            }

            chart.setDailySeconds(secondsByDay, firstDay, today);

        } catch (Exception ex) {
            ex.printStackTrace();

            JOptionPane.showMessageDialog(
                    this,
                    "Could not load weekly analytics.\n"
                            + ex.getMessage(),
                    "Analytics Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private String formatDuration(long seconds) {
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

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
            );

            int left = 72;
            int right = 15;
            int top = 32;
            int bottom = 65;

            int chartWidth = getWidth() - left - right;
            int chartHeight = getHeight() - top - bottom;

            if (chartWidth <= 0 || chartHeight <= 0) {
                g2.dispose();
                return;
            }

            long maxSeconds = 0;

            for (long seconds : dailySeconds) {
                maxSeconds = Math.max(maxSeconds, seconds);
            }

            // Use a 15-minute minimum scale for short sessions.
            // Round upward in 15-minute increments.
            long step = 15 * 60L;
            long scale = Math.max(step, maxSeconds);
            scale = ((scale + step - 1) / step) * step;

            // Five evenly spaced Y-axis ticks.
            long tickStep = Math.max(1, (scale + 3) / 4);
            scale = tickStep * 4;

            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));

            for (int i = 0; i <= 4; i++) {
                long value = tickStep * i;

                int y = top + chartHeight
                        - (int) ((double) value / scale * chartHeight);

                g2.setColor(gridColor);
                g2.drawLine(left, y, left + chartWidth, y);

                g2.setColor(mutedColor);
                String label = formatDuration(value);

                FontMetrics fm = g2.getFontMetrics();
                int labelX = left - fm.stringWidth(label) - 10;

                g2.drawString(label, labelX, y + 4);
            }

            int slotWidth = chartWidth / 7;
            int barWidth = Math.max(10, Math.min(34, slotWidth / 2));

            for (int i = 0; i < 7; i++) {
                int x = left + i * slotWidth
                        + (slotWidth - barWidth) / 2;

                int barHeight = (int) (
                        (double) dailySeconds[i] / scale * chartHeight
                );

                // Make any positive duration visible as a small bar.
                if (dailySeconds[i] > 0) {
                    barHeight = Math.max(4, barHeight);
                }

                int y = top + chartHeight - barHeight;

                g2.setColor(accentColor);

                if (dailySeconds[i] > 0) {
                    g2.fillRoundRect(
                            x, y, barWidth, barHeight, 8, 8
                    );

                    // Show the exact duration above the bar.
                    String duration = formatDuration(dailySeconds[i]);
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 10));

                    FontMetrics durationMetrics = g2.getFontMetrics();
                    int durationX = left + i * slotWidth
                            + (slotWidth
                            - durationMetrics.stringWidth(duration)) / 2;

                    int durationY = Math.max(top + 12, y - 6);

                    g2.setColor(textColor);
                    g2.drawString(duration, durationX, durationY);
                }

                LocalDate date = firstDay.plusDays(i);

                String day = date.getDayOfWeek().getDisplayName(
                        TextStyle.SHORT,
                        Locale.ENGLISH
                );

                if (date.equals(today)) {
                    day = day + " (Today)";
                }

                g2.setFont(new Font(
                        "SansSerif",
                        date.equals(today) ? Font.BOLD : Font.PLAIN,
                        10
                ));

                g2.setColor(date.equals(today) ? textColor : mutedColor);

                FontMetrics fm = g2.getFontMetrics();
                int labelX = left + i * slotWidth
                        + (slotWidth - fm.stringWidth(day)) / 2;

                g2.drawString(day, labelX, top + chartHeight + 22);
            }

            g2.dispose();
        }
    }
}
