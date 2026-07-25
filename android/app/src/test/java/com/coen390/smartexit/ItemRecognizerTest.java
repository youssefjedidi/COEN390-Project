package com.coen390.smartexit;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class ItemRecognizerTest {

    private final ItemRecognizer recognizer = new ItemRecognizer();

    @Test
    public void returnsMatched_whenExactlyOneItemRangeContainsTheReading() {
        ItemProfile keys = calibratedItem("Keys", 136.0, 144.0);
        ItemProfile wallet = calibratedItem("Wallet", 116.0, 124.0);

        RecognitionResult result = recognizer.recognize(
                new PlateReading(2, 138.0),
                Arrays.asList(keys, wallet)
        );

        assertEquals(RecognitionStatus.MATCHED, result.getStatus());
        assertSame(keys, result.getCandidates().get(0));
        assertEquals(2, result.getReading().getPlateNumber());
    }

    @Test
    public void acceptsAReading_onEitherBoundaryOfTheSavedRange() {
        ItemProfile keys = calibratedItem("Keys", 136.0, 144.0);

        RecognitionResult lowerBoundary = recognizer.recognize(
                new PlateReading(1, 136.0),
                Collections.singletonList(keys)
        );
        RecognitionResult upperBoundary = recognizer.recognize(
                new PlateReading(1, 144.0),
                Collections.singletonList(keys)
        );

        assertEquals(RecognitionStatus.MATCHED, lowerBoundary.getStatus());
        assertEquals(RecognitionStatus.MATCHED, upperBoundary.getStatus());
    }

    @Test
    public void returnsUnknown_whenTheReadingIsOutsideEverySavedRange() {
        ItemProfile keys = calibratedItem("Keys", 136.0, 144.0);
        ItemProfile wallet = calibratedItem("Wallet", 116.0, 124.0);

        RecognitionResult result = recognizer.recognize(
                new PlateReading(3, 130.0),
                Arrays.asList(keys, wallet)
        );

        assertEquals(RecognitionStatus.UNKNOWN, result.getStatus());
        assertEquals(0, result.getCandidates().size());
    }

    @Test
    public void returnsAmbiguous_withEveryOverlappingCandidate() {
        ItemProfile keys = calibratedItem("Keys", 130.0, 145.0);
        ItemProfile wallet = calibratedItem("Wallet", 125.0, 140.0);

        RecognitionResult result = recognizer.recognize(
                new PlateReading(4, 138.0),
                Arrays.asList(keys, wallet)
        );

        assertEquals(RecognitionStatus.AMBIGUOUS, result.getStatus());
        assertEquals(Arrays.asList(keys, wallet), result.getCandidates());
    }

    @Test
    public void keepsAllCandidates_whenFourItemRangesOverlap() {
        ItemProfile keys = calibratedItem("Keys", 130.0, 145.0);
        ItemProfile wallet = calibratedItem("Wallet", 125.0, 140.0);
        ItemProfile badge = calibratedItem("Badge", 132.0, 142.0);
        ItemProfile medication = calibratedItem("Medication", 135.0, 150.0);

        RecognitionResult result = recognizer.recognize(
                new PlateReading(2, 138.0),
                Arrays.asList(keys, wallet, badge, medication)
        );

        assertEquals(RecognitionStatus.AMBIGUOUS, result.getStatus());
        assertEquals(
                Arrays.asList(keys, wallet, badge, medication),
                result.getCandidates()
        );
    }

    @Test
    public void ignoresItemsThatHaveNotBeenCalibrated() {
        ItemProfile uncalibratedItem = new ItemProfile("Keys");

        RecognitionResult result = recognizer.recognize(
                new PlateReading(1, 138.0),
                Collections.singletonList(uncalibratedItem)
        );

        assertEquals(RecognitionStatus.UNKNOWN, result.getStatus());
    }

    @Test
    public void returnsUnknown_whenThereAreNoSavedItems() {
        RecognitionResult result = recognizer.recognize(
                new PlateReading(1, 138.0),
                Collections.emptyList()
        );

        assertEquals(RecognitionStatus.UNKNOWN, result.getStatus());
    }

    @Test
    public void rejectsMissingRecognitionInputs() {
        PlateReading reading = new PlateReading(1, 138.0);

        IllegalArgumentException missingReading = assertThrows(
                IllegalArgumentException.class,
                () -> recognizer.recognize(null, Collections.emptyList())
        );
        IllegalArgumentException missingProfiles = assertThrows(
                IllegalArgumentException.class,
                () -> recognizer.recognize(reading, null)
        );
        IllegalArgumentException missingProfile = assertThrows(
                IllegalArgumentException.class,
                () -> recognizer.recognize(reading, Collections.singletonList(null))
        );

        assertEquals("item recognition requires a plate reading", missingReading.getMessage());
        assertEquals("item recognition requires saved profiles", missingProfiles.getMessage());
        assertEquals("saved profiles cannot contain null", missingProfile.getMessage());
    }

    private ItemProfile calibratedItem(String name, double minimum, double maximum) {
        ItemProfile item = new ItemProfile(name);
        item.setWeightRange(minimum, maximum);
        return item;
    }
}
