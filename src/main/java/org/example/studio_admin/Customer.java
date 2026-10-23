package org.example.studio_admin;

import java.util.List;

public class Customer {

    private int customer_id;

    private String studio_ID;

    private String customer_name;

    private String phone;

    private String address;

    private List<CustomerImage> images;


    // ==========================================
    // CUSTOMER ID
    // ==========================================

    public int getCustomer_id() {
        return customer_id;
    }

    public void setCustomer_id(int customer_id) {
        this.customer_id = customer_id;
    }


    // ==========================================
    // STUDIO ID
    // ==========================================

    public String getStudio_ID() {
        return studio_ID;
    }

    public void setStudio_ID(String studio_ID) {
        this.studio_ID = studio_ID;
    }


    // ==========================================
    // CUSTOMER NAME
    // ==========================================

    public String getCustomer_name() {
        return customer_name;
    }

    public void setCustomer_name(String customer_name) {
        this.customer_name = customer_name;
    }


    // ==========================================
    // PHONE
    // ==========================================

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }


    // ==========================================
    // ADDRESS
    // ==========================================

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }


    // ==========================================
    // IMAGES
    // ==========================================

    public List<CustomerImage> getImages() {
        return images;
    }

    public void setImages(List<CustomerImage> images) {
        this.images = images;
    }
}