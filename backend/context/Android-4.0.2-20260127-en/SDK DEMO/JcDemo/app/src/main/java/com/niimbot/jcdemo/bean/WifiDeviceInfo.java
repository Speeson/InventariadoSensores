package com.niimbot.jcdemo.bean;

public class WifiDeviceInfo {

    String deviceName;
    String ip;
    int port;
    int connectState;

    public WifiDeviceInfo(String deviceName, String ip, int port, int connectState) {
        this.deviceName = deviceName;
        this.ip = ip;
        this.port = port;
        this.connectState = connectState;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getConnectState() {
        return connectState;
    }

    public void setConnectState(int connectState) {
        this.connectState = connectState;
    }


}
