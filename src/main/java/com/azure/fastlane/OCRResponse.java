package com.azure.fastlane;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class OCRResponse {

    @SerializedName("orientation")
    @Expose
    private String orientation;
    @SerializedName("regions")
    @Expose
    private List<Region> regions = null;
    @SerializedName("textAngle")
    @Expose
    private Integer textAngle;
    @SerializedName("language")
    @Expose
    private String language;

    public String getOrientation() {
        return orientation;
    }

    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public List<Region> getRegions() {
        return regions;
    }

    public void setRegions(List<Region> regions) {
        this.regions = regions;
    }

    public Integer getTextAngle() {
        return textAngle;
    }

    public void setTextAngle(Integer textAngle) {
        this.textAngle = textAngle;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
