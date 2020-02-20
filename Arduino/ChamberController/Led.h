int ledState = LOW;
void flipLed() {
  ledState = ledState == HIGH ? LOW : HIGH;
  digitalWrite(LED_BUILTIN, ledState);
}

//// NOTE: Blocking. Only for use at startup.
//void buzzLed() {
//  for (int i = 0; i < 25; i++) {
//    digitalWrite(LED_BUILTIN, HIGH);
//    delay(20);
//    digitalWrite(LED_BUILTIN, LOW);
//    delay(20);
//  }
//}

//int onMs = 50;
//int offMs = 950;
//unsigned long prevMillisLedFlip = 0;
//void maybeFlipLed() {
//  if (ledState == LOW) {
//    if (uptimeMillis - prevMillisLedFlip >= offMs) {
//      flipLed();
//      prevMillisLedFLip = uptimeMillis;
//    }
//  } else {
//    if (uptimeMillis - prevMillisLedFlip >= onMs) {
//      flipLed();
//      prevMillisLedFLip = uptimeMillis;
//    }
//  }
//}

uint32_t millisSinceLastFlipLed = 0;
uint32_t prevMillisFlipLed = 0;
void setFlipLedPeriod(uint16_t ledFlashPeriodMillis) {
  if (TIME_UP(prevMillisFlipLed, uptimeMillis, ledFlashPeriodMillis)) {
    millisSinceLastFlipLed = 0;
    flipLed();

    prevMillisFlipLed = uptimeMillis;
  }
}


// End