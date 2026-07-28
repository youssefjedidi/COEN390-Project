package com.coen390.smartexit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Screen for adding a new tracked item or editing an existing one (UI-4.2).
 *
 * Pass EXTRA_ITEM_ID to edit an existing item; omit it to create a new one.
 * Calibration (weight range) is shown as read-only status here; collecting
 * new calibration samples is handled separately (UI-4.3).
 */
public class AddEditItemActivity extends Activity {

    public static final String EXTRA_ITEM_ID = "com.coen390.smartexit.EXTRA_ITEM_ID";

    private ItemProfileRepository repository;
    private String editingItemId; // null when creating a new item
    private ItemProfile editingProfile; // null when creating a new item

    private TextView screenTitle;
    private EditText itemNameInput;
    private TextView calibrationStatus;

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

        screenTitle = findViewById(R.id.screenTitle);
        itemNameInput = findViewById(R.id.itemNameInput);
        calibrationStatus = findViewById(R.id.calibrationStatus);
        Button saveButton = findViewById(R.id.saveButton);
        Button cancelButton = findViewById(R.id.cancelButton);
        Button deleteButton = findViewById(R.id.deleteButton);

        editingItemId = getIntent().getStringExtra(EXTRA_ITEM_ID);
        loadExistingItemIfEditing();

        saveButton.setOnClickListener(v -> onSaveClicked());
        cancelButton.setOnClickListener(v -> finish());
        deleteButton.setOnClickListener(v -> confirmDelete());
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
        findViewById(R.id.deleteButton).setVisibility(android.view.View.VISIBLE);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_item_confirm_title)
                .setMessage(R.string.delete_item_confirm_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    repository.delete(editingProfile.getId());
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
            repository.save(editingProfile);
        } else {
            ItemProfile newProfile = new ItemProfile(name);
            repository.save(newProfile);
        }

        finish();
    }
}