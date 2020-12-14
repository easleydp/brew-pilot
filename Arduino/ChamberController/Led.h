uint8_t ledState = LOW;
void flipLed() {
  ledState = ledState == HIGH ? LOW : HIGH;
  digitalWrite(LED_BUILTIN, ledState);
}

//// Buzz LED for the specified duration. NOTE: Blocking (typically only used at startup)
#define LED_BUZZ_PERIOD  40  // ON + OFF time in milliseconds
void buzzLed(uint16_t ms) {
  const uint16_t cycles = ms / LED_BUZZ_PERIOD;
  for (uint16_t i = 0; i < cycles; i++) {
    digitalWrite(LED_BUILTIN, HIGH);
    delay(LED_BUZZ_PERIOD / 2);
    digitalWrite(LED_BUILTIN, LOW);
    delay(LED_BUZZ_PERIOD / 2);
  }
}


uint16_t flipLedPeriod = 100;
uint32_t millisSinceLastFlipLed = 0;
uint32_t prevMillisFlipLed = 0;
void maybeFlipLed() {
  if (TIME_UP(prevMillisFlipLed, uptimeMillis, flipLedPeriod)) {
    millisSinceLastFlipLed = 0;
    flipLed();

    prevMillisFlipLed = uptimeMillis;
  }
}


// End
