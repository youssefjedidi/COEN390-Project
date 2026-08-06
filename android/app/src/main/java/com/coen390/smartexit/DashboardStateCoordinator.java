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
    private final AmbiguityChoice[] ambiguityChoices;
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
        ambiguityChoices = new AmbiguityChoice[plateCount + 1];
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
        applyRememberedAmbiguityChoices();
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
                    && ambiguityChoices[plateNumber] == null) {
                List<ItemProfile> unclaimedCandidates = unclaimedCandidates(result);
                if (unclaimedCandidates.size() >= 2) {
                    ambiguousResults.add(
                            RecognitionResult.ambiguous(
                                    result.getReading(),
                                    unclaimedCandidates
                            )
                    );
                }
            }
        }
        return ambiguousResults;
    }

    List<TrackedItemState> confirmAmbiguousMatch(int plateNumber, String itemId) {
        RecognitionResult ambiguousResult = requireAmbiguousResult(plateNumber);
        List<ItemProfile> unclaimedCandidates = unclaimedCandidates(ambiguousResult);
        ItemProfile selectedItem = findItem(unclaimedCandidates, itemId);
        if (selectedItem == null) {
            throw new IllegalArgumentException(
                    "item " + itemId + " is not available for plate " + plateNumber
            );
        }

        replaceCompletedResult(
                plateNumber,
                RecognitionResult.matched(ambiguousResult.getReading(), selectedItem)
        );
        ambiguityChoices[plateNumber] =
                AmbiguityChoice.confirmed(candidateKey(ambiguousResult), itemId);
        resolveForcedMatches();
        return itemStateTracker.update(completedSnapshot);
    }

    List<TrackedItemState> leaveAmbiguousMatchUnresolved(int plateNumber) {
        RecognitionResult result = requireAmbiguousResult(plateNumber);
        ambiguityChoices[plateNumber] =
                AmbiguityChoice.unresolved(candidateKey(result));
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

    private void applyRememberedAmbiguityChoices() {
        for (int index = 0; index < completedSnapshot.size(); index++) {
            RecognitionResult result = completedSnapshot.get(index);
            int plateNumber = result.getReading().getPlateNumber();
            if (result.getStatus() != RecognitionStatus.AMBIGUOUS) {
                ambiguityChoices[plateNumber] = null;
                continue;
            }

            AmbiguityChoice choice = ambiguityChoices[plateNumber];
            String currentKey = candidateKey(result);
            if (choice == null) {
                continue;
            }
            if (!choice.candidateKey.equals(currentKey)) {
                ambiguityChoices[plateNumber] = null;
                continue;
            }
            if (choice.selectedItemId == null) {
                continue;
            }

            ItemProfile selectedItem = findCandidate(result, choice.selectedItemId);
            if (selectedItem == null || isMatchedOnAnotherPlate(selectedItem, plateNumber)) {
                ambiguityChoices[plateNumber] = null;
                continue;
            }
            completedSnapshot.set(
                    index,
                    RecognitionResult.matched(result.getReading(), selectedItem)
            );
        }
        resolveForcedMatches();
    }

    private void resolveForcedMatches() {
        // Each new match can leave a single unclaimed candidate on another plate.
        while (resolveNextForcedMatch()) {
        }
    }

    private boolean resolveNextForcedMatch() {
        for (int index = 0; index < completedSnapshot.size(); index++) {
            RecognitionResult result = completedSnapshot.get(index);
            if (result.getStatus() != RecognitionStatus.AMBIGUOUS) {
                continue;
            }

            List<ItemProfile> candidates = unclaimedCandidates(result);
            int plateNumber = result.getReading().getPlateNumber();
            if (candidates.isEmpty()) {
                completedSnapshot.set(index, RecognitionResult.unknown(result.getReading()));
                ambiguityChoices[plateNumber] = null;
                return true;
            }
            if (candidates.size() == 1) {
                ItemProfile item = candidates.get(0);
                completedSnapshot.set(
                        index,
                        RecognitionResult.matched(result.getReading(), item)
                );
                ambiguityChoices[plateNumber] =
                        AmbiguityChoice.confirmed(candidateKey(result), item.getId());
                return true;
            }
        }
        return false;
    }

    private List<ItemProfile> unclaimedCandidates(RecognitionResult result) {
        List<ItemProfile> candidates = new ArrayList<>();
        int plateNumber = result.getReading().getPlateNumber();
        for (ItemProfile candidate : result.getCandidates()) {
            if (!isMatchedOnAnotherPlate(candidate, plateNumber)) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private boolean isMatchedOnAnotherPlate(ItemProfile item, int plateNumber) {
        for (RecognitionResult result : completedSnapshot) {
            if (result.getReading().getPlateNumber() == plateNumber
                    || result.getStatus() != RecognitionStatus.MATCHED) {
                continue;
            }
            if (result.getCandidates().get(0).getId().equals(item.getId())) {
                return true;
            }
        }
        return false;
    }

    private String candidateKey(RecognitionResult result) {
        StringBuilder key = new StringBuilder();
        for (ItemProfile candidate : result.getCandidates()) {
            if (key.length() > 0) {
                key.append('|');
            }
            key.append(candidate.getId());
        }
        return key.toString();
    }

    private ItemProfile findCandidate(RecognitionResult result, String itemId) {
        return findItem(result.getCandidates(), itemId);
    }

    private ItemProfile findItem(List<ItemProfile> items, String itemId) {
        for (ItemProfile item : items) {
            if (item.getId().equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    private void beginNextCycle() {
        for (int plateNumber = 1; plateNumber <= plateCount; plateNumber++) {
            platesUpdatedThisCycle[plateNumber] = false;
        }
        updatedPlateCount = 0;
    }

    private static final class AmbiguityChoice {
        private final String candidateKey;
        private final String selectedItemId;

        private AmbiguityChoice(String candidateKey, String selectedItemId) {
            this.candidateKey = candidateKey;
            this.selectedItemId = selectedItemId;
        }

        private static AmbiguityChoice confirmed(String candidateKey, String itemId) {
            return new AmbiguityChoice(candidateKey, itemId);
        }

        private static AmbiguityChoice unresolved(String candidateKey) {
            return new AmbiguityChoice(candidateKey, null);
        }
    }
}
