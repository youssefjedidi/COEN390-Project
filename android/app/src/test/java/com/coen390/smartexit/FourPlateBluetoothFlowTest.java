package com.coen390.smartexit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FourPlateBluetoothFlowTest {

    private static final int PLATE_COUNT = 4;
    private static final double EMPTY_WEIGHT_THRESHOLD_GRAMS = 1.0;


    @Test
    public void completeFourPlateSnapshot_updatesItemsOnCorrectPlates() {
        ItemProfile keys = calibratedItem(
                "keys",
                "Keys",
                36.0,
                42.0
        );

        ItemProfile wallet = calibratedItem(
                "wallet",
                "Wallet",
                115.0,
                125.0
        );

        ItemProfile medication = calibratedItem(
                "medication",
                "Medication",
                70.0,
                80.0
        );

        ItemProfile phone = calibratedItem(
                "phone",
                "Phone",
                190.0,
                210.0
        );

        List<ItemProfile> savedProfiles = Arrays.asList(
                keys,
                wallet,
                medication,
                phone
        );

        ItemRecognizer recognizer = new ItemRecognizer();
        ItemStateTracker tracker = new ItemStateTracker(
                savedProfiles,
                PLATE_COUNT
        );

        List<String> payloads = Arrays.asList(
                "1,39.0,OK",
                "2,120.0,OK",
                "3,75.0,OK",
                "4,200.0,OK"
        );

        List<RecognitionResult> snapshot = parseCompleteSnapshot(
                payloads,
                recognizer,
                savedProfiles
        );

        List<TrackedItemState> states = tracker.update(snapshot);

        assertItemState(
                states,
                keys,
                TrackedItemStatus.PRESENT,
                1
        );

        assertItemState(
                states,
                wallet,
                TrackedItemStatus.PRESENT,
                2
        );

        assertItemState(
                states,
                medication,
                TrackedItemStatus.PRESENT,
                3
        );

        assertItemState(
                states,
                phone,
                TrackedItemStatus.PRESENT,
                4
        );
    }

    /* THAOMY COM-5.4 (We can remove my comments afterwards)  Verifies that an incomplete snapshot containing only three plate messages is rejected before the tracked item state changes.*/
    @Test
    public void missingPlateMessage_doesNotChangePreviousState() {
        ItemProfile keys = calibratedItem(
                "keys",
                "Keys",
                36.0,
                42.0
        );

        List<ItemProfile> savedProfiles =
                Arrays.asList(keys);

        ItemRecognizer recognizer = new ItemRecognizer();
        ItemStateTracker tracker = new ItemStateTracker(
                savedProfiles,
                PLATE_COUNT
        );

        List<RecognitionResult> initialSnapshot =
                parseCompleteSnapshot(
                        Arrays.asList(
                                "1,39.0,OK",
                                "2,0.0,NO_LOAD",
                                "3,0.0,NO_LOAD",
                                "4,0.0,NO_LOAD"
                        ),
                        recognizer,
                        savedProfiles
                );

        List<TrackedItemState> initialStates =
                tracker.update(initialSnapshot);

        assertItemState(
                initialStates,
                keys,
                TrackedItemStatus.PRESENT,
                1
        );

        List<RecognitionResult> incompleteSnapshot =
                parseAvailablePayloads(
                        Arrays.asList(
                                "1,0.0,NO_LOAD",
                                "2,0.0,NO_LOAD",
                                "3,39.0,OK"
                        ),
                        recognizer,
                        savedProfiles
                );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tracker.update(incompleteSnapshot)
        );

        assertEquals(
                "plate snapshot must contain 4 results, but contained 3",
                exception.getMessage()
        );

        /*
         * The failed update must not replace or partially modify
         * the last valid state.
         */
        assertSame(initialStates, tracker.getStates());

        assertItemState(
                tracker.getStates(),
                keys,
                TrackedItemStatus.PRESENT,
                1
        );
    }

    /* THAO-MY COM-5.4: Verifies that one malformed Bluetooth message is rejected by the parser and therefore cannot partially update the previous four-plate item state.*/
    @Test
    public void malformedPlateMessage_doesNotChangePreviousState() {
        ItemProfile wallet = calibratedItem(
                "wallet",
                "Wallet",
                115.0,
                125.0
        );

        List<ItemProfile> savedProfiles =
                Arrays.asList(wallet);

        ItemRecognizer recognizer = new ItemRecognizer();
        ItemStateTracker tracker = new ItemStateTracker(
                savedProfiles,
                PLATE_COUNT
        );

        List<RecognitionResult> initialSnapshot =
                parseCompleteSnapshot(
                        Arrays.asList(
                                "1,0.0,NO_LOAD",
                                "2,120.0,OK",
                                "3,0.0,NO_LOAD",
                                "4,0.0,NO_LOAD"
                        ),
                        recognizer,
                        savedProfiles
                );

        List<TrackedItemState> initialStates =
                tracker.update(initialSnapshot);

        assertItemState(
                initialStates,
                wallet,
                TrackedItemStatus.PRESENT,
                2
        );

        BluetoothPayloadParser.ParseResult malformedResult =
                BluetoothPayloadParser.parse(
                        "3,not-a-weight,OK"
                );

        assertFalse(malformedResult.isValid());
        assertNotNull(malformedResult.getErrorMessage());

        /* Because the malformed payload was rejected, no incomplete or partially processed snapshot is sent to the tracker.*/
        assertSame(initialStates, tracker.getStates());

        assertItemState(
                tracker.getStates(),
                wallet,
                TrackedItemStatus.PRESENT,
                2
        );
    }

    private List<RecognitionResult> parseCompleteSnapshot(
            List<String> payloads,
            ItemRecognizer recognizer,
            List<ItemProfile> savedProfiles
    ) {
        assertEquals(
                "A complete snapshot requires four payloads",
                PLATE_COUNT,
                payloads.size()
        );

        return parseAvailablePayloads(
                payloads,
                recognizer,
                savedProfiles
        );
    }

    private List<RecognitionResult> parseAvailablePayloads(
            List<String> payloads,
            ItemRecognizer recognizer,
            List<ItemProfile> savedProfiles
    ) {
        List<RecognitionResult> snapshot = new ArrayList<>();

        for (String payload : payloads) {
            BluetoothPayloadParser.ParseResult parseResult =
                    BluetoothPayloadParser.parse(payload);

            assertTrue(
                    parseResult.getErrorMessage(),
                    parseResult.isValid()
            );

            BluetoothReading bluetoothReading =
                    parseResult.getReading();

            assertNotNull(bluetoothReading);
            assertTrue(bluetoothReading.hasPlateNumber());

            PlateReading plateReading = new PlateReading(
                    bluetoothReading.getPlateNumber(),
                    bluetoothReading.getWeightGrams()
            );

            snapshot.add(
                    recognizeReading(
                            plateReading,
                            bluetoothReading.getStatus(),
                            recognizer,
                            savedProfiles
                    )
            );
        }

        return snapshot;
    }

    private RecognitionResult recognizeReading(
            PlateReading reading,
            BluetoothReading.Status bluetoothStatus,
            ItemRecognizer recognizer,
            List<ItemProfile> savedProfiles
    ) {
        if (bluetoothStatus == BluetoothReading.Status.NO_LOAD
                || Math.abs(reading.getWeightGrams())
                <= EMPTY_WEIGHT_THRESHOLD_GRAMS) {
            return RecognitionResult.empty(reading);
        }

        /* Only a valid and stable OK reading should be passed into item recognition.*/
        if (bluetoothStatus != BluetoothReading.Status.OK) {
            return RecognitionResult.unknown(reading);
        }

        return recognizer.recognize(
                reading,
                savedProfiles
        );
    }

    private ItemProfile calibratedItem(
            String id,
            String name,
            double minimumWeight,
            double maximumWeight
    ) {
        return new ItemProfile(
                id,
                name,
                minimumWeight,
                maximumWeight
        );
    }

    private void assertItemState(
            List<TrackedItemState> states,
            ItemProfile expectedItem,
            TrackedItemStatus expectedStatus,
            Integer expectedPlateNumber
    ) {
        TrackedItemState matchingState = null;

        for (TrackedItemState state : states) {
            if (state.getItem()
                    .getId()
                    .equals(expectedItem.getId())) {
                matchingState = state;
                break;
            }
        }

        assertNotNull(
                "A tracked state should exist for "
                        + expectedItem.getName(),
                matchingState
        );

        assertEquals(
                expectedStatus,
                matchingState.getStatus()
        );

        assertEquals(
                expectedPlateNumber,
                matchingState.getPlateNumber()
        );
    }
}
