# Bluetooth Weight Payload

For Sprint 1, the ESP32 will send one UTF-8 text value in each BLE notification.

## Format

```text
weight_grams,status,sequence
```

| Field | Type | Meaning |
| --- | --- | --- |
| `weight_grams` | Decimal number | Current filtered tray weight in grams, rounded to one decimal place. |
| `status` | Text | One of `OK`, `NO_LOAD`, `UNSTABLE`, or `ERROR`. |
| `sequence` | Integer from 0 to 9999 | Increases with each notification and returns to 0 after 9999. It is used only to spot repeated or missed messages during testing. |

## Examples

```text
142.3,OK,7
0.0,NO_LOAD,8
141.8,UNSTABLE,9
0.0,ERROR,10
```

Fields are separated by commas, with no spaces or newline character. One BLE notification contains one complete payload, sent approximately every 500 ms while a phone is connected.

`OK` means the sensor produced a stable reading above the no-load threshold. `NO_LOAD` means the stable reading is within 5 g of zero. `UNSTABLE` means the difference between the highest and lowest sample in the current reading is greater than 5 g. These thresholds are starting values and may be adjusted after testing the physical load cell. `ERROR` means the sensor is unavailable, uncalibrated, or outside its expected range.

The Android app should split the value on commas in the order shown above. It should reject a payload when it does not contain exactly three fields, the weight is not a valid decimal number, the sequence is not an integer from 0 to 9999, or the status is not one of the four values above. An invalid payload should place the display in an error state instead of crashing the app.

The sequence number is included because it helps the team identify missed or duplicate notifications without requiring the ESP32 and phone clocks to be synchronized. With the team's current 1 kg load cell, the longest valid payload remains within 20 bytes.
