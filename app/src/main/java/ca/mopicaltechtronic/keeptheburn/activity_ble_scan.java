package ca.mopicaltechtronic.keeptheburn;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class activity_ble_scan extends AppCompatActivity {
    private final int SCAN_PERIOD = 30000;

    private static final UUID HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private final List<BleDevice> deviceList = new ArrayList<>();
    private Context context;

    private BluetoothLeScanner scanner;
    private BleDeviceAdapter adapterList;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean scanning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_scan);
        context = this;

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapterList = new BleDeviceAdapter(deviceList, device -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("mac_address", device.mac);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
        recyclerView.setAdapter(adapterList);

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            Toast.makeText(this, "Unable to retrieve bluetooth adapter", Toast.LENGTH_LONG);
            finish();
            return;
        }

        if (!adapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth is disabled, unable to scan", Toast.LENGTH_LONG);
            finish();
            return;
        }

        scanner = adapter.getBluetoothLeScanner();
        startScan();
    }

    private void startScan() {
        handler.postDelayed(() -> {
            Toast.makeText(context, "Stoping BLE scan...", Toast.LENGTH_LONG);
            stopScan();
        }, SCAN_PERIOD);

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(HR_SERVICE_UUID))
                .build();

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanner.startScan(Collections.singletonList(filter),
                settings,
                bleScanCallback);
        scanning = true;
    }

    private void stopScan() {
        scanner.stopScan(bleScanCallback);
        scanning = false;
    }

    private ScanCallback bleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            String mac = device.getAddress();
            int rssi = result.getRssi();

            for (BleDevice b : deviceList) {
                if (b.mac.equals(mac))
                    return;
            }

            deviceList.add(new BleDevice(name, mac, rssi));

            runOnUiThread(() -> adapterList.notifyDataSetChanged());
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if ((scanner != null) && scanning) {
            stopScan();
        }
    }
}