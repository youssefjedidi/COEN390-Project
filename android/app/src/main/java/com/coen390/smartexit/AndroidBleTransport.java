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

import java.nio.charset.StandardCharsets;
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
    private UUID expectedCommandCharacteristicUuid;
    private BluetoothGattCharacteristic commandCharacteristic;
    private WeightStationConnection.CommandEvents commandEvents;
    private Runnable commandTimeout;
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
            UUID commandCharacteristicUuid,
            WeightStationConnection.ConnectionMode mode,
            WeightStationConnection.ConnectionEvents events
    ) {
        disconnectGatt();
        connectionEvents = events;
        expectedServiceUuid = serviceUuid;
        expectedCharacteristicUuid = characteristicUuid;
        expectedCommandCharacteristicUuid = commandCharacteristicUuid;
        connectionReady = false;

        try {
            BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(device.address);
            bluetoothGatt = bluetoothDevice.connectGatt(
                    appContext,
                    mode == WeightStationConnection.ConnectionMode.AUTO_RECONNECT,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_1M_MASK,
                    mainHandler
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
    public void writeCommand(
            String command,
            WeightStationConnection.CommandEvents events
    ) {
        BluetoothGatt gatt = bluetoothGatt;
        BluetoothGattCharacteristic characteristic = commandCharacteristic;
        if (!connectionReady || gatt == null || characteristic == null || commandEvents != null) {
            events.onCommandWriteFailed();
            return;
        }

        byte[] value = command.getBytes(StandardCharsets.UTF_8);
        commandEvents = events;

        try {
            if (!startCharacteristicWrite(gatt, characteristic, value)) {
                finishCommandWrite(false);
            }
        } catch (SecurityException | IllegalStateException exception) {
            finishCommandWrite(false);
        }
    }

    @Override
    public void scheduleCommandTimeout(Runnable timeout, long delayMillis) {
        cancelCommandTimeout();
        commandTimeout = timeout;
        mainHandler.postDelayed(timeout, delayMillis);
    }

    @Override
    public void cancelCommandTimeout() {
        if (commandTimeout != null) {
            mainHandler.removeCallbacks(commandTimeout);
            commandTimeout = null;
        }
    }

    @Override
    @SuppressLint("MissingPermission")
    public void disconnect() {
        cancelCommandTimeout();
        connectionEvents = null;
        commandEvents = null;
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
            handleConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            handleServicesDiscovered(gatt, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            handleDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onCharacteristicWrite(
                BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic,
                int status
        ) {
            if (gatt == bluetoothGatt
                    && expectedCommandCharacteristicUuid.equals(characteristic.getUuid())) {
                finishCommandWrite(status == BluetoothGatt.GATT_SUCCESS);
            }
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic,
                byte[] value
        ) {
            handleCharacteristicNotification(gatt, characteristic, value);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onCharacteristicChanged(
                BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleCharacteristicNotification(gatt, characteristic, characteristic.getValue());
            }
        }
    };

    private void handleConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (gatt != bluetoothGatt) {
            return;
        }

        boolean connected = status == BluetoothGatt.GATT_SUCCESS
                && newState == BluetoothProfile.STATE_CONNECTED;
        if (connected) {
            discoverServices(gatt);
            return;
        }

        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            handleGattDisconnected(gatt);
        } else if (status != BluetoothGatt.GATT_SUCCESS) {
            finishConnectionWithFailure(WeightStationConnection.Failure.CONNECTION_FAILED);
        }
    }

    private void handleServicesDiscovered(BluetoothGatt gatt, int status) {
        if (gatt != bluetoothGatt) {
            return;
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            finishConnectionWithFailure(WeightStationConnection.Failure.CONNECTION_FAILED);
            return;
        }

        enableWeightNotifications(gatt);
    }

    private void handleDescriptorWrite(
            BluetoothGatt gatt,
            BluetoothGattDescriptor descriptor,
            int status
    ) {
        if (gatt != bluetoothGatt) {
            return;
        }

        if (!CLIENT_CONFIGURATION_UUID.equals(descriptor.getUuid())) {
            return;
        }

        UUID characteristicUuid = descriptor.getCharacteristic().getUuid();
        if (status != BluetoothGatt.GATT_SUCCESS) {
            if (expectedCommandCharacteristicUuid.equals(characteristicUuid)) {
                commandCharacteristic = null;
                finishNotificationSetup();
            } else {
                finishConnectionWithFailure(
                        WeightStationConnection.Failure.NOTIFICATION_SETUP_FAILED
                );
            }
            return;
        }

        if (expectedCharacteristicUuid.equals(characteristicUuid)) {
            if (commandCharacteristic == null) {
                finishNotificationSetup();
            } else {
                enableCommandNotifications(gatt);
            }
        } else if (expectedCommandCharacteristicUuid.equals(characteristicUuid)) {
            finishNotificationSetup();
        }
    }

    private void handleCharacteristicNotification(
            BluetoothGatt gatt,
            BluetoothGattCharacteristic characteristic,
            byte[] value
    ) {
        if (gatt != bluetoothGatt) {
            return;
        }

        if (value == null) {
            return;
        }

        WeightStationConnection.ConnectionEvents events = connectionEvents;
        if (events == null) {
            return;
        }

        UUID characteristicUuid = characteristic.getUuid();
        if (expectedCharacteristicUuid.equals(characteristicUuid)) {
            events.onPayloadReceived(new String(value, StandardCharsets.UTF_8));
        } else if (expectedCommandCharacteristicUuid.equals(characteristicUuid)) {
            events.onCommandResponse(new String(value, StandardCharsets.UTF_8));
        }
    }

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
        BluetoothGattCharacteristic candidate =
                service.getCharacteristic(expectedCommandCharacteristicUuid);
        if (candidate != null
                && (candidate.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                && (candidate.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                && candidate.getDescriptor(CLIENT_CONFIGURATION_UUID) != null) {
            commandCharacteristic = candidate;
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

    @SuppressLint("MissingPermission")
    private void enableCommandNotifications(BluetoothGatt gatt) {
        BluetoothGattDescriptor descriptor =
                commandCharacteristic.getDescriptor(CLIENT_CONFIGURATION_UUID);
        if (!gatt.setCharacteristicNotification(commandCharacteristic, true)
                || !writeNotificationDescriptor(gatt, descriptor)) {
            commandCharacteristic = null;
            finishNotificationSetup();
        }
    }

    private void finishNotificationSetup() {
        connectionReady = true;
        WeightStationConnection.ConnectionEvents events = connectionEvents;
        if (events != null) {
            events.onReady(commandCharacteristic != null);
        }
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("MissingPermission")
    private boolean startCharacteristicWrite(
            BluetoothGatt gatt,
            BluetoothGattCharacteristic characteristic,
            byte[] value
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return gatt.writeCharacteristic(
                    characteristic,
                    value,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS;
        }

        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        characteristic.setValue(value);
        return gatt.writeCharacteristic(characteristic);
    }

    private void finishCommandWrite(boolean succeeded) {
        WeightStationConnection.CommandEvents events = commandEvents;
        commandEvents = null;
        if (events == null) {
            return;
        }

        if (succeeded) {
            events.onCommandWritten();
        } else {
            events.onCommandWriteFailed();
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
        commandCharacteristic = null;
        commandEvents = null;
        cancelCommandTimeout();

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
        commandCharacteristic = null;
        commandEvents = null;
        cancelCommandTimeout();
        safeCloseGatt(gatt);
    }

    private void safeCloseGatt(BluetoothGatt gatt) {
        try {
            gatt.close();
        } catch (SecurityException ignored) {
        }
    }
}
