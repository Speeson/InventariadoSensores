package com.niimbot.jcdemo.print.core;


import android.os.Handler;
import android.util.Log;

import com.gengcon.www.jcprintersdk.JCPrintApi;
import com.gengcon.www.jcprintersdk.callback.Callback;
import com.gengcon.www.jcprintersdk.callback.PrintCallback;
import com.niimbot.jcdemo.app.MyApplication;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Print utility class
 *
 * @author zhangbin
 */
public class PrintUtil {
    private static final String TAG = "PrintUtil";
    private static int mConnectedType = -1;
    /**
     * Singleton instance, using volatile to ensure multi-thread visibility and ordering
     */
    private static volatile JCPrintApi api;

    /**
     * Callback interface for handling printer status change events
     */
    private static final Callback CALLBACK = new Callback() {
        private static final String TAG = "PrintUtil";

        /**
         * Connection success callback
         *
         * @param address Device address, Bluetooth MAC address for Bluetooth, IP address for WiFi
         * @param type   Connection type, 0 for Bluetooth connection, 1 for WiFi connection
         */
        @Override
        public void onConnectSuccess(String address, int type) {
            mConnectedType = type;
        }

        /**
         * Disconnection callback
         * This method is called when the device is disconnected.
         */
        @Override
        public void onDisConnect() {
            mConnectedType = -1;
        }

        /**
         * Battery level change callback
         * This method is called when the device battery level changes.
         *
         * @param powerLevel Battery level, value range is 1 to 4, representing 1 to 4 bars of power, full power is 4 bars
         */
        @Override
        public void onElectricityChange(int powerLevel) {

        }

        /**
         * Monitor cover status change callback
         * This method is called when the cover status changes. Currently this callback only supports H10/D101/D110/D11/B21/B16/B32/Z401/B3S/B203/B1/B18 series printers.
         *
         * @param coverStatus Cover status, 0 means cover is open, 1 means cover is closed
         */
        @Override
        public void onCoverStatus(int coverStatus) {

        }

        /**
         * Monitor paper status change
         * This method is called when the paper status changes. Currently this callback only supports H10/D101/D110/D11/B21/B16/B32/Z401/B203/B1/B18 series printers.
         *
         * @param paperStatus 0 for paper available, 1 for paper out
         */
        @Override
        public void onPaperStatus(int paperStatus) {

        }

        /**
         * Monitor label RFID reading status change
         * This method is called when the label RFID reading status changes.
         *
         * @param rfidReadStatus 0 for failed to read label RFID, 1 for successfully read label RFID. Currently this callback only supports H10/D101/D110/D11/B21/B16/B32/Z401/B203/B1/B18 series printers.
         */
        @Override
        public void onRfidReadStatus(int rfidReadStatus) {

        }


        /**
         * Monitor ribbon RFID reading status change
         * This method is called when the ribbon RFID reading status changes.
         *
         * @param ribbonRfidReadStatus 0 for failed to read ribbon RFID, 1 for successfully read ribbon RFID. Currently this callback only supports B18/B32/Z401/P1/P1S series printers.
         */
        @Override
        public void onRibbonRfidReadStatus(int ribbonRfidReadStatus) {

        }

        /**
         * Monitor ribbon status changes
         * Called when paper status changes
         *
         * @param ribbonStatus 0 for no ribbon, 1 for ribbon. Currently this callback only supports B18/B32/Z401/P1/P1S series printers.
         */
        @Override
        public void onRibbonStatus(int ribbonStatus) {

        }


        /**
         * Firmware error callback, requires upgrade
         * Called when the device is connected successfully but a firmware error occurs, indicating that a firmware upgrade is required.
         */
        @Override
        public void onFirmErrors() {

        }
    };


    /**
     * Get JCPrintApi singleton instance
     *
     * @return JCPrintApi instance
     */
    public static JCPrintApi getInstance() {
        // Double-checked locking to ensure instance is initialized only once
        if (api == null) {
            synchronized (PrintUtil.class) {
                if (api == null) {
                    api = JCPrintApi.getInstance(CALLBACK);
                    //api.init is deprecated, use initSdk instead, method name is more accurate
                    api.initSdk(MyApplication.getInstance());
                    //Get built-in directory path
//                    File directory = MyApplication.getInstance().getFilesDir();
                    //Get custom font path
//                    File customFontDirectory = new File(directory, "custom_font");
//                    api.initDefaultImageLibrarySettings(customFontDirectory.getAbsolutePath(), "");

                }
            }
        }

        return api;

    }


    /**
     * Connect to Bluetooth printer using printer MAC address (synchronous)
     *
     * @param address Printer address
     * @return Success or failure
     */
    public static int connectBluetoothPrinter(String address) {
        // Get singleton instance to ensure thread safety
        JCPrintApi localApi = getInstance();
        Log.d(TAG, "connectBluetoothPrinter: "+address);
        //api.openPrinterByAddress(address), use connectBluetoothPrinter instead, method name is more accurate
        return localApi.connectBluetoothPrinter(address);
    }

    /**
     * Close printer
     */
    public static void close() {
        // Get singleton instance to ensure thread safety
        JCPrintApi localApi = getInstance();
        localApi.close();
    }


    public static int getConnectedType() {
        return mConnectedType;
    }

    public static void setConnectedType(int connectedType) {
        mConnectedType = connectedType;
    }

    /**
     * Check if printer is connected
     *
     * @return Connection status code
     */
    public static int isConnection() {
        // Get singleton instance to ensure thread safety
        JCPrintApi localApi = getInstance();
        return localApi.isConnection();
    }

    /**
     * Print callback interface
     */
    public interface PrintStatusCallback {
        void onProgress(int pageIndex, int quantityIndex);

        void onError(int errorCode, int printState);

        void onCancelJob(boolean isSuccess);

        void onPrintComplete();
    }


    /**
     * Start label print job
     *
     * @param copies Number of copies per page, not total copies
     * @param density Print density
     * @param labelType Label type
     * @param mode Print mode
     * @param handler Handler for processing callbacks
     * @param jsonList List of JSON strings containing print data
     * @param infoList List of strings containing print information
     * @param callback Print status callback interface
     */
    public static void startLabelPrintJob( int copies, int density,int labelType, int mode,
                                          Handler handler,
                                          List<String> jsonList, List<String> infoList ,PrintStatusCallback callback) {
        Log.d(TAG, "startLabelPrintJob: ");
        // Print parameter validation
        if (isConnection() != 0) {
            handler.post(() -> callback.onError(-1, 0));
            return;
        }

        // Initialize print status
        AtomicInteger generatedPages = new AtomicInteger(0);
        AtomicBoolean isError = new AtomicBoolean(false);
        AtomicBoolean isCancel = new AtomicBoolean(false);
        int pages = jsonList.size();
        // Set total print quantity
        getInstance().setTotalPrintQuantity(pages * copies);

        // Start print job
        getInstance().startPrintJob(density, labelType, mode, new PrintCallback() {
            @Override
            public void onProgress(int pageIndex, int quantityIndex, HashMap<String, Object> hashMap) {
                handler.post(() -> callback.onProgress(pageIndex, quantityIndex));
                if (pageIndex == pages && quantityIndex == copies) {
                    getInstance().endPrintJob();
                    handler.post(callback::onPrintComplete);
                }
            }

            @Override
            public void onError(int i) {
                // No need to handle
            }

            @Override
            public void onError(int errorCode, int printState) {
                Log.d(TAG, "onError: " + errorCode + " " + printState);
                isError.set(true);
                handler.post(() -> callback.onError(errorCode, printState));
            }

            @Override
            public void onCancelJob(boolean success) {
                isCancel.set(success);
                handler.post(() -> callback.onCancelJob(success));
            }

            @Override
            public void onBufferFree(int pageIndex, int bufferSize) {
//                Log.d(TAG, "Test:onBufferFree " + pageIndex + " " + bufferSize);
                if (isError.get() || isCancel.get() || pageIndex > pages) return;

                Log.d(TAG, "Test:onBufferFree ");
                // Generate print data
                int commitSize = Math.min(pages - generatedPages.get(), bufferSize);
                Log.d(TAG, "Test:onBufferFree-commitSize: "+commitSize);
                List<String> subJson = jsonList.subList(generatedPages.get(), generatedPages.get() + commitSize);
                List<String> subInfo = infoList.subList(generatedPages.get(), generatedPages.get() + commitSize);
                Log.d(TAG, "Test:onBufferFree-subJson: "+subJson);
                Log.d(TAG, "Test:onBufferFree-subInfo: "+subInfo);
                getInstance().commitData(subJson, subInfo);

                generatedPages.addAndGet(commitSize);
                Log.d(TAG, "Test:onBufferFree-generatedPages: "+generatedPages.get());
            }
        });




}



}
