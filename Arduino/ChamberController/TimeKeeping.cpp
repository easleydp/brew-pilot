/**
 * @file TimeKeeping.cpp
 * @brief Millis-based tick system for minutes and seconds.
 */
#include "TimeKeeping.h"
#include "ChamberControl.h"

uint32_t prevMillisMinuteTick = 0;
uint32_t prevMillisSecondTick = 0;

// Called once per elapsed minute, though not necessarily on a precise schedule.
void _minuteTick() {
  uptimeMins++;
  // Notify listeners
  chambersMinuteTick();
}

// Ditto seconds
void _secondTick() {
  // Notify listeners
  chambersSecondTick();
}

void keepTrackOfTime() {
  uptimeMillis = millis();
  while (TIME_UP(prevMillisMinuteTick, uptimeMillis, MINUTE_MILLIS)) {
    _minuteTick();
    prevMillisMinuteTick += MINUTE_MILLIS;
  }
  while (TIME_UP(prevMillisSecondTick, uptimeMillis, SECOND_MILLIS)) {
    _secondTick();
    prevMillisSecondTick += SECOND_MILLIS;
  }
}
