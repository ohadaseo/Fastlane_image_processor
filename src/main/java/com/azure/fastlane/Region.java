package com.azure.fastlane;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Region {

    @SerializedName("boundingBox")
    @Expose
    private String boundingBox;
    @SerializedName("lines")
    @Expose
    private List<Line> lines = null;

    public String getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(String boundingBox) {
        this.boundingBox = boundingBox;
    }

    public List<Line> getLines() {
        return lines;
    }

    public void setLines(List<Line> lines) {
        this.lines = lines;
    }


    public class Word {

        @SerializedName("boundingBox")
        @Expose
        private String boundingBox;
        @SerializedName("text")
        @Expose
        private String text;

        public String getBoundingBox() {
            return boundingBox;
        }

        public void setBoundingBox(String boundingBox) {
            this.boundingBox = boundingBox;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

    }
}
