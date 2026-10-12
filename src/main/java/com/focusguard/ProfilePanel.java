package com.focusguard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/** Profile page embedded in the main CardLayout: edit name/email, change password and log out. */
public class ProfilePanel extends JPanel {
    private static final Color BG = new Color(247, 244, 255), CARD = Color.WHITE,
            FIELD = new Color(250, 248, 255), BORDER = new Color(226, 217, 243),
            TEXT = new Color(48, 39, 71), MUTED = new Color(130, 122, 150),
            PURPLE = new Color(118, 85, 201), ACCENT = new Color(138, 106, 214),
            PALE = new Color(233, 223, 255), OK = new Color(46, 143, 97),
            ERROR = new Color(211, 68, 94), BLUE = new Color(91, 141, 220);

    private final JTextField nameField = new JTextField(26);
    private final JTextField emailField = new JTextField(26);
    private final JPasswordField currentField = new JPasswordField(26);
    private final JPasswordField newField = new JPasswordField(26);
    private final JPasswordField confirmField = new JPasswordField(26);
    private final JLabel avatarInitials = new JLabel("", SwingConstants.CENTER);
    private final JLabel summaryName = new JLabel();
    private final JLabel summaryEmail = new JLabel();
    private final JLabel accountStatus = status();
    private final JLabel securityStatus = status();
    private JButton saveButton;
    private JButton changeButton;
    private final UserDAO dao = new UserDAO();

    public ProfilePanel() {
        setBackground(BG);
        setLayout(new BorderLayout(0, 20));
        setBorder(new EmptyBorder(30, 34, 30, 34));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        String firstName = UserSession.getFirstName();
        header.add(label(firstName.isEmpty() ? "My Profile" : "My Profile · " + firstName, 28, true, TEXT));
        header.add(Box.createVerticalStrut(6));
        header.add(label("Manage your account, security and session.", 14, false, MUTED));
        add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 16, 0);
        body.add(summaryCard(), gbc);
        gbc.gridy++; body.add(accountCard(), gbc);
        gbc.gridy++; body.add(securityCard(), gbc);
        gbc.gridy++; body.add(sessionCard(), gbc);
        gbc.gridy++; gbc.weighty = 1; body.add(Box.createVerticalGlue(), gbc);
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        loadSessionValues();
        refreshSummary();
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

    private void loadSessionValues() {
        nameField.setText(UserSession.getFullName() == null ? "" : UserSession.getFullName());
        emailField.setText(UserSession.getEmail() == null ? "" : UserSession.getEmail());
    }

    private void refreshSummary() {
        String name = UserSession.getFullName() == null || UserSession.getFullName().isEmpty()
                ? "FocusGuard member" : UserSession.getFullName();
        String initials = UserSession.getInitials();
        avatarInitials.setText(initials.isEmpty() ? "FG" : initials);
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
