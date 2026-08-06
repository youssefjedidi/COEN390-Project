package com.coen390.smartexit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CalibrationPlateDetectorTest {

    @Test
    public void noItemPlaced_staysWaiting() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4, 10.0);
        detector.reset();

        CalibrationPlateDetector.Result first = detector.observe(okReading(1, 0.0f));
        CalibrationPlateDetector.Result second = detector.observe(okReading(1, 2.0f));

        assertEquals(CalibrationPlateDetector.Outcome.WAITING, first.getOutcome());
        assertEquals(CalibrationPlateDetector.Outcome.WAITING, second.getOutcome());
    }

    @Test
    public void onePlateChanges_detectsThatPlate() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4, 10.0);
        detector.reset();
        detector.observe(okReading(1, 0.0f));
        detector.observe(okReading(2, 0.0f));

        CalibrationPlateDetector.Result result = detector.observe(okReading(1, 50.0f));

        assertEquals(CalibrationPlateDetector.Outcome.DETECTED, result.getOutcome());
        assertEquals(1, result.getDetectedPlate());
    }

    @Test
    public void multiplePlatesChange_reportsAmbiguous() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4, 10.0);
        detector.reset();
        detector.observe(okReading(1, 0.0f));
        detector.observe(okReading(2, 0.0f));
        detector.observe(okReading(1, 50.0f));

        CalibrationPlateDetector.Result result = detector.observe(okReading(2, 60.0f));

        assertEquals(CalibrationPlateDetector.Outcome.AMBIGUOUS, result.getOutcome());
    }

    @Test
    public void ambiguousResolvesBackToDetected_whenOnePlateReturnsToBaseline() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4, 10.0);
        detector.reset();
        detector.observe(okReading(1, 0.0f));
        detector.observe(okReading(2, 0.0f));
        detector.observe(okReading(1, 50.0f));
        detector.observe(okReading(2, 60.0f));

        CalibrationPlateDetector.Result result = detector.observe(okReading(2, 1.0f));

        assertEquals(CalibrationPlateDetector.Outcome.DETECTED, result.getOutcome());
        assertEquals(1, result.getDetectedPlate());
    }

    @Test
    public void sensorError_reportsSensorError() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4, 10.0);
        detector.reset();

        CalibrationPlateDetector.Result result = detector.observe(
                BluetoothReading.forPlate(1, 0.0f, BluetoothReading.Status.ERROR)
        );

        assertEquals(CalibrationPlateDetector.Outcome.SENSOR_ERROR, result.getOutcome());
    }

    @Test
    public void unstableReading_doesNotCountAsChange() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4, 10.0);
        detector.reset();
        detector.observe(okReading(1, 0.0f));

        CalibrationPlateDetector.Result result = detector.observe(
                BluetoothReading.forPlate(1, 999.0f, BluetoothReading.Status.UNSTABLE)
        );

        assertEquals(CalibrationPlateDetector.Outcome.WAITING, result.getOutcome());
    }

    @Test
    public void resetClearsPreviousState() {
        CalibrationPlateDetector detector = new CalibrationPlateDetector(4, 10.0);
        detector.reset();
        detector.observe(okReading(1, 0.0f));
        detector.observe(okReading(1, 50.0f));

        detector.reset();
        CalibrationPlateDetector.Result result = detector.observe(okReading(1, 50.0f));

        assertEquals(CalibrationPlateDetector.Outcome.WAITING, result.getOutcome());
    }

    private BluetoothReading okReading(int plateNumber, float weightGrams) {
        return BluetoothReading.forPlate(
                plateNumber,
                weightGrams,
                BluetoothReading.Status.OK
        );
    }
}