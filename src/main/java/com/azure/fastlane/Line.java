package com.azure.fastlane;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Line {

    @SerializedName("boundingBox")
    @Expose
    private String boundingBox;
    @SerializedName("words")
    @Expose
    private List<Region.Word> words = null;

    public String getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(String boundingBox) {
        this.boundingBox = boundingBox;
    }

    public List<Region.Word> getWords() {
        return words;
    }

    public void setWords(List<Region.Word> words) {
        this.words = words;
    }
}
