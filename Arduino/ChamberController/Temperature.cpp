/**
 * @file Temperature.cpp
 * @brief Dallas 1-Wire sensor management.
 */
#include "Temperature.h"

// Setup a oneWire instance to communicate with any OneWire devices
OneWire oneWire(PIN__ONE_WIRE_BUS);
// Pass our oneWire reference to Dallas Temperature sensor
DallasTemperature dallas(&oneWire);

Sensor sensorData[SENSOR_COUNT];

uint8_t badSensorCount = 0;

void initSensorData(uint8_t ourIndex, uint16_t shortAddress, int8_t error) {
  sensorData[ourIndex].shortAddress = shortAddress;
  sensorData[ourIndex].error = error;
  sensorData[ourIndex].prevReading = SHRT_MIN;
}

uint16_t shortenAddress(const DeviceAddress& fullAddress) {
  return ((uint16_t)fullAddress[1]) << 8 | ((uint16_t)fullAddress[2]);
}

Sensor* findSensorByAddress(const DeviceAddress& fullAddress) {
  uint16_t shortAddress = shortenAddress(fullAddress);
  for (uint8_t i = 0; i < SENSOR_COUNT; i++)
    if (sensorData[i].shortAddress == shortAddress)
      return &sensorData[i];
  return NULL;
}

boolean readTemperatures() {
  dallas.requestTemperatures();
  uint8_t sensorCount = dallas.getDS18Count();
  if (sensorCount != SENSOR_COUNT) {
    badSensorCount = SENSOR_COUNT - sensorCount;
    logMsg(LOG_ERROR, "T", 'C', 1, badSensorCount /* uint8_t */);
    return false;
  }
  return true;
}

void initTemperatureSensors() {
  delay(1000);
  dallas.begin();
  delay(1000);
  readTemperatures();
  // Edit this in sympathy with SENSOR_COUNT, having established each device's address and error using calibrateTemperatureSensors()
  // Note: the error offset is x100. This value divided by 100 will be ADDED to the reading from the device.
  initSensorData(CH1_T_BEER, 0x3A11, 25);
  initSensorData(CH1_T_CHAMBER, 0x3606, 11);
  initSensorData(CH2_T_BEER, 0x3EE1, -4);
  initSensorData(CH2_T_CHAMBER, 0x79BA, -8);
  initSensorData(T_EXTERNAL, 0xBD96, -15);
  initSensorData(T_PROJECT_BOX, 0x3B79, -12);

  DeviceAddress address;
  for (uint8_t i = 0; i < SENSOR_COUNT; i++) {
    dallas.getAddress(address, i);
    Sensor* ptr = findSensorByAddress(address);
    if (ptr == NULL) {
      Serial.print(F("ERROR! not found: "));
      Serial.println(shortenAddress(address), HEX);
      return;
    }
    ptr->dallasIndex = i;
  }
  badSensorCount = 0;
}

int16_t getTemperature(uint8_t sensorIndex) {
  Sensor& sensor = sensorData[sensorIndex];
  float readingRaw = dallas.getTempCByIndex(sensor.dallasIndex) + ((float)sensor.error) / 100.0f;
  int16_t prevReading = sensor.prevReading;
  // A disconnected sensor seems to give a reading of -127.04.
  // Regard anything less that -50 as an error and return the previous reading.
  if (readingRaw < -50.0f) {
    // Some sensors seem prone to give this false reading occasionally even when not actually
    // disconnected, so we log as warning rather than error.
    logMsg(LOG_WARN, "T", 'D', 1, sensorIndex /* uint8_t */, readingRaw /* float */);
    return prevReading;
  }
  int16_t reading = (readingRaw + 0.05f) * 10;
  if (prevReading == SHRT_MIN) {
    prevReading = reading;
  }
  sensor.prevReading = reading;
  return (prevReading + reading) / 2;
}

void readTBeer(ChamberData& cd) {
  int16_t t = getTemperature(cd.chamberId == 1 ? CH1_T_BEER : CH2_T_BEER);
  cd.tBeer = t != SHRT_MIN ? t : cd.mParams.tTarget;
}
void readTChamber(ChamberData& cd) {
  int16_t t = getTemperature(cd.chamberId == 1 ? CH1_T_CHAMBER : CH2_T_CHAMBER);
  cd.tChamber = t != SHRT_MIN ? t : cd.mParams.tTarget;
}
void readTExternal() {
  int16_t t = getTemperature(T_EXTERNAL);
  tExternal = t != SHRT_MIN ? t : 0;
}
void readTProjectBox() {
  int16_t t = getTemperature(T_PROJECT_BOX);
  tProjectBox = t != SHRT_MIN ? t : 0;
}
