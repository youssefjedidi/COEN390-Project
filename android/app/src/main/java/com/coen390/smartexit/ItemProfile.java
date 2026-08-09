package com.coen390.smartexit;

import java.util.UUID;

public final class ItemProfile {

    private final String id;
    private String name;
    private Double minWeightGrams;
    private Double maxWeightGrams;

    public ItemProfile(String name) {
        this(UUID.randomUUID().toString(), name, null, null);
    }

    public ItemProfile(String id, String name, Double minWeightGrams, Double maxWeightGrams) {
        if ((minWeightGrams == null) != (maxWeightGrams == null)) {
            throw new IllegalArgumentException(
                    "A calibrated item needs both weight boundaries"
            );
        }
        if (minWeightGrams != null) {
            validateWeightRange(minWeightGrams, maxWeightGrams);
        }
        this.id = id;
        this.name = name;
        this.minWeightGrams = minWeightGrams;
        this.maxWeightGrams = maxWeightGrams;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isCalibrated() {
        return minWeightGrams != null && maxWeightGrams != null;
    }

    public Double getMinWeightGrams() { return minWeightGrams; }
    public Double getMaxWeightGrams() { return maxWeightGrams; }

    public void setWeightRange(double minGrams, double maxGrams) {
        validateWeightRange(minGrams, maxGrams);
        this.minWeightGrams = minGrams;
        this.maxWeightGrams = maxGrams;
    }

    private static void validateWeightRange(double minGrams, double maxGrams) {
        if (!Double.isFinite(minGrams) || !Double.isFinite(maxGrams)) {
            throw new IllegalArgumentException("Weight boundaries must be finite");
        }
        if (minGrams < 0.0 || minGrams > maxGrams) {
            throw new IllegalArgumentException(
                    "Weight boundaries must be non-negative and ordered"
            );
        }
    }

    public boolean matches(double readingGrams) {
        return isCalibrated() && readingGrams >= minWeightGrams && readingGrams <= maxWeightGrams;
    }

    @Override
    public String toString() {
        return "ItemProfile{id='" + id + "', name='" + name + "', range=["
                + minWeightGrams + ", " + maxWeightGrams + "]}";
    }
}
