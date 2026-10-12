package com.focusguard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

/** Separate login window that opens the existing dashboard after successful authentication. */
public class LoginFrame extends JFrame {
    static final Color BG = new Color(247, 244, 255);
    static final Color BG_BOTTOM = new Color(235, 226, 249);
    static final Color CARD = Color.WHITE;
    static final Color FIELD = new Color(250, 248, 255);
    static final Color BORDER = new Color(226, 217, 243);
    static final Color TEXT = new Color(48, 39, 71);
    static final Color MUTED = new Color(130, 122, 150);
    static final Color PURPLE = new Color(118, 85, 201);
    static final Color ACCENT = new Color(138, 106, 214);
    static final Color PALE = new Color(233, 223, 255);
    static final Color ERROR = new Color(211, 68, 94);
    static final Color OK = new Color(46, 143, 97);

    private static final Preferences PREFS = Preferences.userNodeForPackage(LoginFrame.class);
    private static final String PREF_EMAIL = "remembered_email";

    private final JTextField emailField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JCheckBox rememberBox = new JCheckBox("Remember my email", false);
    private final JLabel errorLabel = new JLabel(" ");
    private JButton loginButton;
    private JButton registerButton;
    private final UserDAO userDAO = new UserDAO();

    public LoginFrame() {
        super("FocusGuard | Sign in");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(540, 680);
        setMinimumSize(new Dimension(480, 620));
        setLocationRelativeTo(null);
        setResizable(false);

        RoundedPanel root = new RoundedPanel(BG, 0);
        root.setGradient(BG, BG_BOTTOM);
        root.setLayout(new GridBagLayout());
        root.setBorder(new EmptyBorder(26, 28, 26, 28));

        RoundedPanel card = new RoundedPanel(CARD, 26);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(32, 38, 32, 38)));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        card.add(centered("◉", 34, true, PURPLE));
        card.add(Box.createVerticalStrut(6));
        card.add(centered("FOCUSGUARD", 13, true, ACCENT));
        card.add(Box.createVerticalStrut(14));
        card.add(centered("Make room for focus.", 27, true, TEXT));
        card.add(Box.createVerticalStrut(8));
        card.add(centered("A calmer way to get things done", 14, false, MUTED));
        card.add(Box.createVerticalStrut(28));

        styleField(emailField);
        emailField.putClientProperty("JTextField.placeholderText", "you@example.com");
        emailField.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(fieldRow("EMAIL ADDRESS", null));
        card.add(Box.createVerticalStrut(7));
        card.add(emailField);
        card.add(Box.createVerticalStrut(16));

        styleField(passwordField);
        passwordField.putClientProperty("JPasswordField.placeholderText", "Your password");
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton showHide = showHideToggle(passwordField);
        card.add(fieldRow("PASSWORD", showHide));
        card.add(Box.createVerticalStrut(7));
        card.add(passwordField);
        card.add(Box.createVerticalStrut(10));

        rememberBox.setOpaque(false);
        rememberBox.setForeground(MUTED);
        rememberBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
        rememberBox.setFocusable(false);
        rememberBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        rememberBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        card.add(rememberBox);
        card.add(Box.createVerticalStrut(12));

        errorLabel.setForeground(ERROR);
        errorLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        errorLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        card.add(errorLabel);
        card.add(Box.createVerticalStrut(8));

        loginButton = button("Sign in", PURPLE, Color.WHITE);
        registerButton = button("Create an account", PALE, PURPLE);
        card.add(loginButton);
        card.add(Box.createVerticalStrut(11));
        card.add(registerButton);

        root.add(card);
        setContentPane(root);

        String savedEmail = PREFS.get(PREF_EMAIL, null);
        if (savedEmail != null && !savedEmail.isEmpty()) {
            emailField.setText(savedEmail);
            rememberBox.setSelected(true);
            SwingUtilities.invokeLater(passwordField::requestFocusInWindow);
        }

        loginButton.addActionListener(e -> doLogin());
        passwordField.addActionListener(e -> doLogin());
        emailField.addActionListener(e -> passwordField.requestFocusInWindow());
        registerButton.addActionListener(e -> { new RegisterFrame(this).setVisible(true); setVisible(false); });
    }

    private JComponent fieldRow(String caption, JComponent trailing) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        row.add(label(caption), BorderLayout.WEST);
        if (trailing != null) row.add(trailing, BorderLayout.EAST);
        return row;
    }

    private static JButton showHideToggle(JPasswordField field) {
        JButton toggle = new JButton("Show");
        toggle.putClientProperty("JButton.buttonType", "borderless");
        toggle.setForeground(ACCENT);
        toggle.setFont(new Font("SansSerif", Font.BOLD, 11));
        toggle.setFocusable(false);
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.setOpaque(false);
        toggle.setContentAreaFilled(false);
        toggle.setBorder(new EmptyBorder(0, 0, 0, 0));
        toggle.addActionListener(e -> {
            if (field.getEchoChar() == '\0') {
                field.setEchoChar('•');
                toggle.setText("Show");
            } else {
                field.setEchoChar('\0');
                toggle.setText("Hide");
            }
        });
        return toggle;
    }

    private void showError(String message) {
        errorLabel.setText("<html><div style='text-align:center;'>" + message + "</div></html>");
    }

    private void doLogin() {
        String email = emailField.getText().trim();
        char[] password = passwordField.getPassword();
        if (email.isEmpty()) { showError("Enter your email address."); emailField.requestFocus(); return; }
        if (password.length == 0) { showError("Enter your password."); passwordField.requestFocus(); return; }
        errorLabel.setText(" ");
        loginButton.setEnabled(false);
        registerButton.setEnabled(false);
        loginButton.setText("Signing in…");

        new SwingWorker<UserDAO.User, Void>() {
            @Override
            protected UserDAO.User doInBackground() throws Exception {
                return userDAO.login(email, password);
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                registerButton.setEnabled(true);
                loginButton.setText("Sign in");
                try {
                    UserDAO.User user = get();
                    if (rememberBox.isSelected()) PREFS.put(PREF_EMAIL, user.email);
                    else PREFS.remove(PREF_EMAIL);
                    UserSession.setUser(user.id, user.fullName, user.email);
                    dispose();
                    Main.openDashboard();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Sign in was interrupted. Please try again.");
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof IllegalArgumentException) {
                        showError(cause.getMessage());
                        passwordField.setText("");
                        passwordField.requestFocus();
                    } else {
                        JOptionPane.showMessageDialog(LoginFrame.this,
                                friendlyError(cause instanceof Exception ? (Exception) cause : new Exception(cause)),
                                "Sign in unavailable", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }
        }.execute();
    }

    static String friendlyError(Exception ex) {
        return "The database is not available, so account actions cannot be completed.\n\n"
                + "For a frontend preview, run with -Dfocusguard.demo=true.\n\nDetails: " + ex.getMessage();
    }

    static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    static void styleField(JTextField field) {
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        field.setPreferredSize(new Dimension(330, 46));
        field.setFont(new Font("SansSerif", Font.PLAIN, 15));
        field.setForeground(TEXT);
        field.setCaretColor(PURPLE);
        field.setBackground(FIELD);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                new EmptyBorder(10, 13, 10, 13)));
    }

    static JButton button(String text, Color background) {
        return button(text, background, Color.WHITE);
    }

    static JButton button(String text, Color background, Color foreground) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 14));
        b.setForeground(foreground);
        b.setBackground(background);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(new EmptyBorder(14, 20, 14, 20));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        b.setOpaque(true);
        return b;
    }

    static JLabel centered(String text, int size, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height));
        return l;
    }
}
