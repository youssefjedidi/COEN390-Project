package com.coen390.smartexit;

/** Selects a calibration plate after one complete station reading cycle. */
final class CalibrationPlateDetector {

    enum Outcome {
        WAITING,
        DETECTED,
        AMBIGUOUS,
        SENSOR_ERROR
    }

    static final class Result {
        private final Outcome outcome;
        private final int detectedPlate;

        private Result(Outcome outcome, int detectedPlate) {
            this.outcome = outcome;
            this.detectedPlate = detectedPlate;
        }

        static Result waiting() {
            return new Result(Outcome.WAITING, 0);
        }

        static Result detected(int plateNumber) {
            return new Result(Outcome.DETECTED, plateNumber);
        }

        static Result ambiguous() {
            return new Result(Outcome.AMBIGUOUS, 0);
        }

        static Result sensorError() {
            return new Result(Outcome.SENSOR_ERROR, 0);
        }

        Outcome getOutcome() {
            return outcome;
        }

        int getDetectedPlate() {
            return detectedPlate;
        }
    }

    private final int plateCount;
    private int nextPlateNumber;
    private int occupiedPlateCount;
    private int occupiedPlate;

    CalibrationPlateDetector(int plateCount) {
        if (plateCount <= 0) {
            throw new IllegalArgumentException("plateCount must be positive");
        }
        this.plateCount = plateCount;
        reset();
    }

    void reset() {
        restartCycle();
    }

    Result observe(BluetoothReading reading) {
        if (!isReadingUsable(reading)) {
            restartCycle();
            return Result.waiting();
        }
        if (reading.getStatus() == BluetoothReading.Status.ERROR) {
            restartCycle();
            return Result.sensorError();
        }
        if (reading.getStatus() == BluetoothReading.Status.UNSTABLE) {
            restartCycle();
            return Result.waiting();
        }

        int plateNumber = reading.getPlateNumber();
        if (plateNumber != nextPlateNumber) {
            restartCycle();
            if (plateNumber != 1) {
                return Result.waiting();
            }
        }

        if (reading.getStatus() == BluetoothReading.Status.OK) {
            occupiedPlateCount++;
            occupiedPlate = plateNumber;
        }

        nextPlateNumber++;
        if (nextPlateNumber <= plateCount) {
            return Result.waiting();
        }

        Result result = resultForCompletedCycle();
        restartCycle();
        return result;
    }

    private boolean isReadingUsable(BluetoothReading reading) {
        if (reading == null
                || reading.getStatus() == null
                || !reading.hasPlateNumber()) {
            return false;
        }
        int plateNumber = reading.getPlateNumber();
        return plateNumber >= 1 && plateNumber <= plateCount;
    }

    private Result resultForCompletedCycle() {
        if (occupiedPlateCount == 0) {
            return Result.waiting();
        }
        if (occupiedPlateCount > 1) {
            return Result.ambiguous();
        }
        return Result.detected(occupiedPlate);
    }

    private void restartCycle() {
        nextPlateNumber = 1;
        occupiedPlateCount = 0;
        occupiedPlate = 0;
    }
}
