import ITemperatureProfile from './ITemperatureProfile';

export default interface IGyle {
  name: string;
  temperatureProfile?: ITemperatureProfile;
  dtStarted?: number;
  dtEnded?: number;
}
