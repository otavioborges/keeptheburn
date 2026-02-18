package ca.mopicaltechtronic.keeptheburn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.UUID;

public class BleForegroundService extends Service {
    private static final String TAG = "BLE_SERVICE";
    private static final String CHANNEL_ID = "ble_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final UUID HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID HR_MEASUREMENT_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCC_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private NotificationManager notifManager;
    private BluetoothGatt gatt = null;
    private Runnable timeoutConnection;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private int maxHr;
    private int minHr;
    private boolean wasLow = false;
    private boolean wasHigh = false;
    private MediaPlayer lowHrPlayer;
    private MediaPlayer highHrPlayer;

    private PendingIntent stopPendingIntent = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if ("STOP_SERVICE".equals(intent.getAction())) {
            stopServiceCleanly();
            return START_NOT_STICKY;
        }

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        lowHrPlayer = MediaPlayer.create(this, R.raw.power_up);
        highHrPlayer = MediaPlayer.create(this, R.raw.power_down);

        minHr = intent.getIntExtra("min_hr", 0);
        maxHr = intent.getIntExtra("max_hr", 220);
        String mac = intent.getStringExtra("mac_address");
        connectToDevice(mac);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
        }

        if (lowHrPlayer != null)
            lowHrPlayer.release();
        if (highHrPlayer != null)
            highHrPlayer.release();

        Intent intent = new Intent("HR_UPDATE");
        intent.setPackage(getPackageName());
        intent.putExtra("status", "terminated");

        sendBroadcast(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void stopServiceCleanly() {
        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Heart Rate Monitoring",
                NotificationManager.IMPORTANCE_LOW
        );

        notifManager = getSystemService(NotificationManager.class);
        notifManager.createNotificationChannel(channel);
    }

    private Notification createNotification() {
        Intent stopIntent = new Intent(this, BleForegroundService.class);
        stopIntent.setAction("STOP_SERVICE");

        stopPendingIntent = PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Heart Rate Monitoring")
                .setContentText("Heart Rate: -- bpm")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOnlyAlertOnce(true)
                .addAction(android.R.drawable.ic_delete,
                        "Stop",
                        stopPendingIntent)
                .build();
    }

    private void enableNotify(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        gatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCC_UUID);

        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    private int parseHeartRate(BluetoothGattCharacteristic characteristic) {

        int flag = characteristic.getValue()[0] & 0x01;
        int format = (flag != 0) ?
                BluetoothGattCharacteristic.FORMAT_UINT16 :
                BluetoothGattCharacteristic.FORMAT_UINT8;

        return characteristic.getIntValue(format, 1);
    }

    private void doBusinessLogic(int hr) {
        Intent intent = new Intent("HR_UPDATE");
        intent.setPackage(getPackageName());
        intent.putExtra("heart_rate", hr);

        sendBroadcast(intent);
        /* play some sounds boi! */
        if (hr > maxHr) {
            if (!wasHigh) {
                highHrPlayer.seekTo(0);
                highHrPlayer.start();
            }

            wasHigh = true;
        } else if (hr < minHr) {
            if (!wasLow) {
                lowHrPlayer.seekTo(0);
                lowHrPlayer.start();
            }

            wasLow = true;
        } else {
            wasHigh = false;
            wasLow = false;
        }

        if (stopPendingIntent != null) {
            NotificationCompat.Builder build = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Heart Rate Monitoring")
                    .setContentText("Heart Rate: " + hr + " bpm")
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .addAction(android.R.drawable.ic_delete,
                            "Stop",
                            stopPendingIntent);

            if (wasHigh) {
                build.setColor(Color.RED).setColorized(true);
            } else if (wasLow) {
                build.setColor(Color.BLUE).setColorized(true);
            } else {
                build.setColorized(false);
            }

            notifManager.notify(NOTIFICATION_ID, build.build());
        }

    }

    private void connectToDevice(String mac) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = adapter.getRemoteDevice(mac);

        gatt = device.connectGatt(this, false, gattCallback);
        timeoutConnection = new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent("HR_UPDATE");
                intent.setPackage(getPackageName());
                intent.putExtra("status", "timeout");
                sendBroadcast(intent);

                stopServiceCleanly();
            }
        };

        timeoutHandler.postDelayed(timeoutConnection, 10000);
    }

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            timeoutHandler.removeCallbacks(timeoutConnection);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Intent intent = new Intent("HR_UPDATE");
                intent.setPackage(getPackageName());
                intent.putExtra("status", "services");
                sendBroadcast(intent);

                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTING || newState == BluetoothProfile.STATE_DISCONNECTED) {
                Intent intent = new Intent("HR_UPDATE");
                intent.setPackage(getPackageName());
                intent.putExtra("status", "disconnected");
                sendBroadcast(intent);

                stopServiceCleanly();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(HR_SERVICE_UUID);
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(HR_MEASUREMENT_UUID);

            enableNotify(gatt, characteristic);

            Intent intent = new Intent("HR_UPDATE");
            intent.setPackage(getPackageName());
            intent.putExtra("status", "connected");
            sendBroadcast(intent);
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            int heartRate = parseHeartRate(characteristic);
            doBusinessLogic(heartRate);
        }
    };
}
