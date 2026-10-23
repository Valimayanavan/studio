package org.example.studio_admin.models;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class connectionDb {

    private static final String URL = buildUrl();

    private static final String USER = getEnvWithDefault(new String[]{"MYSQLUSER", "DB_USER"}, "root");

    private static final String PASSWORD = getEnvWithDefault(new String[]{"MYSQLPASSWORD", "DB_PASSWORD"}, "rMiTiDvQhOacfYyFCzhLeJaoYyuUjmTJ");

    private static String buildUrl() {
        // Check full URL env vars first (Railway or custom)
        String fullUrl = getEnvWithDefault(new String[]{"MYSQL_URL", "DB_URL"}, null);
        if (fullUrl != null && !fullUrl.isBlank()) {
            if (!fullUrl.startsWith("jdbc:")) {
                fullUrl = "jdbc:" + fullUrl;
            }
            return fullUrl;
        }

        String host = getEnvWithDefault(new String[]{"MYSQLHOST", "DB_HOST"}, "switchyard.proxy.rlwy.net");
        String port = getEnvWithDefault(new String[]{"MYSQLPORT", "DB_PORT"}, "28796");
        String name = getEnvWithDefault(new String[]{"MYSQLDATABASE", "DB_NAME"}, "railway");

        // connectionTimeZone=UTC + forceConnectionTimeZoneToSession makes
        // CURRENT_TIMESTAMP default columns store UTC.
        return "jdbc:mysql://" + host + ":" + port + "/" + name +
                "?useUnicode=true" +
                "&characterEncoding=UTF-8" +
                "&connectionTimeZone=UTC" +
                "&forceConnectionTimeZoneToSession=true" +
                "&allowPublicKeyRetrieval=true" +
                "&sslMode=DISABLED";
    }

    // Helper method to check multiple environment variable keys, falling back to a default value
    private static String getEnvWithDefault(String[] keys, String defaultValue) {
        for (String key : keys) {
            if (key == null) continue;
            String value = System.getenv(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return defaultValue;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}