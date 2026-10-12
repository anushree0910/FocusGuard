
package com.focusguard;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/focusguard_db";

    private static final String USER = "root";

    // Replace this with your MySQL password
    private static final String PASSWORD = "Avdrocks@0504";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
