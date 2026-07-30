package com.coen390.smartexit;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private final DisconnectEventCoordinator disconnectEventCoordinator =
            new DisconnectEventCoordinator();
    private final DisconnectSnapshotRepository disconnectSnapshotRepository;
    private final DisconnectNotifier disconnectNotifier;
    private WeightStationConnection connection;
    private DisconnectSnapshot latestDashboardSnapshot;

    private StationConnectionManager(Context appContext) {
        this.appContext = appContext;
        disconnectSnapshotRepository = new DisconnectSnapshotRepository(appContext);
        disconnectNotifier = new DisconnectNotifier(appContext);
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

    boolean canRequestTare() {
        return connection != null && connection.canRequestTare();
    }

    void requestTare(WeightStationConnection.CommandCallback callback) {
        if (connection == null) {
            callback.onCommandFailed(WeightStationConnection.CommandFailure.NOT_CONNECTED);
            return;
        }
        connection.requestTare(callback);
    }

    /** Fully tears down the connection, e.g. when permissions/BLE support are lost. */
    void reset() {
        if (connection != null) {
            saveDisconnectSnapshot(WeightStationConnection.State.DISCONNECTED);
            connection.close();
            connection = null;
        }
    }

    synchronized void recordDashboardStates(
            List<TrackedItemState> states,
            long timestampMillis
    ) {
        latestDashboardSnapshot = DisconnectSnapshot.from(timestampMillis, states);
    }

    synchronized DisconnectSnapshot getLatestDashboardSnapshot() {
        return latestDashboardSnapshot;
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
        saveDisconnectSnapshot(state);
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

    private void saveDisconnectSnapshot(WeightStationConnection.State state) {
        Optional<DisconnectSnapshot> snapshot;
        synchronized (this) {
            snapshot = disconnectEventCoordinator.onStateChanged(
                    state,
                    latestDashboardSnapshot
            );
            if (snapshot.isPresent()) {
                latestDashboardSnapshot = null;
            }
        }

        if (snapshot.isPresent()) {
            disconnectSnapshotRepository.save(snapshot.get());
            disconnectNotifier.show(snapshot.get());
        }
    }
}
