package com.niimbot.jcdemo.print.data;


import android.util.Log;

import com.niimbot.jcdemo.print.data.json.JsonProcessor;
import com.niimbot.jcdemo.print.model.element.BarCodeElement;
import com.niimbot.jcdemo.print.model.element.Element;
import com.niimbot.jcdemo.print.model.element.GraphElement;
import com.niimbot.jcdemo.print.model.element.ImageElement;
import com.niimbot.jcdemo.print.model.template.InitDrawingBoardParam;
import com.niimbot.jcdemo.print.model.element.LineElement;
import com.niimbot.jcdemo.print.model.template.PrintTemplate;
import com.niimbot.jcdemo.print.model.info.PrinterImageProcessingInfo;
import com.niimbot.jcdemo.print.model.info.PrinterImageProcessingInfoWrapper;
import com.niimbot.jcdemo.print.model.element.QrCodeElement;
import com.niimbot.jcdemo.print.model.element.TextElement;


import java.util.ArrayList;
import java.util.List;

public class PrintData {
    private static final String TAG = "PrintData";




    /**
     * Get print data
     * @param type Print type
     * @param copies Number of copies
     * @param multiple Magnification, 8.0 for 200dpi printer, 11.81 for 300dpi printer
     * @return List of print data
     */
    public static List<ArrayList<String>> getPrintData(String type,int copies,  float multiple) {
        return switch (type) {
            case "text" -> getTextPrintData(copies, multiple);
            case "image" -> getImagePrintData(copies, multiple);
            case "qrcode" -> getQrCodePrintData(copies, multiple);
            case "barcode" -> getBarCodePrintData(copies, multiple);
            case "line" -> getLinePrintData(copies, multiple);
            case "graph" -> getGraphPrintData(copies, multiple);
            case "batch" -> getBatchPrintData(copies, multiple);
            default -> null;
        };
    }

    public static List<ArrayList<String>> getBatchPrintData(int copies, float multiple) {
        float offsetX = 0f;
        float offsetY = 0f;
        float width = 50f;
        float height = 20f;
        int rotate = 0;
        float marginX = 2.0f;
        float marginY = 2.0f;
        float qrCodeHeight = height - marginY * 2.0f;
        float qrCodeWidth = height - marginY * 2.0f;
        float contentWidth = width - marginX * 2.0f;
        float fontSize = 3.2f;

        // Create print template object
        List<PrintTemplate> templates = new ArrayList<>();

        // First template
        InitDrawingBoardParam param1 = new InitDrawingBoardParam(width, height, rotate, "ZT001.ttf", 0, 0);
        List<Element> elements1 = new ArrayList<>();

        // Add QR code element
        QrCodeElement.QrCodeJson qrCodeJson1 = new QrCodeElement.QrCodeJson(marginX + offsetX, marginY + offsetY, qrCodeHeight, qrCodeWidth, "12345678", 31, 0);
        QrCodeElement qrCodeElement1 = new QrCodeElement("qrCode", qrCodeJson1);
        elements1.add(qrCodeElement1);

        // Add text element
        TextElement.TextJson textJson1 = new TextElement.TextJson(marginX * 2 + qrCodeWidth + offsetX, marginY + offsetY, qrCodeHeight, contentWidth - qrCodeWidth - marginX, "Name: Wuhan Jingchen\nAge: 11\nType: Blood Test", "SimSun", 0, fontSize, 0, 1, 0.0f, 1.0f, 6, new boolean[]{true, false, false, false});
        TextElement textElement1 = new TextElement("text", textJson1);
        elements1.add(textElement1);

        PrintTemplate template1 = new PrintTemplate(param1, elements1);
        templates.add(template1);

        // Second template
        InitDrawingBoardParam param2 = new InitDrawingBoardParam(width, height, rotate, "ZT001.ttf", 0, 0);
        List<Element> elements2 = new ArrayList<>();

        // Add QR code element
        QrCodeElement.QrCodeJson qrCodeJson2 = new QrCodeElement.QrCodeJson(marginX + offsetX, marginY + offsetY, qrCodeHeight, qrCodeWidth, "12345678", 31, 0);
        QrCodeElement qrCodeElement2 = new QrCodeElement("qrCode", qrCodeJson2);
        elements2.add(qrCodeElement2);

        // Add text element
        TextElement.TextJson textJson2 = new TextElement.TextJson(marginX * 2 + qrCodeWidth + offsetX, marginY + offsetY, qrCodeHeight, contentWidth - qrCodeWidth - marginX, "Name: Wuhan Jingchen\nAge: 12\nType: Blood Test", "SimSun", 0, fontSize, 0, 1, 0.0f, 1.0f, 6, new boolean[]{true, false, false, false});
        TextElement textElement2 = new TextElement("text", textJson2);
        elements2.add(textElement2);

        PrintTemplate template2 = new PrintTemplate(param2, elements2);
        templates.add(template2);

        // Create a list of print info objects, each template corresponds to an infoWrapper
        List<PrinterImageProcessingInfoWrapper> infoWrappers = new ArrayList<>();
        
        // Create corresponding infoWrapper for the first template
        PrinterImageProcessingInfo info1 = JsonProcessor.createPrinterImageProcessingInfo(rotate, copies, width, height, multiple);
        PrinterImageProcessingInfoWrapper infoWrapper1 = new PrinterImageProcessingInfoWrapper(info1);
        infoWrappers.add(infoWrapper1);
        
        // Create corresponding infoWrapper for the second template
        PrinterImageProcessingInfo info2 = JsonProcessor.createPrinterImageProcessingInfo(rotate, copies, width, height, multiple);
        PrinterImageProcessingInfoWrapper infoWrapper2 = new PrinterImageProcessingInfoWrapper(info2);
        infoWrappers.add(infoWrapper2);
        
        // Use JsonProcessor to directly process template objects, avoiding serialization/deserialization
        List<String> processedJsonData = JsonProcessor.processTemplateList(templates);
        
        // Use JsonProcessor to directly process infoWrapper objects, avoiding serialization/deserialization
        List<String> processedInfoData = JsonProcessor.processInfoWrapperList(infoWrappers);
        
        // Assemble final data structure
        List<ArrayList<String>> printData = new ArrayList<>();
        printData.add(new ArrayList<>(processedJsonData));
        printData.add(new ArrayList<>(processedInfoData));
        
        return printData;
    }

    public static List<ArrayList<String>> getImagePrintData(int copies, float multiple) {
        float offsetX = 0f;
        float offsetY = 0f;
        float width = 50f;
        float height = 30f;
        int rotate = 0;
        String imgData200 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAZAAAADwCAIAAAChXqV1AAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAAyVpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADw/eHBhY2tldCBiZWdpbj0i77u/IiBpZD0iVzVNME1wQ2VoaUh6cmVTek5UY3prYzlkIj8+IDx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IkFkb2JlIFhNUCBDb3JlIDYuMC1jMDA1IDc5LjE2NDU5MCwgMjAyMC8xMi8wOS0xMTo1Nzo0NCAgICAgICAgIj4gPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4gPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIiB4bWxuczp4bXBNTT0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wL21tLyIgeG1sbnM6c3RSZWY9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9zVHlwZS9SZXNvdXJjZVJlZiMiIHhtcDpDcmVhdG9yVG9vbD0iQWRvYmUgUGhvdG9zaG9wIDIyLjEgKE1hY2ludG9zaCkiIHhtcE1NOkluc3RhbmNlSUQ9InhtcC5paWQ6Qjk1MzA5NzlGQzU1MTFFREE3NDlCMzAzMjU0NzAyQzkiIHhtcE1NOkRvY3VtZW50SUQ9InhtcC5kaWQ6Qjk1MzA5N0FGQzU1MTFFREE3NDlCMzAzMjU0NzAyQzkiPiA8eG1wTU06RGVyaXZlZEZyb20gc3RSZWY6aW5zdGFuY2VJRD0ieG1wLmlpZDpCOTUzMDk3N0ZDNTUxMUVEQTc0OUIzMDMyNTQ3MDJDOSIgc3RSZWY6ZG9jdW1lbnRJRD0ieG1wLmRpZDpCOTUzMDk3OEZDNTUxMUVEQTc0OUIzMDMyNTQ3MDJDOSIvPiA8L3JkZjpEZXNjcmlwdGlvbj4gPC9yZGY6UkRGPiA8L3g6eG1wbWV0YT4gPD94cGFja2V0IGVuZD0iciI/Pvfq4t4AABGVSURBVHja7N1L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";

//        String imgData300 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAk4AAAFiCAIAAACPvcByAAAAGXRFWHRTb2Z0d2FyZQBBZG9iZSBJbWFnZVJlYWR5ccllPAAAAyVpVFh0WE1MOmNvbS5hZG9iZS54bXAAAAAAADw/eHBhY2tldCBiZWdpbj0i77u/IiBpZD0iVzVNME1wQ2VoaUh6cmVTek5UY3prYzlkIj8+IDx4OnhtcG1ldGEgeG1sbnM6eD0iYWRvYmU6bnM6bWV0YS8iIHg6eG1wdGs9IkFkb2JlIFhNUCBDb3JlIDYuMC1jMDA1IDc5LjE2NDU5MCwgMjAyMC8xMi8wOS0xMTo1Nzo0NCAgICAgICAgIj4gPHJkZjpSREYgeG1sbnM6cmRmPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5LzAyLzIyLXJkZi1zeW50YXgtbnMjIj4gPHJkZjpEZXNjcmlwdGlvbiByZGY6YWJvdXQ9IiIgeG1sbnM6eG1wPSJodHRwOi8vbnMuYWRvYmUuY29tL3hhcC8xLjAvIiB4bWxuczp4bXBNTT0iaHR0cDovL25zLmFkb2JlLmNvbS94YXAvMS4wL21tLyIgeG1sbnM6c3RSZWY9Imh0dHA6Ly9ucy5hZG9iZS5jb20veGFwLzEuMC9zVHlwZS9SZXNvdXJjZVJlZiMiIHhtcDpDcmVhdG9yVG9vbD0iQWRvYmUgUGhvdG9zaG9wIDIyLjEgKE1hY2ludG9zaCkiIHhtcE1NOkluc3RhbmNlSUQ9InhtcC5paWQ6Qjk1MzA5N0RGQzU1MTFFREE3NDlCMzAzMjU0NzAyQzkiIHhtcE1NOkRvY3VtZW50SUQ9InhtcC5kaWQ6Qjk1MzA5N0VGQzU1MTFFREE3NDlCMzAzMjU0NzAyQzkiPiA8eG1wTU06RGVyaXZlZEZyb20gc3RSZWY6aW5zdGFuY2VJRD0ieG1wLmlpZDpCOTUzMDk3QkZDNTUxMUVEQTc0OUIzMDMyNTQ3MDJDOSIgc3RSZWY6ZG9jdW1lbnRJRD0ieG1wLmRpZDpCOTUzMDk3Q0ZDNTUxMUVEQTc0OUIzMDMyNTQ3MDJDOSIvPiA8L3JkZjpEZXNjcmlwdGlvbj4gPC9yZGY6UkRGPiA8L3g6eG1wbWV0YT4gPD94cGFja2V0IGVuZD0iciI/PgNp8OYAABoeSURBVHja7N1f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";
        String img = imgData200.substring(22);

        img = img.replaceAll("[\r\n]+", ""); // Remove line breaks

        Log.d(TAG, "getImagePrintData: " + img);
        // Create print template object

        InitDrawingBoardParam param = new InitDrawingBoardParam(width, height, rotate, "ZT001.ttf", 0, 0);


        List<Element> elements = new ArrayList<>();


        ImageElement.ImageJson imageJson = new ImageElement.ImageJson(offsetX, offsetY, width, height, 0, img, 0, 127);
        // Add image element
        ImageElement imageElement = new ImageElement("image", imageJson);
        elements.add(imageElement);
        PrintTemplate template = new PrintTemplate(param, elements);
        
        // Create print info object
        PrinterImageProcessingInfo info = JsonProcessor.createPrinterImageProcessingInfo(rotate, copies, width, height, multiple);
        PrinterImageProcessingInfoWrapper infoWrapper = new PrinterImageProcessingInfoWrapper(info);
        
        // Use optimized method to process print data
        return JsonProcessor.processPrintData(template, infoWrapper, copies);
    }

    public static List<ArrayList<String>> getGraphPrintData(int copies, float multiple) {
        float offsetX = 0f;
        float offsetY = 0f;
        float width = 40f;
        float height = 20f;
        int rotate = 0;
        float marginX = 2.0f;
        float marginY = 2.0f;
        float lineWidth = 0.5f;

        // Create drawing board initialization parameters
        InitDrawingBoardParam initParam = new InitDrawingBoardParam(width, height, rotate, "ZT001.ttf", 0, 0);
        // Create graphic element
        List<Element> elements = new ArrayList<>();

        GraphElement.GraphJson graphJson = new GraphElement.GraphJson(marginX + offsetX, marginY + offsetY, width - marginX * 2, height - marginY * 2, 3, 0, 0, lineWidth, 0, new float[]{1, 1});

        GraphElement graphElement = new GraphElement("graph", graphJson);
        elements.add(graphElement);
        // Create print template object
        PrintTemplate template = new PrintTemplate(initParam, elements);
        
        // Create print info object
        PrinterImageProcessingInfo info = JsonProcessor.createPrinterImageProcessingInfo(rotate, copies, width, height, multiple);
        PrinterImageProcessingInfoWrapper infoWrapper = new PrinterImageProcessingInfoWrapper(info);
        
        // Use optimized method to process print data
        return JsonProcessor.processPrintData(template, infoWrapper, copies);
    }

    public static List<ArrayList<String>> getLinePrintData(int copies, float multiple) {
        float offsetX = 0f;
        float offsetY = 0f;
        float width = 40f;
        float height = 20f;
        int rotate = 0;
        float marginX = 2.0f;
        float marginY = 2.0f;
        float lineWidth = 0.5f;

        // Create drawing board initialization parameters
        InitDrawingBoardParam initParam = new InitDrawingBoardParam(width, height, rotate, "ZT001.ttf", 0, 0);

        // Create line element
        List<Element> elements = new ArrayList<>();

        // Top edge
        LineElement.LineJson topLineJson = new LineElement.LineJson(marginX + offsetX, marginY + offsetY, width - marginX * 2, lineWidth, 0, 0, new float[]{1, 1});
        LineElement topLine = new LineElement("line", topLineJson);
        elements.add(topLine);
        // Bottom edge
        LineElement.LineJson bottomLineJson = new LineElement.LineJson(marginX + offsetX, height - marginY + offsetY, width - marginX * 2, lineWidth, 0, 0, new float[]{1, 1});
        LineElement bottomLine = new LineElement("line", bottomLineJson);
        elements.add(bottomLine);
        // Left edge
        LineElement.LineJson leftLineJson = new LineElement.LineJson(marginX + offsetX, marginY + offsetY, lineWidth, height - marginY * 2, 0, 0, new float[]{1, 1});
        LineElement leftLine = new LineElement("line", leftLineJson);
        elements.add(leftLine);
        // Right edge
        LineElement.LineJson rightLineJson = new LineElement.LineJson(width - marginX + offsetX, marginY + offsetY, lineWidth, height - marginY * 2, 0, 0, new float[]{1, 1});
        LineElement rightLine = new LineElement("line", rightLineJson);
        elements.add(rightLine);
        
        // Create print template object
        PrintTemplate template = new PrintTemplate(initParam, elements);
        
        // Create print info object
        PrinterImageProcessingInfo info = JsonProcessor.createPrinterImageProcessingInfo(rotate, copies, width, height, multiple);
        PrinterImageProcessingInfoWrapper infoWrapper = new PrinterImageProcessingInfoWrapper(info);
        
        // Use optimized method to process print data
        return JsonProcessor.processPrintData(template, infoWrapper, copies);
    }

    public static List<ArrayList<String>> getBarCodePrintData(int copies, float multiple) {
        float offsetX = 0f;
        float offsetY = 0f;
        float width = 40f;
        float height = 20f;
        int rotate = 0;
        float marginX = 2.0f;
        float marginY = 2.0f;
        float barCodeWidth = width - marginX * 2.0f;
        float barCodeHeight = height - marginY * 2.0f;
        float fontSize = 3.2f;

        // Create drawing board initialization parameters
        InitDrawingBoardParam initParam = new InitDrawingBoardParam(width, height, rotate, "ZT001.ttf", 0, 0);
        // Create barcode element
        List<Element> elements = new ArrayList<>();
        BarCodeElement.BarCodeJson barCodeJson = new BarCodeElement.BarCodeJson(
                marginX + offsetX,
                marginY + offsetY,
                barCodeWidth, barCodeHeight, "12345678", 0, fontSize, 20, 3.2f, 0);
        BarCodeElement barCodeElement = new BarCodeElement("barCode", barCodeJson);
        elements.add(barCodeElement);

        // Create print template object
        PrintTemplate template = new PrintTemplate(initParam, elements);
        
        // Create print info object
        PrinterImageProcessingInfo info = JsonProcessor.createPrinterImageProcessingInfo(rotate, copies, width, height, multiple);
        PrinterImageProcessingInfoWrapper infoWrapper = new PrinterImageProcessingInfoWrapper(info);
        
        // Use optimized method to process print data
        return JsonProcessor.processPrintData(template, infoWrapper, copies);
    }

    public static List<ArrayList<String>> getQrCodePrintData(int copies, float multiple) {
        float offsetX = 0f;
        float offsetY = 0f;
        float width = 30f;
        float height = 30f;
        int rotate = 0;
        float marginX = 2.0f;
        float marginY = 2.0f;
        float qrCodeHeight = width - marginX * 2.0f;
        float qrCodeWidth = qrCodeHeight;

        // Create drawing board initialization parameters
        InitDrawingBoardParam initParam = new InitDrawingBoardParam(width, height, rotate, "ZT001.ttf", 0, 0);

        // Create QR code element
        // Create QR code element
        List<Element> elements = new ArrayList<>();
        QrCodeElement.QrCodeJson qrCodeJson = new QrCodeElement.QrCodeJson(
                marginX + offsetX,
                marginY + offsetY,
                qrCodeWidth,
                qrCodeHeight,
                "123456789",
                31,
                0
        );
        QrCodeElement qrCodeElement = new QrCodeElement("qrCode", qrCodeJson);
        elements.add(qrCodeElement);

        // Create print template object
        PrintTemplate template = new PrintTemplate(initParam, elements);
        
        // Create print info object
        PrinterImageProcessingInfo info = JsonProcessor.createPrinterImageProcessingInfo(rotate, copies, width, height, multiple);
        PrinterImageProcessingInfoWrapper infoWrapper = new PrinterImageProcessingInfoWrapper(info);
        
        // Use optimized method to process print data
        return JsonProcessor.processPrintData(template, infoWrapper, copies);
    }


    public static List<ArrayList<String>> getTextPrintData(int copies, float multiple) {
        float offsetX = 0f;
        float offsetY = -2.0f;
        float width = 40f;
        float height = 60f;
        int rotate = 0;
        float marginX = 2.0f;
        float marginY = 2.0f;
        float titleFontSize = 5.6f;
        float titleWidth = width - marginX * 2;
        float titleHeight = 7.4f;
        float sheetHeight = 6.0f;
        float contentWidth = width - marginX * 3;
        float contentHeight = 4.3f;
        float fontSize = 3.2f;

        // Initialize drawing board parameters
        InitDrawingBoardParam initParam = new InitDrawingBoardParam(width, height, rotate, "ZT001.ttf", 0, 0);

        // Create text element list
        List<Element> elements = new ArrayList<>();

        // Create and configure each text element
        // Title element: Quality Certificate
        TextElement.TextJson titleJson = new TextElement.TextJson(marginX + offsetX, marginY * 2 + offsetY, titleHeight, titleWidth, "Quality Certificate", "SimSun", 0, titleFontSize, 1, 1, 0.0f, 1.0f, 6, new boolean[]{true, false, false, false});
        TextElement titleElement = new TextElement("text", titleJson);
        elements.add(titleElement);

        // Product name element
        TextElement.TextJson nameJson = new TextElement.TextJson(marginX * 1.5f + offsetX, marginY * 6.5f + offsetY, contentHeight, contentWidth, "Product: Dress", "SimSun", 0, fontSize, 0, 1, 0.0f, 1.0f, 6, new boolean[]{false, false, false, false});
        TextElement nameElement = new TextElement("text", nameJson);
        elements.add(nameElement);

        // Product number element
        TextElement.TextJson productNoJson = new TextElement.TextJson(marginX * 1.5f + offsetX, marginY * 6.5f + sheetHeight + offsetY, contentHeight, contentWidth, "Product No: L0565", "SimSun", 0, fontSize, 0, 1, 0.0f, 1.0f, 6, new boolean[]{false, false, false, false});
        TextElement codeElement = new TextElement("text", productNoJson);
        elements.add(codeElement);

        // Size element
        TextElement.TextJson sizeJson = new TextElement.TextJson(marginX * 1.5f + offsetX, marginY * 6.5f + sheetHeight * 2 + offsetY, contentHeight, contentWidth, "Size: S (155)", "SimSun", 0, fontSize, 0, 1, 0.0f, 1.0f, 6, new boolean[]{false, false, false, false});
        TextElement sizeElement = new TextElement("text", sizeJson);
        elements.add(sizeElement);

        // Color element
        TextElement.TextJson colorJson = new TextElement.TextJson(marginX * 1.5f + offsetX, marginY * 6.5f + sheetHeight * 3 + offsetY, contentHeight, contentWidth, "Color: Blue", "SimSun", 0, fontSize, 0, 1, 0.0f, 1.0f, 6, new boolean[]{false, false, false, false});
        TextElement colorElement = new TextElement("text", colorJson);
        elements.add(colorElement);

        // Fabric element
        TextElement.TextJson fabricJson = new TextElement.TextJson(marginX * 1.5f + offsetX, marginY * 6.5f + sheetHeight * 4 + offsetY, contentHeight, contentWidth, "Fabric: 100% Polyester", "SimSun", 0, fontSize, 0, 1, 0.0f, 1.0f, 6, new boolean[]{false, false, false, false});
        TextElement materialElement = new TextElement("text", fabricJson);
        elements.add(materialElement);

        // Price element
        TextElement.TextJson priceJson = new TextElement.TextJson(marginX * 1.5f + offsetX, marginY * 6.5f + sheetHeight * 5 + offsetY, titleHeight * 2, contentWidth, "National Price\n¥188", "SimSun", 0, titleFontSize, 1, 1, 0.0f, 1.0f, 6, new boolean[]{false, false, false, false});
        TextElement priceElement = new TextElement("text", priceJson);
        elements.add(priceElement);

        // Create print template object
        PrintTemplate template = new PrintTemplate(initParam, elements);
        
        // Create print info object
        PrinterImageProcessingInfo info = JsonProcessor.createPrinterImageProcessingInfo(rotate, copies, width, height, multiple);
        PrinterImageProcessingInfoWrapper infoWrapper = new PrinterImageProcessingInfoWrapper(info);
        
        // Use optimized method to process print data
        return JsonProcessor.processPrintData(template, infoWrapper, copies);
    }








}
