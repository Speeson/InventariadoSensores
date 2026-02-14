package com.niimbot.jcdemo.print.model.element;

public class QrCodeElement extends Element {
    protected QrCodeJson json = null;


    /**
     * Full parameter constructor
     *
     * @param type Element type
     * @param json QrCodeJson object
     * @throws IllegalArgumentException If any parameter is null or empty
     */
    public QrCodeElement(String type, QrCodeJson json) {
        super(type);
        if (json == null) {
            throw new IllegalArgumentException("QrCodeJson cannot be null");
        }
        this.json = json;
    }

    public QrCodeJson getJson() {
        return json;
    }

    public void setJson(QrCodeJson json) {
        if (json == null) {
            throw new IllegalArgumentException("QrCodeJson cannot be null");
        }
        this.json = json;
    }

    public static class QrCodeJson {
        private float x;
        private float y;
        private float height;
        private float width;
        private String value;
        private int codeType;
        private int rotate;


        /**
         * Full parameter constructor
         *
         * @param x        X coordinate
         * @param y        Y coordinate
         * @param width    Width
         * @param height   Height
         * @param value    QR code value
         * @param codeType QR code type
         * @param rotate   Rotation angle
         * @throws IllegalArgumentException If value is null
         */
        public QrCodeJson(float x, float y, float width, float height, String value, int codeType, int rotate) {
            if (value == null) {
                throw new IllegalArgumentException("Value cannot be null");
            }
            this.x = x;
            this.y = y;
            this.height = height;
            this.width = width;
            this.value = value;
            this.codeType = codeType;
            this.rotate = rotate;
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

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            if (value == null) {
                throw new IllegalArgumentException("Value cannot be null");
            }
            this.value = value;
        }

        public int getCodeType() {
            return codeType;
        }

        public void setCodeType(int codeType) {
            this.codeType = codeType;
        }

        public int getRotate() {
            return rotate;
        }

        public void setRotate(int rotate) {
            this.rotate = rotate;
        }
    }
}
