package org.example.studio_admin;

import jakarta.servlet.http.HttpSession;
import org.example.studio_admin.models.connectionDb;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Controller
public class studio_login {

    private final connectionDb db = new connectionDb();

    // PasswordEncoder injected by Spring
    private final PasswordEncoder passwordEncoder;

    // Constructor injection
    public studio_login(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    // =====================================================
    // SHOW LOGIN PAGE
    // =====================================================

    @GetMapping("/studio_login")
    public String loginPage() {
        return "studio_login";
    }

    // =====================================================
    // VERIFY STUDIO LOGIN
    // =====================================================

    @PostMapping("/customer_home_page")
    public String verifyLogin(
            @RequestParam("studio_ID") String studio_ID,
            @RequestParam("password") String password,
            HttpSession session
    ) {

        String sql =
                "SELECT studio_ID, studio_name, owner_name, password " +
                        "FROM studio " +
                        "WHERE studio_ID = ?";

        try (
                Connection con = db.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)
        ) {

            ps.setString(1, studio_ID);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {

                    String id = rs.getString("studio_ID");
                    String studioName = rs.getString("studio_name");
                    String ownerName = rs.getString("owner_name");
                    String storedPassword = rs.getString("password");

                    // =================================================
                    // CHECK PASSWORD
                    // =================================================

                    if (!passwordEncoder.matches(password, storedPassword)) {

                        session.setAttribute(
                                "loginError",
                                "Invalid Studio ID or Password"
                        );

                        return "redirect:/studio_login";
                    }

                    // =================================================
                    // LOGIN SUCCESS
                    // =================================================

                    session.setAttribute(
                            "studio_ID",
                            id
                    );

                    session.setAttribute(
                            "studio_name",
                            studioName
                    );

                    session.setAttribute(
                            "owner_name",
                            ownerName
                    );

                    session.removeAttribute("loginError");

                    System.out.println("Studio Login Successful");
                    System.out.println("Studio ID: " + id);
                    System.out.println("Studio Name: " + studioName);
                    System.out.println("Owner Name: " + ownerName);

                    return "redirect:/customer_home";
                }
            }

            // =================================================
            // STUDIO ID NOT FOUND
            // =================================================

            session.setAttribute(
                    "loginError",
                    "Invalid Studio ID or Password"
            );

            return "redirect:/studio_login";

        } catch (Exception e) {

            e.printStackTrace();

            session.setAttribute(
                    "loginError",
                    "Database error: " + e.getMessage()
            );

            return "redirect:/studio_login";
        }
    }

    // =====================================================
    // LOGOUT
    // =====================================================

    @GetMapping("/studio_logout")
    public String logout(HttpSession session) {

        session.invalidate();

        return "redirect:/studio_login";
    }
}