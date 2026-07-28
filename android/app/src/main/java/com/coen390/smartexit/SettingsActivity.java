package com.coen390.smartexit;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Station Settings screen (UI-3.2 + UI-3.3).
 *
 * Shows the current Bluetooth hardware readiness and the current
 * WeightStationConnection state, with a single action button whose label
 * and behavior change based on that state:
 *   IDLE / DISCONNECTED -> "Connect"
 *   SCANNING / CONNECTING -> progress shown, "Cancel"
 *   CONNECTED -> "Disconnect"
 *   FAILED -> failure reason shown, "Retry"
 *
 * Reads and controls the same shared connection MainActivity uses, via
 * StationConnectionManager, so the two screens never fight over the BLE
 * radio.
 */
public class SettingsActivity extends Activity implements WeightStationConnection.Listener {

    private StationConnectionManager connectionManager;

    private TextView hardwareStatus;
    private TextView connectionStatus;
    private TextView connectionFailureReason;
    private ProgressBar connectionProgress;
    private Button connectionActionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        connectionManager = StationConnectionManager.getInstance(this);

        hardwareStatus = findViewById(R.id.hardwareStatus);
        connectionStatus = findViewById(R.id.connectionStatus);
        connectionFailureReason = findViewById(R.id.connectionFailureReason);
        connectionProgress = findViewById(R.id.connectionProgress);
        connectionActionButton = findViewById(R.id.connectionActionButton);

        Button navDashboardButton = findViewById(R.id.navDashboardButton);
        navDashboardButton.setOnClickListener(v -> finish());

        Button navSettingsButton = findViewById(R.id.navSettingsButton);
        navSettingsButton.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        connectionManager.addListener(this);
        refreshHardwareStatus();
        renderConnectionState(connectionManager.getState(), connectionManager.getFailure());
    }

    @Override
    protected void onPause() {
        connectionManager.removeListener(this);
        super.onPause();
    }

    private void refreshHardwareStatus() {
        if (!BluetoothPermissionHelper.supportsBle(this)) {
            hardwareStatus.setText(R.string.hardware_status_unsupported);
            return;
        }
        if (!BluetoothPermissionHelper.hasRequiredPermissions(this)) {
            hardwareStatus.setText(R.string.hardware_status_permission_required);
            return;
        }
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            hardwareStatus.setText(R.string.hardware_status_off);
            return;
        }
        hardwareStatus.setText(R.string.hardware_status_ready);
    }

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        return bluetoothManager == null ? null : bluetoothManager.getAdapter();
    }

    private void renderConnectionState(
            WeightStationConnection.State state,
            WeightStationConnection.Failure failure
    ) {
        switch (state) {
            case IDLE:
            case DISCONNECTED:
                connectionStatus.setText(
                        state == WeightStationConnection.State.DISCONNECTED
                                ? R.string.connection_status_disconnected
                                : R.string.connection_status_idle
                );
                connectionFailureReason.setVisibility(View.GONE);
                connectionProgress.setVisibility(View.GONE);
                connectionActionButton.setText(R.string.action_connect);
                connectionActionButton.setVisibility(View.VISIBLE);
                connectionActionButton.setOnClickListener(v -> connectionManager.connect());
                break;

            case SCANNING:
            case CONNECTING:
                connectionStatus.setText(
                        state == WeightStationConnection.State.SCANNING
                                ? R.string.connection_status_scanning
                                : R.string.connection_status_connecting
                );
                connectionFailureReason.setVisibility(View.GONE);
                connectionProgress.setVisibility(View.VISIBLE);
                connectionActionButton.setText(R.string.action_cancel);
                connectionActionButton.setVisibility(View.VISIBLE);
                connectionActionButton.setOnClickListener(v -> connectionManager.disconnect());
                break;

            case CONNECTED:
                connectionStatus.setText(R.string.connection_status_connected);
                connectionFailureReason.setVisibility(View.GONE);
                connectionProgress.setVisibility(View.GONE);
                connectionActionButton.setText(R.string.action_disconnect);
                connectionActionButton.setVisibility(View.VISIBLE);
                connectionActionButton.setOnClickListener(v -> connectionManager.disconnect());
                break;

            case FAILED:
                connectionStatus.setText(R.string.connection_status_failed);
                connectionFailureReason.setText(connectionFailureText(failure));
                connectionFailureReason.setVisibility(View.VISIBLE);
                connectionProgress.setVisibility(View.GONE);
                connectionActionButton.setText(R.string.action_retry);
                connectionActionButton.setVisibility(View.VISIBLE);
                connectionActionButton.setOnClickListener(v -> connectionManager.connect());
                break;
        }
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

    @Override
    public void onStateChanged(WeightStationConnection.State state, WeightStationConnection.Failure failure) {
        runOnUiThread(() -> renderConnectionState(state, failure));
    }

    @Override
    public void onReadingReceived(BluetoothReading reading) {
        // Settings screen doesn't display live readings; MainActivity handles that.
    }

    @Override
    public void onInvalidPayload() {
        // Not relevant to this screen.
    }
}