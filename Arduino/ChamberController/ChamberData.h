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

#define FRIDGE_MIN_OFF_TIME_MINS 10 // TODO: swap for configurable ChamberParams.fridgeMinOffTimeMins
#define FRIDGE_MIN_ON_TIME_MINS 5   // TODO: swap for configurable ChamberParams.fridgeMinOnTimeMins

/**
 * Parameters that seldom change. 
 * We have defaults for these in the program code but aim to override from 
 * manager AND then save/restore the manager supplied values to/from EEPROM.
 * Persisted in EEPROM in case of restart with no Pi.
 */
typedef struct {
  byte chamberId;

  int16_t tMin;
  int16_t tMax;
  boolean hasHeater;
  //TODO: uint8_t fridgeMinOnTimeMins, fridgeMinOffTimeMins
  // Heater PID parameters
  float Kp;
  float Ki;
  float Kd;

  uint16_t checksum;
} ChamberParams; // 20 bytes

typedef struct {
  uint8_t chamberId;
  int16_t tTarget;
  uint16_t checksum;
} TTargetWithChecksum;

typedef struct {
  // Parameters that seldom change
  ChamberParams params;

  // These are updated more regularly from the RPi
  boolean exothermic;
  int16_t tTarget;  // We store tTarget in EEPROM on change but not more than once an hour
  int16_t tTargetNext;

  /*
   * Working data and readings
   */

  // Latest readings
  int16_t tBeer;
  int16_t tExternal;
  int16_t tChamber;
  int16_t tPi;
  uint8_t heaterOutput; // 0..100
  boolean fridgeOn;
  char mode;

  // PID state
  float priorError; // tTarget - tBeer, so -ve for cooling, +ve for heating
  float integral;

  uint8_t fridgeStateChangeMins;  // Tops out at 255 (code avoids wrapping). initChamberData() sets to 255
  int8_t tBeerLastDelta; // The last registered change in tBeer ((priorError-error)*10), with decay each time no change. Used to detect trend (+ve signifies rising / -ve falling).
} ChamberData;

ChamberData chamberDataArray[CHAMBER_COUNT];

/** Assumes that, whatever structure is supplied, the last two bytes is a checksum to be ignored. */
template< typename T > uint16_t generateChecksum(const T &t) {
  uint16_t crc = 7;
  const uint8_t *ptr = (const uint8_t*) &t;
  for (int i = 0; i < sizeof(T) - sizeof(uint16_t) /* don't include the checksum */; i++) {
    crc= _crc16_update(crc, ptr[i]);
  }
  return crc;
}

/*
 * EEPROM helpers.
 * Memory map: One TTargetWithChecksum per chamber, follwed by one ChamberParams per chamber.
 */
void getEepromTTargetWithChecksum(int chamberId, TTargetWithChecksum &ttwc) {
  int addr = (chamberId - 1) * sizeof(TTargetWithChecksum);
  EEPROM.get(addr, ttwc);
}
void putEepromTTargetWithChecksum(TTargetWithChecksum &ttwc) {
  int addr = (ttwc.chamberId - 1) * sizeof(TTargetWithChecksum);
  EEPROM.put(addr, ttwc);
}
void getEepromChamberParams(int chamberId, ChamberParams &params) {
  int base = CHAMBER_COUNT * sizeof(TTargetWithChecksum);
  int addr = base + (chamberId - 1) * sizeof(ChamberParams);
  EEPROM.get(addr, params);
}
void putEepromChamberParams(ChamberParams &params) {
  int base = CHAMBER_COUNT * sizeof(TTargetWithChecksum);
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

unsigned long saveTTargetInterval = 1000L * 60 * 60; // save tTarget every hour
unsigned long prevTTargetSaved[CHAMBER_COUNT];  // initialised in initChamberData()
boolean tTargetPersisted[CHAMBER_COUNT];  // initialised in initChamberData()
void updateChamberParams(ChamberData* cd, int tTarget, int tTargetNext, int tMin, int tMax, boolean hasHeater, float Kp, float Ki, float Kd) {
  byte chamberId = cd->params.chamberId;

  cd->tTarget = tTarget;
  cd->tTargetNext = tTargetNext;

  cd->params.tMin = tMin;
  cd->params.tMax = tMax;
  cd->params.hasHeater = hasHeater;
  cd->params.Kp = Kp;
  cd->params.Ki = Ki;
  cd->params.Kd = Kd;
  cd->params.checksum = generateChecksum(cd->params);

  putEepromChamberParams(cd->params);

  if (!tTargetPersisted[chamberId - 1]  ||  (uptimeMillis - prevTTargetSaved[chamberId - 1] >= saveTTargetInterval)) {
    prevTTargetSaved[chamberId - 1] = uptimeMillis;
    TTargetWithChecksum tTargetWithChecksum = { chamberId, tTarget, 0 };
    tTargetWithChecksum.checksum = generateChecksum(tTargetWithChecksum);
    putEepromTTargetWithChecksum(tTargetWithChecksum);
    tTargetPersisted[chamberId - 1] = true;
  }
}

/** Called from setup() */
void initChamberData() {
  for (byte i = 0; i < CHAMBER_COUNT; i++) {
    prevTTargetSaved[i] = 0;
    tTargetPersisted[i] = false;

    ChamberData& cd = chamberDataArray[i];
    memset(&cd, 0, sizeof(ChamberData));
    cd.fridgeStateChangeMins = 255;
    ChamberParams& params = cd.params;
    params.chamberId = i + 1;

    // Default params & tTarget, before we check EEPROM
    params.tMin = -10;
    params.tMax = 400;
    params.hasHeater = true;
    params.Kp = 2.0f;
    params.Ki = 0.01f;
    params.Kd = 20.0f;
    params.checksum = generateChecksum(params);
    cd.tTarget = cd.tTargetNext = 160;

    ChamberParams eepromParams = {};
    getEepromChamberParams(params.chamberId, eepromParams);
    if (eepromParams.chamberId == params.chamberId) {
      if (eepromParams.checksum == generateChecksum(eepromParams)) {
        memcpy(&params, &eepromParams, sizeof(ChamberParams));
      }
    }

    TTargetWithChecksum tTargetWithChecksum = {};
    getEepromTTargetWithChecksum(params.chamberId, tTargetWithChecksum);
    if (tTargetWithChecksum.chamberId == params.chamberId) {
      if (tTargetWithChecksum.checksum == generateChecksum(tTargetWithChecksum)) {
        cd.tTarget = cd.tTargetNext = tTargetWithChecksum.tTarget;
      }
    }
  }
}

// End
