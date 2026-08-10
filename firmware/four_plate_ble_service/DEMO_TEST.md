# Four-Plate BLE Demo Check

## Wiring used by the sketch

| Plate | HX711 DOUT | HX711 SCK | Calibration factor |
|---|---:|---:|---:|
| 1 | 35 | 32 | 921.91 |
| 2 | 18 | 17 | -1025.04 |
| 3 | 34 | 33 | 2545.05 |
| 4 | 19 | 23 | 2154.01 |

The SSD1306 OLED uses GPIO 21 for `SDA` and GPIO 22 for `SCL`. Its default I2C
address is `0x3C`. Confirm these pins against the physical wiring before
uploading.

| OLED | ESP32 |
|---|---:|
| SDA | GPIO 21 |
| SCL | GPIO 22 |
| VCC | 3.3 V |
| GND | GND |

## Calibrate the installed plates

Each plate needs its own calibration because the load cells, HX711 boards, and
mounts do not produce identical raw values. Use the same known mass on every
working plate. The Android flow defaults to a one-pound reference (`453.6 g`),
but the value can be changed before calibration starts.

### Calibrate from Android

1. Upload the integrated firmware and connect the Android app to the station.
2. Open **Settings**, then press **Calibrate plate readings** under **Advanced
   plate setup**.
3. Enter the reference mass in grams. Remove every item and continue; the app
   zeros the empty plates first.
4. Follow the four prompts, moving the same reference mass to the requested
   plate each time.
5. The app advances only after the ESP32 saves that plate's new factor. A failed
   plate can be retried or skipped without restarting the whole setup. Skipping
   lets the team calibrate the remaining plates, but the failed plate should
   still show `SENSOR ERROR` until its sensor or wiring is repaired.

### Serial Monitor fallback

1. Upload the sketch, open Serial Monitor at `115200` baud, and select a line
   ending such as `New Line`.
2. Leave every plate empty and send `t`.
3. Place the reference mass on Plate 1 only. Wait for it to stop moving, then
   send `c1 453.6`.
4. Move the same mass to each remaining plate and send `c2 453.6`, `c3 453.6`,
   and `c4 453.6` respectively.
5. Serial Monitor should print `calibration saved` and the new factor after
   each successful command. The factors are kept after restarting the ESP32.
6. Move the reference mass across all four plates again. Their stable readings
   should now be close to the same known mass.

If a plate reports `no meaningful weight change was detected`, calibration
cannot repair it. Check that plate's load-cell wires, HX711 terminals, mount,
and DOUT/SCK connections. Swapping its HX711 with a working channel can help
separate a failed amplifier from a failed load cell or wire.

## Five-minute test

1. Remove every item from the plates and upload `four_plate_ble_service.ino`.
2. Confirm the OLED shows the startup and tare messages. If no OLED is
   connected, Serial Monitor should report that the firmware is continuing
   without it.
3. Open Serial Monitor at `115200` baud. Confirm all four plates report `ready`.
4. Wait for tare to complete. Empty plates should publish `1,0.0,NO_LOAD` through `4,0.0,NO_LOAD`, and the OLED should show all four plates as `EMPTY`.
5. Calibrate the four installed plates using the procedure above if their
   readings do not agree for the same reference mass.
6. Open Smart Exit on the Android phone and connect to `SmartExit-Station`.
   Confirm the OLED header changes from `OFFLINE` to `CONNECTED`.
7. Place one known item on Plate 1. Confirm Serial Monitor reports `1,<weight>,OK` and the app updates after the reading stabilizes.
8. Move the item to Plate 3. Confirm Plate 1 becomes `NO_LOAD` and Plate 3 reports the item weight.
9. Repeat with all four plates occupied, then disconnect Bluetooth and confirm advertising restarts.

The OLED deliberately shows `ITEM`, `EMPTY`, `MOVING`, or `SENSOR ERROR`
instead of raw grams. Android remains responsible for recognizing and naming
the tracked item.

`UNSTABLE` while touching a plate is expected. A continuing `ERROR` means the
pin map, HX711 connection, calibration direction, or load-cell wiring must be
checked before the demo.

## Tare test

1. Connect the Android app and confirm that the station state is `Connected`.
2. Remove every item from all four plates.
3. Open Settings and press the tare control once.
4. Confirm Serial Monitor prints `BLE tare request queued`, followed by a
   successful tare message for Plates 1 through 4.
5. Wait for weight notifications to resume. Every plate should settle at
   `0.0,NO_LOAD` within the normal 20 g clear threshold.
6. Place the known reference item on each plate in turn. Confirm the measured
   weight still matches its calibrated value; tare must not alter calibration.

For the failure case, disconnect one HX711 while the station is powered off,
restart the station, and send another tare request. Serial Monitor should name
the failed plate and finish with `BLE tare request failed`. Reconnect the HX711
before continuing the demo.
