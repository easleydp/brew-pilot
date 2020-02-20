#define CHAMBER_ITERATION_TIME_MILLIS 60000

static const char* logPrefixChamberControl = "CC";
static const char* logPrefixPid = "PID";


#define F_ON 1
#define F_OFF 0
void forceFridge(ChamberData& cd, byte setting) {
  if (setting == F_ON) {
    //TODO: FRIDGE PIN ON
    if (!cd.fridgeOn) {
      logMsg(LOG_INFO, logPrefixChamberControl, 'F', cd.params.chamberId, cd.fridgeStateChangeMins/* uint8_t */);
      cd.fridgeStateChangeMins = 0;
      cd.fridgeOn = true;
    }
  } else {
    //TODO: FRIDGE PIN OFF
    if (cd.fridgeOn) {
      logMsg(LOG_INFO, logPrefixChamberControl, 'f', cd.params.chamberId, cd.fridgeStateChangeMins/* uint8_t */);
      cd.fridgeStateChangeMins = 0;
      cd.fridgeOn = false;
    }
  }
  memoMinFreeRam(1);
}

/** Activates/deactivates the fridge as requested IF this won't unduly stress the compressor. */
void fridge(ChamberData& cd, byte setting) {
  if (setting == F_ON) {
    // If we think it's already on, set it on again just to be sure.
    // Otherwise (we think it's off), check it's been off for long enough.
    if (cd.fridgeOn) {
      forceFridge(cd, F_ON);
    } else { // We think fridge is off. Check it's been off for long enough.
      if (cd.fridgeStateChangeMins >= FRIDGE_MIN_OFF_TIME_MINS) {
        forceFridge(cd, F_ON);
      }
    }
  } else {  // Request is to turn OFF
    // If we think it's already off, set it off again just to be sure.
    // Otherwise (we think it's on), check it's been on for long enough.
    if (!cd.fridgeOn) {
      forceFridge(cd, F_OFF);
    } else { // We think fridge is on. Check it's been on for long enough.
      if (cd.fridgeStateChangeMins >= FRIDGE_MIN_ON_TIME_MINS) {
        forceFridge(cd, F_OFF);
      }
    }
  }
}
uint8_t prevLevel = 255;
void heater(ChamberData& cd, uint8_t level) {
  // TODO - always set the heat level (just in case) but only log if (i) level appears to have changed and (ii) hit an extreme
  if (prevLevel != level) {
    if (level == 0  ||  level == 100)
      logMsg(LOG_INFO, logPrefixChamberControl, 'H', cd.params.chamberId, level/* uint8_t */);
    prevLevel = level;
  }
  memoMinFreeRam(2);
}

void controlChamber(ChamberData& cd) {
  ChamberParams& params = cd.params;
  uint8_t chamberId = params.chamberId;

  // Defaults, in case we should fall through the following logic without making
  // a deliberate decision.
  boolean fForce = false;
  byte fSetting = F_OFF;
  byte hSetting = 0;
  boolean heatPidWise = false;
  // See if a local mode has been set (via the panel switch), fall-back to the value set by the RPi
  const char mode = cd.mode != MODE_UNSET ? cd.mode : params.mode;

  int16_t tError = cd.tTarget - cd.tBeer; // +ve - beer too cool; -ve beer too warm

  if (mode == MODE_NONE) {
    // Just monitoring
  } else if (mode == MODE_HEAT) {
    fSetting = F_OFF;
    hSetting = cd.tBeer < params.tMax ? 75/* 75% rather than full on */ : 0;
  } else if (mode == MODE_COOL) {
    hSetting = 0;
    fSetting = cd.tBeer > params.tMin ? F_ON : F_OFF;
  } else if (mode == MODE_AUTO  ||  mode == MODE_HOLD) {
    if (tError == 0) {  // Zero error (rare!)
      hSetting = 0;
      if (cd.fridgeOn) {
        // Fridge is on. Keep it on only if hot outside and beer temp is rising.
        if (tExternal > cd.tTarget  &&  cd.tBeerLastDelta > 0) {
          // ... but NOT if temp profile is about to turn upwards in next hour
          if (cd.tTargetNext <= cd.tTarget)
            fSetting = F_ON;
        }
      }
    } else if (tError > 0) {  // beer too cool, needs heating
      int16_t tExternalBoost = tExternal - cd.tBeer; // +ve - in our favour
      if (cd.exothermic) {
        // Assuming our tBeer sensor is near the outside of the fermentation vessel, exothermic means the 
        // beer will actually be warmer internally than our tBeer reading suggests. Compensate for this
        // by adding a couple of degrees to tExternalBoost, i.e. so we're less eager to apply heating.
        tExternalBoost += 20;
      }
      if (tExternalBoost > 0) {  // Outside temp is in our favour
        // Needs heating but we can leave it to tExternal
        hSetting = 0;
        fSetting = F_OFF;  // Under the circumstances maybe this should be forced?
      } else {  // Outside temp is NOT in our favour
        fSetting = F_OFF;
        heatPidWise = true;
      }
    } else { // (tError < 0)  beer too warm
      if (tExternal < cd.tBeer) {  // Outside temp is in our favour
        hSetting = 0;
        // Beer needs cooling but we can leave it to tExternal
        // UNLESS exothermic, in which case we'll need to actively cool.
        if (cd.exothermic) {
          fSetting = F_ON;
        } else {
          fSetting = F_OFF;
        }
      } else {  // Outside temp is NOT in our favour
        if (!cd.fridgeOn) {
          fSetting = F_ON;
        } else {
          // Fridge is already on. Leave it on unless we're approaching the target temp (i.e. within 1 degree) in 
          // which case switch off (min on time permitting, of course).
          fSetting = tError > -10 ? F_OFF : F_ON;
        }
      }
    }
  }

  // Note: we maintain the PID state variables - integral & priorError - even when
  // not PID heating in case we commence PID heating next time round.
  cd.integral += tError;
  logMsg(LOG_DEBUG, logPrefixPid, '~', chamberId, tError/* int16 */, cd.integral/* float */, cd.priorError/* float */);
  if (heatPidWise) {
    float pidOutput = params.Kp*tError + params.Ki*cd.integral + params.Kd*(tError - cd.priorError);
    // PID output range check
    if (pidOutput < 0.0) { // we've screwed-up somehow
      logMsg(LOG_ERROR, logPrefixPid, '!', chamberId, pidOutput/* float */);
      hSetting = 0;
    } else if (pidOutput >= 100.0) {
      logMsg(LOG_WARN, logPrefixPid, '+', chamberId, pidOutput/* float */);
      hSetting = 100;
    } else {
      logMsg(LOG_DEBUG, logPrefixPid, '-', chamberId, pidOutput/* float */);
      hSetting = pidOutput;
    }
  }
  if (cd.priorError - tError != 0) {
    cd.tBeerLastDelta = (cd.priorError - tError) * 10; // *10 is so we don't decay to zero too soon
  } else {
    // Decay
    logMsg(LOG_DEBUG, logPrefixPid, 'd', chamberId, cd.tBeerLastDelta/* int8 */);
    if (cd.tBeerLastDelta > 0)
      cd.tBeerLastDelta -= 1;
    else if (cd.tBeerLastDelta < 0)
      cd.tBeerLastDelta += 1;
  }
  cd.priorError = tError;  

  /* Do it! */

  // fForce may have been set above but ensure it's set to true if we're about to turn the heater on.
  if (hSetting > 0) {
    fForce = true;
    fSetting = F_OFF; // This should be in sympathy already, but just in case.
  }

  if (fForce)
    forceFridge(cd, fSetting);
  else
    fridge(cd, fSetting);

  heater(cd, hSetting);
}

void chambersMinuteTick() {
  for (byte i = 0; i < CHAMBER_COUNT; i++) {
    ChamberData& cd = chamberDataArray[i];
    if (cd.fridgeStateChangeMins < 255)
      cd.fridgeStateChangeMins += 1;
  }
  memoMinFreeRam(3);
}

uint32_t prevMillisChamberControl = CHAMBER_ITERATION_TIME_MILLIS; // rather than 0, so we do an initial control interation immediately after startup.
void controlChambers() {
  if (TIME_UP(prevMillisChamberControl, uptimeMillis, CHAMBER_ITERATION_TIME_MILLIS)) {
    prevMillisChamberControl = uptimeMillis;

    readTemperatures();
    tExternal = getTExternalX10();
    tPi = getTPiX10();
    for (byte i = 0; i < CHAMBER_COUNT; i++) {
      ChamberData& cd = chamberDataArray[i];
      const uint8_t chamberId = cd.params.chamberId;
      cd.tBeer = getTBeerX10(chamberId);
      cd.tChamber = getTChamberX10(chamberId);
      controlChamber(cd);
    }
  }
}
