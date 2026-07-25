package com.coen390.smartexit;

import java.util.ArrayList;
import java.util.List;

final class ItemRecognizer {

    RecognitionResult recognize(PlateReading reading, List<ItemProfile> savedProfiles) {
        if (reading == null) {
            throw new IllegalArgumentException("item recognition requires a plate reading");
        }
        if (savedProfiles == null) {
            throw new IllegalArgumentException("item recognition requires saved profiles");
        }

        List<ItemProfile> candidates = new ArrayList<>();
        for (ItemProfile profile : savedProfiles) {
            if (profile == null) {
                throw new IllegalArgumentException("saved profiles cannot contain null");
            }
            if (profile.matches(reading.getWeightGrams())) {
                candidates.add(profile);
            }
        }

        if (candidates.isEmpty()) {
            return RecognitionResult.unknown(reading);
        }
        if (candidates.size() == 1) {
            return RecognitionResult.matched(reading, candidates.get(0));
        }
        return RecognitionResult.ambiguous(reading, candidates);
    }
}
