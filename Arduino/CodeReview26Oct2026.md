# Arduino code review — 26 October 2026

## Scope

Static review of the Arduino application in `ChamberController/`, with particular
attention to temperature sensing, heater/fridge control, EEPROM-backed
configuration, and serial command handling. Vendored libraries were not reviewed.
No code was changed and no firmware build or hardware test was performed.

## Findings

### Severe

1. **A disconnected temperature probe leaves automatic control using its last reading indefinitely**

   [`ChamberController/Temperature.cpp`](./ChamberController/Temperature.cpp#L73) treats readings below -50 °C as sensor errors, logs a warning, and returns `prevReading`. It never marks that individual probe unhealthy or limits how long the last reading may be used. A disconnected DS18B20 can therefore leave the controller using a stale beer temperature while the PID and relay decisions continue in [`ChamberControl.cpp`](./ChamberController/ChamberControl.cpp#L109) and [`controlChambers`](./ChamberController/ChamberControl.cpp#L311). The bus device count can remain unchanged when a probe disconnects, so the count check does not necessarily signal this fault. Depending on the last reading, the controller may continue heating or cooling without valid feedback.

   **Recommended direction:** track validity and age per probe; on a sustained fault, enter an explicit safe output state and report the affected sensor rather than treating an old value as current.

2. **Disabling a configured heater does not guarantee that its relay is switched off**

   [`maintainHeaters`](./ChamberController/ChamberControl.cpp#L89) only writes the heater pin while `cd.params.hasHeater` is true. If the setting changes from true to false while the element is energised, subsequent calls skip the output update, leaving the active-low relay pin in its previous state. The control path still calls `heater()` for that chamber, but that only changes the requested output level; it does not switch the physical pin off. This can leave a heater energised after its configuration says it is absent or disabled.

   **Recommended direction:** explicitly switch the element off when a chamber has no heater, and make the hardware output state follow that fail-safe state.

### Moderate

3. **A same-count sensor identity mismatch can be reported as healthy and map readings to the wrong probe**

   [`readTemperatures`](./ChamberController/Temperature.cpp#L35) only updates `badSensorCount` when the discovered device count differs from `SENSOR_COUNT`; on a clean start, a matching count leaves it at zero. During address mapping, [`initTemperatureSensors`](./ChamberController/Temperature.cpp#L59) returns early if a configured address is not found, without setting a fault. Unmatched `Sensor.dallasIndex` values remain at their default index (zero), and the main loop retries initialisation only when `badSensorCount` is non-zero ([`ChamberController.cpp`](./ChamberController/ChamberController.cpp#L27)). If a probe is replaced or an unexpected device is present while the total count stays six, one or more control inputs can silently read the first device and the system can indicate normal sensor status.

   **Recommended direction:** validate each configured address and the return value of `getAddress`; treat any mapping failure as a sensor fault and inhibit automatic control until mapping succeeds.

4. **Malformed `setChParams` commands can produce out-of-bounds string pointers**

   [`handleSetChamberParams`](./ChamberController/MessageHandlingDomain.cpp#L12) repeatedly assigns the result of `nullNextComma` to `j` and then to `i` without checking for `-1`. If a required comma is missing, the next `atoi`/`atol`/`atof` call receives `&cmd[-1]` (or another invalid offset). This can read outside the command buffer and may corrupt parameter values or disrupt the controller. The command is persisted to EEPROM after parsing if a chamber ID happens to resolve.

   **Recommended direction:** parse into validated fields using a bounded parser; reject incomplete, malformed, or trailing data before changing RAM or EEPROM state.

5. **Unvalidated compressor timing values can make the fridge impossible to switch off**

   [`handleSetChamberParams`](./ChamberController/MessageHandlingDomain.cpp#L43) converts timing fields directly to `uint8_t`, and [`setChamberParams`](./ChamberController/ChamberData.cpp#L70) stores them without range or combination checks. In [`fridge`](./ChamberController/ChamberControl.cpp#L49), the minimum-on threshold is the sum of `fridgeMinOnTimeMins` and `fridgeSwitchOnLagMins`. For example, values of 200 and 100 produce a threshold of 300 minutes, but `fridgeLastToggleMins` saturates at 255 ([`ChamberControl.cpp`](./ChamberController/ChamberControl.cpp#L288)); the threshold can therefore never be reached and the fridge will not be switched off by this logic.

   **Recommended direction:** validate each field and the combined threshold against the timer's representable range before accepting or persisting parameters.

6. **Both chambers' heater control targets the chamber-one relay**

   [`setHeaterElement`](./ChamberController/ChamberControl.cpp#L29) always writes `PIN__CH1_HEATER`, although it is passed a `ChamberData&`. [`maintainHeaters`](./ChamberController/ChamberControl.cpp#L89) iterates all chambers and checks a per-chamber `hasHeater` flag. If chamber two is configured with a heater, its control will operate the chamber-one pin; if both chambers have heaters, their outputs can override one another. This conflicts with the per-chamber heater configuration and risks controlling the wrong load.

   **Recommended direction:** either support and map a distinct heater output per chamber, or reject/explicitly constrain configurations that enable a heater outside the supported hardware arrangement.

### Minor

7. **Relay output initialisation briefly drives active-low outputs low**

   [`initLowLevelTriggerRelayPin`](./ChamberController/ChamberController.cpp#L7) calls `pinMode(pin, OUTPUT)` before writing `HIGH`. On AVR boards the output latch is initially low, so changing the pin to output can briefly assert the active-low relay before it is switched off. The interval is short and may not actuate a mechanical relay, but it contradicts the stated goal of avoiding relay blips and can matter for faster loads.

   **Recommended direction:** set the output latch to the inactive level before enabling output mode.

## Summary

- Severe: 2
- Moderate: 4
- Minor: 1
