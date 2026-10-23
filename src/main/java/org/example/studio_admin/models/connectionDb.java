package org.example.studio_admin.models;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class connectionDb {

    // Fallbacks from application.properties (set by DatabaseInitializer)
    private static volatile String cfgUrl = null;
    private static volatile String cfgHost = "switchyard.proxy.rlwy.net";
    private static volatile String cfgPort = "28796";
    private static volatile String cfgName = "railway";
    private static volatile String cfgUser = "root";
    private static volatile String cfgPassword = "rMiTiDvQhOacfYyFCzhLeJaoYyuUjmTJ";

    public static void configure(String url, String host, String port, String name, String user, String password) {
        if (url != null && !url.isBlank()) cfgUrl = url;
        if (host != null && !host.isBlank()) cfgHost = host;
        if (port != null && !port.isBlank()) cfgPort = port;
        if (name != null && !name.isBlank()) cfgName = name;
        if (user != null && !user.isBlank()) cfgUser = user;
        cfgPassword = password == null ? "" : password;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url(), user(), password());
    }

    public static String url() {
        // Check environment variables first, then fallback to cfgUrl from application.properties
        String fullUrl = getEnvWithDefault(new String[]{"MYSQL_URL", "DB_URL", "DATABASE_URL"}, cfgUrl);
        if (fullUrl != null && !fullUrl.isBlank()) {
            if (!fullUrl.startsWith("jdbc:")) {
                fullUrl = "jdbc:" + fullUrl;
            }
            return fullUrl;
        }

        String host = getEnvWithDefault(new String[]{"MYSQLHOST", "DB_HOST"}, cfgHost);
        String port = getEnvWithDefault(new String[]{"MYSQLPORT", "DB_PORT"}, cfgPort);
        String name = getEnvWithDefault(new String[]{"MYSQLDATABASE", "DB_NAME"}, cfgName);

        return "jdbc:mysql://" + host + ":" + port + "/" + name +
                "?createDatabaseIfNotExist=true" +
                "&useUnicode=true" +
                "&characterEncoding=UTF-8" +
                "&connectionTimeZone=UTC" +
                "&forceConnectionTimeZoneToSession=true" +
                "&allowPublicKeyRetrieval=true" +
                "&sslMode=DISABLED";
    }

    public static String user() {
        return getEnvWithDefault(new String[]{"MYSQLUSER", "DB_USER", "DB_USERNAME"}, cfgUser);
    }

    public static String password() {
        return getEnvWithDefault(new String[]{"MYSQLPASSWORD", "DB_PASSWORD"}, cfgPassword);
    }

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
}