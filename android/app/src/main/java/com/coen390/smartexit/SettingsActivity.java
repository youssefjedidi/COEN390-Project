package com.coen390.smartexit;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

public class SettingsActivity extends Activity implements
        WeightStationConnection.Listener,
        StationConnectionManager.MonitoringListener {
    private static final int BLUETOOTH_PERMISSION_REQUEST = 1002;
    private static final int PLATE_COUNT = 4;
    private static final double DEFAULT_REFERENCE_MASS_GRAMS = 453.6;

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
    private TextView plateCalibrationStatus;
    private TextView notificationStatus;
    private TextView notificationDetail;
    private ProgressBar connectionProgress;
    private Button connectionActionButton;
    private Button tareButton;
    private Button plateCalibrationButton;
    private Button notificationActionButton;
    private boolean plateCalibrationInProgress;
    private int skippedCalibrationPlates;

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
        plateCalibrationStatus = findViewById(R.id.plateCalibrationStatus);
        plateCalibrationButton = findViewById(R.id.plateCalibrationButton);
        notificationStatus = findViewById(R.id.notificationStatus);
        notificationDetail = findViewById(R.id.notificationDetail);
        notificationActionButton = findViewById(R.id.notificationActionButton);

        new DisconnectNotifier(this).ensureChannel();

        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        connectionActionButton.setOnClickListener(view -> handleConnectionAction());
        tareButton.setOnClickListener(view -> confirmTare());
        plateCalibrationButton.setOnClickListener(view -> beginPlateCalibration());
        notificationActionButton.setOnClickListener(view -> handleNotificationAction());
    }

    @Override
    protected void onResume() {
        super.onResume();
        connectionManager.addListener(this);
        connectionManager.addMonitoringListener(this);
        renderScreen();
    }

    @Override
    protected void onPause() {
        connectionManager.removeListener(this);
        connectionManager.removeMonitoringListener(this);
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
        } else if (requestCode == NotificationPermissionHelper.REQUEST_CODE) {
            renderNotificationState();
        }
    }

    private void renderScreen() {
        HardwareReadiness readiness = getHardwareReadiness();
        if (readiness != HardwareReadiness.READY) {
            pauseMonitoringFor(readiness);
        } else if (shouldResumeMonitoring()) {
            StationMonitoringService.startMonitoring(this);
        }
        renderHardwareStatus(readiness);
        renderConnectionState(
                readiness,
                connectionManager.getMonitoringState(),
                connectionManager.getFailure()
        );
        renderNotificationState();
    }

    private void renderNotificationState() {
        NotificationPermissionHelper.State state = NotificationPermissionHelper.getState(this);
        switch (state) {
            case ALLOWED:
                notificationStatus.setText(R.string.notification_status_allowed);
                notificationDetail.setText(R.string.notification_detail_allowed);
                notificationActionButton.setVisibility(View.GONE);
                break;
            case DENIED:
                notificationStatus.setText(R.string.notification_status_denied);
                notificationDetail.setText(R.string.notification_detail_denied);
                notificationActionButton.setText(R.string.open_notification_settings);
                notificationActionButton.setVisibility(View.VISIBLE);
                break;
            case NOT_REQUESTED:
            default:
                notificationStatus.setText(R.string.notification_status_not_requested);
                notificationDetail.setText(R.string.notification_detail_not_requested);
                notificationActionButton.setText(R.string.allow_notifications);
                notificationActionButton.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void handleNotificationAction() {
        if (NotificationPermissionHelper.getState(this)
                == NotificationPermissionHelper.State.NOT_REQUESTED) {
            NotificationPermissionHelper.request(this);
            return;
        }

        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
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

        MonitoringLifecycle.State state = connectionManager.getMonitoringState();
        if (state == MonitoringLifecycle.State.STARTING
                || state == MonitoringLifecycle.State.MONITORING
                || state == MonitoringLifecycle.State.RECONNECTING) {
            StationMonitoringService.stopMonitoring(this);
        } else {
            StationMonitoringService.startMonitoring(this);
        }
    }

    private void pauseMonitoringFor(HardwareReadiness readiness) {
        if (connectionManager.getMonitoringState() == MonitoringLifecycle.State.STOPPED) {
            return;
        }

        connectionManager.pauseMonitoring(pauseReasonFor(readiness));
    }

    private boolean shouldResumeMonitoring() {
        return connectionManager.getMonitoringState() == MonitoringLifecycle.State.PAUSED
                && connectionManager.isMonitoringEnabled()
                && connectionManager.getMonitoringPauseReason()
                != MonitoringLifecycle.PauseReason.CONNECTION_UNAVAILABLE;
    }

    private MonitoringLifecycle.PauseReason pauseReasonFor(HardwareReadiness readiness) {
        switch (readiness) {
            case PERMISSION_REQUIRED:
                return MonitoringLifecycle.PauseReason.PERMISSION_UNAVAILABLE;
            case BLUETOOTH_OFF:
                return MonitoringLifecycle.PauseReason.BLUETOOTH_OFF;
            case UNSUPPORTED:
            case READY:
            default:
                return MonitoringLifecycle.PauseReason.CONNECTION_UNAVAILABLE;
        }
    }

    private void renderConnectionState(
            HardwareReadiness readiness,
            MonitoringLifecycle.State state,
            WeightStationConnection.Failure failure
    ) {
        connectionProgress.setVisibility(View.GONE);
        connectionFailureReason.setVisibility(View.GONE);

        if (readiness != HardwareReadiness.READY) {
            renderHardwareSetupAction(readiness);
            renderTareState(false, R.string.tare_status_station_required);
            renderPlateCalibrationState(false, R.string.plate_calibration_station_required);
            return;
        }

        switch (state) {
            case STARTING:
                connectionStatus.setText(R.string.connection_status_connecting);
                connectionProgress.setVisibility(View.VISIBLE);
                setConnectionAction(R.string.stop_monitoring, true);
                break;
            case MONITORING:
                connectionStatus.setText(R.string.connection_status_monitoring);
                setConnectionAction(R.string.stop_monitoring, true);
                break;
            case RECONNECTING:
                connectionStatus.setText(R.string.connection_status_reconnecting);
                connectionProgress.setVisibility(View.VISIBLE);
                setConnectionAction(R.string.stop_monitoring, true);
                break;
            case PAUSED:
                connectionStatus.setText(R.string.connection_status_paused);
                if (failure != null) {
                    connectionFailureReason.setText(connectionFailureText(failure));
                    connectionFailureReason.setVisibility(View.VISIBLE);
                }
                setConnectionAction(R.string.action_retry, true);
                break;
            case STOPPED:
            default:
                connectionStatus.setText(R.string.connection_status_stopped);
                setConnectionAction(R.string.action_connect, true);
                break;
        }

        boolean tareAvailable = state == MonitoringLifecycle.State.MONITORING
                && connectionManager.canRequestTare();
        int tareText = R.string.tare_status_station_required;
        if (tareAvailable) {
            tareText = R.string.tare_status_ready;
        } else if (state == MonitoringLifecycle.State.MONITORING) {
            tareText = R.string.tare_status_firmware_required;
        }
        renderTareState(tareAvailable, tareText);
        renderPlateCalibrationState(
                state == MonitoringLifecycle.State.MONITORING
                        && connectionManager.canRequestPlateCalibration(),
                state == MonitoringLifecycle.State.MONITORING
                        ? R.string.plate_calibration_ready
                        : R.string.plate_calibration_station_required
        );
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
        tareButton.setAlpha(enabled ? 1.0f : 0.45f);
        tareStatus.setText(statusText);
    }

    private void renderPlateCalibrationState(boolean enabled, int statusText) {
        boolean actionEnabled = enabled && !plateCalibrationInProgress;
        plateCalibrationButton.setEnabled(actionEnabled);
        plateCalibrationButton.setAlpha(actionEnabled ? 1.0f : 0.45f);
        if (!plateCalibrationInProgress) {
            plateCalibrationStatus.setText(statusText);
        }
    }

    private void beginPlateCalibration() {
        EditText massInput = new EditText(this);
        massInput.setHint(R.string.plate_calibration_reference_hint);
        massInput.setInputType(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        massInput.setText(String.valueOf(DEFAULT_REFERENCE_MASS_GRAMS));
        massInput.selectAll();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.plate_calibration_reference_title)
                .setMessage(R.string.plate_calibration_reference_message)
                .setView(massInput)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.action_continue, null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    Double referenceMass = parseReferenceMass(massInput.getText().toString());
                    if (referenceMass == null) {
                        massInput.setError(getString(R.string.plate_calibration_invalid_mass));
                        return;
                    }

                    dialog.dismiss();
                    zeroScalesBeforeCalibration(referenceMass);
                }));
        dialog.show();
    }

    private Double parseReferenceMass(String text) {
        try {
            double mass = Double.parseDouble(text.trim().replace(',', '.'));
            return mass >= 20.0 && mass <= 1000.0 ? mass : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void zeroScalesBeforeCalibration(double referenceMass) {
        plateCalibrationInProgress = true;
        skippedCalibrationPlates = 0;
        plateCalibrationButton.setEnabled(false);
        plateCalibrationStatus.setText(R.string.plate_calibration_zeroing);

        connectionManager.requestCalibrationTare(new WeightStationConnection.CommandCallback() {
            @Override
            public void onCommandSucceeded() {
                runOnUiThread(() -> showPlateCalibrationStep(1, referenceMass));
            }

            @Override
            public void onCommandFailed(WeightStationConnection.CommandFailure failure) {
                runOnUiThread(() -> finishPlateCalibrationWithMessage(
                        R.string.plate_calibration_zero_failed
                ));
            }
        });
    }

    private void showPlateCalibrationStep(int plateNumber, double referenceMass) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.plate_calibration_step_title, plateNumber))
                .setMessage(getString(
                        R.string.plate_calibration_step_message,
                        referenceMass,
                        plateNumber
                ))
                .setCancelable(false)
                .setNegativeButton(
                        R.string.action_stop,
                        (dialog, which) -> finishPlateCalibration()
                )
                .setPositiveButton(
                        R.string.plate_calibration_step_action,
                        (dialog, which) -> requestPlateCalibration(
                                plateNumber,
                                referenceMass
                        )
                )
                .show();
    }

    private void requestPlateCalibration(int plateNumber, double referenceMass) {
        plateCalibrationStatus.setText(getString(
                R.string.plate_calibration_running,
                plateNumber
        ));

        connectionManager.requestPlateCalibration(
                plateNumber,
                referenceMass,
                new WeightStationConnection.CommandCallback() {
                    @Override
                    public void onCommandSucceeded() {
                        runOnUiThread(() -> continuePlateCalibration(
                                plateNumber,
                                referenceMass
                        ));
                    }

                    @Override
                    public void onCommandFailed(
                            WeightStationConnection.CommandFailure failure
                    ) {
                        runOnUiThread(() -> showPlateCalibrationFailure(
                                plateNumber,
                                referenceMass,
                                failure
                        ));
                    }
                }
        );
    }

    private void showPlateCalibrationFailure(
            int plateNumber,
            double referenceMass,
            WeightStationConnection.CommandFailure failure
    ) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        int message = failure == WeightStationConnection.CommandFailure.STATION_REJECTED
                ? R.string.plate_calibration_failed
                : R.string.plate_calibration_command_failed;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.plate_calibration_failed_title, plateNumber))
                .setMessage(message)
                .setCancelable(false)
                .setNegativeButton(
                        R.string.action_stop,
                        (dialog, which) -> finishPlateCalibration()
                )
                .setNeutralButton(
                        R.string.plate_calibration_skip,
                        (dialog, which) -> {
                            skippedCalibrationPlates++;
                            continuePlateCalibration(plateNumber, referenceMass);
                        }
                )
                .setPositiveButton(
                        R.string.action_retry,
                        (dialog, which) -> showPlateCalibrationStep(
                                plateNumber,
                                referenceMass
                        )
                )
                .show();
    }

    private void continuePlateCalibration(int plateNumber, double referenceMass) {
        if (plateNumber < PLATE_COUNT) {
            showPlateCalibrationStep(plateNumber + 1, referenceMass);
        } else {
            showPlateCalibrationComplete();
        }
    }

    private void showPlateCalibrationComplete() {
        int message = skippedCalibrationPlates == 0
                ? R.string.plate_calibration_complete
                : R.string.plate_calibration_complete_with_skips;
        plateCalibrationInProgress = false;
        skippedCalibrationPlates = 0;
        renderScreen();
        new AlertDialog.Builder(this)
                .setTitle(R.string.plate_calibration_complete_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void finishPlateCalibrationWithMessage(int message) {
        finishPlateCalibration();
        if (!isFinishing() && !isDestroyed()) {
            new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void finishPlateCalibration() {
        plateCalibrationInProgress = false;
        skippedCalibrationPlates = 0;
        renderScreen();
    }

    private void confirmTare() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.tare_confirm_title)
                .setMessage(R.string.tare_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.action_tare,
                        (dialog, which) -> requestTare()
                )
                .show();
    }

    private void requestTare() {
        tareButton.setEnabled(false);
        tareStatus.setText(R.string.tare_status_sending);
        connectionManager.requestTare(new WeightStationConnection.CommandCallback() {
            @Override
            public void onCommandSucceeded() {
                runOnUiThread(() -> {
                    tareStatus.setText(R.string.tare_status_complete);
                    tareButton.setEnabled(connectionManager.canRequestTare());
                });
            }

            @Override
            public void onCommandFailed(WeightStationConnection.CommandFailure failure) {
                runOnUiThread(() -> {
                    tareStatus.setText(tareFailureText(failure));
                    tareButton.setEnabled(connectionManager.canRequestTare());
                });
            }
        });
    }

    private int tareFailureText(WeightStationConnection.CommandFailure failure) {
        if (failure == WeightStationConnection.CommandFailure.NOT_SUPPORTED) {
            return R.string.tare_status_firmware_required;
        }
        if (failure == WeightStationConnection.CommandFailure.STATION_REJECTED) {
            return R.string.tare_status_rejected;
        }
        if (failure == WeightStationConnection.CommandFailure.TIMED_OUT) {
            return R.string.tare_status_timeout;
        }
        if (failure == WeightStationConnection.CommandFailure.DISCONNECTED) {
            return R.string.tare_status_disconnected;
        }
        return R.string.tare_status_failed;
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
    public void onMonitoringStateChanged(
            MonitoringLifecycle.State state,
            MonitoringLifecycle.PauseReason pauseReason
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
