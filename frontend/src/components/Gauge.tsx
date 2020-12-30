import './Gauge.scss';
import React, { useEffect } from 'react';
import axios from 'axios';

// https://www.npmjs.com/package/highcharts
import * as HighchartsMore from 'highcharts/highcharts-more';
import * as Highcharts from 'highcharts';
HighchartsMore.default(Highcharts);

type GaugeProps = {
  chamberId: number;
  tTarget: number | null; // null signifying inactive
  handleAuthError: Function;
};

const Gauge = ({ chamberId, tTarget, handleAuthError }: GaugeProps) => {
  interface ISummaryStatus {
    tTarget: number | null;
    tBeer: number | null;
  }

  const containerId = 'container-' + chamberId;

  interface PlotBand {
    from: number;
    to: number;
    color: string;
    innerRadius?: string;
    outerRadius?: string;
  }
  useEffect(() => {
    const minTemp = -1;
    const maxTemp = 41;
    const plotBands: PlotBand[] = [
      {
        from: minTemp,
        to: 10,
        color: '#66aaff', // blue
      },
      {
        from: 10,
        to: 30,
        color: '#DDDF0D', // yellow
      },
      {
        from: 30,
        to: maxTemp,
        color: '#e36b6b', // red
      },
    ];
    if (tTarget !== null) {
      const _tTarget = tTarget / 10;
      plotBands.push({
        from: _tTarget - 0.25,
        to: _tTarget + 0.25,
        color: '#0b0',
        innerRadius: '102%',
        outerRadius: '111%',
      });
    }

    let interval: number;
    (Highcharts as any).chart(
      containerId,
      {
        credits: { enabled: false },
        chart: {
          type: 'gauge',
          plotBackgroundColor: null,
          plotBackgroundImage: null,
          plotBorderWidth: 0,
          plotShadow: false,
          backgroundColor: 'none',
        },
        title: { text: undefined },
        pane: {
          startAngle: -150,
          endAngle: 150,
          background: [
            {
              backgroundColor: {
                linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },
                stops: [
                  [0, '#fff'],
                  [1, '#333'],
                ],
              },
              borderWidth: 0,
              outerRadius: '109%',
            },
            {
              backgroundColor: {
                linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },
                stops: [
                  [0, '#333'],
                  [1, '#fff'],
                ],
              },
              borderWidth: 1,
              outerRadius: '107%',
            },
            {
              backgroundColor: tTarget !== null ? '#fff' : '#e8e8e8',
            },
            {
              backgroundColor: '#ddd',
              borderWidth: 0,
              outerRadius: '105%',
              innerRadius: '103%',
            },
          ],
        },

        // the value axis
        yAxis: {
          min: minTemp,
          max: maxTemp,

          minorTickInterval: 1,
          minorTickWidth: 1,
          minorTickLength: 10,
          minorTickPosition: 'inside',
          minorTickColor: '#777',

          tickInterval: 5,

          tickPixelInterval: 30,
          tickWidth: 2,
          tickPosition: 'inside',
          tickLength: 10,
          tickColor: '#666',
          labels: {
            step: 1,
            rotation: 'auto',
          },
          title: {
            text: '°C',
          },
          plotBands: plotBands,
        },

        series: [
          {
            name: 'Temperature',
            data: [0],
          },
        ],
        tooltip: { enabled: false },
      },

      function (chart: any) {
        const point = chart.series[0].points[0];
        function getReadings() {
          const url = `/tempctrl/guest/chamber/${chamberId}/summary-status`;
          axios
            .get(url)
            .then(function (response) {
              const status: ISummaryStatus = response.data;
              const tBeer = (status.tBeer || 0) / 10;
              // console.debug(chamberId, response, tBeer);

              point.update(tBeer);
            })
            .catch(function (error) {
              console.debug(-1 * chamberId, url + ' ERROR', error);
              point.update(0);
              const status = error?.response?.status;
              if (status === 403 || status === 401) {
                // Since this is just a child component, parent view must take care of the redirect.
                handleAuthError();
              }
            });
        }

        getReadings();
        interval = window.setInterval(getReadings, 60 * 1000);
      }
    );

    return () => {
      interval && clearInterval(interval);
    };
  }, [chamberId, containerId, tTarget]);

  return <div className="gauge" id={containerId}></div>;
};

export default Gauge;
