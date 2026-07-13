package com.coen390.smartexit;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BLUETOOTH_PERMISSION_REQUEST = 1001;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final MockWeightSource mockWeightSource = new MockWeightSource();

    private TextView stationStatus;
    private TextView readingState;
    private TextView weightValue;
    private TextView lastUpdate;
    private TextView readingDetail;
    private TextView dataSourceLabel;
    private Button bluetoothActionButton;
    private WeightDisplayState currentDisplayState;
    private WeightStationConnection stationConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        stationStatus = findViewById(R.id.stationStatus);
        readingState = findViewById(R.id.readingState);
        weightValue = findViewById(R.id.weightValue);
        lastUpdate = findViewById(R.id.lastUpdate);
        readingDetail = findViewById(R.id.readingDetail);
        dataSourceLabel = findViewById(R.id.dataSourceLabel);
        bluetoothActionButton = findViewById(R.id.bluetoothActionButton);

        Button simulateItemButton = findViewById(R.id.simulateItemButton);
        Button clearTrayButton = findViewById(R.id.clearTrayButton);
        Button simulateOfflineButton = findViewById(R.id.simulateOfflineButton);

        simulateItemButton.setOnClickListener(view -> showMockReading(mockWeightSource.nextItemReading()));
        clearTrayButton.setOnClickListener(view -> showMockReading(mockWeightSource.clearTrayReading()));
        simulateOfflineButton.setOnClickListener(view -> showOfflineState());
        bluetoothActionButton.setOnClickListener(view -> handleBluetoothAction());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBluetoothReadiness();
    }

    @Override
    protected void onDestroy() {
        if (stationConnection != null) {
            stationConnection.close();
        }
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

        WeightStationConnection connection = getOrCreateStationConnection(bluetoothAdapter);
        WeightStationConnection.State state = connection.getState();

        if (state == WeightStationConnection.State.SCANNING
                || state == WeightStationConnection.State.CONNECTING
                || state == WeightStationConnection.State.CONNECTED) {
            connection.disconnect();
        } else {
            connection.connect();
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

        WeightStationConnection connection = getOrCreateStationConnection(bluetoothAdapter);
        WeightStationConnection.State connectionState = connection.getState();
        if (connectionState == WeightStationConnection.State.IDLE) {
            showBluetoothReadyState();
        } else {
            renderConnectionState(connectionState, connection.getFailure());
        }
    }

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        return bluetoothManager == null ? null : bluetoothManager.getAdapter();
    }

    private WeightStationConnection getOrCreateStationConnection(BluetoothAdapter adapter) {
        if (stationConnection == null) {
            WeightStationConnection.Transport transport = new AndroidBleTransport(this, adapter);
            stationConnection = new WeightStationConnection(
                    transport,
                    (state, failure) -> runOnUiThread(() -> renderConnectionState(state, failure))
            );
        }
        return stationConnection;
    }

    private void closeStationConnection() {
        if (stationConnection != null) {
            stationConnection.close();
            stationConnection = null;
        }
    }

    private void showBluetoothSetupState(int stationText, int detailText, int buttonText) {
        currentDisplayState = null;

        stationStatus.setText(stationText);
        stationStatus.setBackgroundResource(R.drawable.status_offline_background);
        stationStatus.setTextColor(getColor(R.color.status_offline_text));
        readingState.setText(R.string.reading_state_waiting);
        weightValue.setText(R.string.empty_weight);
        lastUpdate.setText(R.string.no_data_yet);
        readingDetail.setText(detailText);
        dataSourceLabel.setText(R.string.data_source_waiting);

        if (buttonText == 0) {
            bluetoothActionButton.setVisibility(View.GONE);
            return;
        }

        bluetoothActionButton.setText(buttonText);
        bluetoothActionButton.setVisibility(View.VISIBLE);
    }

    private void showBluetoothReadyState() {
        bluetoothActionButton.setText(R.string.connect_to_station);
        bluetoothActionButton.setVisibility(View.VISIBLE);

        if (currentDisplayState != null) {
            return;
        }

        stationStatus.setText(R.string.station_waiting);
        stationStatus.setBackgroundResource(R.drawable.status_waiting_background);
        stationStatus.setTextColor(getColor(R.color.status_waiting_text));
        readingState.setText(R.string.reading_state_waiting);
        weightValue.setText(R.string.empty_weight);
        lastUpdate.setText(R.string.no_data_yet);
        readingDetail.setText(R.string.reading_detail_bluetooth_ready);
        dataSourceLabel.setText(R.string.data_source_waiting);
    }

    private void renderConnectionState(
            WeightStationConnection.State state,
            WeightStationConnection.Failure failure
    ) {
        if (state == WeightStationConnection.State.SCANNING) {
            showConnectionState(
                    R.string.station_scanning,
                    R.string.reading_detail_station_scanning,
                    R.string.cancel_connection,
                    R.drawable.status_waiting_background,
                    R.color.status_waiting_text
            );
            return;
        }

        if (state == WeightStationConnection.State.CONNECTING) {
            showConnectionState(
                    R.string.station_connecting,
                    R.string.reading_detail_station_connecting,
                    R.string.cancel_connection,
                    R.drawable.status_waiting_background,
                    R.color.status_waiting_text
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
                    R.string.reconnect_station,
                    R.drawable.status_offline_background,
                    R.color.status_offline_text
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
        stationStatus.setText(stationText);
        stationStatus.setBackgroundResource(background);
        stationStatus.setTextColor(getColor(textColor));
        readingDetail.setText(detailText);
        bluetoothActionButton.setText(buttonText);
        bluetoothActionButton.setVisibility(View.VISIBLE);
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

    private void showMockReading(TrayReading reading) {
        if (reading.status == TrayStatus.TRAY_CLEAR) {
            renderState(WeightDisplayState.mockClear(currentTime()));
        } else {
            renderState(WeightDisplayState.mockItem(reading.sampleName, reading.weightGrams, currentTime()));
        }
    }

    private void showOfflineState() {
        renderState(WeightDisplayState.offline(currentTime()));
    }

    // BLE callbacks may arrive on a background thread.
    void onBluetoothWeightReading(int weightGrams, String label) {
        runOnUiThread(() -> renderState(WeightDisplayState.liveItem(label, weightGrams, currentTime())));
    }

    void onBluetoothTrayClear() {
        runOnUiThread(() -> renderState(WeightDisplayState.liveClear(currentTime())));
    }

    void onBluetoothDisconnected() {
        runOnUiThread(this::showOfflineState);
    }

    private void renderState(WeightDisplayState state) {
        currentDisplayState = state;
        renderStationStatus(state.source);

        if (state.weightGrams != null) {
            weightValue.setText(getString(R.string.weight_format, state.weightGrams));
        }

        if (state.source == DataSource.OFFLINE) {
            lastUpdate.setText(getString(R.string.last_checked_format, state.displayTime));
        } else {
            lastUpdate.setText(getString(R.string.last_update_format, state.displayTime));
        }

        dataSourceLabel.setText(dataSourceText(state.source));
        readingState.setText(readingStateText(state.status, state.source));
        readingDetail.setText(readingDetailText(state));
    }

    private void renderStationStatus(DataSource source) {
        if (source == DataSource.OFFLINE) {
            stationStatus.setText(R.string.station_offline);
            stationStatus.setBackgroundResource(R.drawable.status_offline_background);
            stationStatus.setTextColor(getColor(R.color.status_offline_text));
            return;
        }

        stationStatus.setText(source == DataSource.LIVE ? R.string.station_live : R.string.station_mock);
        stationStatus.setBackgroundResource(R.drawable.status_connected_background);
        stationStatus.setTextColor(getColor(R.color.status_connected_text));
    }

    private int dataSourceText(DataSource source) {
        if (source == DataSource.LIVE) {
            return R.string.data_source_live;
        }
        if (source == DataSource.OFFLINE) {
            return R.string.data_source_offline;
        }
        return R.string.data_source_mock;
    }

    private int readingStateText(TrayStatus status, DataSource source) {
        if (source == DataSource.OFFLINE) {
            return R.string.reading_state_offline;
        }
        if (status == TrayStatus.TRAY_CLEAR) {
            return R.string.reading_state_clear;
        }
        if (status == TrayStatus.ITEM_PRESENT) {
            return R.string.reading_state_item_detected;
        }
        return R.string.reading_state_waiting;
    }

    private String readingDetailText(WeightDisplayState state) {
        if (state.source == DataSource.OFFLINE) {
            return getString(R.string.reading_detail_offline);
        }
        if (state.status == TrayStatus.TRAY_CLEAR) {
            return getString(R.string.reading_detail_clear);
        }
        if (state.source == DataSource.LIVE) {
            return getString(R.string.reading_detail_live_item, state.itemLabel);
        }
        return getString(R.string.reading_detail_item, state.itemLabel);
    }

    private String currentTime() {
        return timeFormat.format(new Date());
    }

    private enum DataSource {
        MOCK,
        LIVE,
        OFFLINE
    }

    private enum TrayStatus {
        ITEM_PRESENT,
        TRAY_CLEAR,
        UNKNOWN
    }

    private static class WeightDisplayState {
        final DataSource source;
        final TrayStatus status;
        final Integer weightGrams;
        final String itemLabel;
        final String displayTime;

        private WeightDisplayState(
                DataSource source,
                TrayStatus status,
                Integer weightGrams,
                String itemLabel,
                String displayTime
        ) {
            this.source = source;
            this.status = status;
            this.weightGrams = weightGrams;
            this.itemLabel = itemLabel;
            this.displayTime = displayTime;
        }

        static WeightDisplayState mockItem(String label, int weightGrams, String displayTime) {
            return item(DataSource.MOCK, label, weightGrams, displayTime);
        }

        static WeightDisplayState liveItem(String label, int weightGrams, String displayTime) {
            return item(DataSource.LIVE, label, weightGrams, displayTime);
        }

        static WeightDisplayState mockClear(String displayTime) {
            return clear(DataSource.MOCK, displayTime);
        }

        static WeightDisplayState liveClear(String displayTime) {
            return clear(DataSource.LIVE, displayTime);
        }

        static WeightDisplayState offline(String displayTime) {
            return new WeightDisplayState(DataSource.OFFLINE, TrayStatus.UNKNOWN, null, "", displayTime);
        }

        private static WeightDisplayState item(DataSource source, String label, int weightGrams, String displayTime) {
            return new WeightDisplayState(source, TrayStatus.ITEM_PRESENT, weightGrams, label, displayTime);
        }

        private static WeightDisplayState clear(DataSource source, String displayTime) {
            return new WeightDisplayState(source, TrayStatus.TRAY_CLEAR, 0, "", displayTime);
        }
    }

    private static class TrayReading {
        final TrayStatus status;
        final int weightGrams;
        final String sampleName;

        private TrayReading(TrayStatus status, int weightGrams, String sampleName) {
            this.status = status;
            this.weightGrams = weightGrams;
            this.sampleName = sampleName;
        }

        static TrayReading itemPresent(String sampleName, int weightGrams) {
            return new TrayReading(TrayStatus.ITEM_PRESENT, weightGrams, sampleName);
        }

        static TrayReading trayClear() {
            return new TrayReading(TrayStatus.TRAY_CLEAR, 0, "");
        }
    }

    private static class MockWeightSource {
        private final TrayReading[] sampleReadings = {
                TrayReading.itemPresent("Wallet", 146),
                TrayReading.itemPresent("Keys", 38),
                TrayReading.itemPresent("Medication pouch", 82)
        };

        private int nextSampleIndex = 0;

        TrayReading nextItemReading() {
            TrayReading reading = sampleReadings[nextSampleIndex];
            nextSampleIndex = (nextSampleIndex + 1) % sampleReadings.length;
            return reading;
        }

        TrayReading clearTrayReading() {
            return TrayReading.trayClear();
        }
    }
}
