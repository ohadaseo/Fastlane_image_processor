package com.azure.fastlane;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class BillingRecord {

        @SerializedName("license_number")
        @Expose
        private String licenseNumber;
        @SerializedName("image_blob_url")
        @Expose
        private String blobURL;

        public BillingRecord(String licenseNumber, String url) {
            this.licenseNumber = licenseNumber;
            this.blobURL = url;

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

}
