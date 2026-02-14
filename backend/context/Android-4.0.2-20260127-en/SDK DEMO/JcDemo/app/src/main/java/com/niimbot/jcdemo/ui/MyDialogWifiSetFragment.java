package com.niimbot.jcdemo.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.niimbot.jcdemo.R;

public class MyDialogWifiSetFragment extends DialogFragment {
    private MyDialogWifiSetFragment.OnWifiSetListener onWifiSetListener;
    private String wifiNameSet;

    public interface OnWifiSetListener {
        void onWifiSet(String wifiName, String wifiPassword);
    }

    public MyDialogWifiSetFragment(String connectedWifiName, OnWifiSetListener listener) {
        super();
        onWifiSetListener = listener;
        wifiNameSet = connectedWifiName;
    }



    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle("Configure Printer WiFi").setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        // Get the layout inflater
        LayoutInflater inflater = requireActivity().getLayoutInflater();


        View view = inflater.inflate(R.layout.dialog_wifi_set, null);

        builder.setView(view);
        EditText etWifiName = view.findViewById(R.id.et_wifi_name);
        etWifiName.setText(wifiNameSet);

        builder.setPositiveButton("Configure", (dialog, which) -> {
            EditText etWifiPassword = view.findViewById(R.id.et_wifi_password);
            if (etWifiPassword != null) {
                String wifiName = etWifiName.getText().toString().trim();
                String wifiPassword = etWifiPassword.getText().toString().trim();

                dialog.dismiss();
                onWifiSetListener.onWifiSet(wifiName, wifiPassword);
            }
        });

        return builder.create();
    }


}
