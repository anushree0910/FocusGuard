
package com.focusguard;

import com.sun.jna.platform.win32.WinDef.HWND;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DistractionMonitor {

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    private final DistractionDAO distractionDAO = new DistractionDAO();

    private ScheduledFuture<?> monitoringTask;

    private volatile boolean running = false;
    private volatile boolean dialogOpen = false;
    private volatile int currentSessionId = -1;

    private volatile String approvedApp = "";
    private volatile String ignoredApp = "";
    private volatile long restoreWindowUntil = 0;

    private String candidateApp = "";
    private String candidateTitle = "";
    private HWND candidateWindow;
    private long candidateSince = 0;

    private String trackingApp = "";
    private int trackingRecordId = -1;
    private long distractionStartTime = 0;

    private static final long CONFIRMATION_TIME_MS = 500;

    private static final Set<String> PROTECTED_PROCESSES = Set.of(
            "explorer", "java", "javaw", "dwm", "winlogon",
            "csrss", "services", "lsass", "svchost", "smss",
            "system", "taskmgr", "sihost", "fontdrvhost", "wininit"
    );

    private static final Set<String> SAFE_TO_CLOSE = Set.of(
            "notepad", "chrome", "msedge", "firefox", "calc"
    );

    public void setSessionId(int sessionId) {
        currentSessionId = sessionId;
    }

    public void startMonitoring() {
        if (running) {
            return;
        }

        running = true;

        monitoringTask = scheduler.scheduleAtFixedRate(() -> {
            if (!running || dialogOpen) {
                return;
            }

            // Capture the active window only once per polling cycle.
            HWND activeWindow =
                    ForegroundAppTracker.getActiveWindowHandle();

            String process =
                    ForegroundAppTracker.getProcessName(activeWindow);

            String title =
                    ForegroundAppTracker.getWindowTitle(activeWindow);

            if (process == null || process.isBlank()) {
                resetCandidate();
                return;
            }

            process = process.toLowerCase(Locale.ROOT).trim();

            if (!ignoredApp.isEmpty() && !process.equals(ignoredApp)) {
                ignoredApp = "";
            }

            if (process.equals(ignoredApp)) {
                resetCandidate();
                return;
            }

            if (isFocusGuardWindow(process, title)
                    || PROTECTED_PROCESSES.contains(process)) {

                // Allow time for the approved window to regain focus.
                if (System.currentTimeMillis() > restoreWindowUntil) {
                    finishTracking();
                }

                resetCandidate();
                return;
            }

            if (process.equals(approvedApp)) {
                resetCandidate();

                if (distractionStartTime == 0) {
                    startTracking(process);
                }

                return;
            }

            long now = System.currentTimeMillis();

            if (!process.equals(candidateApp)) {
                candidateApp = process;
                candidateTitle = title == null ? "" : title;
                candidateWindow = activeWindow;
                candidateSince = now;
                return;
            }

            if (now - candidateSince < CONFIRMATION_TIME_MS) {
                return;
            }

            finishTracking();

            dialogOpen = true;

            String appToPrompt = candidateApp;
            String titleToPrompt = candidateTitle;
            HWND windowToRestore = candidateWindow;

            resetCandidate();

            SwingUtilities.invokeLater(() -> {
                if (running) {
                    showReminder(
                            appToPrompt,
                            titleToPrompt,
                            windowToRestore
                    );
                } else {
                    dialogOpen = false;
                }
            });

        }, 0, 250, TimeUnit.MILLISECONDS);
    }

    private void resetCandidate() {
        candidateApp = "";
        candidateTitle = "";
        candidateWindow = null;
        candidateSince = 0;
    }

    private boolean isFocusGuardWindow(String process, String title) {
        if (title != null && title.toLowerCase(Locale.ROOT)
                .contains("focusguard")) {
            return true;
        }

        return process.equals("java") || process.equals("javaw");
    }

    private void startTracking(String appName) {
        if (distractionStartTime != 0) {
            return;
        }

        trackingApp = appName;
        distractionStartTime = System.currentTimeMillis();
        trackingRecordId = -1;

        try {
            trackingRecordId = distractionDAO.startDistraction(
                    currentSessionId, appName, null
            );

            System.out.println("Distraction saved: " + appName
                    + " (ID " + trackingRecordId + ")");

        } catch (SQLException ex) {
            System.err.println("Could not save distraction to MySQL.");
            ex.printStackTrace();
        }
    }

    private void showReminder(
            String appName,
            String windowTitle,
            HWND targetWindow) {

        JDialog dialog = new JDialog(
                (Frame) null,
                "FocusGuard",
                Dialog.ModalityType.APPLICATION_MODAL
        );

        dialog.setUndecorated(true);
        dialog.setSize(460, 350);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setFocusableWindowState(true);

        Color background = new Color(28, 20, 48);
        Color cardColor = new Color(40, 29, 65);
        Color lavender = new Color(190, 160, 255);
        Color white = new Color(248, 245, 255);
        Color muted = new Color(190, 180, 210);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 20));
        mainPanel.setBackground(background);
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(lavender, 2),
                BorderFactory.createEmptyBorder(25, 28, 24, 28)
        ));

        JPanel topPanel = new JPanel();
        topPanel.setOpaque(false);
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        JLabel brand = new JLabel("✦  F O C U S G U A R D");
        brand.setFont(new Font("SansSerif", Font.BOLD, 13));
        brand.setForeground(lavender);
        brand.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel icon = new JLabel("◉");
        icon.setFont(new Font("SansSerif", Font.PLAIN, 38));
        icon.setForeground(lavender);
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel heading = new JLabel("Protect your focus");
        heading.setFont(new Font("SansSerif", Font.BOLD, 24));
        heading.setForeground(white);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);

        String displayName = appName;

        if (appName.equals("chrome") && !windowTitle.isBlank()) {
            displayName = "Chrome — " + windowTitle;
        } else if (appName.equals("msedge") && !windowTitle.isBlank()) {
            displayName = "Edge — " + windowTitle;
        } else if (appName.equals("notepad")) {
            displayName = "Notepad";
        }

        JLabel message = new JLabel(
                "<html><div style='text-align:center;'>"
                        + "You're using <b>" + escapeHtml(displayName)
                        + "</b> during your focus session."
                        + "<br><br>Is it important enough to continue?"
                        + "</div></html>"
        );

        message.setFont(new Font("SansSerif", Font.PLAIN, 14));
        message.setForeground(muted);
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        message.setHorizontalAlignment(SwingConstants.CENTER);

        topPanel.add(brand);
        topPanel.add(Box.createVerticalStrut(15));
        topPanel.add(icon);
        topPanel.add(Box.createVerticalStrut(8));
        topPanel.add(heading);
        topPanel.add(Box.createVerticalStrut(14));
        topPanel.add(message);

        JButton yesButton = new JButton("Yes, continue");
        styleButton(yesButton, lavender, background);

        JButton noButton = new JButton("No, close it");
        styleButton(noButton, cardColor, white);

        noButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(100, 80, 130), 1
                ),
                BorderFactory.createEmptyBorder(12, 8, 12, 8)
        ));

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(yesButton);
        buttonPanel.add(noButton);

        JLabel footer = new JLabel(
                "Your goals deserve your attention."
        );
        footer.setFont(new Font("SansSerif", Font.ITALIC, 11));
        footer.setForeground(new Color(140, 125, 165));
        footer.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel lowerPanel = new JPanel(new BorderLayout(0, 14));
        lowerPanel.setOpaque(false);
        lowerPanel.add(buttonPanel, BorderLayout.CENTER);
        lowerPanel.add(footer, BorderLayout.SOUTH);

        mainPanel.add(topPanel, BorderLayout.CENTER);
        mainPanel.add(lowerPanel, BorderLayout.SOUTH);

        yesButton.addActionListener(e -> {
            approvedApp = appName;
            ignoredApp = "";
            resetCandidate();

            if (!appName.equals(trackingApp)) {
                finishTracking();
                startTracking(appName);
            }

            // Give Windows time to return focus to the original window.
            restoreWindowUntil = System.currentTimeMillis() + 1500;

            dialogOpen = false;
            dialog.dispose();

            // Restore the exact window that triggered the popup.
            Timer restoreTimer = new Timer(180, event -> {
                boolean restored =
                        ForegroundAppTracker.restoreWindow(targetWindow);
                System.out.println(
                        "Window restore result: " + restored
                                + " | Target title: "
                                + ForegroundAppTracker.getWindowTitle(targetWindow)
                                + " | Current foreground: "
                                + ForegroundAppTracker.getWindowTitle(
                                ForegroundAppTracker.getActiveWindowHandle()
                        )
                );

                if (!restored && targetWindow != null) {
                    System.out.println(
                            "Could not restore the original window. "
                                    + "Windows may have blocked the focus change."
                    );
                }
            });

            restoreTimer.setRepeats(false);
            restoreTimer.start();
        });

        noButton.addActionListener(e -> {
            approvedApp = "";
            ignoredApp = appName;

            resetCandidate();
            finishTracking();

            dialogOpen = false;
            dialog.dispose();

            if (!SAFE_TO_CLOSE.contains(appName)) {
                System.out.println(
                        "For safety, FocusGuard will not terminate " + appName
                );
                return;
            }

            try {
                Process closeProcess = new ProcessBuilder(
                        "taskkill", "/IM", appName + ".exe", "/T", "/F"
                ).start();

                Thread closeThread = new Thread(() -> {
                    try {
                        int exitCode = closeProcess.waitFor();

                        if (exitCode != 0) {
                            System.err.println(
                                    "Windows could not close " + appName
                                            + ". Exit code: " + exitCode
                            );
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });

                closeThread.setDaemon(true);
                closeThread.start();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        dialog.getRootPane().registerKeyboardAction(
                e -> noButton.doClick(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        dialog.setContentPane(mainPanel);
        dialog.setAlwaysOnTop(true);
        dialog.setLocationRelativeTo(null);

        // Keep the popup in front while it is waiting for your decision.
        dialog.setVisible(true);
        dialog.setVisible(true);

// This runs after the modal dialog has fully closed.
        dialogOpen = false;

        if (approvedApp != null && targetWindow != null) {
            restoreWindowUntil = System.currentTimeMillis() + 1500;

            javax.swing.Timer restoreTimer = new javax.swing.Timer(250, event -> {
                boolean restored =
                        ForegroundAppTracker.restoreWindow(targetWindow);

                com.sun.jna.platform.win32.WinDef.HWND currentWindow =
                        ForegroundAppTracker.getActiveWindowHandle();

                System.out.println(
                        "Window restore result: " + restored
                                + " | Target title: "
                                + ForegroundAppTracker.getWindowTitle(targetWindow)
                                + " | Current foreground: "
                                + ForegroundAppTracker.getWindowTitle(currentWindow)
                );
            });

            restoreTimer.setRepeats(false);
            restoreTimer.start();
        }

        // Do not repeatedly force FocusGuard to the front after a click.
    }

    private void finishTracking() {
        if (distractionStartTime == 0) {
            return;
        }

        int seconds = (int) ((System.currentTimeMillis()
                - distractionStartTime) / 1000);

        if (trackingRecordId != -1) {
            try {
                distractionDAO.finishDistraction(
                        trackingRecordId, seconds
                );

                System.out.println(trackingApp
                        + " distraction duration: " + seconds + " seconds");

            } catch (SQLException ex) {
                System.err.println(
                        "Could not update distraction duration."
                );
                ex.printStackTrace();
            }
        }

        trackingRecordId = -1;
        distractionStartTime = 0;
        trackingApp = "";
    }

    private void styleButton(
            JButton button,
            Color background,
            Color foreground) {

        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        button.setBorder(
                BorderFactory.createEmptyBorder(13, 8, 13, 8)
        );
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public void stopMonitoring() {
        running = false;

        if (monitoringTask != null) {
            monitoringTask.cancel(false);
            monitoringTask = null;
        }

        finishTracking();

        approvedApp = "";
        ignoredApp = "";
        restoreWindowUntil = 0;
        resetCandidate();
        dialogOpen = false;
    }
}
