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
    private final ItemProfile fob =
            new ItemProfile("fob", "Fob", 37.0, 45.0);
    private final ItemProfile medication =
            new ItemProfile("medication", "Medication", 35.0, 46.0);

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

        completeSnapshot(coordinator, 40.0);

        assertState(
                coordinator.getStates(),
                keys,
                TrackedItemStatus.PRESENT,
                1
        );
        assertState(
                coordinator.getStates(),
                keyCard,
                TrackedItemStatus.MISSING,
                null
        );
        assertTrue(coordinator.getPendingAmbiguousResults().isEmpty());
    }

    @Test
    public void resolvesTwoCandidateMatchConsistently() {
        assertAmbiguousChoiceFlow(keys, keyCard);
    }

    @Test
    public void resolvesThreeCandidateMatchConsistently() {
        assertAmbiguousChoiceFlow(keys, keyCard, fob);
    }

    @Test
    public void resolvesFourCandidateMatchConsistently() {
        assertAmbiguousChoiceFlow(keys, keyCard, fob, medication);
    }

    @Test
    public void keepsSimilarItemsDistinctAfterOneIsConfirmedAndTheOtherIsRemoved() {
        ItemProfile personalPhone =
                new ItemProfile("personal-phone", "Personal phone", 190.0, 210.0);
        ItemProfile workPhone =
                new ItemProfile("work-phone", "Work phone", 190.0, 210.0);
        DashboardStateCoordinator coordinator = new DashboardStateCoordinator(
                Arrays.asList(personalPhone, workPhone),
                PLATE_COUNT,
                REQUIRED_SAMPLES,
                STABILITY_TOLERANCE_GRAMS,
                EMPTY_WEIGHT_THRESHOLD_GRAMS
        );

        completeSnapshot(coordinator, 200.0, 0.0, 200.0, 0.0);

        assertEquals(2, coordinator.getPendingAmbiguousResults().size());

        List<TrackedItemState> confirmedStates =
                coordinator.confirmAmbiguousMatch(1, personalPhone.getId());

        assertState(confirmedStates, personalPhone, TrackedItemStatus.PRESENT, 1);
        assertState(confirmedStates, workPhone, TrackedItemStatus.PRESENT, 3);
        assertTrue(coordinator.getPendingAmbiguousResults().isEmpty());

        completeSnapshot(coordinator, 200.0, 0.0, 200.0, 0.0);
        assertState(coordinator.getStates(), personalPhone, TrackedItemStatus.PRESENT, 1);
        assertState(coordinator.getStates(), workPhone, TrackedItemStatus.PRESENT, 3);

        completeSnapshot(coordinator, 200.0, 0.0, 0.0, 0.0);
        assertState(coordinator.getStates(), personalPhone, TrackedItemStatus.PRESENT, 1);
        assertState(coordinator.getStates(), workPhone, TrackedItemStatus.MISSING, null);

        completeSnapshot(coordinator, 200.0, 200.0, 0.0, 0.0);
        assertState(coordinator.getStates(), personalPhone, TrackedItemStatus.PRESENT, 1);
        assertState(coordinator.getStates(), workPhone, TrackedItemStatus.PRESENT, 2);
    }

    @Test
    public void removesConfirmedItemsFromLaterAmbiguousChoices() {
        ItemProfile personalPhone =
                new ItemProfile("personal-phone", "Personal phone", 190.0, 210.0);
        ItemProfile workPhone =
                new ItemProfile("work-phone", "Work phone", 190.0, 210.0);
        ItemProfile sparePhone =
                new ItemProfile("spare-phone", "Spare phone", 190.0, 210.0);
        DashboardStateCoordinator coordinator = new DashboardStateCoordinator(
                Arrays.asList(personalPhone, workPhone, sparePhone),
                PLATE_COUNT,
                REQUIRED_SAMPLES,
                STABILITY_TOLERANCE_GRAMS,
                EMPTY_WEIGHT_THRESHOLD_GRAMS
        );

        completeSnapshot(coordinator, 200.0, 0.0, 200.0, 0.0);
        coordinator.confirmAmbiguousMatch(1, personalPhone.getId());

        List<RecognitionResult> pendingResults =
                coordinator.getPendingAmbiguousResults();

        assertEquals(1, pendingResults.size());
        assertEquals(3, pendingResults.get(0).getReading().getPlateNumber());
        assertEquals(
                Arrays.asList(workPhone, sparePhone),
                pendingResults.get(0).getCandidates()
        );
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

        completeSnapshot(coordinator, 40.0);

        List<TrackedItemState> unresolvedStates =
                coordinator.leaveAmbiguousMatchUnresolved(1);

        assertState(unresolvedStates, keys, TrackedItemStatus.UNKNOWN, null);
        assertState(unresolvedStates, keyCard, TrackedItemStatus.UNKNOWN, null);
        assertTrue(coordinator.getPendingAmbiguousResults().isEmpty());

        completeSnapshot(coordinator, 40.0);

        assertTrue(coordinator.getPendingAmbiguousResults().isEmpty());
    }

    @Test
    public void asksAgainAfterTheDismissedPlateBecomesUnambiguous() {
        DashboardStateCoordinator coordinator = new DashboardStateCoordinator(
                Arrays.asList(keys, keyCard),
                PLATE_COUNT,
                REQUIRED_SAMPLES,
                STABILITY_TOLERANCE_GRAMS,
                EMPTY_WEIGHT_THRESHOLD_GRAMS
        );

        completeSnapshot(coordinator, 40.0);
        coordinator.leaveAmbiguousMatchUnresolved(1);

        completeSnapshot(coordinator, 0.0);
        completeSnapshot(coordinator, 40.0);

        assertEquals(1, coordinator.getPendingAmbiguousResults().size());
    }

    @Test
    public void asksAgainWhenTheCandidatesForAPlateChange() {
        DashboardStateCoordinator coordinator = new DashboardStateCoordinator(
                Arrays.asList(keys, keyCard, fob),
                PLATE_COUNT,
                REQUIRED_SAMPLES,
                STABILITY_TOLERANCE_GRAMS,
                EMPTY_WEIGHT_THRESHOLD_GRAMS
        );

        completeSnapshot(coordinator, 40.0);
        coordinator.leaveAmbiguousMatchUnresolved(1);

        completeSnapshot(coordinator, 43.0);

        assertEquals(1, coordinator.getPendingAmbiguousResults().size());
        assertEquals(
                Arrays.asList(keyCard, fob),
                coordinator.getPendingAmbiguousResults().get(0).getCandidates()
        );
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

    private void assertAmbiguousChoiceFlow(ItemProfile... candidates) {
        List<ItemProfile> candidateList = Arrays.asList(candidates);
        DashboardStateCoordinator coordinator = new DashboardStateCoordinator(
                candidateList,
                PLATE_COUNT,
                REQUIRED_SAMPLES,
                STABILITY_TOLERANCE_GRAMS,
                EMPTY_WEIGHT_THRESHOLD_GRAMS
        );

        List<TrackedItemState> unresolvedStates =
                completeSnapshot(coordinator, 40.0);
        RecognitionResult pendingResult =
                coordinator.getPendingAmbiguousResults().get(0);

        assertEquals(candidateList, pendingResult.getCandidates());
        for (ItemProfile candidate : candidateList) {
            assertState(unresolvedStates, candidate, TrackedItemStatus.UNKNOWN, null);
        }

        ItemProfile selectedItem = candidates[candidates.length - 1];
        List<TrackedItemState> resolvedStates =
                coordinator.confirmAmbiguousMatch(1, selectedItem.getId());

        for (ItemProfile candidate : candidateList) {
            TrackedItemStatus expectedStatus = candidate == selectedItem
                    ? TrackedItemStatus.PRESENT
                    : TrackedItemStatus.MISSING;
            Integer expectedPlate = candidate == selectedItem ? 1 : null;
            assertState(resolvedStates, candidate, expectedStatus, expectedPlate);
        }
        assertTrue(coordinator.getPendingAmbiguousResults().isEmpty());
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

    private List<TrackedItemState> completeSnapshot(
            DashboardStateCoordinator coordinator,
            double firstPlateWeight
    ) {
        return completeSnapshot(coordinator, firstPlateWeight, 0.0, 0.0, 0.0);
    }

    private List<TrackedItemState> completeSnapshot(
            DashboardStateCoordinator coordinator,
            double plateOneWeight,
            double plateTwoWeight,
            double plateThreeWeight,
            double plateFourWeight
    ) {
        addStableReading(coordinator, 1, plateOneWeight);
        addStableReading(coordinator, 2, plateTwoWeight);
        addStableReading(coordinator, 3, plateThreeWeight);
        return addStableReading(coordinator, 4, plateFourWeight).get();
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
