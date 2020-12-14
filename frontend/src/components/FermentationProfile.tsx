import './FermentationProfile.scss';
import React, { useEffect, useRef, useCallback } from 'react';
import { isMobile } from 'react-device-detect';
import { useHistory } from 'react-router-dom';
import { useAppState, Auth } from './state';
import { useParams } from 'react-router-dom';
import ITemperatureProfile from '../api/ITemperatureProfile';
import applyPatchRangeDefaultLeft from '../api/RangeDefaultLeftPatch.js';
import axios from 'axios';
// import Highcharts, { Chart, Series } from 'highcharts/highstock';
import * as Highcharts from 'highcharts/highstock';
import { log } from 'util';
import DraggablePoints from 'highcharts/modules/draggable-points';

DraggablePoints(Highcharts);

const FermentationProfile = () => {
  const history = useHistory();
  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;

  interface PointDragDropObjectWithXAndY extends Highcharts.PointDragDropObject {
    x: number;
    y: number;
  }
  interface PointDragEventObjectWithNewPoint extends Highcharts.PointDragEventObject {
    newPoint?: PointDragDropObjectWithXAndY;
  }
  interface PointWithIndex extends Highcharts.Point {
    index: number;
  }

  useEffect(() => {
    console.info(
      Auth[isAuth],
      '=================== FermentationProfile useEffect invoked ======================'
    );

    // For this chart we want the Highstock range selector to default to the left hand side
    // of the x-axis, i.e. to show earliest points by default.
    applyPatchRangeDefaultLeft(Highcharts, true);

    // Returns promise for retrieving ITemperatureProfile
    const getTemperatureProfile = (): Promise<ITemperatureProfile> => {
      const url = '/tempctrl/guest/chamber/1/latest-gyle-profile';
      return new Promise((resolve, reject) => {
        axios
          .get(url)
          .then((response) => {
            return resolve(response.data);
          })
          .catch((error) => {
            console.debug(url + ' ERROR', error);
            const status = error.response && error.response.status;
            if (status === 403 || status === 401) {
              console.debug(status, 'Redirecting to signin');
              dispatch({ type: 'LOGOUT' });
              history.push('/signin', { from: '/fermentation-profile' });
            }
            reject(error);
          });
      });
    };

    const buildChart = (profile: ITemperatureProfile): Highcharts.Chart => {
      const utcMsFromDaysAndHours = function (days: number, hours: number) {
        return Date.UTC(1970, 0, days + 1, hours);
      };

      const hourMs = 1000 * 60 * 60;

      const maxDays = 28;

      const minX = utcMsFromDaysAndHours(0, 0);
      const maxX = utcMsFromDaysAndHours(maxDays, 0);

      const minY = -10;
      const maxY = 40;

      const formatTimeAsHtml = function (ms: number) {
        const totalHours = Math.round(ms / hourMs); // Round to nearest hour (i.e. what we'll snap to)
        const days = Math.floor(totalHours / 24) + 1;
        const hours = Math.floor(totalHours % 24);
        if (days === 1) {
          return `Hour&nbsp;${hours}`;
        }
        return `Day&nbsp;${days}, hour&nbsp;${hours}`;
      };

      let dragging = false,
        n1x: number,
        n2x: number,
        isFirstPoint: boolean;

      return Highcharts.stockChart({
        credits: {
          enabled: false,
        },

        plotOptions: {
          line: {
            cursor: 'move',
          },
          series: {
            dragDrop: {
              draggableX: true,
              draggableY: true,

              dragMinX: minX,
              dragMinY: minY,

              dragMaxX: maxX,
              dragMaxY: maxY,

              liveRedraw: true,
            },
          },
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
              type: 'day',
              text: 'max.',
              count: maxDays,
            },
            // {
            //     type: 'all',
            //     text: 'max.',
            // },
          ],
          buttonTheme: {
            width: 60,
          },
          selected: 1,

          inputEnabled: false,
        },

        tooltip: {
          useHTML: true,
          formatter: function () {
            // Round to nearest half a degree (or 1 degree F when we support F) (i.e. what we'll snap to)
            const friendlyTemp = `<strong>${Math.round(this.y * 2) / 2}&deg;C</strong>`;

            if (!this.points) {
              return;
            }
            if (this.x === 0) {
              return `Start at ${friendlyTemp}`;
            }

            const seriesData = this.points[0].series.data;
            const friendlyTime = formatTimeAsHtml(this.x).toLowerCase();
            if (this.x === seriesData[seriesData.length - 1].x) {
              return `Hold at ${friendlyTemp} from<br/>${friendlyTime}`;
            }
            return `${friendlyTemp} at<br/>${friendlyTime}`;
          },
        },

        series: [
          {
            name: 'Profile',
            id: 'tProfile',
            type: 'line',
            dashStyle: 'ShortDot',
            color: '#777',
            data: profile.points.map((p) => [
              utcMsFromDaysAndHours(0, p.hoursSinceStart),
              p.targetTemp / 10,
            ]),
            marker: {
              radius: 10,
              enabled: true, // Workaround the issue of point markers randomly all disappearing on adding a new point
              lineWidth: 1,
              lineColor: '#fff',
            },
            point: {
              events: {
                click: function (event) {
                  if (this.x !== 0) {
                    // Don't remove first point
                    this.remove();
                  }
                },
                drag: function (_e) {
                  const e = _e as PointDragEventObjectWithNewPoint;
                  const newPoint = e.newPoint;
                  if (!newPoint) {
                    return;
                  }
                  const x = newPoint.x;
                  // Find this node's neighbours in the series (n1, n2) so we can constrain `drag` to (n1.x <= x <= n2.x).
                  // Note: We'd have rather done this on dragStart but that event doesn't provide the point.
                  if (!dragging) {
                    dragging = true;
                    const target = e.target as PointWithIndex;
                    const seriesData = target.series.data;
                    const iSeriesData = target.index;
                    const n1 = seriesData[iSeriesData - 1];
                    const n2 = seriesData[iSeriesData + 1];
                    isFirstPoint = !n1;
                    // Include 1 hr buffer (don't want two points with same x)
                    n1x = n1 ? n1.x + hourMs : Number.MIN_VALUE;
                    n2x = n2 ? n2.x - hourMs : Number.MAX_VALUE;
                  }

                  // Constrain to stay within neighbours' time range (including buffer)
                  if (n1x > x || x > n2x) {
                    return false;
                  }

                  // If it's the first point, keep x at 0.
                  if (isFirstPoint && x) {
                    newPoint.x = 0;
                  }
                },
                drop: function (e) {
                  dragging = false;
                  const newPoint = e.newPoint as PointDragDropObjectWithXAndY;
                  if (!newPoint) {
                    return;
                  }

                  // Snap x to nearest 1 hr
                  const x = (newPoint.x = Math.round(newPoint.x / hourMs) * hourMs);

                  // Snap y to nearest half a degree (or 1 degree F when we support F)
                  newPoint.y = Math.round(newPoint.y * 2) / 2;

                  // Constrain to stay within neighbours' time range (including buffer)
                  if (n1x > x) {
                    newPoint.x = n1x;
                  } else if (n2x < x) {
                    newPoint.x = n2x;
                  }
                },
              },
              states: {
                hover: {
                  halo: {
                    size: 16,
                  },
                },
              },
            },
          } as Highcharts.SeriesLineOptions,
        ],

        xAxis: {
          min: minX,
          max: maxX + utcMsFromDaysAndHours(1, 0),
          ordinal: false,
          minRange: 14 * hourMs,
          labels: {
            formatter: function () {
              return formatTimeAsHtml(this.value);
            },
            useHTML: true,
            // staggerLines: 2
          },
          tickInterval: 12 * hourMs,
          minorTickInterval: 1 * hourMs,
          gridLineWidth: 2,
          maxPadding: 0.2,
        },
        yAxis: {
          softMin: 10,
          softMax: 20,
          floor: minY,
          ceiling: maxY,
          minRange: 10,
          maxRange: maxY - minY,
          title: {
            useHTML: true,
            text: 'Temperature (&deg;C)',
          },
          //   minPadding: 0.2,
          //   maxPadding: 0.2,
          plotLines: [
            {
              value: 0,
              width: 1,
              color: '#808080',
            },
          ],
          tickInterval: 5,
          minorTickInterval: 1,
          showLastLabel: true,
          opposite: false,
        },

        chart: {
          renderTo: 'profile-chart-ct',
          type: 'line',
          animation: false,
          events: {
            click: function (_e) {
              // Param is declared as PointerEventObject but that doesn't give access to xAxis & yAxis.
              const e = _e as Highcharts.ChartClickEventObject;

              let x = e.xAxis[0].value,
                y = e.yAxis[0].value;

              if (isNaN(x) || isNaN(y)) {
                return;
              }

              let series = this.series[0],
                points = series.data,
                iLastPoint = points.length - 1,
                lastPoint = points[iLastPoint],
                lastPointX = lastPoint.x;

              // Leave rounding x until later (we may need precise x to determine which way to nudge).
              if (x < minX) {
                x = minX;
              } else if (x > maxX) {
                x = maxX;
              }

              // Round to nearest half a degree (or 1 degree F when we support F)
              y = Math.round(y * 2) / 2;
              if (y < minY) {
                y = minY;
              } else if (y > maxY) {
                y = maxY;
              }

              // If x is > lastPoint but by less that 1 hr, nudge to lastPoint.x + 1 hr.
              // But if this puts it beyond maxX, ignore.
              if (x > lastPointX) {
                if (x - lastPointX < hourMs) {
                  x = lastPointX + hourMs;
                  if (x > maxX) {
                    return;
                  }
                }
              } else {
                // Not beyond last point.
                // If there's only one point (0) and x is tending to 0, nudge forward.
                if (lastPointX === 0) {
                  if (Math.round(x / hourMs) === 0) {
                    x = hourMs;
                  }
                } else {
                  // More than one point
                  // Find the neighbouring points. Ensure the new point is ay least 1 hr from either. If there's
                  // less than 2 hrs between the neighbours ignore.
                  // Some detail:
                  // If the x is exactly the same as that of an existing point:
                  //   If that existing point is the first point (0), choose other neighbour to right.
                  //   If that existing point is the last point, choose other neighbour to left.
                  //   Otherwise, choose whichever way has most space.
                  const iNearestP = series.data.reduce((iNearestP, p, i) => {
                    const nearestP = iNearestP !== -1 ? points[iNearestP] : null;
                    if (!nearestP || Math.abs(nearestP.x - x) > Math.abs(p.x - x)) {
                      iNearestP = i;
                    }
                    return iNearestP;
                  }, -1);
                  let nearestP = points[iNearestP];
                  let otherNeighbourP;
                  if (nearestP.x === x) {
                    if (iNearestP === 0) {
                      otherNeighbourP = points[1];
                    } else if (iNearestP === iLastPoint) {
                      otherNeighbourP = points[iNearestP - 1];
                    } else {
                      const spaceToLeft = nearestP.x - points[iNearestP - 1].x;
                      const spaceToRight = (points[iNearestP + 1].x = nearestP.x);
                      otherNeighbourP =
                        spaceToLeft > spaceToRight ? points[iNearestP - 1] : points[iNearestP + 1];
                    }
                  } else {
                    otherNeighbourP =
                      nearestP.x - x > 0 ? points[iNearestP - 1] : points[iNearestP + 1];
                  }
                  // Sort: ensure otherNeighbourP is later
                  if (otherNeighbourP.x < nearestP.x) {
                    [nearestP, otherNeighbourP] = [otherNeighbourP, nearestP];
                  }
                  if (otherNeighbourP.x - nearestP.x < 2 * hourMs) {
                    return;
                  }
                  if (x - nearestP.x < hourMs) {
                    x = nearestP.x + hourMs;
                  } else if (otherNeighbourP.x - x < hourMs) {
                    x = otherNeighbourP.x - hourMs;
                  }
                }
              }

              x = Math.round(x / hourMs) * hourMs; // Round to nearest hour
              //console.log(0, x / hourMs, y);

              series.addPoint([x, y]);
            },
          },
        },

        navigator: {
          series: {
            type: 'line',
          },
          // xAxis: {
          //     min: utcMsFromDaysAndHours(0, 0),
          //     max: utcMsFromDaysAndHours(1, 0),
          // }
        },
      });
    };

    if (isAuth === Auth.NotLoggedIn) {
      // The user is definitely not logged in. Go straight to signin form.
      history.push('/signin', { from: '/fermentation-profile' });
    } else if (isAuth === Auth.Unknown) {
      // The user has hit F5? Go to the home page where we can check if they're logged in.
      history.push('/fermentation-profile');
    } else {
      getTemperatureProfile().then((profile) => {
        buildChart(profile);
      });
    }
  }, [dispatch, history, isAuth]);

  const instruction = `${isMobile ? 'Tap' : 'Click '} the chart to add a new point. ${
    isMobile ? 'Tap' : 'Click '
  } an existing point to remove, or drag to move.`;
  return (
    <div className="fermentation-profile">
      <div className="header">
        <div className="item button left">
          <button>Reset</button>
        </div>
        <div className="item instructions">
          <h2>Fermentation temperature profile</h2>
          <p>{instruction}</p>
        </div>
        <div className="item button right">
          <button disabled={true}>Save</button>
        </div>
      </div>
      <figure className="highcharts-figure">
        <div id={'profile-chart-ct'}></div>
      </figure>
    </div>
  );
};

export default FermentationProfile;