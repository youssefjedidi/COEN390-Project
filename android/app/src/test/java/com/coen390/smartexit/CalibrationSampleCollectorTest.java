package com.coen390.smartexit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CalibrationSampleCollectorTest {

    @Test
    public void ignoresReadingsFromAnotherPlate() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 3, 5.0, 5.0);
        collector.start(2);

        CalibrationSampleCollector.Update update = collector.add(okReading(1, 140.0f));

        assertEquals(CalibrationSampleCollector.Status.IGNORED, update.getStatus());
        assertEquals(0, collector.getSampleCount());
    }

    @Test
    public void waitsUntilEnoughStableSamplesHaveArrived() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 3, 5.0, 5.0);
        collector.start(2);

        CalibrationSampleCollector.Update first = collector.add(okReading(2, 140.0f));
        CalibrationSampleCollector.Update second = collector.add(okReading(2, 142.0f));

        assertEquals(CalibrationSampleCollector.Status.COLLECTING, first.getStatus());
        assertEquals(1, first.getSampleCount());
        assertEquals(CalibrationSampleCollector.Status.COLLECTING, second.getStatus());
        assertEquals(2, second.getSampleCount());
    }

    @Test
    public void createsRangeAroundObservedStableSamples() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 3, 5.0, 5.0);
        collector.start(3);

        collector.add(okReading(3, 140.0f));
        collector.add(okReading(3, 142.0f));
        CalibrationSampleCollector.Update complete = collector.add(okReading(3, 141.0f));

        // average = 141g, 5% of 141g = 7.05g, which is above the 5g minimum.
        assertEquals(CalibrationSampleCollector.Status.COMPLETE, complete.getStatus());
        assertEquals(132.95, complete.getMinimumWeightGrams(), 0.0001);
        assertEquals(149.05, complete.getMaximumWeightGrams(), 0.0001);
        assertFalse(collector.isActive());
    }

    @Test
    public void lightItemUsesMinimumFiveGramMargin() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 3, 5.0, 5.0);
        collector.start(1);

        collector.add(okReading(1, 19.0f));
        collector.add(okReading(1, 21.0f));
        CalibrationSampleCollector.Update complete = collector.add(okReading(1, 20.0f));

        // average = 20g, 5% of 20g = 1g, so the 5g minimum applies instead.
        // min/max sample = 19/21, so the range is 19-5=14 to 21+5=26.
        assertEquals(14.0, complete.getMinimumWeightGrams(), 0.0001);
        assertEquals(26.0, complete.getMaximumWeightGrams(), 0.0001);
    }

    @Test
    public void mediumItemUsesPercentageMarginOnceItExceedsMinimum() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 3, 5.0, 5.0);
        collector.start(1);

        collector.add(okReading(1, 119.0f));
        collector.add(okReading(1, 121.0f));
        CalibrationSampleCollector.Update complete = collector.add(okReading(1, 120.0f));

        // average = 120g, 5% of 120g = 6g, which is above the 5g minimum.
        // min/max sample = 119/121, so the range is 119-6=113 to 121+6=127.
        assertEquals(113.0, complete.getMinimumWeightGrams(), 0.0001);
        assertEquals(127.0, complete.getMaximumWeightGrams(), 0.0001);
    }

    @Test
    public void heavyItemMarginScalesProportionally() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 3, 5.0, 5.0);
        collector.start(1);

        collector.add(okReading(1, 499.0f));
        collector.add(okReading(1, 501.0f));
        CalibrationSampleCollector.Update complete = collector.add(okReading(1, 500.0f));

        // average = 500g, 5% of 500g = 25g.
        // min/max sample = 499/501, so the range is 499-25=474 to 501+25=526.
        assertEquals(474.0, complete.getMinimumWeightGrams(), 0.0001);
        assertEquals(526.0, complete.getMaximumWeightGrams(), 0.0001);
    }

    @Test
    public void largeWeightJumpStartsANewSampleWindow() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 3, 5.0, 5.0);
        collector.start(1);

        collector.add(okReading(1, 100.0f));
        collector.add(okReading(1, 102.0f));
        CalibrationSampleCollector.Update restarted = collector.add(okReading(1, 130.0f));

        assertEquals(CalibrationSampleCollector.Status.COLLECTING, restarted.getStatus());
        assertEquals(1, restarted.getSampleCount());
        assertEquals(1, collector.getSampleCount());
    }

    @Test
    public void unstableOrEmptyReadingClearsCollectedSamples() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 3, 5.0, 5.0);
        collector.start(4);
        collector.add(okReading(4, 80.0f));
        collector.add(okReading(4, 81.0f));

        CalibrationSampleCollector.Update reset = collector.add(
                BluetoothReading.forPlate(4, 0.0f, BluetoothReading.Status.NO_LOAD)
        );

        assertEquals(CalibrationSampleCollector.Status.WAITING_FOR_ITEM, reset.getStatus());
        assertEquals(0, collector.getSampleCount());
    }

    @Test
    public void sensorErrorStopsTheCurrentSampleWindow() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 3, 5.0, 5.0);
        collector.start(2);
        collector.add(okReading(2, 80.0f));

        CalibrationSampleCollector.Update update = collector.add(
                BluetoothReading.forPlate(2, 0.0f, BluetoothReading.Status.ERROR)
        );

        assertEquals(CalibrationSampleCollector.Status.SENSOR_ERROR, update.getStatus());
        assertEquals(0, collector.getSampleCount());
        assertFalse(collector.isActive());
        assertEquals(
                CalibrationSampleCollector.Status.IGNORED,
                collector.add(okReading(2, 80.0f)).getStatus()
        );
    }

    @Test
    public void ignoresReadingsUntilCalibrationStarts() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 3, 5.0, 5.0);

        CalibrationSampleCollector.Update update = collector.add(okReading(1, 50.0f));

        assertEquals(CalibrationSampleCollector.Status.IGNORED, update.getStatus());
    }

    @Test
    public void cancelDiscardsPartialCalibration() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 3, 5.0, 5.0);
        collector.start(1);
        collector.add(okReading(1, 50.0f));

        collector.cancel();

        assertFalse(collector.isActive());
        assertEquals(0, collector.getSampleCount());
        assertEquals(
                CalibrationSampleCollector.Status.IGNORED,
                collector.add(okReading(1, 51.0f)).getStatus()
        );
    }

    @Test
    public void legacySinglePlateReadingCanCalibratePlateOne() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 2, 5.0, 5.0);
        collector.start(1);

        collector.add(new BluetoothReading(40.0f, BluetoothReading.Status.OK, 1));
        CalibrationSampleCollector.Update complete = collector.add(
                new BluetoothReading(41.0f, BluetoothReading.Status.OK, 2)
        );

        assertEquals(CalibrationSampleCollector.Status.COMPLETE, complete.getStatus());
    }

    @Test
    public void rejectsInvalidConfigurationAndPlateSelection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CalibrationSampleCollector(0, 3, 5.0, 5.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CalibrationSampleCollector(4, 0, 5.0, 5.0)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CalibrationSampleCollector(4, 3, -1.0, 5.0)
        );

        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 3, 5.0, 5.0);
        assertThrows(IllegalArgumentException.class, () -> collector.start(5));
    }

    @Test
    public void clampsMinimumRangeAtZero() {
        CalibrationSampleCollector collector = new CalibrationSampleCollector(4, 2, 5.0, 5.0);
        collector.start(1);

        collector.add(okReading(1, 2.0f));
        CalibrationSampleCollector.Update complete = collector.add(okReading(1, 3.0f));

        assertTrue(complete.isComplete());
        assertEquals(0.0, complete.getMinimumWeightGrams(), 0.0001);
    }

    private BluetoothReading okReading(int plateNumber, float weightGrams) {
        return BluetoothReading.forPlate(
                plateNumber,
                weightGrams,
                BluetoothReading.Status.OK
        );
    }
}
