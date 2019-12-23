import './Gauge.scss';
import React, { useState, useEffect } from 'react';

// https://www.npmjs.com/package/highcharts
import * as HighchartsMore from 'highcharts/highcharts-more';
//import * as HighchartsGauge from 'highcharts/modules/solid-gauge';
import * as Highcharts from 'highcharts';
(HighchartsMore as any)(Highcharts);

type GaugeProps = {
  id: string;
  targetTemp: number;
};

const Gauge = ({ id, targetTemp }: GaugeProps) => {
  let interval: number;
  useEffect(() => {
    (Highcharts as any).chart(
      id,
      {
        credits: {
          enabled: false,
        },
        chart: {
          type: 'gauge',
          plotBackgroundColor: null,
          plotBackgroundImage: null,
          plotBorderWidth: 0,
          plotShadow: false,
          backgroundColor: 'none',
        },
        title: {
          text: undefined, //'Beer fridge temperature',
        },
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
              // default background
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
          min: -1,
          max: 41,

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
          plotBands: [
            {
              from: -1,
              to: 10,
              color: '#66aaff', // blue
            },
            // {
            //   from: 10,
            //   to: 19,
            //   color: '#DDDF0D', // yellow
            // },
            // {
            //   from: 19,
            //   to: 21,
            //   color: '#55BF3B', // green
            // },
            // {
            //   from: 21,
            //   to: 30,
            //   color: '#DDDF0D', // yellow
            // },
            {
              from: 10,
              to: 30,
              color: '#DDDF0D', // yellow
            },
            {
              from: 30,
              to: 41,
              color: '#e36b6b', // red
            },
            {
              from: targetTemp - 0.25,
              to: targetTemp + 0.25,
              // color: '#333',
              color: '#0b0',
              innerRadius: '102%',
              outerRadius: '111%',
            },
          ],
        },

        series: [
          {
            name: 'Temperature',
            data: [20],
            // tooltip: {
            //   valueSuffix: '°C',
            // },
          },
        ],
        tooltip: { enabled: false },
      },

      // Add some life
      function(chart: any) {
        interval = window.setInterval(function() {
          let point = chart.series[0].points[0],
            newVal,
            inc = (Math.random() - 0.5) * 20;

          newVal = point.y + inc;
          if (newVal < -1 || newVal > 41) {
            newVal = point.y - inc;
          }

          newVal = Math.round(newVal * 10) / 10;

          point.update(newVal);
        }, 3000);
      }
    );

    return () => {
      clearInterval(interval);
    };
  }, []);

  return <div id={id}></div>;
};

export default Gauge;
