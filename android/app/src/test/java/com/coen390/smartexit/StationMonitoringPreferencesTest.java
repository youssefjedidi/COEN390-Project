package com.coen390.smartexit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class StationMonitoringPreferencesTest {
    private FakeStore store;
    private StationMonitoringPreferences preferences;

    @Before
    public void setUp() {
        store = new FakeStore();
        preferences = new StationMonitoringPreferences(store);
    }

    @Test
    public void newPreferencesHaveNoStationAndMonitoringIsDisabled() {
        assertNull(preferences.getKnownStationAddress());
        assertFalse(preferences.isMonitoringEnabled());
    }

    @Test
    public void validStationAddressCanBeRemembered() {
        preferences.rememberStation("AA:BB:CC:DD:EE:FF");

        assertEquals("AA:BB:CC:DD:EE:FF", preferences.getKnownStationAddress());
    }

    @Test
    public void monitoringChoiceCanBeRestored() {
        preferences.setMonitoringEnabled(true);
        assertTrue(preferences.isMonitoringEnabled());

        preferences.setMonitoringEnabled(false);
        assertFalse(preferences.isMonitoringEnabled());
    }

    @Test
    public void corruptSavedAddressFallsBackToManualDiscovery() {
        store.putString("known_station_address", "not-an-address");

        assertNull(preferences.getKnownStationAddress());
    }

    @Test
    public void invalidAddressCannotBeSavedByTheApp() {
        assertThrows(
                IllegalArgumentException.class,
                () -> preferences.rememberStation("SmartExit-Station")
        );
    }

    private static final class FakeStore implements StationMonitoringPreferences.Store {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public String getString(String key) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : null;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (boolean) value : defaultValue;
        }

        @Override
        public void putString(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void putBoolean(String key, boolean value) {
            values.put(key, value);
        }
    }
}
