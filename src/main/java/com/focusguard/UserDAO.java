package com.focusguard;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.sql.*;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Authentication/profile data access. Assumes DBConnection.getConnection()
 * returns a java.sql.Connection to the FocusGuard MySQL database.
 */
public class UserDAO {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    public static boolean isEmailValid(String email) {
        return email != null && !email.isEmpty() && email.length() <= 150 && email.matches(EMAIL_REGEX);
    }

    public static final class User {
        public final int id;
        public final String fullName;
        public final String email;

        public User(int id, String fullName, String email) {
            this.id = id;
            this.fullName = fullName;
            this.email = email;
        }
    }

    public User register(String fullName, String email, char[] password) throws Exception {
        String cleanName = fullName == null ? "" : fullName.trim();
        String cleanEmail = email == null ? "" : email.trim().toLowerCase();
        validate(cleanName, cleanEmail, password);

        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        String hash = hashPassword(password, salt);

        String sql = "INSERT INTO users (full_name, email, password_hash) VALUES (?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cleanName);
            ps.setString(2, cleanEmail);
            ps.setString(3, "pbkdf2$" + ITERATIONS + "$" + toHex(salt) + "$" + hash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return new User(keys.getInt(1), cleanName, cleanEmail);
            }
            throw new SQLException("Account was created, but its ID could not be retrieved.");
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1062 || "23000".equals(ex.getSQLState())) {
                throw new IllegalArgumentException("An account with this email already exists.");
            }
            throw ex;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public User login(String email, char[] password) throws Exception {
        String cleanEmail = email == null ? "" : email.trim().toLowerCase();
        if (cleanEmail.isEmpty() || password == null || password.length == 0) {
            throw new IllegalArgumentException("Enter your email and password.");
        }

        String sql = "SELECT user_id, full_name, email, password_hash FROM users WHERE email = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, cleanEmail);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !verifyPassword(password, rs.getString("password_hash"))) {
                    throw new IllegalArgumentException("Incorrect email or password.");
                }
                return new User(rs.getInt("user_id"), rs.getString("full_name"), rs.getString("email"));
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public boolean existsByEmail(String email) throws SQLException {
        String cleanEmail = email == null ? "" : email.trim().toLowerCase();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM users WHERE email = ?")) {
            ps.setString(1, cleanEmail);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public User getById(int userId) throws SQLException {
        String sql = "SELECT user_id, full_name, email FROM users WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new User(rs.getInt("user_id"), rs.getString("full_name"),
                        rs.getString("email")) : null;
            }
        }
    }

    public void updateProfile(int userId, String fullName, String email) throws Exception {
        String name = fullName == null ? "" : fullName.trim();
        String cleanEmail = email == null ? "" : email.trim().toLowerCase();
        if (name.isEmpty() || name.length() > 100) {
            throw new IllegalArgumentException("Name must contain 1–100 characters.");
        }
        if (!isEmailValid(cleanEmail)) {
            throw new IllegalArgumentException("Enter a valid email address (maximum 150 characters).");
        }
        String sql = "UPDATE users SET full_name = ?, email = ? WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, cleanEmail);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1062 || "23000".equals(ex.getSQLState())) {
                throw new IllegalArgumentException("That email address is already in use.");
            }
            throw ex;
        }
        UserSession.setUser(userId, name, cleanEmail);
    }

    public void changePassword(int userId, char[] currentPassword, char[] newPassword) throws Exception {
        try {
            if (newPassword == null || newPassword.length < 8) {
                throw new IllegalArgumentException("New password must be at least 8 characters.");
            }
            String currentHash;
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement("SELECT password_hash FROM users WHERE user_id = ?")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("User account was not found.");
                    currentHash = rs.getString(1);
                }
            }
            if (!verifyPassword(currentPassword, currentHash)) {
                throw new IllegalArgumentException("Current password is incorrect.");
            }

            byte[] salt = new byte[SALT_BYTES];
            RANDOM.nextBytes(salt);
            String encoded = "pbkdf2$" + ITERATIONS + "$" + toHex(salt) + "$" + hashPassword(newPassword, salt);
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement("UPDATE users SET password_hash = ? WHERE user_id = ?")) {
                ps.setString(1, encoded);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
        } finally {
            if (currentPassword != null) Arrays.fill(currentPassword, '\0');
            if (newPassword != null) Arrays.fill(newPassword, '\0');
        }
    }

    private static void validate(String name, String email, char[] password) {
        if (name.isEmpty() || name.length() > 100)
            throw new IllegalArgumentException("Name must contain 1–100 characters.");
        if (!isEmailValid(email))
            throw new IllegalArgumentException("Enter a valid email address.");
        if (password == null || password.length < 8)
            throw new IllegalArgumentException("Password must be at least 8 characters.");
    }

    private static String hashPassword(char[] password, byte[] salt) throws Exception {
        return hashPasswordWithIterations(password, salt, ITERATIONS);
    }

    private static boolean verifyPassword(char[] password, String encoded) throws Exception {
        if (password == null || encoded == null) return false;
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) return false;
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = fromHex(parts[2]);
        byte[] expected = fromHex(parts[3]);
        byte[] actual = fromHex(hashPasswordWithIterations(password, salt, iterations));
        if (actual.length != expected.length) return false;
        int diff = 0;
        for (int i = 0; i < actual.length; i++) diff |= actual[i] ^ expected[i];
        return diff == 0;
    }

    private static String hashPasswordWithIterations(char[] password, byte[] salt, int iterations) throws Exception {
        KeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS);
        return toHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded());
    }

    private static String toHex(byte[] data) {
        StringBuilder out = new StringBuilder(data.length * 2);
        for (byte b : data) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }

    private static byte[] fromHex(String value) {
        if (value.length() % 2 != 0) throw new IllegalArgumentException("Invalid password hash.");
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
}
