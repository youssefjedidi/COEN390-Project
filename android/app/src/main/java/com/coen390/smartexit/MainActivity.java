package com.coen390.smartexit;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    static final String ACTION_SHOW_DISCONNECT_SNAPSHOT =
            "com.coen390.smartexit.SHOW_DISCONNECT_SNAPSHOT";

    private static final int BLUETOOTH_PERMISSION_REQUEST = 1001;
    private static final int MAX_DASHBOARD_ITEMS = 4;
    private static final int REQUIRED_STABLE_SAMPLES = 3;
    private static final double STABILITY_TOLERANCE_GRAMS = 5.0;
    private static final double EMPTY_WEIGHT_THRESHOLD_GRAMS = 5.0;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    private TextView stationStatus;
    private TextView connectionDetail;
    private View connectionProgress;
    private Button bluetoothActionButton;
    private View addItemButton;
    private View dashboardEmptyState;
    private View dashboardItemGrid;
    private View dashboardGridSecondRow;
    private TextView dashboardDataStatus;
    private View[] itemRows;
    private TextView[] itemNames;
    private TextView[] itemStatuses;
    private TextView[] itemDetails;
    private DashboardStateCoordinator dashboardStateCoordinator;
    private boolean ambiguousDialogVisible;
    private ItemProfileRepository itemProfileRepository;
    private DisconnectSnapshotRepository disconnectSnapshotRepository;
    private List<ItemProfile> visibleProfiles = new ArrayList<>();
    private StationConnectionManager connectionManager;
    private WeightStationConnection.Listener connectionListener;
    private boolean showDisconnectSnapshot;

    static Intent newIntentForDisconnectSnapshot(Context context) {
        return new Intent(context, MainActivity.class)
                .setAction(ACTION_SHOW_DISCONNECT_SNAPSHOT);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        showDisconnectSnapshot = isDisconnectSnapshotIntent(getIntent());

        stationStatus = findViewById(R.id.stationStatus);
        connectionDetail = findViewById(R.id.connectionDetail);
        connectionProgress = findViewById(R.id.connectionProgress);
        bluetoothActionButton = findViewById(R.id.bluetoothActionButton);

        itemProfileRepository = new ItemProfileRepository(this);
        disconnectSnapshotRepository = new DisconnectSnapshotRepository(this);
        bindDashboardViews();
        bluetoothActionButton.setOnClickListener(view -> handleBluetoothAction());

        connectionManager = StationConnectionManager.getInstance(this);
        connectionListener = new WeightStationConnection.Listener() {
            @Override
            public void onStateChanged(
                    WeightStationConnection.State state,
                    WeightStationConnection.Failure failure
            ) {
                runOnUiThread(() -> handleConnectionState(state, failure));
            }

            @Override
            public void onReadingReceived(BluetoothReading reading) {
                showBluetoothReading(reading);
            }

            @Override
            public void onInvalidPayload() {
                // A malformed packet is ignored. The next valid four-plate cycle updates the dashboard.
            }
        };
        connectionManager.addListener(connectionListener);

        addItemButton = findViewById(R.id.addItemButton);
        addItemButton.setOnClickListener(
                view -> startActivity(AddEditItemActivity.newIntentForAdd(this))
        );

        findViewById(R.id.settingsButton).setOnClickListener(
                view -> startActivity(new Intent(this, SettingsActivity.class))
        );
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showDisconnectSnapshot = isDisconnectSnapshotIntent(intent);
        showSavedItems();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showSavedItems();
        updateBluetoothReadiness();
        NotificationPermissionHelper.requestIfNeeded(this, !visibleProfiles.isEmpty());
    }

    @Override
    protected void onDestroy() {
        connectionManager.removeListener(connectionListener);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == BLUETOOTH_PERMISSION_REQUEST) {
            updateBluetoothReadiness();
        }
    }

    private void handleBluetoothAction() {
        if (!BluetoothPermissionHelper.hasRequiredPermissions(this)) {
            BluetoothPermissionHelper.recordPermissionRequest(this);
            requestPermissions(
                    BluetoothPermissionHelper.requiredPermissions(),
                    BLUETOOTH_PERMISSION_REQUEST
            );
            return;
        }

        BluetoothAdapter bluetoothAdapter = getBluetoothAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            return;
        }

        WeightStationConnection connection = connectionManager.getOrCreateConnection();
        if (connection == null) {
            return;
        }
        WeightStationConnection.State state = connection.getState();

        if (state == WeightStationConnection.State.SCANNING
                || state == WeightStationConnection.State.CONNECTING
                || state == WeightStationConnection.State.CONNECTED) {
            connectionManager.disconnect();
        } else {
            connectionManager.connect();
        }
    }

    private void updateBluetoothReadiness() {
        if (!BluetoothPermissionHelper.supportsBle(this)) {
            closeStationConnection();
            showBluetoothSetupState(
                    R.string.station_ble_unsupported,
                    R.string.reading_detail_ble_unsupported,
                    0
            );
            return;
        }

        if (!BluetoothPermissionHelper.hasRequiredPermissions(this)) {
            closeStationConnection();
            if (BluetoothPermissionHelper.wasPermissionRequested(this)) {
                showBluetoothSetupState(
                        R.string.station_permission_denied,
                        R.string.reading_detail_permission_denied,
                        R.string.try_bluetooth_access_again
                );
            } else {
                showBluetoothSetupState(
                        R.string.station_permission_required,
                        R.string.reading_detail_permission_required,
                        R.string.allow_bluetooth_access
                );
            }
            return;
        }

        BluetoothAdapter bluetoothAdapter = getBluetoothAdapter();

        if (bluetoothAdapter == null) {
            closeStationConnection();
            showBluetoothSetupState(
                    R.string.station_ble_unsupported,
                    R.string.reading_detail_ble_unsupported,
                    0
            );
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            closeStationConnection();
            showBluetoothSetupState(
                    R.string.station_bluetooth_off,
                    R.string.reading_detail_bluetooth_off,
                    R.string.open_bluetooth_settings
            );
            return;
        }

        WeightStationConnection connection = connectionManager.getOrCreateConnection();
        if (connection == null) {
            showBluetoothReadyState();
            return;
        }
        WeightStationConnection.State connectionState = connection.getState();
        if (connectionState == WeightStationConnection.State.IDLE) {
            showBluetoothReadyState();
        } else {
            handleConnectionState(connectionState, connection.getFailure());
        }
    }

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        return bluetoothManager == null ? null : bluetoothManager.getAdapter();
    }

    private void closeStationConnection() {
        connectionManager.reset();
    }

    private void showBluetoothSetupState(int stationText, int detailText, int buttonText) {
        showIdleConnectionPresentation();
        stationStatus.setText(stationText);
        stationStatus.setBackgroundResource(R.drawable.status_offline_background);
        stationStatus.setTextColor(getColor(R.color.status_offline_text));
        connectionDetail.setText(detailText);

        if (buttonText == 0) {
            bluetoothActionButton.setVisibility(View.GONE);
            return;
        }

        bluetoothActionButton.setText(buttonText);
        bluetoothActionButton.setVisibility(View.VISIBLE);
    }

    private void showBluetoothReadyState() {
        showIdleConnectionPresentation();
        bluetoothActionButton.setText(R.string.connect_to_station);
        bluetoothActionButton.setVisibility(View.VISIBLE);
        stationStatus.setText(R.string.station_disconnected);
        stationStatus.setBackgroundResource(R.drawable.status_neutral_background);
        stationStatus.setTextColor(getColor(R.color.status_neutral_text));
        connectionDetail.setText(R.string.reading_detail_bluetooth_ready);
    }

    private void renderConnectionState(
            WeightStationConnection.State state,
            WeightStationConnection.Failure failure
    ) {
        if (state == WeightStationConnection.State.SCANNING) {
            showPendingConnectionState(R.string.station_scanning);
            return;
        }

        if (state == WeightStationConnection.State.CONNECTING) {
            showPendingConnectionState(
                    R.string.station_connecting,
                    R.string.reading_detail_station_connecting
            );
            return;
        }

        if (state == WeightStationConnection.State.CONNECTED) {
            showConnectionState(
                    R.string.station_connected,
                    R.string.reading_detail_station_connected,
                    R.string.disconnect_station,
                    R.drawable.status_connected_background,
                    R.color.status_connected_text
            );
            connectionDetail.setVisibility(View.GONE);
            showSecondaryBluetoothAction();
            return;
        }

        if (state == WeightStationConnection.State.FAILED) {
            showConnectionState(
                    R.string.station_connection_failed,
                    connectionFailureText(failure),
                    R.string.try_connection_again,
                    R.drawable.status_offline_background,
                    R.color.status_offline_text
            );
            return;
        }

        if (state == WeightStationConnection.State.DISCONNECTED) {
            showConnectionState(
                    R.string.station_disconnected,
                    R.string.reading_detail_station_disconnected,
                    R.string.connect_to_station,
                    R.drawable.status_neutral_background,
                    R.color.status_neutral_text
            );
            return;
        }

        showBluetoothReadyState();
    }

    private void showConnectionState(
            int stationText,
            int detailText,
            int buttonText,
            int background,
            int textColor
    ) {
        showIdleConnectionPresentation();
        stationStatus.setText(stationText);
        stationStatus.setBackgroundResource(background);
        stationStatus.setTextColor(getColor(textColor));
        connectionDetail.setText(detailText);
        bluetoothActionButton.setText(buttonText);
        bluetoothActionButton.setVisibility(View.VISIBLE);
    }

    private void showPendingConnectionState(int stationText, int detailText) {
        showConnectionState(
                stationText,
                detailText,
                R.string.cancel_connection,
                R.drawable.status_waiting_background,
                R.color.status_waiting_text
        );
        connectionProgress.setVisibility(View.VISIBLE);
        showSecondaryBluetoothAction();
    }

    private void showPendingConnectionState(int stationText) {
        showPendingConnectionState(stationText, R.string.reading_detail_waiting);
        connectionDetail.setVisibility(View.GONE);
    }

    private void showIdleConnectionPresentation() {
        connectionProgress.setVisibility(View.GONE);
        connectionDetail.setVisibility(View.VISIBLE);
        bluetoothActionButton.setBackgroundResource(R.drawable.primary_button_background);
        bluetoothActionButton.setTextColor(getColor(R.color.button_primary_text));
    }

    private void showSecondaryBluetoothAction() {
        bluetoothActionButton.setBackgroundResource(R.drawable.secondary_button_background);
        bluetoothActionButton.setTextColor(getColor(R.color.button_secondary_text));
    }

    private int connectionFailureText(WeightStationConnection.Failure failure) {
        if (failure == WeightStationConnection.Failure.STATION_NOT_FOUND) {
            return R.string.reading_detail_station_not_found;
        }
        if (failure == WeightStationConnection.Failure.SERVICE_MISSING) {
            return R.string.reading_detail_service_missing;
        }
        if (failure == WeightStationConnection.Failure.CHARACTERISTIC_MISSING) {
            return R.string.reading_detail_characteristic_missing;
        }
        if (failure == WeightStationConnection.Failure.NOTIFICATION_SETUP_FAILED) {
            return R.string.reading_detail_notification_setup_failed;
        }
        if (failure == WeightStationConnection.Failure.SCAN_UNAVAILABLE) {
            return R.string.reading_detail_scan_unavailable;
        }
        return R.string.reading_detail_connection_failed;
    }

    private void bindDashboardViews() {
        dashboardEmptyState = findViewById(R.id.dashboardEmptyState);
        dashboardItemGrid = findViewById(R.id.dashboardItemGrid);
        dashboardGridSecondRow = findViewById(R.id.dashboardGridSecondRow);
        dashboardDataStatus = findViewById(R.id.dashboardDataStatus);

        itemRows = new View[] {
                findViewById(R.id.itemRow1),
                findViewById(R.id.itemRow2),
                findViewById(R.id.itemRow3),
                findViewById(R.id.itemRow4)
        };
        itemNames = new TextView[MAX_DASHBOARD_ITEMS];
        itemStatuses = new TextView[MAX_DASHBOARD_ITEMS];
        itemDetails = new TextView[MAX_DASHBOARD_ITEMS];

        for (int i = 0; i < MAX_DASHBOARD_ITEMS; i++) {
            int index = i;
            itemNames[index] = itemRows[index].findViewById(R.id.itemName);
            itemStatuses[index] = itemRows[index].findViewById(R.id.itemStatus);
            itemDetails[index] = itemRows[index].findViewById(R.id.itemDetail);
            itemRows[index].setOnClickListener(v -> openItemDetails(index));
        }
    }

    private void showSavedItems() {
        List<ItemProfile> savedProfiles = itemProfileRepository.getAll();
        int visibleCount = Math.min(savedProfiles.size(), MAX_DASHBOARD_ITEMS);
        List<ItemProfile> nextVisibleProfiles =
                new ArrayList<>(savedProfiles.subList(0, visibleCount));
        visibleProfiles = nextVisibleProfiles;

        addItemButton.setVisibility(
                visibleCount == MAX_DASHBOARD_ITEMS ? View.GONE : View.VISIBLE
        );

        dashboardStateCoordinator = new DashboardStateCoordinator(
                visibleProfiles,
                MAX_DASHBOARD_ITEMS,
                REQUIRED_STABLE_SAMPLES,
                STABILITY_TOLERANCE_GRAMS,
                EMPTY_WEIGHT_THRESHOLD_GRAMS
        );

        DisconnectSnapshot liveSnapshot = connectionManager.getLatestDashboardSnapshot();
        DisconnectSnapshot cachedSnapshot = disconnectSnapshotRepository.load();
        DisconnectSnapshot selectedSnapshot = DashboardSnapshotSelector.select(
                liveSnapshot,
                cachedSnapshot,
                showDisconnectSnapshot
        );
        if (selectedSnapshot == null) {
            showWaitingDashboard();
            return;
        }

        boolean cachedSnapshotSelected = selectedSnapshot == cachedSnapshot;
        renderDashboard(selectedSnapshot.restore(visibleProfiles), cachedSnapshotSelected);
        if (cachedSnapshotSelected) {
            showCachedDashboardTime(selectedSnapshot.getTimestampMillis());
        } else {
            showLiveDashboardTime(selectedSnapshot.getTimestampMillis());
        }
    }

    private void showWaitingDashboard() {
        renderDashboard(dashboardStateCoordinator.getStates(), false);
        dashboardDataStatus.setText(R.string.dashboard_data_waiting);
    }

    private void renderDashboard(List<TrackedItemState> states, boolean cachedSnapshot) {
        int visibleCount = Math.min(states.size(), MAX_DASHBOARD_ITEMS);
        dashboardEmptyState.setVisibility(visibleCount == 0 ? View.VISIBLE : View.GONE);
        dashboardItemGrid.setVisibility(visibleCount == 0 ? View.GONE : View.VISIBLE);
        dashboardGridSecondRow.setVisibility(
                visibleCount > 2 ? View.VISIBLE : View.GONE
        );

        for (int index = 0; index < MAX_DASHBOARD_ITEMS; index++) {
            if (index >= visibleCount) {
                itemRows[index].setVisibility(View.INVISIBLE);
                continue;
            }

            itemRows[index].setVisibility(View.VISIBLE);
            renderDashboardItem(index, states.get(index), cachedSnapshot);
        }
    }

    private void openItemDetails(int index) {
        if (index < visibleProfiles.size()) {
            ItemProfile profile = visibleProfiles.get(index);
            startActivity(AddEditItemActivity.newIntentForEdit(this, profile.getId()));
        }
    }

    private void renderDashboardItem(
            int index,
            TrackedItemState state,
            boolean cachedSnapshot
    ) {
        DashboardItemDisplayState displayState =
                DashboardItemDisplayState.from(state.getStatus(), cachedSnapshot);
        itemNames[index].setText(state.getItem().getName());
        itemRows[index].setContentDescription(
                getString(R.string.edit_item_accessibility, state.getItem().getName())
        );
        itemStatuses[index].setText(itemStatusText(displayState));
        String detailText = itemDetailText(state, displayState);
        itemDetails[index].setVisibility(detailText == null ? View.INVISIBLE : View.VISIBLE);
        if (detailText != null) {
            itemDetails[index].setText(detailText);
        }

        if (displayState == DashboardItemDisplayState.PRESENT) {
            styleItemStatus(
                    itemStatuses[index],
                    R.drawable.status_connected_background,
                    R.color.status_connected_text
            );
        } else if (displayState == DashboardItemDisplayState.MISSING) {
            styleItemStatus(
                    itemStatuses[index],
                    R.drawable.status_missing_background,
                    R.color.status_missing_text
            );
        } else if (displayState == DashboardItemDisplayState.WAS_ON_TRAY) {
            styleItemStatus(
                    itemStatuses[index],
                    R.drawable.status_offline_background,
                    R.color.status_offline_text
            );
        } else if (displayState == DashboardItemDisplayState.WAS_NOT_ON_TRAY) {
            styleItemStatus(
                    itemStatuses[index],
                    R.drawable.status_waiting_background,
                    R.color.status_waiting_text
            );
        } else if (displayState == DashboardItemDisplayState.WAS_UNKNOWN) {
            styleItemStatus(
                    itemStatuses[index],
                    R.drawable.status_neutral_background,
                    R.color.status_neutral_text
            );
        } else {
            styleItemStatus(
                    itemStatuses[index],
                    R.drawable.status_waiting_background,
                    R.color.status_waiting_text
            );
        }
    }

    private int itemStatusText(DashboardItemDisplayState status) {
        if (status == DashboardItemDisplayState.PRESENT) {
            return R.string.item_status_present;
        }
        if (status == DashboardItemDisplayState.MISSING) {
            return R.string.item_status_missing;
        }
        if (status == DashboardItemDisplayState.WAS_ON_TRAY) {
            return R.string.item_status_was_on_tray;
        }
        if (status == DashboardItemDisplayState.WAS_NOT_ON_TRAY) {
            return R.string.item_status_was_not_on_tray;
        }
        if (status == DashboardItemDisplayState.WAS_UNKNOWN) {
            return R.string.item_status_was_unknown;
        }
        return R.string.item_status_unknown;
    }

    private String itemDetailText(
            TrackedItemState state,
            DashboardItemDisplayState displayState
    ) {
        if (state.getStatus() == TrackedItemStatus.PRESENT
                && state.getPlateNumber() != null) {
            return getString(R.string.item_detail_plate, state.getPlateNumber());
        }
        if (state.getStatus() == TrackedItemStatus.MISSING) {
            return null;
        }
        if (!state.getItem().isCalibrated()) {
            return getString(R.string.item_detail_calibration_required);
        }
        if (displayState == DashboardItemDisplayState.WAS_UNKNOWN) {
            return getString(R.string.item_detail_was_unknown);
        }
        return getString(R.string.item_detail_waiting);
    }

    private void styleItemStatus(TextView statusView, int background, int textColor) {
        statusView.setBackgroundResource(background);
        statusView.setTextColor(getColor(textColor));
    }

    private void showBluetoothReading(BluetoothReading reading) {
        runOnUiThread(() -> updateDashboardFromReading(reading));
    }

    private void updateDashboardFromReading(BluetoothReading reading) {
        if (!reading.hasPlateNumber()
                || dashboardStateCoordinator == null
                || ambiguousDialogVisible) {
            return;
        }
        if (reading.getStatus() == BluetoothReading.Status.ERROR
                || reading.getStatus() == BluetoothReading.Status.UNSTABLE) {
            return;
        }

        double weightGrams = reading.getStatus() == BluetoothReading.Status.NO_LOAD
                ? 0.0
                : reading.getWeightGrams();
        dashboardStateCoordinator
                .processReading(
                        new PlateReading(reading.getPlateNumber(), weightGrams)
                )
                .ifPresent(this::handleDashboardUpdate);
    }

    private void handleDashboardUpdate(List<TrackedItemState> states) {
        showDisconnectSnapshot = false;
        long timestampMillis = System.currentTimeMillis();
        connectionManager.recordDashboardStates(states, timestampMillis);
        renderDashboard(states, false);
        showLiveDashboardTime(timestampMillis);
        showNextAmbiguousMatch();
    }

    private void showNextAmbiguousMatch() {
        List<RecognitionResult> pendingResults =
                dashboardStateCoordinator.getPendingAmbiguousResults();
        if (pendingResults.isEmpty()) {
            ambiguousDialogVisible = false;
            return;
        }

        RecognitionResult result = pendingResults.get(0);
        List<ItemProfile> candidates = result.getCandidates();
        int plateNumber = result.getReading().getPlateNumber();
        ambiguousDialogVisible = true;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.ambiguous_match_title, plateNumber))
                .setItems(
                        candidateNames(candidates),
                        (dialog, selectedIndex) ->
                                confirmAmbiguousMatch(
                                        plateNumber,
                                        candidates.get(selectedIndex)
                                )
                )
                .setNegativeButton(
                        R.string.ambiguous_match_not_sure,
                        (dialog, which) -> leaveAmbiguousMatchUnresolved(plateNumber)
                )
                .setCancelable(false)
                .show();
    }

    private String[] candidateNames(List<ItemProfile> candidates) {
        String[] names = new String[candidates.size()];
        for (int index = 0; index < candidates.size(); index++) {
            names[index] = candidates.get(index).getName();
        }
        return names;
    }

    private void confirmAmbiguousMatch(int plateNumber, ItemProfile selectedItem) {
        List<TrackedItemState> states =
                dashboardStateCoordinator.confirmAmbiguousMatch(
                        plateNumber,
                        selectedItem.getId()
                );
        finishAmbiguousChoice(states);
    }

    private void leaveAmbiguousMatchUnresolved(int plateNumber) {
        List<TrackedItemState> states =
                dashboardStateCoordinator.leaveAmbiguousMatchUnresolved(plateNumber);
        finishAmbiguousChoice(states);
    }

    private void finishAmbiguousChoice(List<TrackedItemState> states) {
        ambiguousDialogVisible = false;
        long timestampMillis = System.currentTimeMillis();
        connectionManager.recordDashboardStates(states, timestampMillis);
        renderDashboard(states, false);
        showLiveDashboardTime(timestampMillis);
        showNextAmbiguousMatch();
    }

    private void handleConnectionState(
            WeightStationConnection.State state,
            WeightStationConnection.Failure failure
    ) {
        renderConnectionState(state, failure);

        if (state == WeightStationConnection.State.DISCONNECTED) {
            showSavedDisconnectSnapshot();
        }
    }

    private void showSavedDisconnectSnapshot() {
        DisconnectSnapshot snapshot = disconnectSnapshotRepository.load();
        if (snapshot != null) {
            renderDashboard(snapshot.restore(visibleProfiles), true);
            showCachedDashboardTime(snapshot.getTimestampMillis());
        }
    }

    private boolean isDisconnectSnapshotIntent(Intent intent) {
        return intent != null
                && ACTION_SHOW_DISCONNECT_SNAPSHOT.equals(intent.getAction());
    }

    private void showLiveDashboardTime(long timestampMillis) {
        dashboardDataStatus.setText(
                getString(
                        R.string.dashboard_data_live,
                        formatTime(timestampMillis)
                )
        );
    }

    private void showCachedDashboardTime(long timestampMillis) {
        dashboardDataStatus.setText(
                getString(
                        R.string.dashboard_data_cached,
                        formatTime(timestampMillis)
                )
        );
    }

    private String formatTime(long timestampMillis) {
        return timeFormat.format(new Date(timestampMillis));
    }
}
