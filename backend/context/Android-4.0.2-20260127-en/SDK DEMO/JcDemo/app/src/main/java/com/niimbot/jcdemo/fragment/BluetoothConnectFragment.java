package com.niimbot.jcdemo.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.niimbot.jcdemo.Constant;
import com.niimbot.jcdemo.R;
import com.niimbot.jcdemo.adapter.BlueDeviceAdapter;
import com.niimbot.jcdemo.app.MyApplication;
import com.niimbot.jcdemo.bean.BlueDeviceInfo;
import com.niimbot.jcdemo.databinding.FragmentBluetoothConnectBinding;
import com.niimbot.jcdemo.ui.MyDialogFragment;
import com.niimbot.jcdemo.ui.MyDialogLoadingFragment;
import com.niimbot.jcdemo.ui.MyDialogWifiSetFragment;
import com.niimbot.jcdemo.utils.BluetoothUtils;
import com.niimbot.jcdemo.print.core.PrintUtil;
import com.permissionx.guolindev.PermissionX;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class BluetoothConnectFragment extends Fragment {
    private FragmentBluetoothConnectBinding bind;
    private Context context;
    private static final String USER_DEFINED = "Custom";
    private static final String TAG = "BlueConnectFragment";
    private ExecutorService executorService;
    private BluetoothAdapter mBluetoothAdapter;
    private Set<String> deviceList;
    private BlueDeviceAdapter blueDeviceAdapter;
    private List<BlueDeviceInfo> blueDeviceList;

    private MyDialogLoadingFragment fragment;
    private MyDialogWifiSetFragment dialogWifiSetFragment;

    private BlueDeviceAdapter.OnItemClickListener itemClickListener;
    private int itemPosition;
    private BlueDeviceInfo lastConnectedDevice;
    /**
     * Printer filter
     */
    private String printNameStart = "";
    Handler handler = new Handler(Looper.getMainLooper());

    private boolean isSaveInstanceStateCalled = false;


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        isSaveInstanceStateCalled = true;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        bind = FragmentBluetoothConnectBinding.inflate(inflater, container, false);

        return bind.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        init();
    }



    @Override
    public void onResume() {
        super.onResume();
        isSaveInstanceStateCalled = false;
        if(fragment!=null){
            fragment.dismiss();
        }
        initEvent();

    }

    private void init() {
        context = MyApplication.getInstance();
        bind.spinKit.setVisibility(View.GONE);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();



        deviceList = new HashSet<>();
        blueDeviceList = new ArrayList<>();
        //Register broadcast
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        Log.d(TAG, "Initialize: Register broadcast ");
        requireActivity().registerReceiver(receiver, intentFilter);
        Log.d(TAG, "Initialize: Registration complete");
        //Register Bluetooth list adapter
        blueDeviceAdapter = new BlueDeviceAdapter(blueDeviceList);
        bind.rvDeviceList.setAdapter(blueDeviceAdapter);
        bind.rvDeviceList.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        //Register thread pool
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("connect_activity_pool_%d");
            return thread;
        };

        executorService = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>(1024), threadFactory, new ThreadPoolExecutor.AbortPolicy());



    }

    @SuppressLint({"NotifyDataSetChanged", "MissingPermission"})
    private void initEvent() {
        final String[] connectedDeviceName = {""};
        bind.rgPrintModel.setOnCheckedChangeListener((group, checkedId) -> {
            mBluetoothAdapter.cancelDiscovery();
            bind.spinKit.setVisibility(View.GONE);
            // First set to hidden, adjust visibility later based on situation
            bind.etModel.setVisibility(View.GONE);

            if (R.id.rb_b3s == checkedId) {
                printNameStart = "B3S";
            } else if (R.id.rb_b21 == checkedId) {
                printNameStart = "B21";
            } else if (R.id.rb_Z401 == checkedId) {
                printNameStart = "Z401";
            } else if (R.id.rb_all == checkedId) {
                printNameStart = "";
            } else if (R.id.rb_input == checkedId) {
                bind.etModel.setVisibility(View.VISIBLE);
                printNameStart = USER_DEFINED;
            }

        });


        executorService.submit(() -> {
            SharedPreferences preferences = context.getSharedPreferences("connectedPrinterInfo", Context.MODE_PRIVATE);
            String deviceName = preferences.getString("deviceName", "");
            String deviceHardwareAddress = preferences.getString("deviceHardwareAddress", "");
            int connectState = preferences.getInt("connectState", 12);

            Log.d(TAG, "Test: Pairing status change: Determine connection status 1" );
            if (PrintUtil.isConnection() == 0) {
                Log.d(TAG, "Test: Pairing status change: Determine connection status 2" );
                handler.post(() -> {
                    if (!deviceName.isEmpty()&&PrintUtil.getConnectedType()==0) {
                        Log.d(TAG, "Test: Pairing status change: Determine connection status 3" );
                        lastConnectedDevice = new BlueDeviceInfo(deviceName, deviceHardwareAddress, connectState);
                        bind.tvConnected.setVisibility(View.VISIBLE);
                        connectedDeviceName[0] = lastConnectedDevice.getDeviceName();
                        bind.tvName.setText(connectedDeviceName[0]);
                        bind.tvAddress.setText(lastConnectedDevice.getDeviceHardwareAddress());
                        setWifiConfigureDisplayStatus(connectedDeviceName[0]);
                        bind.tvStatus.setText("Disconnect");
                        bind.clConnected.setVisibility(View.VISIBLE);
                    }
                });

            }else {
                Log.d(TAG, "Test: Pairing status change: Determine connection status 4" );
                closeProcess();
            }
        });

        itemClickListener = position -> {
            itemPosition = position;
            if (mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.cancelDiscovery();
            }

            int connectState = blueDeviceList.get(position).getConnectState();
            BluetoothDevice bluetoothDevice = mBluetoothAdapter.getRemoteDevice(blueDeviceList.get(position).getDeviceHardwareAddress());
            switch (connectState) {
                case Constant.NO_BOND -> executorService.submit(() -> {
                    requireActivity().runOnUiThread(() -> {
                        bind.spinKit.setVisibility(View.GONE);
                        fragment = new MyDialogLoadingFragment("Pairing");
                        fragment.show(requireActivity().getSupportFragmentManager(), "pairing");
                    });
                    Log.d(TAG, "Pairing: Start");
                    boolean returnValue = false;
                    try {
                        returnValue = BluetoothUtils.createBond(bluetoothDevice);
                    } catch (Exception e) {
                        Log.d(TAG, "Crash log" + e.getMessage());
                    }
                    Log.d(TAG, "Pairing: In progress:" + returnValue);

                });

                case Constant.BONDED -> executorService.submit(() -> {
                    requireActivity().runOnUiThread(() -> {
                        bind.spinKit.setVisibility(View.GONE);
                        fragment = new MyDialogLoadingFragment("Connecting");
                        fragment.show(requireActivity().getSupportFragmentManager(), "CONNECT");
                    });

                    BlueDeviceInfo blueDeviceInfo = new BlueDeviceInfo(bluetoothDevice.getName(), bluetoothDevice.getAddress(), connectState);
                    PrintUtil.setConnectedType(-1);
                    int connectResult = PrintUtil.connectBluetoothPrinter(blueDeviceInfo.getDeviceHardwareAddress());
                    Log.d(TAG, "Test: Connection result " + connectResult);

                    handler.post(() -> {
                        String hint = "";

                        switch (connectResult) {
                            case 0 -> {
                                lastConnectedDevice = blueDeviceInfo;
                                lastConnectedDevice.setConnectState(13);
                                blueDeviceList.remove(position);
                                hint = "Connection successful";
                                SharedPreferences preferences = context.getSharedPreferences("printConfiguration", Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = preferences.edit();
                                SharedPreferences connectedPrinterInfo = context.getSharedPreferences("connectedPrinterInfo", Context.MODE_PRIVATE);
                                SharedPreferences.Editor connectedPrinterInfoEditor = connectedPrinterInfo.edit();
                                String printerName = lastConnectedDevice.getDeviceName();
                                if (printerName.matches("^(B32|Z401|T8).*")) {
                                    editor.putInt("printMode", 2);
                                    editor.putInt("printDensity", 8);
                                    editor.putFloat("printMultiple", 11.81F);
                                } else if (printerName.matches("^(M2|M3|EP2M).*")) {
                                    editor.putInt("printMode", 2);
                                    editor.putInt("printDensity", 3);
                                    editor.putFloat("printMultiple", 11.81F);
                                } else if (printerName.matches("^(B21_Pro).*")) {
                                    editor.putInt("printMode", 1);
                                    editor.putInt("printDensity", 3);
                                    editor.putFloat("printMultiple", 11.81F);
                                } else {
                                    editor.putInt("printMode", 1);
                                    editor.putInt("printDensity", 3);
                                    editor.putFloat("printMultiple", 8);
                                }
                                editor.apply(); //Submit changes
                                connectedPrinterInfoEditor.putString("deviceName", lastConnectedDevice.getDeviceName());
                                connectedPrinterInfoEditor.putString("deviceHardwareAddress", lastConnectedDevice.getDeviceHardwareAddress());
                                connectedPrinterInfoEditor.putInt("connectState", lastConnectedDevice.getConnectState());
                                connectedPrinterInfoEditor.apply();
                            }
                            case -1 -> hint = "Connection failed";
                            case -2 -> hint = "Unsupported device model";
                            default -> {
                            }
                        }

                        if (lastConnectedDevice != null) {
                            blueDeviceAdapter.notifyItemRemoved(position);
                            bind.tvConnected.setVisibility(View.VISIBLE);
                            connectedDeviceName[0] = lastConnectedDevice.getDeviceName();
                            bind.tvName.setText(connectedDeviceName[0]);
                            bind.tvAddress.setText(lastConnectedDevice.getDeviceHardwareAddress());
                            setWifiConfigureDisplayStatus(connectedDeviceName[0]);
                            bind.tvStatus.setText("Disconnect");
                            bind.clConnected.setVisibility(View.VISIBLE);
                        }

                        if (isSaveInstanceStateCalled) {
                            // Already called, do not execute DialogFragment.dismiss() method
                            return;
                        }


                        fragment.dismiss();
                        Toast.makeText(getActivity(), hint, Toast.LENGTH_SHORT).show();
                    });
                });
                default -> {
                }
            }
        };

        bind.tvStatus.setOnClickListener(v -> {
            PrintUtil.close();
            closeProcess();
        });

        blueDeviceAdapter.setOnClickListener(itemClickListener);

        bind.clSearch.setOnClickListener(v -> {
            Log.d(TAG, "Test: Initialize: Search ");
            bind.spinKit.setVisibility(View.GONE);
            if (!mBluetoothAdapter.isEnabled()) {
                Toast.makeText(getActivity(), "Bluetooth is not enabled", Toast.LENGTH_SHORT).show();
            } else {
                permissionRequest();
            }


        });

        bind.tvWifiConfigure.setOnClickListener(v -> {
            WifiManager wifiManager = (WifiManager) requireActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            String connectedWifiName = "";
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                connectedWifiName = wifiInfo.getSSID().replace("\"", "");
            }


            dialogWifiSetFragment = new MyDialogWifiSetFragment(connectedWifiName, (wifiName, wifiPassword) -> {

                // Validate WiFi name
                if (TextUtils.isEmpty(wifiName)) {
                    Toast.makeText(getActivity(), "Please enter WiFi name", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Validate WiFi password
                if (TextUtils.isEmpty(wifiPassword)) {
                    Toast.makeText(getActivity(), "Please enter WiFi password", Toast.LENGTH_SHORT).show();
                    return;
                }

                fragment = new MyDialogLoadingFragment("Configuring");
                fragment.show(requireActivity().getSupportFragmentManager(), "configure");
                // Pass WiFi account and password to main thread through Handler
                handler.post(() -> {
                    // Execute UI-related operations in main thread
                    // Simulate WiFi configuration operation
                    simulateWiFiConfiguration(wifiName, wifiPassword);
                });


            });
            dialogWifiSetFragment.show(requireActivity().getSupportFragmentManager(), "configure");


        });


    }

    private void  closeProcess() {
        SharedPreferences preferences = context.getSharedPreferences("printConfiguration", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("printMode", 1);
        editor.putInt("printDensity", 3);
        editor.putFloat("printMultiple", 8);
        editor.apply(); //Submit changes

        SharedPreferences connectedPrinterInfo = context.getSharedPreferences("connectedPrinterInfo", Context.MODE_PRIVATE);
        SharedPreferences.Editor connectedPrinterInfoEditor = connectedPrinterInfo.edit();
        connectedPrinterInfoEditor.putString("deviceName", "");
        connectedPrinterInfoEditor.putString("deviceHardwareAddress", "");
        connectedPrinterInfoEditor.putInt("connectState", 11);
        connectedPrinterInfoEditor.apply();

        if (lastConnectedDevice != null) {
            lastConnectedDevice.setConnectState(12);
            blueDeviceList.add(lastConnectedDevice);
        }

        requireActivity().runOnUiThread(() -> {
            bind.tvConnected.setVisibility(View.GONE);
            bind.clConnected.setVisibility(View.GONE);
            blueDeviceAdapter.notifyItemChanged(blueDeviceList.size() - 1);
        });
        lastConnectedDevice = null;



    }

    /**
     * Add method in this class to simulate WiFi configuration operation
     * @param wifiName WiFi network name
     * @param wifiPassword WiFi network password
     */
    private void simulateWiFiConfiguration(String wifiName, String wifiPassword) {
        // Execute WiFi configuration operation in child thread through thread pool
        // This method configures WiFi settings and returns:
        // 0 - Configuration successful
        // -1 - Configuration failed  
        // -3 - Device not supported
        executorService.execute(() -> {
            int wifiConfigureResult = PrintUtil.getInstance().configurationWifi(wifiName, wifiPassword);
            // Handle UI-related operations in main thread
            handler.post(() -> {
                // Configuration result handling
                if (wifiConfigureResult == 0) {
                    // Configuration successful
                    Toast.makeText(getActivity(), "WiFi configuration successful", Toast.LENGTH_SHORT).show();
                } else if (wifiConfigureResult == -1) {
                    // Configuration failed
                    Toast.makeText(getActivity(), "WiFi configuration failed", Toast.LENGTH_SHORT).show();
                } else if (wifiConfigureResult == -3) {
                    // Device not supported
                    Toast.makeText(getActivity(), "Your device is not supported", Toast.LENGTH_SHORT).show();
                }

                // Configuration completed, close loading dialog
                fragment.dismiss();
            });
        });
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {


        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // Bluetooth discovery
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (device != null) {
                    @SuppressLint("MissingPermission") String deviceName = device.getName();
                    String deviceHardwareAddress = device.getAddress();
                    @SuppressLint("MissingPermission") int deviceStatus = device.getBondState();


                    @SuppressLint("MissingPermission") boolean supportBluetoothType = device.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC || device.getType() == BluetoothDevice.DEVICE_TYPE_DUAL;
                    boolean supportPrintName;
                    if (USER_DEFINED.equals(printNameStart)) {
                        printNameStart = bind.etModel.getText().toString().trim();
                    }

                    if (TextUtils.isEmpty(printNameStart)) {
                        supportPrintName = deviceName != null;
                    } else {
                        supportPrintName = deviceName != null && deviceName.startsWith(printNameStart);
                    }


                    if (supportBluetoothType && supportPrintName) {
                        if (deviceList.add(deviceName)) {
                            blueDeviceList.add(new BlueDeviceInfo(deviceName, deviceHardwareAddress, deviceStatus));
                            blueDeviceAdapter.notifyItemInserted(blueDeviceList.size());
                        }
                    }

                }

            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                bind.spinKit.setVisibility(View.VISIBLE);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                bind.spinKit.setVisibility(View.GONE);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                if (itemPosition != -1 && itemPosition < blueDeviceList.size()) {
                    blueDeviceList.get(itemPosition).setConnectState(state);
                    blueDeviceAdapter.notifyItemChanged(itemPosition);
                }

            } else if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                if (fragment != null) {
                    fragment.dismiss();
                }
            }
        }
    };

    /**
     * Check GPS enabled status
     *
     * @return GPS enabled or not
     */
    public boolean isGpsOPen() {
        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void permissionRequest() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        } else {
            permissions = new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION};
        }

        PermissionX.init(requireActivity())
                .permissions(permissions)
                .request(this::handlePermissionResult);
    }

    private void handlePermissionResult(boolean allGranted, List<String> grantedList, List<String> deniedList) {
        if (allGranted) {
            handleAllPermissionsGranted();
        } else {
            Toast.makeText(getActivity(), "Permission request failed" + deniedList, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleAllPermissionsGranted() {
        if (!isGpsOPen()) {
            // All permissions granted
            MyDialogFragment fragment = new MyDialogFragment("Please enable GPS, not enabling may cause normal Bluetooth search to fail", 1);
            fragment.show(requireActivity().getSupportFragmentManager(), "GPS");
        } else {
            startBluetoothDiscovery();
        }
    }


    @SuppressLint({"MissingPermission", "NotifyDataSetChanged"})
    private void startBluetoothDiscovery() {
        itemPosition = -1;
        int itemCount = blueDeviceList.size();
        //Clear list data
        deviceList.clear();
        blueDeviceList.clear();
        blueDeviceAdapter.notifyItemRangeRemoved(0, itemCount);
        bind.spinKit.setVisibility(View.VISIBLE);
        if (mBluetoothAdapter.isDiscovering()) {
            if (mBluetoothAdapter.cancelDiscovery()) {

                executorService.execute(() -> {
                    try {
                        //Wait 1 second after cancellation before searching again
                        Thread.sleep(1000);
                        mBluetoothAdapter.startDiscovery();

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            }
        } else {
            mBluetoothAdapter.startDiscovery();
        }
    }

    private void setWifiConfigureDisplayStatus(String deviceName) {
        if (deviceName.startsWith("K3_W")) {
            bind.tvWifiConfigure.setVisibility(View.VISIBLE);
        } else {
            bind.tvWifiConfigure.setVisibility(View.GONE);
        }
    }


    @SuppressLint("MissingPermission")
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister broadcast receiver
        if (mBluetoothAdapter != null && mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        requireActivity().unregisterReceiver(receiver);
    }
}