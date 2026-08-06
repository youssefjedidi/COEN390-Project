package com.coen390.smartexit;

/**
 * Detects which single plate an item was placed on, so calibration (UI-4.7)
 * no longer requires the user to manually select a plate.
 *
 * Usage: call reset() to start a fresh detection attempt, then feed live
 * readings to observe() until it reports DETECTED, AMBIGUOUS, or an error.
 * The first reading seen for each plate after reset() becomes that plate's
 * baseline (its "starting value" before an item is placed).
 */
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
    private final double changeThresholdGrams;
    private final Float[] baselineGrams;
    private final boolean[] changedFlags;

    CalibrationPlateDetector(int plateCount, double changeThresholdGrams) {
        if (plateCount <= 0) {
            throw new IllegalArgumentException("plateCount must be positive");
        }
        if (!Double.isFinite(changeThresholdGrams) || changeThresholdGrams < 0.0) {
            throw new IllegalArgumentException(
                    "changeThresholdGrams must be finite and non-negative"
            );
        }
        this.plateCount = plateCount;
        this.changeThresholdGrams = changeThresholdGrams;
        this.baselineGrams = new Float[plateCount];
        this.changedFlags = new boolean[plateCount];
    }

    /** Clears all recorded baselines and changed-state, ready for a new detection attempt. */
    void reset() {
        for (int index = 0; index < plateCount; index++) {
            baselineGrams[index] = null;
            changedFlags[index] = false;
        }
    }

    /**
     * Feed one live reading during detection. Returns WAITING until exactly
     * one plate's weight has risen past its baseline by more than the
     * configured threshold, at which point it returns DETECTED with that
     * plate number. Returns AMBIGUOUS if more than one plate has changed,
     * and can resolve back to WAITING/DETECTED if a plate's weight returns
     * to its baseline (e.g. the user removed the item from the wrong plate).
     */
    Result observe(BluetoothReading reading) {
        if (reading == null || !reading.hasPlateNumber()) {
            return Result.waiting();
        }
        if (reading.getStatus() == BluetoothReading.Status.ERROR) {
            return Result.sensorError();
        }
        if (reading.getStatus() != BluetoothReading.Status.OK) {
            return Result.waiting();
        }

        int plateNumber = reading.getPlateNumber();
        if (plateNumber < 1 || plateNumber > plateCount) {
            return Result.waiting();
        }
        int index = plateNumber - 1;

        Float baseline = baselineGrams[index];
        if (baseline == null) {
            baselineGrams[index] = reading.getWeightGrams();
            changedFlags[index] = false;
        } else {
            boolean changed = (reading.getWeightGrams() - baseline) > changeThresholdGrams;
            changedFlags[index] = changed;
            if (!changed) {
                baselineGrams[index] = reading.getWeightGrams();
            }
        }

        int changedCount = 0;
        int lastChangedPlate = 0;
        for (int i = 0; i < plateCount; i++) {
            if (changedFlags[i]) {
                changedCount++;
                lastChangedPlate = i + 1;
            }
        }

        if (changedCount == 0) {
            return Result.waiting();
        }
        if (changedCount > 1) {
            return Result.ambiguous();
        }
        return Result.detected(lastChangedPlate);
    }
}