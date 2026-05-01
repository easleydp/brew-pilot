/**
 * @file Common.h
 * @brief Basic utility functions and uptime tracking.
 */
#ifndef COMMON_H
#define COMMON_H

#include <Arduino.h>
#include <limits.h>
#include <stdarg.h>
#include <stdio.h>

// Gives the version of the Arduino environment being used. Referred to in some libs.
// Looks like Arduino IDE sets this but vscode Arduino extension does not, so fake release 1.6.5.
#ifndef ARDUINO
#define ARDUINO 10605
#endif

#define TIME_UP(prev, curr, interval) ((uint32_t)(curr - prev) >= interval)

extern uint32_t uptimeMillis;
extern uint32_t uptimeMins;

const char* strFromProgMem(const char* addr);

const char* itoa(int value);

/**
 * In the buffer referenced by `cmd`, finds the first comma starting from offset `startingOffset`,
 * sets the comma to null, and returns the offset of the following character.
 * Returns -1 if the end of the buffer is reached.
 */
int nullNextComma(char* cmd, int startingOffset);

// https://playground.arduino.cc/Code/AvailableMemory/
int freeRam();

extern int minFreeRam;
extern uint8_t minFreeRamLocation;

// Call this to record lowest minFreeRam from whereever you suspect stack may be deep.
// The minFreeRam & minFreeRamLocation are then available for reporting.
void memoMinFreeRam(uint8_t location);

void printComma();

#endif  // COMMON_H
