package org.example.studio_admin;

import jakarta.servlet.http.HttpSession;
import org.example.studio_admin.models.connectionDb;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Controller
public class customer_register_controller {

    private final connectionDb db = new connectionDb();

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();


    // =====================================================
    // SHOW STUDIO REGISTRATION PAGE
    // =====================================================

    @GetMapping("/studio_registration")
    public String studioRegistration() {
        return "studio_register";
    }


    // =====================================================
    // REGISTER STUDIO AFTER SUCCESSFUL PAYMENT
    // =====================================================

    @PostMapping("/studio_register")
    public String addStudio(

            @RequestParam("studio_name")
            String studio_name,

            @RequestParam("studio_mobile")
            String studio_mobile,

            @RequestParam("studio_email")
            String studio_email,

            @RequestParam("owner_name")
            String owner_name,

            @RequestParam("new_password")
            String new_password,

            @RequestParam("re_password")
            String re_password,

            @RequestParam("plan")
            String plan,

            @RequestParam(value = "studio_logo", required = false)
            MultipartFile studio_logo,

            HttpSession session
    ) {

        // =====================================================
        // CREATE STUDIO ID
        // =====================================================

        String studio_ID =
                studio_name.trim() + "_" + studio_mobile.trim();


        // =====================================================
        // CHECK PASSWORD MATCH
        // =====================================================

        if (!new_password.equals(re_password)) {

            session.setAttribute(
                    "studioError",
                    "Password and Confirm Password do not match!"
            );

            return "redirect:/studio_registration";
        }


        // =====================================================
        // CHECK PASSWORD LENGTH
        // =====================================================

        if (new_password.length() < 6) {

            session.setAttribute(
                    "studioError",
                    "Password must contain at least 6 characters!"
            );

            return "redirect:/studio_registration";
        }


        // =====================================================
        // CHECK SUBSCRIPTION PLAN
        // =====================================================

        if (!plan.equals("1_MONTH")
                && !plan.equals("3_MONTH")
                && !plan.equals("1_YEAR")) {

            session.setAttribute(
                    "studioError",
                    "Invalid subscription plan selected!"
            );

            return "redirect:/studio_registration";
        }


        // =====================================================
        // DATABASE QUERIES
        // =====================================================

        String checkStudioSql =
                "SELECT Studio_ID " +
                        "FROM studio " +
                        "WHERE Studio_ID = ?";


        String checkEmailSql =
                "SELECT Studio_Email " +
                        "FROM studio " +
                        "WHERE Studio_Email = ?";


        /*
         * IMPORTANT:
         *
         * The subscription must already be ACTIVE.
         *
         * This happens in:
         *
         * POST /api/subscription/verify
         *
         * after Razorpay payment succeeds.
         */

        String checkSubscriptionSql =
                "SELECT id, plan, status " +
                        "FROM subscription " +
                        "WHERE email = ? " +
                        "AND plan = ? " +
                        "AND status = 'ACTIVE' " +
                        "ORDER BY id DESC " +
                        "LIMIT 1";


        String insertSql =
                "INSERT INTO studio " +
                        "(Studio_ID, Studio_name, Studio_mobile, " +
                        "Studio_Email, Owner_name, Password, Studio_Logo) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";


        try (Connection connection = db.getConnection()) {


            // =====================================================
            // CHECK STUDIO ID
            // =====================================================

            try (PreparedStatement ps =
                         connection.prepareStatement(checkStudioSql)) {

                ps.setString(1, studio_ID);

                try (ResultSet rs = ps.executeQuery()) {

                    if (rs.next()) {

                        session.setAttribute(
                                "studioError",
                                "Studio ID '" +
                                        studio_ID +
                                        "' already exists!"
                        );

                        return "redirect:/studio_registration";
                    }
                }
            }


            // =====================================================
            // CHECK EMAIL
            // =====================================================

            try (PreparedStatement ps =
                         connection.prepareStatement(checkEmailSql)) {

                ps.setString(1, studio_email);

                try (ResultSet rs = ps.executeQuery()) {

                    if (rs.next()) {

                        session.setAttribute(
                                "studioError",
                                "Email '" +
                                        studio_email +
                                        "' is already registered!"
                        );

                        return "redirect:/studio_registration";
                    }
                }
            }


            // =====================================================
            // VERIFY PAYMENT / SUBSCRIPTION
            // =====================================================

            try (PreparedStatement ps =
                         connection.prepareStatement(
                                 checkSubscriptionSql)) {

                ps.setString(1, studio_email);
                ps.setString(2, plan);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {

                        session.setAttribute(
                                "studioError",
                                "Payment verification failed. " +
                                        "Please complete the subscription payment."
                        );

                        return "redirect:/studio_registration";
                    }

                    String subscriptionStatus =
                            rs.getString("status");

                    String subscriptionPlan =
                            rs.getString("plan");

                    if (!"ACTIVE".equals(subscriptionStatus)) {

                        session.setAttribute(
                                "studioError",
                                "Your subscription payment is not active."
                        );

                        return "redirect:/studio_registration";
                    }

                    if (!plan.equals(subscriptionPlan)) {

                        session.setAttribute(
                                "studioError",
                                "Subscription plan mismatch."
                        );

                        return "redirect:/studio_registration";
                    }
                }
            }


            // =====================================================
            // HASH PASSWORD
            // =====================================================

            String hashedPassword =
                    passwordEncoder.encode(new_password);


            // =====================================================
            // INSERT STUDIO
            // =====================================================

            try (PreparedStatement ps =
                         connection.prepareStatement(insertSql)) {

                ps.setString(1, studio_ID);
                ps.setString(2, studio_name);
                ps.setString(3, studio_mobile);
                ps.setString(4, studio_email);
                ps.setString(5, owner_name);
                ps.setString(6, hashedPassword);


                // =================================================
                // STORE STUDIO LOGO
                // =================================================

                if (studio_logo != null &&
                        !studio_logo.isEmpty()) {

                    try (InputStream inputStream =
                                 studio_logo.getInputStream()) {

                        ps.setBinaryStream(
                                7,
                                inputStream,
                                (int) studio_logo.getSize()
                        );

                        int rows = ps.executeUpdate();

                        if (rows > 0) {

                            session.setAttribute(
                                    "studioSuccess",
                                    "Studio '" +
                                            studio_ID +
                                            "' registered successfully!"
                            );

                            return "redirect:/studio_login";
                        }
                    }

                } else {

                    ps.setNull(
                            7,
                            java.sql.Types.BLOB
                    );

                    int rows = ps.executeUpdate();

                    if (rows > 0) {

                        session.setAttribute(
                                "studioSuccess",
                                "Studio '" +
                                        studio_ID +
                                        "' registered successfully!"
                        );

                        return "redirect:/studio_login";
                    }
                }
            }

        } catch (Exception e) {

            e.printStackTrace();

            session.setAttribute(
                    "studioError",
                    "Unable to register studio: " +
                            e.getMessage()
            );

            return "redirect:/studio_registration";
        }


        return "redirect:/studio_registration";
    }
}