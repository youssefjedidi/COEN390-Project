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

        assertEquals(CalibrationSampleCollector.Status.COMPLETE, complete.getStatus());
        assertEquals(135.0, complete.getMinimumWeightGrams(), 0.0001);
        assertEquals(147.0, complete.getMaximumWeightGrams(), 0.0001);
        assertFalse(collector.isActive());
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
