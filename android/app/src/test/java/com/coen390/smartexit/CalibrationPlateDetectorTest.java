package com.coen390.smartexit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CalibrationPlateDetectorTest {

    @Test
    public void oneOccupiedPlate_isSelectedAfterCompleteCycle() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4);

        assertWaiting(detector.observe(emptyReading(1)));
        assertWaiting(detector.observe(occupiedReading(2, 146.0f)));
        assertWaiting(detector.observe(emptyReading(3)));
        CalibrationPlateDetector.Result result = detector.observe(emptyReading(4));

        assertEquals(CalibrationPlateDetector.Outcome.DETECTED, result.getOutcome());
        assertEquals(2, result.getDetectedPlate());
    }

    @Test
    public void allEmptyPlates_keepWaitingForItem() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4);

        CalibrationPlateDetector.Result result = sendEmptyCycle(detector);

        assertWaiting(result);
    }

    @Test
    public void severalOccupiedPlates_reportAmbiguousAfterCompleteCycle() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4);

        assertWaiting(detector.observe(occupiedReading(1, 120.0f)));
        assertWaiting(detector.observe(emptyReading(2)));
        assertWaiting(detector.observe(occupiedReading(3, 145.0f)));
        CalibrationPlateDetector.Result result = detector.observe(emptyReading(4));

        assertEquals(CalibrationPlateDetector.Outcome.AMBIGUOUS, result.getOutcome());
    }

    @Test
    public void unstableReading_discardsPartialCycle() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4);

        detector.observe(occupiedReading(1, 146.0f));
        assertWaiting(detector.observe(unstableReading(2)));
        assertWaiting(detector.observe(emptyReading(3)));
        assertWaiting(detector.observe(emptyReading(4)));

        CalibrationPlateDetector.Result result = sendCycle(
                detector,
                emptyReading(1),
                emptyReading(2),
                occupiedReading(3, 146.0f),
                emptyReading(4)
        );

        assertEquals(CalibrationPlateDetector.Outcome.DETECTED, result.getOutcome());
        assertEquals(3, result.getDetectedPlate());
    }

    @Test
    public void outOfOrderReading_discardsPartialCycle() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4);

        detector.observe(occupiedReading(1, 146.0f));
        assertWaiting(detector.observe(emptyReading(3)));
        assertWaiting(detector.observe(emptyReading(4)));

        CalibrationPlateDetector.Result result = sendCycle(
                detector,
                emptyReading(1),
                occupiedReading(2, 146.0f),
                emptyReading(3),
                emptyReading(4)
        );

        assertEquals(CalibrationPlateDetector.Outcome.DETECTED, result.getOutcome());
        assertEquals(2, result.getDetectedPlate());
    }

    @Test
    public void sensorError_reportsErrorAndDiscardsPartialCycle() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4);

        detector.observe(occupiedReading(1, 146.0f));
        CalibrationPlateDetector.Result error = detector.observe(errorReading(2));

        assertEquals(CalibrationPlateDetector.Outcome.SENSOR_ERROR, error.getOutcome());
        assertWaiting(detector.observe(emptyReading(3)));
        assertWaiting(detector.observe(emptyReading(4)));
    }

    @Test
    public void reset_discardsPartialCycle() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4);
        detector.observe(occupiedReading(1, 146.0f));
        detector.observe(emptyReading(2));

        detector.reset();

        assertWaiting(detector.observe(emptyReading(3)));
        assertWaiting(detector.observe(emptyReading(4)));
        assertWaiting(sendEmptyCycle(detector));
    }

    private CalibrationPlateDetector.Result sendEmptyCycle(
            CalibrationPlateDetector detector
    ) {
        return sendCycle(
                detector,
                emptyReading(1),
                emptyReading(2),
                emptyReading(3),
                emptyReading(4)
        );
    }

    private CalibrationPlateDetector.Result sendCycle(
            CalibrationPlateDetector detector,
            BluetoothReading first,
            BluetoothReading second,
            BluetoothReading third,
            BluetoothReading fourth
    ) {
        detector.observe(first);
        detector.observe(second);
        detector.observe(third);
        return detector.observe(fourth);
    }

    private void assertWaiting(CalibrationPlateDetector.Result result) {
        assertEquals(CalibrationPlateDetector.Outcome.WAITING, result.getOutcome());
    }

    private BluetoothReading occupiedReading(int plateNumber, float weightGrams) {
        return BluetoothReading.forPlate(
                plateNumber,
                weightGrams,
                BluetoothReading.Status.OK
        );
    }

    private BluetoothReading emptyReading(int plateNumber) {
        return BluetoothReading.forPlate(
                plateNumber,
                0.0f,
                BluetoothReading.Status.NO_LOAD
        );
    }

    private BluetoothReading unstableReading(int plateNumber) {
        return BluetoothReading.forPlate(
                plateNumber,
                0.0f,
                BluetoothReading.Status.UNSTABLE
        );
    }

    private BluetoothReading errorReading(int plateNumber) {
        return BluetoothReading.forPlate(
                plateNumber,
                0.0f,
                BluetoothReading.Status.ERROR
        );
    }
}
