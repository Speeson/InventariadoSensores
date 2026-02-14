package com.niimbot.jcdemo.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Toast;


import com.gengcon.www.jcprintersdk.callback.PrintCallback;
import com.niimbot.jcdemo.app.MyApplication;
import com.niimbot.jcdemo.bean.Dish;
import com.niimbot.jcdemo.databinding.ActivityMainBinding;
import com.niimbot.jcdemo.print.data.JsonPrintData;
import com.niimbot.jcdemo.utils.AssetCopier;
import com.niimbot.jcdemo.utils.ImgUtil;
import com.niimbot.jcdemo.print.data.PrintData;
import com.niimbot.jcdemo.print.core.PrintUtil;
import com.permissionx.guolindev.PermissionX;


import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Main page
 *
 * @author zhangbin
 * 2022.03.17
 */
public class MainActivity extends AppCompatActivity {
    private static final SparseArray<String> ERROR_MESSAGES = new SparseArray<>();

    static {
        ERROR_MESSAGES.put(-1, "Printer not connected");
        ERROR_MESSAGES.put(1, "Cover open");
        ERROR_MESSAGES.put(2, "Out of paper");
        ERROR_MESSAGES.put(3, "Low battery");
        ERROR_MESSAGES.put(4, "Battery abnormal");
        ERROR_MESSAGES.put(5, "Manually stopped");
        ERROR_MESSAGES.put(6, "Data error");
        ERROR_MESSAGES.put(7, "Temperature too high");
        ERROR_MESSAGES.put(8, "Paper feeding abnormal");
        ERROR_MESSAGES.put(9, "Printing");
        ERROR_MESSAGES.put(10, "Print head not detected");
        ERROR_MESSAGES.put(11, "Ambient temperature too low");
        ERROR_MESSAGES.put(12, "Print head not locked");
        ERROR_MESSAGES.put(13, "Ribbon not detected");
        ERROR_MESSAGES.put(14, "Mismatched ribbon");
        ERROR_MESSAGES.put(15, "Ribbon used up");
        ERROR_MESSAGES.put(16, "Unsupported paper type");
        ERROR_MESSAGES.put(17, "Paper type setting failed");
        ERROR_MESSAGES.put(18, "Print mode setting failed");
        ERROR_MESSAGES.put(19, "Density setting failed");
        ERROR_MESSAGES.put(20, "Failed to write RFID");
        ERROR_MESSAGES.put(21, "Margin setting failed");
        ERROR_MESSAGES.put(22, "Communication abnormal");
        ERROR_MESSAGES.put(23, "Printer connection lost");
        ERROR_MESSAGES.put(24, "Drawing board parameter error");
        ERROR_MESSAGES.put(25, "Rotation angle error");
        ERROR_MESSAGES.put(26, "JSON parameter error");
        ERROR_MESSAGES.put(27, "Paper feeding abnormal (B3S)");
        ERROR_MESSAGES.put(28, "Check paper type");
        ERROR_MESSAGES.put(29, "RFID tag not written");
        ERROR_MESSAGES.put(30, "Density setting not supported");
        ERROR_MESSAGES.put(31, "Print mode not supported");
        ERROR_MESSAGES.put(34, "RFID writing not supported");
        ERROR_MESSAGES.put(50, "Invalid label");
        ERROR_MESSAGES.put(51, "Invalid ribbon and label");
        ERROR_MESSAGES.put(52, "Firmware data reception timeout");
        ERROR_MESSAGES.put(53, "Non-dedicated ribbon (current model uses mismatched ribbon)");
        ERROR_MESSAGES.put(58, "Non-genuine consumables (neither label nor ribbon recognized) (for some new models)");
        ERROR_MESSAGES.put(59, "Non-genuine ribbon (ribbon not recognized) (for some new models)");
        ERROR_MESSAGES.put(60, "Consumables over limit (both label and ribbon exceed usage limit) (for some new models)");
        ERROR_MESSAGES.put(61, "Ribbon over limit (ribbon exceeds usage limit) (for some new models)");
        ERROR_MESSAGES.put(62, "Non-genuine label (label not recognized) (for some new models)");
        ERROR_MESSAGES.put(63, "Label over limit (label exceeds usage limit) (for some new models)");
    }


    private static final String TAG = "MainActivity";
    private static final String RB_THERMAL = "Thermal";
    private ActivityMainBinding bind;
    private Context context;

    /**
     * Image data
     */
    private ArrayList<String> jsonList;
    /**
     * Image processing data
     */
    private ArrayList<String> infoList;

    /**
     * Print mode
     */
    private int printMode;

    /**
     * Print density
     */
    private int printDensity = 3;

    /**
     * Print multiple (resolution)
     */
    private Float printMultiple = 8.0f;
    /**
     * Whether there is a print error
     */
    private boolean isError;
    /**
     * Whether printing is canceled
     */
    private boolean isCancel;

    /**
     * Total page count
     */
    private int pageCount;

    /**
     * Number of copies per page
     */
    private int quantity;

    /**
     * Print progress loading
     */
    private MyDialogLoadingFragment fragment;
    private ExecutorService executorService;

    Handler handler = new Handler(Looper.getMainLooper());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bind = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(bind.getRoot());
        init();
    }

    private void init() {
        context = getApplicationContext();
        // Set custom font path name
        String customFontDirectory = "custom_font";
        // Copy font files to internal storage
        AssetCopier.copyAssetsToInternalStorage(context, "ZT008.ttf", customFontDirectory);
        permissionRequest();
        // Register thread pool
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("print_pool_%d");
            return thread;
        };

        executorService = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(1024), threadFactory, new ThreadPoolExecutor.AbortPolicy());

        initPrintData();
        initEvent();
    }


    private void initPrintData() {
        jsonList = new ArrayList<>();
        infoList = new ArrayList<>();
    }


    private void initEvent() {
        bind.btnConnect.setOnClickListener(v -> {
            Intent intent = new Intent(context, ConnectActivity.class);
            startActivity(intent);
        });

        // Common print logic binding
        setupPrintButton(bind.btnTextPrint, "text", printMultiple, 1);
        setupPrintButton(bind.btnBarcodePrint, "barcode", printMultiple, 1);
        setupPrintButton(bind.btnQrcodePrint, "qrcode", printMultiple, 1);
        setupPrintButton(bind.btnQrcodeWithLogoPrint, "qrcodeWithLogo", printMultiple, 1);
        setupPrintButton(bind.btnLinePrint, "line", printMultiple, 1);
        setupPrintButton(bind.btnGraphPrint, "graph", printMultiple, 1);
        setupPrintButton(bind.btnImagePrint, "image", printMultiple, 1);
        setupPrintButton(bind.btnBatchPrint, "batch", printMultiple, 2);

        bind.btnBitmapPrint.setOnClickListener(v -> {
            printMode = bind.rbThermal.isChecked() ? 1 : 2;
            executorService.submit(this::printBitmap);
        });

        bind.rgPrintMode.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton radioButton = findViewById(checkedId);
            String printModeOption = radioButton.getText().toString();

            SharedPreferences preferences = context.getSharedPreferences("printConfiguration", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            if (RB_THERMAL.equals(printModeOption)) {
                editor.putInt("printMode", 1);
            } else {
                editor.putInt("printMode", 2);
            }

            editor.apply();
        });
    }

    /**
     * Bind common logic for print buttons
     */
    private void setupPrintButton(Button button, String printType, float multiple, int copies) {
        button.setOnClickListener(v -> {
            printMode = bind.rbThermal.isChecked() ? 1 : 2;
            executorService.submit(() -> {
                initPrintData();
                // Choose one of the following two methods to create print data
                // Method 1: Get print data read from JSON
                List<ArrayList<String>> printData = JsonPrintData.getPrintData(this, printType, copies, multiple);
                // Method 2: Get printer data created through objects
//                List<ArrayList<String>> printData = PrintData.getPrintData(printType, copies, multiple);
                assert printData != null;
                int length = printData.get(0).size();
                Log.d(TAG, "printLabel: " + printData);
                for (int i = 0; i < length; i++) {
                    jsonList.add(printData.get(0).get(i));
                    infoList.add(printData.get(1).get(i));
                }
                printLabel(copies, jsonList, infoList);
            });


        });
    }


    /**
     * Print label
     *
     * @param copies Number of copies
     */
    private void printLabel(int copies, List<String> jsonList, List<String> infoList) {
        // Show loading dialog
        fragment = new MyDialogLoadingFragment("Printing");
        fragment.show(getSupportFragmentManager(), "PRINT");
        PrintUtil.startLabelPrintJob(copies, printDensity, 1, printMode, handler, jsonList, infoList, new PrintUtil.PrintStatusCallback() {
            @Override
            public void onProgress(int pageIndex, int quantityIndex) {
                String progress = "Print progress: Printed to page " + pageIndex + ", copy " + quantityIndex;
                fragment.setStateStr(progress);
            }

            @Override
            public void onError(int errorCode, int printState) {
                String errorMessage = ERROR_MESSAGES.get(errorCode, "Unknown error");
                handlePrintResult(fragment, errorMessage);
            }

            @Override
            public void onCancelJob(boolean isSuccess) {
                handlePrintResult(fragment, isSuccess ? "Print cancelled" : "Cancel failed");

            }

            @Override
            public void onPrintComplete() {
                handlePrintResult(fragment, "Print successful");

            }
        });

    }

    private void printBitmap() {
        if (PrintUtil.isConnection() != 0) {
            handler.post(() -> Toast.makeText(MyApplication.getInstance(), "Printer not connected", Toast.LENGTH_SHORT).show());
            return;
        }

        fragment = new MyDialogLoadingFragment("Printing");
        fragment.show(getSupportFragmentManager(), "PRINT");


        // Reset error status variable
        isError = false;
        // Reset cancel print status variable
        isCancel = false;

        int orientation = 0;
        pageCount = 2;
        
        
        AtomicInteger generatedPrintDataPageCount = new AtomicInteger(0);
        int totalQuantity = pageCount;
        /*
         * This method is used to set the total number of copies to print. Represents the sum of the number of copies of all pages.
         * For example, if you have 3 pages to print, the first page prints 3 copies, the second page prints 2 copies, and the third page prints 5 copies, then the total number of copies should be 10 (3+2+5)
         */
        PrintUtil.getInstance().setTotalPrintQuantity(totalQuantity);
        /*
         * Parameter 1: Print density, Parameter 2: Paper type, Parameter 3: Print mode
         * Print density B50/B50W/T6/T7/T8 recommends setting 6 or 8, Z401/B32 recommends setting 8, B3S/B21/B203/B1 recommends setting 3
         */
        PrintUtil.getInstance().startPrintJob(printDensity, 1, printMode, new PrintCallback() {
            @Override
            public void onProgress(int pageIndex, int quantityIndex, HashMap<String, Object> hashMap) {
                // pageIndex is the print page progress, quantityIndex is the print copy progress, such as page 2, copy 3
                handler.post(() -> fragment.setStateStr("Print progress: Printed to page " + pageIndex + ", copy " + quantityIndex));
                Log.d(TAG, "Test: Print progress: Printed to page: " + pageIndex);
                // Print progress callback
                if (pageIndex == pageCount) {
                    Log.d(TAG, "Test:onProgress: End printing");
                    // endJob is deprecated, use endPrintJob with clearer method meaning
                    if (PrintUtil.getInstance().endPrintJob()) {
                        Log.d(TAG, "End print successful");
                    } else {
                        Log.d(TAG, "End print failed");
                    }

                    handlePrintResult(fragment, "Print successful");
                }
            }
            
            @Override
            public void onError(int i) {

            }

            @Override
            public void onError(int errorCode, int printState) {
                Log.d(TAG, "Test:onError");
                isError = true;
                String errorMsg = ERROR_MESSAGES.get(errorCode);
                handlePrintResult(fragment, errorMsg);
            }

            @Override
            public void onCancelJob(boolean isSuccess) {
                // Cancel print success callback
                isCancel = true;
            }

            @Override
            public void onBufferFree(int pageIndex, int bufferSize) {
                /*
                 * 1. If printing is not ended and SDK cache becomes free, this interface is automatically called back. This callback will be reported multiple times until printing ends.
                 * 2. During printing, if errors occur, printing is cancelled, or pageIndex exceeds the total number of pages, it returns. (This control code must be retained, otherwise it will cause printing failure)
                 */
                if (notRequireSubmitData(pageIndex)) return;

                if (generatedPrintDataPageCount.get() < pageCount) {
                    // Generate receipt for printing
                    ArrayList<Dish> dishList = new ArrayList<>();
                    dishList.add(new Dish("Stir-fried Pork with Chili" + pageIndex, "Medium spicy", 29.9, 1));
                    dishList.add(new Dish("Beef Brisket with Potatoes" + pageIndex, "Medium spicy", 49.9, 1));

                    Bitmap bitmap = ImgUtil.Companion.generatePosReceiptImage(dishList);
                    int bitmapWidth = bitmap.getWidth();
                    int bitmapHeight = bitmap.getHeight();
                    PrintUtil.getInstance().commitImageData(orientation, bitmap, (int) (bitmapWidth / printMultiple), (int) (bitmapHeight / printMultiple), 1, 0, 0, 0, 0, "");
                    generatedPrintDataPageCount.incrementAndGet();

                    // Read image from Assets directory to print label
//                    Bitmap bitmap;
//                    AssetManager assetManager = context.getAssets();
//                    try {
//                        InputStream inputStream = assetManager.open("Niimbot Print Test.png");
//                        bitmap = BitmapFactory.decodeStream(inputStream);
//                        inputStream.close();
//                        int bitmapWidth = bitmap.getWidth();
//                        int bitmapHeight = bitmap.getHeight();
//                        PrintUtil.getInstance().commitImageData(orientation, bitmap, (int) (bitmapWidth / printMultiple), (int) (bitmapHeight / printMultiple), 1, 0, 0, 0, 0, "");
//                        // Update generated image page count
//                        generatedPrintDataPageCount.incrementAndGet();
//                    } catch (IOException e) {
//                        Log.e(TAG, "Failed to read Assets image: " + e.getMessage());
//                    }

                }
            }
        });


    }

    private boolean notRequireSubmitData(int pageIndex) {
        return isError || isCancel || pageIndex > pageCount;
    }

    // Keep UI handling method
    private void handlePrintResult(MyDialogLoadingFragment fragment, String message) {
        handler.post(() -> {
            if (fragment != null) fragment.dismiss();
            Toast.makeText(MyApplication.getInstance(), message, Toast.LENGTH_SHORT).show();
        });
    }

    private void permissionRequest() {
        // Select different permission arrays based on Android version
        String[] permissions = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ? 
            new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT} : 
            new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};

        // Use PermissionX to request permissions and set callback handling functions
        PermissionX.init(MainActivity.this).permissions(permissions).request(this::handlePermissionResult);
    }

    private void handlePermissionResult(boolean allGranted, List<String> grantedList, List<String> deniedList) {
        if (allGranted) {
            handleAllPermissionsGranted();
        } else {
            handler.post(() -> showPermissionFailedToast(deniedList));
        }
    }

    /**
     * Handle the case when all permissions have been granted
     */
    private void handleAllPermissionsGranted() {
        if (!isGpsEnabled(context)) {
            handler.post(this::showGpsEnableDialog);
        }
    }

    private void showPermissionFailedToast(List<String> deniedList) {
        Toast.makeText(this, "Permission request failed" + deniedList, Toast.LENGTH_SHORT).show();
    }

    private void showGpsEnableDialog() {
        String message = "Please enable GPS, failure to enable may result in inability to perform Bluetooth search normally";
        int dialogType = 1;
        MyDialogFragment fragment = new MyDialogFragment(message, dialogType);
        fragment.show(getSupportFragmentManager(), "GPS");
    }

    /**
     * Check if GPS is enabled
     *
     * @param context Context object
     * @return Returns true if GPS is enabled, otherwise returns false
     */
    public boolean isGpsEnabled(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences preferences = context.getSharedPreferences("printConfiguration", Context.MODE_PRIVATE);
        printMode = preferences.getInt("printMode", 1);
        printDensity = preferences.getInt("printDensity", 3);
        // Except for B32/Z401/T8/M2/M3/B21_Pro where printMultiple is 11.81, others are 8
        printMultiple = preferences.getFloat("printMultiple", 8.0F);
        if (printMode == 1) {
            bind.rbThermal.setChecked(true);
        } else {
            bind.rbThermalTransfer.setChecked(true);
        }
    }
}