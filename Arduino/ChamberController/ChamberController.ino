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

/*
 * https://forum.arduino.cc/index.php?topic=122413.0
 *
 * if all your time calculations are done as:
if  ((later_time - earlier_time ) >=duration ) {action}
then the rollover does generally not come into play.
 *
 * millis() returns an unsigned long.
 *
 * Whenever you subtract an older time from a newer one, you get the correct unsigned result. No matter if there was an overflow.
 *
 * >>>> if ((unsigned long)(currentMillis - previousMillis) >= interval) {
 * #define TIME_UP(curr, prev, interval)  ((unsigned long)(curr - prev) >= interval)
 */

void loop() {
  keepTrackOfTime();
  handleMessages();
  if (!temperatureSensorsOk) {
    initTemperatureSensors();
  }
  if (temperatureSensorsOk) {
    //testTemperatureSensors();
    controlChambers();
    setFlipLedPeriod(1000);
  } else {
    setFlipLedPeriod(200);
  }
}

// End
