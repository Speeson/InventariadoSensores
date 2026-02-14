package com.niimbot.jcdemo.print.model.template;

import com.google.gson.annotations.SerializedName;

public class InitDrawingBoardParam {
    private float width;
    private float height;
    private int rotate;
    private String path;
    private float verticalShift;

    @SerializedName("HorizontalShift") // Handle uppercase field name
    private float horizontalShift;
    
    /**
     * Constructor with all parameters
     * @param width Drawing board width
     * @param height Drawing board height
     * @param rotate Rotation angle
     * @param path File path
     * @param verticalShift Vertical offset
     * @param horizontalShift Horizontal offset
     * @throws IllegalArgumentException If path is null
     */
    public InitDrawingBoardParam(float width, float height, int rotate, String path, float verticalShift, float horizontalShift) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        this.width = width;
        this.height = height;
        this.rotate = rotate;
        this.path = path;
        this.verticalShift = verticalShift;
        this.horizontalShift = horizontalShift;
    }


    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public int getRotate() {
        return rotate;
    }

    public void setRotate(int rotate) {
        this.rotate = rotate;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Path cannot be null");
        }
        this.path = path;
    }

    public float getVerticalShift() {
        return verticalShift;
    }

    public void setVerticalShift(float verticalShift) {
        this.verticalShift = verticalShift;
    }

    public float getHorizontalShift() {
        return horizontalShift;
    }

    public void setHorizontalShift(float horizontalShift) {
        this.horizontalShift = horizontalShift;
    }
}
