package com.niimbot.jcdemo.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.gengcon.www.jcprintersdk.bean.PrinterDevice;
import com.niimbot.jcdemo.bean.WifiDeviceInfo;
import com.niimbot.jcdemo.databinding.WifiItemBinding;

import java.util.List;

public class WifiDeviceAdapter extends RecyclerView.Adapter<WifiDeviceAdapter.WifiDevicerViewHolder>{

    private final List<WifiDeviceInfo> wifiDeviceList;
    private OnItemClickListener listener;


    public interface OnItemClickListener {
        /**
         * Item click
         *
         * @param position Position
         */
        void onItemClick(int position);
    }


    public WifiDeviceAdapter(List<WifiDeviceInfo> wifiDeviceList) {
        this.wifiDeviceList = wifiDeviceList;
    }

    public void setOnClickListener(OnItemClickListener onItemClickListener) {
        listener = onItemClickListener;
    }


    @NonNull
    @Override
    public WifiDevicerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        WifiItemBinding bind = WifiItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new WifiDevicerViewHolder(bind);
    }

    @Override
    public void onBindViewHolder(@NonNull WifiDeviceAdapter.WifiDevicerViewHolder holder, int position) {
        holder.bind.tvName.setText(wifiDeviceList.get(position).getDeviceName());
        holder.bind.tvAddress.setText(wifiDeviceList.get(position).getIp());
        holder.bind.tvStatus.setText("Not connected");
        holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
    }

    @Override
    public int getItemCount() {
        return wifiDeviceList!=null?wifiDeviceList.size():0;
    }


    static class  WifiDevicerViewHolder extends RecyclerView.ViewHolder{
        WifiItemBinding bind;
        public WifiDevicerViewHolder(WifiItemBinding bind) {
            super(bind.getRoot());
            this.bind = bind;
        }
    }
}
