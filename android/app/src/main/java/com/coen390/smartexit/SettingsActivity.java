package com.coen390.smartexit;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class SettingsActivity extends Activity implements WeightStationConnection.Listener {
    private static final int BLUETOOTH_PERMISSION_REQUEST = 1002;

    private enum HardwareReadiness {
        READY,
        PERMISSION_REQUIRED,
        BLUETOOTH_OFF,
        UNSUPPORTED
    }

    private StationConnectionManager connectionManager;
    private TextView hardwareStatus;
    private TextView connectionStatus;
    private TextView connectionFailureReason;
    private TextView tareStatus;
    private ProgressBar connectionProgress;
    private Button connectionActionButton;
    private Button tareButton;

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
        tareStatus = findViewById(R.id.tareStatus);
        tareButton = findViewById(R.id.tareButton);

        findViewById(R.id.navDashboardButton).setOnClickListener(view -> finish());
        findViewById(R.id.navSettingsButton).setEnabled(false);
        connectionActionButton.setOnClickListener(view -> handleConnectionAction());
        tareButton.setOnClickListener(view -> requestTare());
    }

    @Override
    protected void onResume() {
        super.onResume();
        connectionManager.addListener(this);
        renderScreen();
    }

    @Override
    protected void onPause() {
        connectionManager.removeListener(this);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST) {
            renderScreen();
        }
    }

    private void renderScreen() {
        HardwareReadiness readiness = getHardwareReadiness();
        if (readiness != HardwareReadiness.READY
                && connectionManager.getState() != WeightStationConnection.State.IDLE) {
            connectionManager.reset();
        }
        renderHardwareStatus(readiness);
        renderConnectionState(
                readiness,
                connectionManager.getState(),
                connectionManager.getFailure()
        );
    }

    private HardwareReadiness getHardwareReadiness() {
        if (!BluetoothPermissionHelper.supportsBle(this)) {
            return HardwareReadiness.UNSUPPORTED;
        }
        if (!BluetoothPermissionHelper.hasRequiredPermissions(this)) {
            return HardwareReadiness.PERMISSION_REQUIRED;
        }

        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null) {
            return HardwareReadiness.UNSUPPORTED;
        }
        return adapter.isEnabled()
                ? HardwareReadiness.READY
                : HardwareReadiness.BLUETOOTH_OFF;
    }

    private void renderHardwareStatus(HardwareReadiness readiness) {
        int text;
        switch (readiness) {
            case READY:
                text = R.string.hardware_status_ready;
                break;
            case PERMISSION_REQUIRED:
                text = R.string.hardware_status_permission_required;
                break;
            case BLUETOOTH_OFF:
                text = R.string.hardware_status_off;
                break;
            case UNSUPPORTED:
            default:
                text = R.string.hardware_status_unsupported;
                break;
        }
        hardwareStatus.setText(text);
    }

    private BluetoothAdapter getBluetoothAdapter() {
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        return bluetoothManager == null ? null : bluetoothManager.getAdapter();
    }

    private void handleConnectionAction() {
        HardwareReadiness readiness = getHardwareReadiness();
        if (readiness == HardwareReadiness.PERMISSION_REQUIRED) {
            BluetoothPermissionHelper.recordPermissionRequest(this);
            requestPermissions(
                    BluetoothPermissionHelper.requiredPermissions(),
                    BLUETOOTH_PERMISSION_REQUEST
            );
            return;
        }
        if (readiness == HardwareReadiness.BLUETOOTH_OFF) {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            return;
        }
        if (readiness != HardwareReadiness.READY) {
            return;
        }

        WeightStationConnection.State state = connectionManager.getState();
        if (state == WeightStationConnection.State.SCANNING
                || state == WeightStationConnection.State.CONNECTING
                || state == WeightStationConnection.State.CONNECTED) {
            connectionManager.disconnect();
        } else {
            connectionManager.connect();
        }
    }

    private void renderConnectionState(
            HardwareReadiness readiness,
            WeightStationConnection.State state,
            WeightStationConnection.Failure failure
    ) {
        connectionProgress.setVisibility(View.GONE);
        connectionFailureReason.setVisibility(View.GONE);

        if (readiness != HardwareReadiness.READY) {
            renderHardwareSetupAction(readiness);
            renderTareState(false, R.string.tare_status_station_required);
            return;
        }

        switch (state) {
            case SCANNING:
            case CONNECTING:
                connectionStatus.setText(
                        state == WeightStationConnection.State.SCANNING
                                ? R.string.connection_status_scanning
                                : R.string.connection_status_connecting
                );
                connectionProgress.setVisibility(View.VISIBLE);
                setConnectionAction(R.string.action_cancel, true);
                break;
            case CONNECTED:
                connectionStatus.setText(R.string.connection_status_connected);
                setConnectionAction(R.string.action_disconnect, true);
                break;
            case FAILED:
                connectionStatus.setText(R.string.connection_status_failed);
                connectionFailureReason.setText(connectionFailureText(failure));
                connectionFailureReason.setVisibility(View.VISIBLE);
                setConnectionAction(R.string.action_retry, true);
                break;
            case DISCONNECTED:
                connectionStatus.setText(R.string.connection_status_disconnected);
                setConnectionAction(R.string.action_connect, true);
                break;
            case IDLE:
            default:
                connectionStatus.setText(R.string.connection_status_idle);
                setConnectionAction(R.string.action_connect, true);
                break;
        }

        boolean tareAvailable = state == WeightStationConnection.State.CONNECTED
                && connectionManager.canRequestTare();
        int tareText = R.string.tare_status_station_required;
        if (tareAvailable) {
            tareText = R.string.tare_status_ready;
        } else if (state == WeightStationConnection.State.CONNECTED) {
            tareText = R.string.tare_status_firmware_required;
        }
        renderTareState(tareAvailable, tareText);
    }

    private void renderHardwareSetupAction(HardwareReadiness readiness) {
        switch (readiness) {
            case PERMISSION_REQUIRED:
                connectionStatus.setText(R.string.connection_status_permission_required);
                setConnectionAction(R.string.allow_bluetooth_access, true);
                break;
            case BLUETOOTH_OFF:
                connectionStatus.setText(R.string.connection_status_bluetooth_off);
                setConnectionAction(R.string.open_bluetooth_settings, true);
                break;
            case UNSUPPORTED:
            default:
                connectionStatus.setText(R.string.connection_status_unavailable);
                setConnectionAction(R.string.action_unavailable, false);
                break;
        }
    }

    private void setConnectionAction(int text, boolean enabled) {
        connectionActionButton.setText(text);
        connectionActionButton.setEnabled(enabled);
    }

    private void renderTareState(boolean enabled, int statusText) {
        tareButton.setEnabled(enabled);
        tareStatus.setText(statusText);
    }

    private void requestTare() {
        tareButton.setEnabled(false);
        tareStatus.setText(R.string.tare_status_sending);
        connectionManager.requestTare(new WeightStationConnection.CommandCallback() {
            @Override
            public void onCommandSent() {
                runOnUiThread(() -> {
                    tareStatus.setText(R.string.tare_status_sent);
                    tareButton.setEnabled(connectionManager.canRequestTare());
                });
            }

            @Override
            public void onCommandFailed(WeightStationConnection.CommandFailure failure) {
                runOnUiThread(() -> {
                    int text = failure == WeightStationConnection.CommandFailure.NOT_SUPPORTED
                            ? R.string.tare_status_firmware_required
                            : R.string.tare_status_failed;
                    tareStatus.setText(text);
                    tareButton.setEnabled(connectionManager.canRequestTare());
                });
            }
        });
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
    public void onStateChanged(
            WeightStationConnection.State state,
            WeightStationConnection.Failure failure
    ) {
        runOnUiThread(this::renderScreen);
    }

    @Override
    public void onReadingReceived(BluetoothReading reading) {
    }

    @Override
    public void onInvalidPayload() {
    }
}
