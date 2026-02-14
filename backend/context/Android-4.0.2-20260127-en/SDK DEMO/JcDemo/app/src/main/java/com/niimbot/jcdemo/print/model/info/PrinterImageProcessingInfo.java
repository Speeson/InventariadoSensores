package com.niimbot.jcdemo.print.model.info;

public class PrinterImageProcessingInfo {
    private int orientation;
    private int[] margin;
    private int printQuantity;
    private float horizontalOffset;
    private float verticalOffset;
    private float width;
    private float height;
    private String printMultiple;
    private String epc;
    
    /**
     * Constructor with complete parameters
     * @param orientation Rotation angle
     * @param margin Margin array [top, right, bottom, left]
     * @param printQuantity Number of copies to print
     * @param horizontalOffset Horizontal offset
     * @param verticalOffset Vertical offset
     * @param width Width
     * @param height Height
     * @param printMultiple Print multiple
     * @param epc EPC value
     */
    public PrinterImageProcessingInfo(int orientation, int[] margin, int printQuantity,
                                     float horizontalOffset, float verticalOffset,
                                     float width, float height, String printMultiple, String epc) {
        this.orientation = orientation;
        this.margin = margin != null ? margin : new int[4];
        this.printQuantity = printQuantity;
        this.horizontalOffset = horizontalOffset;
        this.verticalOffset = verticalOffset;
        this.width = width;
        this.height = height;
        this.printMultiple = printMultiple;
        this.epc = epc;
    }
    

    
    
    
   
  

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public int[] getMargin() {
        return margin;
    }

    public void setMargin(int[] margin) {
        this.margin = margin;
    }

    public int getPrintQuantity() {
        return printQuantity;
    }

    public void setPrintQuantity(int printQuantity) {
        this.printQuantity = printQuantity;
    }

    public float getHorizontalOffset() {
        return horizontalOffset;
    }

    public void setHorizontalOffset(float horizontalOffset) {
        this.horizontalOffset = horizontalOffset;
    }

    public float getVerticalOffset() {
        return verticalOffset;
    }

    public void setVerticalOffset(float verticalOffset) {
        this.verticalOffset = verticalOffset;
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

    public String getPrintMultiple() {
        return printMultiple;
    }

    public void setPrintMultiple(String printMultiple) {
        this.printMultiple = printMultiple;
    }

    public String getEpc() {
        return epc;
    }

    public void setEpc(String epc) {
        this.epc = epc;
    }
}
