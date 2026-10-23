package org.example.studio_admin;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import org.example.studio_admin.models.connectionDb;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
public class customer_register_controller {

    private final connectionDb db = new connectionDb();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping("/studio_registration")
    public String studioRegistration(HttpSession session, Model model) {
        Object error = session.getAttribute("studioError");
        if (error != null) {
            model.addAttribute("studioError", error);
            session.removeAttribute("studioError");
        }
        return "studio_register";
    }

    @GetMapping("/studio_registration/credentials")
    public String registrationCredentials(HttpSession session, Model model) {
        Object studioId = session.getAttribute("registrationStudioId");
        Object temporaryPassword = session.getAttribute("registrationTemporaryPassword");

        if (studioId == null || temporaryPassword == null) {
            return session.getAttribute("studio_ID") != null
                    ? "redirect:/customer_home"
                    : "redirect:/studio_registration";
        }

        model.addAttribute("credentialsAvailable", true);
        model.addAttribute("createdStudioId", studioId);
        model.addAttribute("temporaryPassword", temporaryPassword);
        session.removeAttribute("registrationStudioId");
        session.removeAttribute("registrationTemporaryPassword");
        return "studio_register";
    }

    @PostMapping("/studio_register")
    @ResponseBody
    public Map<String, Object> addStudio(
            @RequestParam("studio_name") String studioName,
            @RequestParam("studio_mobile") String studioMobile,
            @RequestParam("studio_email") String studioEmail,
            @RequestParam("owner_name") String ownerName,
            @RequestParam("plan") String plan,
            @RequestParam("razorpayOrderId") String razorpayOrderId,
            @RequestParam(value = "studio_logo", required = false) MultipartFile studioLogo,
            HttpServletRequest request,
            HttpSession session
    ) {
        studioName = studioName.trim();
        studioMobile = studioMobile.trim();
        studioEmail = studioEmail.trim();
        ownerName = ownerName.trim();

        if (studioName.isEmpty() || !studioMobile.matches("\\d{6,15}")
                || studioEmail.isEmpty() || ownerName.isEmpty()) {
            return registrationFailure("Please provide valid studio and owner details.");
        }

        if (!isSupportedPlan(plan)) {
            return registrationFailure("Invalid subscription plan selected.");
        }

        String studioId = studioName.replaceAll("\\s+", "_") + "_" + studioMobile;
        String temporaryPassword = UUID.randomUUID().toString().replace("-", "");
        byte[] logoBytes;

        try {
            logoBytes = studioLogo == null || studioLogo.isEmpty()
                    ? null
                    : studioLogo.getBytes();
        } catch (Exception e) {
            return registrationFailure("Unable to read the studio logo.");
        }

        try (Connection connection = db.getConnection()) {
            connection.setAutoCommit(false);

            try {
                if (exists(connection, "SELECT 1 FROM studio WHERE Studio_ID = ?", studioId)) {
                    connection.rollback();
                    return registrationFailure("Studio ID '" + studioId + "' already exists.");
                }

                if (exists(connection, "SELECT 1 FROM studio WHERE Studio_Email = ?", studioEmail)) {
                    connection.rollback();
                    return registrationFailure("Email '" + studioEmail + "' is already registered.");
                }

                int subscriptionId = findPaidSubscription(connection, razorpayOrderId, studioEmail, plan);
                if (subscriptionId == 0) {
                    connection.rollback();
                    return registrationFailure(
                            "Payment verification failed or this payment has already been used."
                    );
                }

                String insertSql = """
                        INSERT INTO studio
                            (Studio_ID, Studio_name, Studio_mobile, Studio_Email,
                             Owner_name, Password, Studio_Logo)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """;

                try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
                    statement.setString(1, studioId);
                    statement.setString(2, studioName);
                    statement.setString(3, studioMobile);
                    statement.setString(4, studioEmail);
                    statement.setString(5, ownerName);
                    statement.setString(6, passwordEncoder.encode(temporaryPassword));
                    if (logoBytes == null) {
                        statement.setNull(7, java.sql.Types.BLOB);
                    } else {
                        statement.setBytes(7, logoBytes);
                    }
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE subscription SET studio_ID = ? WHERE id = ? AND studio_ID IS NULL")) {
                    statement.setString(1, studioId);
                    statement.setInt(2, subscriptionId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("This subscription has already been used.");
                    }
                }

                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return registrationFailure("Unable to register studio. Please contact support if payment was taken.");
        }

        request.changeSessionId();
        session.setAttribute("studio_ID", studioId);
        session.setAttribute("studio_name", studioName);
        session.setAttribute("owner_name", ownerName);
        session.setAttribute("registrationStudioId", studioId);
        session.setAttribute("registrationTemporaryPassword", temporaryPassword);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("redirectUrl", "/studio_registration/credentials");
        return response;
    }

    private Map<String, Object> registrationFailure(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }

    private boolean isSupportedPlan(String plan) {
        return "1_MONTH".equals(plan)
                || "3_MONTH".equals(plan)
                || "1_YEAR".equals(plan)
                || "3_DAY_TRIAL".equals(plan)
                || "7_DAY_TRIAL".equals(plan)
                || "14_DAY_TRIAL".equals(plan);
    }

    private boolean exists(Connection connection, String sql, String value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private int findPaidSubscription(
            Connection connection,
            String orderId,
            String email,
            String plan
    ) throws Exception {
        String sql = """
                SELECT id
                FROM subscription
                WHERE razorpay_order_id = ?
                  AND email = ?
                  AND plan = ?
                  AND status = 'ACTIVE'
                  AND studio_ID IS NULL
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);
            statement.setString(2, email);
            statement.setString(3, plan);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("id") : 0;
            }
        }
    }
}
