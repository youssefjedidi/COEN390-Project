package com.coen390.smartexit;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StableReadingFilterTest {

    @Test
    public void returnsNoReading_untilThePlateHasEnoughSamples() {
        StableReadingFilter filter = new StableReadingFilter(4, 3, 5.0);

        assertFalse(filter.add(new PlateReading(1, 138.0)).isPresent());
        assertFalse(filter.add(new PlateReading(1, 140.0)).isPresent());
    }

    @Test
    public void returnsAverage_whenThreeReadingsAreWithinTolerance() {
        StableReadingFilter filter = new StableReadingFilter(4, 3, 5.0);

        filter.add(new PlateReading(2, 138.0));
        filter.add(new PlateReading(2, 140.0));
        Optional<PlateReading> result = filter.add(new PlateReading(2, 139.0));

        assertTrue(result.isPresent());
        assertEquals(2, result.get().getPlateNumber());
        assertEquals(139.0, result.get().getWeightGrams(), 0.0001);
    }

    @Test
    public void acceptsAWindow_whoseSpreadIsExactlyAtTolerance() {
        StableReadingFilter filter = new StableReadingFilter(4, 3, 5.0);

        filter.add(new PlateReading(1, 100.0));
        filter.add(new PlateReading(1, 105.0));
        Optional<PlateReading> result = filter.add(new PlateReading(1, 102.0));

        assertTrue(result.isPresent());
        assertEquals(102.3333, result.get().getWeightGrams(), 0.0001);
    }

    @Test
    public void discardsOldestReading_beforeEvaluatingNextWindow() {
        StableReadingFilter filter = new StableReadingFilter(4, 3, 5.0);

        filter.add(new PlateReading(3, 100.0));
        filter.add(new PlateReading(3, 103.0));
        filter.add(new PlateReading(3, 105.0));
        Optional<PlateReading> result = filter.add(new PlateReading(3, 108.0));

        assertTrue(result.isPresent());
        assertEquals(105.3333, result.get().getWeightGrams(), 0.0001);
    }

    @Test
    public void largeJump_startsANewWindowForThatPlate() {
        StableReadingFilter filter = new StableReadingFilter(4, 3, 5.0);

        filter.add(new PlateReading(1, 100.0));
        filter.add(new PlateReading(1, 102.0));
        assertFalse(filter.add(new PlateReading(1, 120.0)).isPresent());
        assertFalse(filter.add(new PlateReading(1, 121.0)).isPresent());
        Optional<PlateReading> result = filter.add(new PlateReading(1, 122.0));

        assertTrue(result.isPresent());
        assertEquals(121.0, result.get().getWeightGrams(), 0.0001);
    }

    @Test
    public void keepsSampleWindowsIndependentForEachPlate() {
        StableReadingFilter filter = new StableReadingFilter(4, 3, 5.0);

        filter.add(new PlateReading(1, 100.0));
        filter.add(new PlateReading(2, 200.0));
        filter.add(new PlateReading(1, 101.0));
        filter.add(new PlateReading(2, 201.0));
        Optional<PlateReading> plateTwo = filter.add(new PlateReading(2, 202.0));
        Optional<PlateReading> plateOne = filter.add(new PlateReading(1, 102.0));

        assertTrue(plateTwo.isPresent());
        assertEquals(201.0, plateTwo.get().getWeightGrams(), 0.0001);
        assertTrue(plateOne.isPresent());
        assertEquals(101.0, plateOne.get().getWeightGrams(), 0.0001);
    }

    @Test
    public void resetPlate_clearsOnlyThatPlateWindow() {
        StableReadingFilter filter = new StableReadingFilter(4, 3, 5.0);

        filter.add(new PlateReading(1, 100.0));
        filter.add(new PlateReading(1, 101.0));
        filter.add(new PlateReading(2, 200.0));
        filter.add(new PlateReading(2, 201.0));
        filter.resetPlate(1);

        assertFalse(filter.add(new PlateReading(1, 102.0)).isPresent());
        assertTrue(filter.add(new PlateReading(2, 202.0)).isPresent());
    }

    @Test
    public void resetAll_clearsEveryPlateWindow() {
        StableReadingFilter filter = new StableReadingFilter(4, 3, 5.0);

        filter.add(new PlateReading(1, 100.0));
        filter.add(new PlateReading(1, 101.0));
        filter.resetAll();

        assertFalse(filter.add(new PlateReading(1, 102.0)).isPresent());
    }

    @Test
    public void constructor_rejectsInvalidConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StableReadingFilter(0, 3, 5.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StableReadingFilter(4, 0, 5.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StableReadingFilter(4, 3, -1.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StableReadingFilter(4, 3, Double.NaN)
        );
    }

    @Test
    public void add_rejectsReadingOutsideConfiguredPlateRange() {
        StableReadingFilter filter = new StableReadingFilter(4, 3, 5.0);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> filter.add(new PlateReading(5, 100.0))
        );

        assertEquals("plateNumber must be between 1 and 4, but was 5", error.getMessage());
    }

    @Test
    public void add_rejectsNullReading() {
        StableReadingFilter filter = new StableReadingFilter(4, 3, 5.0);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> filter.add(null)
        );

        assertEquals("a stable-reading filter requires a reading", error.getMessage());
    }
}
