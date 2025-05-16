#ifndef TIME_KEEPING_H
#define TIME_KEEPING_H

#include "Common.h"
#include "TimeKeeping.h"

#define MINUTE_MILLIS 60000
#define SECOND_MILLIS 1000

// This depends on certain globals defined in Common.h:
// uint32_t uptimeMillis = 0;
// uint32_t uptimeMins = 0;

// uint32_t millisSinceLastUpdatedMins = 0;
// uint32_t prevUptimeMillis = 0;

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

uint32_t prevMillisMinuteTick = 0;
uint32_t prevMillisSecondTick = 0;
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

#endif  // TIME_KEEPING_H