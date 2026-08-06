package com.coen390.smartexit;

final class CalibrationSampleCollector {

    enum Status {
        IGNORED,
        COLLECTING,
        WAITING_FOR_ITEM,
        WAITING_FOR_STABLE,
        SENSOR_ERROR,
        COMPLETE
    }

    static final class Update {
        private final Status status;
        private final int sampleCount;
        private final double minimumWeightGrams;
        private final double maximumWeightGrams;

        private Update(
                Status status,
                int sampleCount,
                double minimumWeightGrams,
                double maximumWeightGrams
        ) {
            this.status = status;
            this.sampleCount = sampleCount;
            this.minimumWeightGrams = minimumWeightGrams;
            this.maximumWeightGrams = maximumWeightGrams;
        }

        static Update inProgress(Status status, int sampleCount) {
            return new Update(status, sampleCount, Double.NaN, Double.NaN);
        }

        static Update complete(
                int sampleCount,
                double minimumWeightGrams,
                double maximumWeightGrams
        ) {
            return new Update(
                    Status.COMPLETE,
                    sampleCount,
                    minimumWeightGrams,
                    maximumWeightGrams
            );
        }

        Status getStatus() {
            return status;
        }

        int getSampleCount() {
            return sampleCount;
        }

        boolean isComplete() {
            return status == Status.COMPLETE;
        }

        double getMinimumWeightGrams() {
            return minimumWeightGrams;
        }

        double getMaximumWeightGrams() {
            return maximumWeightGrams;
        }
    }

    private final int plateCount;
    private final int requiredSamples;
    private final double stabilityToleranceGrams;
    private static final double MARGIN_PERCENTAGE = 0.05;
    private final double minimumMarginGrams;
    private final double[] samples;

    private int selectedPlate;
    private int sampleCount;
    private boolean active;

    CalibrationSampleCollector(
            int plateCount,
            int requiredSamples,
            double stabilityToleranceGrams,
            double rangeMarginGrams
    ) {
        if (plateCount <= 0) {
            throw new IllegalArgumentException("plateCount must be positive");
        }
        if (requiredSamples <= 0) {
            throw new IllegalArgumentException("requiredSamples must be positive");
        }
        if (!isNonNegativeFinite(stabilityToleranceGrams)) {
            throw new IllegalArgumentException(
                    "stabilityToleranceGrams must be finite and non-negative"
            );
        }
        if (!isNonNegativeFinite(rangeMarginGrams)) {
            throw new IllegalArgumentException(
                    "rangeMarginGrams must be finite and non-negative"
            );
        }

        this.plateCount = plateCount;
        this.requiredSamples = requiredSamples;
        this.stabilityToleranceGrams = stabilityToleranceGrams;
        this.minimumMarginGrams = rangeMarginGrams;
        samples = new double[requiredSamples];
    }

    void start(int plateNumber) {
        if (plateNumber < 1 || plateNumber > plateCount) {
            throw new IllegalArgumentException(
                    "plateNumber must be between 1 and " + plateCount
            );
        }

        selectedPlate = plateNumber;
        sampleCount = 0;
        active = true;
    }

    void cancel() {
        sampleCount = 0;
        active = false;
    }

    Update add(BluetoothReading reading) {
        if (!active || reading == null || !belongsToSelectedPlate(reading)) {
            return Update.inProgress(Status.IGNORED, sampleCount);
        }

        if (reading.getStatus() == BluetoothReading.Status.ERROR) {
            sampleCount = 0;
            return Update.inProgress(Status.SENSOR_ERROR, sampleCount);
        }

        if (reading.getStatus() == BluetoothReading.Status.NO_LOAD
                || reading.getWeightGrams() <= 0.0f) {
            sampleCount = 0;
            return Update.inProgress(Status.WAITING_FOR_ITEM, sampleCount);
        }

        if (reading.getStatus() != BluetoothReading.Status.OK) {
            sampleCount = 0;
            return Update.inProgress(Status.WAITING_FOR_STABLE, sampleCount);
        }

        double weightGrams = reading.getWeightGrams();
        if (!Double.isFinite(weightGrams)) {
            sampleCount = 0;
            return Update.inProgress(Status.WAITING_FOR_STABLE, sampleCount);
        }

        // A large jump means the item or the user's hand moved. Start a fresh,
        // bounded window with the newest reading instead of mixing both states.
        if (sampleCount > 0 && spreadIncluding(weightGrams) > stabilityToleranceGrams) {
            sampleCount = 0;
        }

        samples[sampleCount] = weightGrams;
        sampleCount++;

        if (sampleCount < requiredSamples) {
            return Update.inProgress(Status.COLLECTING, sampleCount);
        }

        double minimum = samples[0];
        double maximum = samples[0];
        for (int index = 1; index < sampleCount; index++) {
            minimum = Math.min(minimum, samples[index]);
            maximum = Math.max(maximum, samples[index]);
        }

        active = false;
        double sum = 0;
        for (int sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
            sum += samples[sampleIndex];
        }
        double averageWeight = sum / sampleCount;
        double margin = Math.max(minimumMarginGrams, averageWeight * MARGIN_PERCENTAGE);
        return Update.complete(
                sampleCount,
                Math.max(0.0, minimum - margin),
                maximum + margin
        );
    }

    boolean isActive() {
        return active;
    }

    int getSampleCount() {
        return sampleCount;
    }

    int getRequiredSamples() {
        return requiredSamples;
    }

    int getSelectedPlate() {
        return selectedPlate;
    }

    private boolean belongsToSelectedPlate(BluetoothReading reading) {
        if (reading.hasPlateNumber()) {
            return reading.getPlateNumber() == selectedPlate;
        }
        return selectedPlate == 1;
    }

    private double spreadIncluding(double nextWeight) {
        double minimum = nextWeight;
        double maximum = nextWeight;
        for (int index = 0; index < sampleCount; index++) {
            minimum = Math.min(minimum, samples[index]);
            maximum = Math.max(maximum, samples[index]);
        }
        return maximum - minimum;
    }

    private static boolean isNonNegativeFinite(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
