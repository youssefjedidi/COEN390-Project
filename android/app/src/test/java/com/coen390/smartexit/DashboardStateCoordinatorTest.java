package com.coen390.smartexit;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DashboardStateCoordinatorTest {

    private static final int PLATE_COUNT = 4;
    private static final int REQUIRED_SAMPLES = 3;
    private static final double STABILITY_TOLERANCE_GRAMS = 5.0;
    private static final double EMPTY_WEIGHT_THRESHOLD_GRAMS = 5.0;

    private final ItemProfile keys =
            new ItemProfile("keys", "Keys", 36.0, 42.0);
    private final ItemProfile wallet =
            new ItemProfile("wallet", "Wallet", 140.0, 152.0);
    private final ItemProfile keyCard =
            new ItemProfile("key-card", "Key card", 38.0, 44.0);

    @Test
    public void startsWithUnknownItems_beforeEveryPlateHasAStableReading() {
        DashboardStateCoordinator coordinator = newCoordinator();

        assertState(
                coordinator.getStates(),
                keys,
                TrackedItemStatus.UNKNOWN,
                null
        );
        assertState(
                coordinator.getStates(),
                wallet,
                TrackedItemStatus.UNKNOWN,
                null
        );
    }

    @Test
    public void waitsForACompletePlateSnapshot_beforeChangingItemStates() {
        DashboardStateCoordinator coordinator = newCoordinator();

        assertFalse(addStableReading(coordinator, 1, 39.0).isPresent());
        assertFalse(addStableReading(coordinator, 2, 146.0).isPresent());
        assertFalse(addStableReading(coordinator, 3, 0.0).isPresent());

        Optional<List<TrackedItemState>> update =
                addStableReading(coordinator, 4, 0.0);

        assertTrue(update.isPresent());
        assertState(update.get(), keys, TrackedItemStatus.PRESENT, 1);
        assertState(update.get(), wallet, TrackedItemStatus.PRESENT, 2);
    }

    @Test
    public void movesAnItemAfterTheNewPlateReadingBecomesStable() {
        DashboardStateCoordinator coordinator = newCoordinator();

        addStableReading(coordinator, 1, 39.0);
        addStableReading(coordinator, 2, 146.0);
        addStableReading(coordinator, 3, 0.0);
        addStableReading(coordinator, 4, 0.0);

        assertFalse(addStableReading(coordinator, 1, 0.0).isPresent());
        assertFalse(addStableReading(coordinator, 2, 146.0).isPresent());
        assertFalse(addStableReading(coordinator, 3, 40.0).isPresent());
        Optional<List<TrackedItemState>> update =
                addStableReading(coordinator, 4, 0.0);

        assertTrue(update.isPresent());
        assertState(update.get(), keys, TrackedItemStatus.PRESENT, 3);
        assertState(update.get(), wallet, TrackedItemStatus.PRESENT, 2);
    }

    @Test
    public void keepsThePreviousDashboardState_untilEveryPlateIsFresh() {
        DashboardStateCoordinator coordinator = newCoordinator();

        addStableReading(coordinator, 1, 39.0);
        addStableReading(coordinator, 2, 146.0);
        addStableReading(coordinator, 3, 0.0);
        addStableReading(coordinator, 4, 0.0);

        Optional<List<TrackedItemState>> partialUpdate =
                addStableReading(coordinator, 1, 0.0);

        assertFalse(partialUpdate.isPresent());
        assertState(
                coordinator.getStates(),
                keys,
                TrackedItemStatus.PRESENT,
                1
        );
    }

    @Test
    public void unstableSamplesDoNotReplaceTheLastStablePlateResult() {
        DashboardStateCoordinator coordinator = newCoordinator();

        addStableReading(coordinator, 1, 39.0);
        addStableReading(coordinator, 2, 146.0);
        addStableReading(coordinator, 3, 0.0);
        addStableReading(coordinator, 4, 0.0);

        assertFalse(
                coordinator.processReading(new PlateReading(1, 80.0)).isPresent()
        );
        assertFalse(
                coordinator.processReading(new PlateReading(1, 39.0)).isPresent()
        );

        assertState(
                coordinator.getStates(),
                keys,
                TrackedItemStatus.PRESENT,
                1
        );
    }

    @Test
    public void letsTheUserResolveAnAmbiguousPlateReading() {
        DashboardStateCoordinator coordinator = new DashboardStateCoordinator(
                Arrays.asList(keys, keyCard),
                PLATE_COUNT,
                REQUIRED_SAMPLES,
                STABILITY_TOLERANCE_GRAMS,
                EMPTY_WEIGHT_THRESHOLD_GRAMS
        );

        addStableReading(coordinator, 1, 40.0);
        addStableReading(coordinator, 2, 0.0);
        addStableReading(coordinator, 3, 0.0);
        List<TrackedItemState> ambiguousStates =
                addStableReading(coordinator, 4, 0.0).get();

        assertState(ambiguousStates, keys, TrackedItemStatus.UNKNOWN, null);
        assertState(ambiguousStates, keyCard, TrackedItemStatus.UNKNOWN, null);
        assertEquals(1, coordinator.getPendingAmbiguousResults().size());

        List<TrackedItemState> resolvedStates =
                coordinator.confirmAmbiguousMatch(1, keys.getId());

        assertState(resolvedStates, keys, TrackedItemStatus.PRESENT, 1);
        assertState(resolvedStates, keyCard, TrackedItemStatus.MISSING, null);
        assertTrue(coordinator.getPendingAmbiguousResults().isEmpty());
    }

    @Test
    public void leavesCandidatesUnknownWhenTheUserIsNotSure() {
        DashboardStateCoordinator coordinator = new DashboardStateCoordinator(
                Arrays.asList(keys, keyCard),
                PLATE_COUNT,
                REQUIRED_SAMPLES,
                STABILITY_TOLERANCE_GRAMS,
                EMPTY_WEIGHT_THRESHOLD_GRAMS
        );

        addStableReading(coordinator, 1, 40.0);
        addStableReading(coordinator, 2, 0.0);
        addStableReading(coordinator, 3, 0.0);
        addStableReading(coordinator, 4, 0.0);

        List<TrackedItemState> unresolvedStates =
                coordinator.leaveAmbiguousMatchUnresolved(1);

        assertState(unresolvedStates, keys, TrackedItemStatus.UNKNOWN, null);
        assertState(unresolvedStates, keyCard, TrackedItemStatus.UNKNOWN, null);
        assertTrue(coordinator.getPendingAmbiguousResults().isEmpty());
    }

    private DashboardStateCoordinator newCoordinator() {
        return new DashboardStateCoordinator(
                Arrays.asList(keys, wallet),
                PLATE_COUNT,
                REQUIRED_SAMPLES,
                STABILITY_TOLERANCE_GRAMS,
                EMPTY_WEIGHT_THRESHOLD_GRAMS
        );
    }

    private Optional<List<TrackedItemState>> addStableReading(
            DashboardStateCoordinator coordinator,
            int plateNumber,
            double weightGrams
    ) {
        Optional<List<TrackedItemState>> update = Optional.empty();
        for (int sample = 0; sample < REQUIRED_SAMPLES; sample++) {
            update = coordinator.processReading(
                    new PlateReading(plateNumber, weightGrams)
            );
            if (update.isPresent()) {
                break;
            }
        }
        return update;
    }

    private void assertState(
            List<TrackedItemState> states,
            ItemProfile item,
            TrackedItemStatus expectedStatus,
            Integer expectedPlate
    ) {
        TrackedItemState matchingState = null;
        for (TrackedItemState state : states) {
            if (state.getItem().getId().equals(item.getId())) {
                matchingState = state;
                break;
            }
        }

        if (matchingState == null) {
            throw new AssertionError("No state found for " + item.getName());
        }

        assertEquals(expectedStatus, matchingState.getStatus());
        if (expectedPlate == null) {
            assertNull(matchingState.getPlateNumber());
        } else {
            assertEquals(expectedPlate, matchingState.getPlateNumber());
        }
    }
}
