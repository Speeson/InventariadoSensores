package com.niimbot.jcdemo.print.data.json;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Asset folder JSON data reader
 * Used to read JSON file content from assets directory
 */
public class AssetJsonReader {
    private static final String TAG = "AssetJsonReader";
    
    /**
     * Read JSON file content from assets directory
     * 
     * @param context Android context
     * @param fileName JSON file name (without path)
     * @return JSON file content string, returns null if read fails
     */
    public static String readJsonFromAssets(Context context, String fileName) {
        if (context == null) {
            Log.e(TAG, "Context is null");
            return null;
        }
        
        if (fileName == null || fileName.trim().isEmpty()) {
            Log.e(TAG, "File name is null or empty");
            return null;
        }
        
        StringBuilder stringBuilder = new StringBuilder();
        
        try (InputStream inputStream = context.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            
            Log.d(TAG, "Successfully read JSON file: " + fileName);
            return stringBuilder.toString();
            
        } catch (IOException e) {
            Log.e(TAG, "Error reading JSON file from assets: " + fileName, e);
            return null;
        }
    }
    
    /**
     * Read JSON file content from assets directory with error callback
     * 
     * @param context Android context
     * @param fileName JSON file name (without path)
     * @param errorCallback Error callback interface
     * @return JSON file content string, returns null if read fails
     */
    public static String readJsonFromAssets(Context context, String fileName, JsonReadErrorCallback errorCallback) {
        String result = readJsonFromAssets(context, fileName);
        
        if (result == null && errorCallback != null) {
            errorCallback.onError("Failed to read JSON file: " + fileName);
        }
        
        return result;
    }
    
    /**
     * Check if specified file exists in assets directory
     * 
     * @param context Android context
     * @param fileName File name
     * @return Returns true if file exists, otherwise false
     */
    public static boolean existsInAssets(Context context, String fileName) {
        if (context == null || fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        try {
            InputStream inputStream = context.getAssets().open(fileName);
            inputStream.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * JSON read error callback interface
     */
    public interface JsonReadErrorCallback {
        /**
         * Called when an error occurs while reading JSON file
         * 
         * @param errorMessage Error message
         */
        void onError(String errorMessage);
    }
}