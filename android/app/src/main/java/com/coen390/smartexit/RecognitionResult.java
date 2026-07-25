package com.coen390.smartexit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecognitionResult {

    private final RecognitionStatus status;
    private final PlateReading reading;
    private final List<ItemProfile> candidates;

    private RecognitionResult(
            RecognitionStatus status,
            PlateReading reading,
            List<ItemProfile> candidates
    ) {
        this.status = status;
        this.reading = reading;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
    }

    public static RecognitionResult matched(PlateReading reading, ItemProfile item) {
        if (item == null) {
            throw new IllegalArgumentException("a matched result requires an item");
        }

        return new RecognitionResult(
                RecognitionStatus.MATCHED,
                requireReading(reading),
                Collections.singletonList(item)
        );
    }

    public static RecognitionResult ambiguous(
            PlateReading reading,
            List<ItemProfile> candidates
    ) {
        if (candidates == null) {
            throw new IllegalArgumentException(
                    "an ambiguous result requires a candidate list"
            );
        }
        if (candidates.size() < 2) {
            throw new IllegalArgumentException(
                    "an ambiguous result requires at least two candidates, but received "
                            + candidates.size()
            );
        }
        if (candidates.contains(null)) {
            throw new IllegalArgumentException(
                    "an ambiguous result cannot contain a null candidate"
            );
        }

        return new RecognitionResult(
                RecognitionStatus.AMBIGUOUS,
                requireReading(reading),
                candidates
        );
    }

    public static RecognitionResult unknown(PlateReading reading) {
        return new RecognitionResult(
                RecognitionStatus.UNKNOWN,
                requireReading(reading),
                Collections.emptyList()
        );
    }

    public static RecognitionResult empty(PlateReading reading) {
        return new RecognitionResult(
                RecognitionStatus.EMPTY,
                requireReading(reading),
                Collections.emptyList()
        );
    }

    private static PlateReading requireReading(PlateReading reading) {
        if (reading == null) {
            throw new IllegalArgumentException("a recognition result requires a reading");
        }
        return reading;
    }

    public RecognitionStatus getStatus() {
        return status;
    }

    public PlateReading getReading() {
        return reading;
    }

    public List<ItemProfile> getCandidates() {
        return candidates;
    }
}
