// Only used for test. Otherwise we read temperatures whenever requested.
#define TEMP_READINGS_MILLIS 10000

// Set this to your number of sensors, e.g. (tChamber + tBeer) * 2 chambers + tExternal + tPi = 6
#define SENSOR_COUNT 6

// Setup a oneWire instance to communicate with any OneWire devices
OneWire oneWire(PIN__ONE_WIRE_BUS);
// Pass our oneWire reference to Dallas Temperature sensor
DallasTemperature dallas(&oneWire);

//void printSensorAddress(DeviceAddress address)
//{
//  for (uint8_t i = 0; i < 8; i++)
//  {
//    Serial.print(F("0x"));
//    if (address[i] < 0x10) Serial.print(F("0"));
//    Serial.print(address[i], HEX);
//    if (i < 7) Serial.print(F(","));
//  }
//}

typedef struct {
  // Abbreviated address. See shortenAddress()'s comment.
  uint16_t shortAddress;

  // Actual hardware index, assigned by the Dallas library
  uint8_t dallasIndex;

  // Error * 100. After being divided by 100 this value will be ADDED to the reading from the device.
  int8_t error;  // int8_t accommodates error range -1.28..+1.27

  int16_t prevReading;  // Used to apply a degree of averaging (noise smoothing)
} Sensor;
Sensor sensorData[SENSOR_COUNT];

// Our indexes 0..<SENSOR_COUNT-1> (not to be confused with Sensor.dallasIndex).
#define CH1_T_BEER 0
#define CH1_T_CHAMBER 1
#define CH2_T_BEER 2
#define CH2_T_CHAMBER 3
#define T_EXTERNAL 4
#define T_PI 5

static const char* logPrefixTemperature = "T";

void initSensorData(uint8_t ourIndex, uint16_t shortAddress, int8_t error) {
  sensorData[ourIndex].shortAddress = shortAddress;
  sensorData[ourIndex].error = error;
  sensorData[ourIndex].prevReading = INT_MIN;
}

// Each sensor has an 64 bit address. For such a relatively small number of sensors as ours 16 bits is sufficient to discriminate.
// (Note: byte[0] is always 0x28 for DS18B20; [4] appears to be always either 5E or 5F; [5] appears to be always 14; [6] appears to
// be  always 01; [7] is CRC of first 7 bytes.)
// Most significant byte of the return value is byte[1] of the full address, LSB of the return value is byte[2] of the full address.
uint16_t shortenAddress(const DeviceAddress& fullAddress) {
  return ((uint16_t) fullAddress[1]) << 8  |  ((uint16_t) fullAddress[2]);
}

Sensor* findSensorByAddress(const DeviceAddress& fullAddress) {
  uint16_t shortAddress = shortenAddress(fullAddress);
  for (uint8_t i = 0; i < SENSOR_COUNT; i++)
    if (sensorData[i].shortAddress == shortAddress)
      return &sensorData[i];
  return NULL;
}

uint8_t badSensorCount = 0;

// This must be called once each period before reading the individual temperatures (using getTemperatureX10()).
// Retuns true if all ok.
boolean readTemperatures() {
  dallas.requestTemperatures();
  uint8_t sensorCount = dallas.getDS18Count();
  if (sensorCount != SENSOR_COUNT) {
    badSensorCount = SENSOR_COUNT - sensorCount;
    logMsg(LOG_ERROR, logPrefixTemperature, 'C', 1, badSensorCount/* uint8_t */);
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
  initSensorData(CH1_T_BEER,    0x3A11, 19);
  initSensorData(CH1_T_CHAMBER, 0x3606, 7);
  initSensorData(CH2_T_BEER,    0x3EE1, 11);
  initSensorData(CH2_T_CHAMBER, 0x79BA, -6);
  initSensorData(T_EXTERNAL,    0xBD96, -9);
  initSensorData(T_PI,          0x3B79, -13);

  DeviceAddress address;
  for (uint8_t i = 0; i < SENSOR_COUNT; i++) {
    dallas.getAddress(address, i);
    Sensor* ptr = findSensorByAddress(address);
    if (ptr == NULL) {
      Serial.print(F("ERROR! no found: "));
      Serial.println(shortenAddress(address), HEX);
      return;
    }
    ptr->dallasIndex = i;
  }
  badSensorCount = 0;
}

// Retrieves latest reading for the specified sensor, converts to int x10, and applies a degree of averaging w.r.t. previous readings.
int16_t getTemperatureX10(uint8_t sensorIndex) {
  Sensor& sensor = *(&sensorData[sensorIndex]);  // Who knows why `sensorData[sensorIndex]` doesn't work
  float reading = dallas.getTempCByIndex(sensor.dallasIndex) + ((float) sensor.error) / 100.0f;
  // A disconnected sensor seems to give a reading of approximately -127.
  // Regard anything less that -50 as an error and return a special value that denotes 'DO NOT USE!'
  if (reading < -50.0f) {
    logMsg(LOG_ERROR, logPrefixTemperature, 'D', 1, sensorIndex/* uint8_t */, reading/* float */);
    return SHRT_MIN;
  }
  int16_t readingX10 = (reading + 0.05f) * 10;
  int16_t prevReading = sensor.prevReading;
  if (prevReading == INT_MIN) {
    prevReading = readingX10;
  }
  sensor.prevReading = readingX10;
  return (prevReading + readingX10) / 2;
}

void readTBeer(ChamberData& cd) {
  int16_t t = getTemperatureX10(cd.chamberId == 1 ? CH1_T_BEER : CH2_T_BEER);
  cd.tBeer = t != SHRT_MIN ? t : cd.mParams.tTarget;
}
void readTChamber(ChamberData& cd) {
  int16_t t = getTemperatureX10(cd.chamberId == 1 ? CH1_T_CHAMBER : CH2_T_CHAMBER);
  cd.tChamber = t != SHRT_MIN ? t : cd.mParams.tTarget;
}
void readTExternal() {
  int16_t t = getTemperatureX10(T_EXTERNAL);
  tExternal = t != SHRT_MIN ? t : 0;
}
void readTPi() {
  int16_t t = getTemperatureX10(T_PI);
  tPi = t != SHRT_MIN ? t : 0;
}

//unsigned long prevMillisReadTemperatures = 0;
//void testTemperatureSensors() {
//  if (badSensorCount > 0)
//    return;
//  if (uptimeMillis - prevMillisReadTemperatures >= TEMP_READINGS_MILLIS) {
//    prevMillisReadTemperatures = uptimeMillis;
//
//    dallas.requestTemperatures();
//    float total = 0;
//    for (uint8_t i = 0; i < SENSOR_COUNT; i++) {
//
//      Sensor sensor = sensorData[i];
//      Serial.print(F("Sensor "));
//      Serial.print(i);
//      Serial.print(F(": "));
//      Serial.print(sensor.dallasIndex);
//      Serial.print(F(", raw: "));
//      float reading = dallas.getTempCByIndex(sensor.dallasIndex);
//      total += reading;
//      Serial.print(reading);
//      Serial.print(F(", adjusted: "));
//      Serial.print(reading + ((float) sensor.error) / 100.0f);
//      Serial.print(F(" (intX10: "));
//      Serial.print(getTemperatureX10(i));
//      Serial.println(F(")"));
//    }
//    Serial.print(F("------ Avg: "));
//    Serial.print(total / SENSOR_COUNT);
//    Serial.println(" ------");
//  }
//}
//
//#define AVG_READING_COUNT 10
//float averageReadingAcrossAllSensors(float readings[]) {
//  float total = 0;
//  for (uint8_t i = 0; i < SENSOR_COUNT; i++)
//    total += readings[i];
//  return total / SENSOR_COUNT;
//}
//float avgReadings[SENSOR_COUNT][AVG_READING_COUNT];
//float readingErrs[SENSOR_COUNT][AVG_READING_COUNT];
//uint8_t readingModuloCount = 0;
//void calibrateTemperatureSensors() {
//  if (uptimeMillis - prevMillisReadTemperatures >= TEMP_READINGS_MILLIS) {
//    prevMillisReadTemperatures = uptimeMillis;
//
//    if (!sensorCountChecked) {
//      uint8_t sensorCount = dallas.getDS18Count();
//      if (sensorCount != SENSOR_COUNT) {
//        Serial.print(F("ERROR! sensore count: "));
//        Serial.println(sensorCount);
//        return;
//      }
//      sensorCountChecked = true;
//    }
//    // Serial.print(F("Sensor count: "));
//    // Serial.println(sensorCount);
//
//    // Collect a reading for each sensor
//    DeviceAddress address;
//    float currReadings[SENSOR_COUNT];
//    // Serial.print(F("Readings ("));
//    // Serial.print(readingModuloCount + 1);
//    // Serial.print(F("):"));
//    dallas.requestTemperatures();
//    for (uint8_t i = 0; i < SENSOR_COUNT; i++) {
//
//      // Serial.print(F("Sensor "));
//      // Serial.print(i);
//      // Serial.print(F(": "));
//      // Serial.print(dallas.getResolution(address));
//      // Serial.print(F("; "));
//      // dallas.getAddress(address, i);
//      // printSensorAddress(address);
//      // Serial.print(F("; "));
//      // Serial.println(dallas.getTempCByIndex(i));
//
//      float reading = dallas.getTempCByIndex(i);
//      currReadings[i] = reading;
//      // Serial.print(reading); Serial.print(F(" ");)
//    }
//
//    // Now we have a reading for each sensor, compute average across them all (the golden reading).
//    float golden = averageReadingAcrossAllSensors(currReadings);
//    // Serial.print(F("Average: ")); Serial.print(golden);
//    // Serial.println("");
//    // Calculate the error for each sensor and store so we can compute average error later.
//    for (uint8_t i = 0; i < SENSOR_COUNT; i++) {
//      readingErrs[i][readingModuloCount] = golden - currReadings[i];
//    }
//
//    // When the error array is full, compute average error for each sensor and print.
//    if (++readingModuloCount == AVG_READING_COUNT) {
//      readingModuloCount = 0;
//
//      for (uint8_t i = 0; i < SENSOR_COUNT; i++) {
//        float totalErr = 0;
//        for (uint8_t j = 0; j < AVG_READING_COUNT; j++) {
//          totalErr += readingErrs[i][j];
//        }
//        Serial.print(F("Sensor "));
//        Serial.print(i);
//        Serial.print(F(" avg error: "));
//        dallas.getAddress(address, i);
//        printSensorAddress(address);
//        Serial.print(F("; "));
//        Serial.println(totalErr / AVG_READING_COUNT);
//      }
//    }
//  }
//}

// End
