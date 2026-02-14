package com.niimbot.jcdemo.print.data;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.niimbot.jcdemo.print.data.json.AssetJsonReader;
import com.niimbot.jcdemo.print.data.json.JsonProcessor;
import com.niimbot.jcdemo.print.model.info.PrinterImageProcessingInfo;
import com.niimbot.jcdemo.print.model.info.PrinterImageProcessingInfoWrapper;
import com.niimbot.jcdemo.print.model.template.PrintTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于JSON数据创建打印数据的类
 * 通过读取assets目录下的JSON文件来创建打印数据
 */
public class JsonPrintData {
    private static final String TAG = "JsonPrintData";

    /**
     * 获取打印数据
     * @param context Android上下文
     * @param type 打印类型
     * @param copies 打印份数
     * @param multiple 倍率，200dpi打印机为8.0 300dpi打印机为11.81
     * @return 打印数据列表，格式与PrintData.getPrintData()相同
     */
    public static List<ArrayList<String>> getPrintData(Context context, String type,int copies,  float multiple) {
        String jsonFileName;
        switch (type) {
            case "text" -> jsonFileName = "printData/textPrintData.json";
            case "image" -> jsonFileName = "printData/imagePrintData.json";
            case "qrcode" -> jsonFileName = "printData/qrCodeData.json";
            case "qrcodeWithLogo" -> jsonFileName = "printData/qrCodeWithLogoData.json";
            case "barcode" -> jsonFileName = "printData/barCodeData.json";
            case "line" -> jsonFileName = "printData/linePrintData.json";
            case "graph" -> jsonFileName = "printData/graphPrintData.json";
            case "batch" -> jsonFileName = "printData/batchPrintData.json";
            default -> jsonFileName = "";
        }


        return getPrintDataFromJson(context, jsonFileName, copies, multiple);
    }

    /**
     * 从JSON文件获取打印数据
     *
     * @param context      Android上下文
     * @param jsonFileName assets目录下的JSON文件名（不含路径）
     * @return 打印数据列表，格式与PrintData.getPrintData()相同
     */
    public static List<ArrayList<String>> getPrintDataFromJson(Context context, String jsonFileName, int copies, float multiple) {
        try {
            // 从assets读取JSON文件
            String jsonContent = AssetJsonReader.readJsonFromAssets(context, jsonFileName,
                    error -> Log.e(TAG, "读取JSON文件失败: " + jsonFileName + ", 错误: " + error));

            if (jsonContent == null || jsonContent.isEmpty()) {
                Log.e(TAG, "JSON文件内容为空: " + jsonFileName);
                return null;
            }

            // 解析JSON数据
            return parseJsonToPrintData(jsonContent, copies, multiple);

        } catch (Exception e) {
            Log.e(TAG, "从JSON文件获取打印数据失败: " + jsonFileName, e);
            return null;
        }
    }


    /**
     * 解析JSON内容为打印数据
     *
     * @param jsonContent JSON内容
     * @return 打印数据列表
     */
    private static List<ArrayList<String>> parseJsonToPrintData(String jsonContent, int copies, float multiple) {
        try {
            // 使用JsonProcessor中配置了ElementAdapter的Gson实例
            List<PrintTemplate> templates = null;

            // 尝试解析为PrintTemplate数组
            try {
                // 先尝试解析为PrintTemplate数组
                PrintTemplate[] templateArray = JsonProcessor.GSON_INSTANCE.fromJson(jsonContent, PrintTemplate[].class);
                if (templateArray != null && templateArray.length > 0) {
                    templates = new ArrayList<>();
                    Collections.addAll(templates, templateArray);
                }
            } catch (Exception e) {
                // 如果解析数组失败，尝试解析为单个PrintTemplate对象
                try {
                    PrintTemplate template = JsonProcessor.GSON_INSTANCE.fromJson(jsonContent, PrintTemplate.class);
                    if (template != null) {
                        templates = new ArrayList<>();
                        templates.add(template);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "无法解析JSON为PrintTemplate对象或数组", ex);
                    return null;
                }
            }

            if (templates == null || templates.isEmpty()) {
                Log.e(TAG, "JSON中没有有效的模板数据");
                return null;
            }

            // 使用JsonProcessor处理模板对象
            List<String> processedJsonData = JsonProcessor.processTemplateList(templates);
            int templateLength = templates.size();
            List<PrinterImageProcessingInfoWrapper> infoWrappers = new ArrayList<>();

            for (int i = 0; i < templateLength; i++) {
                infoWrappers.add(createPrinterImageProcessingInfoWrapper(templates.get(i), copies, multiple));
            }
            // 创建打印信息数据列表
            List<String> processedInfoData = JsonProcessor.processInfoWrapperList(infoWrappers);


            // 组装最终数据结构
            List<ArrayList<String>> printData = new ArrayList<>();
            printData.add(new ArrayList<>(processedJsonData));
            printData.add(new ArrayList<>(processedInfoData));

            return printData;

        } catch (JsonSyntaxException e) {
            Log.e(TAG, "JSON语法错误", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "解析JSON内容为打印数据失败", e);
            return null;
        }
    }

    //基于打印数据的PrintTemplate创建PrinterImageProcessingInfoWrapper
    public static PrinterImageProcessingInfoWrapper createPrinterImageProcessingInfoWrapper(PrintTemplate printTemplate, int copies, float multiple) {
        PrinterImageProcessingInfo info = JsonProcessor.createPrinterImageProcessingInfo(printTemplate.getInitDrawingBoardParam().getRotate(), copies, printTemplate.getInitDrawingBoardParam().getWidth(), printTemplate.getInitDrawingBoardParam().getHeight(), multiple);
        return new PrinterImageProcessingInfoWrapper(info);
    }


}