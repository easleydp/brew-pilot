import React, { useState, useEffect } from 'react';
import axios from 'axios';

// https://www.npmjs.com/package/highcharts
import * as Highcharts from 'highcharts/highstock';
//import * as Exporting from 'highcharts/modules/exporting';
//Exporting(Highcharts);

//import '../lib/highstockPatch-7.2.1';

const Home: React.FC = () => {
  useEffect(() => {
    console.log(0, 'mount');
    debugger;
    return () => {
      console.log(1, 'unmount');
      debugger;
    };
  }, []);

  return (
    <div className="home container-fluid">
      <div className="row">
        <div className="col-sm-6 bg-yellow">
          <div className="card1">
            <div className="inner">
              <h3>Fermenter</h3>
              <div className="chamber-gauge">[gauge here]</div>
              <div>Click gauge for details</div>
            </div>
          </div>
        </div>
        <div className="col-sm-6 bg-pink">
          <div className="card2">
            <div className="inner">
              <h3>Beer fridge</h3>
              <div className="chamber-gauge">[gauge here]</div>
              <div>Click gauge for details</div>
            </div>
          </div>
        </div>
      </div>
      <div>Click a gauge for details</div>
    </div>
  );
};

export default Home;
