package ca.mopicaltechtronic.keeptheburn;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity implements HeartRateView {
    private TextView tvHeartRate;
    private TextInputEditText etTopHr;
    private TextInputEditText etBottomHr;
    private BleHeartRateManager ble_manager;
    private ActivityResultLauncher<String[]> permissionLauncher;

    private MaterialButton btnConnect;
    private ProgressBar pgConnecting;
    private HeartRateView activity;

    AppPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvHeartRate = findViewById(R.id.tvHeartRate);
        etTopHr = findViewById(R.id.etTopHr);
        etBottomHr = findViewById(R.id.etBottomHr);

        MaterialButton btnAddDevice = findViewById(R.id.btnAddDevice);
        btnConnect = findViewById(R.id.btnConnect);
        pgConnecting = findViewById(R.id.progressBar);

        btnAddDevice.setOnClickListener(v -> {
            launchScanActivity(this);
        });

        btnConnect.setOnClickListener(v -> {
            btnConnect.setEnabled(false);
            btnConnect.setText("");
            pgConnecting.setVisibility(ProgressBar.VISIBLE);
            initBleConnection();
        });

        preferences = new AppPreferences(this);
        setupRollerBehavior();
        checkForPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                    hrReceiver,
                    new IntentFilter("HR_UPDATE"),
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(hrReceiver, new IntentFilter("HR_UPDATE"));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(hrReceiver);
    }

    private final ActivityResultLauncher<Intent> activityBLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            String mac = result.getData().getStringExtra("mac_address");

            preferences.saveMacAddress(mac);
        }
    });

    private void launchScanActivity(Activity caller) {
        Intent intent = new Intent(caller, activity_ble_scan.class);
        activityBLauncher.launch(intent);
    }

    private void checkForPermissions() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;

                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (!allGranted) {
                        // User said no :(
                        Toast.makeText(this,
                                "Bluetooth permissions required!",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this,
                            Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {

                permissionLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.POST_NOTIFICATIONS
                });

            }
        }
    }

    private int TryGetText(TextInputEditText text, int def) {
        if (text != null && text.getText() != null) {
            String value = String.valueOf(text.getText());
            if (value.isEmpty())
                return def;
            else
                return Integer.parseInt(value);
        }

        return def;
    }

    private void initBleConnection() {
        String mac = preferences.getMacAddress();
        String bottom, top;

        if (mac != null) {
//            ble_manager = new BleHeartRateManager(this, mac, this);
            Intent serviceIntent = new Intent(this, BleForegroundService.class);
            serviceIntent.putExtra("mac_address", mac);

            serviceIntent.putExtra("min_hr", TryGetText(etBottomHr, 0));
            serviceIntent.putExtra("max_hr", TryGetText(etTopHr, 250));

            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            Toast.makeText(this, "Device was not yet set.", Toast.LENGTH_LONG).show();
        }
    }

    private void setupRollerBehavior() {
        etTopHr.setOnLongClickListener(v -> {
            adjustValue(etTopHr, 1);
            return true;
        });

        etBottomHr.setOnLongClickListener(v -> {
            adjustValue(etBottomHr, -1);
            return true;
        });
    }

    private void adjustValue(TextInputEditText editText, int delta) {
        String valueStr = editText.getText() != null ? editText.getText().toString() : "";
        int value = valueStr.isEmpty() ? 0 : Integer.parseInt(valueStr);
        value += delta;
        editText.setText(String.valueOf(value));
    }

    @Override
    public void onHeartRateReceived(int heartRate) {
        runOnUiThread(() -> {
            tvHeartRate.setText(heartRate + " bpm");
        });
    }

    @Override
    public void onConnectionStateChanged(ConnectionStatus status) {
        runOnUiThread(() -> {
            switch (status) {
                case NotConnected:
                    tvHeartRate.setText("Not connected");
                case NotAllowed:
                    tvHeartRate.setText("Not allowed");
                case Connecting:
                    tvHeartRate.setText("Connecting...");
                case Connected:
                    tvHeartRate.setText("Connected");
                case NotFound:
                    tvHeartRate.setText("Not Found");
            }
        });
    }

    private final BroadcastReceiver hrReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.hasExtra("heart_rate")) {
                int hr = intent.getIntExtra("heart_rate", 0);
                runOnUiThread(() -> {
                    tvHeartRate.setText(hr + " bpm");
                });
            } else if (intent.hasExtra("status")) {
                String status = intent.getStringExtra("status");
                if (status == null)
                    return;

                switch (status) {
                    case "timeout":
                        Toast.makeText(context, "Couldn't find HR device", Toast.LENGTH_LONG).show();
                        btnConnect.setText("Connect");
                        btnConnect.setEnabled(true);
                        pgConnecting.setVisibility(ProgressBar.GONE);
                        break;
                    case "services":
                        Toast.makeText(context, "Device found, connecting...", Toast.LENGTH_LONG).show();
                        break;
                    case "disconnected":
                        Toast.makeText(context, "HR device disconnected", Toast.LENGTH_LONG).show();
                        btnConnect.setText("Connect");
                        btnConnect.setEnabled(true);
                        pgConnecting.setVisibility(ProgressBar.GONE);
                        break;
                    case "connected":
                        btnConnect.setText("Connected");
                        pgConnecting.setVisibility(ProgressBar.GONE);
                        break;
                    case "terminated":
                        Toast.makeText(context, "HR monitoring stopped", Toast.LENGTH_LONG).show();
                        btnConnect.setText("Connect");
                        btnConnect.setEnabled(true);
                        pgConnecting.setVisibility(ProgressBar.GONE);
                        break;
                }
            }
        }
    };
}