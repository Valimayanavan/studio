package org.example.studio_admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.example.studio_admin.models.connectionDb;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

            String sql =
                    "SELECT image_name, image_data " +
                            "FROM customer_images " +
                            "WHERE image_id = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(sql)) {

                ps.setInt(1, imageId);

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
            HttpSession session,
            HttpServletRequest request) {

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


            // =================================================
            // CREATE FULL URL
            // =================================================

            String baseUrl =
                    request.getScheme()
                            + "://"
                            + request.getServerName();

            if (request.getServerPort() != 80
                    &&
                    request.getServerPort() != 443) {

                baseUrl +=
                        ":" +
                                request.getServerPort();
            }

            String link =
                    baseUrl +
                            "/customer_selection_link/" +
                            token;


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
                                "(customer_id, image_name, image_data) " +
                                "VALUES (?, ?, ?)";

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
    // CUSTOMER SELECTION & UPLOAD PAGE
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
                        String imageSql = "SELECT image_id, image_name FROM customer_images WHERE customer_id = ? ORDER BY image_id DESC";

                        try (PreparedStatement imgPs = con.prepareStatement(imageSql)) {
                            imgPs.setInt(1, customerId);
                            try (ResultSet imgRs = imgPs.executeQuery()) {
                                while (imgRs.next()) {
                                    Map<String, Object> img = new HashMap<>();
                                    img.put("image_id", imgRs.getInt("image_id"));
                                    img.put("image_name", imgRs.getString("image_name"));
                                    images.add(img);
                                }
                            }
                        }

                        model.addAttribute("customer", customer);
                        model.addAttribute("images", images);
                        return "customer_selection"; // Name of your Thymeleaf HTML file
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        model.addAttribute("error", "Invalid or expired selection link.");
        return "customer_selection";
    }


    // =========================================================
    // UPLOAD CUSTOMER IMAGES
    // POST /upload_customer_image
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
            String insertSql = "INSERT INTO customer_images (customer_id, image_name, image_data) VALUES (?, ?, ?)";
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
}
