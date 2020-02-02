#include <limits.h>
#include <stdarg.h>
#include <stdio.h>
#include <Base64.h> // https://github.com/agdl/Base64
#include <EEPROM.h>
#include <OneWire.h>
#include <DallasTemperature.h>

#include "Common.h"
#include "Logging.h"
#include "Temperature.h"
#include "ChamberData.h"
#include "ChamberControl.h"
#include "Led.h"
#include "MessageHandlingGen.h"
#include "MessageHandlingDomain.h"
#include "TimeKeeping.h"

static const char* mainLogPrefix = "MN";

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  //buzzLed();

  Serial.begin(57600);
  while (!Serial) {
    ; // wait for serial port to connect. Needed for ATmega32u4-based boards and Arduino 101
  }

  // Start the temperature sensor library
  sensors.begin();
//  tempDeviceCount = sensors.getDeviceCount();
//  Serial.print(F("TD count:"));
//  Serial.println(tempDeviceCount);

  initLoggingData();
  initChamberData();

  logMsg(LOG_WARN, mainLogPrefix, '0');
}

void loop() {
  keepTrackOfTime();
  handleMessages();
//  controlChambers();
}

// End
