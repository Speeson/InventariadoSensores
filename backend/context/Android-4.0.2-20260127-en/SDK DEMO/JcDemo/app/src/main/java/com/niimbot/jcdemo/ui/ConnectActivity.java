package com.niimbot.jcdemo.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import com.google.android.material.tabs.TabLayoutMediator;
import com.niimbot.jcdemo.adapter.ConnectAdapter;
import com.niimbot.jcdemo.databinding.ActivityConnectBinding;
import com.niimbot.jcdemo.fragment.BluetoothConnectFragment;
import com.niimbot.jcdemo.fragment.WifiConnectFragment;

/**
 * Device connection
 *
 * @author zhangbin
 * 2022.03.17
 */
public class ConnectActivity extends AppCompatActivity  {
    private static final String TAG = "ConnectActivity";

    private ActivityConnectBinding bind;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bind = ActivityConnectBinding.inflate(getLayoutInflater());
        setContentView(bind.getRoot());
        init();
    }




    private void init() {
        ConnectAdapter connectAdapter = new ConnectAdapter(this);
        bind.viewPager.setAdapter(connectAdapter);
        new TabLayoutMediator(bind.tabs, bind.viewPager, (tab, position) -> {
            Log.d(TAG, "Wuhan: " + position);
            tab.setText(position == 0 ? "Bluetooth" : "WiFi");

        }).attach();

        bind.ivBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}