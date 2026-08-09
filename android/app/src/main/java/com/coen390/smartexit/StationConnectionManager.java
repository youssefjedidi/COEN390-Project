package com.coen390.smartexit;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class StationConnectionManager implements WeightStationConnection.Listener {

    interface DashboardListener {
        void onDashboardUpdated(DisconnectSnapshot snapshot);
    }

    private static final int PLATE_COUNT = 4;
    private static final int REQUIRED_STABLE_SAMPLES = 3;
    private static final double STABILITY_TOLERANCE_GRAMS = 5.0;
    private static final double EMPTY_WEIGHT_THRESHOLD_GRAMS = 5.0;

    private static StationConnectionManager instance;

    static synchronized StationConnectionManager getInstance(Context context) {
        if (instance == null) {
            instance = new StationConnectionManager(context.getApplicationContext());
        }
        return instance;
    }

    private final Context appContext;
    private final List<WeightStationConnection.Listener> listeners =
            new CopyOnWriteArrayList<>();
    private final List<DashboardListener> dashboardListeners =
            new CopyOnWriteArrayList<>();
    private final DisconnectSnapshotRepository disconnectSnapshotRepository;
    private final StationReadingProcessor readingProcessor;
    private final StationMonitoringPreferences monitoringPreferences;
    private WeightStationConnection connection;
    private DisconnectSnapshot latestDashboardSnapshot;

    private StationConnectionManager(Context appContext) {
        this.appContext = appContext;
        disconnectSnapshotRepository = new DisconnectSnapshotRepository(appContext);
        monitoringPreferences = new StationMonitoringPreferences(appContext);
        readingProcessor = new StationReadingProcessor(
                new ItemProfileRepository(appContext).getAll(),
                PLATE_COUNT,
                REQUIRED_STABLE_SAMPLES,
                STABILITY_TOLERANCE_GRAMS,
                EMPTY_WEIGHT_THRESHOLD_GRAMS
        );
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
        if (conn == null) {
            return;
        }

        String knownAddress = monitoringPreferences.getKnownStationAddress();
        if (knownAddress == null) {
            conn.connect();
        } else {
            conn.connectKnown(knownAddress);
        }
    }

    void disconnect() {
        monitoringPreferences.setMonitoringEnabled(false);
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
            connection.close();
            connection = null;
        }
    }

    void refreshProfiles(List<ItemProfile> profiles) {
        readingProcessor.replaceProfiles(profiles);
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

    void addDashboardListener(DashboardListener listener) {
        dashboardListeners.add(listener);
    }

    void removeDashboardListener(DashboardListener listener) {
        dashboardListeners.remove(listener);
    }

    List<RecognitionResult> getPendingAmbiguousResults() {
        return readingProcessor.getPendingAmbiguousResults();
    }

    void confirmAmbiguousMatch(int plateNumber, String itemId) {
        publishDashboardSnapshot(
                readingProcessor.confirmAmbiguousMatch(
                        plateNumber,
                        itemId,
                        System.currentTimeMillis()
                )
        );
    }

    void leaveAmbiguousMatchUnresolved(int plateNumber) {
        publishDashboardSnapshot(
                readingProcessor.leaveAmbiguousMatchUnresolved(
                        plateNumber,
                        System.currentTimeMillis()
                )
        );
    }

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager bluetoothManager =
                (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        return bluetoothManager == null ? null : bluetoothManager.getAdapter();
    }

    @Override
    public void onStateChanged(WeightStationConnection.State state, WeightStationConnection.Failure failure) {
        if (state == WeightStationConnection.State.CONNECTED) {
            String address = connection == null ? null : connection.getConnectedStationAddress();
            if (address != null) {
                monitoringPreferences.rememberStation(address);
                monitoringPreferences.setMonitoringEnabled(true);
            }
        }
        for (WeightStationConnection.Listener listener : listeners) {
            listener.onStateChanged(state, failure);
        }

        if (state == WeightStationConnection.State.DISCONNECTED
                && monitoringPreferences.isMonitoringEnabled()) {
            connect();
        }
    }

    @Override
    public void onReadingReceived(BluetoothReading reading) {
        readingProcessor
                .process(reading, System.currentTimeMillis())
                .ifPresent(this::publishDashboardSnapshot);
        for (WeightStationConnection.Listener listener : listeners) {
            listener.onReadingReceived(reading);
        }
    }

    @Override
    public void onInvalidPayload() {
        for (WeightStationConnection.Listener listener : listeners) {
            listener.onInvalidPayload();
        }
    }

    private void publishDashboardSnapshot(DisconnectSnapshot snapshot) {
        synchronized (this) {
            latestDashboardSnapshot = snapshot;
        }
        disconnectSnapshotRepository.save(snapshot);
        for (DashboardListener listener : dashboardListeners) {
            listener.onDashboardUpdated(snapshot);
        }
    }
}
