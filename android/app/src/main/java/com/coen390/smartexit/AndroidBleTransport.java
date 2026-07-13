package com.coen390.smartexit;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.util.Collections;
import java.util.UUID;

final class AndroidBleTransport implements WeightStationConnection.Transport {
    private static final long SCAN_TIMEOUT_MS = 10_000;
    private static final UUID CLIENT_CONFIGURATION_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context appContext;
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private WeightStationConnection.ScanEvents scanEvents;
    private BluetoothGatt bluetoothGatt;
    private WeightStationConnection.ConnectionEvents connectionEvents;
    private UUID expectedServiceUuid;
    private UUID expectedCharacteristicUuid;
    private boolean connectionReady;

    AndroidBleTransport(Context context, BluetoothAdapter bluetoothAdapter) {
        this.appContext = context.getApplicationContext();
        this.bluetoothAdapter = bluetoothAdapter;
    }

    @Override
    @SuppressLint("MissingPermission")
    public void startScan(UUID serviceUuid, WeightStationConnection.ScanEvents events) {
        stopScan();
        scanEvents = events;
        scanner = bluetoothAdapter.getBluetoothLeScanner();

        if (scanner == null) {
            finishScanWithFailure(WeightStationConnection.Failure.SCAN_UNAVAILABLE);
            return;
        }

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(serviceUuid))
                .build();
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                handleScanResult(result);
            }

            @Override
            public void onScanFailed(int errorCode) {
                finishScanWithFailure(WeightStationConnection.Failure.SCAN_FAILED);
            }
        };

        try {
            scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
            mainHandler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS);
        } catch (SecurityException | IllegalStateException exception) {
            finishScanWithFailure(WeightStationConnection.Failure.SCAN_UNAVAILABLE);
        }
    }

    @Override
    @SuppressLint("MissingPermission")
    public void stopScan() {
        mainHandler.removeCallbacks(scanTimeout);

        if (scanner != null && scanCallback != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException | IllegalStateException ignored) {
            }
        }

        scanner = null;
        scanCallback = null;
        scanEvents = null;
    }

    @Override
    @SuppressLint("MissingPermission")
    public void connect(
            WeightStationConnection.DeviceCandidate device,
            UUID serviceUuid,
            UUID characteristicUuid,
            WeightStationConnection.ConnectionEvents events
    ) {
        disconnectGatt();
        connectionEvents = events;
        expectedServiceUuid = serviceUuid;
        expectedCharacteristicUuid = characteristicUuid;
        connectionReady = false;

        try {
            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(device.address);
            bluetoothGatt = bluetoothDevice.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
            );

            if (bluetoothGatt == null) {
                finishConnectionWithFailure(WeightStationConnection.Failure.CONNECTION_FAILED);
            }
        } catch (IllegalArgumentException | SecurityException exception) {
            finishConnectionWithFailure(WeightStationConnection.Failure.CONNECTION_FAILED);
        }
    }

    @Override
    @SuppressLint("MissingPermission")
    public void disconnect() {
        connectionEvents = null;
        disconnectGatt();
    }

    @SuppressLint("MissingPermission")
    private void handleScanResult(ScanResult result) {
        if (scanEvents == null) {
            return;
        }

        String name = result.getScanRecord() == null
                ? null
                : result.getScanRecord().getDeviceName();
        WeightStationConnection.DeviceCandidate candidate =
                new WeightStationConnection.DeviceCandidate(result.getDevice().getAddress(), name);
        WeightStationConnection.ScanEvents events = scanEvents;
        stopScan();
        events.onDeviceFound(candidate);
    }

    private final Runnable scanTimeout = () ->
            finishScanWithFailure(WeightStationConnection.Failure.STATION_NOT_FOUND);

    private void finishScanWithFailure(WeightStationConnection.Failure failure) {
        WeightStationConnection.ScanEvents events = scanEvents;
        stopScan();
        if (events != null) {
            events.onScanFailed(failure);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (gatt != bluetoothGatt) {
                return;
            }

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                discoverServices(gatt);
                return;
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handleGattDisconnected(gatt);
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                finishConnectionWithFailure(WeightStationConnection.Failure.CONNECTION_FAILED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (gatt != bluetoothGatt) {
                return;
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                finishConnectionWithFailure(WeightStationConnection.Failure.CONNECTION_FAILED);
                return;
            }

            enableWeightNotifications(gatt);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (gatt != bluetoothGatt || !CLIENT_CONFIGURATION_UUID.equals(descriptor.getUuid())) {
                return;
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                finishConnectionWithFailure(WeightStationConnection.Failure.NOTIFICATION_SETUP_FAILED);
                return;
            }

            connectionReady = true;
            WeightStationConnection.ConnectionEvents events = connectionEvents;
            if (events != null) {
                events.onReady();
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void discoverServices(BluetoothGatt gatt) {
        try {
            if (!gatt.discoverServices()) {
                finishConnectionWithFailure(WeightStationConnection.Failure.CONNECTION_FAILED);
            }
        } catch (SecurityException exception) {
            finishConnectionWithFailure(WeightStationConnection.Failure.CONNECTION_FAILED);
        }
    }

    @SuppressLint("MissingPermission")
    private void enableWeightNotifications(BluetoothGatt gatt) {
        BluetoothGattService service = gatt.getService(expectedServiceUuid);
        if (service == null) {
            finishConnectionWithFailure(WeightStationConnection.Failure.SERVICE_MISSING);
            return;
        }

        BluetoothGattCharacteristic characteristic =
                service.getCharacteristic(expectedCharacteristicUuid);
        if (characteristic == null) {
            finishConnectionWithFailure(WeightStationConnection.Failure.CHARACTERISTIC_MISSING);
            return;
        }

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CONFIGURATION_UUID);
        if (descriptor == null || !gatt.setCharacteristicNotification(characteristic, true)) {
            finishConnectionWithFailure(WeightStationConnection.Failure.NOTIFICATION_SETUP_FAILED);
            return;
        }

        if (!writeNotificationDescriptor(gatt, descriptor)) {
            finishConnectionWithFailure(WeightStationConnection.Failure.NOTIFICATION_SETUP_FAILED);
        }
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("MissingPermission")
    private boolean writeNotificationDescriptor(BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            ) == BluetoothStatusCodes.SUCCESS;
        }

        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        return gatt.writeDescriptor(descriptor);
    }

    private void handleGattDisconnected(BluetoothGatt gatt) {
        WeightStationConnection.ConnectionEvents events = connectionEvents;
        boolean wasReady = connectionReady;
        closeGatt(gatt);

        if (events == null) {
            return;
        }

        if (wasReady) {
            events.onDisconnected();
        } else {
            events.onConnectionFailed(WeightStationConnection.Failure.CONNECTION_FAILED);
        }
    }

    private void finishConnectionWithFailure(WeightStationConnection.Failure failure) {
        WeightStationConnection.ConnectionEvents events = connectionEvents;
        disconnectGatt();
        if (events != null) {
            events.onConnectionFailed(failure);
        }
    }

    @SuppressLint("MissingPermission")
    private void disconnectGatt() {
        BluetoothGatt gatt = bluetoothGatt;
        bluetoothGatt = null;
        connectionReady = false;

        if (gatt == null) {
            return;
        }

        try {
            gatt.disconnect();
        } catch (SecurityException ignored) {
        }
        safeCloseGatt(gatt);
    }

    private void closeGatt(BluetoothGatt gatt) {
        if (gatt == bluetoothGatt) {
            bluetoothGatt = null;
        }
        connectionReady = false;
        safeCloseGatt(gatt);
    }

    private void safeCloseGatt(BluetoothGatt gatt) {
        try {
            gatt.close();
        } catch (SecurityException ignored) {
        }
    }
}
