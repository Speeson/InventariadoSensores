package com.niimbot.jcdemo.utils;

import android.Manifest;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;

import com.permissionx.guolindev.PermissionX;

import java.util.List;

/**
 * Permission utility class
 *
 * @author zhangbin
 */
public class PermissionUtils {
    private static final String TAG = "PermissionUtils";

    public static void requestPermission(FragmentActivity activity,String[] permissions){
        PermissionX.init(activity)
                .permissions(permissions)
                .request(PermissionUtils::handlePermissionResult) ;
    }


    private static void handlePermissionResult(boolean allGranted, List<String> grantedList, List<String> deniedList) {
        if (allGranted) {
            // All permissions granted
            Log.d(TAG, "handlePermissionResult: "+grantedList.toString());
        } else {
            // Show denied permissions
            Log.d(TAG, "handlePermissionResult: "+deniedList.toString());
        }
    }
}
