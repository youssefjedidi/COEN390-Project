package com.coen390.smartexit;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class StationConnectionManager implements WeightStationConnection.Listener {

    interface DashboardListener {
        void onDashboardUpdated(DisconnectSnapshot snapshot);
    }

    interface MonitoringListener {
        void onMonitoringStateChanged(
                MonitoringLifecycle.State state,
                MonitoringLifecycle.PauseReason pauseReason
        );
    }

    private static final int PLATE_COUNT = 4;
    private static final int REQUIRED_STABLE_SAMPLES = 3;
    private static final double STABILITY_TOLERANCE_GRAMS = 5.0;
    private static final double EMPTY_WEIGHT_THRESHOLD_GRAMS = 5.0;
    private static final long DEPARTURE_GRACE_PERIOD_MS = 10_000L;

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
    private final List<MonitoringListener> monitoringListeners =
            new CopyOnWriteArrayList<>();
    private final DisconnectSnapshotRepository disconnectSnapshotRepository;
    private final StationReadingProcessor readingProcessor;
    private final StationMonitoringPreferences monitoringPreferences;
    private final MonitoringLifecycle monitoringLifecycle = new MonitoringLifecycle();
    private final DepartureReminderCoordinator departureReminder;
    private WeightStationConnection connection;
    private DisconnectSnapshot latestDashboardSnapshot;

    private StationConnectionManager(Context appContext) {
        this.appContext = appContext;
        disconnectSnapshotRepository = new DisconnectSnapshotRepository(appContext);
        monitoringPreferences = new StationMonitoringPreferences(appContext);
        DisconnectNotifier disconnectNotifier = new DisconnectNotifier(appContext);
        departureReminder = new DepartureReminderCoordinator(
                DEPARTURE_GRACE_PERIOD_MS,
                new HandlerScheduler(new Handler(Looper.getMainLooper())),
                disconnectSnapshotRepository::save,
                disconnectNotifier::show
        );
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
    private WeightStationConnection getOrCreateConnection() {
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

    void startMonitoring() {
        monitoringPreferences.setMonitoringEnabled(true);
        if (getState() == WeightStationConnection.State.CONNECTED) {
            monitoringLifecycle.connected();
            notifyMonitoringListeners();
            return;
        }
        clearLiveDashboardSnapshot();
        monitoringLifecycle.start();
        notifyMonitoringListeners();
        connectToStation();
    }

    private void connectToStation() {
        WeightStationConnection stationConnection = getOrCreateConnection();
        if (stationConnection == null) {
            monitoringLifecycle.pause(MonitoringLifecycle.PauseReason.CONNECTION_UNAVAILABLE);
            notifyMonitoringListeners();
            return;
        }

        String knownAddress = monitoringPreferences.getKnownStationAddress();
        if (knownAddress == null) {
            stationConnection.connect();
        } else {
            stationConnection.connectKnown(knownAddress);
        }
    }

    void stopMonitoring() {
        monitoringPreferences.setMonitoringEnabled(false);
        clearLiveDashboardSnapshot();
        monitoringLifecycle.disconnect(MonitoringLifecycle.DisconnectCause.MANUAL_STOP);
        departureReminder.cancelDeparture();
        notifyMonitoringListeners();
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

    void requestCalibrationTare(WeightStationConnection.CommandCallback callback) {
        if (connection == null) {
            callback.onCommandFailed(WeightStationConnection.CommandFailure.NOT_CONNECTED);
            return;
        }
        connection.requestCalibrationTare(callback);
    }

    boolean canRequestPlateCalibration() {
        return connection != null && connection.canRequestPlateCalibration();
    }

    void requestPlateCalibration(
            int plateNumber,
            double referenceMassGrams,
            WeightStationConnection.CommandCallback callback
    ) {
        if (connection == null) {
            callback.onCommandFailed(WeightStationConnection.CommandFailure.NOT_CONNECTED);
            return;
        }
        connection.requestPlateCalibration(plateNumber, referenceMassGrams, callback);
    }

    /** Fully tears down the connection, e.g. when permissions/BLE support are lost. */
    void pauseMonitoring(MonitoringLifecycle.PauseReason reason) {
        if (monitoringLifecycle.getState() == MonitoringLifecycle.State.PAUSED
                && monitoringLifecycle.getPauseReason() == reason) {
            return;
        }
        clearLiveDashboardSnapshot();
        monitoringLifecycle.pause(reason);
        departureReminder.cancelDeparture();
        if (connection != null) {
            connection.close();
            connection = null;
        }
        notifyMonitoringListeners();
    }

    MonitoringLifecycle.State getMonitoringState() {
        return monitoringLifecycle.getState();
    }

    MonitoringLifecycle.PauseReason getMonitoringPauseReason() {
        return monitoringLifecycle.getPauseReason();
    }

    boolean isMonitoringEnabled() {
        return monitoringPreferences.isMonitoringEnabled();
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

    void addMonitoringListener(MonitoringListener listener) {
        monitoringListeners.add(listener);
    }

    void removeMonitoringListener(MonitoringListener listener) {
        monitoringListeners.remove(listener);
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
    public void onStateChanged(
            WeightStationConnection.State state,
            WeightStationConnection.Failure failure
    ) {
        if (state == WeightStationConnection.State.CONNECTED) {
            handleStationConnected();
        }
        notifyConnectionListeners(state, failure);

        if (state == WeightStationConnection.State.DISCONNECTED) {
            handleStationDisconnected();
        } else if (state == WeightStationConnection.State.FAILED) {
            handleConnectionFailure();
        }
        notifyMonitoringListeners();
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
        departureReminder.onFreshSnapshot(snapshot);
        for (DashboardListener listener : dashboardListeners) {
            listener.onDashboardUpdated(snapshot);
        }
    }

    private synchronized void clearLiveDashboardSnapshot() {
        latestDashboardSnapshot = null;
    }

    private void handleStationConnected() {
        readingProcessor.resetCycle();
        clearLiveDashboardSnapshot();

        String stationAddress = connection == null
                ? null
                : connection.getConnectedStationAddress();
        if (stationAddress != null) {
            monitoringPreferences.rememberStation(stationAddress);
            monitoringPreferences.setMonitoringEnabled(true);
        }

        monitoringLifecycle.connected();
        departureReminder.onReconnected();
    }

    private void handleStationDisconnected() {
        if (!monitoringPreferences.isMonitoringEnabled()) {
            return;
        }

        clearLiveDashboardSnapshot();
        MonitoringLifecycle.DisconnectCause cause = getDisconnectCause();
        if (monitoringLifecycle.disconnect(cause)) {
            departureReminder.onLinkLost();
        }

        if (cause == MonitoringLifecycle.DisconnectCause.LINK_LOSS) {
            connectToStation();
        } else {
            departureReminder.cancelDeparture();
        }
    }

    private void handleConnectionFailure() {
        if (!monitoringPreferences.isMonitoringEnabled()) {
            return;
        }

        clearLiveDashboardSnapshot();
        monitoringLifecycle.pause(getPauseReasonForCurrentAvailability());
        departureReminder.cancelDeparture();
    }

    private void notifyConnectionListeners(
            WeightStationConnection.State state,
            WeightStationConnection.Failure failure
    ) {
        for (WeightStationConnection.Listener listener : listeners) {
            listener.onStateChanged(state, failure);
        }
    }

    private void notifyMonitoringListeners() {
        MonitoringLifecycle.State state = monitoringLifecycle.getState();
        MonitoringLifecycle.PauseReason reason = monitoringLifecycle.getPauseReason();
        for (MonitoringListener listener : monitoringListeners) {
            listener.onMonitoringStateChanged(state, reason);
        }
    }

    @SuppressLint("MissingPermission")
    private MonitoringLifecycle.DisconnectCause getDisconnectCause() {
        if (!BluetoothPermissionHelper.hasRequiredPermissions(appContext)) {
            return MonitoringLifecycle.DisconnectCause.PERMISSION_UNAVAILABLE;
        }
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return MonitoringLifecycle.DisconnectCause.BLUETOOTH_OFF;
        }
        return MonitoringLifecycle.DisconnectCause.LINK_LOSS;
    }

    @SuppressLint("MissingPermission")
    private MonitoringLifecycle.PauseReason getPauseReasonForCurrentAvailability() {
        if (!BluetoothPermissionHelper.hasRequiredPermissions(appContext)) {
            return MonitoringLifecycle.PauseReason.PERMISSION_UNAVAILABLE;
        }
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return MonitoringLifecycle.PauseReason.BLUETOOTH_OFF;
        }
        return MonitoringLifecycle.PauseReason.CONNECTION_UNAVAILABLE;
    }

    private static final class HandlerScheduler
            implements DepartureReminderCoordinator.Scheduler {
        private final Handler handler;
        private Runnable pendingTask;

        private HandlerScheduler(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void schedule(Runnable task, long delayMillis) {
            cancel();
            pendingTask = task;
            handler.postDelayed(task, delayMillis);
        }

        @Override
        public void cancel() {
            if (pendingTask != null) {
                handler.removeCallbacks(pendingTask);
                pendingTask = null;
            }
        }
    }
}
