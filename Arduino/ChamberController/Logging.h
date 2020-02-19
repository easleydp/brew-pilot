// Rather than set a logLevel, we try and log everything but if buffer space runs out we canibalise starting with debug messages.
#define LOG_ERROR 3
#define LOG_WARN 2
#define LOG_INFO 1
#define LOG_DEBUG 0

// Tune as low as possible. Each logged message is validated to check it fits.
// Currently can't be greater than 15, given bit structure of LogRecord.packed.
#define LOG_BYTES_MAX 12  // 12 being enough for 3 floats

// Mustn't be > 255 given the present implementation [use of uint8_t]
#define LOG_RECORD_COUNT 46

// Packed bit structure:
// bit 7: chamberId-1 (unused in certain messages)
// but 6: unused
// bits 5,4,3,2: buffLen (0..LOG_BYTES_MAX)
// bits 1,0: logLevel
#define log_getChamberId(byte)  ((((byte) & 0x80) >> 7) + 1)
#define log_setChamberId(byte, cid)  ((byte) |= ((cid - 1) << 7))
#define log_getBuffLen(byte)  (((byte) & 0x3C) >> 2)
#define log_setBuffLen(byte, len)  ((byte) |= (((len) << 2) & 0x3C))
#define log_getLevel(byte)  ((byte) & 0x03)
#define log_setLevel(byte, level)  ((byte) |= ((level) & 0x03))

typedef struct {
  uint8_t sequenceNum;  // Assuming LOG_RECORD_COUNT <= 255
  uint8_t packed;
  const char* prefix;  // e.g. "PID"
  char id;  // e.g. '+'. Also serves to denote record occupied (0 signifies unoccupied).
  byte buff[LOG_BYTES_MAX];  // binary data
} LogRecord;

LogRecord logRecords[LOG_RECORD_COUNT];

// This will keep track of whether we had to resort to ejecting older messages (or whether we failed
// to find a slot for an inferior message). This will be sent to RPi whenever it pulls some messages.
// After the RPi pulls some messages thereby freeing records, we'll set it back to false.
boolean logDataEjected = false;

// Assuming LOG_RECORD_COUNT <= 255
uint8_t nextLogRecordSequenceNum = 0;  // Modulo 255

/**
 * Returns the oldest occupied log record or NULL if none.
 * Optionally filters to the specfified log level if filterByLevel is true.
 *
 * Imagine a scattering of seqNums: 20, 3, 7, 33, 1, 47, 29  ---(1)
 * and nextSeqNum = 31
 * So, all in (1) are obviously older than 31 (31, if present, being the oldest, therefore 30 being the youngest).
 * For N in (1)
 *   If N >= 31
 *     // To keep our current oldestSoFar it would need to be (i) also >= 31 AND (ii) <= N
 *     If oldestSoFar < 31 OR oldestSoFar > N
 *       oldestSoFar = N
 *   Else // N is 0..30
 *     // To keep our current oldestSoFar it would need to be (i) >= 31 OR (ii) <= N
 *     If oldestSoFar < 31 AND oldestSoFar > N
 *       oldestSoFar = N
 *   EndIf
 * EndFor
 * But note that this is all a bit dodgy given that messages may be ejected before slurped.
 */
LogRecord* findOldestLogMessage(boolean filterByLevel, uint8_t filterLevel) {
  uint8_t oldestSequenceNum;
  boolean first = true;
  LogRecord* oldestLrPtr = NULL;
  for (uint8_t i = 0; i < LOG_RECORD_COUNT; i++) {
    LogRecord& lr = logRecords[i];
    if (lr.id != 0  &&  (!filterByLevel  ||   log_getLevel(lr.packed) == filterLevel)) {
      uint8_t sequenceNum = lr.sequenceNum;
      if (first) {
        first = false;
        oldestSequenceNum = sequenceNum; // bootstrap
        oldestLrPtr = &lr;
      } else {  // oldestSequenceNumis bootstrapped
        if (sequenceNum >= nextLogRecordSequenceNum) {
          if (oldestSequenceNum < nextLogRecordSequenceNum || oldestSequenceNum > sequenceNum) {
            oldestSequenceNum = sequenceNum;
            oldestLrPtr = &lr;
          }
        } else {
          if (oldestSequenceNum < nextLogRecordSequenceNum && oldestSequenceNum > sequenceNum) {
            oldestSequenceNum = sequenceNum;
            oldestLrPtr = &lr;
          }
        }
      }
    }
  }
  return oldestLrPtr;
}
LogRecord* findOldestLogMessage() {
  return findOldestLogMessage(false, 0);
}

/** Returns NULL if none can be found */
LogRecord* findLogRecordForNewMessage(uint8_t logLevel) {
  for (uint8_t i = 0; i < LOG_RECORD_COUNT; i++) {
    LogRecord& lr = logRecords[i];
    if (lr.id == 0) {
      return &logRecords[i];
    }
  }

  // If we get here, every record is occupied with a message. See if there's an inferior message to eject.
  // Pass 1: Determine the lowest log level present. (If it's higher than ours, quit.)
  uint8_t lowestLogLevel = 255;
  for (uint8_t i = 0; i < LOG_RECORD_COUNT; i++) {
    LogRecord& lr = logRecords[i];
    uint8_t logLevel = log_getLevel(lr.packed);
    if (lowestLogLevel > logLevel) {
      lowestLogLevel = logLevel;
    }
  }
  if (lowestLogLevel > logLevel) {
    return NULL; // quit
  }
  // Pass 2: Determine the oldest message with that log level.
  // (Note: We now know we'll find one. So we can set the following flag:)
  logDataEjected = true;
  return findOldestLogMessage(true, lowestLogLevel);
}

static const char* loggingLogPrefix = "LG";
static const char loggingLogErrorId = '!';

// This method that takes a pointer to a buffer (and a length). Copies no more than LOG_BYTES_MAX from buffer.
void logMsgBuffer(uint8_t logLevel, const char* prefix, char id, uint8_t chamberId, byte* buffer, uint8_t len) {
  // Try and find an unused slot
  LogRecord* lrPtr = findLogRecordForNewMessage(logLevel);
  if (lrPtr == NULL) {
    logDataEjected = true;
    return;
  }

  lrPtr->sequenceNum = nextLogRecordSequenceNum++;
  lrPtr->prefix = prefix;
  lrPtr->id = id;

  len = len > LOG_BYTES_MAX ? LOG_BYTES_MAX : len;
  // Copy into our buffer and null out any spare bytes
  for (uint8_t i = 0; i < LOG_BYTES_MAX; i++)
    lrPtr->buff[i] = i < len ? buffer[i] : 0;

  uint8_t packed = 0;
  log_setChamberId(packed, chamberId);
  log_setBuffLen(packed, len);
  log_setLevel(packed, logLevel);
  lrPtr->packed = packed;

  lrPtr->id = id;  // Finally, mark record as 'occupied'

  memoMinFreeRam(10);
}

byte _dummyLogParam;
template <typename A, typename B, typename C> void logMsg(uint8_t logLevel, const char* prefix, char id, uint8_t chamberId, const A &a, const B &b, const C &c) {
  uint8_t len = 0;
  uint8_t partLen;
  byte buffer[LOG_BYTES_MAX];
  if (&a != (void*) &_dummyLogParam) {
    partLen = sizeof(A);
    if (len + partLen > LOG_BYTES_MAX)
      return logMsg(LOG_ERROR, loggingLogPrefix, 'a', chamberId, id, prefix, strlen(prefix));
    memcpy(&buffer[len], &a, partLen);
    len += partLen;
  }
  if (&b != (void*) &_dummyLogParam) {
    partLen = sizeof(B);
    if (len + partLen > LOG_BYTES_MAX)
      return logMsg(LOG_ERROR, loggingLogPrefix, 'b', chamberId, id, prefix, strlen(prefix));
    memcpy(&buffer[len], &b, partLen);
    len += partLen;
  }
  if (&c != (void*) &_dummyLogParam) {
    partLen = sizeof(C);
    if (len + partLen > LOG_BYTES_MAX)
      return logMsg(LOG_ERROR, loggingLogPrefix, 'c', chamberId, id, prefix, strlen(prefix));
    memcpy(&buffer[len], &c, partLen);
    len += partLen;
  }
  logMsgBuffer(logLevel, prefix, id, chamberId, buffer, len);
}
template <typename A, typename B> void logMsg(uint8_t logLevel, const char* prefix, char id, uint8_t chamberId, const A &a, const B &b) {
  logMsg(logLevel, prefix, id, chamberId, a, b, _dummyLogParam);
}
template <typename A> void logMsg(uint8_t logLevel, const char* prefix, char id, uint8_t chamberId, const A &a) {
  logMsg(logLevel, prefix, id, chamberId, a, _dummyLogParam, _dummyLogParam);
}
void logMsg(uint8_t logLevel, const char* prefix, char id, uint8_t chamberId) {
  logMsg(logLevel, prefix, id, chamberId, _dummyLogParam, _dummyLogParam, _dummyLogParam);
}
void logMsg(uint8_t logLevel, const char* prefix, char id) {
  logMsg(logLevel, prefix, id, 1, _dummyLogParam, _dummyLogParam, _dummyLogParam);
}

void serialiseLogMessage(const LogRecord* lrPtr) {
  const uint8_t packed = lrPtr->packed;

  Serial.print(lrPtr->sequenceNum);
  printComma();
  Serial.print(log_getLevel(packed));
  printComma();
  Serial.print(lrPtr->prefix);
  printComma();
  Serial.print(lrPtr->id);
  printComma();
  Serial.print(log_getChamberId(packed));
  printComma();
  // Finally (if there is any binary data), buffLen and the data buffer (BASE64 encoded)
  uint8_t buffLen = log_getBuffLen(packed);
  if (buffLen > 0) {
    Serial.print(buffLen); // redundant but maybe useful as a check
    printComma();
    uint8_t b64EncodedLen = Base64.encodedLength(buffLen);
    char b64String[b64EncodedLen];
    Base64.encode(b64String, (char*) lrPtr->buff, buffLen);
    Serial.print(b64String);
  }
  memoMinFreeRam(11);
}
void deallocateLogRecord(LogRecord* lrPtr) {
  lrPtr->id = 0;
}

/** Called from setup() */
void initLoggingData() {
  for (byte i = 0; i < LOG_RECORD_COUNT; i++) {
    LogRecord& lr = logRecords[i];
    memset(&lr, 0, sizeof(LogRecord));
  }
}

// End
