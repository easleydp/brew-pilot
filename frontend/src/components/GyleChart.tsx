import './GyleChart.scss';
import React, { useEffect } from 'react';
import { useHistory } from 'react-router-dom';
import { useAppState, Auth } from './state';
import { useParams } from 'react-router-dom';
import axios from 'axios';

import Highcharts from 'highcharts/highstock';

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
    tTarget: number | null;
    tBeer: number | null;
    tExternal: number | null;
    tChamber: number | null;
    tPi: number | null;
    heaterOutput: number | null;
    coolerOn: boolean | null;
    mode: Mode | null;
  }
  interface IGyleDetails {
    chamberName: string;
    gyleId: number;
    gyleName: string;
    temperatureProfile: ITemperatureProfile;
    recentReadings: IReadings[];
  }

  const fetchGyleDetails = async () => {
    const url = '/guest/chamber/' + chamberId + '/active-gyle-details';
    try {
      const response = await axios(url);
      console.log(url, response);
      buildChart(response.data);
    } catch (error) {
      console.debug(url + ' ERROR', error);
      const status = error.response && error.response.status;
      if (status === 403 || status === 401) {
        console.debug(status, 'Redirecting to login');
        dispatch({ type: 'LOGOUT' });
        history.push('/login', { from: url });
      }
    }
  };

  let interval: number;

  const buildChart = (gyleDetails: IGyleDetails) => {
    const utcMsFromDaysAndHours = function(days: number, hours: number) {
      return Date.UTC(1970, 0, days + 1, hours);
    };

    const hourMs = 1000 * 60 * 60;

    const dataTTarget = [
      [utcMsFromDaysAndHours(0, 0), 15],
      [utcMsFromDaysAndHours(0, 1), 15],
      [utcMsFromDaysAndHours(0, 2), 20],
      [utcMsFromDaysAndHours(0, 6), 20],
      [utcMsFromDaysAndHours(0, 7), 20],
    ];

    const dataTAmbient = [
      [utcMsFromDaysAndHours(0, 0), 18.7],
      [utcMsFromDaysAndHours(0, 1), 18.9],
      [utcMsFromDaysAndHours(0, 2), 18.8],
      [utcMsFromDaysAndHours(0, 3), 15.7],
      [utcMsFromDaysAndHours(0, 4), 10.5],
      [utcMsFromDaysAndHours(0, 5), 8.2],
      [utcMsFromDaysAndHours(0, 6), 6.0],
    ];

    const dataTChamber = [
      [utcMsFromDaysAndHours(0, 0), 20.1],
      [utcMsFromDaysAndHours(0, 1), 15.9],
      [utcMsFromDaysAndHours(0, 2), 16.2],
      [utcMsFromDaysAndHours(0, 3), 25.1],
      [utcMsFromDaysAndHours(0, 4), 23.8],
      [utcMsFromDaysAndHours(0, 5), 22.9],
      [utcMsFromDaysAndHours(0, 6), 21.0],
    ];

    const dataTBeer = [
      [utcMsFromDaysAndHours(0, 0), 15.1],
      [utcMsFromDaysAndHours(0, 1), 15.2],
      [utcMsFromDaysAndHours(0, 2), 20.9],
      [utcMsFromDaysAndHours(0, 3), 20.1],
      [utcMsFromDaysAndHours(0, 4), 19.8],
      [utcMsFromDaysAndHours(0, 5), 19.9],
      [utcMsFromDaysAndHours(0, 6), 20.0],
    ];

    const dataFridge = [
      // [utcMsFromDaysAndHours(0, 0), 0],
      [utcMsFromDaysAndHours(0, 1), 0],
      [utcMsFromDaysAndHours(0, 1), 10],
      [utcMsFromDaysAndHours(0, 2), 10],
      [utcMsFromDaysAndHours(0, 2), 0],
      // [utcMsFromDaysAndHours(0, 6), 0],
    ];

    const dataHeater = [
      // [utcMsFromDaysAndHours(0, 0), 0],
      // [utcMsFromDaysAndHours(0, 1), 0],
      [utcMsFromDaysAndHours(0, 2), 0],
      [utcMsFromDaysAndHours(0, 3), 1],
      [utcMsFromDaysAndHours(0, 3), 0],
      [utcMsFromDaysAndHours(0, 4), null],
      [utcMsFromDaysAndHours(0, 4), 0],
      [utcMsFromDaysAndHours(0, 4), 2],
      [utcMsFromDaysAndHours(0, 5), 9],
      [utcMsFromDaysAndHours(0, 6), 10],
    ];

    const formatTimeAsHtml = function(ms: number) {
      const totalHours = Math.round(ms / hourMs); // Round to nearest hour (i.e. what we'll snap to)
      const days = Math.floor(totalHours / 24) + 1;
      const hours = Math.floor(totalHours % 24);
      if (days === 1) {
        return `Hour&nbsp;${hours}`;
      }
      return `Day&nbsp;${days}, hour&nbsp;${hours}`;
    };

    Highcharts.stockChart(
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
          text: gyleDetails.chamberName + ' temperature log',
        },

        tooltip: {
          useHTML: true,
          formatter: function() {
            const friendlyTemp = `<strong>${this.y}&deg;C</strong>`;

            // TODO - analyse
            //   const i = this.points[0].point.index;
            //   if (i === 0) {
            //     return `Started at ${friendlyTemp}`;
            //   }

            const friendlyTime = formatTimeAsHtml(this.x).toLowerCase();
            return `${friendlyTemp} at<br/>${friendlyTime}`;
          },
        },

        series: [
          {
            name: 'Beer temp.',
            data: dataTBeer,
            type: 'spline',
            color: 'rgba(247, 163, 92, 1.0)',
            showInNavigator: true,
          } as Highcharts.SeriesSplineOptions,
          {
            name: 'Target beer temp.',
            data: dataTTarget,
            type: 'line',
            dashStyle: 'ShortDot',
            color: '#777',
          } as Highcharts.SeriesLineOptions,
          {
            name: 'Ambient temp.',
            data: dataTAmbient,
            type: 'spline',
            color: 'rgba(0, 150, 0, 0.5)',
          } as Highcharts.SeriesSplineOptions,
          {
            name: 'Chamber temp.',
            data: dataTChamber,
            type: 'spline',
            color: 'rgba(131, 50, 168, 0.5)',
          } as Highcharts.SeriesSplineOptions,
          {
            name: 'Fridge',
            data: dataFridge,
            type: 'area',
            color: 'rgba(113, 166, 210, 1.0)',
            fillOpacity: 0.5,
            showInNavigator: true,
          } as Highcharts.SeriesAreaOptions,
          {
            name: 'Heater',
            data: dataHeater,
            type: 'areaspline',
            color: 'rgba(255, 90, 150, 0.75)',
            fillOpacity: 0.5,
            showInNavigator: true,
          } as Highcharts.SeriesAreasplineOptions,
        ],

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
          // xAxis: {
          //     min: utcMsFromDaysAndHours(0, 0),
          //     max: utcMsFromDaysAndHours(1, 0),
          // }
        },
      },
      // Chart callback function
      chart => {
        function getReadings() {
          // axios
          //   .get(`/guest/chamber/${chamberId}/recent-readings`, {sinceDt: lastDt})
          //   .then(function(response) {
          //     const readingsList: IReadings[] = response.data;
          //     const tBeer = (status.tBeer || 0) / 10;
          //     chart.series[0].addPoint([utcMsFromDaysAndHours(0, 6 + temp++), 21.0], true);
          //   })
          //   .catch(function(error) {
          //     console.log(-1 * chamberId, error);
          //   });
        }

        interval = window.setInterval(getReadings, 60 * 1000);
      }
    );
  };

  useEffect(() => {
    console.info(
      Auth[isAuth],
      chamberId,
      '=================== GyleChart useEffect invoked ======================'
    );

    // If we know the user is definitely not logged in, go straight to login form.
    if (isAuth === Auth.NotLoggedIn) {
      history.push('/login', { from: '/gyle-chart/' + chamberId });
    } else {
      fetchGyleDetails();
    }

    return () => {
      interval && clearInterval(interval);
    };
  }, []);

  return (
    <div className="gyle-chart">
      <div id={'gyle-chart-ct'}></div>
    </div>
  );
};

export default GyleChart;
