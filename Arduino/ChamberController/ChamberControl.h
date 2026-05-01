/**
 * @file ChamberControl.h
 * @brief PID control logic for fridge and heater.
 */
#ifndef CHAMBER_CONTROL_H
#define CHAMBER_CONTROL_H

#define CHAMBER_ITERATION_TIME_MILLIS 60000

#include "Common.h"
#include "Logging.h"
#include "Temperature.h"

#define ON 1
#define OFF 0

// When cooling the beer over a period of time we can't feather the control input as
// we can when heating so the achieved temperature profile is inevitably a sawtooth
// waveform. This waveform should be approximately centred on tTarget. The greater
// this value, the more the sawtooth is lifted.
#define COOLING_SAWTOOTH_MIDPOINT 3 /* 0.3 degrees (this value assumes fridgeMinOnTimeMins is of the order of 10 mins) */

// When tExternal is in our favour (for heating or cooling) by at least this much
// we may avoid actively heating/cooling.
#define T_EXTERNAL_BOOST_THRESHOLD 20 /* 2 degrees */

// To guard against see-sawing we only consider heating if the fridge has been off
// for at least this long. Likewise with cooling after heating.
#define ANTI_SEESAW_MARGIN_MINS 90

void forceFridge(ChamberData& cd, byte setting);
void setHeaterElement(ChamberData& cd, byte setting);

/** Activates/deactivates the fridge as requested IF this won't unduly stress the compressor. */
void fridge(ChamberData& cd, byte setting);
void heater(ChamberData& cd, uint8_t outputLevel);

void maintainHeaters();

void controlChamber(ChamberData& cd);

void chambersMinuteTick();
void chambersSecondTick();

void controlChambers();

#endif  // CHAMBER_CONTROL_H
