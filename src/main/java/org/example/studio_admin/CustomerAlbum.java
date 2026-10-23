package org.example.studio_admin;

public class CustomerAlbum {

    private int album_id;

    private int customer_id;

    private String album_name;


    public int getAlbum_id() {

        return album_id;

    }


    public void setAlbum_id(
            int album_id
    ) {

        this.album_id = album_id;

    }


    public int getCustomer_id() {

        return customer_id;

    }


    public void setCustomer_id(
            int customer_id
    ) {

        this.customer_id = customer_id;

    }


    public String getAlbum_name() {

        return album_name;

    }


    public void setAlbum_name(
            String album_name
    ) {

        this.album_name = album_name;

    }

}