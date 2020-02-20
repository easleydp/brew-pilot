import './GyleChart.scss';
import React, { useEffect } from 'react';
import { useHistory } from 'react-router-dom';
import { useAppState, Auth } from './state';
import { useParams } from 'react-router-dom';
import axios from 'axios';

import Highcharts, { Chart, Series, SeriesOptions } from 'highcharts/highstock';

const GyleChart = () => {
  const history = useHistory();
  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;

  const { chamberId } = useParams();

  enum Mode {
    AUTO, // Aim for the target temp specified in the ChamberParameters, if any. Otherwise, operate as per `HOLD`.
    HOLD, // Aim to maintain tBeer as it was when this mode was engaged (reflected in tTarget).
    COOL, // Force cool (while < tMin). No heating.
    HEAT, // Force heat (while < tMax). No cooling.
    NONE, // No heating, no cooling, just monitoring.
  }

  interface ITemperatureProfile {
    points: {
      hoursSinceStart: number;
      targetTemp: number;
    }[];
  }
  interface IReadings {
    dt: number;
    tTarget: number | undefined;
    tBeer: number | undefined;
    tExternal: number | undefined;
    tChamber: number | undefined;
    tPi: number | undefined;
    heaterOutput: number | undefined;
    coolerOn: boolean | undefined;
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
    recentReadings: IReadings[];
    readingsLogs: string[];
  }

  useEffect(() => {
    console.info(
      Auth[isAuth],
      chamberId,
      '=================== GyleChart useEffect invoked ======================'
    );

    let gyleDetails: IGyleDetails;
    let readingsPeriodMillis: number;
    let readingsTimestampResolutionMillis: number;
    let lastDt: number | null;

    // Returns promise for retrieving IGyleDetails
    const getGyleDetails = (): Promise<IGyleDetails> => {
      const url = '/guest/chamber/' + chamberId + '/active-gyle-details';
      return new Promise((resolve, reject) => {
        axios
          .get(url)
          .then(response => {
            return resolve(response.data);
          })
          .catch(error => {
            console.debug(url + ' ERROR', error);
            const status = error.response && error.response.status;
            if (status === 403 || status === 401) {
              console.debug(status, 'Redirecting to login');
              dispatch({ type: 'LOGOUT' });
              history.push('/login', { from: '/' });
            }
            reject(error);
          });
      });
    };

    // Returns promise for retrieving latest readings
    const getLatestReadings = (): Promise<IReadings[]> => {
      const url = '/guest/chamber/' + chamberId + '/recent-readings';
      return new Promise((resolve, reject) => {
        axios
          .get(url, { params: { sinceDt: lastDt || 0 } })
          .then(response => {
            return resolve(response.data);
          })
          .catch(error => {
            console.debug(url + ' ERROR', error);
            const status = error.response && error.response.status;
            if (status === 403 || status === 401) {
              console.debug(status, 'Redirecting to login');
              dispatch({ type: 'LOGOUT' });
              history.push('/login', { from: '/' });
            }
            reject(error);
          });
      });
    };

    // Called by setInterval
    const addLatestReadings = () => {
      getLatestReadings().then(latestReadings => {
        if (latestReadings.length) {
          lastDt = latestReadings[latestReadings.length - 1].dt;
          addBunchOfReadings(latestReadings);
        }
      });
    };

    /** Adds the supplied readings to the chart then redraws. */
    const addBunchOfReadings = (readingsList: IReadings[]) => {
      if (!readingsList || readingsList.length === 0) {
        return;
      }

      let prevReadings: IReadings | null = null;
      readingsList.forEach((readings, index) => {
        // Sanity check. Each successive readings record should occur later in time.
        if (prevReadings !== null && prevReadings.dt >= readings.dt) {
          throw Error(
            `readingsList not ascending in time. Prev: ${prevReadings.dt}; Current: ${readings.dt}`
          );
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
        if (!chart.series) debugger;
        const oldestDt = (chart.series as SeriesPlus[]).reduce<number>((dt, series) => {
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

        const { tTarget, tBeer, tExternal, tChamber, coolerOn, heaterOutput } = readings;
        maybeAddTemperaturePoint(dt, tTarget, tTargetSeries);
        maybeAddTemperaturePoint(dt, tBeer, tBeerSeries);
        maybeAddTemperaturePoint(dt, tExternal, tExternalSeries);
        maybeAddTemperaturePoint(dt, tChamber, tChamberSeries);
        maybeAddFridgePoint(dt, coolerOn, fridgeSeries);
        heaterSeries && maybeAddHeaterPoint(dt, heaterOutput, heaterSeries);
      });

      const dt = restoreUtcMillisPrecision(readingsList[readingsList.length - 1].dt);
      maybeBackfillAFinalPoint(dt, tTargetSeries);
      maybeBackfillAFinalPoint(dt, tBeerSeries);
      maybeBackfillAFinalPoint(dt, tExternalSeries);
      maybeBackfillAFinalPoint(dt, tChamberSeries);
      maybeBackfillAFinalPoint(dt, fridgeSeries);
      heaterSeries && maybeBackfillAFinalPoint(dt, heaterSeries);

      chart.redraw();
    };

    const maybeAddTemperaturePoint = (
      dt: number,
      value: number | undefined,
      series: SeriesPlus
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

    const maybeAddFridgePoint = (dt: number, coolerOn: boolean | undefined, series: SeriesPlus) => {
      if (coolerOn !== undefined) {
        const value = coolerOn ? 10 : null;
        const valuesLen = series.yData ? series.yData.length : 0;
        // Only add if it's a different value than previous.
        if (valuesLen === 0) {
          // First point
          series.addPoint([dt, value] as any, false);
        } else {
          const lastX = series.xData[valuesLen - 1];
          const lastY = series.yData[valuesLen - 1];
          if (lastY !== value) {
            const timeSinceLastPoint = dt - (lastX as number);
            if (value !== null) {
              // Fridge has transitioned from off to on.
              // Backfill a null and a 0 (if room) before the new value.
              if (timeSinceLastPoint > readingsPeriodMillis) {
                series.addPoint([dt - readingsPeriodMillis, null] as any, false);
                series.addPoint([dt - readingsPeriodMillis, 0], false);
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
      series: SeriesPlus
    ) => {
      if (heaterOutput !== undefined) {
        const value = heaterOutput > 0 ? heaterOutput / 10 : null;
        // const value = heaterOutput > 0 ? Math.floor(Math.random() * 20) + 1 : null;
        // const value = heaterOutput > 0 ? 10 : null;
        const valuesLen = series.yData ? series.yData.length : 0;
        // Only add if it's a different value than previous.
        //                debugger;
        if (valuesLen === 0) {
          // First point
          series.addPoint([dt, value] as any, false);
        } else {
          if (valuesLen !== series.xData.length) throw Error('Program error');
          const lastX = series.xData[valuesLen - 1];
          const lastY = series.yData[valuesLen - 1];
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

    const maybeBackfillAFinalPoint = (dt: number, series: SeriesPlus) => {
      const valuesLen = series.yData.length;
      const lastX = series.xData[valuesLen - 1];
      const lastY = series.yData[valuesLen - 1];
      if (lastX < dt && lastY !== null) {
        series.addPoint([dt, lastY], false);
      }
    };

    let interval: NodeJS.Timeout;

    // BE generally returns ms since epoch / readingsTimestampResolutionMillis
    const restoreUtcMillisPrecision = (timestamp: number): number => {
      return timestamp * readingsTimestampResolutionMillis;
    };

    // const utcMsFromDaysAndHours = function(days: number, hours: number) {
    //   return Date.UTC(1970, 0, days + 1, hours);
    // };

    const buildChart = (chamberName: string, hasHeater: boolean): Chart => {
      const hourMs = 1000 * 60 * 60;

      const formatTimeAsHtml = function(ms: number) {
        const totalHours = Math.round(ms / hourMs); // Round to nearest hour (i.e. what we'll snap to)
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
          type: 'line',
          dashStyle: 'ShortDot',
          color: '#777',
        } as Highcharts.SeriesLineOptions,
        {
          name: 'Beer temp.',
          type: 'spline',
          color: 'rgba(247, 163, 92, 1.0)',
          showInNavigator: true,
        } as Highcharts.SeriesSplineOptions,
        {
          name: 'Chamber temp.',
          selected: false,
          type: 'spline',
          color: 'rgba(131, 50, 168, 0.5)',
        } as Highcharts.SeriesSplineOptions,
        {
          name: 'Outside temp.',
          selected: false,
          type: 'spline',
          color: 'rgba(0, 150, 0, 0.5)',
        } as Highcharts.SeriesSplineOptions,
        {
          name: 'Fridge on',
          selected: false,
          type: 'area',
          color: 'rgba(113, 166, 210, 1.0)',
          fillOpacity: 0.3,
          lineWidth: 1,
          //showInNavigator: true,
        } as Highcharts.SeriesAreaOptions,
      ] as Array<Highcharts.SeriesOptionsType>;

      if (hasHeater) {
        series.push({
          name: 'Heater output',
          selected: false,
          type: 'areaspline',
          color: 'rgba(255, 90, 150, 0.75)',
          fillOpacity: 0.3,
          lineWidth: 1,
          //showInNavigator: true,
        } as Highcharts.SeriesAreasplineOptions);
      }

      return Highcharts.stockChart(
        {
          credits: {
            enabled: false,
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
            text: chamberName + ' temperature log',
          },

          tooltip: {
            useHTML: true,
            formatter: function() {
              const friendlyTemp = `<strong>${this.y}&deg;C</strong>`;

              // TODO - analyse how this used to work in the js version. In this ts version there
              // are two issues (the first being easy to deal with): (1) this.points may be
              // undefined; (2) Point has no `index` property.
              //   const i = this.points[0].point.index;
              //   if (i === 0) {
              //     return `Started at ${friendlyTemp}`;
              //   }

              const friendlyTime = formatTimeAsHtml(this.x).toLowerCase();
              return `${friendlyTemp} at<br/>${friendlyTime}`;
              // return `${friendlyTemp} at<br/>${this.x / 30000}`;
            },
          },

          plotOptions: {
            series: {
              // Not obvious to users that legend labels are clickable so also show checkboxes
              showCheckbox: true,
              selected: true, // Default. Can be overridden per series.
              events: {
                checkboxClick: function(event) {
                  const series = event.item;
                  if (event.checked) {
                    series.show();
                  } else {
                    series.hide();
                  }
                  return false;
                },
                // User may click on legend labels or the checkbox. Amazingly we have to take care of keeping things in sync.
                hide: function(event) {
                  const series = event.target as Series | null;
                  if (series !== null) {
                    series.select(false);
                    return false;
                  }
                },
                show: function(event) {
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
        chart => {
          // It seems we have to take care of initially hiding any series with `selected: false`
          chart.series.filter(s => !s.selected).forEach(s => s.hide());
        }
      );
    };

    // Returns promise for the readings from the specified log file.
    const getLogFileReadings = (logName: string): Promise<IReadings[]> => {
      return new Promise((resolve, reject) => {
        axios
          .get(`/data/chambers/${chamberId}/gyles/${gyleDetails.gyleId}/logs/${logName}.ndjson`)
          .then(response => {
            const ndjson: string = response.data;
            const readings: IReadings[] = ndjson
              .split('\n')
              .filter(json => json)
              .map(json => JSON.parse(json));
            resolve(readings);
          })
          .catch(error => {
            reject(error);
          });
      });
    };

    // Returns promise for aggregating readings from all the logs specified in the supplied
    // gyleDetails along with the latestReadings from the supplied gyleDetails.
    const getAggregatedReadings = (gyleDetails: IGyleDetails): Promise<IReadings[]> => {
      return new Promise((resolve, reject) => {
        const aggregatedReadings: IReadings[] = [];
        Promise.all(gyleDetails.readingsLogs.map(logName => getLogFileReadings(logName))).then(
          logFilesReadings => {
            logFilesReadings.forEach(logFileReadings => {
              aggregatedReadings.push(...logFileReadings);
            });
            aggregatedReadings.push(...gyleDetails.recentReadings);
            resolve(aggregatedReadings);
          }
        );
      });
    };

    let chart: Chart;
    interface SeriesPlus extends Series {
      // We need access to these private properties since Series.points isn't 'live' while adding points.
      // Actually, we could do without this if we kept the last added point for each series as some local state.
      xData: number[]; // Time axis - never null;
      yData: (number | null)[]; // Property axis - can be null for discontinuous properties such as coolerOn.
    }
    let tTargetSeries: SeriesPlus,
      tBeerSeries: SeriesPlus,
      tExternalSeries: SeriesPlus,
      tChamberSeries: SeriesPlus,
      fridgeSeries: SeriesPlus,
      heaterSeries: SeriesPlus;

    let anotherChartVar: Chart;

    // If we know the user is definitely not logged in, go straight to login form.
    if (isAuth === Auth.NotLoggedIn) {
      history.push('/login', { from: '/gyle-chart/' + chamberId });
    } else {
      getGyleDetails()
        .then(_gyleDetails => {
          gyleDetails = _gyleDetails;
          readingsPeriodMillis = gyleDetails.readingsPeriodMillis;
          readingsTimestampResolutionMillis = gyleDetails.readingsTimestampResolutionMillis;
          lastDt = gyleDetails.recentReadings.length
            ? gyleDetails.recentReadings[gyleDetails.recentReadings.length - 1].dt
            : null;
          chart = buildChart(gyleDetails.chamberName, gyleDetails.hasHeater);
          [
            // NOTE: These must be kept in same order as the series definitions
            tTargetSeries,
            tBeerSeries,
            tChamberSeries,
            tExternalSeries,
            fridgeSeries,
            heaterSeries,
          ] = chart.series as SeriesPlus[];
          chart.showLoading();
          return getAggregatedReadings(gyleDetails);
        })
        .then(aggregatedReadings => {
          // Unclear what's going on but sometimes (most times) on refreshing this page (as opposed to navigating to this
          // page) we arrive here and chart.series (and a bunch of its other properties) have gone! TODO: Investigate.
          if (!chart.series) {
            console.error('chart corrupted');
            history.push('/home');
            return;
          }

          addBunchOfReadings(aggregatedReadings);
          chart.hideLoading();

          interval = setInterval(() => {
            addLatestReadings();
          }, readingsPeriodMillis);
        });
    }

    return () => {
      interval && clearInterval(interval);
    };
  }, [dispatch, history, chamberId, isAuth]);

  return (
    <div className="gyle-chart">
      <div id={'gyle-chart-ct'}></div>
    </div>
  );
};

export default GyleChart;