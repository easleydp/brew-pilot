import './Gauge.scss';
import React, { useState, useEffect } from 'react';
import axios from 'axios';

// https://www.npmjs.com/package/highcharts
import * as HighchartsMore from 'highcharts/highcharts-more';
import * as Highcharts from 'highcharts';
(HighchartsMore as any)(Highcharts);

type GaugeProps = {
  chamberId: number;
  tTarget: number | null;
};

const Gauge = ({ chamberId, tTarget }: GaugeProps) => {
  interface ISummaryStatus {
    tTarget: number | null;
    tBeer: number | null;
  }

  const [summaryStatus, setSummaryStatus] = useState<ISummaryStatus>({
    tTarget: null,
    tBeer: null,
  });

  let interval: number;
  const containerId = 'container-' + chamberId;
  const minTemp = -1;
  const maxTemp = 41;

  interface PlotBand {
    from: number;
    to: number;
    color: string;
    innerRadius?: string;
    outerRadius?: string;
  }
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
  if (tTarget) {
    tTarget = tTarget / 10;
    plotBands.push({
      from: tTarget - 0.25,
      to: tTarget + 0.25,
      color: '#0b0',
      innerRadius: '102%',
      outerRadius: '111%',
    });
  }

  useEffect(() => {
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
                  [0, '#FFF'],
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
                  [1, '#FFF'],
                ],
              },
              borderWidth: 1,
              outerRadius: '107%',
            },
            {
              backgroundColor: '#fff',
            },
            {
              backgroundColor: '#DDD',
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

      function(chart: any) {
        const point = chart.series[0].points[0];
        function getReadings() {
          axios
            .get(`/chamber/${chamberId}/summary-status`)
            .then(function(response) {
              const status: ISummaryStatus = response.data;
              setSummaryStatus(status);
              const tTarget = (status.tTarget || 0) / 10;
              const tBeer = (status.tBeer || 0) / 10;
              console.log(chamberId, response, tTarget, tBeer);

              point.update(tBeer);
            })
            .catch(function(error) {
              console.log(-1 * chamberId, error);
              point.update(0);
            });
        }

        getReadings();
        interval = window.setInterval(getReadings, 60 * 1000);
      }
    );

    return () => {
      if (interval) {
        clearInterval(interval);
      }
    };
  }, []);

  return <div id={containerId}></div>;
};

export default Gauge;
