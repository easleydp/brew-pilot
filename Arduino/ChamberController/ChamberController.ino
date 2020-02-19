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

  initTemperatureSensors();
  initLoggingData();
  initChamberData();

  logMsg(LOG_WARN, mainLogPrefix, '0');
}

//TODO:
// investigate why two readings with zeros
// Check LedFlip still works since enhancing to handle wrapping

void loop() {
  keepTrackOfTime();
  handleMessages();
  if (temperatureSensorsOk) {
    //testTemperatureSensors();
    controlChambers();
    setFlipLedPeriod(1000);
  } else {
    setFlipLedPeriod(200);
  }
}

// End
