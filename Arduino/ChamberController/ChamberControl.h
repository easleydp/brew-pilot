#define CHAMBER_ITERATION_TIME_MILLIS 60000

#define ON 1
#define OFF 0

// When cooling the beer over a period of time we can't feather the control input as
// we can when heating so the achieved temperature profile is inevitably a sawtooth
// waveform. This waveform should be approximately centred on tTarget. The greater
// this value, the more the sawtooth is lifted.
#define COOLING_SAWTOOTH_MIDPOINT 2 /* 0.2 degrees (this value assumes fridgeMinOnTimeMins is of the order of 10 mins) */

// When tExternal is in our favour (for heating or cooling) by at least this much
// we may avoid actively heating/cooling.
#define T_EXTERNAL_BOOST_THRESHOLD 20 /* 2 degrees */

// To guard against see-sawing between heating & cooling we only consider heating
// if the fridge has been off for at least this long.
#define ANTI_SEESAW_MARGIN_MINS 120

static const char* logPrefixChamberControl = "CC";
static const char* logPrefixPid = "PID";

void forceFridge(ChamberData& cd, byte setting) {
  uint8_t pin = cd.chamberId == 1 ? PIN__CH1_FRIDGE : PIN__CH2_FRIDGE;
  if (setting == ON) {
    // FRIDGE ON
    digitalWrite(pin, LOW);
    if (!cd.fridgeOn) {
      logMsg(LOG_INFO, logPrefixChamberControl, 'F', cd.chamberId, cd.fridgeStateChangeMins/* uint8_t */);
      cd.fridgeStateChangeMins = 0;
      cd.fridgeOn = true;
    }
  } else {
    // FRIDGE OFF
    digitalWrite(pin, HIGH);
    if (cd.fridgeOn) {
      logMsg(LOG_INFO, logPrefixChamberControl, 'f', cd.chamberId, cd.fridgeStateChangeMins/* uint8_t */);
      cd.fridgeStateChangeMins = 0;
      cd.fridgeOn = false;
    }
  }
  memoMinFreeRam(1);
}

void setHeaterElement(ChamberData& cd, byte setting) {
  if (setting == ON) {
    // HEATER ON
    digitalWrite(PIN__CH1_HEATER, LOW);
    if (!cd.heaterElementOn) {
      cd.heaterElementStateChangeSecs = 0;
      cd.heaterElementOn = true;
    }
  } else {
    // HEATER OFF
    digitalWrite(PIN__CH1_HEATER, HIGH);
    if (cd.heaterElementOn) {
      cd.heaterElementStateChangeSecs = 0;
      cd.heaterElementOn = false;
    }
  }
  memoMinFreeRam(6);
}

/** Activates/deactivates the fridge as requested IF this won't unduly stress the compressor. */
void fridge(ChamberData& cd, byte setting) {
  ChamberParams& params = cd.params;
  if (setting == ON) {
    // If we think it's already on, set it on again just to be sure.
    // Otherwise (we think it's off), check it's been off for long enough.
    if (cd.fridgeOn) {
      forceFridge(cd, ON);
    } else { // We think fridge is off. Check it's been off for long enough.
      if (cd.fridgeStateChangeMins >= params.fridgeMinOffTimeMins) {
        forceFridge(cd, ON);
      }
    }
  } else {  // Request is to turn OFF
    // If we think it's already off, set it off again just to be sure.
    // Otherwise (we think it's on), check it's been on for long enough.
    if (!cd.fridgeOn) {
      forceFridge(cd, OFF);
    } else { // We think fridge is on. Check it's been on for long enough.
      if (cd.fridgeStateChangeMins >= (params.fridgeMinOnTimeMins + params.fridgeSwitchOnLagMins)) {
        forceFridge(cd, OFF);
      }
    }
  }
}
void heater(ChamberData& cd, uint8_t outputLevel) {
  // We always set the heat level (just in case) but only log if (i) level appears to have changed and (ii) hit an extreme.
  if (outputLevel == 0  ||  outputLevel == 100)
    if (cd.heaterOutput != outputLevel)
      logMsg(LOG_INFO, logPrefixChamberControl, 'H', cd.chamberId, outputLevel/* uint8_t */);

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
  uint8_t chamberId = cd.chamberId;

  // Defaults, in case we should fall through the following logic without making
  // a deliberate decision.
  boolean fForce = false;
  byte fSetting = OFF;
  byte hSetting = 0;
  boolean heatPidWise = false;
  const char mode = cd.params.mode;
  const int16_t tTarget = cd.mParams.tTarget;
  const int16_t tTargetNext = cd.mParams.tTargetNext;

  int16_t tError = tTarget - cd.tBeer; // +ve - beer too cool; -ve beer too warm

  // First temperature readings can be way out. Paper over this.
  const boolean justStarted = uptimeMins < 2;
  if (justStarted) {
    tError = 0;
    tExternal = tTarget;
  }

  // Assume exothermic if not beer fridge and gyle age is between 12h and 4 days.
  // Note: gyleAgeHours is -1 for beer fridge.
  const boolean exothermic = 12 < cd.mParams.gyleAgeHours && cd.mParams.gyleAgeHours < (4 * 24);

  // +ve - in our favour for heating the beer; -ve - in our favour for cooling the beer
  int16_t tExternalBoost = tExternal - cd.tBeer;

  if (tError == 0) {  // Zero error (rare!)
    if (cd.fridgeOn) {
      // Fridge is on. Keep it on only if hot outside and beer temp is rising.
      if (tExternal > tTarget  &&  cd.tBeerLastDelta > 0) {
        // ... but NOT if temp profile is about to turn upwards in next hour
        if (tTargetNext <= tTarget)
          fSetting = ON;
      }
    } else {
      // Continue to 'heatPidWise' if tExternal < target. In this condition we want the
      // integral component to maintain the target temperature with a steady heater output.
      if (tExternal < tTarget)
        heatPidWise = true;
    }
  } else if (tError > 0) {  // beer too cool, needs heating
    if (exothermic) {
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
    const int16_t tErrorAdjustedForSawtooth = tError + COOLING_SAWTOOTH_MIDPOINT; // or maybe not
    logMsg(LOG_DEBUG, logPrefixPid, 'X', chamberId, tErrorAdjustedForSawtooth, tExternalBoost);
    if (tErrorAdjustedForSawtooth < 0) {
      if ((tExternalBoost + T_EXTERNAL_BOOST_THRESHOLD) < tErrorAdjustedForSawtooth) {  // Outside temp is markedly in our favour
        // Beer needs cooling but we can leave it to tExternal
        // UNLESS exothermic OR we're ramping down, in which case we should actively cool.
        if (exothermic || tTargetNext < tTarget) {
          fSetting = ON;
        }
      } else {  // Outside temp is not sufficiently in our favour
        fSetting = ON;
      }
      if (cd.fridgeOn  &&  fSetting == ON) {
        // Cooling is set to continue.
        // If, however, chamber temp has more than crossed below target and beer temp is
        // approaching target, then - on the assumption the cooling has some momentum -
        // switch off early (min on time permitting, of course).
        if (tTarget - cd.tChamber > 3  &&  tErrorAdjustedForSawtooth > -3) {
            fSetting = OFF;
        }
      }
    }
  }

  // Note: we maintain the PID state variables - integral & priorError - even when
  // not PID heating in case we commence PID heating next time round.

  // To avoid integral wind-up, we constrain as follows: If the integral contribution is too large, reject the adjustment.
  float latestIntegral = cd.mParams.integral + tError;
  float integralContrib = params.Ki * latestIntegral;
  if (abs(integralContrib) > 50)
    logMsg(LOG_DEBUG, logPrefixPid, 'W', chamberId, integralContrib/* float */);
  else
    cd.mParams.integral = latestIntegral;

  logMsg(LOG_DEBUG, logPrefixPid, '~', chamberId, tError/* int16 */, cd.mParams.integral/* float */, cd.priorError/* float */);
  if (heatPidWise) {
    // Tuning insight - see what each PID factor is contributing:
    logMsg(LOG_DEBUG, logPrefixPid, 'C'/* PID output Components (3 floats) */,
      chamberId, params.Kp*tError, params.Ki*cd.mParams.integral, params.Kd*(tError - cd.priorError));
    float pidOutput = params.Kp*tError + params.Ki*cd.mParams.integral + params.Kd*(tError - cd.priorError);
    // PID output range check
    if (pidOutput < 0.0) { // Surprising that heatPidWise is true but PID output is -ve. Can happen though, most commonly due to integral being -ve.
      logMsg(LOG_WARN, logPrefixPid, '!', chamberId, pidOutput/* float */);
      hSetting = 0;
    } else if (pidOutput > 100.0) {
      // This isn't unusual if there's no heater since the beer may get significantly cooler than the target.
      logMsg(params.hasHeater ? LOG_WARN : LOG_DEBUG, logPrefixPid, '+', chamberId, pidOutput/* float */);
      hSetting = 100;
    } else {
      logMsg(LOG_DEBUG, logPrefixPid, '-', chamberId, pidOutput/* float */);
      hSetting = round(pidOutput);
    }
  }

  if (hSetting > 0) {
    // To help avoid the possibility of see-sawing between heating & cooling, don't even consider
    // heating if fridge has been on recently (or is on now).
    if (cd.fridgeOn || cd.fridgeStateChangeMins < ANTI_SEESAW_MARGIN_MINS) {
      // Heating countermanded
      logMsg(LOG_DEBUG, logPrefixChamberControl, 'C', chamberId, cd.fridgeStateChangeMins/* uint8_t */, hSetting/* byte */);
      hSetting = 0;
    }
  }

  // Detect tBeer change/trend, with decay each time no change.
  if (cd.priorError - tError != 0) {
    cd.tBeerLastDelta = (cd.priorError - tError) * 10; // *10 is so we don't decay to zero too soon
  } else {
    // No change in the error since last time. Decay the last recorded change in error.
    logMsg(LOG_DEBUG, logPrefixPid, 'd', chamberId, cd.tBeerLastDelta/* int8 */);
    if (cd.tBeerLastDelta > 0)
      cd.tBeerLastDelta -= 1;
    else if (cd.tBeerLastDelta < 0)
      cd.tBeerLastDelta += 1;
  }

  cd.priorError = tError;

  /*** Check any vetoes ***/

  // fForce may have been set above but ensure it's set to true if we're about to turn the heater on.
  if (hSetting > 0
      // `mode` vetoing fridge?
      ||  mode == MODE_MONITOR_ONLY  ||  mode == MODE_DISABLE_FRIDGE
      ||  justStarted) {
    fForce = true;
    fSetting = OFF; // This should be in sympathy already, but just in case.
  }

  // `mode` vetoing heater?
  if (mode == MODE_MONITOR_ONLY  ||  mode == MODE_DISABLE_HEATER  ||  justStarted) {
    hSetting = 0;
  }

  /*** Apply ***/

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
      cd.fridgeStateChangeMins++;

    // gyleAgeHours also gets set from the RPi. We update it here just in case we're
    // offline. If we're not offline it'll get reset from RPi soon enough - no biggy.
    if (uptimeMins % 60 == 0)
      if (cd.mParams.gyleAgeHours != -1) // Not beer fridge
        cd.mParams.gyleAgeHours++;
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
    readTemperatures();  // Seems to take about 120ms per sensor (~720ms for 6 sensors)
    logMsg(LOG_DEBUG, logPrefixChamberControl, 'j', 1, ((uint32_t) millis() - t)/* uint32_t */);
    readTExternal();
    readTProjectBox();
    for (byte i = 0; i < CHAMBER_COUNT; i++) {
      ChamberData& cd = chamberDataArray[i];
      readTBeer(cd);
      readTChamber(cd);
      controlChamber(cd);
    }
    logMsg(LOG_DEBUG, logPrefixChamberControl, 'k', 1, ((uint32_t) millis() - t)/* uint32_t */);  // ~1100ms for 6 sensors
  }
  maintainHeaters();
}
