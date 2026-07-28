package com.coen390.smartexit;

final class BluetoothPayloadParser {
    private static final int MIN_PLATE_NUMBER = 1;
    private static final int MAX_PLATE_NUMBER = 4;
    private static final float MAX_WEIGHT_GRAMS = 1000.0f;
    private static final int MAX_SEQUENCE = 9999;

    private BluetoothPayloadParser() {
    }

    static ParseResult parse(String payload) {
        if (payload == null || payload.isEmpty()) {
            return ParseResult.invalid("Payload is empty.");
        }

        if (!payload.equals(payload.trim())) {
            return ParseResult.invalid("Payload contains unexpected whitespace.");
        }

        String[] fields = payload.split(",", -1);
        if (fields.length != 3) {
            return ParseResult.invalid("Expected three comma-separated fields.");
        }

        BluetoothReading.Status legacyStatus = parseStatus(fields[1]);
        if (legacyStatus != null) {
            return parseLegacyReading(fields, legacyStatus);
        }

        return parsePlateReading(fields);
    }

    private static ParseResult parseLegacyReading(
            String[] fields,
            BluetoothReading.Status status
    ) {
        Float weight = parseWeight(fields[0]);
        if (weight == null) {
            return ParseResult.invalid("Weight is not a valid value.");
        }

        Integer sequence = parseSequence(fields[2]);
        if (sequence == null) {
            return ParseResult.invalid("Sequence is not valid.");
        }

        return ParseResult.valid(new BluetoothReading(weight, status, sequence));
    }

    private static ParseResult parsePlateReading(String[] fields) {
        Integer plateNumber = parsePlateNumber(fields[0]);
        if (plateNumber == null) {
            return ParseResult.invalid("Plate number must be between 1 and 4.");
        }

        Float weight = parseWeight(fields[1]);
        if (weight == null) {
            return ParseResult.invalid("Weight is not a valid value.");
        }

        BluetoothReading.Status status = parseStatus(fields[2]);
        if (status == null) {
            return ParseResult.invalid("Status is not recognized.");
        }

        return ParseResult.valid(
                BluetoothReading.forPlate(plateNumber, weight, status)
        );
    }

    private static Integer parsePlateNumber(String field) {
        try {
            int plateNumber = Integer.parseInt(field);
            if (plateNumber < MIN_PLATE_NUMBER || plateNumber > MAX_PLATE_NUMBER) {
                return null;
            }
            return plateNumber;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Float parseWeight(String field) {
        try {
            float weight = Float.parseFloat(field);
            if (!Float.isFinite(weight) || weight < 0.0f || weight > MAX_WEIGHT_GRAMS) {
                return null;
            }
            return weight;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static BluetoothReading.Status parseStatus(String field) {
        try {
            return BluetoothReading.Status.valueOf(field);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Integer parseSequence(String field) {
        try {
            int sequence = Integer.parseInt(field);
            if (sequence < 0 || sequence > MAX_SEQUENCE) {
                return null;
            }
            return sequence;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static final class ParseResult {
        private final BluetoothReading reading;
        private final String errorMessage;

        private ParseResult(BluetoothReading reading, String errorMessage) {
            this.reading = reading;
            this.errorMessage = errorMessage;
        }

        static ParseResult valid(BluetoothReading reading) {
            return new ParseResult(reading, null);
        }

        static ParseResult invalid(String errorMessage) {
            return new ParseResult(null, errorMessage);
        }

        boolean isValid() {
            return reading != null;
        }

        BluetoothReading getReading() {
            return reading;
        }

        String getErrorMessage() {
            return errorMessage;
        }
    }
}
