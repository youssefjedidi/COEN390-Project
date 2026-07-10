package com.coen390.smartexit;

final class BluetoothReading {
    enum Status {
        OK,
        NO_LOAD,
        UNSTABLE,
        ERROR
    }

    private final float weightGrams;
    private final Status status;
    private final int sequence;

    BluetoothReading(float weightGrams, Status status, int sequence) {
        this.weightGrams = weightGrams;
        this.status = status;
        this.sequence = sequence;
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
