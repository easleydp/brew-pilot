/**
 * Change this to define maximum number of chambers that need to be supported.
 */
#define CHAMBER_COUNT 2

/*
 * NOTE: Currently, chamber IDs must be contiguous starting from 1.
 * E.g., with CHAMBER_COUNT of 2, IDs must be 1 and 2.
 */

// Aim for the target temp specified in the ChamberParameters, if any. Otherwise, operate as per `HOLD`.
#define MODE_AUTO 'A'
// Aim to maintain tBeer as it was when this mode was engaged (reflected in tTarget).
#define MODE_HOLD 'O'
// Force cool (while < tMin). No heating.
#define MODE_COOL 'C'
// Force heat (while < tMax). No cooling.
#define MODE_HEAT 'H'
// No heating, no cooling, just monitoring.
#define MODE_NONE 'N'
// Unset. Only applicable to ChamberData.mode (ChamberParams,mode should always be set).
#define MODE_UNSET '-'


/**
 * Chamber Parameters that seldom change.
 * We have defaults for these in the program code but aim to override from
 * manager AND then save/restore the manager supplied values to/from EEPROM.
 * Saved in EEPROM in case of restart with no Pi.
 */
typedef struct {
  uint8_t chamberId;  // 1, 2, etc.

  int16_t tMin, tMax;
  boolean hasHeater;
  uint8_t fridgeMinOnTimeMins, fridgeMinOffTimeMins, fridgeSwitchOnLagMins;
  // Heater PID parameters
  float Kp, Ki, Kd;
  char mode;  // This is the mode set by the RPi. May be overridden locally (by the panel switch).

  uint16_t checksum;
} ChamberParams; // 20 bytes

/**
 * Chamber Parameters that change more regularly.
 */
typedef struct {
  uint8_t chamberId;
  int16_t tTarget;
  int16_t gyleAgeHours;
  uint16_t checksum;
} MovingChamberParams;

// Latest common readings
int16_t tExternal;
int16_t tPi;

typedef struct {
  // Parameters that seldom change
  ChamberParams params;

  // These are updated more regularly from the RPi
  int16_t gyleAgeHours; // -1 for beer fridge
  int16_t tTarget;  // We store tTarget in EEPROM on change but not more than once an hour
  int16_t tTargetNext;

  /*
   * Working data and readings
   */

  // Latest readings
  int16_t tBeer;
  int16_t tChamber;
  uint8_t heaterOutput; // 0..100
  boolean heaterElementOn; // Although heaterOutput is supposedly 0..100, in reality the heater element is either ON or OFF at any given point in time.
  boolean fridgeOn;
  char mode;  // `UNSET` unless locally overridden (by the panel switch)

  // PID state
  float priorError; // tTarget - tBeer, so -ve for cooling, +ve for heating
  float integral;

  uint8_t fridgeStateChangeMins;  // Tops out at 255 (code avoids wrapping). initChamberData() sets to 255
  uint8_t heaterElementStateChangeSecs;  // Tops out at 255 (code avoids wrapping). initChamberData() sets to 255
  int8_t tBeerLastDelta; // The last registered change in tBeer ((priorError-error)*10), with decay each time no change. Used to detect trend (+ve signifies rising / -ve falling).
} ChamberData;

static const char* logPrefixChamberData = "CD";

ChamberData chamberDataArray[CHAMBER_COUNT];

/** Assumes that, whatever structure is supplied, the last two bytes is a checksum to be ignored. */
template<typename T> uint16_t generateChecksum(const T &t) {
  uint16_t crc = 7;
  const uint8_t *ptr = (const uint8_t*) &t;
  for (uint8_t i = 0; i < sizeof(T) - sizeof(uint16_t) /* don't include the checksum */; i++) {
    crc= _crc16_update(crc, ptr[i]);
  }
  return crc;
}

/*
 * EEPROM helpers.
 * Memory map: One MovingChamberParams per chamber, followed by one ChamberParams per chamber.
 */
void getEepromMovingChamberParams(uint8_t chamberId, MovingChamberParams &mcp) {
  int addr = (chamberId - 1) * sizeof(MovingChamberParams);
  EEPROM.get(addr, mcp);
}
void putEepromMovingChamberParams(MovingChamberParams &mcp) {
  int addr = (mcp.chamberId - 1) * sizeof(MovingChamberParams);
  EEPROM.put(addr, mcp);
}
void getEepromChamberParams(uint8_t chamberId, ChamberParams &params) {
  int base = CHAMBER_COUNT * sizeof(MovingChamberParams);
  int addr = base + (chamberId - 1) * sizeof(ChamberParams);
  EEPROM.get(addr, params);
}
void putEepromChamberParams(ChamberParams &params) {
  int base = CHAMBER_COUNT * sizeof(MovingChamberParams);
  int addr = base + (params.chamberId - 1) * sizeof(ChamberParams);
  EEPROM.put(addr, params);
}

ChamberData* findChamber(byte chamberId) {
  for (byte i = 0; i < CHAMBER_COUNT; i++) {
    ChamberData* cd = &chamberDataArray[i];
    if (cd->params.chamberId == chamberId)
      return cd;
  }
  return NULL;
}

void saveMovingChamberParams(uint8_t chamberId, int16_t tTarget, int16_t gyleAgeHours) {
  MovingChamberParams movingChamberParams = { chamberId, tTarget, gyleAgeHours, 0 };
  movingChamberParams.checksum = generateChecksum(movingChamberParams);
  putEepromMovingChamberParams(movingChamberParams);
  memoMinFreeRam(20);
}

const unsigned long saveMovingChamberParamsInterval = 1000L * 60 * 60; // save tTarget every hour
uint32_t millisSinceLastTTargetSave[CHAMBER_COUNT] = {0, 0};
uint32_t prevMillisTTargetSave[CHAMBER_COUNT] = {0, 0};
void saveMovingChamberParamsOnceInAWhile(uint8_t chamberId, int16_t tTarget, int16_t gyleAgeHours) {
  if (TIME_UP(prevMillisTTargetSave[chamberId - 1], uptimeMillis, saveMovingChamberParamsInterval)) {
    millisSinceLastTTargetSave[chamberId - 1] = 0;
    saveMovingChamberParams(chamberId, tTarget, gyleAgeHours);
    logMsg(LOG_DEBUG, logPrefixChamberData, '2', chamberId, tTarget/* int16_t */, gyleAgeHours/* int16_t */);

    prevMillisTTargetSave[chamberId - 1] = uptimeMillis;
    memoMinFreeRam(21);
  }
}

boolean movingChamberParamsSaved[CHAMBER_COUNT] = {false, false};
void updateChamberParams(
  ChamberData& cd, int16_t gyleAgeHours, int16_t tTarget, int16_t tTargetNext, int16_t tMin, int16_t tMax, boolean hasHeater,
  uint8_t fridgeMinOnTimeMins, uint8_t fridgeMinOffTimeMins, uint8_t fridgeSwitchOnLagMins,
  float Kp, float Ki, float Kd, char mode) {

  uint8_t chamberId = cd.params.chamberId;

  logMsg(LOG_DEBUG, logPrefixChamberData, '0', chamberId, tTarget/* int16_t */, mode/* char */);

  cd.gyleAgeHours = gyleAgeHours;
  cd.tTarget = tTarget;
  cd.tTargetNext = tTargetNext;

  cd.params.tMin = tMin;
  cd.params.tMax = tMax;
  cd.params.hasHeater = hasHeater;
  cd.params.fridgeMinOnTimeMins = fridgeMinOnTimeMins;
  cd.params.fridgeMinOffTimeMins = fridgeMinOffTimeMins;
  cd.params.fridgeSwitchOnLagMins = fridgeSwitchOnLagMins;
  cd.params.Kp = Kp;
  cd.params.Ki = Ki;
  cd.params.Kd = Kd;
  cd.params.mode = mode;
  cd.params.checksum = generateChecksum(cd.params);
  putEepromChamberParams(cd.params);

  if (!movingChamberParamsSaved[chamberId - 1]) {
    saveMovingChamberParams(chamberId, tTarget, gyleAgeHours);
    movingChamberParamsSaved[chamberId - 1] = true;
    logMsg(LOG_DEBUG, logPrefixChamberData, '1', chamberId, tTarget/* int16_t */, gyleAgeHours/* int16_t */);
  } else {
    saveMovingChamberParamsOnceInAWhile(chamberId, tTarget, gyleAgeHours);
  }
}

/** Called from setup() */
void initChamberData() {
  for (byte i = 0; i < CHAMBER_COUNT; i++) {
    ChamberData& cd = chamberDataArray[i];
    memset(&cd, 0, sizeof(ChamberData));
    cd.fridgeStateChangeMins = 255;
    cd.heaterElementStateChangeSecs = 255;
    cd.mode = MODE_UNSET;  // If this becomes set it will override params.mode
    ChamberParams& params = cd.params;
    params.chamberId = i + 1;

    // Default params & tTarget, before we check EEPROM
    params.tMin = -10;
    params.tMax = 400;
    params.hasHeater = true;
    params.fridgeMinOnTimeMins = 10;
    params.fridgeMinOffTimeMins = 15;
    params.fridgeSwitchOnLagMins = 0;
    params.Kp = 16.0f;
    params.Ki = 0.32f;
    params.Kd = 20.0f;
    params.mode = MODE_HOLD;
    params.checksum = generateChecksum(params);
    cd.tTarget = cd.tTargetNext = 160;

    ChamberParams eepromParams = {};
    getEepromChamberParams(params.chamberId, eepromParams);
    if (eepromParams.chamberId == params.chamberId) {
      if (eepromParams.checksum == generateChecksum(eepromParams)) {
        memcpy(&params, &eepromParams, sizeof(ChamberParams));
        logMsg(LOG_DEBUG, logPrefixChamberData, 'p', params.chamberId);
      } else {
        logMsg(LOG_ERROR, logPrefixChamberData, 'P', params.chamberId);
      }
    } else {
      logMsg(LOG_ERROR, logPrefixChamberData, 'Q', params.chamberId, eepromParams.chamberId/* uint8_t */);
    }

    MovingChamberParams mcp = {};
    getEepromMovingChamberParams(params.chamberId, mcp);
    if (mcp.chamberId == params.chamberId) {
      if (mcp.checksum == generateChecksum(mcp)) {
        cd.tTarget = cd.tTargetNext = mcp.tTarget;
        logMsg(LOG_DEBUG, logPrefixChamberData, 't', params.chamberId, mcp.tTarget/* int16_t */, mcp.gyleAgeHours/* int16_t */);
      } else {
        logMsg(LOG_ERROR, logPrefixChamberData, 'T', params.chamberId, mcp.tTarget/* int16_t */, mcp.gyleAgeHours/* int16_t */);
      }
    } else {
      logMsg(LOG_ERROR, logPrefixChamberData, 'U', params.chamberId, mcp.chamberId/* uint8_t */);
    }
  }
}

// End
