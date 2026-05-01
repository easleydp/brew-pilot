Code organisation:

- Separation of Concerns: Each module has a .h file for declarations (structs, constants, function prototypes) and a .cpp file for implementation (function definitions, global variable definitions).
- Global Variables: Declared as extern in the headers and defined in the corresponding .cpp files.
- Templates: Function templates (like logMsg and generateChecksum) are in the header files, as is required for C++ compilation in this context.
- Header Guards: All header files use #ifndef guards to prevent multiple inclusions.
- Structure:
  - Common.h/cpp: Basic utility functions and uptime tracking.
  - Led.h/cpp: LED control logic.
  - Logging.h/cpp: Circular buffer logging system.
  - ChamberData.h/cpp: EEPROM management and chamber state data.
  - Temperature.h/cpp: Dallas 1-Wire sensor management.
  - TimeKeeping.h/cpp: Millis-based tick system for minutes and seconds.
  - ChamberControl.h/cpp: PID control logic for fridge and heater.
  - MessageHandlingGen.h/cpp: General serial communication helpers.
  - MessageHandlingDomain.h/cpp: Specific command dispatching logic.
  - ChamberController.h/cpp: Main entry point logic called by the .ino file.

This structure makes the codebase more maintainable, improves compilation efficiency, and adheres to standard C++ and Arduino development patterns.
