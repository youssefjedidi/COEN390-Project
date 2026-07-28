package com.coen390.smartexit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Screen for adding a new tracked item or editing an existing one (UI-4.2).
 *
 * Pass EXTRA_ITEM_ID to edit an existing item; omit it to create a new one.
 * A completed calibration remains pending until the user saves the item.
 */
public class AddEditItemActivity extends Activity implements WeightStationConnection.Listener {

    public static final String EXTRA_ITEM_ID = "com.coen390.smartexit.EXTRA_ITEM_ID";
    private static final int PLATE_COUNT = 4;
    private static final int CALIBRATION_SAMPLE_COUNT = 5;
    private static final double CALIBRATION_STABILITY_GRAMS = 5.0;
    private static final double CALIBRATION_RANGE_MARGIN_GRAMS = 5.0;

    private ItemProfileRepository repository;
    private StationConnectionManager connectionManager;
    private final CalibrationSampleCollector calibrationCollector =
            new CalibrationSampleCollector(
                    PLATE_COUNT,
                    CALIBRATION_SAMPLE_COUNT,
                    CALIBRATION_STABILITY_GRAMS,
                    CALIBRATION_RANGE_MARGIN_GRAMS
            );
    private String editingItemId; // null when creating a new item
    private ItemProfile editingProfile; // null when creating a new item
    private Double pendingMinimumWeight;
    private Double pendingMaximumWeight;

    private TextView screenTitle;
    private EditText itemNameInput;
    private TextView calibrationStatus;
    private TextView calibrationMessage;
    private Spinner calibrationPlate;
    private Button calibrationButton;

    public static Intent newIntentForAdd(Context context) {
        return new Intent(context, AddEditItemActivity.class);
    }

    public static Intent newIntentForEdit(Context context, String itemId) {
        Intent intent = new Intent(context, AddEditItemActivity.class);
        intent.putExtra(EXTRA_ITEM_ID, itemId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_item);

        repository = new ItemProfileRepository(getApplicationContext());
        connectionManager = StationConnectionManager.getInstance(this);

        screenTitle = findViewById(R.id.screenTitle);
        itemNameInput = findViewById(R.id.itemNameInput);
        calibrationStatus = findViewById(R.id.calibrationStatus);
        calibrationMessage = findViewById(R.id.calibrationMessage);
        calibrationPlate = findViewById(R.id.calibrationPlate);
        calibrationButton = findViewById(R.id.calibrationButton);
        Button saveButton = findViewById(R.id.saveButton);
        Button cancelButton = findViewById(R.id.cancelButton);

        editingItemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        loadExistingItemIfEditing();

        saveButton.setOnClickListener(v -> onSaveClicked());
        cancelButton.setOnClickListener(v -> finish());
        calibrationButton.setOnClickListener(v -> startCalibration());
    }

    @Override
    protected void onResume() {
        super.onResume();
        connectionManager.addListener(this);
        renderCalibrationAvailability(connectionManager.getState());
    }

    @Override
    protected void onPause() {
        connectionManager.removeListener(this);
        if (calibrationCollector.isActive()) {
            calibrationCollector.cancel();
        }
        super.onPause();
    }

    private void loadExistingItemIfEditing() {
        if (editingItemId == null) {
            screenTitle.setText(getString(R.string.add_item_title));
            calibrationStatus.setText(getString(R.string.calibration_status_uncalibrated));
            return;
        }

        List<ItemProfile> allProfiles = repository.getAll();
        for (ItemProfile profile : allProfiles) {
            if (profile.getId().equals(editingItemId)) {
                editingProfile = profile;
                break;
            }
        }

        if (editingProfile == null) {
            Toast.makeText(this, R.string.item_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        screenTitle.setText(getString(R.string.edit_item_title));
        itemNameInput.setText(editingProfile.getName());
        updateCalibrationStatusText(editingProfile);
    }

    private void updateCalibrationStatusText(ItemProfile profile) {
        if (profile.isCalibrated()) {
            calibrationStatus.setText(
                    getString(
                            R.string.calibration_status_format,
                            profile.getMinWeightGrams(),
                            profile.getMaxWeightGrams()
                    )
            );
        } else {
            calibrationStatus.setText(getString(R.string.calibration_status_uncalibrated));
        }
    }

    private void onSaveClicked() {
        String name = itemNameInput.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, R.string.item_name_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (editingProfile != null) {
            editingProfile.setName(name);
            applyPendingCalibration(editingProfile);
            repository.save(editingProfile);
        } else {
            ItemProfile newProfile = new ItemProfile(name);
            applyPendingCalibration(newProfile);
            repository.save(newProfile);
        }

        finish();
    }

    private void startCalibration() {
        int plateNumber = calibrationPlate.getSelectedItemPosition() + 1;
        calibrationCollector.start(plateNumber);
        calibrationMessage.setText(
                getString(
                        R.string.calibration_collecting_format,
                        plateNumber,
                        0,
                        CALIBRATION_SAMPLE_COUNT
                )
        );
        calibrationButton.setText(R.string.calibration_restart);
    }

    private void handleCalibrationReading(BluetoothReading reading) {
        CalibrationSampleCollector.Update update = calibrationCollector.add(reading);
        switch (update.getStatus()) {
            case IGNORED:
                return;
            case WAITING_FOR_ITEM:
                calibrationMessage.setText(
                        getString(
                                R.string.calibration_waiting_for_item,
                                calibrationCollector.getSelectedPlate()
                        )
                );
                return;
            case WAITING_FOR_STABLE:
                calibrationMessage.setText(R.string.calibration_waiting_for_stable);
                return;
            case SENSOR_ERROR:
                calibrationMessage.setText(R.string.calibration_sensor_error);
                return;
            case COLLECTING:
                calibrationMessage.setText(
                        getString(
                                R.string.calibration_collecting_format,
                                calibrationCollector.getSelectedPlate(),
                                update.getSampleCount(),
                                calibrationCollector.getRequiredSamples()
                        )
                );
                return;
            case COMPLETE:
                pendingMinimumWeight = update.getMinimumWeightGrams();
                pendingMaximumWeight = update.getMaximumWeightGrams();
                calibrationStatus.setText(
                        getString(
                                R.string.calibration_status_format,
                                pendingMinimumWeight,
                                pendingMaximumWeight
                        )
                );
                calibrationMessage.setText(R.string.calibration_ready_to_save);
                calibrationButton.setText(R.string.calibration_again);
        }
    }

    private void applyPendingCalibration(ItemProfile profile) {
        if (pendingMinimumWeight != null && pendingMaximumWeight != null) {
            profile.setWeightRange(pendingMinimumWeight, pendingMaximumWeight);
        }
    }

    private void renderCalibrationAvailability(WeightStationConnection.State state) {
        boolean connected = state == WeightStationConnection.State.CONNECTED;
        calibrationButton.setEnabled(connected);
        calibrationPlate.setEnabled(connected);

        if (!connected) {
            if (calibrationCollector.isActive()) {
                calibrationCollector.cancel();
            }
            calibrationButton.setText(R.string.calibration_start);
            calibrationMessage.setText(
                    pendingMinimumWeight == null
                            ? R.string.calibration_station_required
                            : R.string.calibration_ready_to_save
            );
        } else if (pendingMinimumWeight != null) {
            calibrationButton.setText(R.string.calibration_again);
            calibrationMessage.setText(R.string.calibration_ready_to_save);
        } else if (!calibrationCollector.isActive()) {
            calibrationButton.setText(R.string.calibration_start);
            calibrationMessage.setText(R.string.calibration_instructions);
        }
    }

    @Override
    public void onStateChanged(
            WeightStationConnection.State state,
            WeightStationConnection.Failure failure
    ) {
        runOnUiThread(() -> renderCalibrationAvailability(state));
    }

    @Override
    public void onReadingReceived(BluetoothReading reading) {
        // Keep collection and screen updates on one thread so a BLE callback
        // cannot race with the user restarting calibration.
        runOnUiThread(() -> handleCalibrationReading(reading));
    }

    @Override
    public void onInvalidPayload() {
        runOnUiThread(() -> {
            if (calibrationCollector.isActive()) {
                calibrationMessage.setText(R.string.calibration_invalid_reading);
            }
        });
    }
}
