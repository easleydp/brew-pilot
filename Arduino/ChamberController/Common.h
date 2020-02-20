#define TIME_UP(prev, curr, interval)  ((uint32_t)(curr - prev) >= interval)

uint32_t uptimeMillis = 0;
uint32_t uptimeMins = 0;

char _progMemBuff[80];
const char* strFromProgMem(const char* addr) {
  byte k;
  for (k = 0; k < strlen_P(addr); k++) {
    if (k == sizeof(_progMemBuff) - 1)
      break;
    char myChar = pgm_read_byte_near(addr + k);
    _progMemBuff[k] = myChar;
  }
  _progMemBuff[k] = 0;
  return _progMemBuff;
}


char _itoaBuff[10];
const char* itoa(int value) {
  return itoa(value, _itoaBuff, 10);
}


/**
 * In the buffer referenced by `cmd`, finds the first comma starting from offset `startingOffset`,
 * sets the comma to null, and returns the offset of the following character.
 * Returns -1 if the end of the buffer is reached.
 */
int nullNextComma(char* cmd, int startingOffset) {
  while (true) {
    char ch = cmd[startingOffset++];
    if (ch == ',') {
      cmd[startingOffset - 1] = '\0';
      return startingOffset;
    }
    if (ch == '\0') {
      return -1;
    }
  };
}

// https://playground.arduino.cc/Code/AvailableMemory/
int freeRam() {
  extern int __heap_start, *__brkval;
  int v;
  return (int) &v - (__brkval == 0 ? (int) &__heap_start : (int) __brkval);
}
int minFreeRam = 32767;
uint8_t minFreeRamLocation = 0;
// Call this to record lowest minFreeRam from whereever you suspect stack may be deep.
// The minFreeRam is then available for reporting.
void memoMinFreeRam(uint8_t location) {
  int fr = freeRam();
  if (minFreeRam > fr) {
    minFreeRam = fr;
    location = 0;
  }
}

void printComma() {
  Serial.print(',');
}

// End
