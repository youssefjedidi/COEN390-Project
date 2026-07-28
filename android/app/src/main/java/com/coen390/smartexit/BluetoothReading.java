package com.coen390.smartexit;

final class BluetoothReading {
    enum Status {
        OK,
        NO_LOAD,
        UNSTABLE,
        ERROR
    }

    private static final int NO_PLATE = 0;
    private static final int NO_SEQUENCE = -1;

    private final int plateNumber;
    private final float weightGrams;
    private final Status status;
    private final int sequence;

    BluetoothReading(float weightGrams, Status status, int sequence) {
        this(NO_PLATE, weightGrams, status, sequence);
    }

    static BluetoothReading forPlate(
            int plateNumber,
            float weightGrams,
            Status status
    ) {
        return new BluetoothReading(
                plateNumber,
                weightGrams,
                status,
                NO_SEQUENCE
        );
    }

    private BluetoothReading(
            int plateNumber,
            float weightGrams,
            Status status,
            int sequence
    ) {
        this.plateNumber = plateNumber;
        this.weightGrams = weightGrams;
        this.status = status;
        this.sequence = sequence;
    }

    boolean hasPlateNumber() {
        return plateNumber != NO_PLATE;
    }

    int getPlateNumber() {
        return plateNumber;
    }

    float getWeightGrams() {
        return weightGrams;
    }

    Status getStatus() {
        return status;
    }

    int getSequence() {
        return sequence;
    }
}
