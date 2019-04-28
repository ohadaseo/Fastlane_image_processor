package com.azure.fastlane;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.sql.Timestamp;
import java.util.Date;

public class BillingRecord {

    @SerializedName("Timestamp")
    @Expose
    private String timestamp;
    @SerializedName("license_number")
    @Expose
    private String licenseNumber;
    @SerializedName("image_blob_url")
    @Expose
    private String blobURL;

    public BillingRecord(String url, String licenseNumber) {
        this.licenseNumber = licenseNumber;
        this.blobURL = url;
        generateTimeStamp();
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getBlobURL() {
        return blobURL;
    }

    public void setBlobURL(String blobURL) {
        this.blobURL = blobURL;
    }

    public void generateTimeStamp() {
        Date date = new Date();

        long time = date.getTime();

        Timestamp ts = new Timestamp(time);
        timestamp = ts.toString();
    }
}
