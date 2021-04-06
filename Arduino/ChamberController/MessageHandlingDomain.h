static const char CMD_STATUS[] PROGMEM = "status";
static const char CMD_SET_CHAMBER_PARAMS[] PROGMEM = "setChParams:";
static const char CMD_GET_CHAMBER_READINGS[] PROGMEM = "getChRds:";
//static const char CMD_TEST_LOG_MESSAGE[] PROGMEM = "testLogMsg:";
static const char CMD_GET_LOG_MESSAGES[] PROGMEM = "getLogMsgs";
//static const char CMD_FLIP_LED[] PROGMEM = "flipLed";

void handleSetChamberParams(char* cmd) {
  int i = strlen(strFromProgMem(CMD_SET_CHAMBER_PARAMS));
  int j = nullNextComma(cmd, i);
  byte chamberId = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  int16_t gyleAgeHours = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  int16_t tTarget = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  int16_t tTargetNext = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  int16_t tMin = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  int16_t tMax = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  boolean hasHeater = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  uint8_t fridgeMinOnTimeMins = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  uint8_t fridgeMinOffTimeMins = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  uint8_t fridgeSwitchOnLagMins = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  float Kp = atof(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  float Ki = atof(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  float Kd = atof(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  char mode = cmd[i];

  ChamberData* cdPtr = findChamber(chamberId);
  if (cdPtr == NULL) {
    return respondWithError("chamberId,", itoa(chamberId));
  }
  setChamberParams(*cdPtr, gyleAgeHours, tTarget, tTargetNext, tMin, tMax, hasHeater,
    fridgeMinOnTimeMins, fridgeMinOffTimeMins, fridgeSwitchOnLagMins, Kp, Ki, Kd, mode);
  sendAck();
}

void handleGetChamberReadings(const char* cmd) {
  int i = strlen(strFromProgMem(CMD_GET_CHAMBER_READINGS));
  // The remainder of the request is the chamber ID
  byte chamberId = atoi(&cmd[i]);
  const ChamberData* cdPtr = findChamber(chamberId);
  if (cdPtr == NULL) {
    return respondWithError("chamberId,", itoa(chamberId));
  }
  sendToMasterStart();
  Serial.print(F("chRds:"));
  Serial.print(cdPtr->mParams.gyleAgeHours);
  printComma();
  Serial.print(cdPtr->mParams.tTarget);
  printComma();
  Serial.print(cdPtr->mParams.tTargetNext);
  printComma();
  Serial.print(cdPtr->params.tMin);
  printComma();
  Serial.print(cdPtr->params.tMax);
  printComma();
  Serial.print(cdPtr->params.hasHeater);
  printComma();
  Serial.print(cdPtr->params.fridgeMinOnTimeMins);
  printComma();
  Serial.print(cdPtr->params.fridgeMinOffTimeMins);
  printComma();
  Serial.print(cdPtr->params.fridgeSwitchOnLagMins);
  printComma();
  Serial.print(cdPtr->params.Kp);
  printComma();
  Serial.print(cdPtr->params.Ki);
  printComma();
  Serial.print(cdPtr->params.Kd);
  printComma();
  Serial.print(cdPtr->params.mode);
  printComma();
  Serial.print(cdPtr->tBeer);
  printComma();
  Serial.print(cdPtr->tChamber);
  printComma();
  Serial.print(tExternal);
  printComma();
  Serial.print(tProjectBox);
  printComma();
  Serial.print(cdPtr->heaterOutput);
  printComma();
  Serial.print(cdPtr->fridgeOn);
  sendToMasterEnd();
}

//void handleTestLogMessage(char* cmd) {
//  int i = strlen(strFromProgMem(CMD_TEST_LOG_MESSAGE));
//  int j = nullNextComma(cmd, i);
//  uint8_t logLevel = atoi(&cmd[i]);
//
//  i = j;
//  j = nullNextComma(cmd, i);
//  char id = cmd[i];
//
//  i = j;
//  j = nullNextComma(cmd, i);
//  uint8_t chamberId = atoi(&cmd[i]);
//
//  i = j;
//  j = nullNextComma(cmd, i);
//  float _float = atof(&cmd[i]);
//
//  logMsg(logLevel, "TST", id, chamberId, _float + 1.0, logLevel, sizeof(float));
//
//  sendAck();
//}

void handleGetLogMessages() {
  while (LogRecord* lrPtr = findOldestLogMessage()) {
    sendToMasterStart();
    Serial.print(F("logMsg:"));
    slurpLogMessage(lrPtr);
    sendToMasterEnd();
  }
  sendAck();
}

void handleStatus() {
  sendToMasterStart();
  Serial.print(F("status:")); Serial.print(uptimeMins);
  printComma(); Serial.print(tExternal);
  printComma(); Serial.print(tProjectBox);
  printComma(); Serial.print(minFreeRam);
  printComma(); Serial.print(minFreeRamLocation);
  printComma(); Serial.print(badSensorCount);
  printComma(); Serial.print(logBufferCannibalised);
  logBufferCannibalised = false; // reset, now that we've notified
  sendToMasterEnd();
}

void dispatchCmd(char* cmd) {
  if (strcmp_P(cmd, CMD_STATUS) == 0) {
    handleStatus();
  } else if (prefix(strFromProgMem(CMD_SET_CHAMBER_PARAMS), cmd)) {
    handleSetChamberParams(cmd);
  } else if (prefix(strFromProgMem(CMD_GET_CHAMBER_READINGS), cmd)) {
    handleGetChamberReadings(cmd);
  // } else if (prefix(strFromProgMem(CMD_TEST_LOG_MESSAGE), cmd)) {
  //   handleTestLogMessage(cmd);
  } else if (strcmp_P(cmd, CMD_GET_LOG_MESSAGES) == 0) {
    handleGetLogMessages();
  // } else if (strcmp_P(cmd, CMD_FLIP_LED) == 0) {
  //   //flipLed();
  //   sendToMasterStart(); Serial.print(F("ledState:")); Serial.print(ledState); sendToMasterEnd();
  } else {
    sendToMaster("UnrecCmd:", cmd);
  }
}

byte iIpBuf = 0;
byte ipBuffState = 0;
char ipBuf[128];
void handleMessages() {
  while (Serial.available()) {
    byte b = Serial.read();
    switch (b) {
      case 0x5E: // '^'
        if (ipBuffState == 0) {
          iIpBuf = 0;
          ipBuffState = 1;
        }
        break;
      case 0x24: // '$'
        if (ipBuffState == 1) {
          ipBuf[iIpBuf] = 0;
          ipBuffState = 0;
          dispatchCmd(ipBuf);
        }
        break;
      default:
        if (ipBuffState == 1  &&  b > 0x1F) {
          ipBuf[iIpBuf++] = b;
          if (iIpBuf == sizeof(ipBuf)) {
            ipBuf[iIpBuf - 1] = 0;
            sendToMaster("ipBufOvr:", ipBuf);
            iIpBuf = 0;
            ipBuffState = 0;
          }
        }
        break;
    }
  }
}

// End
