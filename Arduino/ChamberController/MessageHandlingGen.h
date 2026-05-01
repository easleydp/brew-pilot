/**
 * @file MessageHandlingGen.h
 * @brief General serial communication helpers.
 */
#ifndef MESSAGE_HANDLING_GEN_H
#define MESSAGE_HANDLING_GEN_H

#include "Common.h"

bool prefix(const char* pre, const char* str);

void sendToMasterStart();
void sendToMasterEnd();

void sendToMaster(const char* str1, const char* str2, const char* str3);
void sendToMaster(const char* str1, const char* str2);
void sendToMaster(const char* response);

void sendAck();
void respondWithError(const char* str1, const char* str2);

#endif  // MESSAGE_HANDLING_GEN_H
