package com.coen390.smartexit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BluetoothPayloadParserTest {
    @Test
    public void parsesNormalWeightReading() {
        BluetoothPayloadParser.ParseResult result = BluetoothPayloadParser.parse("142.3,OK,7");

        assertTrue(result.getErrorMessage(), result.isValid());
        BluetoothReading reading = result.getReading();
        assertNotNull(reading);
        assertEquals(142.3f, reading.getWeightGrams(), 0.001f);
        assertEquals(BluetoothReading.Status.OK, reading.getStatus());
        assertEquals(7, reading.getSequence());
    }

    @Test
    public void acceptsEveryDocumentedStatus() {
        assertStatus("0.0,NO_LOAD,8", BluetoothReading.Status.NO_LOAD);
        assertStatus("141.8,UNSTABLE,9", BluetoothReading.Status.UNSTABLE);
        assertStatus("0.0,ERROR,10", BluetoothReading.Status.ERROR);
    }

    @Test
    public void acceptsContractBoundaries() {
        assertTrue(BluetoothPayloadParser.parse("0.0,NO_LOAD,0").isValid());
        assertTrue(BluetoothPayloadParser.parse("1000.0,OK,9999").isValid());
    }

    @Test
    public void rejectsWrongNumberOfFields() {
        assertInvalid("142.3,OK");
        assertInvalid("142.3,OK,7,extra");
        assertInvalid("");
        assertInvalid(null);
    }

    @Test
    public void rejectsInvalidWeight() {
        assertInvalid("abc,OK,7");
        assertInvalid("NaN,OK,7");
        assertInvalid("-0.1,OK,7");
        assertInvalid("1000.1,OK,7");
    }

    @Test
    public void rejectsInvalidStatusOrSequence() {
        assertInvalid("142.3,UNKNOWN,7");
        assertInvalid("142.3,OK,-1");
        assertInvalid("142.3,OK,10000");
        assertInvalid("142.3,OK,abc");
    }

    @Test
    public void rejectsUnexpectedWhitespace() {
        assertInvalid(" 142.3,OK,7");
        assertInvalid("142.3, OK,7");
        assertInvalid("142.3,OK,7\n");
    }

    private void assertStatus(String payload, BluetoothReading.Status expectedStatus) {
        BluetoothPayloadParser.ParseResult result = BluetoothPayloadParser.parse(payload);

        assertTrue(result.getErrorMessage(), result.isValid());
        assertNotNull(result.getReading());
        assertEquals(expectedStatus, result.getReading().getStatus());
    }

    private void assertInvalid(String payload) {
        BluetoothPayloadParser.ParseResult result = BluetoothPayloadParser.parse(payload);

        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }
}
