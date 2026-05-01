/**
 * @file MessageHandlingGen.cpp
 * @brief General serial communication helpers.
 */
#include "MessageHandlingGen.h"

bool prefix(const char* pre, const char* str) {
  return strncmp(pre, str, strlen(pre)) == 0;
}

void sendToMasterStart() {
  Serial.print('^');
}

void sendToMasterEnd() {
  Serial.println('$');  // Line ending merely for readability in console. Ignored by recipient.
}

void sendToMaster(const char* str1, const char* str2, const char* str3) {
  sendToMasterStart();
  Serial.print(str1);
  if (str2 != NULL) {
    Serial.print(str2);
    if (str3 != NULL) {
      Serial.print(str3);
    }
  }
  sendToMasterEnd();
}

void sendToMaster(const char* str1, const char* str2) {
  sendToMaster(str1, str2, NULL);
}

void sendToMaster(const char* response) {
  sendToMaster(response, NULL, NULL);
}

void sendAck() {
  sendToMaster("ack");
}

void respondWithError(const char* str1, const char* str2) {
  sendToMaster("err:", str1, str2);
}
