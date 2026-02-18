package ca.mopicaltechtronic.keeptheburn;

public class BleDevice {
    public String name;
    public String mac;
    public int rssi;

    public BleDevice(String name, String mac, int rssi) {
        this.name = name;
        this.mac = mac;
        this.rssi = rssi;
    }
}
