#ifndef CHAMBER_CONTROLLER_H
#define CHAMBER_CONTROLLER_H

#include "ChamberControl.h"
#include "Common.h"
#include "Led.h"
#include "Logging.h"
#include "MessageHandlingDomain.h"
#include "MessageHandlingGen.h"
#include "Pins.h"
#include "Temperature.h"
#include "TimeKeeping.h"

void initLowLevelTriggerRelayPin(uint8_t pin) {
  pinMode(pin, OUTPUT);  // Configure the PIN and
  digitalWrite(pin, HIGH);  // immediately switch it OFF!
}

void ChamberControllerSetup() {
  pinMode(LED_BUILTIN, OUTPUT);
  buzzLed(500);  // Useful visual indication that we've (re-)started

  // Since all the relays are 'low level trigger', switch them all OFF. IMPORTANT to do this
  // immediately after the PIN is configured to avoid 'blipping' fridge compressors.
  initLowLevelTriggerRelayPin(PIN__CH1_FRIDGE);
  initLowLevelTriggerRelayPin(PIN__CH2_FRIDGE);
  initLowLevelTriggerRelayPin(PIN__CH1_HEATER);

  Serial.begin(57600);
  while (!Serial) {
    ;  // Wait for serial port to connect. (Only really needed for ATmega32u4-based boards and Arduino 101)
  }

  // Experience has shown that, when bugs arise on this side, master may never get to receive log messages.
  // There's a greater likelihood it will receive this. (This is the only unsolicited message we ever send.)
  sendToMaster("MCU-0");  // Signifies MCU has (re-)started

  initTemperatureSensors();
  initLoggingData();
  initChamberData();

  // Logging of π etc. is just to test the various data types, to prove the backend can correctly
  // deserialise each.
  logMsg(LOG_INFO, mainLogPrefix, '0', 1, 3.14159274101F, (uint32_t)0xFEDC, (int16_t)-12345);
}

void ChamberControllerLoop() {
  keepTrackOfTime();
  handleMessages();
  if (badSensorCount) {
    initTemperatureSensors();
  }
  if (/* still bad */ badSensorCount) {
    flipLedPeriod = 200;
  } else {
    flipLedPeriod = 1000;
    // testTemperatureSensors();
  }
  maybeFlipLed();
  // Note: Even in the face of bad sensors we (conservatively) control chambers.
  controlChambers();
}

#endif  // CHAMBER_CONTROLLER_H