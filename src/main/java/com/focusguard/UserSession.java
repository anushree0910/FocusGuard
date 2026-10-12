package com.focusguard;

/** In-memory identity of the user who authenticated in this application run. */
public final class UserSession {
    private static int userId = -1;
    private static String fullName;
    private static String email;
    private static String firstName = "";
    private static String initials = "";

    private UserSession() {}

    public static synchronized void setUser(int id, String name, String userEmail) {
        userId = id;
        fullName = name;
        email = userEmail;
        String trimmed = name == null ? "" : name.trim();
        firstName = trimmed.isEmpty() ? "" : trimmed.split("\\s+")[0];
        StringBuilder initialsBuilder = new StringBuilder();
        for (String word : trimmed.split("\\s+")) {
            if (!word.isEmpty()) initialsBuilder.append(Character.toUpperCase(word.charAt(0)));
            if (initialsBuilder.length() == 2) break;
        }
        initials = initialsBuilder.toString();
    }

    public static synchronized boolean isLoggedIn() { return userId > 0; }
    public static synchronized int getUserId() { return userId; }
    public static synchronized String getFullName() { return fullName; }
    public static synchronized String getEmail() { return email; }
    public static synchronized String getFirstName() { return firstName; }
    public static synchronized String getInitials() { return initials; }

    public static synchronized void clear() {
        userId = -1;
        fullName = null;
        email = null;
        firstName = "";
        initials = "";
    }
}
