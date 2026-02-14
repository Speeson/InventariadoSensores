package com.niimbot.jcdemo.print.data.json;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.niimbot.jcdemo.print.core.PrintUtil;
import com.niimbot.jcdemo.print.data.adapter.ElementAdapter;

import com.niimbot.jcdemo.print.model.element.BarCodeElement;
import com.niimbot.jcdemo.print.model.element.Element;
import com.niimbot.jcdemo.print.model.element.GraphElement;
import com.niimbot.jcdemo.print.model.element.ImageElement;
import com.niimbot.jcdemo.print.model.element.LineElement;
import com.niimbot.jcdemo.print.model.element.QrCodeElement;
import com.niimbot.jcdemo.print.model.element.QrCodeWithLogoElement;
import com.niimbot.jcdemo.print.model.element.TextElement;
import com.niimbot.jcdemo.print.model.info.PrinterImageProcessingInfo;
import com.niimbot.jcdemo.print.model.info.PrinterImageProcessingInfoWrapper;
import com.niimbot.jcdemo.print.model.template.PrintTemplate;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JsonProcessor {
    private static final String TAG = "JsonSerializer";

    /**
     * Create a unified Gson instance to avoid repeated creation and circular dependency issues
     */
    public static final Gson GSON_INSTANCE = createGsonInstance();

    /**
     * Create a configured Gson instance
     *
     * @return Configured Gson instance
     */
    private static Gson createGsonInstance() {
        // Use lazy initialization to avoid circular dependencies
        return new GsonBuilder()
                .registerTypeAdapter(Element.class, new ElementAdapter(null))
                .registerTypeAdapterFactory(new TypeAdapterFactory() {
                    @Override
                    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                        if (type.getRawType().equals(Element.class)) {
                            // Use the current Gson instance to avoid circular dependencies
                            return (TypeAdapter<T>) new ElementAdapter(gson);
                        }
                        return null;
                    }
                })
                .create();
    }
    
    /**
     * Safely serialize object to JSON string
     * @param object Object to serialize
     * @return JSON string, returns null if serialization fails
     */
    public static String serializeToJson(Object object) {
        if (object == null) {
            Log.w(TAG, "Attempted to serialize null object");
            return null;
        }
        
        try {
            return GSON_INSTANCE.toJson(object);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "JSON syntax error during serialization: ", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during serialization: ", e);
            return null;
        }
    }
    

    
    /**
     * Safely serialize PrinterImageProcessingInfoWrapper object to JSON string
     * @param infoWrapper PrinterImageProcessingInfoWrapper object to serialize
     * @return JSON string, returns null if serialization fails
     */
    public static String serializePrinterInfo(PrinterImageProcessingInfoWrapper infoWrapper) {
        return serializeToJson(infoWrapper);
    }
    
    /**
     * Process print data, including serialization and formatting
     * @param template Print template object
     * @param info Print info object
     * @param dataType Data type 1-single JSON data, 2-JSON data array
     * @return List containing print data, returns empty list if processing fails
     */
    public static List<ArrayList<String>> processPrintData(PrintTemplate template, PrinterImageProcessingInfoWrapper info, int dataType) {
        List<ArrayList<String>> printData = new ArrayList<>();
        ArrayList<String> printJsonData = new ArrayList<>();
        ArrayList<String> printInfoData = new ArrayList<>();


        Log.d(TAG, "processPrintData: "+serializeToJson( template));
        // Process template object directly, avoiding unnecessary serialization/deserialization
        List<String> processedTemplateData = processTemplateObject(template, dataType);
        if (processedTemplateData.isEmpty()) {
            Log.e(TAG, "Failed to process template data");
            return Collections.emptyList();
        }
        printJsonData.addAll(processedTemplateData);
        
        // Serialize info object
        String infoJson = serializePrinterInfo(info);
        if (infoJson == null) {
            Log.e(TAG, "Failed to serialize printer info");
            return Collections.emptyList();
        }
        
        printInfoData.add(infoJson);
        
        // Assemble final data
        printData.add(printJsonData);
        printData.add(printInfoData);
        
        return printData;
    }
    
    /**
     * Directly process template object, avoiding serialization/deserialization overhead
     * @param template Print template object
     * @param dataType Data type 1-single data, 2-multiple data
     * @return Processed print data list
     */
    private static List<String> processTemplateObject(PrintTemplate template, int dataType) {
        List<String> printDataList = new ArrayList<>();
        
        try {
            if (dataType == 1) {
                // Single data, process directly
                printDataList.add(processPrintTemplate(template));
            } else {
                // Multiple data, copy template as needed
                // Note: This assumes dataType=2 requires processing multiple identical templates
                // If actual requirements differ, this logic can be adjusted
                printDataList.add(processPrintTemplate(template));
                // If multiple different templates are needed, use processTemplateList method
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing template object: ", e);
        }
        
        return printDataList;
    }

    /**
     * Create and initialize PrinterImageProcessingInfo object
     *
     * @param rotate   Rotation angle
     * @param copies   Number of copies
     * @param width    Width
     * @param height   Height
     * @param multiple Print multiple
     * @param epc      EPC value
     * @return Initialized PrinterImageProcessingInfo object
     */
    public static PrinterImageProcessingInfo createPrinterImageProcessingInfo(int rotate, int copies, float width, float height, float multiple, String epc) {
        return new PrinterImageProcessingInfo(rotate, new int[]{0, 0, 0, 0}, copies, 0.0f, 0.0f, width, height, String.valueOf(multiple), epc);
    }

    /**
     * Create and initialize PrinterImageProcessingInfo object (using default EPC value)
     *
     * @param rotate   Rotation angle
     * @param copies   Number of copies
     * @param width    Width
     * @param height   Height
     * @param multiple Print multiple
     * @return Initialized PrinterImageProcessingInfo object
     */
    public static PrinterImageProcessingInfo createPrinterImageProcessingInfo(int rotate, int copies, float width, float height, float multiple) {
        return createPrinterImageProcessingInfo(rotate, copies, width, height, multiple, "");
    }
    
    /**
     * Process PrinterImageProcessingInfoWrapper list, avoiding serialization/deserialization operations
     * 
     * @param infoWrappers List of PrinterImageProcessingInfoWrapper objects
     * @return Processed data list
     */
    public static List<String> processInfoWrapperList(List<PrinterImageProcessingInfoWrapper> infoWrappers) {
        List<String> result = new ArrayList<>();
        
        if (infoWrappers == null || infoWrappers.isEmpty()) {
            Log.w(TAG, "Info wrapper list is null or empty");
            return result;
        }
        
        for (PrinterImageProcessingInfoWrapper infoWrapper : infoWrappers) {
            try {
                // Process object directly, without serialization/deserialization
                String processedData = serializePrinterInfo(infoWrapper);
                if (processedData != null) {
                    result.add(processedData);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing info wrapper: " + e.getMessage(), e);
            }
        }
        
        return result;
    }
    
    /**
     * Process template list for batch printing
     * @param templates Template list
     * @return Processed print data list
     */
    public static List<String> processTemplateList(List<PrintTemplate> templates) {
        List<String> printDataList = new ArrayList<>();
        
        if (templates == null || templates.isEmpty()) {
            Log.w(TAG, "Template list is null or empty");
            return printDataList;
        }
        Log.d(TAG, "processPrintData: "+serializeToJson( templates));
        try {
            for (PrintTemplate template : templates) {
                printDataList.add(processPrintTemplate(template));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing template list: ", e);
        }
        
        return printDataList;
    }
    



    private static String processPrintTemplate(PrintTemplate template) {
        List<String> fonts = new ArrayList<>();
        PrintUtil.getInstance().drawEmptyLabel(template.getInitDrawingBoardParam().getWidth(), template.getInitDrawingBoardParam().getHeight(), template.getInitDrawingBoardParam().getRotate(), fonts);

        List<Element> elements = template.getElements();
        for (Element element : elements) {
            if (element instanceof TextElement textElement) {
                TextElement.TextJson json = textElement.getJson();
                Log.d(TAG, "getJsonPrintData-getTextAlignHorizontal:" + json.getTextAlignHorizontal());
                PrintUtil.getInstance().drawLabelText(json.getX(), json.getY(), json.getWidth(), json.getHeight(), json.getValue(), json.getFontFamily(), json.getFontSize(), json.getRotate(), json.getTextAlignHorizontal(), json.getTextAlignVertical(), json.getLineMode(), json.getLetterSpacing(), json.getLineSpacing(), json.getFontStyle());

            } else if (element instanceof BarCodeElement barCodeElement) {
                BarCodeElement.BarCodeJson json = barCodeElement.getJson();
                PrintUtil.getInstance().drawLabelBarCode(json.getX(), json.getY(), json.getWidth(), json.getHeight(), json.getCodeType(), json.getValue(), json.getFontSize(), json.getRotate(), json.getTextHeight(), json.getTextPosition());

            } else if (element instanceof LineElement lineElement) {
                LineElement.LineJson json = lineElement.getJson();
                Log.d(TAG, "processPrintTemplate: "+json.getRotate());
                PrintUtil.getInstance().drawLabelLine(json.getX(), json.getY(), json.getWidth(), json.getHeight(),json.getRotate(), json.getLineType(),  json.getDashwidth());

            } else if (element instanceof GraphElement graphElement) {
                GraphElement.GraphJson json = graphElement.getJson();
                PrintUtil.getInstance().drawLabelGraph(json.getX(), json.getY(), json.getWidth(), json.getHeight(), json.getGraphType(), json.getRotate(), json.getCornerRadius(), json.getLineWidth(), json.getLineType(), json.getDashWidth());

            } else if (element instanceof QrCodeElement qrCodeElement) {
                QrCodeElement.QrCodeJson json = qrCodeElement.getJson();
                PrintUtil.getInstance().drawLabelQrCode(json.getX(), json.getY(), json.getWidth(), json.getHeight(), json.getValue(), json.getCodeType(), json.getRotate());

            } else if (element instanceof QrCodeWithLogoElement qrCodeWithLogoElement) {
                QrCodeWithLogoElement.QrCodeWithLogoJson json = qrCodeWithLogoElement.getJson();
                PrintUtil.getInstance().drawLabelQrCode(json.getX(), json.getY(), json.getWidth(), json.getHeight(), json.getValue(), json.getCodeType(), json.getRotate(), json.getCorrectLevel(), json.getLogoImageData(), json.getAnchor(), json.getScale());

            } else if (element instanceof ImageElement imageElement) {
                ImageElement.ImageJson json = imageElement.getJson();
                PrintUtil.getInstance().drawLabelImage(json.getImageData(), json.getX(), json.getY(), json.getWidth(), json.getHeight(), json.getRotate(), json.getImageProcessingType(), json.getImageProcessingValue());
            }
        }

        byte[] printData = PrintUtil.getInstance().generateLabelJson();
        Log.d(TAG, "processPrintTemplate1: "+new String(printData));
        Log.d(TAG, "processPrintTemplate2: "+new String(printData, StandardCharsets.UTF_8));
        return new String(printData, StandardCharsets.UTF_8);
    }
}
