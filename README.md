# brew-pilot

RaspberryPi and Arduino temperature controller for fermentation chambers and beer fridges.

The front end currently assumes one fermenter and one beer fridge, though most of the internals are flexible in this regard, allowing any number of _chambers_ to be configured. Each chamber may have any number of associated _gyles_ (all but the latest being historical). For a given chamber, the latest gyle is considered 'active' if it has a start time <= now AND either no end time or an end time > now.

Each chamber is assumed to have refrigeration but a heater is optional. A chamber without a heater is assumed to be a _beer fridge_.

## Key components

- Frontend is a React SPA.
- Backend is a Java Spring Boot app.
- The Arduino microcontroller code is C++.

In addition the RPi should be equipped with a Linux OS, a Java VM and a webserver such as nginx.

Note there is no _database_. Rather, the last few minutes worth of readings are held in memory by the Java app before being dumped to disk (as a series of ndjson files). On request, the Java app simply provides the frontend with the names of the ndjson files (alongside any _latest readings_ from memory); the frontend then requests the data files directly (from the web server, without the Java app being further involved). Note: the ndjson files are aggregated once in a while so the frontend should never have to request too many.

The only RPi model to have been proven is the "Pi 4 Model B", though "Pi 3 Model B" should be sufficient.<br>
Arduino model is UNO.

## Rationale for Arduino

The reason for having an Arduino in addition to the RPi is robustness; the Arduino is relatively simple and therefore less likely to break. If the RPi goes down for any reason the Arduino will continue controlling the chambers according to the last set of parameters received from the RPi. The Arduino caches the last set of parameters received in EEPROM. So, if for some reason only the Arduino were to come back up after a power cut, it would still be able to maintain the last setpoint temperature.

It's also convenient that the Arduino, having 5v IO pins, can drive commonly available relays directly.

## Misc hardware

### Relay recommendations

- SSDs for heaters, since these are cycled frequently.
- Mechanical for fridges, since the inductive load can be a challenge for SSDs.

### Temperature probes

These should be DS18B20 (the waterproof variety where the business end is housed in a short stainless steel tube). Despite the fact that this sensor's interface is described as 'one-wire' (that is, one shared signal wire in addition to a common ground wire) it's much better to use three wires, especially so for a long cable run.

Probes used:

- All the chambers are assumed to be co-located, so a common probe is used to measure the external temperature (e.g. ambient temperature in your garage).
- Each chamber uses two probes - one for the ambient temperature insider the chamber and a second for the beer. For a beer fridge this is perhaps overkill (and is something that should be made configurable); I wrap one beer bottle in a wine cooler sleeve and push the _beer_ probe between bottle and sleeve. The _chamber_ probe should just be dangled somewhere away from the beer (and obviously away from the heater and the internal sides of the fridge). For a fermenter without a thermowell it's sufficient to just strap the probe to the side of the vessel, under a wodge of insulation such as bubble wrap.
- Finally, a probe is dedicated to measuring the temperature inside the project box. Again, this is perhaps overkill (something that should be configurable).

## Demo

A live BrewPilot installation may be visible here: https://brewpilot.ml/ (username: `guest`; password: `BeerIsGood!`)
