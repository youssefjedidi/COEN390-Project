package com.coen390.smartexit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RecognitionResultTest {

    @Test
    public void matchedResult_containsReadingAndSingleItem() {
        PlateReading reading = new PlateReading(3, 146.2);
        ItemProfile wallet = new ItemProfile("Wallet");

        RecognitionResult result = RecognitionResult.matched(reading, wallet);

        assertEquals(RecognitionStatus.MATCHED, result.getStatus());
        assertSame(reading, result.getReading());
        assertEquals(Collections.singletonList(wallet), result.getCandidates());
    }

    @Test
    public void ambiguousResult_containsEveryPossibleItem() {
        PlateReading reading = new PlateReading(2, 130.0);
        ItemProfile keys = new ItemProfile("Keys");
        ItemProfile medication = new ItemProfile("Medication");

        RecognitionResult result = RecognitionResult.ambiguous(
                reading,
                Arrays.asList(keys, medication)
        );

        assertEquals(RecognitionStatus.AMBIGUOUS, result.getStatus());
        assertSame(reading, result.getReading());
        assertEquals(Arrays.asList(keys, medication), result.getCandidates());
    }

    @Test
    public void unknownAndEmptyResults_haveNoCandidates() {
        PlateReading unknownReading = new PlateReading(1, 75.0);
        PlateReading emptyReading = new PlateReading(4, 0.0);

        RecognitionResult unknown = RecognitionResult.unknown(unknownReading);
        RecognitionResult empty = RecognitionResult.empty(emptyReading);

        assertEquals(RecognitionStatus.UNKNOWN, unknown.getStatus());
        assertSame(unknownReading, unknown.getReading());
        assertTrue(unknown.getCandidates().isEmpty());
        assertEquals(RecognitionStatus.EMPTY, empty.getStatus());
        assertSame(emptyReading, empty.getReading());
        assertTrue(empty.getCandidates().isEmpty());
    }

    @Test
    public void plateReading_acceptsPlateNumbersBeyondCurrentPrototype() {
        PlateReading reading = new PlateReading(5, 100.0);

        assertEquals(5, reading.getPlateNumber());
        assertEquals(100.0, reading.getWeightGrams(), 0.0);
    }

    @Test
    public void plateReading_rejectsNonPositivePlateNumber() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new PlateReading(0, 100.0)
        );

        assertEquals("plateNumber must be positive, but was 0", error.getMessage());
    }

    @Test
    public void plateReading_rejectsNonFiniteWeight() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new PlateReading(1, Double.NaN)
        );

        assertEquals("weightGrams must be finite, but was NaN", error.getMessage());
    }

    @Test
    public void matchedResult_rejectsNullItem() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RecognitionResult.matched(new PlateReading(1, 100.0), null)
        );

        assertEquals("a matched result requires an item", error.getMessage());
    }

    @Test
    public void result_rejectsNullReading() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RecognitionResult.empty(null)
        );

        assertEquals("a recognition result requires a reading", error.getMessage());
    }

    @Test
    public void ambiguousResult_rejectsFewerThanTwoCandidates() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RecognitionResult.ambiguous(
                        new PlateReading(1, 120.0),
                        Collections.singletonList(new ItemProfile("Keys"))
                )
        );

        assertEquals(
                "an ambiguous result requires at least two candidates, but received 1",
                error.getMessage()
        );
    }

    @Test
    public void ambiguousResult_rejectsNullCandidate() {
        List<ItemProfile> candidates = Arrays.asList(new ItemProfile("Keys"), null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> RecognitionResult.ambiguous(
                        new PlateReading(1, 120.0),
                        candidates
                )
        );

        assertEquals(
                "an ambiguous result cannot contain a null candidate",
                error.getMessage()
        );
    }

    @Test
    public void candidateList_isCopiedAndReadOnly() {
        ItemProfile keys = new ItemProfile("Keys");
        ItemProfile medication = new ItemProfile("Medication");
        List<ItemProfile> candidates = new ArrayList<>(Arrays.asList(keys, medication));
        RecognitionResult result = RecognitionResult.ambiguous(
                new PlateReading(2, 130.0),
                candidates
        );

        candidates.clear();

        assertEquals(Arrays.asList(keys, medication), result.getCandidates());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.getCandidates().clear()
        );
    }
}
