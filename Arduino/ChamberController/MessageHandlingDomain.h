static const char CMD_STATUS[] PROGMEM = "status";
static const char CMD_SET_CHAMBER_PARAMS[] PROGMEM = "setParams:";
static const char CMD_GET_CHAMBER_READINGS[] PROGMEM = "getChmbrRds:";
static const char CMD_FLIP_LED[] PROGMEM = "flipLed";

void handleSetChamberParams(char* cmd) {
  int i = strlen(strFromProgMem(CMD_SET_CHAMBER_PARAMS));
  int j = nullNextComma(cmd, i);
  byte chamberId = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  int tTarget = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  int tTargetNext = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  int tMin = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  int tMax = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  boolean hasHeater = atoi(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  float Kp = atof(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  float Ki = atof(&cmd[i]);

  i = j;
  j = nullNextComma(cmd, i);
  float Kd = atof(&cmd[i]);

  ChamberData* cd = findChamber(chamberId);
Serial.println(chamberId);
Serial.println((int) cd);
  if (cd == NULL) {
    return respondWithError("chamberId,", itoa(chamberId));
  }
  updateChamberParams(cd, tTarget, tTargetNext, tMin, tMax, hasHeater, Kp, Ki, Kd);
  sendAck();
}

void handleGetChamberReadings(const char* cmd) {
  int i = strlen(strFromProgMem(CMD_GET_CHAMBER_READINGS));
  // The remainder of the request is the chamber ID
  byte chamberId = atoi(&cmd[i]);
  ChamberData* cd = findChamber(chamberId);
  if (cd == NULL) {
    return respondWithError("chamberId,", itoa(chamberId));
  }
  sendToMasterStart();
  Serial.print("chmbrRds:");
  Serial.print(cd->tTarget);
  sendComma();
  Serial.print(cd->tTargetNext);
  sendComma();
  Serial.print(cd->params.tMin);
  sendComma();
  Serial.print(cd->params.tMax);
  sendComma();
  Serial.print(cd->params.hasHeater);
  sendComma();
  Serial.print(cd->tBeer);
  sendComma();
  Serial.print(cd->tExternal);
  sendComma();
  Serial.print(cd->tChamber);
  sendComma();
  Serial.print(cd->tPi);
  sendComma();
  Serial.print(cd->heaterOutput);
  sendComma();
  Serial.print(cd->fridgeOn);
  sendComma();
  Serial.print(cd->mode);
  sendToMasterEnd();
}

void dispatchCmd(char* cmd) {
  if (strcmp_P(cmd, CMD_STATUS) == 0) {
    sendToMasterStart(); Serial.print("status:"); Serial.print(uptimeMins); sendComma(); Serial.print(minFreeRam); sendToMasterEnd();
  } else if (prefix(strFromProgMem(CMD_SET_CHAMBER_PARAMS), cmd)) {
    handleSetChamberParams(cmd);
  } else if (prefix(strFromProgMem(CMD_GET_CHAMBER_READINGS), cmd)) {
    handleGetChamberReadings(cmd);
  } else if (strcmp_P(cmd, CMD_FLIP_LED) == 0) {
    flipLed();
    sendToMasterStart(); Serial.print("ledState:"); Serial.print(ledState); sendToMasterEnd();
  } else {
    sendToMaster("UnrecCmd:", cmd);
  }
  flipLed();
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
