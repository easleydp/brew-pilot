import './FermentationProfile.scss';
import React, { useEffect, useRef, useState, useCallback } from 'react';
import { isMobile } from 'react-device-detect';
import { useHistory } from 'react-router-dom';
import ILocationState from '../api/ILocationState';
import { useAppState, Auth } from './state';
import { useParams } from 'react-router-dom';
import IGyle from '../api/IGyle';
import ITemperatureProfile from '../api/ITemperatureProfile';
import applyPatchRangeDefaultLeft from '../api/RangeDefaultLeftPatch.js';
import Utils from '../util/Utils';
import axios from 'axios';
import { Button } from 'react-bootstrap';
import Toast from 'react-bootstrap/Toast';
import Highcharts, {
  Chart,
  Series,
  SeriesLineOptions,
  XAxisPlotLinesOptions,
} from 'highcharts/highstock';
// import * as Highcharts from 'highcharts/highstock';
import DraggablePoints from 'highcharts/modules/draggable-points';
import Loading from './Loading';

DraggablePoints(Highcharts);

const FermentationProfile = () => {
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

  const history = useHistory<ILocationState>();
  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;
  const isLoggedIn = isAuth === Auth.LoggedIn;
  const isAdmin = isLoggedIn && state.isAdmin;

  const [loading, setLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [buttonsDisabled, setButtonsDisabled] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);
  const [showError, setShowError] = useState(false);

  const gyleRef = useRef<IGyle | undefined>(undefined);
  const chartRef = useRef<Chart | undefined>(undefined);
  const backedUpProfileRef = useRef<ITemperatureProfile | undefined>(undefined);
  const startTimeOffsetRef = useRef<number | undefined>(undefined);

  const onCloseErrorToast = () => {
    setButtonsDisabled(false);
    setShowError(false);
    setErrorMessage(null);
  };

  const buildChart = (profile: ITemperatureProfile, startTimeOffset?: number): Highcharts.Chart => {
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

    // If startTimeOffset has been supplied, plot a vertical line indicating time now.
    const xAxisPlotLine: Array<XAxisPlotLinesOptions> | undefined = startTimeOffset
      ? [
          {
            color: '#eaa',
            dashStyle: 'Dash',
            zIndex: 999,
            width: 2,
            value: startTimeOffset,
            label: { text: 'Now', rotation: 0, x: -12, y: -3, style: { color: '#c00' } },
          },
        ]
      : undefined;

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
        plotLines: xAxisPlotLine,
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

  useEffect(() => {
    console.info(
      Auth[isAuth],
      '=================== FermentationProfile useEffect invoked ======================'
    );

    // For this chart we want the Highstock range selector to default to the left hand side
    // of the x-axis, i.e. to show earliest points by default.
    applyPatchRangeDefaultLeft(Highcharts, true);

    // Returns promise for retrieving IGyle. (We retrieve the whole gyle since we need dtStarted/Ended as well as the profile.)
    const getGyle = (): Promise<IGyle> => {
      const url = '/tempctrl/guest/chamber/1/latest-gyle';
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
              history.push({ pathname: '/signin', state: { from: '/fermenter-profile' } });
              dispatch({ type: 'LOGOUT' });
            }
            reject(error);
          });
      });
    };

    // // Returns promise for retrieving ITemperatureProfile
    // const getTemperatureProfile = (): Promise<ITemperatureProfile> => {
    //   const url = '/tempctrl/guest/chamber/1/latest-gyle-profile';
    //   return new Promise((resolve, reject) => {
    //     axios
    //       .get(url)
    //       .then((response) => {
    //         return resolve(response.data);
    //       })
    //       .catch((error) => {
    //         console.debug(url + ' ERROR', error);
    //         const status = error?.response?.status;
    //         if (status === 403 || status === 401) {
    //           console.debug(`Redirecting to signin after ${status}`);
    //           history.push({ pathname: '/signin', state: { from: '/fermentation-profile' } });
    //           dispatch({ type: 'LOGOUT' });
    //         }
    //         reject(error);
    //       });
    //   });
    // };

    if (isAuth === Auth.NotLoggedIn) {
      // The user is definitely not logged in. Go straight to signin form.
      history.push({ pathname: '/signin', state: { from: '/fermentation-profile' } });
    } else if (isAuth === Auth.Unknown) {
      // The user has hit F5? Go to the home page where we can check if they're logged in.
      history.push({ pathname: '/', state: { from: '/fermentation-profile' } });
    } else {
      getGyle().then((gyle) => {
        setLoading(false);
        gyleRef.current = gyle;
        backedUpProfileRef.current = gyle.temperatureProfile!;
        // If the gyle is active (i.e. has dtStarted < now but no dtEnded) then pass the
        // start time to buildChart so it may plot a "Now" indicator line.
        const now = Date.now();
        const dtStarted = !gyle.dtEnded ? gyle.dtStarted : undefined;
        startTimeOffsetRef.current = dtStarted && dtStarted < now ? now - dtStarted : undefined;
        chartRef.current = buildChart(backedUpProfileRef.current, startTimeOffsetRef.current);
      });
    }
  }, [dispatch, history, isAuth]);

  const handleReset = () => {
    chartRef.current = buildChart(backedUpProfileRef.current!, startTimeOffsetRef.current);
  };

  const handleSave = async () => {
    const chart = chartRef.current;
    if (!chart) {
      throw Error('Should have a Chart at this stage.');
    }
    const profileSeries = chart.get('tProfile') as Series;
    // Beware:
    // * Using Series.getValidPoints() (or raw Series.points) omits non-visible points;
    // * Using raw Series.data omits never displayed points.
    // * Series.options.data works as long as we account for the following quirks:
    //   - Each point is an Array<number> of len 2 unless moved in which case an object {x, y};
    //   - First point, which should always have x of zero, will have x of 5e-324 if moved.
    const options = profileSeries.options as SeriesLineOptions;
    const optionsData = options.data as Array<Array<number> | { x: number; y: number }>;
    const temperatureProfile: ITemperatureProfile = {
      points: optionsData.map((pt, i) => {
        let x, y;
        if (Array.isArray(pt)) {
          x = pt[0];
          y = pt[1];
        } else {
          x = pt.x;
          y = pt.y;
        }
        if (i === 0 && x < 1) {
          x = 0;
        }
        return {
          hoursSinceStart: x / (1000 * 60 * 60),
          targetTemp: y * 10,
        };
      }),
    };

    console.debug(0, backedUpProfileRef.current?.points);
    console.debug(1, optionsData);
    console.debug(2, temperatureProfile.points);

    setButtonsDisabled(true);
    const url = '/tempctrl/admin/chamber/1/latest-gyle-profile';
    try {
      await axios.post(url, temperatureProfile);
      backedUpProfileRef.current = temperatureProfile;
      setShowSuccess(true);
      setButtonsDisabled(false);
    } catch (error) {
      console.debug(url + ' ERROR', error);
      const status = error?.response?.status;
      if (status === 403 || status === 401) {
        console.debug(`Redirecting to signin after ${status}`);
        history.push({ pathname: '/signin', state: { from: '/fermentation-profile' } });
        dispatch({ type: 'LOGOUT' });
      } else {
        setErrorMessage(Utils.getErrorMessage(error));
        setShowError(true);
      }
    }
  };

  const instruction = `${isMobile ? 'Tap' : 'Click '} the chart to add a new point. ${
    isMobile ? 'Tap' : 'Click '
  } an existing point to remove, or drag to move.`;

  return loading ? (
    <Loading />
  ) : (
    <div className="fermentation-profile">
      <Toast
        className="success"
        onClose={() => setShowSuccess(false)}
        show={showSuccess}
        delay={2000}
        autohide
      >
        <Toast.Header closeButton={false}>
          <strong className="mr-auto">Changes saved</strong>
          <small></small>
        </Toast.Header>
      </Toast>
      <Toast className="error" onClose={onCloseErrorToast} show={showError}>
        <Toast.Header>
          <strong className="mr-auto">Error</strong>
          <small></small>
        </Toast.Header>
        <Toast.Body>{errorMessage}</Toast.Body>
      </Toast>
      <div className="header">
        <div className="item button left">
          <Button variant="secondary" disabled={buttonsDisabled} onClick={handleReset}>
            Reset
          </Button>
        </div>
        <div className="item instructions">
          <h2>Fermentation temperature profile</h2>
          <p>{instruction}</p>
        </div>
        <div className="item button right">
          <Button variant="primary" disabled={!isAdmin || buttonsDisabled} onClick={handleSave}>
            Save
          </Button>
        </div>
      </div>
      <figure className="highcharts-figure">
        <div id={'profile-chart-ct'}></div>
      </figure>
    </div>
  );
};

export default FermentationProfile;
