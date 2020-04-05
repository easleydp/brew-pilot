export default interface ITemperatureProfile {
  points: {
    hoursSinceStart: number;
    targetTemp: number;
  }[];
}
