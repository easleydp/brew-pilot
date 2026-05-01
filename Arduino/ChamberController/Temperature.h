/**
 * @file Temperature.h
 * @brief Dallas 1-Wire sensor management.
 */
#ifndef TEMPERATURE_H
#define TEMPERATURE_H

#include <DallasTemperature.h>
#include <OneWire.h>
#include <limits.h>

#include "ChamberData.h"
#include "Logging.h"
#include "Pins.h"

// Only used for test. Otherwise we read temperatures whenever requested.
#define TEMP_READINGS_MILLIS 10000

// Set this to your number of sensors, e.g. (tChamber + tBeer) * 2 chambers + tExternal + tProjectBox = 6
#define SENSOR_COUNT 6

extern DallasTemperature dallas;

typedef struct {
  // Abbreviated address. See shortenAddress()'s comment.
  uint16_t shortAddress;

  // Actual hardware index, assigned by the Dallas library
  uint8_t dallasIndex;

  // Error * 100. After being divided by 100 this value will be ADDED to the reading from the device.
  int8_t error;  // int8_t accommodates error range -1.28..+1.27

  int16_t prevReading;  // Used to apply a degree of averaging (noise smoothing)
} Sensor;

extern Sensor sensorData[SENSOR_COUNT];

// Our indexes 0..<SENSOR_COUNT-1> (not to be confused with Sensor.dallasIndex).
#define CH1_T_BEER 0
#define CH1_T_CHAMBER 1
#define CH2_T_BEER 2
#define CH2_T_CHAMBER 3
#define T_EXTERNAL 4
#define T_PROJECT_BOX 5

void initSensorData(uint8_t ourIndex, uint16_t shortAddress, int8_t error);

// Each sensor has a 64 bit address. For such a relatively small number of sensors as ours 16 bits is sufficient to discriminate.
uint16_t shortenAddress(const DeviceAddress& fullAddress);

Sensor* findSensorByAddress(const DeviceAddress& fullAddress);

extern uint8_t badSensorCount;

// This must be called once each period before reading the individual temperatures (using getTemperature()).
// Retuns true if all ok.
boolean readTemperatures();

void initTemperatureSensors();

// Retrieves latest reading for the specified sensor, converts to int x10,
// and applies a degree of averaging w.r.t. previous readings.
int16_t getTemperature(uint8_t sensorIndex);

void readTBeer(ChamberData& cd);
void readTChamber(ChamberData& cd);
void readTExternal();
void readTProjectBox();

#endif  // TEMPERATURE_H
