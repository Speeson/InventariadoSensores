package com.niimbot.jcdemo.print.model.element;

public class QrCodeWithLogoElement extends Element {
    protected QrCodeWithLogoJson json = null;


    /**
     * Full parameter constructor
     *
     * @param type element type
     * @param json QrCodeJson object
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    public QrCodeWithLogoElement(String type, QrCodeWithLogoJson json) {
        super(type);
        if (json == null) {
            throw new IllegalArgumentException("QrCodeJson cannot be null");
        }
        this.json = json;
    }

    public QrCodeWithLogoJson getJson() {
        return json;
    }

    public void setJson(QrCodeWithLogoJson json) {
        if (json == null) {
            throw new IllegalArgumentException("QrCodeJson cannot be null");
        }
        this.json = json;
    }

    public static class QrCodeWithLogoJson {
        private float x;
        private float y;
        private float height;
        private float width;
        private String value;
        private int codeType;
        private int rotate;
        private int correctLevel;
        private String logoImageData;
        private int anchor;
        private float scale;



        /**
         * Full parameter constructor
         *
         * @param x        X coordinate
         * @param y        Y coordinate
         * @param width    width
         * @param height   height
         * @param value    QR code value
         * @param codeType QR code type
         * @param rotate   rotation angle
         * @throws IllegalArgumentException if value is null
         */
        public QrCodeWithLogoJson(float x, float y, float width, float height, String value, int codeType, int rotate, int correctLevel, String logoImageData, int anchor, float scale) {
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
            this.correctLevel = correctLevel;
            this.logoImageData = logoImageData;
            this.anchor = anchor;
            this.scale = scale;
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

        public int getCorrectLevel() {
            return correctLevel;
        }

        public void setCorrectLevel(int correctLevel) {
            this.correctLevel = correctLevel;
        }

        public String getLogoImageData() {
            return logoImageData;
        }

        public void setLogoImageData(String logoImageData) {
            this.logoImageData = logoImageData;
        }

        public int getAnchor() {
            return anchor;
        }

        public void setAnchor(int anchor) {
            this.anchor = anchor;
        }

        public float getScale() {
            return scale;
        }

        public void setScale(float scale) {
            this.scale = scale;
        }
    }
}
