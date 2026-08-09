package com.coen390.smartexit;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FourPlateBluetoothFlowTest {

    private static final int PLATE_COUNT = 4;
    private static final int REQUIRED_SAMPLES = 3;
    private static final double STABILITY_TOLERANCE_GRAMS = 5.0;
    private static final double EMPTY_WEIGHT_THRESHOLD_GRAMS = 5.0;

    @Test
    public void completeFourPlateSnapshot_updatesItemsOnCorrectPlates() {
        ItemProfile keys = calibratedItem("keys", "Keys", 36.0, 42.0);
        ItemProfile wallet = calibratedItem("wallet", "Wallet", 115.0, 125.0);
        ItemProfile medication = calibratedItem("medication", "Medication", 70.0, 80.0);
        ItemProfile phone = calibratedItem("phone", "Phone", 190.0, 210.0);
        List<ItemProfile> profiles = Arrays.asList(keys, wallet, medication, phone);
        DashboardStateCoordinator coordinator = newCoordinator(profiles);

        assertFalse(addStablePayload(coordinator, "1,39.0,OK").isPresent());
        assertFalse(addStablePayload(coordinator, "2,120.0,OK").isPresent());
        assertFalse(addStablePayload(coordinator, "3,75.0,OK").isPresent());
        Optional<List<TrackedItemState>> update =
                addStablePayload(coordinator, "4,200.0,OK");

        assertTrue(update.isPresent());
        assertItemState(update.get(), keys, TrackedItemStatus.PRESENT, 1);
        assertItemState(update.get(), wallet, TrackedItemStatus.PRESENT, 2);
        assertItemState(update.get(), medication, TrackedItemStatus.PRESENT, 3);
        assertItemState(update.get(), phone, TrackedItemStatus.PRESENT, 4);
    }

    @Test
    public void missingPlateMessage_doesNotChangePreviousState() {
        ItemProfile keys = calibratedItem("keys", "Keys", 36.0, 42.0);
        DashboardStateCoordinator coordinator = newCoordinator(Arrays.asList(keys));

        List<TrackedItemState> initialStates = completeSnapshot(
                coordinator,
                "1,39.0,OK",
                "2,0.0,NO_LOAD",
                "3,0.0,NO_LOAD",
                "4,0.0,NO_LOAD"
        );
        assertItemState(initialStates, keys, TrackedItemStatus.PRESENT, 1);

        assertFalse(addStablePayload(coordinator, "1,0.0,NO_LOAD").isPresent());
        assertFalse(addStablePayload(coordinator, "2,0.0,NO_LOAD").isPresent());
        assertFalse(addStablePayload(coordinator, "3,39.0,OK").isPresent());

        assertItemState(
                coordinator.getStates(),
                keys,
                TrackedItemStatus.PRESENT,
                1
        );
    }

    @Test
    public void malformedPlateMessage_doesNotChangePreviousState() {
        ItemProfile wallet = calibratedItem("wallet", "Wallet", 115.0, 125.0);
        DashboardStateCoordinator coordinator = newCoordinator(Arrays.asList(wallet));

        List<TrackedItemState> initialStates = completeSnapshot(
                coordinator,
                "1,0.0,NO_LOAD",
                "2,120.0,OK",
                "3,0.0,NO_LOAD",
                "4,0.0,NO_LOAD"
        );
        assertItemState(initialStates, wallet, TrackedItemStatus.PRESENT, 2);

        assertFalse(addStablePayload(coordinator, "1,0.0,NO_LOAD").isPresent());
        assertFalse(addStablePayload(coordinator, "2,0.0,NO_LOAD").isPresent());

        BluetoothPayloadParser.ParseResult malformed =
                BluetoothPayloadParser.parse("3,not-a-weight,OK");
        assertFalse(malformed.isValid());
        assertNotNull(malformed.getErrorMessage());

        assertFalse(addStablePayload(coordinator, "4,0.0,NO_LOAD").isPresent());
        assertItemState(
                coordinator.getStates(),
                wallet,
                TrackedItemStatus.PRESENT,
                2
        );

        Optional<List<TrackedItemState>> recoveredUpdate =
                addStablePayload(coordinator, "3,120.0,OK");
        assertTrue(recoveredUpdate.isPresent());
        assertItemState(
                recoveredUpdate.get(),
                wallet,
                TrackedItemStatus.PRESENT,
                3
        );
    }

    private DashboardStateCoordinator newCoordinator(List<ItemProfile> profiles) {
        return new DashboardStateCoordinator(
                profiles,
                PLATE_COUNT,
                REQUIRED_SAMPLES,
                STABILITY_TOLERANCE_GRAMS,
                EMPTY_WEIGHT_THRESHOLD_GRAMS
        );
    }

    private List<TrackedItemState> completeSnapshot(
            DashboardStateCoordinator coordinator,
            String plateOne,
            String plateTwo,
            String plateThree,
            String plateFour
    ) {
        assertFalse(addStablePayload(coordinator, plateOne).isPresent());
        assertFalse(addStablePayload(coordinator, plateTwo).isPresent());
        assertFalse(addStablePayload(coordinator, plateThree).isPresent());
        Optional<List<TrackedItemState>> update =
                addStablePayload(coordinator, plateFour);
        assertTrue("The fourth stable plate should complete the snapshot", update.isPresent());
        return update.get();
    }

    private Optional<List<TrackedItemState>> addStablePayload(
            DashboardStateCoordinator coordinator,
            String payload
    ) {
        BluetoothPayloadParser.ParseResult parsed =
                BluetoothPayloadParser.parse(payload);
        assertTrue(parsed.getErrorMessage(), parsed.isValid());

        BluetoothReading reading = parsed.getReading();
        assertNotNull(reading);
        assertTrue(reading.hasPlateNumber());

        double weightGrams = reading.getStatus() == BluetoothReading.Status.NO_LOAD
                ? 0.0
                : reading.getWeightGrams();
        Optional<List<TrackedItemState>> update = Optional.empty();
        for (int sample = 0; sample < REQUIRED_SAMPLES; sample++) {
            update = coordinator.processReading(
                    new PlateReading(reading.getPlateNumber(), weightGrams)
            );
        }
        return update;
    }

    private ItemProfile calibratedItem(
            String id,
            String name,
            double minimumWeight,
            double maximumWeight
    ) {
        return new ItemProfile(id, name, minimumWeight, maximumWeight);
    }

    private void assertItemState(
            List<TrackedItemState> states,
            ItemProfile expectedItem,
            TrackedItemStatus expectedStatus,
            Integer expectedPlateNumber
    ) {
        TrackedItemState matchingState = null;
        for (TrackedItemState state : states) {
            if (state.getItem().getId().equals(expectedItem.getId())) {
                matchingState = state;
                break;
            }
        }

        assertNotNull("No state found for " + expectedItem.getName(), matchingState);
        assertEquals(expectedStatus, matchingState.getStatus());
        assertEquals(expectedPlateNumber, matchingState.getPlateNumber());
    }
}
