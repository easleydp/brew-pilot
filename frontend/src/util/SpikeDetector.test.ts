import SpikeDetector from './SpikeDetector';

const mapMinutesToMillis = (times: number[]): number[] => {
  return times.map((t) => t * 60 * 1000);
};

const spikeDetector = new SpikeDetector({
  spikeDetectionThreshold: 2.0, // °C
  spikeReturnToPriorTolerance: 0.1, // °C
  readingsPeriod: 60 * 1000,
  spikeMaxPeriods: 2,
});

test('no spike', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 4, 5]),
    yData: [5.0, 5.1, 5.2, 5.0],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([]);
});

test('no spike (2)', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 3]),
    yData: [5.0, 5.1, 5.2],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([]);
});

test('no spike (3)', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2]),
    yData: [5.0, 5.1],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([]);
});

test('no spike (4)', () => {
  const series = {
    xData: mapMinutesToMillis([1]),
    yData: [5.0],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([]);
});

test('no spike (5)', () => {
  const series = {
    xData: mapMinutesToMillis([]),
    yData: [],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([]);
});

test('simple +ve spike', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 3]),
    yData: [5.0, 20.0, 5.1],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([[1]]);
});

test('+ve spike reverting with overshoot', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 3]),
    yData: [5.0, 20.0, 4.0],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([[1]]);
});

test('simple -ve spike', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 3]),
    yData: [5.0, -20.0, 5.1],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([[1]]);
});

test('-ve spike reverting with overshoot', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 3]),
    yData: [5.0, -20.0, 6.0],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([[1]]);
});

test('flat top spike', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 3, 4]),
    yData: [5.0, 20.0, 20.0, 5.1],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([[2, 1]]);
});

test('flat top non-spike, too wide', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 3, 4, 5]),
    yData: [5.0, 20.0, 20.0, 20.0, 5.1],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([]);
});

test('flat top non-spike, too wide (2)', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 4, 5]),
    yData: [5.0, 20.0, 20.0, 5.1],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([]);
});

test('multiple spikes', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 3, 4, 5]),
    yData: [5.0, 20.0, 4.9, -20.0, 5.1],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([[3], [1]]);
});

test('detect spike after backing up', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 3, 4, 5]),
    // The initial jump (to 20) turns out to be too wide but, after backing up the
    // cursor the start of that fat spike, a narrow spike (to 30) should be found.
    yData: [5.0, 20.0, 30.0, 20.0, 5.0],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([[2]]);
});

test('initial half spike not detected', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 3, 4, 5, 6]),
    yData: [20.0, 5.1, 5.2, 5.1, 5.1, 5.1],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([]);
});

test('final half spike not detected', () => {
  const series = {
    xData: mapMinutesToMillis([1, 2, 3]),
    yData: [5.1, 5.0, 20.0],
  };
  expect(spikeDetector.detectSpikes(series)).toEqual([]);
});
