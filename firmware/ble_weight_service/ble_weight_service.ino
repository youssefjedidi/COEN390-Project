#include <Arduino.h>
#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <Preferences.h>

#include "HX711.h"

constexpr int HX711_DOUT_PIN = 16;
constexpr int HX711_SCK_PIN = 4;

constexpr byte SAMPLE_COUNT = 5;
constexpr float CALIBRATION_MASS_GRAMS = 100.0f;
constexpr float CLEAR_THRESHOLD_GRAMS = 5.0f;
constexpr float STABILITY_THRESHOLD_GRAMS = 5.0f;
constexpr float MAX_WEIGHT_GRAMS = 1000.0f;
constexpr unsigned long NOTIFY_INTERVAL_MS = 500;
constexpr size_t PAYLOAD_BUFFER_SIZE = 21;
constexpr uint16_t MAX_SEQUENCE_NUMBER = 9999;

constexpr char DEVICE_NAME[] = "SmartExit-Station";
constexpr char SERVICE_UUID[] = "05442887-a14c-4c36-906c-0fe1af039f9f";
constexpr char WEIGHT_CHARACTERISTIC_UUID[] = "e3abbc63-b985-4c8e-8e38-d423ce320106";
constexpr char PREFERENCES_NAMESPACE[] = "smart_exit";
constexpr char CALIBRATION_KEY[] = "cal_factor";

HX711 scale;
Preferences preferences;

BLEServer *bleServer = nullptr;
BLECharacteristic *weightCharacteristic = nullptr;

volatile bool deviceConnected = false;
bool previousConnectionState = false;
bool loadCellReady = false;
bool scaleCalibrated = false;
bool preferencesReady = false;
uint16_t sequenceNumber = 0;

enum class WeightStatus {
  Ok,
  NoLoad,
  Unstable,
  Error
};

struct WeightReading {
  float grams;
  WeightStatus status;
};

class StationServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *) override {
    deviceConnected = true;
    Serial.println("BLE client connected.");
  }

  void onDisconnect(BLEServer *) override {
    deviceConnected = false;
    Serial.println("BLE client disconnected.");
  }
};

void startBluetoothService() {
  BLEDevice::init(DEVICE_NAME);

  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new StationServerCallbacks());

  BLEService *weightService = bleServer->createService(SERVICE_UUID);
  weightCharacteristic = weightService->createCharacteristic(
      WEIGHT_CHARACTERISTIC_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  weightCharacteristic->addDescriptor(new BLE2902());
  weightCharacteristic->setValue("0.0,ERROR,0");

  weightService->start();

  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->start();

  Serial.println("BLE weight service is advertising.");
}

bool applySavedCalibration() {
  if (!preferencesReady) {
    return false;
  }

  float savedFactor = preferences.getFloat(CALIBRATION_KEY, 0.0f);
  if (fabsf(savedFactor) <= 0.01f) {
    return false;
  }

  scale.set_scale(savedFactor);
  return true;
}

void startLoadCell() {
  scale.begin(HX711_DOUT_PIN, HX711_SCK_PIN);
  scale.set_scale();

  if (!scale.wait_ready_timeout(3000)) {
    Serial.println("HX711 not ready. BLE will report an error state.");
    return;
  }

  Serial.println("Remove all weight from the load cell.");
  Serial.println("Taring in 3 seconds...");
  delay(3000);
  scale.tare(20);
  loadCellReady = true;

  // A calibration factor belongs to one physical scale, so load the value
  // measured on this ESP32 instead of borrowing a number from another unit.
  if (applySavedCalibration()) {
    scaleCalibrated = true;
    Serial.println("Saved load-cell calibration loaded.");
    return;
  }

  Serial.println("No calibration is saved.");
  Serial.println("Place a 100 g reference weight on the scale, then send 'c'.");
}

void calibrateLoadCell() {
  if (!loadCellReady) {
    Serial.println("Cannot calibrate because the HX711 is not ready.");
    return;
  }

  if (!scale.wait_ready_timeout(1000)) {
    Serial.println("Cannot calibrate because the HX711 stopped responding.");
    return;
  }

  long referenceReading = scale.get_value(20);
  float calibrationFactor = referenceReading / CALIBRATION_MASS_GRAMS;

  if (fabsf(calibrationFactor) <= 0.01f) {
    Serial.println("Calibration failed. Check that the 100 g weight is on the scale.");
    return;
  }

  scale.set_scale(calibrationFactor);
  scaleCalibrated = true;

  bool calibrationSaved =
      preferencesReady &&
      preferences.putFloat(CALIBRATION_KEY, calibrationFactor) == sizeof(float);

  if (calibrationSaved) {
    Serial.print("Calibration saved. Factor: ");
  } else {
    Serial.print("Calibration is active but was not saved. Factor: ");
  }
  Serial.println(calibrationFactor, 4);
}

void handleSerialCommand() {
  if (Serial.available() == 0) {
    return;
  }

  char command = Serial.read();

  if (command == 'c' || command == 'C') {
    calibrateLoadCell();
    return;
  }

  if (command == 't' || command == 'T') {
    if (!loadCellReady) {
      Serial.println("Cannot tare because the HX711 is not ready.");
      return;
    }

    if (!scale.wait_ready_timeout(1000)) {
      Serial.println("Cannot tare because the HX711 stopped responding.");
      return;
    }

    Serial.println("Taring empty load cell...");
    scale.tare(20);
    Serial.println("Tare complete.");
  }
}

WeightReading readWeight() {
  if (!loadCellReady || !scaleCalibrated ||
      !scale.wait_ready_timeout(1000)) {
    return {0.0f, WeightStatus::Error};
  }

  float totalWeight = 0.0f;
  float minimumWeight = MAX_WEIGHT_GRAMS;
  float maximumWeight = -MAX_WEIGHT_GRAMS;

  // The average and stability check use the same samples, so the status
  // describes the exact readings that produced the transmitted weight.
  for (byte sample = 0; sample < SAMPLE_COUNT; sample++) {
    if (!scale.wait_ready_timeout(250)) {
      return {0.0f, WeightStatus::Error};
    }

    float weight = scale.get_units(1);
    if (!isfinite(weight)) {
      return {0.0f, WeightStatus::Error};
    }

    totalWeight += weight;
    minimumWeight = min(minimumWeight, weight);
    maximumWeight = max(maximumWeight, weight);
  }

  float filteredWeight = totalWeight / SAMPLE_COUNT;

  if (filteredWeight < -CLEAR_THRESHOLD_GRAMS ||
      filteredWeight > MAX_WEIGHT_GRAMS) {
    return {0.0f, WeightStatus::Error};
  }

  if (maximumWeight - minimumWeight > STABILITY_THRESHOLD_GRAMS) {
    return {max(0.0f, filteredWeight), WeightStatus::Unstable};
  }

  if (filteredWeight >= -CLEAR_THRESHOLD_GRAMS &&
      filteredWeight <= CLEAR_THRESHOLD_GRAMS) {
    return {0.0f, WeightStatus::NoLoad};
  }

  return {filteredWeight, WeightStatus::Ok};
}

const char *statusText(WeightStatus status) {
  switch (status) {
    case WeightStatus::Ok:
      return "OK";
    case WeightStatus::NoLoad:
      return "NO_LOAD";
    case WeightStatus::Unstable:
      return "UNSTABLE";
    case WeightStatus::Error:
      return "ERROR";
  }

  return "ERROR";
}

void formatWeightPayload(
    const WeightReading &reading,
    char *payload,
    size_t payloadSize) {
  snprintf(
      payload,
      payloadSize,
      "%.1f,%s,%u",
      reading.grams,
      statusText(reading.status),
      sequenceNumber);
}

void publishWeightReading() {
  WeightReading reading = readWeight();
  char payload[PAYLOAD_BUFFER_SIZE];
  formatWeightPayload(reading, payload, sizeof(payload));

  weightCharacteristic->setValue(payload);
  Serial.print("BLE payload: ");
  Serial.println(payload);

  if (deviceConnected) {
    weightCharacteristic->notify();
    sequenceNumber = sequenceNumber >= MAX_SEQUENCE_NUMBER
                         ? 0
                         : sequenceNumber + 1;
  }
}

void updateAdvertisingAfterConnectionChange() {
  if (!deviceConnected && previousConnectionState) {
    delay(500);
    bleServer->startAdvertising();
    previousConnectionState = false;
    Serial.println("BLE advertising restarted.");
    return;
  }

  if (deviceConnected && !previousConnectionState) {
    previousConnectionState = true;
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println();
  Serial.println("Smart Exit BLE weight service");
  Serial.println("Send 't' to tare or 'c' to calibrate with 100 g.");

  preferencesReady = preferences.begin(PREFERENCES_NAMESPACE, false);
  if (!preferencesReady) {
    Serial.println("Preferences storage is unavailable; calibration will last until restart.");
  }

  startLoadCell();
  startBluetoothService();
}

void loop() {
  handleSerialCommand();
  updateAdvertisingAfterConnectionChange();

  static unsigned long lastNotification = 0;
  if (millis() - lastNotification < NOTIFY_INTERVAL_MS) {
    return;
  }
  lastNotification = millis();

  publishWeightReading();
}
