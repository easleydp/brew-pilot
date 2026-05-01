/**
 * @file Led.h
 * @brief LED control logic.
 */
#ifndef LED_H
#define LED_H

#include "Common.h"

extern uint8_t ledState;
void flipLed();

//// Buzz LED for the specified duration. NOTE: Blocking (typically only used at startup)
#define LED_BUZZ_PERIOD 40  // ON + OFF time in milliseconds
void buzzLed(uint16_t ms);

extern uint16_t flipLedPeriod;
extern uint32_t millisSinceLastFlipLed;
extern uint32_t prevMillisFlipLed;
void maybeFlipLed();

#endif  // LED_H
