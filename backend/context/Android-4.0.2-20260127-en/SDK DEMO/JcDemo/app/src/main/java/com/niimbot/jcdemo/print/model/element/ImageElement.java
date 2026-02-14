package com.niimbot.jcdemo.print.model.element;

public class ImageElement extends Element {
    protected ImageJson json = null;

    /**
     * Full parameter constructor
     *
     * @param type Element type
     * @param json Image JSON object
     * @throws IllegalArgumentException If json is null
     */
    public ImageElement(String type, ImageJson json) {
        super(type);
        if (json == null) {
            throw new IllegalArgumentException("ImageJson cannot be null");
        }
        this.json = json;
    }

    public ImageJson getJson() {
        return json;
    }

    public void setJson(ImageJson json) {
        if (json == null) {
            throw new IllegalArgumentException("ImageJson cannot be null");
        }
        this.json = json;
    }

    public static class ImageJson {
        private float x;
        private float y;
        private float width;
        private float height;
        private int rotate;
        private String imageData;
        private int imageProcessingType;
        private float imageProcessingValue;


        /**
         * Full parameter constructor
         *
         * @param x                    X coordinate
         * @param y                    Y coordinate
         * @param width                Width
         * @param height               Height
         * @param rotate               Rotation angle
         * @param imageData            Image data
         * @param imageProcessingType  Image processing type
         * @param imageProcessingValue Image processing value
         * @throws IllegalArgumentException If imageData is null
         */
        public ImageJson(float x, float y, float width, float height, int rotate,
                         String imageData, int imageProcessingType, float imageProcessingValue) {
            if (imageData == null) {
                throw new IllegalArgumentException("Image data cannot be null");
            }
            this.x = x;
            this.y = y;
            this.height = height;
            this.width = width;
            this.rotate = rotate;
            this.imageData = imageData;
            this.imageProcessingType = imageProcessingType;
            this.imageProcessingValue = imageProcessingValue;
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

        public int getRotate() {
            return rotate;
        }

        public void setRotate(int rotate) {
            this.rotate = rotate;
        }

        public String getImageData() {
            return imageData;
        }

        public void setImageData(String imageData) {
            if (imageData == null) {
                throw new IllegalArgumentException("Image data cannot be null");
            }
            this.imageData = imageData;
        }

        public int getImageProcessingType() {
            return imageProcessingType;
        }

        public void setImageProcessingType(int imageProcessingType) {
            this.imageProcessingType = imageProcessingType;
        }

        public float getImageProcessingValue() {
            return imageProcessingValue;
        }

        public void setImageProcessingValue(float imageProcessingValue) {
            this.imageProcessingValue = imageProcessingValue;
        }
    }

}
