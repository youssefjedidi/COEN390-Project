# Bluetooth Weight Payload

For Sprint 1, the ESP32 will send one UTF-8 text value in each BLE notification.

## Format

```text
weight_grams,status,sequence
```

| Field | Type | Meaning |
| --- | --- | --- |
| `weight_grams` | Non-negative integer | Current filtered tray weight in grams. Use `0` when the tray is clear or the sensor cannot provide a reading. |
| `status` | Text | One of `PRESENT`, `CLEAR`, or `ERROR`. |
| `sequence` | Integer from 0 to 65535 | Increases with each notification and returns to 0 after 65535. It is used only to spot repeated or missed messages during testing. |

## Examples

```text
146,PRESENT,17
0,CLEAR,18
0,ERROR,19
```

The ESP32 sends the filtered weight as a whole number. Fields are separated by commas, with no spaces or newline character. One BLE notification contains one complete payload.

The Android app should reject a payload when it does not contain exactly three fields, the weight or sequence is not a valid integer, or the status is not one of the three values above. An invalid payload should place the display in an error state instead of crashing the app.

The sequence number is included because it helps the team identify missed or duplicate notifications without requiring the ESP32 and phone clocks to be synchronized. With the planned 10 kg maximum load, the longest valid payload remains within 20 bytes.
