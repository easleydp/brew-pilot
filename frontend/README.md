# Frontend

React and TypeScript management interface for BrewPilot.

Use the Node version specified in [`.nvmrc`](./.nvmrc), then install dependencies:

```sh
npm ci
```

## Available scripts

- `npm start` runs the Vite development server at <http://localhost:5173>. Requests to
  `/tempctrl` are proxied to the backend at `http://localhost:8081`.
- `npm test` runs the test suite once; `npm run test:watch` starts it in watch mode.
- `npm run build` type-checks and creates the production build in `build/`.
