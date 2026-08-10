package com.coen390.smartexit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ItemStateTracker {

    private final int plateCount;
    private final List<ItemProfile> trackedItems;
    private List<TrackedItemState> currentItemStates;

    ItemStateTracker(List<ItemProfile> items, int plateCount) {
        if (items == null) {
            throw new IllegalArgumentException("item state tracking requires an item list");
        }
        if (plateCount <= 0) {
            throw new IllegalArgumentException(
                    "plateCount must be positive, but was " + plateCount
            );
        }

        List<ItemProfile> validatedItems = new ArrayList<>();
        List<TrackedItemState> initialStates = new ArrayList<>();

        for (ItemProfile item : items) {
            if (item == null) {
                throw new IllegalArgumentException("tracked items cannot contain null");
            }
            if (findItem(validatedItems, item.getId()) != null) {
                throw new IllegalArgumentException(
                        "tracked items contain duplicate id " + item.getId()
                );
            }
            validatedItems.add(item);
            initialStates.add(TrackedItemState.unknown(item));
        }

        this.plateCount = plateCount;
        trackedItems = readOnlyCopy(validatedItems);
        currentItemStates = readOnlyCopy(initialStates);
    }

    List<TrackedItemState> getStates() {
        return currentItemStates;
    }

    List<TrackedItemState> update(List<RecognitionResult> plateSnapshot) {
        validateSnapshot(plateSnapshot);

        List<TrackedItemState> updatedItemStates = new ArrayList<>();
        for (ItemProfile item : trackedItems) {
            updatedItemStates.add(resolveState(item, plateSnapshot));
        }

        currentItemStates = readOnlyCopy(updatedItemStates);
        return currentItemStates;
    }

    private TrackedItemState resolveState(
            ItemProfile item,
            List<RecognitionResult> plateSnapshot
    ) {
        Integer matchedPlateNumber = null;
        boolean matchedOnMoreThanOnePlate = false;
        boolean appearsInAmbiguousResult = false;
        boolean sensorUnavailable = false;

        for (RecognitionResult result : plateSnapshot) {
            if (result.getStatus() == RecognitionStatus.MATCHED
                    && sameItem(item, result.getCandidates().get(0))) {
                if (matchedPlateNumber == null) {
                    matchedPlateNumber = result.getReading().getPlateNumber();
                } else {
                    matchedOnMoreThanOnePlate = true;
                }
            } else if (result.getStatus() == RecognitionStatus.AMBIGUOUS
                    && containsItem(result.getCandidates(), item)) {
                appearsInAmbiguousResult = true;
            } else if (result.getStatus() == RecognitionStatus.UNAVAILABLE) {
                sensorUnavailable = true;
            }
        }

        if (matchedPlateNumber != null && !matchedOnMoreThanOnePlate) {
            return TrackedItemState.present(item, matchedPlateNumber);
        }
        if (matchedOnMoreThanOnePlate || appearsInAmbiguousResult || sensorUnavailable) {
            return TrackedItemState.unknown(item);
        }
        return TrackedItemState.missing(item);
    }

    private void validateSnapshot(List<RecognitionResult> plateSnapshot) {
        if (plateSnapshot == null) {
            throw new IllegalArgumentException("item state tracking requires a plate snapshot");
        }
        if (plateSnapshot.size() != plateCount) {
            throw new IllegalArgumentException(
                    "plate snapshot must contain " + plateCount
                            + " results, but contained " + plateSnapshot.size()
            );
        }

        boolean[] seenPlates = new boolean[plateCount + 1];
        for (RecognitionResult result : plateSnapshot) {
            if (result == null) {
                throw new IllegalArgumentException("plate snapshot cannot contain null");
            }

            int plateNumber = result.getReading().getPlateNumber();
            if (plateNumber < 1 || plateNumber > plateCount) {
                throw new IllegalArgumentException(
                        "plateNumber must be between 1 and " + plateCount
                                + ", but was " + plateNumber
                );
            }
            if (seenPlates[plateNumber]) {
                throw new IllegalArgumentException(
                        "plate snapshot contains plate " + plateNumber + " more than once"
                );
            }
            seenPlates[plateNumber] = true;

            for (ItemProfile candidate : result.getCandidates()) {
                requireTracked(candidate);
            }
        }
    }

    private void requireTracked(ItemProfile item) {
        if (findTrackedItem(item.getId()) == null) {
            throw new IllegalArgumentException(
                    "recognition result contains untracked item " + item.getName()
            );
        }
    }

    private ItemProfile findTrackedItem(String itemId) {
        return findItem(trackedItems, itemId);
    }

    private ItemProfile findItem(List<ItemProfile> items, String itemId) {
        for (ItemProfile item : items) {
            if (item.getId().equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    private boolean containsItem(List<ItemProfile> candidates, ItemProfile item) {
        for (ItemProfile candidate : candidates) {
            if (sameItem(candidate, item)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameItem(ItemProfile first, ItemProfile second) {
        return first.getId().equals(second.getId());
    }

    private <T> List<T> readOnlyCopy(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
