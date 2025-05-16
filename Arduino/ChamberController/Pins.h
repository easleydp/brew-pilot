#ifndef PINS_H
#define PINS_H

/*
 * I/O pins
 */

// Input
#define PIN__ONE_WIRE_BUS 2  // Temperature sensor bus [WHITE]

// Output
#define PIN__CH1_FRIDGE 7  // Mechanical relay, ch1  [YELLOW]
#define PIN__CH2_FRIDGE 8  // Mechanical relay, ch2  [ORANGE]
#define PIN__CH1_HEATER 12  // SSR, ch1               [ORANGE/WHITE]

#endif  // PINS_H