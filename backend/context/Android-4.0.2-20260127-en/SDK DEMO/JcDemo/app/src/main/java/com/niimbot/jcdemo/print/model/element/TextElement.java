package com.niimbot.jcdemo.print.model.element;

import com.google.gson.annotations.SerializedName;

public class TextElement extends Element{

    protected TextJson json = null;

    
    /**
     * Full parameter constructor
     * @param type Element type
     * @param json TextJson object
     * @throws IllegalArgumentException If any parameter is null or empty
     */
    public TextElement(String type, TextJson json) {
        super(type);
        if (json == null) {
            throw new IllegalArgumentException("TextJson cannot be null");
        }
        this.json = json;
    }

    public TextJson getJson() {
        return json;
    }

    public void setJson(TextJson json) {
        if (json == null) {
            throw new IllegalArgumentException("TextJson cannot be null");
        }
        this.json = json;
    }

    public static class TextJson{
        private float x;
        private float y;
        private float height;
        private float width;
        private String value;
        private String fontFamily;
        private int rotate;
        private float fontSize;
         @SerializedName("textAlignHorizontal")
        private int textAlignHorizontal;
        private int textAlignVertical;
        private float letterSpacing;
        private float lineSpacing;
        private int lineMode;
        private boolean[] fontStyle;
        

        
        /**
         * Full parameter constructor
         * @param x X coordinate
         * @param y Y coordinate
         * @param height Height
         * @param width Width
         * @param value Text value
         * @param fontFamily Font family
         * @param rotate Rotation angle
         * @param fontSize Font size
         * @param textAlignHorizontal Horizontal text alignment
         * @param textAlignVertical Vertical text alignment
         * @param letterSpacing Letter spacing
         * @param lineSpacing Line spacing
         * @param lineMode Line mode
         * @param fontStyle Font style array
         * @throws IllegalArgumentException If value or fontFamily is null
         */
        public TextJson(float x, float y, float height, float width, String value, String fontFamily, int rotate, float fontSize, int textAlignHorizontal, int textAlignVertical, float letterSpacing, float lineSpacing, int lineMode, boolean[] fontStyle) {
            if (value == null) {
                throw new IllegalArgumentException("Value cannot be null");
            }
            if (fontFamily == null) {
                throw new IllegalArgumentException("FontFamily cannot be null");
            }
            this.x = x;
            this.y = y;
            this.height = height;
            this.width = width;
            this.value = value;
            this.fontFamily = fontFamily;
            this.rotate = rotate;
            this.fontSize = fontSize;
            this.textAlignHorizontal = textAlignHorizontal;
            this.textAlignVertical = textAlignVertical;
            this.letterSpacing = letterSpacing;
            this.lineSpacing = lineSpacing;
            this.lineMode = lineMode;
            this.fontStyle = fontStyle != null ? fontStyle : new boolean[]{false, false, false};
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

        public String getFontFamily() {
            return fontFamily;
        }

        public void setFontFamily(String fontFamily) {
            if (fontFamily == null) {
                throw new IllegalArgumentException("FontFamily cannot be null");
            }
            this.fontFamily = fontFamily;
        }

        public int getRotate() {
            return rotate;
        }

        public void setRotate(int rotate) {
            this.rotate = rotate;
        }

        public float getFontSize() {
            return fontSize;
        }

        public void setFontSize(float fontSize) {
            this.fontSize = fontSize;
        }

        public int getTextAlignHorizontal() {
            return textAlignHorizontal;
        }

        public void setTextAlignHorizontal(int textAlignHorizontal) {
            this.textAlignHorizontal = textAlignHorizontal;
        }

        public int getTextAlignVertical() {
            return textAlignVertical;
        }

        public void setTextAlignVertical(int textAlignVertical) {
            this.textAlignVertical = textAlignVertical;
        }

        public float getLetterSpacing() {
            return letterSpacing;
        }

        public void setLetterSpacing(float letterSpacing) {
            this.letterSpacing = letterSpacing;
        }

        public float getLineSpacing() {
            return lineSpacing;
        }

        public void setLineSpacing(float lineSpacing) {
            this.lineSpacing = lineSpacing;
        }

        public int getLineMode() {
            return lineMode;
        }

        public void setLineMode(int lineMode) {
            this.lineMode = lineMode;
        }

        public boolean[] getFontStyle() {
            return fontStyle;
        }

        public void setFontStyle(boolean[] fontStyle) {
            this.fontStyle = fontStyle != null ? fontStyle : new boolean[]{false, false, false};
        }
    }

}
