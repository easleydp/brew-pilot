/**
 * @file ChamberController.h
 * @brief Main entry point logic.
 */
#ifndef CHAMBER_CONTROLLER_H
#define CHAMBER_CONTROLLER_H

#include "ChamberControl.h"
#include "Common.h"
#include "Led.h"
#include "Logging.h"
#include "MessageHandlingDomain.h"
#include "MessageHandlingGen.h"
#include "Pins.h"
#include "Temperature.h"
#include "TimeKeeping.h"

void ChamberControllerSetup();
void ChamberControllerLoop();

#endif  // CHAMBER_CONTROLLER_H
