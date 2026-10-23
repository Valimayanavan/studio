package org.example.studio_admin;

import jakarta.servlet.http.HttpSession;
import org.example.studio_admin.models.connectionDb;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Controller
public class customer_home_controller {

    private final connectionDb db = new connectionDb();


    // =========================================================
    // LOGIN CHECK
    // =========================================================

    private boolean isLoggedIn(HttpSession session) {
        return session.getAttribute("studio_ID") != null;
    }
// =========================================================
// DOWNLOAD ALBUM IMAGE
// /download_album_image/{imageId}
// =========================================================

    @GetMapping("/download_album_image/{imageId}")
    public ResponseEntity<byte[]> downloadAlbumImage(
            @PathVariable("imageId") int imageId,
            HttpSession session) {

        if (!isLoggedIn(session)) {
            return ResponseEntity.status(302).build();
        }

        try (Connection con = db.getConnection()) {

            // Only the studio that owns the customer may download the photo
            String sql =
                    "SELECT ci.image_name, ci.image_data " +
                            "FROM customer_images ci " +
                            "JOIN customer c " +
                            "ON c.customer_id = ci.customer_id " +
                            "WHERE ci.image_id = ? " +
                            "AND c.studio_ID = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setInt(1, imageId);

                ps.setString(
                        2,
                        String.valueOf(session.getAttribute("studio_ID"))
                );

                try (ResultSet rs = ps.executeQuery()) {

                    if (rs.next()) {

                        byte[] data =
                                rs.getBytes("image_data");

                        String imageName =
                                rs.getString("image_name");

                        if (imageName == null ||
                                imageName.trim().isEmpty()) {

                            imageName = "image_" +
                                    imageId +
                                    ".jpg";
                        }

                        // keep the header valid: no quotes / line breaks / slashes
                        imageName = imageName
                                .replaceAll("[\\r\\n\"\\\\/]", "_");

                        return ResponseEntity.ok()
                                .header(
                                        "Content-Disposition",
                                        "attachment; filename=\"" +
                                                imageName +
                                                "\""
                                )
                                .contentType(
                                        MediaType.APPLICATION_OCTET_STREAM
                                )
                                .body(data);
                    }
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

        return ResponseEntity
                .notFound()
                .build();
    }

    // =========================================================
    // CUSTOMER HOME
    // =========================================================

    @GetMapping("/customer_home")
    public String customerHome(
            Model model,
            HttpSession session) {

        if (!isLoggedIn(session)) {
            return "redirect:/studio_login";
        }

        String studioId = String.valueOf(session.getAttribute("studio_ID"));
        List<Map<String, Object>> customers = new ArrayList<>();

        try (Connection con = db.getConnection()) {

            String sql = "SELECT customer_id, customer_name, phone, address, studio_ID " +
                    "FROM customer WHERE studio_ID = ? ORDER BY customer_id DESC";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, studioId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> customer = new HashMap<>();
                        int customerId = rs.getInt("customer_id");

                        customer.put("customer_id", customerId);
                        customer.put("customer_name", rs.getString("customer_name"));
                        customer.put("phone", rs.getString("phone"));
                        customer.put("address", rs.getString("address"));
                        customer.put("studio_ID", rs.getString("studio_ID"));

                        // LOAD CUSTOMER IMAGES
                        List<Map<String, Object>> images = new ArrayList<>();
                        String imageSql = "SELECT image_id, image_name FROM customer_images " +
                                "WHERE customer_id = ? ORDER BY image_id";

                        try (PreparedStatement imagePs = con.prepareStatement(imageSql)) {
                            imagePs.setInt(1, customerId);

                            try (ResultSet imageRs = imagePs.executeQuery()) {
                                while (imageRs.next()) {
                                    Map<String, Object> image = new HashMap<>();
                                    image.put("image_id", imageRs.getInt("image_id"));
                                    image.put("image_name", imageRs.getString("image_name"));
                                    images.add(image);
                                }
                            }
                        }

                        customer.put("images", images);
                        customers.add(customer);
                    }
                }
            }

            // Pass attributes to model
            model.addAttribute("customers", customers);
            model.addAttribute("studio_ID", studioId);
            model.addAttribute("studio_name", session.getAttribute("studio_name"));
            model.addAttribute("owner_name", session.getAttribute("owner_name"));

            return "customer_home";

        } catch (Exception e) {
            e.printStackTrace();

            model.addAttribute("customers", customers);
            model.addAttribute("error", "Database error: " + e.getMessage());
            model.addAttribute("studio_ID", studioId);
            model.addAttribute("studio_name", session.getAttribute("studio_name"));
            model.addAttribute("owner_name", session.getAttribute("owner_name"));

            return "customer_home";
        }
    }

    @PostMapping("/customer_images/{customerId}")
    public String uploadStudioCustomerImages(
            @PathVariable int customerId,
            @RequestParam("photos") MultipartFile[] photos,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (!isLoggedIn(session)) {
            return "redirect:/studio_login";
        }

        String studioId = String.valueOf(session.getAttribute("studio_ID"));
        if (photos == null || photos.length == 0
                || java.util.Arrays.stream(photos).allMatch(MultipartFile::isEmpty)) {
            redirectAttributes.addFlashAttribute("uploadError", "Choose at least one photo to upload.");
            return "redirect:/customer_home";
        }

        try (Connection con = db.getConnection()) {
            if (!customerBelongsToStudio(con, customerId, studioId)) {
                redirectAttributes.addFlashAttribute("uploadError", "Customer not found.");
                return "redirect:/customer_home";
            }

            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO customer_images (customer_id, image_name, image_data, uploaded_by) " +
                            "VALUES (?, ?, ?, 'STUDIO')")) {
                int uploaded = 0;
                for (MultipartFile photo : photos) {
                    if (photo == null || photo.isEmpty()) {
                        continue;
                    }

                    String contentType = photo.getContentType();
                    if (contentType == null
                            || !(contentType.equalsIgnoreCase("image/jpeg")
                            || contentType.equalsIgnoreCase("image/png")
                            || contentType.equalsIgnoreCase("image/webp"))) {
                        throw new IllegalArgumentException("Only JPG, PNG, and WEBP photos are supported.");
                    }

                    ps.setInt(1, customerId);
                    ps.setString(2, safeDownloadName(photo.getOriginalFilename(), "photo"));
                    ps.setBytes(3, photo.getBytes());
                    ps.executeUpdate();
                    uploaded++;
                }

                if (uploaded == 0) {
                    throw new IllegalArgumentException("Choose at least one supported photo.");
                }

                con.commit();
                redirectAttributes.addFlashAttribute(
                        "uploadMessage",
                        uploaded + " studio photo(s) added to the customer gallery.");
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("uploadError", "Unable to upload photos: " + errorMessage(e));
        }

        return "redirect:/customer_home";
    }

    @GetMapping("/customer_folders/{customerId}")
    public String customerFolders(
            @PathVariable int customerId,
            Model model,
            HttpSession session) {

        if (!isLoggedIn(session)) {
            return "redirect:/studio_login";
        }

        String studioId = String.valueOf(session.getAttribute("studio_ID"));

        try (Connection con = db.getConnection()) {
            Map<String, Object> customer = new HashMap<>();
            String customerSql = """
                    SELECT customer_id, customer_name
                    FROM customer
                    WHERE customer_id = ? AND studio_ID = ?
                    """;

            try (PreparedStatement ps = con.prepareStatement(customerSql)) {
                ps.setInt(1, customerId);
                ps.setString(2, studioId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "redirect:/customer_home";
                    }
                    customer.put("customer_id", rs.getInt("customer_id"));
                    customer.put("customer_name", rs.getString("customer_name"));
                }
            }

            Map<Integer, Map<String, Object>> foldersById = new LinkedHashMap<>();
            Map<Integer, List<Map<String, Object>>> folderImagesById = new HashMap<>();
            String folderSql = """
                    SELECT f.folder_id, f.folder_name, f.token, f.created_at,
                           fi.image_id, fi.image_name
                    FROM customer_photo_folders f
                    LEFT JOIN customer_photo_folder_images fi ON fi.folder_id = f.folder_id
                    WHERE f.customer_id = ?
                    ORDER BY f.folder_id DESC, fi.image_id
                    """;

            try (PreparedStatement ps = con.prepareStatement(folderSql)) {
                ps.setInt(1, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int folderId = rs.getInt("folder_id");
                        Map<String, Object> folder = foldersById.get(folderId);
                        if (folder == null) {
                            folder = new HashMap<>();
                            folder.put("folder_id", folderId);
                            folder.put("folder_name", rs.getString("folder_name"));
                            folder.put("token", rs.getString("token"));
                            folder.put("created_at", rs.getString("created_at"));
                            foldersById.put(folderId, folder);
                            folderImagesById.put(folderId, new ArrayList<>());
                        }

                        int imageId = rs.getInt("image_id");
                        if (!rs.wasNull()) {
                            Map<String, Object> image = new HashMap<>();
                            image.put("image_id", imageId);
                            image.put("image_name", rs.getString("image_name"));
                            folderImagesById.get(folderId).add(image);
                        }
                    }
                }
            }

            for (Map.Entry<Integer, Map<String, Object>> entry : foldersById.entrySet()) {
                List<Map<String, Object>> images = folderImagesById.get(entry.getKey());
                entry.getValue().put("images", images);
                entry.getValue().put("image_count", images.size());
            }

            model.addAttribute("customer", customer);
            model.addAttribute("folders", new ArrayList<>(foldersById.values()));
            model.addAttribute("studioName", session.getAttribute("studio_name"));
            model.addAttribute("folderMessage", session.getAttribute("folderMessage"));
            session.removeAttribute("folderMessage");
            return "customer_folders";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Unable to load this customer's folders.");
            model.addAttribute("customer", Map.of("customer_id", customerId, "customer_name", ""));
            model.addAttribute("folders", List.of());
            return "customer_folders";
        }
    }

    @PostMapping("/customer_folders/{customerId}")
    public String createCustomerFolder(
            @PathVariable int customerId,
            @RequestParam("folderName") String folderName,
            @RequestParam(value = "photos", required = false) MultipartFile[] photos,
            HttpSession session) {

        if (!isLoggedIn(session)) {
            return "redirect:/studio_login";
        }

        folderName = folderName.trim();
        if (folderName.isEmpty() || folderName.length() > 255) {
            session.setAttribute("folderMessage", "Enter a folder name with at most 255 characters.");
            return "redirect:/customer_folders/" + customerId;
        }

        String studioId = String.valueOf(session.getAttribute("studio_ID"));
        try (Connection con = db.getConnection()) {
            con.setAutoCommit(false);
            try {
                if (!customerBelongsToStudio(con, customerId, studioId)) {
                    con.rollback();
                    return "redirect:/customer_home";
                }

                String token = UUID.randomUUID().toString().replace("-", "");
                int folderId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO customer_photo_folders (customer_id, folder_name, token) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, customerId);
                    ps.setString(2, folderName);
                    ps.setString(3, token);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Could not retrieve the new folder ID.");
                        }
                        folderId = keys.getInt(1);
                    }
                }

                int uploaded = saveFolderPhotos(con, folderId, photos);
                con.commit();
                session.setAttribute(
                        "folderMessage",
                        "Folder created successfully. " + uploaded + " photo(s) uploaded."
                );
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            session.setAttribute("folderMessage", "Unable to create folder: " + errorMessage(e));
        }

        return "redirect:/customer_folders/" + customerId;
    }

    @PostMapping("/customer_folder/{folderId}/upload")
    public String uploadCustomerFolderPhotos(
            @PathVariable int folderId,
            @RequestParam("photos") MultipartFile[] photos,
            HttpSession session) {

        if (!isLoggedIn(session)) {
            return "redirect:/studio_login";
        }

        String studioId = String.valueOf(session.getAttribute("studio_ID"));
        int customerId = 0;
        try (Connection con = db.getConnection()) {
            con.setAutoCommit(false);
            try {
                String folderSql = """
                        SELECT f.customer_id
                        FROM customer_photo_folders f
                        JOIN customer c ON c.customer_id = f.customer_id
                        WHERE f.folder_id = ? AND c.studio_ID = ?
                        """;
                try (PreparedStatement ps = con.prepareStatement(folderSql)) {
                    ps.setInt(1, folderId);
                    ps.setString(2, studioId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            return "redirect:/customer_home";
                        }
                        customerId = rs.getInt("customer_id");
                    }
                }

                int uploaded = saveFolderPhotos(con, folderId, photos);
                if (uploaded == 0) {
                    throw new IllegalArgumentException("Choose at least one supported photo.");
                }
                con.commit();
                session.setAttribute("folderMessage", uploaded + " photo(s) added to the folder.");
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            session.setAttribute("folderMessage", "Unable to upload photos: " + errorMessage(e));
        }

        return customerId > 0
                ? "redirect:/customer_folders/" + customerId
                : "redirect:/customer_home";
    }

    @GetMapping("/customer_folder/{token}")
    public String viewCustomerPhotoFolder(
            @PathVariable String token,
            Model model) {

        try (Connection con = db.getConnection()) {
            String folderSql = """
                    SELECT f.folder_id, f.customer_id, f.folder_name, f.token, c.customer_name
                    FROM customer_photo_folders f
                    JOIN customer c ON c.customer_id = f.customer_id
                    WHERE f.token = ?
                    """;
            Map<String, Object> folder = new HashMap<>();
            int folderId;
            try (PreparedStatement ps = con.prepareStatement(folderSql)) {
                ps.setString(1, token);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return "error";
                    }
                    folderId = rs.getInt("folder_id");
                    folder.put("folder_id", folderId);
                    folder.put("customer_id", rs.getInt("customer_id"));
                    folder.put("folder_name", rs.getString("folder_name"));
                    folder.put("token", rs.getString("token"));
                    model.addAttribute("customerName", rs.getString("customer_name"));
                }
            }

            List<Map<String, Object>> images = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement("""
                    SELECT image_id, image_name
                    FROM customer_photo_folder_images
                    WHERE folder_id = ?
                    ORDER BY image_id
                    """)) {
                ps.setInt(1, folderId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> image = new HashMap<>();
                        image.put("image_id", rs.getInt("image_id"));
                        image.put("image_name", rs.getString("image_name"));
                        images.add(image);
                    }
                }
            }

            model.addAttribute("folder", folder);
            model.addAttribute("images", images);
            return "customer_photo_folder";
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    @GetMapping("/customer_folder_image/{token}/{imageId}")
    @ResponseBody
    public ResponseEntity<byte[]> viewCustomerFolderImage(
            @PathVariable String token,
            @PathVariable int imageId) {
        return getCustomerFolderImage(token, imageId, false);
    }

    @GetMapping("/download_customer_folder_image/{token}/{imageId}")
    @ResponseBody
    public ResponseEntity<byte[]> downloadCustomerFolderImage(
            @PathVariable String token,
            @PathVariable int imageId) {
        return getCustomerFolderImage(token, imageId, true);
    }

    @GetMapping("/download_customer_folder/{token}")
    public ResponseEntity<StreamingResponseBody> downloadCustomerFolder(@PathVariable String token) {
        String folderName;
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT folder_name FROM customer_photo_folders WHERE token = ?")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return ResponseEntity.notFound().build();
                }
                folderName = safeDownloadName(rs.getString("folder_name"), "photos");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }

        String zipName = folderName + ".zip";
        StreamingResponseBody body = outputStream -> {
            String sql = """
                    SELECT image_id, image_name, image_data
                    FROM customer_photo_folder_images
                    WHERE folder_id = (
                        SELECT folder_id
                        FROM customer_photo_folders
                        WHERE token = ?
                    )
                    ORDER BY image_id
                    """;
            try (Connection con = db.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql);
                 ZipOutputStream zip = new ZipOutputStream(outputStream)) {
                ps.setString(1, token);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String entryName = rs.getInt("image_id") + "_"
                                + safeDownloadName(rs.getString("image_name"), "photo");
                        zip.putNextEntry(new ZipEntry(entryName));
                        try (InputStream image = rs.getBinaryStream("image_data")) {
                            image.transferTo(zip);
                        }
                        zip.closeEntry();
                    }
                }
                zip.finish();
            } catch (Exception e) {
                throw new IOException("Unable to create the customer photo archive.", e);
            }
        };

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(zipName, StandardCharsets.UTF_8).build().toString()
                )
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @GetMapping("/download_customer_album/{token}")
    public ResponseEntity<StreamingResponseBody> downloadCustomerAlbum(@PathVariable String token) {
        String albumName;
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT album_name FROM customer_albums WHERE token = ?")) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return ResponseEntity.notFound().build();
                }
                albumName = safeDownloadName(rs.getString("album_name"), "album");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }

        String zipName = albumName + ".zip";
        StreamingResponseBody body = outputStream -> {
            String sql = """
                    SELECT ci.image_id, ci.image_name, ci.image_data
                    FROM customer_album_images ai
                    JOIN customer_images ci ON ci.image_id = ai.image_id
                    JOIN customer_albums a ON a.album_id = ai.album_id
                    WHERE a.token = ?
                    ORDER BY ai.album_image_id
                    """;
            try (Connection con = db.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql);
                 ZipOutputStream zip = new ZipOutputStream(outputStream)) {
                ps.setString(1, token);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String entryName = rs.getInt("image_id") + "_"
                                + safeDownloadName(rs.getString("image_name"), "photo");
                        zip.putNextEntry(new ZipEntry(entryName));
                        try (InputStream image = rs.getBinaryStream("image_data")) {
                            image.transferTo(zip);
                        }
                        zip.closeEntry();
                    }
                }
                zip.finish();
            } catch (Exception e) {
                throw new IOException("Unable to create the customer album archive.", e);
            }
        };

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(zipName, StandardCharsets.UTF_8).build().toString()
                )
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    private boolean customerBelongsToStudio(Connection con, int customerId, String studioId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM customer WHERE customer_id = ? AND studio_ID = ?")) {
            ps.setInt(1, customerId);
            ps.setString(2, studioId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int saveFolderPhotos(
            Connection con,
            int folderId,
            MultipartFile[] photos
    ) throws Exception {
        if (photos == null || photos.length == 0) {
            return 0;
        }

        int uploaded = 0;
        String imageSql = """
                INSERT INTO customer_photo_folder_images (folder_id, image_name, image_data)
                VALUES (?, ?, ?)
                """;
        try (PreparedStatement imageStatement = con.prepareStatement(imageSql)) {
            for (MultipartFile photo : photos) {
                if (photo == null || photo.isEmpty()) {
                    continue;
                }

                String contentType = photo.getContentType();
                if (contentType == null
                        || !(contentType.equalsIgnoreCase("image/jpeg")
                        || contentType.equalsIgnoreCase("image/png")
                        || contentType.equalsIgnoreCase("image/webp"))) {
                    throw new IllegalArgumentException("Unsupported photo type.");
                }

                String imageName = safeDownloadName(photo.getOriginalFilename(), "photo");
                imageStatement.setInt(1, folderId);
                imageStatement.setString(2, imageName);
                imageStatement.setBytes(3, photo.getBytes());
                imageStatement.executeUpdate();
                uploaded++;
            }
        }
        return uploaded;
    }

    private ResponseEntity<byte[]> getCustomerFolderImage(String token, int imageId, boolean download) {
        String sql = """
                SELECT fi.image_name, fi.image_data
                FROM customer_photo_folder_images fi
                JOIN customer_photo_folders f ON f.folder_id = fi.folder_id
                WHERE f.token = ? AND fi.image_id = ?
                """;
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setInt(2, imageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String imageName = safeDownloadName(rs.getString("image_name"), "photo");
                    byte[] data = rs.getBytes("image_data");
                    ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                            .contentType(imageMediaType(imageName));
                    if (download) {
                        response.header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment()
                                        .filename(imageName, StandardCharsets.UTF_8)
                                        .build()
                                        .toString()
                        );
                    }
                    return response.body(data);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResponseEntity.notFound().build();
    }

    private String safeDownloadName(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String safeName = name.replaceAll("[\\r\\n\"\\\\/]", "_").trim();
        return safeName.isEmpty() ? fallback : safeName.substring(0, Math.min(safeName.length(), 200));
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private MediaType imageMediaType(String imageName) {
        String lower = imageName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    // =========================================================================
// FETCH AND SERVE STUDIO LOGO DIRECTLY FROM THE DATABASE
// =========================================================================
    @GetMapping("/studio_logo/{studioId}")
    @ResponseBody
    public ResponseEntity<byte[]> getStudioLogo(@PathVariable("studioId") String studioId) {
        String sql = "SELECT studio_logo FROM studio WHERE studio_ID = ?";

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, studioId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] logoBytes = rs.getBytes("studio_logo");

                    if (logoBytes != null && logoBytes.length > 0) {
                        HttpHeaders headers = new HttpHeaders();
                        // Automatically detect PNG/JPEG or set to generic image
                        headers.setContentType(MediaType.IMAGE_PNG);
                        return new ResponseEntity<>(logoBytes, headers, HttpStatus.OK);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResponseEntity.notFound().build();
    }
    // =================================================
    // GENERATE CUSTOMER SELECTION LINK
    // =========================================================

    @PostMapping("/generate_selection_link/{customerId}")
    @ResponseBody
    public Map<String, Object> generateSelectionLink(
            @PathVariable int customerId,
            HttpSession session) {

        Map<String, Object> response =
                new HashMap<>();

        if (!isLoggedIn(session)) {

            response.put(
                    "success",
                    false
            );

            response.put(
                    "message",
                    "Session expired. Please login again."
            );

            return response;
        }

        String studioId =
                String.valueOf(
                        session.getAttribute("studio_ID")
                );

        try (Connection con = db.getConnection()) {



            // =================================================
            // CHECK CUSTOMER BELONGS TO STUDIO
            // =================================================

            String checkSql =
                    "SELECT customer_id " +
                            "FROM customer " +
                            "WHERE customer_id = ? " +
                            "AND studio_ID = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(checkSql)) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.setString(
                        2,
                        studioId
                );

                try (ResultSet rs =
                             ps.executeQuery()) {

                    if (!rs.next()) {

                        response.put(
                                "success",
                                false
                        );

                        response.put(
                                "message",
                                "Customer not found."
                        );

                        return response;
                    }
                }
            }


            // =================================================
            // GENERATE TOKEN
            // =================================================

            String token =
                    UUID.randomUUID().toString();


            // =================================================
            // INSERT TOKEN
            // =================================================

            String insertSql =
                    "INSERT INTO customer_selection_links " +
                            "(customer_id, token) " +
                            "VALUES (?, ?)";

            try (PreparedStatement ps =
                         con.prepareStatement(insertSql)) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.setString(
                        2,
                        token
                );

                ps.executeUpdate();
            }


            // Build from the current request so forwarded host/scheme headers
            // and any application context path are retained behind a proxy.
            String link = ServletUriComponentsBuilder
                    .fromCurrentContextPath()
                    .path("/customer_selection_link/{token}")
                    .buildAndExpand(token)
                    .toUriString();


            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "CUSTOMER SELECTION LINK GENERATED"
            );

            System.out.println(
                    "Customer ID : " +
                            customerId
            );

            System.out.println(
                    "Token       : " +
                            token
            );

            System.out.println(
                    "URL         : " +
                            link
            );

            System.out.println(
                    "========================================"
            );


            response.put(
                    "success",
                    true
            );

            response.put(
                    "link",
                    link
            );

            response.put(
                    "token",
                    token
            );

            return response;

        } catch (Exception e) {

            e.printStackTrace();

            response.put(
                    "success",
                    false
            );

            response.put(
                    "message",
                    "Database error: " +
                            e.getMessage()
            );

            return response;
        }
    }


    // =========================================================
    // CREATE ALBUM FROM CUSTOMER SELECTED PHOTOS
    // =========================================================

    @PostMapping("/create_album/{customerId}")
    public String createAlbum(
            @PathVariable("customerId") int customerId,
            @RequestParam("album_name") String albumName,
            HttpSession session) {

        if (!isLoggedIn(session)) {
            return "redirect:/studio_login";
        }

        String studioId =
                String.valueOf(
                        session.getAttribute("studio_ID")
                );

        Connection con = null;

        try {

            con = db.getConnection();

            con.setAutoCommit(false);



            // =================================================
            // VERIFY CUSTOMER
            // =================================================

            String verifySql =
                    "SELECT customer_id " +
                            "FROM customer " +
                            "WHERE customer_id = ? " +
                            "AND studio_ID = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(verifySql)) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.setString(
                        2,
                        studioId
                );

                try (ResultSet rs =
                             ps.executeQuery()) {

                    if (!rs.next()) {

                        throw new Exception(
                                "Customer does not belong " +
                                        "to this studio."
                        );
                    }
                }
            }


            // =================================================
            // CHECK ALBUM NAME
            // =================================================

            if (albumName == null ||
                    albumName.trim().isEmpty()) {

                throw new Exception(
                        "Album name cannot be empty."
                );
            }

            albumName =
                    albumName.trim();


            // =================================================
            // GENERATE ALBUM TOKEN
            // =================================================

            String albumToken =
                    UUID.randomUUID().toString();


            // =================================================
            // CREATE ALBUM
            // =================================================

            String albumSql =
                    "INSERT INTO customer_albums " +
                            "(customer_id, album_name, token) " +
                            "VALUES (?, ?, ?)";

            int albumId;

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 albumSql,
                                 Statement.RETURN_GENERATED_KEYS
                         )) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.setString(
                        2,
                        albumName
                );

                ps.setString(
                        3,
                        albumToken
                );

                ps.executeUpdate();

                try (ResultSet rs =
                             ps.getGeneratedKeys()) {

                    if (!rs.next()) {

                        throw new Exception(
                                "Unable to create album."
                        );
                    }

                    albumId =
                            rs.getInt(1);
                }
            }


            // =================================================
            // GET SELECTED PHOTOS FROM LATEST TOKEN
            // =================================================

            String selectedSql =
                    "SELECT csi.image_id " +
                            "FROM customer_selected_images csi " +
                            "JOIN customer_selection_links csl " +
                            "ON csl.token = csi.token " +
                            "WHERE csi.customer_id = ? " +
                            "AND csl.link_id = (" +
                            "    SELECT MAX(link_id) " +
                            "    FROM customer_selection_links " +
                            "    WHERE customer_id = ?" +
                            ") " +
                            "ORDER BY csi.selection_id";


            // =================================================
            // INSERT SELECTED PHOTOS INTO ALBUM
            // =================================================

            String albumImageSql =
                    "INSERT INTO customer_album_images " +
                            "(album_id, image_id) " +
                            "VALUES (?, ?)";

            int selectedCount = 0;


            try (PreparedStatement selectedPs =
                         con.prepareStatement(selectedSql);

                 PreparedStatement albumImagePs =
                         con.prepareStatement(
                                 albumImageSql
                         )) {

                selectedPs.setInt(
                        1,
                        customerId
                );

                selectedPs.setInt(
                        2,
                        customerId
                );

                try (ResultSet rs =
                             selectedPs.executeQuery()) {

                    while (rs.next()) {

                        int imageId =
                                rs.getInt("image_id");

                        albumImagePs.setInt(
                                1,
                                albumId
                        );

                        albumImagePs.setInt(
                                2,
                                imageId
                        );

                        albumImagePs.addBatch();

                        selectedCount++;
                    }
                }


                // =================================================
                // NO SELECTED PHOTOS
                // =================================================

                if (selectedCount == 0) {

                    throw new Exception(
                            "No photos have been selected " +
                                    "by the customer."
                    );
                }


                albumImagePs.executeBatch();
            }


            // =================================================
            // COMMIT
            // =================================================

            con.commit();


            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "ALBUM CREATED"
            );

            System.out.println(
                    "Album ID        : " +
                            albumId
            );

            System.out.println(
                    "Customer ID     : " +
                            customerId
            );

            System.out.println(
                    "Album Name      : " +
                            albumName
            );

            System.out.println(
                    "Selected Photos : " +
                            selectedCount
            );

            System.out.println(
                    "========================================"
            );


            return "redirect:/customer_albums/" +
                    customerId;

        } catch (Exception e) {

            e.printStackTrace();

            try {

                if (con != null) {
                    con.rollback();
                }

            } catch (Exception rollbackError) {

                rollbackError.printStackTrace();
            }


            return "redirect:/customer_selection_manage/" +
                    customerId;

        } finally {

            try {

                if (con != null) {

                    con.setAutoCommit(true);
                    con.close();
                }

            } catch (Exception e) {

                e.printStackTrace();
            }
        }
    }
// =========================================================
// ADD / SAVE CUSTOMER
// POST /customer_register
// =========================================================
// =========================================================
// DELETE CUSTOMER
// POST /customer_delete/{id}
// =========================================================

    @PostMapping("/customer_delete/{id}")
    public String deleteCustomer(
            @PathVariable("id") int customerId,
            HttpSession session) {

        // =====================================================
        // CHECK LOGIN
        // =====================================================

        if (!isLoggedIn(session)) {
            return "redirect:/studio_login";
        }

        Object studioIdObject =
                session.getAttribute("studio_ID");

        if (studioIdObject == null) {
            return "redirect:/studio_login";
        }

        String studioId =
                studioIdObject.toString();

        Connection con = null;

        try {

            con = db.getConnection();

            // =================================================
            // START TRANSACTION
            // =================================================

            con.setAutoCommit(false);


            // =================================================
            // 1. VERIFY CUSTOMER BELONGS TO LOGGED-IN STUDIO
            // =================================================

            String checkSql =
                    "SELECT customer_id " +
                            "FROM customer " +
                            "WHERE customer_id = ? " +
                            "AND studio_ID = ?";

            boolean customerExists = false;

            try (PreparedStatement ps =
                         con.prepareStatement(checkSql)) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.setString(
                        2,
                        studioId
                );

                try (ResultSet rs =
                             ps.executeQuery()) {

                    if (rs.next()) {
                        customerExists = true;
                    }
                }
            }


            // =================================================
            // CUSTOMER NOT FOUND
            // =================================================

            if (!customerExists) {

                con.rollback();

                return "redirect:/customer_home?error=true";
            }


            // =================================================
            // 2. DELETE ALBUM IMAGES
            // =================================================

            String deleteAlbumImages =
                    "DELETE FROM customer_album_images " +
                            "WHERE album_id IN (" +
                            "    SELECT album_id " +
                            "    FROM customer_albums " +
                            "    WHERE customer_id = ?" +
                            ")";

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 deleteAlbumImages
                         )) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.executeUpdate();
            }


            // =================================================
            // 3. DELETE CUSTOMER ALBUMS
            // =================================================

            String deleteAlbums =
                    "DELETE FROM customer_albums " +
                            "WHERE customer_id = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 deleteAlbums
                         )) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.executeUpdate();
            }


            // =================================================
            // 4. DELETE SELECTED IMAGES
            // =================================================

            String deleteSelectedImages =
                    "DELETE FROM customer_selected_images " +
                            "WHERE customer_id = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 deleteSelectedImages
                         )) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.executeUpdate();
            }


            // =================================================
            // 5. DELETE CUSTOMER SELECTION LINKS
            // =================================================

            String deleteSelectionLinks =
                    "DELETE FROM customer_selection_links " +
                            "WHERE customer_id = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 deleteSelectionLinks
                         )) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.executeUpdate();
            }


            // =================================================
            // 6. DELETE CUSTOMER IMAGES
            // =================================================

            String deleteImages =
                    "DELETE FROM customer_images " +
                            "WHERE customer_id = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 deleteImages
                         )) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.executeUpdate();
            }


            // =================================================
            // 7. DELETE CUSTOMER
            // =================================================

            String deleteCustomer =
                    "DELETE FROM customer " +
                            "WHERE customer_id = ? " +
                            "AND studio_ID = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 deleteCustomer
                         )) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.setString(
                        2,
                        studioId
                );

                int deletedRows =
                        ps.executeUpdate();

                if (deletedRows == 0) {

                    throw new SQLException(
                            "Customer could not be deleted."
                    );
                }
            }


            // =================================================
            // 8. COMMIT
            // =================================================

            con.commit();


            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "CUSTOMER DELETED SUCCESSFULLY"
            );

            System.out.println(
                    "Studio ID   : " +
                            studioId
            );

            System.out.println(
                    "Customer ID : " +
                            customerId
            );

            System.out.println(
                    "========================================"
            );


            return "redirect:/customer_home?success=deleted";


        } catch (Exception e) {

            e.printStackTrace();


            // =================================================
            // ROLLBACK
            // =================================================

            try {

                if (con != null) {
                    con.rollback();
                }

            } catch (Exception rollbackError) {

                rollbackError.printStackTrace();
            }


            return "redirect:/customer_home?error=true";


        } finally {

            try {

                if (con != null) {

                    con.setAutoCommit(true);
                    con.close();
                }

            } catch (Exception e) {

                e.printStackTrace();
            }
        }
    }
    @PostMapping("/customer_register")
    public String registerCustomer(

            @RequestParam("customer_name")
            String customerName,

            @RequestParam("phone")
            String phone,

            @RequestParam(
                    value = "address",
                    required = false
            )
            String address,

            @RequestParam(
                    value = "images",
                    required = false
            )
            MultipartFile[] images,

            HttpSession session) {

        // =====================================================
        // CHECK LOGIN
        // =====================================================

        if (!isLoggedIn(session)) {
            return "redirect:/studio_login";
        }

        Object studioIdObject =
                session.getAttribute("studio_ID");

        if (studioIdObject == null) {
            return "redirect:/studio_login";
        }

        String studioId =
                studioIdObject.toString();

        Connection con = null;

        try {

            con = db.getConnection();

            // =================================================
            // START TRANSACTION
            // =================================================

            con.setAutoCommit(false);

            // =================================================
            // VALIDATE CUSTOMER NAME
            // =================================================

            if (customerName == null ||
                    customerName.trim().isEmpty()) {

                throw new Exception(
                        "Customer name is required."
                );
            }

            if (phone == null ||
                    phone.trim().isEmpty()) {

                throw new Exception(
                        "Phone number is required."
                );
            }

            customerName =
                    customerName.trim();

            phone =
                    phone.trim();

            if (address == null) {
                address = "";
            }

            address =
                    address.trim();

            // =================================================
            // INSERT CUSTOMER
            // =================================================

            String customerSql =
                    "INSERT INTO customer " +
                            "(studio_ID, customer_name, phone, address) " +
                            "VALUES (?, ?, ?, ?)";

            int customerId;

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 customerSql,
                                 Statement.RETURN_GENERATED_KEYS
                         )) {

                ps.setString(
                        1,
                        studioId
                );

                ps.setString(
                        2,
                        customerName
                );

                ps.setString(
                        3,
                        phone
                );

                ps.setString(
                        4,
                        address
                );

                int affectedRows =
                        ps.executeUpdate();

                if (affectedRows == 0) {

                    throw new SQLException(
                            "Customer was not inserted."
                    );
                }

                // =============================================
                // GET CUSTOMER ID
                // =============================================

                try (ResultSet keys =
                             ps.getGeneratedKeys()) {

                    if (!keys.next()) {

                        throw new SQLException(
                                "Unable to get generated customer ID."
                        );
                    }

                    customerId =
                            keys.getInt(1);
                }
            }

            // =================================================
            // INSERT CUSTOMER IMAGES
            // =================================================

            int imageCount = 0;

            if (images != null &&
                    images.length > 0) {

                String imageSql =
                        "INSERT INTO customer_images " +
                                "(customer_id, image_name, image_data, uploaded_by) " +
                                "VALUES (?, ?, ?, 'STUDIO')";

                try (PreparedStatement imagePs =
                             con.prepareStatement(imageSql)) {

                    for (MultipartFile image : images) {

                        // Skip empty files
                        if (image == null ||
                                image.isEmpty()) {

                            continue;
                        }

                        String fileName =
                                image.getOriginalFilename();

                        if (fileName == null ||
                                fileName.trim().isEmpty()) {

                            fileName =
                                    "image_" +
                                            (imageCount + 1) +
                                            ".jpg";
                        }

                        byte[] imageData =
                                image.getBytes();

                        imagePs.setInt(
                                1,
                                customerId
                        );

                        imagePs.setString(
                                2,
                                fileName
                        );

                        imagePs.setBytes(
                                3,
                                imageData
                        );

                        imagePs.addBatch();

                        imageCount++;
                    }

                    if (imageCount > 0) {
                        imagePs.executeBatch();
                    }
                }
            }

            // =================================================
            // COMMIT
            // =================================================

            con.commit();

            System.out.println(
                    "========================================"
            );

            System.out.println(
                    "CUSTOMER SAVED SUCCESSFULLY"
            );

            System.out.println(
                    "Studio ID    : " + studioId
            );

            System.out.println(
                    "Customer ID  : " + customerId
            );

            System.out.println(
                    "Customer Name: " + customerName
            );

            System.out.println(
                    "Phone        : " + phone
            );

            System.out.println(
                    "Images Saved : " + imageCount
            );

            System.out.println(
                    "========================================"
            );

            // =================================================
            // RETURN TO CUSTOMER HOME
            // =================================================

            return "redirect:/customer_home?success=true";

        } catch (Exception e) {

            e.printStackTrace();

            // =================================================
            // ROLLBACK
            // =================================================

            try {

                if (con != null) {
                    con.rollback();
                }

            } catch (Exception rollbackError) {

                rollbackError.printStackTrace();
            }

            return "redirect:/customer_home?error=true";

        } finally {

            try {

                if (con != null) {

                    con.setAutoCommit(true);
                    con.close();
                }

            } catch (Exception e) {

                e.printStackTrace();
            }
        }
    }

    // =========================================================
    // CUSTOMER ALBUMS PAGE
    // /customer_albums/{customerId}
    // =========================================================

    @GetMapping("/customer_albums/{customerId}")
    public String customerAlbums(
            @PathVariable("customerId") int customerId,
            Model model,
            HttpSession session) {

        if (!isLoggedIn(session)) {

            return "redirect:/studio_login";
        }

        String studioId =
                String.valueOf(
                        session.getAttribute("studio_ID")
                );

        try (Connection con = db.getConnection()) {



            // =================================================
            // GET CUSTOMER
            // =================================================

            String customerSql =
                    "SELECT customer_id, " +
                            "customer_name, " +
                            "phone, " +
                            "address, " +
                            "studio_ID " +
                            "FROM customer " +
                            "WHERE customer_id = ? " +
                            "AND studio_ID = ?";

            Map<String, Object> customer =
                    new HashMap<>();

            try (PreparedStatement ps =
                         con.prepareStatement(customerSql)) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.setString(
                        2,
                        studioId
                );

                try (ResultSet rs =
                             ps.executeQuery()) {

                    if (!rs.next()) {

                        model.addAttribute(
                                "error",
                                "Customer not found."
                        );

                        model.addAttribute(
                                "customer",
                                customer
                        );

                        model.addAttribute(
                                "albums",
                                new ArrayList<>()
                        );

                        return "customer_albums";
                    }


                    customer.put(
                            "customer_id",
                            rs.getInt("customer_id")
                    );

                    customer.put(
                            "customer_name",
                            rs.getString("customer_name")
                    );

                    customer.put(
                            "phone",
                            rs.getString("phone")
                    );

                    customer.put(
                            "address",
                            rs.getString("address")
                    );

                    customer.put(
                            "studio_ID",
                            rs.getString("studio_ID")
                    );
                }
            }


            // =================================================
            // GET ALBUMS
            // =================================================

            List<Map<String, Object>> albums =
                    new ArrayList<>();

            String albumSql =
                    "SELECT album_id, " +
                            "album_name, " +
                            "token, " +
                            "created_at " +
                            "FROM customer_albums " +
                            "WHERE customer_id = ? " +
                            "ORDER BY album_id DESC";

            try (PreparedStatement ps =
                         con.prepareStatement(albumSql)) {

                ps.setInt(
                        1,
                        customerId
                );

                try (ResultSet rs =
                             ps.executeQuery()) {

                    while (rs.next()) {

                        Map<String, Object> album =
                                new HashMap<>();

                        album.put(
                                "album_id",
                                rs.getInt("album_id")
                        );

                        album.put(
                                "album_name",
                                rs.getString("album_name")
                        );

                        album.put(
                                "token",
                                rs.getString("token")
                        );

                        album.put(
                                "created_at",
                                rs.getString("created_at")
                        );

                        albums.add(album);
                    }
                }
            }


            model.addAttribute(
                    "customer",
                    customer
            );

            model.addAttribute(
                    "albums",
                    albums
            );

            return "customer_albums";

        } catch (Exception e) {

            e.printStackTrace();

            model.addAttribute(
                    "error",
                    "Database error: " +
                            e.getMessage()
            );

            model.addAttribute(
                    "customer",
                    new HashMap<>()
            );

            model.addAttribute(
                    "albums",
                    new ArrayList<>()
            );

            return "customer_albums";
        }
    }


    // =========================================================
    // VIEW ALBUM
    // /album/{albumId}
    // =========================================================

    @GetMapping("/album/{albumId}")
    public String viewAlbum(
            @PathVariable("albumId") int albumId,
            Model model,
            HttpSession session) {

        if (!isLoggedIn(session)) {
            return "redirect:/studio_login";
        }

        String studioId =
                String.valueOf(
                        session.getAttribute("studio_ID")
                );

        try (Connection con = db.getConnection()) {



            // =================================================
            // GET ALBUM + VERIFY STUDIO OWNERSHIP
            // =================================================

            String albumSql =
                    "SELECT ca.album_id, " +
                            "ca.customer_id, " +
                            "ca.album_name, " +
                            "ca.token " +
                            "FROM customer_albums ca " +
                            "JOIN customer c " +
                            "ON c.customer_id = ca.customer_id " +
                            "WHERE ca.album_id = ? " +
                            "AND c.studio_ID = ?";

            Map<String, Object> album =
                    new HashMap<>();

            try (PreparedStatement ps =
                         con.prepareStatement(albumSql)) {

                ps.setInt(
                        1,
                        albumId
                );

                ps.setString(
                        2,
                        studioId
                );

                try (ResultSet rs =
                             ps.executeQuery()) {

                    if (!rs.next()) {

                        return "redirect:/customer_home";
                    }


                    album.put(
                            "album_id",
                            rs.getInt("album_id")
                    );

                    album.put(
                            "customer_id",
                            rs.getInt("customer_id")
                    );

                    album.put(
                            "album_name",
                            rs.getString("album_name")
                    );

                    album.put(
                            "token",
                            rs.getString("token")
                    );
                }
            }


            // =================================================
            // GET ONLY ALBUM IMAGES
            // =================================================

            List<Map<String, Object>> images =
                    new ArrayList<>();

            String imageSql =
                    "SELECT ci.image_id, " +
                            "ci.customer_id, " +
                            "ci.image_name " +
                            "FROM customer_album_images cai " +
                            "JOIN customer_images ci " +
                            "ON ci.image_id = cai.image_id " +
                            "WHERE cai.album_id = ? " +
                            "ORDER BY cai.album_image_id";

            try (PreparedStatement ps =
                         con.prepareStatement(imageSql)) {

                ps.setInt(
                        1,
                        albumId
                );

                try (ResultSet rs =
                             ps.executeQuery()) {

                    while (rs.next()) {

                        Map<String, Object> image =
                                new HashMap<>();

                        image.put(
                                "image_id",
                                rs.getInt("image_id")
                        );

                        image.put(
                                "customer_id",
                                rs.getInt("customer_id")
                        );

                        image.put(
                                "image_name",
                                rs.getString("image_name")
                        );

                        images.add(image);
                    }
                }
            }


            model.addAttribute(
                    "album",
                    album
            );

            model.addAttribute(
                    "images",
                    images
            );

            return "view_album";

        } catch (Exception e) {

            e.printStackTrace();

            return "redirect:/customer_home";
        }
    }


    // =========================================================
    // STUDIO CUSTOMER SELECTION MANAGEMENT PAGE
    // /customer_selection_manage/{customerId}
    // =========================================================

    @GetMapping("/customer_selection_manage/{customerId}")
    public String viewCustomerSelections(
            @PathVariable("customerId") int customerId,
            Model model,
            HttpSession session) {

        if (!isLoggedIn(session)) {

            return "redirect:/studio_login";
        }

        String studioId =
                String.valueOf(
                        session.getAttribute("studio_ID")
                );

        try (Connection con = db.getConnection()) {



            // =================================================
            // GET CUSTOMER DETAILS
            // =================================================

            String customerSql =
                    "SELECT customer_id, " +
                            "customer_name, " +
                            "phone, " +
                            "address, " +
                            "studio_ID " +
                            "FROM customer " +
                            "WHERE customer_id = ? " +
                            "AND studio_ID = ?";

            Map<String, Object> customer =
                    new HashMap<>();

            try (PreparedStatement ps =
                         con.prepareStatement(customerSql)) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.setString(
                        2,
                        studioId
                );

                try (ResultSet rs =
                             ps.executeQuery()) {

                    if (!rs.next()) {

                        model.addAttribute(
                                "error",
                                "Customer not found."
                        );

                        model.addAttribute(
                                "customer",
                                customer
                        );

                        model.addAttribute(
                                "selectedImages",
                                new ArrayList<>()
                        );

                        return "customer_selection_manage";
                    }


                    // CUSTOMER ID
                    customer.put(
                            "customer_id",
                            rs.getInt("customer_id")
                    );


                    // CUSTOMER NAME
                    customer.put(
                            "customer_name",
                            rs.getString("customer_name")
                    );


                    // PHONE
                    customer.put(
                            "phone",
                            rs.getString("phone")
                    );


                    // ADDRESS
                    customer.put(
                            "address",
                            rs.getString("address")
                    );


                    // STUDIO ID
                    customer.put(
                            "studio_ID",
                            rs.getString("studio_ID")
                    );
                }
            }


            // =================================================
            // GET ONLY SELECTED PHOTOS
            // FROM LATEST SELECTION LINK
            // =================================================

            List<Map<String, Object>> selectedImages =
                    new ArrayList<>();

            String imageSql =
                    "SELECT ci.image_id, " +
                            "ci.image_name " +
                            "FROM customer_selected_images csi " +
                            "JOIN customer_images ci " +
                            "ON ci.image_id = csi.image_id " +
                            "WHERE csi.customer_id = ? " +
                            "AND csi.token = (" +
                            "    SELECT token " +
                            "    FROM customer_selection_links " +
                            "    WHERE customer_id = ? " +
                            "    ORDER BY link_id DESC " +
                            "    LIMIT 1" +
                            ") " +
                            "ORDER BY csi.selection_id";

            try (PreparedStatement ps =
                         con.prepareStatement(imageSql)) {

                ps.setInt(
                        1,
                        customerId
                );

                ps.setInt(
                        2,
                        customerId
                );

                try (ResultSet rs =
                             ps.executeQuery()) {

                    while (rs.next()) {

                        Map<String, Object> image =
                                new HashMap<>();

                        image.put(
                                "image_id",
                                rs.getInt("image_id")
                        );

                        image.put(
                                "image_name",
                                rs.getString("image_name")
                        );

                        selectedImages.add(image);
                    }
                }
            }


            // =================================================
            // SEND DATA TO THYMELEAF
            // =================================================

            model.addAttribute(
                    "customer",
                    customer
            );

            model.addAttribute(
                    "selectedImages",
                    selectedImages
            );

            return "customer_selection_manage";

        } catch (Exception e) {

            e.printStackTrace();

            model.addAttribute(
                    "error",
                    "Database error: " +
                            e.getMessage()
            );

            model.addAttribute(
                    "customer",
                    new HashMap<>()
            );

            model.addAttribute(
                    "selectedImages",
                    new ArrayList<>()
            );

            return "customer_selection_manage";
        }
    }



    // =========================================================
    // SAVE CUSTOMER PHOTO SELECTION
    // =========================================================

    // =========================================================
    // CUSTOMER GALLERY & ALBUMS PAGE
    // GET /customer_selection_link/{token}
    // =========================================================
    @GetMapping("/customer_selection_link/{token}")
    public String customerSelectionPage(@PathVariable("token") String token, Model model) {
        try (Connection con = db.getConnection()) {

            String sql = "SELECT c.customer_id, c.customer_name, c.phone, c.address, csl.token " +
                    "FROM customer_selection_links csl " +
                    "JOIN customer c ON csl.customer_id = c.customer_id " +
                    "WHERE csl.token = ?";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, token);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> customer = new HashMap<>();
                        int customerId = rs.getInt("customer_id");
                        customer.put("customer_id", customerId);
                        customer.put("customer_name", rs.getString("customer_name"));
                        customer.put("token", token);

                        // Load existing customer images
                        List<Map<String, Object>> images = new ArrayList<>();
                        String imageSql = "SELECT image_id, image_name, uploaded_by FROM customer_images " +
                                "WHERE customer_id = ? ORDER BY image_id DESC";

                        try (PreparedStatement imgPs = con.prepareStatement(imageSql)) {
                            imgPs.setInt(1, customerId);
                            try (ResultSet imgRs = imgPs.executeQuery()) {
                                while (imgRs.next()) {
                                    Map<String, Object> img = new HashMap<>();
                                    img.put("image_id", imgRs.getInt("image_id"));
                                    img.put("image_name", imgRs.getString("image_name"));
                                    img.put("uploaded_by", imgRs.getString("uploaded_by"));
                                    images.add(img);
                                }
                            }
                        }

                        Map<Integer, Map<String, Object>> albumViews = new LinkedHashMap<>();
                        Map<Integer, List<Map<String, Object>>> albumImagesById = new HashMap<>();
                        String albumSql = """
                                SELECT a.album_id, a.album_name, a.token,
                                       ci.image_id, ci.image_name, ci.uploaded_by
                                FROM customer_albums a
                                LEFT JOIN customer_album_images ai ON ai.album_id = a.album_id
                                LEFT JOIN customer_images ci
                                  ON ci.image_id = ai.image_id AND ci.customer_id = a.customer_id
                                WHERE a.customer_id = ?
                                ORDER BY a.album_id DESC, ai.album_image_id
                                """;

                        try (PreparedStatement albumPs = con.prepareStatement(albumSql)) {
                            albumPs.setInt(1, customerId);
                            try (ResultSet albumRs = albumPs.executeQuery()) {
                                while (albumRs.next()) {
                                    int albumId = albumRs.getInt("album_id");
                                    Map<String, Object> album = albumViews.get(albumId);
                                    if (album == null) {
                                        album = new HashMap<>();
                                        album.put("album_id", albumId);
                                        album.put("album_name", albumRs.getString("album_name"));
                                        album.put("token", albumRs.getString("token"));
                                        albumViews.put(albumId, album);
                                        albumImagesById.put(albumId, new ArrayList<>());
                                    }

                                    int imageId = albumRs.getInt("image_id");
                                    if (!albumRs.wasNull()) {
                                        Map<String, Object> image = new HashMap<>();
                                        image.put("image_id", imageId);
                                        image.put("image_name", albumRs.getString("image_name"));
                                        image.put("uploaded_by", albumRs.getString("uploaded_by"));
                                        albumImagesById.get(albumId).add(image);
                                    }
                                }
                            }
                        }

                        for (Map.Entry<Integer, Map<String, Object>> entry : albumViews.entrySet()) {
                            entry.getValue().put("images", albumImagesById.get(entry.getKey()));
                        }

                        model.addAttribute("customer", customer);
                        model.addAttribute("images", images);
                        model.addAttribute("albums", new ArrayList<>(albumViews.values()));
                        return "customer_portal";
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        model.addAttribute("error", "This customer gallery link is invalid or unavailable.");
        return "customer_portal";
    }

    @PostMapping("/customer_portal/{token}/albums")
    public String createCustomerPortalAlbum(
            @PathVariable String token,
            @RequestParam("folderName") String folderName,
            @RequestParam(value = "imageIds", required = false) List<Integer> imageIds,
            @RequestParam(value = "photos", required = false) MultipartFile[] photos,
            RedirectAttributes redirectAttributes) {

        String normalizedName = folderName == null ? "" : folderName.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 255) {
            redirectAttributes.addFlashAttribute("portalError", "Enter an album name of 1 to 255 characters.");
            return "redirect:/customer_selection_link/" + token;
        }

        try (Connection con = db.getConnection()) {
            con.setAutoCommit(false);
            try {
                int customerId;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT customer_id FROM customer_selection_links WHERE token = ?")) {
                    ps.setString(1, token);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            redirectAttributes.addFlashAttribute("portalError", "This customer gallery link is invalid.");
                            return "redirect:/customer_selection_link/" + token;
                        }
                        customerId = rs.getInt("customer_id");
                    }
                }

                int albumId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO customer_albums (customer_id, album_name, token) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, customerId);
                    ps.setString(2, normalizedName);
                    ps.setString(3, UUID.randomUUID().toString());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException("The album could not be created.");
                        }
                        albumId = rs.getInt(1);
                    }
                }

                if (imageIds != null && !imageIds.isEmpty()) {
                    String linkSql = """
                            INSERT INTO customer_album_images (album_id, image_id)
                            SELECT ?, image_id
                            FROM customer_images
                            WHERE customer_id = ? AND image_id = ?
                            """;
                    try (PreparedStatement ps = con.prepareStatement(linkSql)) {
                        for (Integer imageId : imageIds) {
                            if (imageId == null || imageId <= 0) {
                                throw new IllegalArgumentException("An invalid gallery photo was selected.");
                            }
                            ps.setInt(1, albumId);
                            ps.setInt(2, customerId);
                            ps.setInt(3, imageId);
                            if (ps.executeUpdate() != 1) {
                                throw new IllegalArgumentException("A selected photo is no longer available.");
                            }
                        }
                    }
                }

                addPhotosToAlbum(con, customerId, albumId, photos);
                con.commit();
                redirectAttributes.addFlashAttribute("portalMessage", "Album created successfully.");
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("portalError", "Unable to create album: " + errorMessage(e));
        }

        return "redirect:/customer_selection_link/" + token;
    }

    @PostMapping("/customer_portal/{token}/albums/{albumId}/photos")
    @ResponseBody
    public Map<String, Object> uploadCustomerAlbumPhotos(
            @PathVariable String token,
            @PathVariable int albumId,
            @RequestParam("photos") MultipartFile[] photos) {

        Map<String, Object> response = new HashMap<>();
        if (token == null || token.isBlank() || albumId <= 0 || photos == null || photos.length == 0) {
            response.put("success", false);
            response.put("message", "Choose photos for a valid album.");
            return response;
        }

        try (Connection con = db.getConnection()) {
            con.setAutoCommit(false);
            try {
                int customerId;
                String ownershipSql = """
                        SELECT a.customer_id
                        FROM customer_selection_links link
                        JOIN customer_albums a ON a.customer_id = link.customer_id
                        WHERE link.token = ? AND a.album_id = ?
                        """;
                try (PreparedStatement ps = con.prepareStatement(ownershipSql)) {
                    ps.setString(1, token);
                    ps.setInt(2, albumId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            response.put("success", false);
                            response.put("message", "This album is not available from this customer link.");
                            return response;
                        }
                        customerId = rs.getInt("customer_id");
                    }
                }

                int uploaded = addPhotosToAlbum(con, customerId, albumId, photos);
                if (uploaded == 0) {
                    throw new IllegalArgumentException("Choose at least one supported photo.");
                }
                con.commit();
                response.put("success", true);
                response.put("message", uploaded + " photo(s) added to the album.");
                return response;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Unable to upload photos: " + errorMessage(e));
            return response;
        }
    }

    private int addPhotosToAlbum(
            Connection con,
            int customerId,
            int albumId,
            MultipartFile[] photos
    ) throws Exception {
        if (photos == null || photos.length == 0) {
            return 0;
        }

        int uploaded = 0;
        String imageSql = """
                INSERT INTO customer_images (customer_id, image_name, image_data, uploaded_by)
                VALUES (?, ?, ?, 'CUSTOMER')
                """;
        String albumImageSql = "INSERT INTO customer_album_images (album_id, image_id) VALUES (?, ?)";

        try (PreparedStatement imageStatement = con.prepareStatement(imageSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement albumImageStatement = con.prepareStatement(albumImageSql)) {
            for (MultipartFile photo : photos) {
                if (photo == null || photo.isEmpty()) {
                    continue;
                }
                String contentType = photo.getContentType();
                if (contentType == null
                        || !(contentType.equalsIgnoreCase("image/jpeg")
                        || contentType.equalsIgnoreCase("image/png")
                        || contentType.equalsIgnoreCase("image/webp"))) {
                    throw new IllegalArgumentException("Only JPG, PNG, and WEBP photos are supported.");
                }

                imageStatement.setInt(1, customerId);
                imageStatement.setString(2, safeDownloadName(photo.getOriginalFilename(), "photo"));
                imageStatement.setBytes(3, photo.getBytes());
                imageStatement.executeUpdate();

                int imageId;
                try (ResultSet keys = imageStatement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Unable to save an uploaded photo.");
                    }
                    imageId = keys.getInt(1);
                }

                albumImageStatement.setInt(1, albumId);
                albumImageStatement.setInt(2, imageId);
                albumImageStatement.executeUpdate();
                uploaded++;
            }
        }
        return uploaded;
    }

    // =========================================================
    // UPLOAD CUSTOMER GALLERY IMAGES
    // POST /upload_customer_images
    // =========================================================
    @PostMapping("/upload_customer_images")
    @ResponseBody
    public Map<String, Object> uploadCustomerImage(
            @RequestParam("token") String token,
            @RequestParam("photos") MultipartFile[] files) {

        Map<String, Object> response = new HashMap<>();

        if (token == null || token.trim().isEmpty() || files == null || files.length == 0) {
            response.put("success", false);
            response.put("message", "Invalid upload request.");
            return response;
        }

        try (Connection con = db.getConnection()) {

            // Find customer ID by token
            String customerSql = "SELECT customer_id FROM customer_selection_links WHERE token = ?";
            int customerId = 0;

            try (PreparedStatement ps = con.prepareStatement(customerSql)) {
                ps.setString(1, token);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        customerId = rs.getInt("customer_id");
                    }
                }
            }

            if (customerId == 0) {
                response.put("success", false);
                response.put("message", "Invalid session token.");
                return response;
            }

            // Save uploaded image bytes to DB
            String insertSql = """
                    INSERT INTO customer_images (customer_id, image_name, image_data, uploaded_by)
                    VALUES (?, ?, ?, 'CUSTOMER')
                    """;
            List<Map<String, Object>> uploadedImages = new ArrayList<>();

            try (PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                for (MultipartFile file : files) {
                    if (file == null || file.isEmpty()) continue;

                    String contentType = file.getContentType();
                    if (contentType == null ||
                            !(contentType.equalsIgnoreCase("image/jpeg") ||
                                    contentType.equalsIgnoreCase("image/png") ||
                                    contentType.equalsIgnoreCase("image/webp"))) {
                        continue;
                    }

                    String fileName = file.getOriginalFilename();
                    if (fileName == null || fileName.trim().isEmpty()) {
                        fileName = "uploaded_image_" + System.currentTimeMillis() + ".jpg";
                    } else {
                        fileName = fileName.replace("\\", "_").replace("/", "_").trim();
                    }

                    ps.setInt(1, customerId);
                    ps.setString(2, fileName);
                    ps.setBytes(3, file.getBytes());
                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            Map<String, Object> img = new HashMap<>();
                            img.put("image_id", rs.getInt(1));
                            img.put("image_name", fileName);
                            uploadedImages.add(img);
                        }
                    }
                }
            }

            if (uploadedImages.isEmpty()) {
                response.put("success", false);
                response.put("message", "No valid JPG, PNG or WEBP images were uploaded.");
                return response;
            }

            response.put("success", true);
            response.put("message", uploadedImages.size() + " image(s) uploaded successfully.");
            response.put("images", uploadedImages);
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Server error during upload: " + e.getMessage());
            return response;
        }
    }

    @PostMapping("/upload_customer_folder_images")
    @ResponseBody
    public Map<String, Object> uploadCustomerFolderImages(
            @RequestParam("token") String token,
            @RequestParam("folderId") int folderId,
            @RequestParam("photos") MultipartFile[] files) {

        Map<String, Object> response = new HashMap<>();
        if (token == null || token.isBlank() || folderId <= 0 || files == null || files.length == 0) {
            response.put("success", false);
            response.put("message", "Choose photos for a valid folder.");
            return response;
        }

        try (Connection con = db.getConnection()) {
            con.setAutoCommit(false);
            try {
                String ownershipSql = """
                        SELECT 1
                        FROM customer_selection_links link
                        JOIN customer_photo_folders folder
                          ON folder.customer_id = link.customer_id
                        WHERE link.token = ? AND folder.folder_id = ?
                        """;
                try (PreparedStatement ps = con.prepareStatement(ownershipSql)) {
                    ps.setString(1, token);
                    ps.setInt(2, folderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            response.put("success", false);
                            response.put("message", "This folder is not available from this customer link.");
                            return response;
                        }
                    }
                }

                int uploaded = saveFolderPhotos(con, folderId, files);
                if (uploaded == 0) {
                    throw new IllegalArgumentException("Choose at least one supported photo.");
                }
                con.commit();
                response.put("success", true);
                response.put("message", uploaded + " photo(s) uploaded successfully.");
                return response;
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Unable to upload photos: " + errorMessage(e));
            return response;
        }
    }

    // =========================================================
    // SAVE CUSTOMER SELECTION
    // POST /save_customer_selection
    // =========================================================
    @PostMapping("/save_customer_selection")
    @ResponseBody
    public Map<String, Object> saveCustomerSelection(
            @RequestParam("token") String token,
            @RequestParam(value = "image_ids", required = false) List<Integer> imageIds) {

        Map<String, Object> response = new HashMap<>();

        if (token == null || token.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Invalid selection token.");
            return response;
        }

        Connection con = null;
        try {
            con = db.getConnection();
            con.setAutoCommit(false);


            // Get customer ID
            String customerSql = "SELECT customer_id FROM customer_selection_links WHERE token = ?";
            int customerId = 0;

            try (PreparedStatement ps = con.prepareStatement(customerSql)) {
                ps.setString(1, token);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        customerId = rs.getInt("customer_id");
                    }
                }
            }

            if (customerId == 0) {
                con.rollback();
                response.put("success", false);
                response.put("message", "Customer not found for this token.");
                return response;
            }

            // Validate that every selected image belongs to this customer
            if (imageIds != null && !imageIds.isEmpty()) {
                String verifyImageSql =
                        "SELECT image_id FROM customer_images " +
                                "WHERE image_id = ? AND customer_id = ?";

                try (PreparedStatement verifyPs =
                             con.prepareStatement(verifyImageSql)) {
                    for (Integer imageId : imageIds) {
                        if (imageId == null) {
                            con.rollback();
                            response.put("success", false);
                            response.put("message", "Invalid image selection.");
                            return response;
                        }

                        verifyPs.setInt(1, imageId);
                        verifyPs.setInt(2, customerId);

                        try (ResultSet verifyRs = verifyPs.executeQuery()) {
                            if (!verifyRs.next()) {
                                con.rollback();
                                response.put("success", false);
                                response.put("message", "One or more selected images do not belong to this customer.");
                                return response;
                            }
                        }
                    }
                }
            }

            // Clear previous selections for this token
            String deleteSql = "DELETE FROM customer_selected_images WHERE token = ?";
            try (PreparedStatement ps = con.prepareStatement(deleteSql)) {
                ps.setString(1, token);
                ps.executeUpdate();
            }

            // Insert new selections
            if (imageIds != null && !imageIds.isEmpty()) {
                String insertSql = "INSERT INTO customer_selected_images (customer_id, token, image_id) VALUES (?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                    for (Integer imageId : imageIds) {
                        ps.setInt(1, customerId);
                        ps.setString(2, token);
                        ps.setInt(3, imageId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            con.commit();

            response.put("success", true);
            response.put("message", "Your photo selection has been saved successfully!");
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            try {
                if (con != null) con.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            response.put("success", false);
            response.put("message", "Database error: " + e.getMessage());
            return response;
        } finally {
            try {
                if (con != null) {
                    con.setAutoCommit(true);
                    con.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    // =========================================================
    // SERVE CUSTOMER IMAGE
    // =========================================================

    @GetMapping("/customer_image/{imageId}")
    @ResponseBody
    public ResponseEntity<byte[]> customerImage(
            @PathVariable("imageId") int imageId) {

        try (Connection con = db.getConnection()) {

            String sql =
                    "SELECT image_name, image_data " +
                            "FROM customer_images " +
                            "WHERE image_id = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setInt(
                        1,
                        imageId
                );

                try (ResultSet rs =
                             ps.executeQuery()) {

                    if (rs.next()) {

                        byte[] data =
                                rs.getBytes(
                                        "image_data"
                                );

                        String name =
                                rs.getString(
                                        "image_name"
                                );

                        MediaType type =
                                MediaType.APPLICATION_OCTET_STREAM;


                        if (name != null) {

                            String lower =
                                    name.toLowerCase();


                            if (lower.endsWith(".jpg")
                                    ||
                                    lower.endsWith(".jpeg")) {

                                type =
                                        MediaType.IMAGE_JPEG;

                            } else if (
                                    lower.endsWith(".png")) {

                                type =
                                        MediaType.IMAGE_PNG;

                            } else if (
                                    lower.endsWith(".webp")) {

                                type =
                                        MediaType.parseMediaType(
                                                "image/webp"
                                        );
                            }
                        }


                        return ResponseEntity
                                .ok()
                                .contentType(type)
                                .body(data);
                    }
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }


        return ResponseEntity
                .notFound()
                .build();
    }

    @GetMapping("/download_customer_image/{token}/{imageId}")
    @ResponseBody
    public ResponseEntity<byte[]> downloadCustomerImage(
            @PathVariable String token,
            @PathVariable int imageId) {

        String sql = """
                SELECT image.image_name, image.image_data
                FROM customer_images image
                JOIN customer_selection_links link ON link.customer_id = image.customer_id
                WHERE link.token = ? AND image.image_id = ?
                """;
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setInt(2, imageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String imageName = safeDownloadName(rs.getString("image_name"), "photo");
                    return ResponseEntity.ok()
                            .header(
                                    HttpHeaders.CONTENT_DISPOSITION,
                                    ContentDisposition.attachment()
                                            .filename(imageName, StandardCharsets.UTF_8)
                                            .build()
                                            .toString()
                            )
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(rs.getBytes("image_data"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResponseEntity.notFound().build();
    }

}
