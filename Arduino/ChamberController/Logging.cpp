/**
 * @file Logging.cpp
 * @brief Circular buffer logging system.
 */
#include "Logging.h"

LogRecord logRecords[LOG_RECORD_COUNT];

// This will keep track of whether we had to resort to ejecting older messages (or whether we failed
// to find a slot for an inferior message). This will be sent to RPi whenever it pulls some messages.
// After the RPi pulls some messages thereby freeing records, we'll set it back to false.
boolean logBufferCannibalised = false;

// Assuming LOG_RECORD_COUNT <= 255
uint8_t nextLogRecordSequenceNum = 0;  // Modulo 255

byte _dummyLogParam;

/**
 * Returns the oldest occupied log record or NULL if none.
 * Optionally filters to the specfified log level if filterByLevel is true.
 */
LogRecord* findOldestLogMessage(boolean filterByLevel, uint8_t filterLevel) {
  uint8_t oldestSequenceNum;
  boolean first = true;
  LogRecord* oldestLrPtr = NULL;
  for (uint8_t i = 0; i < LOG_RECORD_COUNT; i++) {
    LogRecord& lr = logRecords[i];
    if (lr.id != 0 && (!filterByLevel || log_getLevel(lr.packed) == filterLevel)) {
      uint8_t sequenceNum = lr.sequenceNum;
      if (first) {
        first = false;
        oldestSequenceNum = sequenceNum;  // bootstrap
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
    return NULL;  // quit
  }
  // Pass 2: Determine the oldest message with that log level.
  // (Note: We now know we'll find one. So we can set the following flag:)
  logBufferCannibalised = true;
  return findOldestLogMessage(true, lowestLogLevel);
}

// This method that takes a pointer to a buffer (and a length). Copies no more than LOG_BYTES_MAX from buffer.
void logMsgBuffer(uint8_t logLevel, const char* prefix, char id, uint8_t chamberId, byte* buffer, uint8_t len) {
  // Try and find an unused slot
  LogRecord* lrPtr = findLogRecordForNewMessage(logLevel);
  if (lrPtr == NULL) {
    logBufferCannibalised = true;
    return;
  }

  lrPtr->sequenceNum = nextLogRecordSequenceNum++;
  lrPtr->prefix = prefix;
  lrPtr->id = id;

  len = len > LOG_BYTES_MAX ? LOG_BYTES_MAX : len;
  // Copy into our buffer and null out any spare bytes
  for (uint8_t i = 0; i < LOG_BYTES_MAX; i++)
    lrPtr->buff[i] = i < len ? buffer[i] : 0;

  // Pack chamberId, buffLen and level into one byte
  uint8_t packed = 0;
  log_setChamberId(packed, chamberId);
  log_setBuffLen(packed, len);
  log_setLevel(packed, logLevel);
  lrPtr->packed = packed;

  lrPtr->id = id;  // Finally, mark record as 'occupied'

  memoMinFreeRam(10);
}

void logMsg(uint8_t logLevel, const char* prefix, char id, uint8_t chamberId) {
  logMsg(logLevel, prefix, id, chamberId, _dummyLogParam, _dummyLogParam, _dummyLogParam);
}

void logMsg(uint8_t logLevel, const char* prefix, char id) {
  logMsg(logLevel, prefix, id, 1, _dummyLogParam, _dummyLogParam, _dummyLogParam);
}

/**
 * Serialises then deallocates a log record.
 * Called while RPi is slurping log messages.
 */
void slurpLogMessage(LogRecord* lrPtr) {
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
    Serial.print(buffLen);  // redundant but maybe useful as a check
    printComma();
    uint8_t b64EncodedLen = Base64.encodedLength(buffLen);
    char b64String[b64EncodedLen];
    Base64.encode(b64String, (char*)lrPtr->buff, buffLen);
    Serial.print(b64String);
  }
  memoMinFreeRam(11);

  // Deallocate the record
  lrPtr->id = 0;
}

/** Called from setup() */
void initLoggingData() {
  for (byte i = 0; i < LOG_RECORD_COUNT; i++) {
    LogRecord& lr = logRecords[i];
    memset(&lr, 0, sizeof(LogRecord));
  }
}
