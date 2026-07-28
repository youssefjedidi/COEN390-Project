package com.coen390.smartexit;

import java.util.List;
import java.util.Optional;

final class DisconnectEventCoordinator {

    private boolean connectedSession;

    Optional<DisconnectSnapshot> onStateChanged(
            WeightStationConnection.State state,
            List<TrackedItemState> latestValidStates,
            long timestampMillis
    ) {
        if (state == WeightStationConnection.State.CONNECTED) {
            connectedSession = true;
            return Optional.empty();
        }

        if (state != WeightStationConnection.State.DISCONNECTED || !connectedSession) {
            return Optional.empty();
        }

        connectedSession = false;
        if (latestValidStates == null) {
            return Optional.empty();
        }
        return Optional.of(DisconnectSnapshot.from(timestampMillis, latestValidStates));
    }
}
