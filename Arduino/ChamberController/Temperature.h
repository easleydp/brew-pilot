
// Data wire is conntected to the Arduino digital pin 4
#define ONE_WIRE_BUS 4
// Setup a oneWire instance to communicate with any OneWire devices
OneWire oneWire(ONE_WIRE_BUS);
// Pass our oneWire reference to Dallas Temperature sensor 
DallasTemperature sensors(&oneWire);


byte tempDeviceCount; // Number of temperature devices found
DeviceAddress tempDeviceAddress;

//void printTempDeviceAddress(DeviceAddress deviceAddress) {
//  for (uint8_t i = 0; i < 8; i++) {
//    if (deviceAddress[i] < 16) Serial.print("0");
//      Serial.print(deviceAddress[i], HEX);
//  }
//}

//#define TEMP_READINGS_MILLIS 10000
//unsigned long prevMillisReadTemperatures = 0;
//void maybeReadTemperatures() {
//  if (uptimeMillis - prevMillisReadTemperatures >= TEMP_READINGS_MILLIS) {
//    sensors.requestTemperatures();
//    prevMillisReadTemperatures = uptimeMillis;
//  }
//}

// End
