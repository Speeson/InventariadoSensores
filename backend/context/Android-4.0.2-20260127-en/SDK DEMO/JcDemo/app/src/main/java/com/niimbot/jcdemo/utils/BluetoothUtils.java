package com.niimbot.jcdemo.utils;

import android.bluetooth.BluetoothDevice;

import java.lang.reflect.Method;

/**
 * Bluetooth utility class for handling Bluetooth device pairing and other operations.
 * @author zhangbin
 */
public class BluetoothUtils {

    /**
     * Pair with a device
     *
     * @param btDevice Bluetooth device to pair with
     * @return Whether pairing was successful
     * @throws Exception Reflection calls may throw exceptions
     */
    public static boolean createBond(BluetoothDevice btDevice) throws Exception {
        // Get the class object of BluetoothDevice class
        Class<?> btClass = BluetoothDevice.class;

        // Get a reference to the createBond method
        Method createBondMethod = btClass.getMethod("createBond");
        // Call the createBond method and return the pairing result
        return (Boolean) createBondMethod.invoke(btDevice);
    }


}
