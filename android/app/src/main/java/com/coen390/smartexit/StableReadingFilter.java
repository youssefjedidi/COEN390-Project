package com.coen390.smartexit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;

final class StableReadingFilter {

    private final int plateCount;
    private final int requiredSamples;
    private final double toleranceGrams;
    private final SampleWindow[] windows;

    StableReadingFilter(int plateCount, int requiredSamples, double toleranceGrams) {
        if (plateCount <= 0) {
            throw new IllegalArgumentException(
                    "plateCount must be positive, but was " + plateCount
            );
        }
        if (requiredSamples <= 0) {
            throw new IllegalArgumentException(
                    "requiredSamples must be positive, but was " + requiredSamples
            );
        }
        if (!Double.isFinite(toleranceGrams) || toleranceGrams < 0.0) {
            throw new IllegalArgumentException(
                    "toleranceGrams must be finite and non-negative, but was "
                            + toleranceGrams
            );
        }

        this.plateCount = plateCount;
        this.requiredSamples = requiredSamples;
        this.toleranceGrams = toleranceGrams;
        this.windows = new SampleWindow[plateCount + 1];

        for (int plateNumber = 1; plateNumber <= plateCount; plateNumber++) {
            windows[plateNumber] = new SampleWindow(requiredSamples);
        }
    }

    Optional<PlateReading> add(PlateReading reading) {
        if (reading == null) {
            throw new IllegalArgumentException("a stable-reading filter requires a reading");
        }

        SampleWindow window = windowFor(reading.getPlateNumber());
        double weightGrams = reading.getWeightGrams();

        // Check the next rolling window, not old samples that would be removed anyway.
        if (!window.wouldStayWithinTolerance(weightGrams, toleranceGrams)) {
            window.clear();
        }

        window.add(weightGrams);
        if (!window.isFull()) {
            return Optional.empty();
        }

        return Optional.of(new PlateReading(reading.getPlateNumber(), window.average()));
    }

    void resetPlate(int plateNumber) {
        windowFor(plateNumber).clear();
    }

    void resetAll() {
        for (int plateNumber = 1; plateNumber <= plateCount; plateNumber++) {
            windows[plateNumber].clear();
        }
    }

    private SampleWindow windowFor(int plateNumber) {
        if (plateNumber < 1 || plateNumber > plateCount) {
            throw new IllegalArgumentException(
                    "plateNumber must be between 1 and " + plateCount + ", but was "
                            + plateNumber
            );
        }
        return windows[plateNumber];
    }

    private static final class SampleWindow {
        private final int capacity;
        private final Deque<Double> samples = new ArrayDeque<>();

        SampleWindow(int capacity) {
            this.capacity = capacity;
        }

        boolean wouldStayWithinTolerance(double nextWeight, double toleranceGrams) {
            double minimum = nextWeight;
            double maximum = nextWeight;
            Iterator<Double> retainedSamples = samples.iterator();

            if (samples.size() == capacity) {
                retainedSamples.next();
            }

            while (retainedSamples.hasNext()) {
                double sample = retainedSamples.next();
                minimum = Math.min(minimum, sample);
                maximum = Math.max(maximum, sample);
            }

            return maximum - minimum <= toleranceGrams;
        }

        void add(double weightGrams) {
            if (samples.size() == capacity) {
                samples.removeFirst();
            }
            samples.addLast(weightGrams);
        }

        boolean isFull() {
            return samples.size() == capacity;
        }

        double average() {
            double total = 0.0;
            for (double sample : samples) {
                total += sample;
            }
            return total / samples.size();
        }

        void clear() {
            samples.clear();
        }
    }
}
