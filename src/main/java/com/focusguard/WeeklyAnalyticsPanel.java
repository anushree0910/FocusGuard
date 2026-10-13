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

        topRow.setOpaque(false);
        topRow.add(header, BorderLayout.CENTER);
        topRow.add(refreshButton, BorderLayout.EAST);

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

        JLabel chartTitle = new JLabel("Daily Focus Time");
        chartTitle.setForeground(textColor);
        chartTitle.setFont(new Font("SansSerif", Font.BOLD, 18));

        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);

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

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(mutedColor);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 10));

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

        try {
            List<SessionDAO.FocusSession> sessions =
                    sessionDAO.getAllSessions();

                for (SessionDAO.FocusSession session : sessions) {
                        continue;
                    }

                    LocalDateTime startDateTime =
                            session.startTime.toLocalDateTime();

                    LocalDate sessionDate = startDateTime.toLocalDate();

                    if (sessionDate.isBefore(firstDay)
                            || sessionDate.isAfter(today)) {
                        continue;
                    }

                    int dayIndex = (int) (
                    );

                    if (dayIndex < 0 || dayIndex >= 7) {
                        continue;
                    }

                    secondsByDay[dayIndex] +=

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

            dailyAverageLabel.setText(
                    formatDuration(totalSeconds / 7)
            );

            if (bestDayIndex >= 0 && bestDaySeconds > 0) {
                LocalDate bestDate = firstDay.plusDays(bestDayIndex);

                                TextStyle.SHORT,
                                Locale.ENGLISH
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

                int right = 15;

                int chartWidth = getWidth() - left - right;
                int chartHeight = getHeight() - top - bottom;

                if (chartWidth <= 0 || chartHeight <= 0) {
                    return;
                }

                long maxSeconds = 0;

                for (long seconds : dailySeconds) {
                    maxSeconds = Math.max(maxSeconds, seconds);
                }

                long step = 15 * 60L;
                long scale = Math.max(step, maxSeconds);
                scale = ((scale + step - 1) / step) * step;

                scale = tickStep * 4;


                for (int i = 0; i <= 4; i++) {
                    long value = tickStep * i;

                    int y = top + chartHeight

                    g2.setColor(gridColor);

                    g2.setColor(mutedColor);
                    g2.drawString(label, labelX, y + 4);
                }


                for (int i = 0; i < 7; i++) {
                    int x = left + i * slotWidth
                            + (slotWidth - barWidth) / 2;

                    int barHeight = (int) (
                    );

                    if (dailySeconds[i] > 0) {
                        barHeight = Math.max(4, barHeight);
                    }

                    int y = top + chartHeight - barHeight;

                        g2.setColor(accentColor);
                        g2.fillRoundRect(
                                x, y, barWidth, barHeight, 8, 8
                        );


                        int durationX = left + i * slotWidth
                                + (slotWidth

                        int durationY = Math.max(top + 12, y - 6);

                        g2.setColor(textColor);
                    }

                    LocalDate date = firstDay.plusDays(i);

                                    TextStyle.SHORT,
                                    Locale.ENGLISH
                            );

                    g2.setFont(new Font(
                            "SansSerif",
                            10
                    ));


                    int labelX = left + i * slotWidth

                }
                g2.dispose();
            }
        }
}
