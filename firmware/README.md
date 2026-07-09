# ESP32 Firmware

The firmware folder contains two Arduino sketches for Sprint 1:

- `hx711_serial_read/hx711_serial_read.ino` checks the load-cell wiring and compares a single HX711 sample with a ten-sample average.
- `ble_weight_service/ble_weight_service.ino` reads the load cell and publishes the filtered weight through a BLE characteristic.

Both sketches use GPIO 16 for HX711 `DOUT` and GPIO 4 for `SCK`, matching the team's current wiring.

The ESP32 Thing Plus GPIO pins use 3.3 V logic and are not 5 V-tolerant. Power the HX711 logic from 3.3 V, or confirm that the amplifier board provides a separate 3.3 V logic supply. Do not connect a 5 V `DOUT` signal directly to GPIO 16.

## BLE weight service

The integrated sketch advertises as `SmartExit-Station` and uses these UUIDs:

```text
Service:        05442887-a14c-4c36-906c-0fe1af039f9f
Characteristic: e3abbc63-b985-4c8e-8e38-d423ce320106
```

The characteristic supports read and notify. Its value follows the [Bluetooth payload contract](../docs/bluetooth_payload.md).

Before the first weight test, leave the scale empty while the ESP32 starts. When the Serial Monitor asks for calibration, place a 100 g reference weight on the scale and send `c`. The ESP32 stores the calibration factor for later restarts. Send `t` at any time to tare the empty scale again.

The sketches were compiled for the SparkFun ESP32 Thing Plus using ESP32 Arduino core `2.0.17` and `HX711 Arduino Library` version `0.7.5`. The BLE and Preferences libraries are included with the ESP32 Arduino package.
