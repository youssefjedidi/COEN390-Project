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
    private final boolean[] ambiguityHandled;
    private List<RecognitionResult> completedSnapshot = new ArrayList<>();
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
        ambiguityHandled = new boolean[plateCount + 1];
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

        completedSnapshot = currentSnapshot();
        for (int index = 1; index <= plateCount; index++) {
            ambiguityHandled[index] = false;
        }
        List<TrackedItemState> updatedStates =
                itemStateTracker.update(completedSnapshot);
        beginNextCycle();
        return Optional.of(updatedStates);
    }

    List<RecognitionResult> getPendingAmbiguousResults() {
        List<RecognitionResult> ambiguousResults = new ArrayList<>();
        for (RecognitionResult result : completedSnapshot) {
            int plateNumber = result.getReading().getPlateNumber();
            if (result.getStatus() == RecognitionStatus.AMBIGUOUS
                    && !ambiguityHandled[plateNumber]) {
                ambiguousResults.add(result);
            }
        }
        return ambiguousResults;
    }

    List<TrackedItemState> confirmAmbiguousMatch(int plateNumber, String itemId) {
        RecognitionResult ambiguousResult = requireAmbiguousResult(plateNumber);
        ItemProfile selectedItem = null;
        for (ItemProfile candidate : ambiguousResult.getCandidates()) {
            if (candidate.getId().equals(itemId)) {
                selectedItem = candidate;
                break;
            }
        }
        if (selectedItem == null) {
            throw new IllegalArgumentException(
                    "item " + itemId + " is not a candidate for plate " + plateNumber
            );
        }

        replaceCompletedResult(
                plateNumber,
                RecognitionResult.matched(ambiguousResult.getReading(), selectedItem)
        );
        return itemStateTracker.update(completedSnapshot);
    }

    List<TrackedItemState> leaveAmbiguousMatchUnresolved(int plateNumber) {
        requireAmbiguousResult(plateNumber);
        ambiguityHandled[plateNumber] = true;
        return itemStateTracker.update(completedSnapshot);
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

    private RecognitionResult requireAmbiguousResult(int plateNumber) {
        if (plateNumber < 1 || plateNumber > plateCount) {
            throw new IllegalArgumentException(
                    "plateNumber must be between 1 and " + plateCount
            );
        }
        if (completedSnapshot.size() != plateCount) {
            throw new IllegalStateException("no completed plate snapshot is available");
        }

        RecognitionResult result = completedSnapshot.get(plateNumber - 1);
        if (result.getStatus() != RecognitionStatus.AMBIGUOUS) {
            throw new IllegalStateException(
                    "plate " + plateNumber + " does not have an ambiguous reading"
            );
        }
        return result;
    }

    private void replaceCompletedResult(int plateNumber, RecognitionResult replacement) {
        List<RecognitionResult> updatedSnapshot = new ArrayList<>(completedSnapshot);
        updatedSnapshot.set(plateNumber - 1, replacement);
        completedSnapshot = updatedSnapshot;
    }

    private void beginNextCycle() {
        for (int plateNumber = 1; plateNumber <= plateCount; plateNumber++) {
            platesUpdatedThisCycle[plateNumber] = false;
        }
        updatedPlateCount = 0;
    }
}
