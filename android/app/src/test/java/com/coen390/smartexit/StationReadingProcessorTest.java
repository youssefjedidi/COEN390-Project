package com.coen390.smartexit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Optional;

public class StationReadingProcessorTest {
    private ItemProfile keys;
    private ItemProfile wallet;
    private StationReadingProcessor processor;

    @Before
    public void setUp() {
        keys = new ItemProfile("keys", "House keys", 95.0, 105.0);
        wallet = new ItemProfile("wallet", "Wallet", 195.0, 205.0);
        processor = new StationReadingProcessor(
                Arrays.asList(keys, wallet),
                4,
                1,
                5.0,
                5.0
        );
    }

    @Test
    public void completeFourPlateCycleProducesOneDashboardSnapshot() {
        assertFalse(processor.process(reading(1, 100.0f), 1000L).isPresent());
        assertFalse(processor.process(noLoad(2), 1001L).isPresent());
        assertFalse(processor.process(reading(3, 200.0f), 1002L).isPresent());

        Optional<DisconnectSnapshot> update = processor.process(noLoad(4), 1003L);

        assertTrue(update.isPresent());
        assertEquals(1003L, update.get().getTimestampMillis());
        assertEquals(1, item(update.get(), "keys").getPlateNumber().intValue());
        assertEquals(3, item(update.get(), "wallet").getPlateNumber().intValue());
    }

    @Test
    public void unstableAndLegacyReadingsCannotCompleteTheDashboard() {
        assertFalse(processor.process(
                BluetoothReading.forPlate(1, 100.0f, BluetoothReading.Status.UNSTABLE),
                1000L
        ).isPresent());
        assertFalse(processor.process(
                new BluetoothReading(100.0f, BluetoothReading.Status.OK, 1),
                1001L
        ).isPresent());
    }

    @Test
    public void sensorErrorCompletesTheCycleWithoutClaimingItemsAreMissing() {
        assertFalse(processor.process(reading(1, 100.0f), 1000L).isPresent());
        assertFalse(processor.process(noLoad(2), 1001L).isPresent());
        assertFalse(processor.process(
                BluetoothReading.forPlate(3, 0.0f, BluetoothReading.Status.ERROR),
                1002L
        ).isPresent());

        DisconnectSnapshot update = processor.process(noLoad(4), 1003L).get();

        assertEquals(TrackedItemStatus.PRESENT, item(update, "keys").getStatus());
        assertEquals(TrackedItemStatus.UNKNOWN, item(update, "wallet").getStatus());
    }

    @Test
    public void replacingProfilesStartsACompleteFreshCycle() {
        processor.process(reading(1, 100.0f), 1000L);
        processor.replaceProfiles(Arrays.asList(wallet));

        assertFalse(processor.process(noLoad(2), 1001L).isPresent());
        assertFalse(processor.process(reading(3, 200.0f), 1002L).isPresent());
        assertFalse(processor.process(noLoad(4), 1003L).isPresent());
        assertTrue(processor.process(noLoad(1), 1004L).isPresent());
    }

    @Test
    public void reconnectStartsACompleteFreshCycle() {
        assertFalse(processor.process(reading(1, 100.0f), 1000L).isPresent());
        assertFalse(processor.process(noLoad(2), 1001L).isPresent());
        assertFalse(processor.process(reading(3, 200.0f), 1002L).isPresent());

        processor.resetCycle();

        assertFalse(processor.process(noLoad(4), 1003L).isPresent());
        assertFalse(processor.process(reading(1, 100.0f), 1004L).isPresent());
        assertFalse(processor.process(noLoad(2), 1005L).isPresent());
        assertTrue(processor.process(reading(3, 200.0f), 1006L).isPresent());
    }

    private BluetoothReading reading(int plateNumber, float grams) {
        return BluetoothReading.forPlate(plateNumber, grams, BluetoothReading.Status.OK);
    }

    private BluetoothReading noLoad(int plateNumber) {
        return BluetoothReading.forPlate(
                plateNumber,
                0.0f,
                BluetoothReading.Status.NO_LOAD
        );
    }

    private DisconnectSnapshot.ItemEntry item(DisconnectSnapshot snapshot, String id) {
        for (DisconnectSnapshot.ItemEntry item : snapshot.getItems()) {
            if (item.getItemId().equals(id)) {
                return item;
            }
        }
        throw new AssertionError("missing item " + id);
    }
}
