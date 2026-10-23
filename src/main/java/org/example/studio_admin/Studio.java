package org.example.studio_admin;

public class Studio {
    private String studio_ID;
    private String studio_name;
    private long studio_mobile; // Changed to long to avoid int overflow for 10-digit numbers
    private String studio_Email;
    private String owner_name;
    private String password;
    private String studio_logo; // Base64 encoded string

    // Getters and Setters
    public String getStudio_ID() { return studio_ID; }
    public void setStudio_ID(String studio_ID) { this.studio_ID = studio_ID; }

    public String getStudio_name() { return studio_name; }
    public void setStudio_name(String studio_name) { this.studio_name = studio_name; }

    public long getStudio_mobile() { return studio_mobile; }
    public void setStudio_mobile(long studio_mobile) { this.studio_mobile = studio_mobile; }

    public String getStudio_Email() { return studio_Email; }
    public void setStudio_Email(String studio_Email) { this.studio_Email = studio_Email; }

    public String getOwner_name() { return owner_name; }
    public void setOwner_name(String owner_name) { this.owner_name = owner_name; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getStudio_logo() { return studio_logo; }
    public void setStudio_logo(String studio_logo) { this.studio_logo = studio_logo; }
}