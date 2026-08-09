package com.coen390.smartexit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Processes raw station readings independently of whichever Activity is visible. */
final class StationReadingProcessor {
    private final int plateCount;
    private final int requiredSamples;
    private final double stabilityToleranceGrams;
    private final double emptyWeightThresholdGrams;
    private DashboardStateCoordinator dashboardCoordinator;

    StationReadingProcessor(
            List<ItemProfile> profiles,
            int plateCount,
            int requiredSamples,
            double stabilityToleranceGrams,
            double emptyWeightThresholdGrams
    ) {
        this.plateCount = plateCount;
        this.requiredSamples = requiredSamples;
        this.stabilityToleranceGrams = stabilityToleranceGrams;
        this.emptyWeightThresholdGrams = emptyWeightThresholdGrams;
        replaceProfiles(profiles);
    }

    synchronized void replaceProfiles(List<ItemProfile> profiles) {
        dashboardCoordinator = new DashboardStateCoordinator(
                new ArrayList<>(profiles),
                plateCount,
                requiredSamples,
                stabilityToleranceGrams,
                emptyWeightThresholdGrams
        );
    }

    synchronized Optional<DisconnectSnapshot> process(
            BluetoothReading reading,
            long timestampMillis
    ) {
        if (!reading.hasPlateNumber()
                || reading.getStatus() == BluetoothReading.Status.ERROR
                || reading.getStatus() == BluetoothReading.Status.UNSTABLE) {
            return Optional.empty();
        }

        double grams = reading.getStatus() == BluetoothReading.Status.NO_LOAD
                ? 0.0
                : reading.getWeightGrams();
        return dashboardCoordinator
                .processReading(new PlateReading(reading.getPlateNumber(), grams))
                .map(states -> DisconnectSnapshot.from(timestampMillis, states));
    }

    synchronized List<RecognitionResult> getPendingAmbiguousResults() {
        return dashboardCoordinator.getPendingAmbiguousResults();
    }

    synchronized DisconnectSnapshot confirmAmbiguousMatch(
            int plateNumber,
            String itemId,
            long timestampMillis
    ) {
        return DisconnectSnapshot.from(
                timestampMillis,
                dashboardCoordinator.confirmAmbiguousMatch(plateNumber, itemId)
        );
    }

    synchronized DisconnectSnapshot leaveAmbiguousMatchUnresolved(
            int plateNumber,
            long timestampMillis
    ) {
        return DisconnectSnapshot.from(
                timestampMillis,
                dashboardCoordinator.leaveAmbiguousMatchUnresolved(plateNumber)
        );
    }
}
