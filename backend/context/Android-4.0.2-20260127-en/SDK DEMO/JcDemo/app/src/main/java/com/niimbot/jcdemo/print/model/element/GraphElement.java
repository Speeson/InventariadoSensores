package com.niimbot.jcdemo.print.model.element;

import com.google.gson.annotations.SerializedName;

public class GraphElement extends Element {
    protected GraphJson json = null;

    /**
     * Full parameter constructor
     *
     * @param type Element type
     * @param json Graph JSON object
     * @throws IllegalArgumentException If json is null
     */
    public GraphElement(String type, GraphJson json) {
        super(type);
        if (json == null) {
            throw new IllegalArgumentException("GraphJson cannot be null");
        }
        this.json = json;
    }

    public GraphJson getJson() {
        return json;
    }

    public void setJson(GraphJson json) {
        if (json == null) {
            throw new IllegalArgumentException("GraphJson cannot be null");
        }
        this.json = json;
    }

    public static class GraphJson {
        private float x;
        private float y;
        private float height;
        private float width;
        private int graphType;
        private int rotate;
        private float cornerRadius;
        private float lineWidth;
        private int lineType;
        @SerializedName("dashwidth")
        private float[] dashWidth;


        /**
         * Full parameter constructor
         *
         * @param x            X coordinate
         * @param y            Y coordinate
         * @param width        Width
         * @param height       Height
         * @param graphType    Graph type
         * @param rotate       Rotation angle
         * @param cornerRadius Corner radius
         * @param lineWidth    Line width
         * @param lineType     Line type
         * @param dashWidth    Dash width array
         */
        public GraphJson(float x, float y, float width, float height, int graphType,
                         int rotate, float cornerRadius, float lineWidth, int lineType, float[] dashWidth) {
            this.x = x;
            this.y = y;
            this.height = height;
            this.width = width;
            this.graphType = graphType;
            this.rotate = rotate;
            this.cornerRadius = cornerRadius;
            this.lineWidth = lineWidth;
            this.lineType = lineType;
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

        public float getHeight() {
            return height;
        }

        public void setHeight(float height) {
            this.height = height;
        }

        public float getWidth() {
            return width;
        }

        public void setWidth(float width) {
            this.width = width;
        }

        public int getGraphType() {
            return graphType;
        }

        public void setGraphType(int graphType) {
            this.graphType = graphType;
        }

        public int getRotate() {
            return rotate;
        }

        public void setRotate(int rotate) {
            this.rotate = rotate;
        }

        public float getCornerRadius() {
            return cornerRadius;
        }

        public void setCornerRadius(float cornerRadius) {
            this.cornerRadius = cornerRadius;
        }

        public float getLineWidth() {
            return lineWidth;
        }

        public void setLineWidth(float lineWidth) {
            this.lineWidth = lineWidth;
        }

        public int getLineType() {
            return lineType;
        }

        public void setLineType(int lineType) {
            this.lineType = lineType;
        }

        public float[] getDashWidth() {
            return dashWidth;
        }

        public void setDashWidth(float[] dashWidth) {
            this.dashWidth = dashWidth != null ? dashWidth : new float[]{1.0f, 1.0f};
        }
    }
}
