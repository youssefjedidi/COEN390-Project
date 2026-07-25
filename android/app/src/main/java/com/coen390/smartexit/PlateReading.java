package com.coen390.smartexit;

public final class PlateReading {

    private final int plateNumber;
    private final double weightGrams;

    public PlateReading(int plateNumber, double weightGrams) {
        if (plateNumber <= 0) {
            throw new IllegalArgumentException(
                    "plateNumber must be positive, but was " + plateNumber
            );
        }
        if (!Double.isFinite(weightGrams)) {
            throw new IllegalArgumentException(
                    "weightGrams must be finite, but was " + weightGrams
            );
        }

        this.plateNumber = plateNumber;
        this.weightGrams = weightGrams;
    }

    public int getPlateNumber() {
        return plateNumber;
    }

    public double getWeightGrams() {
        return weightGrams;
    }
}
