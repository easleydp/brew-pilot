(function() {
  const utcMsFromDaysAndHours = function(days, hours) {
    return Date.UTC(1970, 0, days + 1, hours);
  };

  const hourMs = 1000 * 60 * 60;

  const maxDays = 28;

  const minX = utcMsFromDaysAndHours(0, 0);
  const maxX = utcMsFromDaysAndHours(maxDays, 0);

  const minY = -10;
  const maxY = 40;

  const formatTimeAsHtml = function(ms) {
    const totalHours = Math.round(ms / hourMs); // Round to nearest hour (i.e. what we'll snap to)
    const days = Math.floor(totalHours / 24) + 1;
    const hours = Math.floor(totalHours % 24);
    if (days === 1) {
      return `Hour&nbsp;${hours}`;
    }
    return `Day&nbsp;${days}, hour&nbsp;${hours}`;
  };

  const data = [
    [utcMsFromDaysAndHours(0, 0), 15],
    [utcMsFromDaysAndHours(0, 2), 20],
  ];

  let dragging = false,
    n1x,
    n2x,
    isFirstPoint;

  Highcharts.stockChart('container', {
    credits: {
      enabled: false,
    },

    plotOptions: {
      line: {
        cursor: 'move',
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

    title: {
      text: 'Fermentation temperature profile',
    },
    subtitle: {
      text: 'Click the chart to add a new point. Click a point to remove it.',
    },

    tooltip: {
      useHTML: true,
      formatter: function() {
        // Round to nearest half a degree (or 1 degree F when we support F) (i.e. what we'll snap to)
        const friendlyTemp = `<strong>${Math.round(this.y * 2) / 2}&deg;C</strong>`;

        const i = this.points[0].point.index;
        if (i === 0) {
          return `Start at ${friendlyTemp}`;
        }

        const friendlyTime = formatTimeAsHtml(this.x).toLowerCase();
        if (i === this.points[0].series.data.length - 1) {
          return `Hold at ${friendlyTemp} from<br/>${friendlyTime}`;
        }
        return `${friendlyTemp} at<br/>${friendlyTime}`;
      },
    },

    series: [
      {
        data: data,
        marker: {
          radius: 10,
          enabled: true, // Workaround the issue of point markers randomly all disappearing on adding a new point
          lineWidth: 1,
          lineColor: '#fff',
        },

        point: {
          events: {
            click: function() {
              if (this.index !== 0) {
                // Don't remove first point
                this.remove();
              }
            },
            drag: function(e) {
              const x = e.newPoint.x;
              // Find this node's neighbours in the series (n1, n2) so we can constrain `drag` to (n1.x <= x <= n2.x).
              // Note: We'd have rather done this on dragStart but that event doesn't provide the point.
              if (!dragging) {
                dragging = true;
                const target = e.target;
                const seriesData = target.series.data;
                const iSeriesData = seriesData.map(d => d.id).indexOf(target.id);
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
                e.newPoint.x = 0;
              }
            },
            drop: function(e) {
              dragging = false;

              // Snap x to nearest 1 hr
              const x = (e.newPoint.x = Math.round(e.newPoint.x / hourMs) * hourMs);

              // Snap y to nearest half a degree (or 1 degree F when we support F)
              e.newPoint.y = Math.round(e.newPoint.y * 2) / 2;

              // Constrain to stay within neighbours' time range (including buffer)
              if (n1x > x) {
                e.newPoint.x = n1x;
              } else if (n2x < x) {
                e.newPoint.x = n2x;
              }
            },
          },
        },
        dragDrop: {
          draggableX: true,
          draggableY: true,

          dragMinX: minX,
          dragMinY: minY,

          dragMaxX: maxX,
          dragMaxY: maxY,

          //liveRedraw: false
        },
        states: {
          hover: {
            halo: {
              size: 16,
            },
          },
        },
      },
    ],

    xAxis: {
      min: minX,
      max: maxX + utcMsFromDaysAndHours(1, 0),
      ordinal: false,
      minRange: 14 * hourMs,
      labels: {
        formatter: function() {
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
      type: 'line',
      animation: false,
      events: {
        click: function(e) {
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
                const nearestP = iNearestP !== null ? points[iNearestP] : null;
                if (!nearestP || Math.abs(nearestP.x - x) > Math.abs(p.x - x)) {
                  iNearestP = i;
                }
                return iNearestP;
              }, null);
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
          console.log(0, x / hourMs, y);

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
})();
