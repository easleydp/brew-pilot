/*
 * I/O pins
 */
// Input
#define PIN__ONE_WIRE_BUS 2  // Temperature sensor bus [WHITE]
// Output
#define PIN__CH1_FRIDGE   7  // Mechanical relay, ch1  [YELLOW]
#define PIN__CH2_FRIDGE   8  // Mechanical relay, ch2  [ORANGE]
#define PIN__CH1_HEATER   12 // SSR, ch1               [ORANGE/WHITE]


#include <limits.h>
#include <stdarg.h>
#include <stdio.h>
#include <Base64.h> // https://github.com/agdl/Base64
#include <EEPROM.h>
#include <OneWire.h>
#include <DallasTemperature.h>

#include "Common.h"
#include "Logging.h"
#include "ChamberData.h"
#include "Temperature.h"
#include "ChamberControl.h"
#include "Led.h"
#include "MessageHandlingGen.h"
#include "MessageHandlingDomain.h"
#include "TimeKeeping.h"


static const char* mainLogPrefix = "MN";

void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(PIN__CH1_FRIDGE, OUTPUT);
  pinMode(PIN__CH2_FRIDGE, OUTPUT);
  pinMode(PIN__CH1_HEATER, OUTPUT);
  //buzzLed();

  // Since all the relays are 'low level trigger', switch them all OFF
  digitalWrite(PIN__CH1_FRIDGE, HIGH);
  digitalWrite(PIN__CH2_FRIDGE, HIGH);
  digitalWrite(PIN__CH1_HEATER, HIGH);

  Serial.begin(57600);
  while (!Serial) {
    ; // wait for serial port to connect. Needed for ATmega32u4-based boards and Arduino 101
  }

  initTemperatureSensors();
  initLoggingData();
  initChamberData();

  logMsg(LOG_INFO, mainLogPrefix, '0', 1, 3.14159274101F, (uint32_t) 0xFEDC, (int16_t) -12345);
}

void loop() {
  keepTrackOfTime();
  handleMessages();
  if (badSensorCount) {
    initTemperatureSensors();
  }
  if (/* still bad */badSensorCount) {
    setFlipLedPeriod(200);
  } else {
    setFlipLedPeriod(1000);
    //testTemperatureSensors();
  }
  // Note: Even in the face of bad sensors we (conservatively) control chambers.
  controlChambers();
}

// End
