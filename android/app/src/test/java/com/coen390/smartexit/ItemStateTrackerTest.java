package com.coen390.smartexit;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class ItemStateTrackerTest {

    private final ItemProfile keys = calibratedItem("keys", "Keys", 36.0, 42.0);
    private final ItemProfile wallet = calibratedItem("wallet", "Wallet", 140.0, 152.0);

    @Test
    public void startsWithUnknownState_untilTheFirstPlateSnapshotArrives() {
        ItemStateTracker tracker = new ItemStateTracker(Arrays.asList(keys, wallet), 4);

        assertState(tracker.getStates(), keys, TrackedItemStatus.UNKNOWN, null);
        assertState(tracker.getStates(), wallet, TrackedItemStatus.UNKNOWN, null);
    }

    @Test
    public void recordsPresentItemAndPlate_fromACompleteSnapshot() {
        ItemStateTracker tracker = new ItemStateTracker(Arrays.asList(keys, wallet), 4);

        List<TrackedItemState> states = tracker.update(Arrays.asList(
                matched(1, 39.0, keys),
                empty(2),
                empty(3),
                empty(4)
        ));

        assertState(states, keys, TrackedItemStatus.PRESENT, 1);
        assertState(states, wallet, TrackedItemStatus.MISSING, null);
    }

    @Test
    public void updatesThePlate_whenAnItemMoves() {
        ItemStateTracker tracker = new ItemStateTracker(Collections.singletonList(keys), 4);
        tracker.update(Arrays.asList(
                matched(1, 39.0, keys),
                empty(2),
                empty(3),
                empty(4)
        ));

        List<TrackedItemState> states = tracker.update(Arrays.asList(
                empty(1),
                empty(2),
                matched(3, 40.0, keys),
                empty(4)
        ));

        assertEquals(1, states.size());
        assertState(states, keys, TrackedItemStatus.PRESENT, 3);
    }

    @Test
    public void marksAnItemMissing_whenItIsRemoved() {
        ItemStateTracker tracker = new ItemStateTracker(Collections.singletonList(keys), 4);
        tracker.update(Arrays.asList(
                matched(1, 39.0, keys),
                empty(2),
                empty(3),
                empty(4)
        ));

        List<TrackedItemState> states = tracker.update(allPlatesEmpty());

        assertState(states, keys, TrackedItemStatus.MISSING, null);
    }

    @Test
    public void marksEveryCandidateUnknown_whenAPlateIsAmbiguous() {
        ItemStateTracker tracker = new ItemStateTracker(Arrays.asList(keys, wallet), 4);

        List<TrackedItemState> states = tracker.update(Arrays.asList(
                empty(1),
                RecognitionResult.ambiguous(
                        new PlateReading(2, 95.0),
                        Arrays.asList(keys, wallet)
                ),
                empty(3),
                empty(4)
        ));

        assertState(states, keys, TrackedItemStatus.UNKNOWN, null);
        assertState(states, wallet, TrackedItemStatus.UNKNOWN, null);
    }

    @Test
    public void keepsADefiniteMatch_whenAnotherPlateIsAmbiguous() {
        ItemStateTracker tracker = new ItemStateTracker(Arrays.asList(keys, wallet), 4);

        List<TrackedItemState> states = tracker.update(Arrays.asList(
                matched(1, 39.0, keys),
                RecognitionResult.ambiguous(
                        new PlateReading(2, 95.0),
                        Arrays.asList(keys, wallet)
                ),
                empty(3),
                empty(4)
        ));

        assertState(states, keys, TrackedItemStatus.PRESENT, 1);
        assertState(states, wallet, TrackedItemStatus.UNKNOWN, null);
    }

    @Test
    public void avoidsDuplicatePlacement_whenTwoPlatesMatchTheSameItem() {
        ItemStateTracker tracker = new ItemStateTracker(Collections.singletonList(keys), 4);

        List<TrackedItemState> states = tracker.update(Arrays.asList(
                matched(1, 39.0, keys),
                empty(2),
                matched(3, 40.0, keys),
                empty(4)
        ));

        assertEquals(1, states.size());
        assertState(states, keys, TrackedItemStatus.UNKNOWN, null);
    }

    @Test
    public void rejectsAnIncompleteOrDuplicatePlateSnapshot() {
        ItemStateTracker tracker = new ItemStateTracker(Collections.singletonList(keys), 4);

        IllegalArgumentException incomplete = assertThrows(
                IllegalArgumentException.class,
                () -> tracker.update(Arrays.asList(empty(1), empty(2), empty(3)))
        );
        IllegalArgumentException duplicate = assertThrows(
                IllegalArgumentException.class,
                () -> tracker.update(Arrays.asList(
                        empty(1),
                        empty(2),
                        empty(2),
                        empty(4)
                ))
        );

        assertEquals(
                "plate snapshot must contain 4 results, but contained 3",
                incomplete.getMessage()
        );
        assertEquals("plate snapshot contains plate 2 more than once", duplicate.getMessage());
    }

    private List<RecognitionResult> allPlatesEmpty() {
        return Arrays.asList(empty(1), empty(2), empty(3), empty(4));
    }

    private RecognitionResult matched(int plateNumber, double weight, ItemProfile item) {
        return RecognitionResult.matched(new PlateReading(plateNumber, weight), item);
    }

    private RecognitionResult empty(int plateNumber) {
        return RecognitionResult.empty(new PlateReading(plateNumber, 0.0));
    }

    private ItemProfile calibratedItem(
            String id,
            String name,
            double minimum,
            double maximum
    ) {
        return new ItemProfile(id, name, minimum, maximum);
    }

    private void assertState(
            List<TrackedItemState> states,
            ItemProfile item,
            TrackedItemStatus expectedStatus,
            Integer expectedPlate
    ) {
        TrackedItemState state = null;
        for (TrackedItemState candidate : states) {
            if (candidate.getItem().getId().equals(item.getId())) {
                state = candidate;
                break;
            }
        }

        if (state == null) {
            throw new AssertionError("No state found for " + item.getName());
        }

        assertEquals(expectedStatus, state.getStatus());
        if (expectedPlate == null) {
            assertNull(state.getPlateNumber());
        } else {
            assertEquals(expectedPlate, state.getPlateNumber());
        }
    }
}
