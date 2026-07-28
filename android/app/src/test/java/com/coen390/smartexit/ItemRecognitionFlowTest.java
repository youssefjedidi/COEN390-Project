package com.coen390.smartexit;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ItemRecognitionFlowTest {

    private static final int PLATE_COUNT = 4;
    private static final int REQUIRED_SAMPLES = 3;
    private static final double STABILITY_TOLERANCE_GRAMS = 5.0;
    private static final double EMPTY_WEIGHT_THRESHOLD_GRAMS = 1.0;

    @Test
    public void itemCanBePlacedMovedAndRemovedThroughCompleteRecognitionFlow() {
        ItemProfile keys = new ItemProfile(
                "keys",
                "Keys",
                36.0,
                42.0
        );

        List<ItemProfile> savedProfiles = Collections.singletonList(keys);

        StableReadingFilter filter = new StableReadingFilter(
                PLATE_COUNT,
                REQUIRED_SAMPLES,
                STABILITY_TOLERANCE_GRAMS
        );

        ItemRecognizer recognizer = new ItemRecognizer();
        ItemStateTracker tracker = new ItemStateTracker(
                savedProfiles,
                PLATE_COUNT
        );

        /* Stage 1: Keys are placed on Plate 1. */
        List<RecognitionResult> placedSnapshot = Arrays.asList(
                stableResult(filter, recognizer, savedProfiles, 1, 39.0),
                stableResult(filter, recognizer, savedProfiles, 2, 0.0),
                stableResult(filter, recognizer, savedProfiles, 3, 0.0),
                stableResult(filter, recognizer, savedProfiles, 4, 0.0)
        );

        List<TrackedItemState> placedStates = tracker.update(placedSnapshot);

        assertItemState(
                placedStates,
                keys,
                TrackedItemStatus.PRESENT,
                1
        );

        /* Stage 2: Keys are removed from Plate 1 and placed on Plate 3. */
        List<RecognitionResult> movedSnapshot = Arrays.asList(
                stableResult(filter, recognizer, savedProfiles, 1, 0.0),
                stableResult(filter, recognizer, savedProfiles, 2, 0.0),
                stableResult(filter, recognizer, savedProfiles, 3, 40.0),
                stableResult(filter, recognizer, savedProfiles, 4, 0.0)
        );

        List<TrackedItemState> movedStates = tracker.update(movedSnapshot);

        assertItemState(
                movedStates,
                keys,
                TrackedItemStatus.PRESENT,
                3
        );

        /* Stage 3: Keys are removed and all four plates become empty. */
        List<RecognitionResult> removedSnapshot = Arrays.asList(
                stableResult(filter, recognizer, savedProfiles, 1, 0.0),
                stableResult(filter, recognizer, savedProfiles, 2, 0.0),
                stableResult(filter, recognizer, savedProfiles, 3, 0.0),
                stableResult(filter, recognizer, savedProfiles, 4, 0.0)
        );

        List<TrackedItemState> removedStates = tracker.update(removedSnapshot);

        assertItemState(
                removedStates,
                keys,
                TrackedItemStatus.MISSING,
                null
        );
    }

    private RecognitionResult stableResult(
            StableReadingFilter filter,
            ItemRecognizer recognizer,
            List<ItemProfile> savedProfiles,
            int plateNumber,
            double weightGrams
    ) {
        // Feed enough matching samples for the filter to emit a stable reading.
        Optional<PlateReading> stableReading = Optional.empty();

        for (int sample = 0; sample < REQUIRED_SAMPLES; sample++) {
            stableReading = filter.add(
                    new PlateReading(plateNumber, weightGrams)
            );
        }

        assertTrue(
                "A stable reading should be produced for Plate " + plateNumber,
                stableReading.isPresent()
        );

        PlateReading reading = stableReading.get();

        // Near-zero readings represent empty plates; all others go through recognition.
        if (Math.abs(reading.getWeightGrams())
                <= EMPTY_WEIGHT_THRESHOLD_GRAMS) {
            return RecognitionResult.empty(reading);
        }

        return recognizer.recognize(reading, savedProfiles);
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

        assertNotNull(
                "The tracked item state should exist",
                matchingState
        );

        assertEquals(expectedStatus, matchingState.getStatus());
        assertEquals(expectedPlateNumber, matchingState.getPlateNumber());
    }
}
