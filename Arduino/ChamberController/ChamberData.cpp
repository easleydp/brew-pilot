/**
 * @file ChamberData.cpp
 * @brief EEPROM management and chamber state data.
 */
#include "ChamberData.h"
#include <util/crc16.h>

int16_t tExternal;
int16_t tProjectBox;

ChamberData chamberDataArray[CHAMBER_COUNT];

uint32_t millisSinceLastTTargetSave[CHAMBER_COUNT] = {0, 0};
uint32_t prevMillisTTargetSave[CHAMBER_COUNT] = {0, 0};
boolean movingChamberParamsSaved[CHAMBER_COUNT] = {false, false};

void getEepromMovingChamberParams(uint8_t chamberId, MovingChamberParams& mParams) {
  int addr = (chamberId - 1) * sizeof(MovingChamberParams);
  EEPROM.get(addr, mParams);
}

void putEepromMovingChamberParams(uint8_t chamberId, MovingChamberParams& mParams) {
  mParams.checksum = generateChecksum(mParams);
  int addr = (chamberId - 1) * sizeof(MovingChamberParams);
  EEPROM.put(addr, mParams);
}

void getEepromChamberParams(uint8_t chamberId, ChamberParams& params) {
  int base = CHAMBER_COUNT * sizeof(MovingChamberParams);
  int addr = base + (chamberId - 1) * sizeof(ChamberParams);
  EEPROM.get(addr, params);
}

void putEepromChamberParams(uint8_t chamberId, ChamberParams& params) {
  params.checksum = generateChecksum(params);
  int base = CHAMBER_COUNT * sizeof(MovingChamberParams);
  int addr = base + (chamberId - 1) * sizeof(ChamberParams);
  EEPROM.put(addr, params);
}

ChamberData* findChamber(byte chamberId) {
  for (byte i = 0; i < CHAMBER_COUNT; i++) {
    ChamberData* cd = &chamberDataArray[i];
    if (cd->chamberId == chamberId)
      return cd;
  }
  return NULL;
}

void saveMovingChamberParams(uint8_t chamberId, MovingChamberParams& mParams) {
  putEepromMovingChamberParams(chamberId, mParams);
  memoMinFreeRam(20);
}

const unsigned long saveMovingChamberParamsInterval = 1000L * 60 * 60;  // save every hour

void saveMovingChamberParamsOnceInAWhile(uint8_t chamberId, MovingChamberParams& mParams) {
  if (TIME_UP(prevMillisTTargetSave[chamberId - 1], uptimeMillis, saveMovingChamberParamsInterval)) {
    millisSinceLastTTargetSave[chamberId - 1] = 0;
    saveMovingChamberParams(chamberId, mParams);
    logMsg(LOG_DEBUG, "CD", '2', chamberId);

    prevMillisTTargetSave[chamberId - 1] = uptimeMillis;
    memoMinFreeRam(21);
  }
}

void setChamberParams(
    ChamberData& cd, int16_t gyleAgeHours, int16_t tTarget, int16_t tTargetNext, int16_t tMin, int16_t tMax, boolean hasHeater,
    uint8_t fridgeMinOnTimeMins, uint8_t fridgeMinOffTimeMins, uint8_t fridgeSwitchOnLagMins, float Kp, float Ki, float Kd, char mode) {
  logMsg(LOG_DEBUG, "CD", '0', cd.chamberId, tTarget /* int16_t */, mode /* char */);

  cd.mParams.tTarget = tTarget;
  cd.mParams.tTargetNext = tTargetNext;
  cd.mParams.gyleAgeHours = gyleAgeHours;

  cd.params.mode = mode;
  cd.params.tMin = tMin;
  cd.params.tMax = tMax;
  cd.params.hasHeater = hasHeater;
  cd.params.fridgeMinOnTimeMins = fridgeMinOnTimeMins;
  cd.params.fridgeMinOffTimeMins = fridgeMinOffTimeMins;
  cd.params.fridgeSwitchOnLagMins = fridgeSwitchOnLagMins;
  cd.params.Kp = Kp;
  cd.params.Ki = Ki;
  cd.params.Kd = Kd;
  putEepromChamberParams(cd.chamberId, cd.params);

  if (!movingChamberParamsSaved[cd.chamberId - 1]) {
    saveMovingChamberParams(cd.chamberId, cd.mParams);
    movingChamberParamsSaved[cd.chamberId - 1] = true;
    logMsg(LOG_DEBUG, "CD", '1', cd.chamberId);
  } else {
    saveMovingChamberParamsOnceInAWhile(cd.chamberId, cd.mParams);
  }
}

/** Called from setup() */
void initChamberData() {
  for (byte i = 0; i < CHAMBER_COUNT; i++) {
    ChamberData& cd = chamberDataArray[i];
    memset(&cd, 0, sizeof(ChamberData));
    uint8_t chamberId = cd.chamberId = i + 1;
    cd.fridgeLastToggleMins = 255;
    cd.heaterLastToggleMins = 255;
    cd.heaterElementStateChangeSecs = 255;
    ChamberParams& params = cd.params;
    MovingChamberParams& mParams = cd.mParams;

    // These values will typically be replaced by the value from EEPROM then later by values from RPi
    mParams.tTarget = mParams.tTargetNext = 160;
    mParams.gyleAgeHours = 0;
    params.tMin = -10;
    params.tMax = 400;
    params.hasHeater = true;
    params.fridgeMinOnTimeMins = 10;
    params.fridgeMinOffTimeMins = 15;
    params.fridgeSwitchOnLagMins = 0;
    params.Kp = 16.0f;
    params.Ki = 0.32f;
    params.Kd = 20.0f;
    params.mode = MODE_MONITOR_ONLY;

    {
      ChamberParams eepromParams = {};
      getEepromChamberParams(chamberId, eepromParams);
      if (eepromParams.checksum == generateChecksum(eepromParams)) {
        memcpy(&params, &eepromParams, sizeof(ChamberParams));
        logMsg(LOG_DEBUG, "CD", 'p', chamberId);
      } else {
        logMsg(LOG_ERROR, "CD", 'P', chamberId);
      }
    }
    {
      MovingChamberParams eepromMParams = {};
      getEepromMovingChamberParams(chamberId, eepromMParams);
      if (eepromMParams.checksum == generateChecksum(eepromMParams)) {
        memcpy(&mParams, &eepromMParams, sizeof(MovingChamberParams));
        logMsg(LOG_DEBUG, "CD", 't', chamberId);
      } else {
        logMsg(LOG_ERROR, "CD", 'T', chamberId);
      }
    }
  }
}
