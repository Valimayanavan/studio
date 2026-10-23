package org.example.studio_admin;

import org.example.studio_admin.models.connectionDb;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import jakarta.servlet.http.HttpSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Controller
public class controller {

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    @GetMapping("/login")
    public String showForm(HttpSession session) {

        Boolean loggedIn =
                (Boolean) session.getAttribute("loggedIn");

        if (loggedIn != null && loggedIn) {
            return "redirect:/home";
        }

        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String user_name,
            @RequestParam String password,
            Model model,
            HttpSession session) {

        connectionDb db = new connectionDb();

        String loginSql =
                "SELECT UserName, Password FROM `admin` WHERE UserName = ?";

        try (
                Connection connection =
                        db.getConnection();

                PreparedStatement loginStatement =
                        connection.prepareStatement(loginSql)
        ) {

            loginStatement.setString(1, user_name);

            try (ResultSet resultSet =
                         loginStatement.executeQuery()) {

                if (resultSet.next()) {

                    String passwordFromDB =
                            resultSet.getString("Password");

                    // BCrypt hash -> normal check.
                    // Legacy plain-text password (old database) -> compare safely,
                    // then upgrade it to a BCrypt hash right away.
                    boolean isHashed =
                            passwordFromDB != null
                                    && passwordFromDB.startsWith("$2");

                    boolean passwordOk;

                    if (isHashed) {
                        passwordOk = passwordEncoder.matches(
                                password,
                                passwordFromDB
                        );
                    } else {
                        passwordOk = passwordFromDB != null
                                && java.security.MessageDigest.isEqual(
                                password.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                passwordFromDB.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                        );

                        if (passwordOk) {
                            try (PreparedStatement upgrade = connection.prepareStatement(
                                    "UPDATE `admin` SET Password = ? WHERE UserName = ?")) {
                                upgrade.setString(1, passwordEncoder.encode(password));
                                upgrade.setString(2, user_name);
                                upgrade.executeUpdate();
                            }
                        }
                    }

                    if (passwordOk) {

                        session.setAttribute(
                                "loggedIn",
                                true
                        );

                        session.setAttribute(
                                "username",
                                user_name
                        );

                        return "redirect:/home";
                    }
                }

                model.addAttribute(
                        "message",
                        "Invalid username or password!"
                );

                return "login";
            }

        } catch (Exception e) {

            e.printStackTrace();

            model.addAttribute(
                    "message",
                    "Database error!"
            );

            return "login";
        }
    }

    @GetMapping("/home")
    public String home(
            HttpSession session,
            Model model) {

        Boolean loggedIn =
                (Boolean) session.getAttribute("loggedIn");

        if (loggedIn == null || !loggedIn) {
            return "redirect:/login";
        }

        model.addAttribute(
                "username",
                session.getAttribute("username")
        );

        String successMessage =
                (String) session.getAttribute("studioSuccess");

        if (successMessage != null) {

            model.addAttribute(
                    "successMessage",
                    successMessage
            );

            session.removeAttribute("studioSuccess");
        }

        String errorMessage =
                (String) session.getAttribute("studioError");

        if (errorMessage != null) {

            model.addAttribute(
                    "errorMessage",
                    errorMessage
            );

            session.removeAttribute("studioError");
        }

        List<Studio> studios =
                new ArrayList<>();

        connectionDb db =
                new connectionDb();

        String sql =
                "SELECT Studio_ID, Studio_name, Studio_mobile, " +
                        "Studio_Email, Owner_name, Password, studio_logo " +
                        "FROM studio ORDER BY Studio_ID";

        try (
                Connection connection =
                        db.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet resultSet =
                        statement.executeQuery()
        ) {

            while (resultSet.next()) {

                Studio studio =
                        new Studio();

                studio.setStudio_ID(
                        resultSet.getString("Studio_ID")
                );

                studio.setStudio_name(
                        resultSet.getString("Studio_name")
                );

                studio.setStudio_mobile(
                        resultSet.getLong("Studio_mobile")
                );

                studio.setStudio_Email(
                        resultSet.getString("Studio_Email")
                );

                studio.setOwner_name(
                        resultSet.getString("Owner_name")
                );

                studio.setPassword(
                        resultSet.getString("Password")
                );

                // Convert blob image to Base64
                byte[] imgBytes =
                        resultSet.getBytes("studio_logo");

                if (
                        imgBytes != null
                                &&
                                imgBytes.length > 0
                ) {

                    String base64Image =
                            Base64.getEncoder()
                                    .encodeToString(imgBytes);

                    studio.setStudio_logo(
                            base64Image
                    );
                }

                studios.add(studio);
            }

        } catch (Exception e) {

            e.printStackTrace();

            model.addAttribute(
                    "errorMessage",
                    "Unable to load studio data!"
            );
        }

        model.addAttribute(
                "studio",
                studios
        );

        return "home";
    }

    @PostMapping("/add-studio")
    public String addStudio(
            @RequestParam String studio_name,
            @RequestParam String studio_mobile,
            @RequestParam String studio_email,
            @RequestParam String owner_name,
            @RequestParam String password,
            @RequestParam("studio_logo")
            MultipartFile logoFile,
            HttpSession session) {

        Boolean loggedIn =
                (Boolean) session.getAttribute("loggedIn");

        if (loggedIn == null || !loggedIn) {
            return "redirect:/login";
        }

        studio_name = studio_name.trim();
        studio_mobile = studio_mobile.trim();

        // Studio_mobile is a BIGINT column - reject anything that is not digits
        if (studio_name.isEmpty()
                || !studio_mobile.matches("\\d{6,15}")) {

            session.setAttribute(
                    "studioError",
                    "Please enter a studio name and a valid mobile number (digits only)."
            );

            return "redirect:/home";
        }

        String studio_ID =
                studio_name.replaceAll(
                        "\\s+",
                        "_"
                )
                        + "_"
                        + studio_mobile;

        connectionDb db =
                new connectionDb();

        String checkSql =
                "SELECT Studio_ID FROM studio WHERE Studio_ID = ?";

        try (
                Connection connection =
                        db.getConnection();

                PreparedStatement checkStatement =
                        connection.prepareStatement(checkSql)
        ) {

            checkStatement.setString(
                    1,
                    studio_ID
            );

            try (ResultSet resultSet =
                         checkStatement.executeQuery()) {

                if (resultSet.next()) {

                    session.setAttribute(
                            "studioError",
                            "Studio ID '"
                                    + studio_ID
                                    + "' already exists!"
                    );

                    return "redirect:/home";
                }
            }

            String hashedPassword =
                    passwordEncoder.encode(password);

            byte[] logoBytes =
                    !logoFile.isEmpty()
                            ? logoFile.getBytes()
                            : null;

            String insertSql =
                    "INSERT INTO studio " +
                            "(Studio_ID, Studio_name, Studio_mobile, " +
                            "Studio_Email, Owner_name, Password, studio_logo) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement statement =
                         connection.prepareStatement(insertSql)) {

                statement.setString(
                        1,
                        studio_ID
                );

                statement.setString(
                        2,
                        studio_name
                );

                statement.setString(
                        3,
                        studio_mobile
                );

                statement.setString(
                        4,
                        studio_email
                );

                statement.setString(
                        5,
                        owner_name
                );

                statement.setString(
                        6,
                        hashedPassword
                );

                if (logoBytes != null) {

                    statement.setBytes(
                            7,
                            logoBytes
                    );

                } else {

                    statement.setNull(
                            7,
                            java.sql.Types.BLOB
                    );
                }

                int rowsInserted =
                        statement.executeUpdate();

                if (rowsInserted > 0) {

                    session.setAttribute(
                            "studioSuccess",
                            "Studio '"
                                    + studio_ID
                                    + "' added successfully!"
                    );
                }
            }

        } catch (Exception e) {

            e.printStackTrace();

            session.setAttribute(
                    "studioError",
                    "Unable to add studio: "
                            + e.getMessage()
            );
        }

        return "redirect:/home";
    }

    @PostMapping("/delete-studio")
    public String deleteStudio(
            @RequestParam String studio_ID,
            HttpSession session) {

        Boolean loggedIn =
                (Boolean) session.getAttribute("loggedIn");

        if (loggedIn == null || !loggedIn) {
            return "redirect:/login";
        }

        connectionDb db =
                new connectionDb();

        String sql =
                "DELETE FROM studio WHERE Studio_ID = ?";

        // Customers (and, through ON DELETE CASCADE, their photos, albums and
        // selection links) must go together with the studio. Otherwise they
        // stay in the database forever and would be handed to anybody who
        // later registers a studio with the same ID.
        String deleteCustomersSql =
                "DELETE FROM customer WHERE studio_ID = ?";

        try (
                Connection connection =
                        db.getConnection()
        ) {

            connection.setAutoCommit(false);

            int rowsDeleted;

            try (
                    PreparedStatement customers =
                            connection.prepareStatement(deleteCustomersSql);

                    PreparedStatement statement =
                            connection.prepareStatement(sql)
            ) {

                customers.setString(
                        1,
                        studio_ID
                );

                customers.executeUpdate();

                statement.setString(
                        1,
                        studio_ID
                );

                rowsDeleted =
                        statement.executeUpdate();

                connection.commit();

            } catch (Exception e) {

                connection.rollback();

                throw e;
            }

            if (rowsDeleted > 0) {

                session.setAttribute(
                        "studioSuccess",
                        "Studio '"
                                + studio_ID
                                + "' deleted successfully!"
                );

            } else {

                session.setAttribute(
                        "studioError",
                        "Studio '"
                                + studio_ID
                                + "' was not found!"
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

            session.setAttribute(
                    "studioError",
                    "Unable to delete studio!"
            );
        }

        return "redirect:/home";
    }


    // =========================================================
    // SUBSCRIPTION
    // =========================================================

    @GetMapping("/subscriptions")
    public String subscriptions(
            HttpSession session,
            Model model) {

        Boolean loggedIn =
                (Boolean) session.getAttribute("loggedIn");

        if (loggedIn == null || !loggedIn) {
            return "redirect:/login";
        }

        List<Map<String, Object>> subscriptions =
                new ArrayList<>();

        int activeCount = 0;
        int pendingCount = 0;
        int expiredCount = 0;
        long revenue = 0;

        connectionDb db =
                new connectionDb();

        String sql = """
                SELECT
                    id,
                    name,
                    phone,
                    email,
                    plan,
                    amount,
                    razorpay_order_id,
                    razorpay_payment_id,
                    razorpay_signature,
                    start_date,
                    expiry_date,
                    trial_start_date,
                    trial_end_date,
                    studio_ID,
                    status,
                    created_at
                FROM subscription
                ORDER BY id DESC
                """;

        try (
                Connection connection =
                        db.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet resultSet =
                        statement.executeQuery()
        ) {

            while (resultSet.next()) {

                Map<String, Object> subscription =
                        new HashMap<>();

                subscription.put(
                        "id",
                        resultSet.getInt("id")
                );

                subscription.put(
                        "name",
                        resultSet.getString("name")
                );

                subscription.put(
                        "phone",
                        resultSet.getString("phone")
                );

                subscription.put(
                        "email",
                        resultSet.getString("email")
                );

                subscription.put(
                        "plan",
                        resultSet.getString("plan")
                );

                // Amount is stored in paise
                subscription.put(
                        "amount",
                        resultSet.getInt("amount") / 100
                );

                subscription.put(
                        "orderId",
                        resultSet.getString(
                                "razorpay_order_id"
                        )
                );

                subscription.put(
                        "paymentId",
                        resultSet.getString(
                                "razorpay_payment_id"
                        )
                );

                subscription.put(
                        "signature",
                        resultSet.getString(
                                "razorpay_signature"
                        )
                );

                subscription.put(
                        "startDate",
                        resultSet.getString(
                                "start_date"
                        )
                );

                subscription.put(
                        "trialStartDate",
                        resultSet.getString("trial_start_date")
                );

                subscription.put(
                        "trialEndDate",
                        resultSet.getString("trial_end_date")
                );

                subscription.put(
                        "studioId",
                        resultSet.getString("studio_ID")
                );

                String expiryDate =
                        resultSet.getString("expiry_date");

                subscription.put(
                        "expiryDate",
                        expiryDate
                );

                String status =
                        resultSet.getString("status");

                subscription.put(
                        "status",
                        status
                );

                // ACTIVE subscription whose expiry date has passed
                boolean expired = false;

                if ("ACTIVE".equalsIgnoreCase(status)
                        && expiryDate != null) {

                    try {
                        expired = java.time.LocalDate
                                .parse(expiryDate.substring(0, 10))
                                .isBefore(java.time.LocalDate.now());
                    } catch (Exception ignored) {
                        // unreadable date -> treat as not expired
                    }
                }

                subscription.put(
                        "expired",
                        expired
                );

                if ("ACTIVE".equalsIgnoreCase(status)) {
                    if (expired) {
                        expiredCount++;
                    } else {
                        activeCount++;
                    }
                    // amount is stored in paise
                    revenue += resultSet.getInt("amount") / 100;
                } else if ("PENDING".equalsIgnoreCase(status)) {
                    pendingCount++;
                }

                subscription.put(
                        "createdAt",
                        resultSet.getString(
                                "created_at"
                        )
                );

                subscriptions.add(
                        subscription
                );
            }

        } catch (Exception e) {

            e.printStackTrace();

            model.addAttribute(
                    "errorMessage",
                    "Unable to load subscription data!"
            );
        }

        model.addAttribute(
                "subscriptions",
                subscriptions
        );

        model.addAttribute("totalCount", subscriptions.size());
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("expiredCount", expiredCount);
        model.addAttribute("revenue", revenue);

        model.addAttribute(
                "username",
                session.getAttribute("username")
        );

        return "subscriptions";
    }


    @GetMapping("/logout")
    public String logout(
            HttpSession session) {

        session.invalidate();

        return "redirect:/login";
    }
}