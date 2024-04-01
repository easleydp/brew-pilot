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
  const inactive = tTarget === null;

  interface PlotBand {
    from: number;
    to: number;
    color: string;
    innerRadius?: string;
    outerRadius?: string;
  }
  useEffect(() => {
    console.info(
      '=================== Gauge useEffect invoked ======================',
      chamberId
    );


    // Hack the Highcharts generated SVG to set a custom dial background image. Liable to break on upgrading Highcharts!
    const svgHackAddDialBgImage = (chart: any) => {
      const gaugeDiv: HTMLElement = chart.renderTo;
      const svgRoot = gaugeDiv.querySelector('svg.highcharts-root');
      const svgDefs = svgRoot!.querySelector('defs');

      const idAndImgName = inactive ? 'gauge-dial-bg-dis' : 'gauge-dial-bg';

      // Add pattern def
      const pattern = svgDefs!.appendChild(document.createElement('pattern'));
      pattern.setAttribute('id', idAndImgName + chamberId); // Add chamberId in case multiple gauges with same (but shifted) image
      pattern.setAttribute('patternUnits', 'userSpaceOnUse');
      pattern.setAttribute('width', '400');
      pattern.setAttribute('height', '400');
      const image = pattern.appendChild(document.createElement('image'));
      image.setAttribute('href', process.env.PUBLIC_URL + '/' + idAndImgName + '.jpg');
      image.setAttribute('x', chamberId % 2 ? '20' : '-70');
      image.setAttribute('y', chamberId % 2 ? '20' : '-70');
      image.setAttribute('style', chamberId % 2 ? '' : 'transform: scaleY(-1) translateY(-260px)');
      image.setAttribute('width', '400');
      image.setAttribute('height', '400');

      // Add path
      const paneGroupPaths = svgRoot!.querySelectorAll('g.highcharts-pane-group>path');
      // We want to insert two new paths after the last but one path. They should be based on the last but one path.
      const insertAfterPath: Element = paneGroupPaths[paneGroupPaths.length - 2];
      const path = insertAfterPath.cloneNode() as Element;
      path.setAttribute('fill', `url(#${idAndImgName + chamberId})`);
      insertAfterPath.after(path);

      // Who knows why this is necessary but without it, it's as if our new defs are missing.
      svgDefs!.innerHTML = svgDefs!.innerHTML;
    };

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
    if (!inactive) {
      const _tTarget = tTarget! / 10;
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
              backgroundColor: inactive ? '#e8e8e8' : '#fff',
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
        svgHackAddDialBgImage(chart);

        const point = chart.series[0].points[0];
        function getBeerTemp() {
          const url = `/tempctrl/guest/chamber/${chamberId}/beer-temp`;
          axios
            .get(url)
            .then(function (response) {
              const status: ISummaryStatus = response.data;
              const tBeer = (status.tBeer || 0) / 10;
              if (point.update) {
                point.update(tBeer);
              } else {
                console.log('point has no update function (1)');
              }
            })
            .catch(function (error) {
              console.debug('chamberId: ' + chamberId, url + ' ERROR', error);
              if (point.update) {
                console.log('setting point to 0 after error');
                point.update(0);
              } else {
                console.log('point has no update function (2)');
              }
              const status = error?.response?.status;
              if (status === 403 || status === 401) {
                // Since this is just a child component, parent view must take care of the redirect.
                handleAuthError();
              }
            });
        }

        getBeerTemp();
        interval = window.setInterval(getBeerTemp, 60 * 1000);
      }
    );

    return () => {
      console.debug('Gauge cleaned-up.');
      interval && clearInterval(interval);
    };
  }, [chamberId, containerId, tTarget, inactive]); // handleAuthError deliberately not included

  return <div className={`gauge ${inactive ? 'inactive' : ''}`} id={containerId}></div>;
};

export default Gauge;
