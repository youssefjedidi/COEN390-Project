# Four-Plate BLE Demo Check

## Wiring used by the sketch

| Plate | HX711 DOUT | HX711 SCK | Calibration factor |
|---|---:|---:|---:|
| 1 | 16 | 4 | 2170.77 |
| 2 | 18 | 17 | 923.52 |
| 3 | 21 | 22 | 2563.81 |
| 4 | 19 | 23 | 1840.87 |

Confirm these pins against the physical wiring before uploading.

## Five-minute test

1. Remove every item from the plates and upload `four_plate_ble_service.ino`.
2. Open Serial Monitor at `115200` baud. Confirm all four plates report `ready`.
3. Wait for tare to complete. Empty plates should publish `1,0.0,NO_LOAD` through `4,0.0,NO_LOAD`.
4. Open Smart Exit on the Android phone and connect to `SmartExit-Station`.
5. Place one known item on Plate 1. Confirm Serial Monitor reports `1,<weight>,OK` and the app updates after the reading stabilizes.
6. Move the item to Plate 3. Confirm Plate 1 becomes `NO_LOAD` and Plate 3 reports the item weight.
7. Repeat with all four plates occupied, then disconnect Bluetooth and confirm advertising restarts.

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
   `0.0,NO_LOAD` within the normal 5 g clear threshold.
6. Place the known reference item on each plate in turn. Confirm the measured
   weight still matches its calibrated value; tare must not alter calibration.

For the failure case, disconnect one HX711 while the station is powered off,
restart the station, and send another tare request. Serial Monitor should name
the failed plate and finish with `BLE tare request failed`. Reconnect the HX711
before continuing the demo.
