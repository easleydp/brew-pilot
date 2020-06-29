import ITemperatureProfile from './ITemperatureProfile';
import { Mode } from './Mode';
export default interface IGyle {
  name: string;
  temperatureProfile?: ITemperatureProfile;
  dtStarted?: number;
  dtEnded?: number;
  mode: Mode;
}
