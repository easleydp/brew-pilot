#define CHAMBER_ITERATION_TIME_MILLIS 60000

#define ON 1
#define OFF 0

// When tExternal is in our favour (for heating or cooling) by at least this much
// we may avoid actively heating/cooling.
#define T_EXTERNAL_BOOST_THRESHOLD 20 /* 2 degrees */

static const char* logPrefixChamberControl = "CC";
static const char* logPrefixPid = "PID";

void forceFridge(ChamberData& cd, byte setting) {
  if (setting == ON) {
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

void setHeaterElement(ChamberData& cd, byte setting) {
  if (setting == ON) {
    //TODO: HEATER PIN ON
    if (!cd.heaterElementOn) {
      cd.heaterElementStateChangeSecs = 0;
      cd.heaterElementOn = true;
    }
  } else {
    //TODO: HEATER PIN OFF
    if (cd.heaterElementOn) {
      cd.heaterElementStateChangeSecs = 0;
      cd.heaterElementOn = false;
    }
  }
  memoMinFreeRam(6);
}

/** Activates/deactivates the fridge as requested IF this won't unduly stress the compressor. */
void fridge(ChamberData& cd, byte setting) {
  if (setting == ON) {
    // If we think it's already on, set it on again just to be sure.
    // Otherwise (we think it's off), check it's been off for long enough.
    if (cd.fridgeOn) {
      forceFridge(cd, ON);
    } else { // We think fridge is off. Check it's been off for long enough.
      if (cd.fridgeStateChangeMins >= FRIDGE_MIN_OFF_TIME_MINS) {
        forceFridge(cd, ON);
      }
    }
  } else {  // Request is to turn OFF
    // If we think it's already off, set it off again just to be sure.
    // Otherwise (we think it's on), check it's been on for long enough.
    if (!cd.fridgeOn) {
      forceFridge(cd, OFF);
    } else { // We think fridge is on. Check it's been on for long enough.
      if (cd.fridgeStateChangeMins >= FRIDGE_MIN_ON_TIME_MINS) {
        forceFridge(cd, OFF);
      }
    }
  }
}
void heater(ChamberData& cd, uint8_t outputLevel) {
  // We always set the heat level (just in case) but only log if (i) level appears to have changed and (ii) hit an extreme.
  if (outputLevel == 0  ||  outputLevel == 100)
    if (cd.heaterOutput != outputLevel)
      logMsg(LOG_INFO, logPrefixChamberControl, 'H', cd.params.chamberId, outputLevel/* uint8_t */);

  cd.heaterOutput = outputLevel; // The setting will take effect courtesy of maintainHeaters()

  memoMinFreeRam(2);
}


// Called as frequently as possible (but possibly as infrequently as once a second or so given how long controlChambers() can take).
// Given that the main loop sometimes takes a second or so to complete and given our heaterLevel has 100 steps, let's use a period
// of 100 seconds. (If the main control loop occasionally takes longer than one second to call us again, no big deal.) So,
// heaterLevel of 1 will give 1 sec ON followed by 99 secs OFF; heaterLevel of 99 will give 99 secs ON followed by 1 sec OFF; etc.
void maintainHeaters() {
  for (byte i = 0; i < CHAMBER_COUNT; i++) {
    ChamberData& cd = chamberDataArray[i];
    if (cd.params.hasHeater) {
      uint8_t heaterOutput = cd.heaterOutput;
      if (heaterOutput == 0) {          // Ensure OFF
        setHeaterElement(cd, OFF);
      } else if (heaterOutput == 100) { // Ensure ON
        setHeaterElement(cd, ON);
      } else if (cd.heaterElementOn) {  // Currently ON. If it's been ON long enough now, turn OFF.
        if (cd.heaterElementStateChangeSecs >= heaterOutput)
          setHeaterElement(cd, OFF);
      } else {                          // Currently OFF. If it's been OFF long enough now, turn ON.
        if (cd.heaterElementStateChangeSecs >= (100 - heaterOutput))
          setHeaterElement(cd, ON);
      }
    }
  }
}

void controlChamber(ChamberData& cd) {
  ChamberParams& params = cd.params;
  uint8_t chamberId = params.chamberId;

  // Defaults, in case we should fall through the following logic without making
  // a deliberate decision.
  boolean fForce = false;
  byte fSetting = OFF;
  byte hSetting = 0;
  boolean heatPidWise = false;
  // See if a local mode has been set (via the panel switch), fall-back to the value set by the RPi
  const char mode = cd.mode != MODE_UNSET ? cd.mode : params.mode;

  int16_t tError = cd.tTarget - cd.tBeer; // +ve - beer too cool; -ve beer too warm

  // +ve - in our favour for heating the beer; -ve - in our favour for cooling the beer
  int16_t tExternalBoost = tExternal - cd.tBeer;

  if (mode == MODE_NONE) {
    // Just monitoring
  } else if (mode == MODE_HEAT) {
    hSetting = cd.tBeer < params.tMax ? 75/* 75% rather than full on */ : 0;
  } else if (mode == MODE_COOL) {
    fSetting = cd.tBeer > params.tMin ? ON : OFF;
  } else if (mode == MODE_AUTO  ||  mode == MODE_HOLD) {
    if (tError == 0) {  // Zero error (rare!)
      if (cd.fridgeOn) {
        // Fridge is on. Keep it on only if hot outside and beer temp is rising.
        if (tExternal > cd.tTarget  &&  cd.tBeerLastDelta > 0) {
          // ... but NOT if temp profile is about to turn upwards in next hour
          if (cd.tTargetNext <= cd.tTarget)
            fSetting = ON;
        }
      }
    } else if (tError > 0) {  // beer too cool, needs heating
      if (cd.exothermic) {
        // Assuming our tBeer sensor is near the outside of the fermentation vessel, exothermic means the
        // beer will actually be warmer internally than our tBeer reading suggests. Compensate for this
        // by adding a couple of degrees to tExternalBoost, i.e. so we're less eager to apply heating.
        tExternalBoost += 20;
      }
      if ((tExternalBoost - T_EXTERNAL_BOOST_THRESHOLD) > tError) {  // Outside temp is markedly in our favour
        // Needs heating but we can leave it to tExternal
      } else {  // Outside temp is not sufficiently in our favour
        heatPidWise = true;
      }
    } else { // (tError < 0)  beer too warm, needs cooling
      if ((tExternalBoost + T_EXTERNAL_BOOST_THRESHOLD) < tError) {  // Outside temp is markedly in our favour
        // Beer needs cooling but we can leave it to tExternal
        // UNLESS exothermic, in which case we'll need to actively cool.
        if (cd.exothermic) {
          fSetting = ON;
        }
      } else {  // Outside temp is not sufficiently in our favour
        if (!cd.fridgeOn) {
          fSetting = ON;
        } else {
          // Fridge is already on. Leave it on unless we're approaching the target temp (i.e. within 1 degree) in
          // which case switch off (min on time permitting, of course).
          fSetting = tError > -10 ? OFF : ON;
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
    fSetting = OFF; // This should be in sympathy already, but just in case.
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

void chambersSecondTick() {
  for (byte i = 0; i < CHAMBER_COUNT; i++) {
    ChamberData& cd = chamberDataArray[i];
    if (cd.params.hasHeater && cd.heaterElementStateChangeSecs < 255)
      cd.heaterElementStateChangeSecs += 1;
  }
  memoMinFreeRam(4);
}

uint32_t prevMillisChamberControl = CHAMBER_ITERATION_TIME_MILLIS; // rather than 0, so we do an initial control interation immediately after startup.
void controlChambers() {
  if (TIME_UP(prevMillisChamberControl, uptimeMillis, CHAMBER_ITERATION_TIME_MILLIS)) {
    prevMillisChamberControl = uptimeMillis;

    uint32_t t = millis();
    if (readTemperatures()) {  // Seems to take about 120ms per sensor (~720ms for 6 sensors)
      logMsg(LOG_DEBUG, logPrefixChamberControl, 'j', 1, ((uint32_t) millis() - t)/* uint32_t */);
      readTExternal();
      readTPi();
      for (byte i = 0; i < CHAMBER_COUNT; i++) {
        ChamberData& cd = chamberDataArray[i];
        readTBeer(cd);
        readTChamber(cd);
        controlChamber(cd);
      }
      logMsg(LOG_DEBUG, logPrefixChamberControl, 'k', 1, ((uint32_t) millis() - t)/* uint32_t */);  // ~1100ms for 6 sensors
    }
  }
  maintainHeaters();
}
