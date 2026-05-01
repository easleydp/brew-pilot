/**
 * @file Logging.h
 * @brief Circular buffer logging system.
 */
#ifndef LOGGING_H
#define LOGGING_H

#include <Base64.h>  // https://github.com/agdl/Base64

#include "Common.h"

// Rather than set a logLevel, we try and log everything but if buffer space runs out we cannibalise starting with debug messages.
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
// bits 6,5,4,3,2: buffLen (0..LOG_BYTES_MAX)
// bits 1,0: logLevel
#define log_getChamberId(byte) ((((byte) & 0x80) >> 7) + 1)
#define log_setChamberId(byte, cid) ((byte) |= ((cid - 1) << 7))
#define log_getBuffLen(byte) (((byte) & 0x7C) >> 2)
#define log_setBuffLen(byte, len) ((byte) |= (((len) << 2) & 0x7C))
#define log_getLevel(byte) ((byte) & 0x03)
#define log_setLevel(byte, level) ((byte) |= ((level) & 0x03))

// Sanity check. Given we only allocate 5 bits for buffLen, max value is 0x1F
#if LOG_BYTES_MAX > 0x1F
#error "LOG_BYTES_MAX is too large."
#endif

static const char* mainLogPrefix = "MN";

typedef struct {
  uint8_t sequenceNum;  // Assuming LOG_RECORD_COUNT <= 255
  uint8_t packed;
  const char* prefix;  // e.g. "PID"
  char id;  // e.g. '+'. Also serves to denote record occupied (0 signifies unoccupied).
  byte buff[LOG_BYTES_MAX];  // binary data
} LogRecord;

extern LogRecord logRecords[LOG_RECORD_COUNT];

// This will keep track of whether we had to resort to ejecting older messages (or whether we failed
// to find a slot for an inferior message). This will be sent to RPi whenever it pulls some messages.
// After the RPi pulls some messages thereby freeing records, we'll set it back to false.
extern boolean logBufferCannibalised;

// Assuming LOG_RECORD_COUNT <= 255
extern uint8_t nextLogRecordSequenceNum;  // Modulo 255

/**
 * Returns the oldest occupied log record or NULL if none.
 * Optionally filters to the specfified log level if filterByLevel is true.
 */
LogRecord* findOldestLogMessage(boolean filterByLevel, uint8_t filterLevel);
LogRecord* findOldestLogMessage();

/** Returns NULL if none can be found */
LogRecord* findLogRecordForNewMessage(uint8_t logLevel);

// This method that takes a pointer to a buffer (and a length). Copies no more than LOG_BYTES_MAX from buffer.
void logMsgBuffer(uint8_t logLevel, const char* prefix, char id, uint8_t chamberId, byte* buffer, uint8_t len);

extern byte _dummyLogParam;
template <typename A, typename B, typename C>
void logMsg(uint8_t logLevel, const char* prefix, char id, uint8_t chamberId, const A& a, const B& b, const C& c) {
  uint8_t len = 0;
  uint8_t partLen;
  byte buffer[LOG_BYTES_MAX];
  if (&a != (void*)&_dummyLogParam) {
    partLen = sizeof(A);
    if (len + partLen > LOG_BYTES_MAX)
      return logMsgBuffer(LOG_ERROR, "LG", 'a', chamberId, (byte*)prefix, strlen(prefix));
    memcpy(&buffer[len], &a, partLen);
    len += partLen;
  }
  if (&b != (void*)&_dummyLogParam) {
    partLen = sizeof(B);
    if (len + partLen > LOG_BYTES_MAX)
      return logMsgBuffer(LOG_ERROR, "LG", 'b', chamberId, (byte*)prefix, strlen(prefix));
    memcpy(&buffer[len], &b, partLen);
    len += partLen;
  }
  if (&c != (void*)&_dummyLogParam) {
    partLen = sizeof(C);
    if (len + partLen > LOG_BYTES_MAX)
      return logMsgBuffer(LOG_ERROR, "LG", 'c', chamberId, (byte*)prefix, strlen(prefix));
    memcpy(&buffer[len], &c, partLen);
    len += partLen;
  }
  logMsgBuffer(logLevel, prefix, id, chamberId, buffer, len);
}
template <typename A, typename B>
void logMsg(uint8_t logLevel, const char* prefix, char id, uint8_t chamberId, const A& a, const B& b) {
  logMsg(logLevel, prefix, id, chamberId, a, b, _dummyLogParam);
}
template <typename A>
void logMsg(uint8_t logLevel, const char* prefix, char id, uint8_t chamberId, const A& a) {
  logMsg(logLevel, prefix, id, chamberId, a, _dummyLogParam, _dummyLogParam);
}
void logMsg(uint8_t logLevel, const char* prefix, char id, uint8_t chamberId);
void logMsg(uint8_t logLevel, const char* prefix, char id);

/**
 * Serialises then deallocates a log record.
 * Called while RPi is slurping log messages.
 */
void slurpLogMessage(LogRecord* lrPtr);

/** Called from setup() */
void initLoggingData();

#endif  // LOGGING_H
