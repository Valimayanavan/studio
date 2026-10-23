package org.example.studio_admin;

import jakarta.annotation.PostConstruct;
import org.example.studio_admin.models.connectionDb;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Creates all MySQL tables on startup from db/schema.sql
 * (every statement is CREATE TABLE IF NOT EXISTS, so this is safe
 * to run on every boot) and optionally seeds a first admin user.
 */
@Component
public class DatabaseInitializer {

    private final PasswordEncoder passwordEncoder;

    public DatabaseInitializer(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }


    @PostConstruct
    public void init() {

        try (Connection con = connectionDb.getConnection()) {

            runSchema(con);
            seedAdmin(con);

            System.out.println("MySQL schema ready");

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Could not initialise the MySQL database. " +
                            "Check DB_URL / DB_HOST / DB_PORT / DB_NAME / " +
                            "DB_USER / DB_PASSWORD.", e
            );
        }
    }


    private void runSchema(Connection con) throws Exception {

        String script;

        try (InputStream in =
                     new ClassPathResource("db/schema.sql").getInputStream()) {

            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Drop "-- comment" lines, then split on ';'
        StringBuilder cleaned = new StringBuilder();

        for (String line : script.split("\n")) {
            if (!line.trim().startsWith("--")) {
                cleaned.append(line).append('\n');
            }
        }

        try (Statement st = con.createStatement()) {

            for (String sql : cleaned.toString().split(";")) {

                if (!sql.trim().isEmpty()) {
                    st.execute(sql);
                }
            }
        }
    }


    private void seedAdmin(Connection con) throws Exception {

        String user = System.getenv("ADMIN_USERNAME");
        String pass = System.getenv("ADMIN_PASSWORD");

        if (user == null || user.isBlank() ||
                pass == null || pass.isBlank()) {
            return;
        }

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM admin")) {

            rs.next();

            if (rs.getInt(1) > 0) {
                return;
            }
        }

        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO admin (UserName, Password) VALUES (?, ?)")) {

            ps.setString(1, user);
            ps.setString(2, passwordEncoder.encode(pass));
            ps.executeUpdate();
        }

        System.out.println("Seeded admin user '" + user + "'");
    }
}
