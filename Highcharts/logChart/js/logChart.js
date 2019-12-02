(function() {
  const utcMsFromDaysAndHours = function(days, hours) {
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

  const formatTimeAsHtml = function(ms) {
    const totalHours = Math.round(ms / hourMs); // Round to nearest hour (i.e. what we'll snap to)
    const days = Math.floor(totalHours / 24) + 1;
    const hours = Math.floor(totalHours % 24);
    if (days === 1) {
      return `Hour&nbsp;${hours}`;
    }
    return `Day&nbsp;${days}, hour&nbsp;${hours}`;
  };

  Highcharts.stockChart('container', {
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
      text: 'Fermentation temperature log',
    },

    tooltip: {
      useHTML: true,
      formatter: function() {
        const friendlyTemp = `<strong>${this.y}&deg;C</strong>`;

        const i = this.points[0].point.index;
        if (i === 0) {
          return `Started at ${friendlyTemp}`;
        }

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
      },
      {
        name: 'Target beer temp.',
        data: dataTTarget,
        type: 'line',
        dashStyle: 'shortdot',
        color: '#777',
      },
      {
        name: 'Ambient temp.',
        data: dataTAmbient,
        type: 'spline',
        color: 'rgba(0, 150, 0, 0.5)',
      },
      {
        name: 'Chamber temp.',
        data: dataTChamber,
        type: 'spline',
        color: 'rgba(131, 50, 168, 0.5)',
      },
      {
        name: 'Fridge',
        data: dataFridge,
        type: 'area',
        color: 'rgba(113, 166, 210, 1.0)',
        fillOpacity: 0.5,
        showInNavigator: true,
      },
      {
        name: 'Heater',
        data: dataHeater,
        type: 'areaspline',
        color: 'rgba(255, 90, 150, 0.75)',
        fillOpacity: 0.5,
        showInNavigator: true,
      },
    ],

    legend: {
      enabled: true,
      align: 'right',
      // backgroundColor: '#FCFFC5',
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

    // chart: {
    //   type: 'spline',
    // },

    navigator: {
      series: {
        type: 'spline',
      },
      // xAxis: {
      //     min: utcMsFromDaysAndHours(0, 0),
      //     max: utcMsFromDaysAndHours(1, 0),
      // }
    },
  });
})();
