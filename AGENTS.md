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
   Able to manage fermentation chambers autonomously if the management app crashes (simply
   maintaining the latest 'set temperature' for each chamber).
2. `./Java` - The management interface backend is a Java Spring Boot application targetting
   a RaspberryPi. Communicates with the Arduino over a serial interface, regularly collecting
   sensor readings and updating the latest 'set temperature' according to a
   user specified temperature profile (all on a per chamber basis).
3. `./frontend` - The management interface frontend is a React web app written in TypeScript.

There is also an `./nginx` folder, containing configuration files for the recommended web server.

## Code Style

- Follow the formatting rules implied by the component's `.prettierrc` file if
  there is one, otherwise adhere to the existing style.
- Use consistent, descriptive naming.
- Prefer small, focused components.
