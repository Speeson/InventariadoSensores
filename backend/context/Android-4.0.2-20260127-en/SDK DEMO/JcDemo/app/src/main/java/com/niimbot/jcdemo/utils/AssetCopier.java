package com.niimbot.jcdemo.utils;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AssetCopier {
    /**
     * Copy assets to internal storage
     * 
     * @param context Android context
     * @param assetName Asset file name
     * @param internalDirectoryName Internal storage directory name
     */
    public static void copyAssetsToInternalStorage(Context context, String assetName, String internalDirectoryName) {
        File internalDir = new File(context.getFilesDir(), internalDirectoryName);
        File outputFile = new File(internalDir, assetName);

        // Create internal storage directory if it doesn't exist
        if (!internalDir.exists()) {
            internalDir.mkdir();
        }

        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            // Open file in assets folder
            inputStream = context.getAssets().open(assetName);

            // Create output stream to the specified file
            outputStream = new FileOutputStream(outputFile);

            // Read data from input stream and write to output stream
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }

            // Flush output stream
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close streams
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
