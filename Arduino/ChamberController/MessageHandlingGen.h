
static const char RESP_ERROR[] PROGMEM = "error:";

bool prefix(const char *pre, const char *str) {
  return strncmp(pre, str, strlen(pre)) == 0;
}

void sendToMasterStart() {
  Serial.print('^');
}
void sendToMasterEnd() {
  Serial.println('$'); // Line ending merely for readability in console. Ignored by recipient.
}
//void sendToMaster(String response) {
//  sendToMaster(response.c_str());
//}
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
//void sendToMaster(int response) {
//  sendToMasterStart();
//  Serial.print(response);
//  sendToMasterEnd();
//}
void sendAck() {
  sendToMaster("ack");
}
void respondWithError(const char* str1, const char* str2) {
  sendToMaster("err:", str1, str2);
}


//int parseInt(const char* str, int offset) {
//  return atoi(&str[offset]);
//}

// End
