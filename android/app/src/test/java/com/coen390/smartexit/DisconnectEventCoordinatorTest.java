package com.coen390.smartexit;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
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
        DisconnectSnapshot latestSnapshot = DisconnectSnapshot.from(
                150L,
                Arrays.asList(
                        TrackedItemState.present(keys, 2),
                        TrackedItemState.missing(wallet)
                )
        );

        coordinator.onStateChanged(
                WeightStationConnection.State.CONNECTED,
                null
        );
        Optional<DisconnectSnapshot> firstDisconnect = coordinator.onStateChanged(
                WeightStationConnection.State.DISCONNECTED,
                latestSnapshot
        );
        Optional<DisconnectSnapshot> repeatedDisconnect = coordinator.onStateChanged(
                WeightStationConnection.State.DISCONNECTED,
                latestSnapshot
        );

        assertTrue(firstDisconnect.isPresent());
        assertEquals(150L, firstDisconnect.get().getTimestampMillis());
        assertEquals(Collections.singletonList("Keys"), firstDisconnect.get().getPresentItemNames());
        assertFalse(repeatedDisconnect.isPresent());
    }

    @Test
    public void ignoresDisconnectWithoutAConnectedSessionOrValidState() {
        DisconnectEventCoordinator coordinator = new DisconnectEventCoordinator();

        assertFalse(
                coordinator.onStateChanged(
                        WeightStationConnection.State.DISCONNECTED,
                        DisconnectSnapshot.from(100L, Collections.emptyList())
                ).isPresent()
        );

        coordinator.onStateChanged(WeightStationConnection.State.CONNECTED, null);

        assertFalse(
                coordinator.onStateChanged(
                        WeightStationConnection.State.DISCONNECTED,
                        null
                ).isPresent()
        );
    }

    @Test
    public void reconnectingArmsTheNextDisconnect() {
        DisconnectEventCoordinator coordinator = new DisconnectEventCoordinator();
        DisconnectSnapshot latestSnapshot = DisconnectSnapshot.from(
                100L,
                Collections.singletonList(TrackedItemState.present(keys, 1))
        );

        coordinator.onStateChanged(WeightStationConnection.State.CONNECTED, null);
        assertTrue(
                coordinator.onStateChanged(
                        WeightStationConnection.State.DISCONNECTED,
                        latestSnapshot
                ).isPresent()
        );

        coordinator.onStateChanged(WeightStationConnection.State.CONNECTED, null);
        assertTrue(
                coordinator.onStateChanged(
                        WeightStationConnection.State.DISCONNECTED,
                        latestSnapshot
                ).isPresent()
        );
    }

    @Test
    public void keepsTheSnapshotWhenNoItemsArePresent() {
        DisconnectEventCoordinator coordinator = new DisconnectEventCoordinator();
        DisconnectSnapshot latestSnapshot = DisconnectSnapshot.from(
                100L,
                Collections.singletonList(TrackedItemState.missing(keys))
        );

        coordinator.onStateChanged(WeightStationConnection.State.CONNECTED, null);
        DisconnectSnapshot snapshot = coordinator.onStateChanged(
                WeightStationConnection.State.DISCONNECTED,
                latestSnapshot
        ).get();

        assertTrue(snapshot.getPresentItemNames().isEmpty());
    }

    @Test
    public void snapshotKeepsEveryPresentItemForOneNotification() {
        DisconnectEventCoordinator coordinator = new DisconnectEventCoordinator();
        DisconnectSnapshot latestSnapshot = DisconnectSnapshot.from(
                100L,
                Arrays.asList(
                        TrackedItemState.present(keys, 1),
                        TrackedItemState.present(wallet, 4)
                )
        );

        coordinator.onStateChanged(WeightStationConnection.State.CONNECTED, null);
        DisconnectSnapshot snapshot = coordinator.onStateChanged(
                WeightStationConnection.State.DISCONNECTED,
                latestSnapshot
        ).get();

        assertEquals(Arrays.asList("Keys", "Wallet"), snapshot.getPresentItemNames());
    }
}
