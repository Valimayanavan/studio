package org.example.studio_admin;

import jakarta.annotation.PostConstruct;
import org.example.studio_admin.models.connectionDb;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Component
public class DatabaseInitializer {

    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public DatabaseInitializer(PasswordEncoder passwordEncoder, Environment environment) {
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        // Hand over db.* values from application.properties to connectionDb
        connectionDb.configure(
                environment.getProperty("db.url"),
                environment.getProperty("db.host"),
                environment.getProperty("db.port"),
                environment.getProperty("db.name"),
                environment.getProperty("db.username"),
                environment.getProperty("db.password")
        );

        try (Connection con = connectionDb.getConnection();
             Statement st = con.createStatement();
             InputStream in = new ClassPathResource("db/schema.sql").getInputStream()) {

            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            StringBuilder cleaned = new StringBuilder();
            for (String line : script.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("--") && !trimmed.isEmpty()) {
                    cleaned.append(line).append('\n');
                }
            }

            int created = 0;
            for (String statement : cleaned.toString().split(";")) {
                String sql = statement.trim();
                if (!sql.isEmpty()) {
                    st.execute(sql);
                    created++;
                }
            }

            migrateCustomerPhotoFolderImages(con);
            ensureColumn(con, "customer_images", "uploaded_by", "VARCHAR(20) NOT NULL DEFAULT 'STUDIO'");
            ensureIndex(con, "customer_images", "idx_images_customer_uploader", "(customer_id, uploaded_by)");
            ensureColumn(con, "subscription", "trial_start_date", "DATE NULL");
            ensureColumn(con, "subscription", "trial_end_date", "DATE NULL");
            ensureColumn(con, "subscription", "studio_ID", "VARCHAR(191) NULL");
            migrateCustomerPhotoFoldersToAlbums(con);
            seedAdmin(con);

            System.out.println("=================================");
            System.out.println("MySQL database ready (" + created + " statements executed)");
            System.out.println("=================================");

        } catch (Exception e) {
            System.err.println("=================================");
            System.err.println("COULD NOT CONNECT TO / PREPARE MYSQL");
            System.err.println("URL      : " + connectionDb.url());
            System.err.println("User     : " + connectionDb.user());
            System.err.println("Check that MySQL is running and that DB_URL / DB_HOST / DB_PORT / DB_NAME /");
            System.err.println("DB_USER / DB_PASSWORD (or db.* in application.properties) are correct.");
            System.err.println("=================================");
            throw new IllegalStateException("Unable to initialize or migrate the MySQL database.", e);
        }
    }

    private void seedAdmin(Connection con) throws Exception {
        String user = System.getenv("ADMIN_USERNAME");
        String pass = System.getenv("ADMIN_PASSWORD");

        if (user == null || user.isBlank() || pass == null || pass.isBlank()) {
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

    private void migrateCustomerPhotoFoldersToAlbums(Connection connection) throws Exception {
        final int migrationVersion = 1;

        try (PreparedStatement check = connection.prepareStatement(
                "SELECT 1 FROM schema_migrations WHERE version = ?")) {
            check.setInt(1, migrationVersion);
            try (ResultSet result = check.executeQuery()) {
                if (result.next()) {
                    return;
                }
            }
        }

        connection.setAutoCommit(false);
        try {
            List<LegacyFolder> folders = new ArrayList<>();
            String folderSql = """
                    SELECT f.folder_id, f.customer_id, f.folder_name, f.token,
                           fi.image_name, fi.image_data
                    FROM customer_photo_folders f
                    LEFT JOIN customer_photo_folder_images fi ON fi.folder_id = f.folder_id
                    ORDER BY f.folder_id, fi.image_id
                    """;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(folderSql)) {
                while (result.next()) {
                    int folderId = result.getInt("folder_id");
                    LegacyFolder folder = folders.isEmpty()
                            ? null
                            : folders.get(folders.size() - 1);
                    if (folder == null || folder.id() != folderId) {
                        folder = new LegacyFolder(
                                folderId,
                                result.getInt("customer_id"),
                                result.getString("folder_name"),
                                result.getString("token"),
                                new ArrayList<>()
                        );
                        folders.add(folder);
                    }

                    String imageName = result.getString("image_name");
                    if (imageName != null) {
                        folder.images().add(new LegacyFolderImage(imageName, result.getBytes("image_data")));
                    }
                }
            }

            for (LegacyFolder folder : folders) {
                int albumId = findOrCreateAlbum(connection, folder);
                for (LegacyFolderImage image : folder.images()) {
                    int imageId;
                    try (PreparedStatement insertImage = connection.prepareStatement(
                            """
                            INSERT INTO customer_images (customer_id, image_name, image_data, uploaded_by)
                            VALUES (?, ?, ?, 'CUSTOMER')
                            """,
                            Statement.RETURN_GENERATED_KEYS)) {
                        insertImage.setInt(1, folder.customerId());
                        insertImage.setString(2, image.name());
                        insertImage.setBytes(3, image.data());
                        insertImage.executeUpdate();
                        try (ResultSet keys = insertImage.getGeneratedKeys()) {
                            if (!keys.next()) {
                                throw new IllegalStateException("Could not migrate a legacy album photo.");
                            }
                            imageId = keys.getInt(1);
                        }
                    }

                    try (PreparedStatement linkImage = connection.prepareStatement(
                            "INSERT INTO customer_album_images (album_id, image_id) VALUES (?, ?)")) {
                        linkImage.setInt(1, albumId);
                        linkImage.setInt(2, imageId);
                        linkImage.executeUpdate();
                    }
                }
            }

            try (PreparedStatement mark = connection.prepareStatement(
                    "INSERT INTO schema_migrations (version, description) VALUES (?, ?)")) {
                mark.setInt(1, migrationVersion);
                mark.setString(2, "Move legacy customer photo folders into albums and gallery images");
                mark.executeUpdate();
            }
            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private int findOrCreateAlbum(Connection connection, LegacyFolder folder) throws Exception {
        try (PreparedStatement find = connection.prepareStatement(
                "SELECT album_id FROM customer_albums WHERE token = ?")) {
            find.setString(1, folder.token());
            try (ResultSet result = find.executeQuery()) {
                if (result.next()) {
                    return result.getInt("album_id");
                }
            }
        }

        try (PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO customer_albums (customer_id, album_name, token)
                VALUES (?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setInt(1, folder.customerId());
            insert.setString(2, folder.name());
            insert.setString(3, folder.token());
            insert.executeUpdate();
            try (ResultSet keys = insert.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new IllegalStateException("Could not migrate a legacy customer album.");
                }
                return keys.getInt(1);
            }
        }
    }

    private record LegacyFolder(
            int id,
            int customerId,
            String name,
            String token,
            List<LegacyFolderImage> images
    ) { }

    private record LegacyFolderImage(String name, byte[] data) { }

    private void migrateCustomerPhotoFolderImages(Connection connection) throws Exception {
        String table = "customer_photo_folder_images";
        if (!tableExists(connection, table)
                || (columnExists(connection, table, "image_name")
                && columnExists(connection, table, "image_data"))) {
            return;
        }

        if (!columnExists(connection, table, "folder_id")
                || !columnExists(connection, table, "image_id")) {
            throw new IllegalStateException(
                    "The existing customer photo folder table has an unsupported schema."
            );
        }

        String legacyTable = "customer_photo_folder_images_legacy";
        if (tableExists(connection, legacyTable)) {
            throw new IllegalStateException(
                    "Both the old and migrated customer photo folder tables exist; manual database review is required."
            );
        }

        List<String> foreignKeys = new ArrayList<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet keys = metadata.getImportedKeys(connection.getCatalog(), null, table)) {
            while (keys.next()) {
                foreignKeys.add(keys.getString("FK_NAME"));
            }
        }

        try (Statement statement = connection.createStatement()) {
            for (String foreignKey : foreignKeys) {
                if (foreignKey != null) {
                    statement.execute(
                            "ALTER TABLE `" + table + "` DROP FOREIGN KEY `" + foreignKey.replace("`", "```") + "`"
                    );
                }
            }
            statement.execute("RENAME TABLE `" + table + "` TO `" + legacyTable + "`");
            statement.execute("""
                    CREATE TABLE `customer_photo_folder_images` (
                        image_id    INT          NOT NULL AUTO_INCREMENT,
                        folder_id   INT          NOT NULL,
                        image_name  VARCHAR(255) NOT NULL,
                        image_data  LONGBLOB     NOT NULL,
                        PRIMARY KEY (image_id),
                        KEY idx_folderimages_folder (folder_id),
                        CONSTRAINT fk_folderimages_folder FOREIGN KEY (folder_id)
                            REFERENCES `customer_photo_folders` (folder_id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.execute("""
                    INSERT INTO `customer_photo_folder_images` (folder_id, image_name, image_data)
                    SELECT legacy.folder_id, images.image_name, images.image_data
                    FROM `customer_photo_folder_images_legacy` legacy
                    JOIN `customer_photo_folders` folders
                      ON folders.folder_id = legacy.folder_id
                    JOIN `customer_images` images
                      ON images.image_id = legacy.image_id
                     AND images.customer_id = folders.customer_id
                    """);
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (ResultSet columns = connection.getMetaData().getColumns(
                connection.getCatalog(), null, table, column)) {
            return columns.next();
        }
    }

    private void ensureColumn(
            Connection connection,
            String table,
            String column,
            String definition
    ) throws Exception {
        String existsSql = """
                SELECT 1
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(existsSql)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return;
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
        }
    }

    private void ensureIndex(
            Connection connection,
            String table,
            String index,
            String columns
    ) throws Exception {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX `" + index + "` ON `" + table + "` " + columns);
        }
    }
}