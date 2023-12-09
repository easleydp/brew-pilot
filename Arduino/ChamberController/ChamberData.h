/**
 * Change this to define maximum number of chambers that need to be supported.
 */
#define CHAMBER_COUNT 2

/*
 * NOTE: Currently, chamber IDs must be contiguous starting from 1.
 * E.g., with CHAMBER_COUNT of 2, IDs must be 1 and 2.
 */

// Aim for the target temp specified in the ChamberParameters.
#define MODE_AUTO 'A'
// Aim to maintain tBeer as it was when this mode was engaged. (This will be reflected
// in tTarget, so no special logic required on this side.)
#define MODE_HOLD 'H'
// As AUTO but disable heater.
#define MODE_DISABLE_HEATER '*'
// As AUTO but disable fridge.
#define MODE_DISABLE_FRIDGE '~'
// No heating, no cooling, just monitoring.
#define MODE_MONITOR_ONLY 'M'

struct ChecksummedParams {
  uint16_t checksum;
};

/**
 * Chamber Parameters that change infrequently.
 * We have defaults for these in the program code but aim to override from
 * manager AND then save/restore the manager supplied values to/from EEPROM.
 *
 * Saved in EEPROM in case of restart with no Pi. Note: we can afford to flush
 * to EEPROM regularly knowing that the EEPROM library will filter if no change.
 */
struct ChamberParams : ChecksummedParams {
  char mode;
  boolean hasHeater;
  // Fridge will stay on at least this long. NOTE: This is the effective on time,
  // i.e. power on time - fridgeSwitchOnLagMins
  uint8_t fridgeMinOnTimeMins;
  // Fridge will stay off at least this long.
  uint8_t fridgeMinOffTimeMins;
  // Some fridges have a built-in anti-cycling feature whereby, after power-up,
  // a few minutes elapse before the compressor is actually switched on.
  uint8_t fridgeSwitchOnLagMins;
  int16_t tMin, tMax;
  // Heater PID parameters
  float Kp, Ki, Kd;
};

/**
 * Chamber Parameters that change frequently. Therefore only saved to EEPROM once in a while.
 */
struct MovingChamberParams : ChecksummedParams {
  int16_t tTarget;
  int16_t tTargetNext;
  int16_t gyleAgeHours;
  float integral;  // Worth preserving this since it may have accumulated a useful value over time
};

// Latest common readings
int16_t tExternal;
int16_t tProjectBox;

typedef struct {
  uint8_t chamberId;  // 1, 2, etc.

  // Parameters that seldom change
  ChamberParams params;

  // These are updated frequently (by the RPi or, in the case of `integral`, by us)
  MovingChamberParams mParams;

  /*
   * Working data and readings
   */

  // Latest readings
  int16_t tBeer;
  int16_t tChamber;
  uint8_t heaterOutput;  // 0..100
  boolean heaterElementOn;  // Although heaterOutput is supposedly 0..100, the actual heater element is either
                            // ON or OFF at any given point in time.
  boolean fridgeOn;

  // PID state
  float priorError;  // tTarget - tBeer, so -ve for cooling, +ve for heating
  // float integral;  Moved to MovingChamberParams

  uint8_t fridgeLastToggleMins;  // Time since last fridge activation/deactivation, i.e. fridgeOn changing between ON & OFF.
                                 // Tops out at 255 (code avoids wrapping). initChamberData() sets to 255
  uint8_t heaterLastToggleMins;  // Ditto but for the heater, i.e. heaterOutput changing between zero and non-zero.

  uint8_t heaterElementStateChangeSecs;  // This is to do with pulsing the actual heater element to get a pseudo variable output.
                                         // Tops out at 255 (code avoids wrapping). initChamberData() sets to 255

  int8_t tBeerLastDelta;  // The last registered change in tBeer ((priorError-error)*10), with decay each time no
                          // change. Used to detect recent trend (+ve signifies rising / -ve falling).
} ChamberData;

static const char* logPrefixChamberData = "CD";

ChamberData chamberDataArray[CHAMBER_COUNT];

/**
 * Assumes that the supplied structure has a uint16_t `checksum` field that should NOT be included in the computation.
 * The supplied structure is temporaily modified: `checksum` field is set to zero and later restored to initial value.
 */
template <typename T>
uint16_t generateChecksum(T& t) {
  uint16_t checksum = t.checksum;
  t.checksum = 0;

  uint16_t crc = 7;
  const uint8_t* ptr = (const uint8_t*)&t;
  for (uint8_t i = 0; i < sizeof(T); i++) {
    crc = _crc16_update(crc, ptr[i]);
  }

  t.checksum = checksum;
  return crc;
}

/*
 * EEPROM helpers.
 * Memory map: One MovingChamberParams per chamber, followed by one ChamberParams per chamber.
 */
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
uint32_t millisSinceLastTTargetSave[CHAMBER_COUNT] = {0, 0};
uint32_t prevMillisTTargetSave[CHAMBER_COUNT] = {0, 0};
void saveMovingChamberParamsOnceInAWhile(uint8_t chamberId, MovingChamberParams& mParams) {
  if (TIME_UP(prevMillisTTargetSave[chamberId - 1], uptimeMillis, saveMovingChamberParamsInterval)) {
    millisSinceLastTTargetSave[chamberId - 1] = 0;
    saveMovingChamberParams(chamberId, mParams);
    logMsg(LOG_DEBUG, logPrefixChamberData, '2', chamberId);

    prevMillisTTargetSave[chamberId - 1] = uptimeMillis;
    memoMinFreeRam(21);
  }
}

boolean movingChamberParamsSaved[CHAMBER_COUNT] = {false, false};
void setChamberParams(
    ChamberData& cd, int16_t gyleAgeHours, int16_t tTarget, int16_t tTargetNext, int16_t tMin, int16_t tMax, boolean hasHeater,
    uint8_t fridgeMinOnTimeMins, uint8_t fridgeMinOffTimeMins, uint8_t fridgeSwitchOnLagMins, float Kp, float Ki, float Kd, char mode) {
  logMsg(LOG_DEBUG, logPrefixChamberData, '0', cd.chamberId, tTarget /* int16_t */, mode /* char */);

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
    logMsg(LOG_DEBUG, logPrefixChamberData, '1', cd.chamberId);
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
        logMsg(LOG_DEBUG, logPrefixChamberData, 'p', chamberId);
      } else {
        logMsg(LOG_ERROR, logPrefixChamberData, 'P', chamberId);
      }
    }
    {
      MovingChamberParams eepromMParams = {};
      getEepromMovingChamberParams(chamberId, eepromMParams);
      if (eepromMParams.checksum == generateChecksum(eepromMParams)) {
        memcpy(&mParams, &eepromMParams, sizeof(MovingChamberParams));
        logMsg(LOG_DEBUG, logPrefixChamberData, 't', chamberId);
      } else {
        logMsg(LOG_ERROR, logPrefixChamberData, 'T', chamberId);
      }
    }
  }
}

// End
