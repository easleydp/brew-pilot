#define MINUTE_MILLIS 60000

// This depends on certain globals defined in Common.h:
//uint32_t uptimeMillis = 0;
//uint32_t uptimeMins = 0;

//uint32_t millisSinceLastUpdatedMins = 0;
//uint32_t prevUptimeMillis = 0;

// Called once per ellapsed minute, though not necessarily on a precise scedule.
void _minuteTick() {
  uptimeMins++;
  // Notify listeners
  chambersMinuteTick();
}

uint32_t prevMillisMinuteTick = 0;
void keepTrackOfTime() {
  while (TIME_UP(prevMillisMinuteTick, uptimeMillis, MINUTE_MILLIS)) {
    _minuteTick();
    prevMillisMinuteTick += MINUTE_MILLIS;
  }
}

//void keepTrackOfTime() {
//  
//  prevUptimeMillis = uptimeMillis;
//  uptimeMillis = millis();
//
//  // millis() returns a uint32_t, which wraps every 49.7 days. So we maintain a minutes count too, which - as a uint32_t - allows us to run for 8171 years.
//  if (prevUptimeMillis != uptimeMillis) {
//    if (uptimeMillis < prevUptimeMillis) {
//      // uptimeMillis has wrapped
//      millisSinceLastUpdatedMins += uptimeMillis + (ULONG_MAX - prevUptimeMillis);
//    } else {
//      millisSinceLastUpdatedMins += uptimeMillis - prevUptimeMillis;
//    }
//    while (millisSinceLastUpdatedMins > MINUTE_MILLIS) {
//      _minuteTick();
//      millisSinceLastUpdatedMins -= MINUTE_MILLIS;
//    }
//  }
//}


// End