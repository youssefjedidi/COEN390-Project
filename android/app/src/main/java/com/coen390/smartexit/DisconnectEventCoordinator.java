package com.coen390.smartexit;

import java.util.Optional;

final class DisconnectEventCoordinator {

    private boolean connectedSession;

    Optional<DisconnectSnapshot> onStateChanged(
            WeightStationConnection.State state,
            DisconnectSnapshot latestValidSnapshot
    ) {
        if (state == WeightStationConnection.State.CONNECTED) {
            connectedSession = true;
            return Optional.empty();
        }

        if (state != WeightStationConnection.State.DISCONNECTED || !connectedSession) {
            return Optional.empty();
        }

        connectedSession = false;
        if (latestValidSnapshot == null) {
            return Optional.empty();
        }
        return Optional.of(latestValidSnapshot);
    }
}
