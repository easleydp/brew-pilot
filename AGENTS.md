# brew-pilot

Refer to `README.md` (the introductory paragraphs and the "Key components" section is
sufficient) for a description of the project purpose and technical overview.

## General Instructions

- Use British English throughout (technical documentation, code comments, and UI text).
- Keep changes **focused and minimal**:

  - Do not make unrelated changes when implementing a feature.
  - Do not remove existing comments unless they are incorrect or obsolete.
  - Refactoring is permitted only when it directly supports the feature being implemented.

- Prefer clarity over cleverness. Write code that is easy to understand and maintain.

## Components

The project consists of the following components, each in a specific top-level folder:

1. `./Arduino` - Arduino microcontroller code written in C++.
   Responsible for real time temperature control.
   Able to manage fermentation chambers autonomously if the Java app crashes (simply
   maintaining the last 'set temperature').
2. `./Java` - Backend for the management interface. A Java Spring Boot application running on
   a RaspberryPi. Communicates with the Arduino over a serial interface, regularly collecting
   sensor readings and updating the latest 'set temperature' (for each chamber) according to a
   user specified temperature profile.
3. `./Frontend` - Frontend is a React web app written in TypeScript.

There is also an `./nginx` folder, containing configuration files for the recommended web server.

## Code Style

- Always follow the formatting rules implied by the component's `.prettierrc` file if
  there is one, otherwise adhere to the existing style.
- Use consistent, descriptive naming.
- Prefer small, focused components.
