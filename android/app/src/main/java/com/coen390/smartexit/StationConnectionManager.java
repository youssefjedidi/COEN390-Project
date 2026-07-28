package com.coen390.smartexit;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

final class StationConnectionManager implements WeightStationConnection.Listener {

    private static StationConnectionManager instance;

    static synchronized StationConnectionManager getInstance(Context context) {
        if (instance == null) {
            instance = new StationConnectionManager(context.getApplicationContext());
        }
        return instance;
    }

    private final Context appContext;
    private final List<WeightStationConnection.Listener> listeners = new ArrayList<>();
    private WeightStationConnection connection;

    private StationConnectionManager(Context appContext) {
        this.appContext = appContext;
    }

    /**
     * Returns the shared connection, creating it if needed. Returns null if
     * a BluetoothAdapter isn't available on this device (BLE unsupported).
     */
    WeightStationConnection getOrCreateConnection() {
        if (connection == null) {
            BluetoothAdapter adapter = getBluetoothAdapter();
            if (adapter == null) {
                return null;
            }
            WeightStationConnection.Transport transport =
                    new AndroidBleTransport(appContext, adapter);
            connection = new WeightStationConnection(transport, this);
        }
        return connection;
    }

    WeightStationConnection.State getState() {
        return connection == null ? WeightStationConnection.State.IDLE : connection.getState();
    }

    WeightStationConnection.Failure getFailure() {
        return connection == null ? null : connection.getFailure();
    }

    void connect() {
        WeightStationConnection conn = getOrCreateConnection();
        if (conn != null) {
            conn.connect();
        }
    }

    void disconnect() {
        if (connection != null) {
            connection.disconnect();
        }
    }

    /** Fully tears down the connection, e.g. when permissions/BLE support are lost. */
    void reset() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    void addListener(WeightStationConnection.Listener listener) {
        listeners.add(listener);
    }

    void removeListener(WeightStationConnection.Listener listener) {
        listeners.remove(listener);
    }

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager bluetoothManager =
                (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        return bluetoothManager == null ? null : bluetoothManager.getAdapter();
    }

    @Override
    public void onStateChanged(WeightStationConnection.State state, WeightStationConnection.Failure failure) {
        for (WeightStationConnection.Listener listener : new ArrayList<>(listeners)) {
            listener.onStateChanged(state, failure);
        }
    }

    @Override
    public void onReadingReceived(BluetoothReading reading) {
        for (WeightStationConnection.Listener listener : new ArrayList<>(listeners)) {
            listener.onReadingReceived(reading);
        }
    }

    @Override
    public void onInvalidPayload() {
        for (WeightStationConnection.Listener listener : new ArrayList<>(listeners)) {
            listener.onInvalidPayload();
        }
    }
}
