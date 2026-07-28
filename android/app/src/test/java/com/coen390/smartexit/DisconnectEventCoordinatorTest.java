package com.coen390.smartexit;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DisconnectEventCoordinatorTest {

    private final ItemProfile keys = new ItemProfile("keys", "Keys", 35.0, 45.0);
    private final ItemProfile wallet = new ItemProfile("wallet", "Wallet", 135.0, 155.0);

    @Test
    public void createsOneSnapshotWhenAConnectedStationDisconnects() {
        DisconnectEventCoordinator coordinator = new DisconnectEventCoordinator();
        List<TrackedItemState> states = Arrays.asList(
                TrackedItemState.present(keys, 2),
                TrackedItemState.missing(wallet)
        );

        coordinator.onStateChanged(
                WeightStationConnection.State.CONNECTED,
                null,
                100L
        );
        Optional<DisconnectSnapshot> firstDisconnect = coordinator.onStateChanged(
                WeightStationConnection.State.DISCONNECTED,
                states,
                200L
        );
        Optional<DisconnectSnapshot> repeatedDisconnect = coordinator.onStateChanged(
                WeightStationConnection.State.DISCONNECTED,
                states,
                300L
        );

        assertTrue(firstDisconnect.isPresent());
        assertEquals(200L, firstDisconnect.get().getTimestampMillis());
        assertEquals(Collections.singletonList("Keys"), firstDisconnect.get().getPresentItemNames());
        assertFalse(repeatedDisconnect.isPresent());
    }

    @Test
    public void ignoresDisconnectWithoutAConnectedSessionOrValidState() {
        DisconnectEventCoordinator coordinator = new DisconnectEventCoordinator();

        assertFalse(
                coordinator.onStateChanged(
                        WeightStationConnection.State.DISCONNECTED,
                        Collections.emptyList(),
                        100L
                ).isPresent()
        );

        coordinator.onStateChanged(WeightStationConnection.State.CONNECTED, null, 200L);

        assertFalse(
                coordinator.onStateChanged(
                        WeightStationConnection.State.DISCONNECTED,
                        null,
                        300L
                ).isPresent()
        );
    }

    @Test
    public void reconnectingArmsTheNextDisconnect() {
        DisconnectEventCoordinator coordinator = new DisconnectEventCoordinator();
        List<TrackedItemState> states =
                Collections.singletonList(TrackedItemState.present(keys, 1));

        coordinator.onStateChanged(WeightStationConnection.State.CONNECTED, null, 100L);
        assertTrue(
                coordinator.onStateChanged(
                        WeightStationConnection.State.DISCONNECTED,
                        states,
                        200L
                ).isPresent()
        );

        coordinator.onStateChanged(WeightStationConnection.State.CONNECTED, null, 300L);
        assertTrue(
                coordinator.onStateChanged(
                        WeightStationConnection.State.DISCONNECTED,
                        states,
                        400L
                ).isPresent()
        );
    }

    @Test
    public void keepsTheSnapshotWhenNoItemsArePresent() {
        DisconnectEventCoordinator coordinator = new DisconnectEventCoordinator();
        List<TrackedItemState> states =
                Collections.singletonList(TrackedItemState.missing(keys));

        coordinator.onStateChanged(WeightStationConnection.State.CONNECTED, null, 100L);
        DisconnectSnapshot snapshot = coordinator.onStateChanged(
                WeightStationConnection.State.DISCONNECTED,
                states,
                200L
        ).get();

        assertTrue(snapshot.getPresentItemNames().isEmpty());
    }
}
