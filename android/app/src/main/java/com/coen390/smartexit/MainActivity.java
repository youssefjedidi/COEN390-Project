package com.coen390.smartexit;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    private TextView stationStatus;
    private TextView readingState;
    private TextView weightValue;
    private TextView lastUpdate;
    private TextView readingDetail;
    private TextView dataSourceLabel;

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

        Button simulateItemButton = findViewById(R.id.simulateItemButton);
        Button clearTrayButton = findViewById(R.id.clearTrayButton);
        Button simulateOfflineButton = findViewById(R.id.simulateOfflineButton);

        simulateItemButton.setOnClickListener(view -> showMockItemReading());
        clearTrayButton.setOnClickListener(view -> showMockClearReading());
        simulateOfflineButton.setOnClickListener(view -> showOfflineState());
    }

    private void showMockItemReading() {
        stationStatus.setText(R.string.station_mock);
        stationStatus.setBackgroundResource(R.drawable.status_connected_background);
        stationStatus.setTextColor(getColor(R.color.status_connected_text));

        readingState.setText(R.string.reading_state_item_detected);
        weightValue.setText("146 g");
        lastUpdate.setText("Last update: " + timeFormat.format(new Date()));
        readingDetail.setText(R.string.reading_detail_item);
        dataSourceLabel.setText(R.string.data_source_mock);
    }

    private void showMockClearReading() {
        stationStatus.setText(R.string.station_mock);
        stationStatus.setBackgroundResource(R.drawable.status_connected_background);
        stationStatus.setTextColor(getColor(R.color.status_connected_text));

        readingState.setText(R.string.reading_state_clear);
        weightValue.setText("0 g");
        lastUpdate.setText("Last update: " + timeFormat.format(new Date()));
        readingDetail.setText(R.string.reading_detail_clear);
        dataSourceLabel.setText(R.string.data_source_mock);
    }

    private void showOfflineState() {
        stationStatus.setText(R.string.station_offline);
        stationStatus.setBackgroundResource(R.drawable.status_offline_background);
        stationStatus.setTextColor(getColor(R.color.status_offline_text));

        readingState.setText(R.string.reading_state_offline);
        lastUpdate.setText("Last checked: " + timeFormat.format(new Date()));
        readingDetail.setText(R.string.reading_detail_offline);
        dataSourceLabel.setText(R.string.data_source_offline);
    }
}
