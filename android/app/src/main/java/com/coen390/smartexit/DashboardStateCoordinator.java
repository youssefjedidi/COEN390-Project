package com.coen390.smartexit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class DashboardStateCoordinator {

    private final int plateCount;
    private final double emptyWeightThresholdGrams;
    private final List<ItemProfile> savedProfiles;
    private final StableReadingFilter stableReadingFilter;
    private final ItemRecognizer itemRecognizer;
    private final ItemStateTracker itemStateTracker;
    private final RecognitionResult[] latestPlateResults;
    private final boolean[] platesUpdatedThisCycle;
    private int updatedPlateCount;

    DashboardStateCoordinator(
            List<ItemProfile> savedProfiles,
            int plateCount,
            int requiredSamples,
            double stabilityToleranceGrams,
            double emptyWeightThresholdGrams
    ) {
        if (savedProfiles == null) {
            throw new IllegalArgumentException(
                    "dashboard state requires saved item profiles"
            );
        }
        if (!Double.isFinite(emptyWeightThresholdGrams)
                || emptyWeightThresholdGrams < 0.0) {
            throw new IllegalArgumentException(
                    "emptyWeightThresholdGrams must be finite and non-negative"
            );
        }

        this.plateCount = plateCount;
        this.emptyWeightThresholdGrams = emptyWeightThresholdGrams;
        this.savedProfiles = new ArrayList<>(savedProfiles);
        stableReadingFilter = new StableReadingFilter(
                plateCount,
                requiredSamples,
                stabilityToleranceGrams
        );
        itemRecognizer = new ItemRecognizer();
        itemStateTracker = new ItemStateTracker(savedProfiles, plateCount);
        latestPlateResults = new RecognitionResult[plateCount + 1];
        platesUpdatedThisCycle = new boolean[plateCount + 1];
    }

    List<TrackedItemState> getStates() {
        return itemStateTracker.getStates();
    }

    Optional<List<TrackedItemState>> processReading(PlateReading reading) {
        Optional<PlateReading> stableReading = stableReadingFilter.add(reading);
        if (!stableReading.isPresent()) {
            return Optional.empty();
        }

        RecognitionResult result = recognize(stableReading.get());
        int plateNumber = result.getReading().getPlateNumber();

        if (!platesUpdatedThisCycle[plateNumber]) {
            platesUpdatedThisCycle[plateNumber] = true;
            updatedPlateCount++;
        }
        latestPlateResults[plateNumber] = result;

        if (updatedPlateCount < plateCount) {
            return Optional.empty();
        }

        List<TrackedItemState> updatedStates =
                itemStateTracker.update(currentSnapshot());
        beginNextCycle();
        return Optional.of(updatedStates);
    }

    private RecognitionResult recognize(PlateReading reading) {
        if (Math.abs(reading.getWeightGrams()) <= emptyWeightThresholdGrams) {
            return RecognitionResult.empty(reading);
        }
        return itemRecognizer.recognize(reading, savedProfiles);
    }

    private List<RecognitionResult> currentSnapshot() {
        List<RecognitionResult> snapshot = new ArrayList<>();
        for (int plateNumber = 1; plateNumber <= plateCount; plateNumber++) {
            snapshot.add(latestPlateResults[plateNumber]);
        }
        return snapshot;
    }

    private void beginNextCycle() {
        for (int plateNumber = 1; plateNumber <= plateCount; plateNumber++) {
            platesUpdatedThisCycle[plateNumber] = false;
        }
        updatedPlateCount = 0;
    }
}
