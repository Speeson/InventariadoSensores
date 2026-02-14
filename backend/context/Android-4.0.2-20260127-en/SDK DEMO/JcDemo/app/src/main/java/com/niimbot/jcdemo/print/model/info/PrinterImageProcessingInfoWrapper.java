package com.niimbot.jcdemo.print.model.info;

import com.google.gson.annotations.SerializedName;

public class PrinterImageProcessingInfoWrapper {
    @SerializedName("printerImageProcessingInfo")
    private PrinterImageProcessingInfo info;
    public PrinterImageProcessingInfoWrapper(PrinterImageProcessingInfo info) {
        this.info = info;
    }

    public PrinterImageProcessingInfo getInfo() {
        return info;
    }

    public void setInfo(PrinterImageProcessingInfo info) {
        this.info = info;
    }
}
