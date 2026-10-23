package org.example.studio_admin;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;

import org.example.studio_admin.models.connectionDb;

import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.time.LocalDate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final RazorpayClient razorpayClient;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;



    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public SubscriptionController(RazorpayClient razorpayClient) {
        this.razorpayClient = razorpayClient;
    }


    // =========================================================
    // CREATE RAZORPAY ORDER
    // =========================================================

    @PostMapping("/create-order")
    public Map<String, Object> createOrder(

            @RequestParam("name") String name,

            @RequestParam("phone") String phone,

            @RequestParam("email") String email,

            @RequestParam("plan") String plan
    ) {

        Map<String, Object> response =
                new HashMap<>();

        try {

            System.out.println("=================================");
            System.out.println("Creating Razorpay Order");
            System.out.println("Name  : " + name);
            System.out.println("Phone : " + phone);
            System.out.println("Email : " + email);
            System.out.println("Plan  : " + plan);


            // -------------------------------------------------
            // PLAN
            // -------------------------------------------------

            int amount;

            switch (plan) {

                case "1_MONTH":
                    amount = 99;
                    break;

                case "3_MONTH":
                    amount = 249;
                    break;

                case "1_YEAR":
                    amount = 799;
                    break;

                case "3_DAY_TRIAL":
                    amount = 1;
                    break;

                case "7_DAY_TRIAL":
                    amount = 29;
                    break;

                case "14_DAY_TRIAL":
                    amount = 59;
                    break;

                default:

                    response.put(
                            "success",
                            false
                    );

                    response.put(
                            "message",
                            "Invalid subscription plan."
                    );

                    return response;
            }


            // -------------------------------------------------
            // INR TO PAISE
            // -------------------------------------------------

            int amountInPaise =
                    amount * 100;


            // -------------------------------------------------
            // RECEIPT
            // -------------------------------------------------

            String receipt =
                    "studio_"
                            + UUID.randomUUID()
                            .toString()
                            .substring(0, 8);


            // -------------------------------------------------
            // RAZORPAY ORDER REQUEST
            // -------------------------------------------------

            JSONObject orderRequest =
                    new JSONObject();

            orderRequest.put(
                    "amount",
                    amountInPaise
            );

            orderRequest.put(
                    "currency",
                    "INR"
            );

            orderRequest.put(
                    "receipt",
                    receipt
            );


            // -------------------------------------------------
            // NOTES
            // -------------------------------------------------

            JSONObject notes =
                    new JSONObject();

            notes.put(
                    "name",
                    name
            );

            notes.put(
                    "phone",
                    phone
            );

            notes.put(
                    "email",
                    email
            );

            notes.put(
                    "plan",
                    plan
            );

            orderRequest.put(
                    "notes",
                    notes
            );


            // -------------------------------------------------
            // CREATE RAZORPAY ORDER
            // -------------------------------------------------

            Order order =
                    razorpayClient.orders.create(
                            orderRequest
                    );

            String orderId =
                    order.get("id");


            System.out.println(
                    "Razorpay Order ID: "
                            + orderId
            );


            // -------------------------------------------------
            // SAVE PENDING PAYMENT
            // -------------------------------------------------

            String sql = """
                    INSERT INTO subscription
                    (
                        name,
                        phone,
                        email,
                        plan,
                        amount,
                        razorpay_order_id,
                        status
                    )
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING')
                    """;


            try (
                    Connection connection =
                            connectionDb.getConnection();

                    PreparedStatement statement =
                            connection.prepareStatement(sql)
            ) {

                statement.setString(
                        1,
                        name
                );

                statement.setString(
                        2,
                        phone
                );

                statement.setString(
                        3,
                        email
                );

                statement.setString(
                        4,
                        plan
                );

                statement.setInt(
                        5,
                        amountInPaise
                );

                statement.setString(
                        6,
                        orderId
                );

                statement.executeUpdate();
            }


            // -------------------------------------------------
            // RESPONSE
            // -------------------------------------------------

            response.put(
                    "success",
                    true
            );

            response.put(
                    "key",
                    razorpayKeyId
            );

            response.put(
                    "orderId",
                    orderId
            );

            response.put(
                    "amount",
                    amountInPaise
            );

            response.put(
                    "currency",
                    "INR"
            );

            response.put(
                    "name",
                    name
            );

            response.put(
                    "email",
                    email
            );

            response.put(
                    "phone",
                    phone
            );

            response.put(
                    "plan",
                    plan
            );


            System.out.println(
                    "Order created successfully"
            );

            System.out.println("=================================");

            return response;


        } catch (Exception e) {

            System.out.println(
                    "ERROR CREATING RAZORPAY ORDER"
            );

            e.printStackTrace();

            response.put(
                    "success",
                    false
            );

            response.put(
                    "message",
                    e.getMessage()
            );

            return response;
        }
    }


    // =========================================================
    // VERIFY RAZORPAY PAYMENT
    // =========================================================

    @PostMapping("/verify")
    public Map<String, Object> verifyPayment(

            @RequestParam("razorpayOrderId")
            String razorpayOrderId,

            @RequestParam("razorpayPaymentId")
            String razorpayPaymentId,

            @RequestParam("razorpaySignature")
            String razorpaySignature
    ) {

        Map<String, Object> response =
                new HashMap<>();

        try {

            System.out.println("=================================");
            System.out.println("PAYMENT VERIFICATION");
            System.out.println(
                    "Order ID   : "
                            + razorpayOrderId
            );
            System.out.println(
                    "Payment ID : "
                            + razorpayPaymentId
            );


            // -------------------------------------------------
            // IMPORTANT
            //
            // Razorpay Java SDK expects:
            //
            // Utils.verifyPaymentSignature(
            //      JSONObject,
            //      secret
            // )
            // -------------------------------------------------

            JSONObject verificationData =
                    new JSONObject();

            verificationData.put(
                    "razorpay_order_id",
                    razorpayOrderId
            );

            verificationData.put(
                    "razorpay_payment_id",
                    razorpayPaymentId
            );

            verificationData.put(
                    "razorpay_signature",
                    razorpaySignature
            );


            // -------------------------------------------------
            // VERIFY SIGNATURE
            // -------------------------------------------------

            boolean verified =
                    Utils.verifyPaymentSignature(
                            verificationData,
                            razorpayKeySecret
                    );


            if (!verified) {

                System.out.println(
                        "PAYMENT SIGNATURE INVALID"
                );

                response.put(
                        "success",
                        false
                );

                response.put(
                        "message",
                        "Payment signature verification failed."
                );

                return response;
            }


            System.out.println(
                    "PAYMENT SIGNATURE VERIFIED"
            );


            // -------------------------------------------------
            // FIND SUBSCRIPTION USING ORDER ID
            // -------------------------------------------------

            String selectSql = """
                    SELECT
                        id,
                        plan,
                        status
                    FROM subscription
                    WHERE razorpay_order_id = ?
                    """;


            int subscriptionId;

            String plan;

            String status;


            try (
                    Connection connection =
                            connectionDb.getConnection();

                    PreparedStatement statement =
                            connection.prepareStatement(selectSql)
            ) {

                statement.setString(
                        1,
                        razorpayOrderId
                );


                try (
                        ResultSet rs =
                                statement.executeQuery()
                ) {

                    if (!rs.next()) {

                        response.put(
                                "success",
                                false
                        );

                        response.put(
                                "message",
                                "Subscription record not found."
                        );

                        return response;
                    }


                    subscriptionId =
                            rs.getInt("id");

                    plan =
                            rs.getString("plan");

                    status =
                            rs.getString("status");
                }
            }


            // -------------------------------------------------
            // ALREADY VERIFIED
            // -------------------------------------------------

            if ("ACTIVE".equalsIgnoreCase(status)) {

                response.put(
                        "success",
                        true
                );

                response.put(
                        "message",
                        "Payment already verified."
                );

                return response;
            }


            // -------------------------------------------------
            // CALCULATE DATES
            // -------------------------------------------------

            LocalDate startDate =
                    LocalDate.now();

            LocalDate expiryDate;


            switch (plan) {

                case "1_MONTH":

                    expiryDate =
                            startDate.plusMonths(1);

                    break;


                case "3_MONTH":

                    expiryDate =
                            startDate.plusMonths(3);

                    break;


                case "1_YEAR":

                    expiryDate =
                            startDate.plusYears(1);

                    break;


                case "3_DAY_TRIAL":

                    expiryDate =
                            startDate.plusDays(3);

                    break;


                case "7_DAY_TRIAL":

                    expiryDate =
                            startDate.plusDays(7);

                    break;


                case "14_DAY_TRIAL":

                    expiryDate =
                            startDate.plusDays(14);

                    break;


                default:

                    response.put(
                            "success",
                            false
                    );

                    response.put(
                            "message",
                            "Invalid subscription plan."
                    );

                    return response;
            }


            // -------------------------------------------------
            // UPDATE PAYMENT
            // -------------------------------------------------

            String updateSql = """
                    UPDATE subscription
                    SET
                        razorpay_payment_id = ?,
                        razorpay_signature = ?,
                        start_date = ?,
                        expiry_date = ?,
                        status = 'ACTIVE'
                    WHERE id = ?
                    """;


            try (
                    Connection connection =
                            connectionDb.getConnection();

                    PreparedStatement statement =
                            connection.prepareStatement(updateSql)
            ) {

                statement.setString(
                        1,
                        razorpayPaymentId
                );

                statement.setString(
                        2,
                        razorpaySignature
                );

                statement.setString(
                        3,
                        startDate.toString()
                );

                statement.setString(
                        4,
                        expiryDate.toString()
                );

                statement.setInt(
                        5,
                        subscriptionId
                );

                statement.executeUpdate();
            }


            // -------------------------------------------------
            // SUCCESS
            // -------------------------------------------------

            System.out.println(
                    "PAYMENT VERIFIED SUCCESSFULLY"
            );

            System.out.println(
                    "Plan       : " + plan
            );

            System.out.println(
                    "Start Date : " + startDate
            );

            System.out.println(
                    "Expiry     : " + expiryDate
            );

            System.out.println("=================================");


            response.put(
                    "success",
                    true
            );

            response.put(
                    "message",
                    "Payment verified successfully."
            );

            response.put(
                    "plan",
                    plan
            );

            response.put(
                    "startDate",
                    startDate.toString()
            );

            response.put(
                    "expiryDate",
                    expiryDate.toString()
            );


            return response;


        } catch (Exception e) {

            System.out.println(
                    "PAYMENT VERIFICATION ERROR"
            );

            e.printStackTrace();

            response.put(
                    "success",
                    false
            );

            response.put(
                    "message",
                    e.getMessage()
            );

            return response;
        }
    }
}