# ESP32 Firmware

The firmware folder contains three Arduino sketches:

- `hx711_serial_read/hx711_serial_read.ino` checks the load-cell wiring and compares a single HX711 sample with a ten-sample average.
- `ble_weight_service/ble_weight_service.ino` reads the load cell and publishes the filtered weight through a BLE characteristic.
- `four_plate_ble_service/four_plate_ble_service.ino` reads all four Sprint 2 plates and notifies Android once per plate.

The first two sketches use GPIO 16 for HX711 `DOUT` and GPIO 4 for `SCK`.
The four-plate pin map and physical test sequence are recorded in
`four_plate_ble_service/DEMO_TEST.md`.

The integrated four-plate sketch supports a 128 x 64 SSD1306 I2C OLED at
address `0x3C`. Connect the display to GPIO 21 (`SDA`) and GPIO 22 (`SCL`).
The firmware continues running if no display is detected. Install the
`Adafruit SSD1306` and `Adafruit GFX Library` Arduino libraries before compiling
the sketch.

The ESP32 Dev Module GPIO pins use 3.3 V logic and are not 5 V-tolerant. Power the HX711 logic from 3.3 V, or confirm that the amplifier board provides a separate 3.3 V logic supply. Do not connect a 5 V `DOUT` signal directly to GPIO 16.

## BLE weight service

The integrated sketch advertises as `SmartExit-Station` and uses these UUIDs:

```text
Service: 05442887-a14c-4c36-906c-0fe1af039f9f
Weight:  e3abbc63-b985-4c8e-8e38-d423ce320106
Command: e3abbc63-b985-4c8e-8e38-d423ce320107
```

The weight characteristic supports read and notify. The command characteristic
supports read and write. Their values follow the
[Bluetooth payload contract](../docs/bluetooth_payload.md).

The four-plate sketch sends one notification per plate in this form:

```text
plate_number,weight_grams,status
```

For example, `3,146.2,OK` reports a stable 146.2 g reading on Plate 3.

Before the first weight test, leave every plate empty while the ESP32 starts.
Send `t` in Serial Monitor or use the app's tare control to zero all four
plates. Taring changes the zero offset but keeps the four calibration factors
saved by the sketch. To calibrate a plate with a known mass, use a command such
as `c1 453.6` for a one-pound reference on Plate 1, or use **Calibrate plate
readings** in the Android Settings screen. Repeat for each plate using the same
reference. Full instructions are in
`four_plate_ble_service/DEMO_TEST.md`.

The sketches were compiled using Arduino's `ESP32 Dev Module` board profile, ESP32 Arduino core `2.0.17`, and `HX711 Arduino Library` version `0.7.5`. The BLE and Preferences libraries are included with the ESP32 Arduino package.
