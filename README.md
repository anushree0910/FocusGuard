# FocusGuard — Focus & Productivity Desktop App

A Java Swing desktop application for running focus sessions, managing tasks, and reviewing productivity — with account login, a personal profile page, weekly analytics, a journal, and CSV export. All persistent data is stored in MySQL.

```
Login → Dashboard → Focus Timer / Tasks / History / Analytics / Journal / Export / Profile → Logout
```

---

## 1. Feature Overview

### Authentication
| Feature | Where |
|---|---|
| Sign in with email + password, inline error messages, "Remember my email" | `LoginFrame` |
| Account creation with live email validation, password-strength meter, confirm-password check, duplicate-email handling | `RegisterFrame` |
| Show/Hide password toggle on all password fields | both auth windows |
| Session state (user id, name, email, first name, initials) held in memory | `UserSession` |
| Log out from Profile → clears session and returns to the sign-in window | `ProfilePanel` → `Main.logoutToLogin()` |

### Workspace (sidebar pages)
| Page | What it does |
|---|---|
| **Dashboard** | Greeting personalized with the signed-in user's first name, today's stats (focus time, sessions, tasks completed), quick links into Focus and Tasks |
| **Focus Timer** | Countdown timer with progress ring, Pomodoro/Study modes, custom durations, pause/resume/stop; sessions are saved to MySQL and survive mid-session stops |
| **My Tasks** | Add/edit tasks with priority and deadline, pill filters (All / Pending / Completed / Overdue), status toggling |
| **Focus History** | Record of sessions started today with totals for focus time and completed sessions |
| **Weekly Analytics** | Last-7-days bar chart, total focus time, completed sessions, daily average, most productive day (`WeeklyAnalyticsPanel`) |
| **Focus Journal** | Write and save reflection entries; history shown as selectable cards; stored per-user as plain text under `data/focus-journal/` |
| **Data Export** | Session-history table with a real CSV export via file chooser |
| **My Profile** | Tabbed page: **Overview** (account card + 26-week focus contribution grid + live progress stats), **Activity** (latest sessions), **Tasks** (completed/pending/overdue + progress bar), **Achievements** (8 badges computed from real history), **Account settings** (edit name/email, change password, log out) |

---

## 2. Project Structure

```
FocusGuard-main/
├── pom.xml                          Maven build (JDK 25, FlatLaf + MySQL connector)
├── data/focus-journal/              Journal text files (journal_user_<id>.txt)
└── src/main/java/com/focusguard/
    ├── Main.java                    Entry point, dashboard shell, timer, tasks, history
    ├── LoginFrame.java              Sign-in window (opens dashboard on success)
    ├── RegisterFrame.java           Account creation window
    ├── ProfilePanel.java            Tabbed profile page (live data + account settings)
    ├── WeeklyAnalyticsPanel.java    7-day analytics chart and stat cards
    ├── FocusJournalPanel.java       Journal writing/reading (file-based)
    ├── DataExportPanel.java         Session table + CSV export
    ├── RoundedPanel.java            Graphics helper: rounded/gradient panels
    ├── UserSession.java             Static in-memory record of the signed-in user
    ├── UserDAO.java                 MySQL: register/login/profile/change-password
    ├── SessionDAO.java              MySQL: focus_sessions CRUD
    ├── TaskDAO.java                 MySQL: tasks CRUD
    ├── DBConnection.java            JDBC URL / credentials (EDIT THIS)
    └── DBTest.java                  Manual connectivity test
```

### Layering
- **UI** (`Main`, frames, panels) never talks to JDBC directly.
- **DAOs** (`UserDAO`, `SessionDAO`, `TaskDAO`) own all SQL, use `PreparedStatement` everywhere (no string-concatenated queries → no SQL injection).
- **`UserSession`** is the single source of truth for "who is logged in".

---

## 3. Security & Data Design

- **Passwords** are never stored in plain text. Format in `users.password_hash`:
  `pbkdf2$<iterations>$<hexSalt>$<hexHash>` — PBKDF2-HMAC-SHA256, 210,000 iterations, 16-byte random salt per user, 256-bit key; verification uses a constant-time comparison.
- Password `char[]` buffers are wiped after use.
- All database access is parameterized; duplicate registration is detected via MySQL error 1062 / SQLState 23000 as well as a pre-check.
- Long-running work (MySQL queries, PBKDF2 hashing ≈ 1 s) runs on `SwingWorker` background threads so the UI never freezes; buttons disable with "Signing in…" / "Saving…" states.
- "Remember my email" uses `java.util.prefs.Preferences` (Windows registry-backed), storing only the email, never the password.

### MySQL schema (`focusguard_db`)
| Table | Columns (key ones) |
|---|---|
| `users` | `user_id`, `full_name`, `email` (UNIQUE), `password_hash`, `created_at` |
| `focus_sessions` | `session_id`, `mode`, `technique`, `start_time`, `end_time`, `duration_minutes`, `elapsed_seconds`, `status` (Active/Paused/Completed/Stopped) |
| `tasks` | `task_id`, `title`, `description`, `priority` (Low/Medium/High), `status` (Pending/Completed), `due_date`, `created_at` |

> **Known limitation:** `focus_sessions` and `tasks` have **no `user_id` column** — all accounts share the same tasks/sessions/analytics. See Future Enhancements.

---

## 4. Setup

### Prerequisites
- **JDK 17+** (developed on JDK 25)
- **MySQL 8** running on `localhost:3306`
- Maven (optional — plain `javac` also works)

### 1. Create the database
```sql
CREATE DATABASE IF NOT EXISTS focusguard_db;
```
Then let the app's tables exist (create them with the columns listed above, or run your friend's SQL script). `due_date` on `tasks` is required by the Tasks page.

### 2. Configure credentials
Edit `src/main/java/com/focusguard/DBConnection.java`:
```java
private static final String USER = "root";
private static final String PASSWORD = "<your mysql password>";
```
⚠️ Do not commit a real password to a shared repository.

### 3. Run

**With Maven:**
```bash
mvn compile exec:java -Dexec.mainClass=com.focusguard.Main
```

**Without Maven (javac + jars from the local repo):**
```bash
javac -encoding UTF-8 -cp "<HOME>/.m2/repository/com/formdev/flatlaf/3.7.2/flatlaf-3.7.2.jar;<HOME>/.m2/repository/com/mysql/mysql-connector-j/9.4.0/mysql-connector-j-9.4.0.jar" -d out src/main/java/com/focusguard/*.java

java -cp "out;<same two jars>" com.focusguard.Main
```

Dependencies are only **FlatLaf 3.7.2** (light theme) and **mysql-connector-j 9.4.0**. (JNA was removed along with the distraction-monitor feature.)

### 4. First launch
1. Register an account (or sign in if `users` already has PBKDF2-hashed rows).
2. Everything else lives behind the sidebar.

---

## 5. UI Theme

Light lavender design shared across every screen:
background `#F7F4FF`, cards white with `#E2D9F3` borders, primary purple `#7655C9` / accent `#8A6AD6`, pale chip `#E9DFFF`, text `#302747`, muted `#827A96`, error `#D3445E`, success `#2E8F61`. FlatLaf is configured with rounded arcs (12 px) and a custom focus color; `RoundedPanel` provides rounded/gradient containers.

---

## 6. Future Enhancements

### High value, small effort
- **Per-user data**: add `user_id` to `tasks` and `focus_sessions`, filter every query by the signed-in user. (Also fixes the shared-analytics limitation.)
- **Editing journal entries / deleting entries** (currently append + view only).
- **Task reminders & notifications** — due-today/overdue badges on the dashboard, optional Windows toast via `java.awt.TrayIcon`.
- **Session notes**: attach a reflection to each focus session, shown in History.
- **"Member since" + avatar upload** on the profile summary (users.created_at already exists).
- **Dark mode toggle** (theme colors are already centralized).

### Medium effort
- **Achievements expansion**: streak freezes, monthly goals, leaderboard-style personal bests; persist unlock dates in a new table.
- **Export tasks + journal to CSV/JSON**, and a full backup/restore (mysqldump wrapper or JSON export of all tables).
- **Scheduled break prompts** between Pomodoro cycles with a lockable "break" UI.
- **Date-range analytics**: month/quarter views, heatmap calendar, tag-based filtering (by mode/technique).
- **Recurring tasks** (daily/weekly templates that auto-spawn).
- **Search & filter** in journal and history lists.

### Larger projects
- **Bring back the distraction guard without native code**: poll the foreground app via a small PowerShell helper process (no JNA/HWND in Java), or a "strict mode" that just blocks preconfigured app names via `taskkill` with user confirmation.
- **Cloud sync / multi-device** using a REST backend instead of direct MySQL.
- **Unit + integration tests** (JUnit) with Testcontainers or an embedded H2/MySQL mode.
- **Installer**: `jpackage` app-image so users don't need Java installed.
- **Accessibility pass**: keyboard navigation audit, screen-reader labels, adjustable font scale.

---

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| "Database unavailable" / login hangs then errors | MySQL not running, or wrong password in `DBConnection.java`. Test with `java com.focusguard.DBTest` |
| `Unknown column 'due_date'` | Run `ALTER TABLE tasks ADD COLUMN due_date DATE NULL;` |
| IDE: `package com.formdev.flatlaf does not exist` | Re-sync Maven (the jars + poms must be in `~/.m2`), or add the two jars as project libraries |
| Profile tabs show "Data unavailable" | Same DB issue — the page retries automatically each time you open it |
| Old friend-created accounts can't log in | Their `password_hash` may predate the PBKDF2 format; re-register or reset via `UserDAO.changePassword` |
