import './GyleChart.scss';
import React, { useEffect, useRef, useCallback } from 'react';
import { useHistory, useLocation } from 'react-router-dom';
import ILocationState from '../api/ILocationState';
import { useAppState, Auth } from './state';
import { useParams } from 'react-router-dom';
import axios from 'axios';
import useInterval from '../api/useInterval';
import SpikeDetector from '../util/SpikeDetector';
import ITemperatureProfile from '../api/ITemperatureProfile';
import applyPatchRangeDefaultLeft from '../api/RangeDefaultLeftPatch.js';

import Highcharts, { Chart, Series } from 'highcharts/highstock';

const GyleChart = () => {
  const history = useHistory<ILocationState>();
  const location = useLocation<ILocationState>();
  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;

  const { chamberId } = useParams<{ chamberId: string }>();

  enum Mode {
    AUTO, // Aim for the target temp specified in the ChamberParameters, if any. Otherwise, operate as per `HOLD`.
    HOLD, // Aim to maintain tBeer as it was when this mode was engaged (reflected in tTarget).
    DISABLE_HEATER, // As AUTO but disable heater.
    DISABLE_FRIDGE, // As AUTO but disable fridge.
    MONITOR_ONLY, // No heating, no cooling, just monitoring.
  }

  interface IReadings {
    dt: number;
    tTarget: number | undefined;
    tBeer: number | undefined;
    tExternal: number | undefined;
    tChamber: number | undefined;
    tPi: number | undefined;
    heaterOutput: number | undefined;
    fridgeOn: boolean | undefined;
    mode: Mode | undefined;
  }
  interface IGyleDetails {
    readingsTimestampResolutionMillis: number;
    readingsPeriodMillis: number;
    chamberName: string;
    hasHeater: boolean;
    gyleId: number;
    gyleName: string;
    temperatureProfile: ITemperatureProfile;
    dtStarted: number;
    dtEnded: number | undefined;
    recentReadings: IReadings[];
    readingsLogs: string[];
  }

  // We need access to the private properties xData & yData since Series.points isn't 'live' while adding points.
  interface SeriesContinuous extends Series {
    xData: number[]; // Time axis - never null;
    yData: number[]; // Property axis - never null.
  }
  interface SeriesDiscontinuous extends Series {
    xData: number[]; // Time axis - never null;
    yData: (number | null)[]; // Property axis - can be null for discontinuous properties such as fridgeOn.
  }

  const chartRef = useRef<Chart | null>(null);
  const gyleDetailsRef = useRef<IGyleDetails | null>(null);
  const readingsPeriodMillisRef = useRef<number>(60 * 1000); // Starting default until we read from gyleDetails.readingsPeriodMillis
  const lastDtRef = useRef<number | null>(null);

  // Helper for when we know we should have GyleDetails
  const getGyleDetails = (): IGyleDetails => {
    const gyleDetails = gyleDetailsRef.current;
    if (!gyleDetails) {
      throw Error('Should have GyleDetails at this stage.');
    }
    return gyleDetails;
  };

  // Helper for when we know we should have a Chart
  const getCurrentChart = (): Chart => {
    const chart = chartRef.current;
    if (!chart) {
      throw Error('Should have a Chart at this stage.');
    }
    return chart;
  };

  // Called by useInterval
  const addLatestReadings = () => {
    getLatestReadings().then((latestReadings) => {
      if (latestReadings.length) {
        lastDtRef.current = latestReadings[latestReadings.length - 1].dt;
        addBunchOfReadings(latestReadings, false);
      }
    });
  };

  // Returns promise for retrieving latest readings
  const getLatestReadings = (): Promise<IReadings[]> => {
    const url = '/tempctrl/guest/chamber/' + chamberId + '/recent-readings';
    return new Promise((resolve, reject) => {
      axios
        .get(url, { params: { sinceDt: lastDtRef.current || 0 } })
        .then((response) => {
          return resolve(response.data);
        })
        .catch((error) => {
          console.debug(url + ' ERROR', error);
          const status = error?.response?.status;
          if (status === 403 || status === 401) {
            console.debug(`Redirecting to signin after ${status}`);
            history.push({ pathname: '/signin', state: { from: location.pathname } });
            dispatch({ type: 'LOGOUT' });
          }
          reject(error);
        });
    });
  };

  // BE generally returns ms since epoch / readingsTimestampResolutionMillis
  const restoreUtcMillisPrecision = useCallback((timestamp: number): number => {
    return timestamp * getGyleDetails().readingsTimestampResolutionMillis;
  }, []);

  /** Adds the supplied readings to the chart then redraws. */
  const addBunchOfReadings = useCallback(
    (readingsList: IReadings[], initialLoad: boolean) => {
      const len = readingsList ? readingsList.length : 0;
      if (len === 0) {
        return;
      }
      const dt0 = readingsList[0].dt,
        dtN = readingsList[len - 1].dt;
      console.log(`Adding ${len} reading record(s) ranging from dt ${dt0} to ${dtN}`);

      let prevReadings: IReadings | null = null;
      const chart = getCurrentChart();

      const tTargetSeries: SeriesContinuous = chart.get('tTarget') as SeriesContinuous,
        tBeerSeries: SeriesContinuous = chart.get('tBeer') as SeriesContinuous,
        tExternalSeries: SeriesContinuous = chart.get('tExternal') as SeriesContinuous,
        tChamberSeries: SeriesContinuous = chart.get('tChamber') as SeriesContinuous,
        fridgeSeries: SeriesDiscontinuous = chart.get('fridgeOn') as SeriesDiscontinuous,
        heaterSeries: SeriesDiscontinuous = chart.get('heaterOutput') as SeriesDiscontinuous;

      readingsList.forEach((readings, index) => {
        // Sanity check. Each successive readings record should occur later in time.
        if (prevReadings !== null && prevReadings.dt >= readings.dt) {
          // Used to throw here but it seems that, after a power outage, the first recentReadings record
          // can appear to be earlier than the last few records of the last ndjson file. (Haven't yet
          // worked out how this can happen; maybe a time server issue?). So now we log, skip and continue.
          console.warn(
            `Skipping out of order readings with dt ${readings.dt} (prev was ${prevReadings.dt})`
          );
          return; // skip
        }
        prevReadings = readings;

        const dt = restoreUtcMillisPrecision(readings.dt);

        // The readings record is sparse, meaning any of the properties may be absent
        // (undefined). A property being undefined signifies no change since the previous
        // reading.
        //
        // Note that the first record in a log file is never sparse. Such 'full' records are
        // NOT rationalised when log files are concatenated (not just here in the FE but also
        // when the BE consolidates smaller log files), therefore we need to be circumspect
        // on detecting a defined value.
        //
        // Backfilling readings: Log record consumer (FE) is aware of the sampling period
        // (P). On finding a record with a new value it should backfill a reading at (dt - P)
        // equal to the previous recorded value. Note, only backfill if dt - previous
        // reading's dt > P.

        // Sanity check: confirm the supplied dt is later than anything we already have.
        const oldestDt = (chart.series as SeriesContinuous[]).reduce<number>((dt, series) => {
          const len = series.xData.length;
          if (len) {
            const lastX = series.xData[len - 1];
            if (lastX > dt) {
              dt = lastX;
            }
          }
          return dt;
        }, 0);
        if (oldestDt >= dt) {
          throw Error(
            `Reading at ${dt} is not later than our the oldest reading we already have (${oldestDt}).`
          );
        }

        const { tTarget, tBeer, tExternal, tChamber, fridgeOn, heaterOutput } = readings;
        maybeAddTemperaturePoint(dt, tTarget, tTargetSeries);
        maybeAddTemperaturePoint(dt, tBeer, tBeerSeries);
        maybeAddTemperaturePoint(dt, tExternal, tExternalSeries);
        maybeAddTemperaturePoint(dt, tChamber, tChamberSeries);
        maybeAddFridgePoint(dt, fridgeOn, fridgeSeries);
        heaterSeries && maybeAddHeaterPoint(dt, heaterOutput, heaterSeries);
      });

      // Because of the sparse nature of the readings some series might need a final synthetic point adding
      // or they would appear to end prematurely, i.e. before the time of the last readings record.
      const dt = restoreUtcMillisPrecision(readingsList[readingsList.length - 1].dt);
      maybeBackfillAFinalPoint(dt, tTargetSeries);
      maybeBackfillAFinalPoint(dt, tBeerSeries);
      maybeBackfillAFinalPoint(dt, tExternalSeries);
      maybeBackfillAFinalPoint(dt, tChamberSeries);
      maybeBackfillAFinalPoint(dt, fridgeSeries);
      heaterSeries && maybeBackfillAFinalPoint(dt, heaterSeries);

      // TODO: Remove this debug code:
      debugCheckSeriesXOrder(tTargetSeries, 'tTargetSeries');
      debugCheckSeriesXOrder(tBeerSeries, 'tBeerSeries');
      debugCheckSeriesXOrder(tExternalSeries, 'tExternalSeries');
      debugCheckSeriesXOrder(tChamberSeries, 'tChamberSeries');
      debugCheckSeriesXOrder(fridgeSeries, 'fridgeSeries');
      heaterSeries && debugCheckSeriesXOrder(heaterSeries, 'heaterSeries');

      // Too expensive to repeat spike detection across the whole of each series for every minor update.
      if (initialLoad) {
        removeRogueTemperatureSpikes(tBeerSeries, 'tBeerSeries');
        removeRogueTemperatureSpikes(tExternalSeries, 'tExternalSeries');
        removeRogueTemperatureSpikes(tChamberSeries, 'tChamberSeries');

        // TODO: Remove this debug code:
        debugCheckSeriesXOrder(tBeerSeries, 'tBeerSeries');
        debugCheckSeriesXOrder(tExternalSeries, 'tExternalSeries');
        debugCheckSeriesXOrder(tChamberSeries, 'tChamberSeries');
      }

      chart.redraw();
    },
    [restoreUtcMillisPrecision]
  );

  // TODO: Remove this debug code:
  const debugCheckSeriesXOrder = (
    series: SeriesContinuous | SeriesDiscontinuous,
    seriesName: string
  ) => {
    let prevX: number = -1;
    let prevY: number | null = -1;
    series.xData.forEach((x, i) => {
      let y = series.yData[i];
      if (x < prevX) {
        console.error(
          `!!! ${seriesName}, i-1=${i - 1}:[${prevX / 30000}, ${y}], i=${i}:[${
            x / 30000
          }, ${prevY}]`
        );
      }
      prevX = x;
      prevY = y;
    });
  };

  const maybeAddTemperaturePoint = (
    dt: number,
    value: number | undefined,
    series: SeriesContinuous
  ) => {
    if (value !== undefined) {
      value = value / 10;
      const valuesLen = series.yData ? series.yData.length : 0;
      // Only add if it's a different value than previous.
      if (valuesLen === 0) {
        // First point
        series.addPoint([dt, value], false);
      } else {
        const lastX = series.xData[valuesLen - 1];
        const lastY = series.yData[valuesLen - 1];
        const readingsPeriodMillis = readingsPeriodMillisRef.current;
        if (lastY !== value) {
          const timeSinceLastPoint = dt - lastX;
          if (timeSinceLastPoint > readingsPeriodMillis) {
            // Backfill
            series.addPoint([dt - readingsPeriodMillis, lastY as number], false);
          }
          series.addPoint([dt, value], false);
        }
      }
    }
  };

  const maybeAddFridgePoint = (
    dt: number,
    fridgeOn: boolean | undefined,
    series: SeriesDiscontinuous
  ) => {
    // fridgeOn undefined signifies 'same as previous value'
    if (fridgeOn !== undefined) {
      const value = fridgeOn ? 10 : null;
      let valuesLen = series.yData ? series.yData.length : 0;
      // Only add if it's a different value than previous.
      if (valuesLen === 0) {
        // First point
        series.addPoint([dt, value] as any, false);
      } else {
        const lastX = series.xData[valuesLen - 1];
        const lastY = series.yData[valuesLen - 1];
        if (lastY !== value) {
          const timeSinceLastPoint = dt - (lastX as number);
          const readingsPeriodMillis = readingsPeriodMillisRef.current;
          if (value !== null) {
            // Fridge has transitioned from off to on.
            // Backfill a null and a 0 (if room) before the new value.
            if (timeSinceLastPoint > readingsPeriodMillis) {
              series.addPoint([dt - readingsPeriodMillis, null] as any, false);
              series.addPoint([dt - readingsPeriodMillis, 0], false);
            } else {
              // There isn't room to backfill a null and a 0.
              // Edge case: If the preceding yValue is null we need to remove it because we need to start rising
              // immediately for the current rising 'on' value. (The zero before the null should remain though.)
              if (series.yData[valuesLen - 1] === null) {
                series.removePoint(--valuesLen, false);
              }
            }
            series.addPoint([dt, value], false);
          } else {
            // value === null
            if (value !== null) throw Error('Logic error - expected value to be null: ' + value);
            // Fridge has transitioned from on to off.
            // Backfill the previous reading (if room) before adding a zero and the new value (null).
            if (timeSinceLastPoint > readingsPeriodMillis) {
              series.addPoint([dt - readingsPeriodMillis, lastY as number], false);
            }
            series.addPoint([dt, 0], false);
            series.addPoint([dt, null] as any, false);
          }
        }
      }
    }
  };

  const maybeAddHeaterPoint = (
    dt: number,
    heaterOutput: number | undefined,
    series: SeriesDiscontinuous
  ) => {
    // heaterOutput undefined signifies 'same as previous value'
    if (heaterOutput !== undefined) {
      const value = heaterOutput > 0 ? heaterOutput / 10 : null;
      let valuesLen = series.yData ? series.yData.length : 0;
      // Only add if it's a different value than previous.
      if (valuesLen === 0) {
        // First point
        series.addPoint([dt, value] as any, false);
      } else {
        if (valuesLen !== series.xData.length) throw Error('Program error');
        const lastX = series.xData[valuesLen - 1];
        const lastY = series.yData[valuesLen - 1];
        const readingsPeriodMillis = readingsPeriodMillisRef.current;
        if (lastY !== value) {
          const timeSinceLastPoint = dt - (lastX as number);
          if (lastY !== null && value !== null) {
            // Heater remains on, just with different value
            if (timeSinceLastPoint > readingsPeriodMillis) {
              // Backfill
              series.addPoint([dt - readingsPeriodMillis, lastY as number], false);
            }
            series.addPoint([dt, value], false);
          } else if (value !== null) {
            // Heater has transitioned from off to on.
            // Backfill a null and a 0 (if room) before the new value.
            if (timeSinceLastPoint > readingsPeriodMillis) {
              series.addPoint([dt - readingsPeriodMillis, null] as any, false);
              series.addPoint([dt - readingsPeriodMillis, 0], false);
            } else {
              // There isn't room to backfill a null and a 0.
              // Edge case: If the preceding yValue is null we need to remove it because we need to start rising
              // immediately for the current rising 'on' value. (The zero before the null should remain though.)
              if (series.yData[valuesLen - 1] === null) {
                series.removePoint(--valuesLen, false);
              }
            }
            series.addPoint([dt, value], false);
          } else {
            // value === null
            if (value !== null) throw Error('Logic error - expected value to be null: ' + value);
            // Heater has transitioned from on to off.
            // Backfill the previous reading (if room) before adding a zero and the new value (null).
            if (timeSinceLastPoint > readingsPeriodMillis) {
              series.addPoint([dt - readingsPeriodMillis, lastY as number], false);
            }
            series.addPoint([dt, 0], false);
            series.addPoint([dt, null] as any, false);
          }
        }
      }
    }
  };

  const maybeBackfillAFinalPoint = (dt: number, series: SeriesContinuous | SeriesDiscontinuous) => {
    const valuesLen = series.yData.length;
    const lastX = series.xData[valuesLen - 1];
    const lastY = series.yData[valuesLen - 1];
    if (lastX < dt && lastY !== null) {
      series.addPoint([dt, lastY], false);
    }
  };

  // Whiz through the temperature sensor series and remove any rogue spikes, i.e. where the temp
  // suddenly jumps by more than (say) 1°C before returning to (near to) the prior prevailing value. We've seen
  // spikes consisting of 4 consecutive rogue y readings, usually but not always the same rogue value.
  //
  // We abstract the spike detection logic into a (unit testable) helper class.
  const spikeDetector = new SpikeDetector({
    spikeDetectionThreshold: 1.0, // °C
    spikeReturnToPriorTolerance: 0.2, // °C
    readingsPeriod: readingsPeriodMillisRef.current,
    spikeMaxPeriods: 4,
  });
  const removeRogueTemperatureSpikes = (series: SeriesContinuous, seriesName: string) => {
    const spikes = spikeDetector.detectSpikes(series);
    // Note: detectSpikes returns indices in reverse order for our convenience when it comes to removing.
    if (spikes.length) {
      const pointCount = spikes.reduce<number>((pointCount, spike) => {
        pointCount += spike.length;
        return pointCount;
      }, 0);
      console.log(
        `Removing ${spikes.length} rogue sensor reading spike(s) (consisting ${pointCount} points) from ${seriesName}`
      );
      spikes.forEach((spike) => {
        //TODO: Delete:
        // if (seriesName === 'tExternalSeries') {
        //   const tempSummary = spike.reduce<string>((str, iPoint) => {
        //     str += ', ' + series.yData[iPoint];
        //     return str;
        //   }, '');
        //   console.log('tempSummary for spike:', tempSummary);
        // }
        spike.forEach((iPoint) => {
          series.removePoint(iPoint, false);
        });
      });
    }
  };

  // const utcMsFromDaysAndHours = function(days: number, hours: number) {
  //   return Date.UTC(1970, 0, days + 1, hours);
  // };

  useEffect(() => {
    console.info(
      Auth[isAuth],
      chamberId,
      '=================== GyleChart useEffect invoked ======================'
    );

    // For this chart we want the Highstock range selector to default to the right hand side
    // of the x-axis, i.e. to show latest points by default. (This is the stock behaviour
    // but we have to take care to undo the patch applied in FermentationProfile.tsx)
    applyPatchRangeDefaultLeft(Highcharts, false);

    // Returns promise for retrieving IGyleDetails
    const getLatestGyleDetails = (): Promise<IGyleDetails> => {
      const url = '/tempctrl/guest/chamber/' + chamberId + '/latest-gyle-details';
      return new Promise((resolve, reject) => {
        axios
          .get(url)
          .then((response) => {
            return resolve(response.data);
          })
          .catch((error) => {
            console.debug(url + ' ERROR', error);
            const status = error?.response?.status;
            if (status === 403 || status === 401) {
              console.debug(`Redirecting to signin after ${status}`);
              history.push({ pathname: '/signin', state: { from: location.pathname } });
              dispatch({ type: 'LOGOUT' });
            }
            reject(error);
          });
      });
    };

    const buildChart = (gyleDetails: IGyleDetails): Chart => {
      const chamberName = gyleDetails.chamberName;
      const hasHeater = gyleDetails.hasHeater;
      const dtStarted = gyleDetails.dtStarted;
      const isBeerFridge = !gyleDetails.hasHeater;
      const hourMs = 1000 * 60 * 60;
      const pad2 = (n: number) => {
        return n < 10 ? '0' + n : '' + n;
      };

      const formatTimeAsHtml = function (ms: number) {
        if (isBeerFridge) {
          const d = new Date(ms);
          // Want e.g. "14:59, 2 May 2020"
          const hrs = d.getHours(),
            mins = d.getMinutes();
          return `${pad2(hrs)}:${pad2(mins)}, ${d.getDate()} ${
            ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][
              d.getMonth()
            ]
          } ${d.getFullYear()}`;
        }
        const totalHours = Math.round((ms - dtStarted) / hourMs); // Round to nearest hour (i.e. what we'll snap to)
        const days = Math.floor(totalHours / 24) + 1;
        const hours = Math.floor(totalHours % 24);
        if (days === 1) {
          return `Hour&nbsp;${hours}`;
        }
        return `Day&nbsp;${days}, hour&nbsp;${hours}`;
      };

      const series = [
        {
          name: 'Target beer temp.',
          id: 'tTarget',
          type: 'line',
          dashStyle: 'ShortDot',
          color: '#777',
        } as Highcharts.SeriesLineOptions,
        {
          name: 'Beer temp.',
          id: 'tBeer',
          type: 'spline',
          color: 'rgba(247, 163, 92, 1.0)',
          showInNavigator: true,
        } as Highcharts.SeriesSplineOptions,
        {
          name: isBeerFridge ? 'Fridge temp.' : 'Chamber temp.',
          id: 'tChamber',
          selected: false,
          type: 'spline',
          color: 'rgba(131, 50, 168, 0.5)',
        } as Highcharts.SeriesSplineOptions,
        {
          name: 'Garage temp.', // TODO: Make "Garage" part configurable
          id: 'tExternal',
          selected: false,
          type: 'spline',
          color: 'rgba(0, 150, 0, 0.5)',
        } as Highcharts.SeriesSplineOptions,
        {
          name: 'Fridge on',
          id: 'fridgeOn',
          selected: false,
          type: 'area',
          color: 'rgba(113, 166, 210, 1.0)',
          fillOpacity: 0.3,
          lineWidth: 1,
          dataGrouping: { enabled: false },
          //showInNavigator: true,
        } as Highcharts.SeriesAreaOptions,
      ] as Array<Highcharts.SeriesOptionsType>;

      if (hasHeater) {
        series.push({
          name: 'Heater output',
          id: 'heaterOutput',
          selected: false,
          type: 'areaspline',
          color: 'rgba(255, 90, 150, 0.75)',
          fillOpacity: 0.3,
          lineWidth: 1,
          dataGrouping: { enabled: false },
          //showInNavigator: true,
        } as Highcharts.SeriesAreasplineOptions);
      }

      return Highcharts.stockChart(
        {
          credits: {
            enabled: false,
          },

          time: {
            useUTC: false,
          },

          rangeSelector: {
            allButtonsEnabled: true,
            buttons: [
              {
                type: 'day',
                text: 'day',
              },
              {
                type: 'day',
                text: '3 days',
                count: 3,
              },
              {
                type: 'week',
                text: 'week',
              },
              {
                type: 'week',
                text: '2 weeks',
                count: 2,
              },
              {
                type: 'all',
                text: 'max.',
              },
            ],
            buttonTheme: {
              width: 60,
            },
            selected: 1,

            inputEnabled: false,
          },

          title: {
            text: chamberName + ' temperature log' + (gyleDetails.dtEnded ? ' (inactive)' : ''),
          },

          tooltip: {
            useHTML: true,
            formatter: function () {
              const points = this.points;
              if (!points) {
                return false;
              }
              const tips: Array<string | null> = points.map((p) => {
                const series = p.series;
                const seriesId = series && series.options.id;
                if (!seriesId) {
                  return null;
                }

                let friendlyValue;
                if (seriesId === 'fridgeOn') {
                  friendlyValue = `Fridge <strong>${p.y ? 'ON' : 'OFF'}</strong>`;
                } else if (seriesId === 'heaterOutput') {
                  if (p.y) {
                    friendlyValue = `Heater output <strong>${Math.round(p.y * 10)}%</strong>`;
                  } else {
                    friendlyValue = `Heater <strong>OFF</strong>`;
                  }
                } else {
                  friendlyValue = `${series.name} <strong>${
                    Math.round(p.y * 10) / 10
                  }&deg;C</strong>`;
                }

                // TODO - analyse how this used to work in the js version. In this ts version there
                // are two issues (the first being easy to deal with): (1) this.points may be
                // undefined; (2) Point has no `index` property.
                //   const i = this.points[0].point.index;
                //   if (i === 0) {
                //     return `Started at ${friendlyTemp}`;
                //   }

                return friendlyValue;
              });

              // First array item is the 'header'
              tips.unshift(formatTimeAsHtml(this.x));
              return tips;
            },
          },

          plotOptions: {
            series: {
              // Not obvious to users that legend labels are clickable so also show checkboxes
              showCheckbox: true,
              selected: true, // Default. Can be overridden per series.
              events: {
                checkboxClick: function (event) {
                  const series = event.item;
                  if (event.checked) {
                    series.show();
                  } else {
                    series.hide();
                  }
                  return false;
                },
                // User may click on legend labels or the checkbox. Amazingly we have to take care of keeping things in sync.
                hide: function (event) {
                  const series = event.target as Series | null;
                  if (series !== null) {
                    series.select(false);
                    return false;
                  }
                },
                show: function (event) {
                  const series = event.target as Series | null;
                  if (series !== null) {
                    series.select(true);
                    return false;
                  }
                },
              },
            },
          },

          // Order dictates the order they appear in the legend. NOTE: If the order is changed, search
          // for `NOTE: These must be kept in same order as the series definitions` and change in sympathy.
          series: series,

          legend: {
            enabled: true,
            align: 'right',
            backgroundColor: '#FFFFE7',
            borderColor: '#999',
            borderWidth: 1,
            layout: 'vertical',
            verticalAlign: 'top',
            y: 100,
            shadow: true,
          },

          xAxis: {
            ordinal: false,
            // labels: {
            //   formatter: function() {
            //     return formatTimeAsHtml(this.value);
            //   },
            //   useHTML: true,
            // },
          },
          yAxis: {
            title: {
              useHTML: true,
              text: 'Temperature (&deg;C)',
            },
          },

          chart: {
            // type: 'spline',
            renderTo: 'gyle-chart-ct',
          },

          navigator: {
            series: {
              type: 'spline',
            },
          },
        },
        (chart) => {
          // It seems we have to take care of initially hiding any series with `selected: false`
          chart.series.filter((s) => !s.selected).forEach((s) => s.hide());
        }
      );
    };

    const getOrBuildChart = (gyleDetails: IGyleDetails): Chart => {
      if (!chartRef.current) {
        chartRef.current = buildChart(gyleDetails);
      }
      return chartRef.current;
    };

    // Returns promise for the readings from the specified log file.
    const getLogFileReadings = (logName: string): Promise<IReadings[]> => {
      return new Promise((resolve, reject) => {
        const gyleDetails = gyleDetailsRef.current;
        if (!gyleDetails) {
          reject(Error('gyleDetails should not be null at this stage'));
          return;
        }
        axios
          .get(
            // Note: We'll configure nginx to handle `/tempctrl/data` itself rather then pass to app server.
            // Only reason for `/tempctrl` prefix is to make this work with React's proxy server. In this
            // case the app server DOES handle the data requests.
            `/tempctrl/data/chambers/${chamberId}/gyles/${gyleDetails.gyleId}/logs/${logName}.ndjson`
          )
          .then((response) => {
            const ndjson: string = response.data;
            const readings: IReadings[] = ndjson
              .split('\n')
              .filter((json) => json)
              .map((json) => JSON.parse(json));
            resolve(readings);
          })
          .catch((error) => {
            // Persevere rather than reject (e.g. due to an odd file not being found), otherwise nothing
            // will be displayed.
            console.warn(`Error while retrieving ${logName}.ndjson:`, error);
            resolve([]);
          });
      });
    };

    // Returns promise for aggregating readings from all the logs specified in the supplied
    // gyleDetails along with the latestReadings from the supplied gyleDetails.
    const getAggregatedReadings = (gyleDetails: IGyleDetails): Promise<IReadings[]> => {
      return new Promise((resolve, reject) => {
        let aggregatedReadings: IReadings[] = [];
        let readingsLogs = gyleDetails.readingsLogs;
        const isBeerFridge = !gyleDetails.hasHeater;
        if (isBeerFridge) {
          const genN = 4;
          // Limit extent of beer fridge chart by fetching no more than one `genN` file (and no files of
          // any later generations). (With genN=4 this gives us about one month's worth of readings.)
          const getGen = (logName: string) => {
            return parseInt(logName.substring(0, logName.indexOf('-')), 10);
          };
          readingsLogs = readingsLogs.filter((logName, i) => {
            const gen = getGen(logName);
            return gen <= genN && (gen !== genN || getGen(readingsLogs[i + 1]) !== genN);
          });
        }
        Promise.all(readingsLogs.map((logName) => getLogFileReadings(logName))).then(
          (logFilesReadings) => {
            logFilesReadings.forEach((logFileReadings) => {
              aggregatedReadings.push(...logFileReadings);
            });
            aggregatedReadings.push(...gyleDetails.recentReadings);

            // Drop any readings earlier than the gyle's start time.
            // Or for beer fridge, drop any readings more than N weeks old.
            const earliestReading = aggregatedReadings[0];
            const dtStarted = gyleDetails.dtStarted;
            if (earliestReading) {
              const totalCount = aggregatedReadings.length;
              if (isBeerFridge) {
                const nWeeks = 4;
                const nWeeksAgo = Date.now() - nWeeks * 7 * 24 * 60 * 60 * 1000;
                aggregatedReadings = aggregatedReadings.filter((reading) => {
                  return restoreUtcMillisPrecision(reading.dt) >= nWeeksAgo;
                });
                const droppedCount = totalCount - aggregatedReadings.length;
                droppedCount &&
                  console.debug(`Dropped ${droppedCount} readings more than ${nWeeks} weeks old`);
              } else if (restoreUtcMillisPrecision(earliestReading.dt) < dtStarted) {
                aggregatedReadings = aggregatedReadings.filter((reading) => {
                  return restoreUtcMillisPrecision(reading.dt) >= dtStarted;
                });
                const droppedCount = totalCount - aggregatedReadings.length;
                console.debug(
                  `Dropped ${droppedCount} readings earlier than the gyle's start time`
                );
              }
            }

            resolve(aggregatedReadings);
          }
        );
      });
    };

    if (isAuth === Auth.NotLoggedIn) {
      // The user is definitely not logged in. Go straight to signin form.
      console.debug('user definitely not logged in');
      history.push({ pathname: '/signin', state: { from: location.pathname } });
    } else if (isAuth === Auth.Unknown) {
      // The user has hit F5? Go to the home page where we can check if they're logged in.
      console.debug('user has hit F5?');
      history.push({ pathname: '/', state: { from: location.pathname } });
    } else {
      getLatestGyleDetails()
        .then((gyleDetails) => {
          gyleDetailsRef.current = gyleDetails;
          readingsPeriodMillisRef.current = gyleDetails.readingsPeriodMillis;
          lastDtRef.current = gyleDetails.recentReadings.length
            ? gyleDetails.recentReadings[gyleDetails.recentReadings.length - 1].dt
            : null;
          const chart = getOrBuildChart(gyleDetails);
          chart.showLoading();
          return getAggregatedReadings(gyleDetails);
        })
        .then((aggregatedReadings) => {
          // TODO: Remove this debug output after Jun 2021. Hoping by then to have got to the bottom of this:
          //   Error: Reading at 1610248020000 is not later than our the oldest reading we already have (1611499230000).
          //   .
          //   1610248020000 = 53674934 found in first record of oldest ndson file (3-53674934-53680932)
          //   1611499230000 = 53716641 found in 'recent readings'
          //   .
          //   Found nowhere else.
          //   Over 14 days between these two! They look legit, just wrong order, as if the aggregated readings hadn't been pushed last.
          //   But why the 53716641 recent readings when this wasn't first or last recent readings rec?
          console.debug('aggregatedReadings', aggregatedReadings);

          addBunchOfReadings(aggregatedReadings, true);
          getCurrentChart().hideLoading();
        });
    }
  }, [dispatch, history, chamberId, isAuth, addBunchOfReadings]);

  useInterval(() => {
    if (!getGyleDetails().dtEnded) {
      addLatestReadings();
    }
  }, readingsPeriodMillisRef.current);

  return (
    <div className="gyle-chart">
      <div id={'gyle-chart-ct'}></div>
    </div>
  );
};

export default GyleChart;
