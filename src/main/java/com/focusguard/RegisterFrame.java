package com.focusguard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/** Account creation with live email validation, password strength and duplicate-email handling. */
public class RegisterFrame extends JFrame {
    private final JFrame loginFrame;
    private final JTextField nameField = new JTextField();
    private final JTextField emailField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JPasswordField confirmField = new JPasswordField();
    private final StrengthBar strengthBar = new StrengthBar();
    private final JLabel emailHint = hint();
    private final JLabel strengthHint = hint();
    private final JLabel matchHint = hint();
    private final JLabel errorLabel = hint();
    private JButton createButton;
    private JButton backButton;
    private final UserDAO userDAO = new UserDAO();

    public RegisterFrame(JFrame loginFrame) {
        super("FocusGuard | Create account");
        this.loginFrame = loginFrame;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(580, 820);
        setMinimumSize(new Dimension(520, 640));
        setLocationRelativeTo(loginFrame);
        setResizable(true);

        RoundedPanel root = new RoundedPanel(LoginFrame.BG, 0);
        root.setGradient(LoginFrame.BG, LoginFrame.BG_BOTTOM);
        root.setLayout(new GridBagLayout());
        root.setBorder(new EmptyBorder(22, 26, 22, 26));

        RoundedPanel card = new RoundedPanel(LoginFrame.CARD, 26);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LoginFrame.BORDER, 1, true),
                new EmptyBorder(28, 38, 28, 38)));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        card.add(LoginFrame.centered("◉", 30, true, LoginFrame.PURPLE));
        card.add(Box.createVerticalStrut(6));
        card.add(LoginFrame.centered("FOCUSGUARD", 13, true, LoginFrame.ACCENT));
        card.add(Box.createVerticalStrut(11));
        card.add(LoginFrame.centered("Create your account", 26, true, LoginFrame.TEXT));
        card.add(Box.createVerticalStrut(7));
        card.add(LoginFrame.centered("Start building better focus habits", 14, false, LoginFrame.MUTED));
        card.add(Box.createVerticalStrut(22));

        LoginFrame.styleField(nameField);
        LoginFrame.styleField(emailField);
        LoginFrame.styleField(passwordField);
        LoginFrame.styleField(confirmField);
        emailField.putClientProperty("JTextField.placeholderText", "you@example.com");
        passwordField.putClientProperty("JPasswordField.placeholderText", "At least 8 characters");
        confirmField.putClientProperty("JPasswordField.placeholderText", "Repeat your password");

        addField(card, "FULL NAME", nameField, null, null);
        addField(card, "EMAIL ADDRESS", emailField, emailHint, null);
        addField(card, "PASSWORD", passwordField, strengthHint, showHideToggle(passwordField));
        strengthBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(strengthBar);
        card.add(Box.createVerticalStrut(15));
        addField(card, "CONFIRM PASSWORD", confirmField, matchHint, showHideToggle(confirmField));

        errorLabel.setForeground(LoginFrame.ERROR);
        errorLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        card.add(errorLabel);
        card.add(Box.createVerticalStrut(6));

        createButton = LoginFrame.button("Create account", LoginFrame.PURPLE, Color.WHITE);
        backButton = LoginFrame.button("Back to sign in", LoginFrame.PALE, LoginFrame.PURPLE);
        card.add(createButton);
        card.add(Box.createVerticalStrut(10));
        card.add(backButton);

        root.add(card);
        JScrollPane scroll = new JScrollPane(root);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(LoginFrame.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        final Dimension rootPref = root.getPreferredSize();
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                Dimension view = scroll.getViewport().getExtentSize();
                root.setPreferredSize(new Dimension(
                        Math.max(view.width, rootPref.width),
                        Math.max(view.height, rootPref.height)));
                root.revalidate();
            }
        });
        setContentPane(scroll);

        wireLiveValidation();
        createButton.addActionListener(e -> register());
        nameField.addActionListener(e -> emailField.requestFocus());
        emailField.addActionListener(e -> passwordField.requestFocus());
        passwordField.addActionListener(e -> confirmField.requestFocus());
        confirmField.addActionListener(e -> register());
        backButton.addActionListener(e -> goBack());
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (loginFrame != null && loginFrame.isDisplayable()) loginFrame.setVisible(true);
            }
        });
    }

    private void wireLiveValidation() {
        onChange(emailField, () -> {
            String email = emailField.getText().trim();
            if (email.isEmpty()) {
                emailHint.setText(" ");
            } else if (UserDAO.isEmailValid(email)) {
                setHint(emailHint, "Valid email format", LoginFrame.OK);
            } else {
                setHint(emailHint, "Enter a valid email address", LoginFrame.ERROR);
            }
        });
        onChange(passwordField, () -> {
            String password = new String(passwordField.getPassword());
            int score = strength(password);
            strengthBar.setScore(score);
            if (password.isEmpty()) strengthHint.setText(" ");
            else if (password.length() < 8) setHint(strengthHint, "Use at least 8 characters", LoginFrame.ERROR);
            else setHint(strengthHint, "Strength: " + strengthWord(score),
                    score <= 1 ? LoginFrame.ERROR : score == 2 ? new Color(214, 138, 45) : LoginFrame.OK);
            updateMatchHint();
        });
        onChange(confirmField, this::updateMatchHint);
    }

    private void updateMatchHint() {
        String password = new String(passwordField.getPassword());
        String confirm = new String(confirmField.getPassword());
        if (confirm.isEmpty()) matchHint.setText(" ");
        else if (password.equals(confirm)) setHint(matchHint, "Passwords match", LoginFrame.OK);
        else setHint(matchHint, "Passwords do not match", LoginFrame.ERROR);
    }

    private void register() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        char[] password = passwordField.getPassword();
        char[] confirm = confirmField.getPassword();
        errorLabel.setText(" ");

        if (name.isEmpty() || name.length() > 100) {
            showError("Please enter your name (max 100 characters).");
            Arrays.fill(password, '\0'); Arrays.fill(confirm, '\0'); return;
        }
        if (!UserDAO.isEmailValid(email)) {
            showError("Please enter a valid email address.");
            Arrays.fill(password, '\0'); Arrays.fill(confirm, '\0'); return;
        }
        if (password.length < 8) {
            showError("Use a password with at least 8 characters.");
            Arrays.fill(password, '\0'); Arrays.fill(confirm, '\0'); return;
        }
        if (!Arrays.equals(password, confirm)) {
            showError("Passwords do not match.");
            Arrays.fill(password, '\0'); Arrays.fill(confirm, '\0'); return;
        }
        Arrays.fill(confirm, '\0');

        createButton.setEnabled(false);
        backButton.setEnabled(false);
        createButton.setText("Creating account…");

        new SwingWorker<UserDAO.User, Void>() {
            @Override
            protected UserDAO.User doInBackground() throws Exception {
                if (userDAO.existsByEmail(email)) {
                    throw new IllegalArgumentException("An account with this email already exists. Try signing in instead.");
                }
                return userDAO.register(name, email, password);
            }

            @Override
            protected void done() {
                createButton.setEnabled(true);
                backButton.setEnabled(true);
                createButton.setText("Create account");
                try {
                    UserDAO.User user = get();
                    UserSession.setUser(user.id, user.fullName, user.email);
                    dispose();
                    Main.openDashboard();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Account creation was interrupted. Please try again.");
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof IllegalArgumentException) {
                        showError(cause.getMessage());
                    } else {
                        JOptionPane.showMessageDialog(RegisterFrame.this,
                                LoginFrame.friendlyError(cause instanceof Exception ? (Exception) cause : new Exception(cause)),
                                "Registration unavailable", JOptionPane.WARNING_MESSAGE);
                    }
                } finally {
                    Arrays.fill(password, '\0');
                    passwordField.setText("");
                    confirmField.setText("");
                }
            }
        }.execute();
    }

    private void goBack() {
        dispose();
        if (loginFrame != null) loginFrame.setVisible(true);
    }

    private void showError(String message) {
        errorLabel.setText("<html><div style='text-align:center;'>" + message + "</div></html>");
        errorLabel.setForeground(LoginFrame.ERROR);
    }

    private static int strength(String password) {
        if (password.isEmpty()) return 0;
        if (password.length() < 8) return 1;
        int score = 1;
        if (password.matches(".*[a-z].*") && password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*\\d.*")) score++;
        if (password.matches(".*[^A-Za-z0-9].*")) score++;
        return score;
    }

    private static String strengthWord(int score) {
        switch (score) {
            case 1: return "Weak";
            case 2: return "Fair";
            case 3: return "Good";
            default: return "Strong";
        }
    }

    private static JLabel hint() {
        JLabel l = new JLabel(" ");
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(LoginFrame.MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        return l;
    }

    private static void setHint(JLabel label, String text, Color color) {
        label.setText(text);
        label.setForeground(color);
    }

    private static void onChange(JTextField field, Runnable runnable) {
        DocumentListener listener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { runnable.run(); }
            @Override public void removeUpdate(DocumentEvent e) { runnable.run(); }
            @Override public void changedUpdate(DocumentEvent e) { runnable.run(); }
        };
        field.getDocument().addDocumentListener(listener);
    }

    private static JButton showHideToggle(JPasswordField field) {
        JButton toggle = new JButton("Show");
        toggle.putClientProperty("JButton.buttonType", "borderless");
        toggle.setForeground(LoginFrame.ACCENT);
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

    private static void addField(JPanel panel, String caption, JTextField field, JLabel hintLabel, JButton toggle) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        row.add(LoginFrame.label(caption), BorderLayout.WEST);
        if (toggle != null) row.add(toggle, BorderLayout.EAST);
        panel.add(row);
        panel.add(Box.createVerticalStrut(6));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(field);
        if (hintLabel != null) {
            panel.add(Box.createVerticalStrut(4));
            panel.add(hintLabel);
        }
        panel.add(Box.createVerticalStrut(12));
    }

    /** Four-segment password strength indicator. */
    private static final class StrengthBar extends JComponent {
        private static final Color[] COLORS = {
                new Color(211, 68, 94), new Color(214, 138, 45),
                new Color(196, 160, 40), new Color(46, 143, 97)
        };
        private int score;

        void setScore(int score) {
            this.score = score;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int gap = 4, segmentHeight = 6;
            int width = (getWidth() - gap * 3) / 4;
            Color active = score > 0 ? COLORS[Math.min(score, COLORS.length) - 1] : null;
            for (int i = 0; i < 4; i++) {
                g2.setColor(i < score ? active : new Color(226, 217, 243));
                g2.fillRoundRect(i * (width + gap), 0, width, segmentHeight, 6, 6);
            }
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() { return new Dimension(330, 6); }

        @Override
        public Dimension getMinimumSize() { return new Dimension(120, 6); }

        @Override
        public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 6); }
    }
}
