#include <Arduino.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <HX711.h>
#include <Preferences.h>
#include <Wire.h>

constexpr int PLATE_COUNT = 4;
constexpr int SAMPLE_COUNT = 9;
constexpr int TRIMMED_SAMPLE_COUNT = 5;

const int DOUT_PINS[PLATE_COUNT] = {35, 18, 34, 19};
const int SCK_PINS[PLATE_COUNT] = {32, 17, 33, 23};
const float CALIBRATION_FACTORS[PLATE_COUNT] = {
    921.91f,
    -1025.04f,
    2545.05f,
    2154.01f
};

constexpr float CLEAR_THRESHOLD_GRAMS = 20.0f;
constexpr float STABILITY_THRESHOLD_GRAMS = 5.0f;
constexpr float STABILITY_THRESHOLD_PERCENT = 0.04f;
constexpr float MAX_WEIGHT_GRAMS = 1000.0f;
constexpr float MIN_CALIBRATION_MASS_GRAMS = 20.0f;
constexpr float MIN_CALIBRATION_FACTOR = 10.0f;
constexpr size_t PAYLOAD_SIZE = 24;
constexpr size_t SERIAL_COMMAND_SIZE = 32;
constexpr char PREFERENCES_NAMESPACE[] = "plate_scales";

constexpr int SCREEN_WIDTH = 128;
constexpr int SCREEN_HEIGHT = 64;
constexpr int OLED_SDA_PIN = 21;
constexpr int OLED_SCL_PIN = 22;
constexpr int OLED_RESET_PIN = -1;
constexpr uint8_t OLED_ADDRESS = 0x3C;

constexpr char DEVICE_NAME[] = "SmartExit-Station";
constexpr char SERVICE_UUID[] = "05442887-a14c-4c36-906c-0fe1af039f9f";
constexpr char WEIGHT_CHARACTERISTIC_UUID[] =
    "e3abbc63-b985-4c8e-8e38-d423ce320106";
constexpr char COMMAND_CHARACTERISTIC_UUID[] =
    "e3abbc63-b985-4c8e-8e38-d423ce320107";

HX711 scales[PLATE_COUNT];
bool scaleAvailable[PLATE_COUNT] = {};
Preferences preferences;
bool preferencesAvailable = false;
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET_PIN);
bool displayAvailable = false;

BLEServer *bleServer = nullptr;
BLECharacteristic *weightCharacteristic = nullptr;
BLECharacteristic *commandCharacteristic = nullptr;
volatile bool deviceConnected = false;
volatile bool tareRequested = false;
volatile bool calibrationRequested = false;
int requestedCalibrationPlate = 0;
float requestedCalibrationMass = 0.0f;
bool previousConnectionState = false;

enum class WeightStatus {
  Ok,
  NoLoad,
  Unstable,
  Error
};

enum class TareResult {
  AllScales,
  AvailableScales,
  Failed
};

struct PlateReading {
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

class StationCommandCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *characteristic) override {
    auto command = characteristic->getValue();

    if (command == "TARE") {
      if (tareRequested || calibrationRequested) {
        characteristic->setValue("TARE_FAILED");
        characteristic->notify();
        return;
      }

      tareRequested = true;
      characteristic->setValue("TARE_QUEUED");
      Serial.println("BLE tare request queued.");
      return;
    }

    int plateNumber = 0;
    float referenceMass = 0.0f;
    char trailingCharacter = '\0';
    bool validCalibrationCommand =
        sscanf(
            command.c_str(),
            "CALIBRATE,%d,%f%c",
            &plateNumber,
            &referenceMass,
            &trailingCharacter) == 2;
    if (validCalibrationCommand) {
      if (tareRequested || calibrationRequested) {
        char response[PAYLOAD_SIZE];
        snprintf(
            response,
            sizeof(response),
            "CALIBRATION_FAILED,%d",
            plateNumber);
        characteristic->setValue(response);
        characteristic->notify();
        return;
      }

      requestedCalibrationPlate = plateNumber;
      requestedCalibrationMass = referenceMass;
      calibrationRequested = true;
      Serial.print("BLE calibration request queued for Plate ");
      Serial.println(plateNumber);
      return;
    }

    characteristic->setValue("UNKNOWN_COMMAND");
    Serial.print("Ignored BLE command: ");
    Serial.println(command.c_str());
  }
};

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

void showDisplayMessage(const char *message) {
  if (!displayAvailable) {
    return;
  }

  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);
  display.setTextSize(1);
  display.setTextWrap(false);
  display.setCursor(0, 2);
  display.println("Smart Exit Station");
  display.drawLine(0, 15, SCREEN_WIDTH - 1, 15, SSD1306_WHITE);
  display.setCursor(0, 25);
  display.println(message);
  display.display();
}

void startDisplay() {
  Wire.begin(OLED_SDA_PIN, OLED_SCL_PIN);
  displayAvailable = display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDRESS);

  if (displayAvailable) {
    Serial.println("OLED ready.");
    showDisplayMessage("Starting...");
  } else {
    Serial.println("OLED not detected. Continuing without display.");
  }
}

void updateDisplay(const PlateReading readings[PLATE_COUNT]) {
  if (!displayAvailable) {
    return;
  }

  display.clearDisplay();
  display.setTextColor(SSD1306_WHITE);
  display.setTextSize(1);
  display.setTextWrap(false);
  display.setCursor(0, 2);
  display.print("BLE: ");
  display.println(deviceConnected ? "CONNECTED" : "OFFLINE");
  display.drawLine(0, 15, SCREEN_WIDTH - 1, 15, SSD1306_WHITE);

  constexpr int ROW_Y[PLATE_COUNT] = {18, 29, 40, 51};
  for (int index = 0; index < PLATE_COUNT; index++) {
    display.setCursor(0, ROW_Y[index]);
    display.print("P");
    display.print(index + 1);
    display.print(": ");

    switch (readings[index].status) {
      case WeightStatus::Ok:
        display.print("ITEM");
        break;
      case WeightStatus::NoLoad:
        display.print("EMPTY");
        break;
      case WeightStatus::Unstable:
        display.print("MOVING");
        break;
      case WeightStatus::Error:
        display.print("SENSOR ERROR");
        break;
    }
  }

  display.display();
}

void startBluetoothService() {
  BLEDevice::init(DEVICE_NAME);

  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new StationServerCallbacks());

  BLEService *service = bleServer->createService(SERVICE_UUID);
  weightCharacteristic = service->createCharacteristic(
      WEIGHT_CHARACTERISTIC_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  weightCharacteristic->addDescriptor(new BLE2902());
  weightCharacteristic->setValue("1,0.0,ERROR");

  commandCharacteristic = service->createCharacteristic(
      COMMAND_CHARACTERISTIC_UUID,
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE |
          BLECharacteristic::PROPERTY_NOTIFY);
  commandCharacteristic->addDescriptor(new BLE2902());
  commandCharacteristic->setCallbacks(new StationCommandCallbacks());
  commandCharacteristic->setValue("READY");

  service->start();

  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->start();

  Serial.println("BLE service is advertising as SmartExit-Station.");
}

bool tareScale(int index, int sampleCount = 20) {
  int64_t total = 0;

  for (int sample = 0; sample < sampleCount; sample++) {
    if (!scales[index].wait_ready_timeout(1000)) {
      return false;
    }
    total += scales[index].read();
  }

  scales[index].set_offset(static_cast<long>(total / sampleCount));
  return true;
}

TareResult tareAllScales() {
  Serial.println("Taring all available plates...");
  int taredScaleCount = 0;

  for (int index = 0; index < PLATE_COUNT; index++) {
    bool tareSucceeded = tareScale(index);
    scaleAvailable[index] = tareSucceeded;
    if (tareSucceeded) {
      taredScaleCount++;
    }

    Serial.print("Plate ");
    Serial.print(index + 1);
    Serial.println(tareSucceeded ? " tare complete." : " tare failed.");
  }

  if (taredScaleCount == PLATE_COUNT) {
    return TareResult::AllScales;
  }
  if (taredScaleCount > 0) {
    return TareResult::AvailableScales;
  }
  return TareResult::Failed;
}

void startScales() {
  for (int index = 0; index < PLATE_COUNT; index++) {
    scales[index].begin(DOUT_PINS[index], SCK_PINS[index]);

    char calibrationKey[12];
    snprintf(calibrationKey, sizeof(calibrationKey), "factor_%d", index + 1);
    float calibrationFactor = preferencesAvailable
                                  ? preferences.getFloat(
                                        calibrationKey,
                                        CALIBRATION_FACTORS[index])
                                  : CALIBRATION_FACTORS[index];
    scales[index].set_scale(calibrationFactor);
    scaleAvailable[index] = scales[index].wait_ready_timeout(3000);

    Serial.print("Plate ");
    Serial.print(index + 1);
    Serial.println(scaleAvailable[index] ? " ready." : " HX711 not ready.");
  }

  Serial.println("Remove all items from the plates. Taring in 3 seconds...");
  showDisplayMessage("Remove all items");
  delay(3000);
  showDisplayMessage("Taring plates...");
  tareAllScales();
}

void sortSamples(float samples[SAMPLE_COUNT]) {
  for (int index = 1; index < SAMPLE_COUNT; index++) {
    float value = samples[index];
    int position = index - 1;

    while (position >= 0 && samples[position] > value) {
      samples[position + 1] = samples[position];
      position--;
    }
    samples[position + 1] = value;
  }
}

PlateReading summarizeSamples(float samples[SAMPLE_COUNT]) {
  // Keep the middle five samples so one or two electrical spikes cannot
  // invalidate an otherwise steady plate reading.
  sortSamples(samples);

  constexpr int FIRST_KEPT_SAMPLE =
      (SAMPLE_COUNT - TRIMMED_SAMPLE_COUNT) / 2;
  constexpr int LAST_KEPT_SAMPLE =
      FIRST_KEPT_SAMPLE + TRIMMED_SAMPLE_COUNT - 1;

  float total = 0.0f;
  for (int index = FIRST_KEPT_SAMPLE; index <= LAST_KEPT_SAMPLE; index++) {
    total += samples[index];
  }

  float average = total / TRIMMED_SAMPLE_COUNT;
  float centralSpread =
      samples[LAST_KEPT_SAMPLE] - samples[FIRST_KEPT_SAMPLE];
  float allowedSpread = max(
      STABILITY_THRESHOLD_GRAMS,
      fabsf(average) * STABILITY_THRESHOLD_PERCENT);

  if (average < -CLEAR_THRESHOLD_GRAMS || average > MAX_WEIGHT_GRAMS) {
    return {0.0f, WeightStatus::Error};
  }
  if (centralSpread > allowedSpread) {
    return {max(0.0f, average), WeightStatus::Unstable};
  }
  if (fabsf(average) <= CLEAR_THRESHOLD_GRAMS) {
    return {0.0f, WeightStatus::NoLoad};
  }
  return {average, WeightStatus::Ok};
}

void collectPlateReadings(PlateReading readings[PLATE_COUNT]) {
  float samples[PLATE_COUNT][SAMPLE_COUNT] = {};
  bool cycleValid[PLATE_COUNT];

  for (int index = 0; index < PLATE_COUNT; index++) {
    // A plate can miss the startup check while its HX711 is still settling.
    // Retry unavailable plates so a temporary startup delay is not permanent.
    if (!scaleAvailable[index]) {
      scaleAvailable[index] = scales[index].wait_ready_timeout(50);
    }
    cycleValid[index] = scaleAvailable[index];
  }

  // The four HX711 modules collect data at the same time. Reading one sample
  // from each plate per round avoids waiting for five full windows in series.
  for (int sample = 0; sample < SAMPLE_COUNT; sample++) {
    for (int index = 0; index < PLATE_COUNT; index++) {
      if (!cycleValid[index]) {
        continue;
      }
      if (!scales[index].wait_ready_timeout(500)) {
        cycleValid[index] = false;
        continue;
      }

      float weight = scales[index].get_units(1);
      if (!isfinite(weight)) {
        cycleValid[index] = false;
        continue;
      }

      samples[index][sample] = weight;
    }
  }

  for (int index = 0; index < PLATE_COUNT; index++) {
    if (!cycleValid[index]) {
      readings[index] = {0.0f, WeightStatus::Error};
      continue;
    }

    readings[index] = summarizeSamples(samples[index]);
  }
}

void publishPlateReading(int index, const PlateReading &reading) {
  char payload[PAYLOAD_SIZE];
  snprintf(
      payload,
      sizeof(payload),
      "%d,%.1f,%s",
      index + 1,
      reading.grams,
      statusText(reading.status));

  weightCharacteristic->setValue(payload);
  if (deviceConnected) {
    weightCharacteristic->notify();
  }

  Serial.print("BLE payload: ");
  Serial.println(payload);
}

void publishAllPlates() {
  PlateReading readings[PLATE_COUNT];
  collectPlateReadings(readings);
  updateDisplay(readings);

  for (int index = 0; index < PLATE_COUNT; index++) {
    publishPlateReading(index, readings[index]);
    delay(50);
  }
}

bool calibratePlate(int plateNumber, float referenceMass) {
  int index = plateNumber - 1;
  if (index < 0 || index >= PLATE_COUNT) {
    Serial.println("Calibration failed: plate must be between 1 and 4.");
    return false;
  }
  if (!isfinite(referenceMass) ||
      referenceMass < MIN_CALIBRATION_MASS_GRAMS ||
      referenceMass > MAX_WEIGHT_GRAMS) {
    Serial.println("Calibration failed: use a reference mass from 20 to 1000 g.");
    return false;
  }
  if (!scales[index].wait_ready_timeout(1000)) {
    Serial.println("Calibration failed: this plate is not responding.");
    return false;
  }
  scaleAvailable[index] = true;

  long referenceReading = scales[index].get_value(25);
  float calibrationFactor = referenceReading / referenceMass;
  if (fabsf(calibrationFactor) < MIN_CALIBRATION_FACTOR) {
    Serial.println(
        "Calibration failed: no meaningful weight change was detected.");
    return false;
  }

  scales[index].set_scale(calibrationFactor);

  char calibrationKey[12];
  snprintf(calibrationKey, sizeof(calibrationKey), "factor_%d", plateNumber);
  bool saved = preferencesAvailable &&
               preferences.putFloat(calibrationKey, calibrationFactor) ==
                   sizeof(float);

  Serial.print("Plate ");
  Serial.print(plateNumber);
  Serial.print(saved ? " calibration saved. Factor: "
                     : " calibration applied until restart. Factor: ");
  Serial.println(calibrationFactor, 4);
  return true;
}

void processSerialCommand(const char *command) {
  if (strcasecmp(command, "t") == 0) {
    tareAllScales();
    return;
  }

  int plateNumber = 0;
  float referenceMass = 0.0f;
  if (sscanf(command, "c%d %f", &plateNumber, &referenceMass) == 2 ||
      sscanf(command, "C%d %f", &plateNumber, &referenceMass) == 2) {
    calibratePlate(plateNumber, referenceMass);
    return;
  }

  Serial.println("Unknown command. Use 't' or 'c<plate> <grams>'.");
}

void handleSerialCommand() {
  static char command[SERIAL_COMMAND_SIZE];
  static size_t commandLength = 0;

  while (Serial.available() > 0) {
    char character = Serial.read();
    if (character == '\r' || character == '\n') {
      if (commandLength > 0) {
        command[commandLength] = '\0';
        processSerialCommand(command);
        commandLength = 0;
      }
      continue;
    }

    if (commandLength < sizeof(command) - 1) {
      command[commandLength++] = character;
    }
  }
}

void handleTareRequest() {
  if (!tareRequested) {
    return;
  }

  tareRequested = false;
  commandCharacteristic->setValue("TARE_RUNNING");
  showDisplayMessage("Taring plates...");

  TareResult result = tareAllScales();
  const char *response = "TARE_FAILED";
  if (result == TareResult::AllScales) {
    response = "TARE_OK";
  } else if (result == TareResult::AvailableScales) {
    response = "TARE_PARTIAL";
  }

  commandCharacteristic->setValue(response);
  if (deviceConnected) {
    commandCharacteristic->notify();
  }
  Serial.print("BLE tare result: ");
  Serial.println(response);
}

void handleCalibrationRequest() {
  if (!calibrationRequested) {
    return;
  }

  int plateNumber = requestedCalibrationPlate;
  float referenceMass = requestedCalibrationMass;
  calibrationRequested = false;

  showDisplayMessage("Calibrating plate...");
  bool calibrationSucceeded = calibratePlate(plateNumber, referenceMass);

  char response[PAYLOAD_SIZE];
  snprintf(
      response,
      sizeof(response),
      calibrationSucceeded ? "CALIBRATION_OK,%d" : "CALIBRATION_FAILED,%d",
      plateNumber);
  commandCharacteristic->setValue(response);
  if (deviceConnected) {
    commandCharacteristic->notify();
  }
}

void restartAdvertisingAfterDisconnect() {
  if (!deviceConnected && previousConnectionState) {
    delay(500);
    bleServer->startAdvertising();
    previousConnectionState = false;
    Serial.println("BLE advertising restarted.");
  } else if (deviceConnected && !previousConnectionState) {
    previousConnectionState = true;
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println();
  Serial.println("Smart Exit four-plate BLE service");
  Serial.println("Send 't' with every plate empty to tare all scales.");
  Serial.println(
      "To calibrate, place a known mass on one plate and send, for example, "
      "'c1 453.6'.");

  preferencesAvailable = preferences.begin(PREFERENCES_NAMESPACE, false);
  if (!preferencesAvailable) {
    Serial.println("Calibration storage unavailable; using firmware factors.");
  }

  startDisplay();
  startScales();
  startBluetoothService();
}

void loop() {
  handleSerialCommand();
  handleTareRequest();
  handleCalibrationRequest();
  restartAdvertisingAfterDisconnect();
  publishAllPlates();
}
