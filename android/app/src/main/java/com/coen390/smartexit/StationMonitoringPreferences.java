package com.coen390.smartexit;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.regex.Pattern;

final class StationMonitoringPreferences {
    private static final String PREFERENCES_NAME = "station_monitoring_prefs";
    private static final String KEY_STATION_ADDRESS = "known_station_address";
    private static final String KEY_MONITORING_ENABLED = "monitoring_enabled";
    private static final Pattern BLUETOOTH_ADDRESS = Pattern.compile(
            "(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"
    );

    interface Store {
        String getString(String key);

        boolean getBoolean(String key, boolean defaultValue);

        void putString(String key, String value);

        void putBoolean(String key, boolean value);
    }

    private final Store store;

    StationMonitoringPreferences(Context context) {
        this(new SharedPreferencesStore(
                context.getApplicationContext().getSharedPreferences(
                        PREFERENCES_NAME,
                        Context.MODE_PRIVATE
                )
        ));
    }

    StationMonitoringPreferences(Store store) {
        this.store = store;
    }

    String getKnownStationAddress() {
        String address = store.getString(KEY_STATION_ADDRESS);
        return isValidAddress(address) ? address : null;
    }

    void rememberStation(String address) {
        if (!isValidAddress(address)) {
            throw new IllegalArgumentException("station address must be a Bluetooth MAC address");
        }
        store.putString(KEY_STATION_ADDRESS, address);
    }

    boolean isMonitoringEnabled() {
        return store.getBoolean(KEY_MONITORING_ENABLED, false);
    }

    void setMonitoringEnabled(boolean enabled) {
        store.putBoolean(KEY_MONITORING_ENABLED, enabled);
    }

    private boolean isValidAddress(String address) {
        return address != null && BLUETOOTH_ADDRESS.matcher(address).matches();
    }

    private static final class SharedPreferencesStore implements Store {
        private final SharedPreferences preferences;

        private SharedPreferencesStore(SharedPreferences preferences) {
            this.preferences = preferences;
        }

        @Override
        public String getString(String key) {
            return preferences.getString(key, null);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return preferences.getBoolean(key, defaultValue);
        }

        @Override
        public void putString(String key, String value) {
            preferences.edit().putString(key, value).apply();
        }

        @Override
        public void putBoolean(String key, boolean value) {
            preferences.edit().putBoolean(key, value).apply();
        }
    }
}
