#include <Arduino.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <HX711.h>
#include <Wire.h>

constexpr int PLATE_COUNT = 4;
constexpr int SAMPLE_COUNT = 5;

const int DOUT_PINS[PLATE_COUNT] = {34, 18, 35, 19};
const int SCK_PINS[PLATE_COUNT] = {32, 17, 33, 23};
const float CALIBRATION_FACTORS[PLATE_COUNT] = {
    2170.77f,
    923.52f,
    2563.81f,
    1840.87f
};

constexpr float CLEAR_THRESHOLD_GRAMS = 5.0f;
constexpr float STABILITY_THRESHOLD_GRAMS = 5.0f;
constexpr float MAX_WEIGHT_GRAMS = 1000.0f;
constexpr unsigned long PUBLISH_INTERVAL_MS = 500;
constexpr size_t PAYLOAD_SIZE = 24;

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
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET_PIN);
bool displayAvailable = false;

BLEServer *bleServer = nullptr;
BLECharacteristic *weightCharacteristic = nullptr;
BLECharacteristic *commandCharacteristic = nullptr;
volatile bool deviceConnected = false;
volatile bool tareRequested = false;
bool previousConnectionState = false;

enum class WeightStatus {
  Ok,
  NoLoad,
  Unstable,
  Error
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
    std::string command = characteristic->getValue();
    if (command != "TARE") {
      characteristic->setValue("UNKNOWN_COMMAND");
      Serial.print("Ignored BLE command: ");
      Serial.println(command.c_str());
      return;
    }

    tareRequested = true;
    characteristic->setValue("TARE_QUEUED");
    Serial.println("BLE tare request queued.");
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
        display.print(readings[index].grams, 0);
        display.print(" g");
        break;
      case WeightStatus::NoLoad:
        display.print("EMPTY");
        break;
      case WeightStatus::Unstable:
        display.print("UNSTABLE");
        break;
      case WeightStatus::Error:
        display.print("ERROR");
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
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE);
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

bool tareAllScales() {
  Serial.println("Taring all available plates...");
  bool allSucceeded = true;

  for (int index = 0; index < PLATE_COUNT; index++) {
    bool tareSucceeded = tareScale(index);
    scaleAvailable[index] = tareSucceeded;
    allSucceeded = allSucceeded && tareSucceeded;

    Serial.print("Plate ");
    Serial.print(index + 1);
    Serial.println(tareSucceeded ? " tare complete." : " tare failed.");
  }

  return allSucceeded;
}

void startScales() {
  for (int index = 0; index < PLATE_COUNT; index++) {
    scales[index].begin(DOUT_PINS[index], SCK_PINS[index]);
    scales[index].set_scale(CALIBRATION_FACTORS[index]);
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

void collectPlateReadings(PlateReading readings[PLATE_COUNT]) {
  float totals[PLATE_COUNT] = {};
  float minimums[PLATE_COUNT];
  float maximums[PLATE_COUNT];
  bool cycleValid[PLATE_COUNT];

  for (int index = 0; index < PLATE_COUNT; index++) {
    minimums[index] = MAX_WEIGHT_GRAMS;
    maximums[index] = -MAX_WEIGHT_GRAMS;
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

      totals[index] += weight;
      minimums[index] = min(minimums[index], weight);
      maximums[index] = max(maximums[index], weight);
    }
  }

  for (int index = 0; index < PLATE_COUNT; index++) {
    if (!cycleValid[index]) {
      readings[index] = {0.0f, WeightStatus::Error};
      continue;
    }

    float average = totals[index] / SAMPLE_COUNT;
    if (average < -CLEAR_THRESHOLD_GRAMS || average > MAX_WEIGHT_GRAMS) {
      readings[index] = {0.0f, WeightStatus::Error};
    } else if (maximums[index] - minimums[index] >
               STABILITY_THRESHOLD_GRAMS) {
      readings[index] = {max(0.0f, average), WeightStatus::Unstable};
    } else if (fabsf(average) <= CLEAR_THRESHOLD_GRAMS) {
      readings[index] = {0.0f, WeightStatus::NoLoad};
    } else {
      readings[index] = {average, WeightStatus::Ok};
    }
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

void handleSerialCommand() {
  if (Serial.available() == 0) {
    return;
  }

  char command = Serial.read();
  if (command == 't' || command == 'T') {
    tareAllScales();
  }
}

void handleTareRequest() {
  if (!tareRequested) {
    return;
  }

  tareRequested = false;
  commandCharacteristic->setValue("TARE_RUNNING");
  showDisplayMessage("Taring plates...");

  bool tareSucceeded = tareAllScales();
  commandCharacteristic->setValue(tareSucceeded ? "TARE_OK" : "TARE_FAILED");
  Serial.println(tareSucceeded ? "BLE tare request complete."
                               : "BLE tare request failed.");
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

  startDisplay();
  startScales();
  startBluetoothService();
}

void loop() {
  handleSerialCommand();
  handleTareRequest();
  restartAdvertisingAfterDisconnect();

  static unsigned long lastPublish = 0;
  if (millis() - lastPublish < PUBLISH_INTERVAL_MS) {
    return;
  }
  lastPublish = millis();

  publishAllPlates();
}
