package com.niimbot.jcdemo.print.model.element;

import com.google.gson.annotations.SerializedName;

public class LineElement extends Element{
    protected LineJson json = null;

    /**
     * Full parameter constructor
     * @param type Element type
     * @param json Line JSON object
     * @throws IllegalArgumentException If json is null
     */
    public LineElement(String type, LineJson json) {
        super(type);
        if (json == null) {
            throw new IllegalArgumentException("LineJson cannot be null");
        }
        this.json = json;
    }

    public LineJson getJson() {
        return json;
    }

    public void setJson(LineJson json) {
        if (json == null) {
            throw new IllegalArgumentException("LineJson cannot be null");
        }
        this.json = json;
    }

    public static class LineJson{
        private float x;
        private float y;
        private float width;
        private float height;

        private int lineType;
        private int rotate;
        @SerializedName("dashwidth")
        private float[] dashWidth;
        

        
        /**
         * Full parameter constructor
         * @param x X coordinate
         * @param y Y coordinate
         * @param width Width
         * @param height Height
         * @param lineType Line type
         * @param rotate Rotation angle
         * @param dashWidth Dash width array
         */
        public LineJson(float x, float y, float width, float height, int lineType,
                       int rotate, float[] dashWidth) {
            this.x = x;
            this.y = y;
            this.height = height;
            this.width = width;
            this.lineType = lineType;
            this.rotate = rotate;
            this.dashWidth = dashWidth != null ? dashWidth : new float[]{5.0f, 5.0f};
        }

        public float getX() {
            return x;
        }

        public void setX(float x) {
            this.x = x;
        }

        public float getY() {
            return y;
        }

        public void setY(float y) {
            this.y = y;
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

        public int getLineType() {
            return lineType;
        }

        public void setLineType(int lineType) {
            this.lineType = lineType;
        }

        public int getRotate() {
            return rotate;
        }

        public void setRotate(int rotate) {
            this.rotate = rotate;
        }

        public float[] getDashWidth() {
            return dashWidth;
        }

        public void setDashWidth(float[] dashWidth) {
            this.dashWidth = dashWidth != null ? dashWidth : new float[]{5.0f, 5.0f};
        }

        public float[] getDashwidth() {
            return this.dashWidth;
        }
    }
}
