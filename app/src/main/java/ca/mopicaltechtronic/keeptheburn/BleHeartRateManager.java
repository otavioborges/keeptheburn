package ca.mopicaltechtronic.keeptheburn;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.UUID;

public class BleHeartRateManager {
    private static final String TAG = "BleHRManager";

    private static final UUID HR_SERVICE_UUID =
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");

    private static final UUID HR_MEASUREMENT_UUID =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");

    private static final UUID CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGatt bluetoothGatt;
    private final HeartRateView heartRateView;
    private final Context context;

    public BleHeartRateManager(Context context, String macAddress, HeartRateView view) {
        this.context = context;
        this.heartRateView = view;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = adapter.getRemoteDevice(macAddress);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            view.onConnectionStateChanged(ConnectionStatus.NotAllowed);
            return;
        } else {
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected");
                heartRateView.onConnectionStateChanged(ConnectionStatus.Connecting);
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    heartRateView.onConnectionStateChanged(ConnectionStatus.NotAllowed);
                    return;
                }
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected");
                heartRateView.onConnectionStateChanged(ConnectionStatus.NotConnected);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService hrService = gatt.getService(HR_SERVICE_UUID);
            if (hrService == null) {
                Log.e(TAG, "HR Service not found");
                return;
            }

            BluetoothGattCharacteristic hrCharacteristic =
                    hrService.getCharacteristic(HR_MEASUREMENT_UUID);

            enableHeartRateNotification(gatt, hrCharacteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {

            if (HR_MEASUREMENT_UUID.equals(characteristic.getUuid())) {
                int heartRate = parseHeartRate(characteristic);
                heartRateView.onHeartRateReceived(heartRate);
            }
        }
    };

    private void enableHeartRateNotification(BluetoothGatt gatt,
                                             BluetoothGattCharacteristic characteristic) {

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            heartRateView.onConnectionStateChanged(ConnectionStatus.NotAllowed);
            return;
        }
        gatt.setCharacteristicNotification(characteristic, true);

        BluetoothGattDescriptor descriptor =
                characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);

        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
        }
    }

    private int parseHeartRate(BluetoothGattCharacteristic characteristic) {
        int flag = characteristic.getProperties();
        int format;

        // 8 or 16 bit format
        if ((characteristic.getValue()[0] & 0x01) != 0) {
            format = BluetoothGattCharacteristic.FORMAT_UINT16;
        } else {
            format = BluetoothGattCharacteristic.FORMAT_UINT8;
        }

        return characteristic.getIntValue(format, 1);
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                heartRateView.onConnectionStateChanged(ConnectionStatus.NotAllowed);
                return;
            }

            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
}
