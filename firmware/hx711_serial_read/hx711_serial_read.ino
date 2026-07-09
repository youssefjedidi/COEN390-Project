#include "HX711.h"

constexpr int HX711_DOUT_PIN = 16;
constexpr int HX711_SCK_PIN = 4;

constexpr byte SAMPLE_COUNT = 10;
constexpr unsigned long PRINT_INTERVAL_MS = 500;

HX711 scale;

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println();
  Serial.println("HX711 reading test");
  Serial.println("Send 't' to tare the empty load cell again.");

  scale.begin(HX711_DOUT_PIN, HX711_SCK_PIN);

  if (!scale.wait_ready_timeout(3000)) {
    Serial.println("HX711 not ready. Check the wiring and power.");
    return;
  }

  Serial.println("Remove all weight from the load cell.");
  Serial.println("Taring in 3 seconds...");
  delay(3000);
  scale.tare(20);
  Serial.println("Tare complete.");
}

void loop() {
  if (Serial.available() > 0) {
    char command = Serial.read();

    if (command == 't' || command == 'T') {
      if (!scale.wait_ready_timeout(1000)) {
        Serial.println("Cannot tare because the HX711 is not ready.");
        return;
      }

      Serial.println("Taring...");
      scale.tare(20);
      Serial.println("Tare complete.");
    }
  }

  // Keep the loop responsive to tare commands while limiting serial output.
  static unsigned long lastPrint = 0;
  if (millis() - lastPrint < PRINT_INTERVAL_MS) {
    return;
  }
  lastPrint = millis();

  if (!scale.wait_ready_timeout(1000)) {
    Serial.println("HX711 not ready.");
    return;
  }

  // Show the noisy sample beside the average so the filter is easy to verify.
  long rawSample = scale.read();
  long filteredRaw = scale.read_average(SAMPLE_COUNT);

  Serial.print("raw=");
  Serial.print(rawSample);
  Serial.print("\tfiltered_raw=");
  Serial.println(filteredRaw);
}
