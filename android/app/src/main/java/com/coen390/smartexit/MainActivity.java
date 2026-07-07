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
    private final MockWeightSource mockWeightSource = new MockWeightSource();

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

        simulateItemButton.setOnClickListener(view -> renderReading(mockWeightSource.nextItemReading()));
        clearTrayButton.setOnClickListener(view -> renderReading(mockWeightSource.clearTrayReading()));
        simulateOfflineButton.setOnClickListener(view -> renderReading(mockWeightSource.offlineReading()));
    }

    private void renderReading(TrayReading reading) {
        if (reading.kind == ReadingKind.OFFLINE) {
            renderOfflineState();
            return;
        }

        renderConnectedState();
        weightValue.setText(getString(R.string.weight_format, reading.weightGrams));
        lastUpdate.setText(getString(R.string.last_update_format, currentTime()));
        dataSourceLabel.setText(R.string.data_source_mock);

        if (reading.kind == ReadingKind.TRAY_CLEAR) {
            readingState.setText(R.string.reading_state_clear);
            readingDetail.setText(R.string.reading_detail_clear);
        } else {
            readingState.setText(R.string.reading_state_item_detected);
            readingDetail.setText(getString(R.string.reading_detail_item, reading.sampleName));
        }
    }

    private void renderConnectedState() {
        stationStatus.setText(R.string.station_mock);
        stationStatus.setBackgroundResource(R.drawable.status_connected_background);
        stationStatus.setTextColor(getColor(R.color.status_connected_text));
    }

    private void renderOfflineState() {
        stationStatus.setText(R.string.station_offline);
        stationStatus.setBackgroundResource(R.drawable.status_offline_background);
        stationStatus.setTextColor(getColor(R.color.status_offline_text));

        readingState.setText(R.string.reading_state_offline);
        lastUpdate.setText(getString(R.string.last_checked_format, currentTime()));
        readingDetail.setText(R.string.reading_detail_offline);
        dataSourceLabel.setText(R.string.data_source_offline);
    }

    private String currentTime() {
        return timeFormat.format(new Date());
    }

    private enum ReadingKind {
        ITEM_PRESENT,
        TRAY_CLEAR,
        OFFLINE
    }

    private static class TrayReading {
        final ReadingKind kind;
        final int weightGrams;
        final String sampleName;

        private TrayReading(ReadingKind kind, int weightGrams, String sampleName) {
            this.kind = kind;
            this.weightGrams = weightGrams;
            this.sampleName = sampleName;
        }

        static TrayReading itemPresent(String sampleName, int weightGrams) {
            return new TrayReading(ReadingKind.ITEM_PRESENT, weightGrams, sampleName);
        }

        static TrayReading trayClear() {
            return new TrayReading(ReadingKind.TRAY_CLEAR, 0, "");
        }

        static TrayReading offline() {
            return new TrayReading(ReadingKind.OFFLINE, 0, "");
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

        TrayReading offlineReading() {
            return TrayReading.offline();
        }
    }
}
