import './Home.scss';
import React, { useState, useEffect } from 'react';
import axios from 'axios';

// https://www.npmjs.com/package/highcharts
import * as Highcharts from 'highcharts/highstock';
//import * as Exporting from 'highcharts/modules/exporting';
//Exporting(Highcharts);

//const Home: React.FC = () => {
const Home = () => {
  const [counter, setCounter] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => {
      setCounter(counter => counter + 1);
    }, 1000);

    return () => {
      clearInterval(interval);
    };
  }, []);

  return (
    <div className="home container-fluid">
      <div className="row">
        <div className="col-sm-6 bg-yellow">
          <div className="card1">
            <div className="inner">
              <h3>Fermenter</h3>
              <div className="chamber-gauge">Count: {counter}</div>
              <div>Click gauge for details</div>
            </div>
          </div>
        </div>
        <div className="col-sm-6 bg-pink">
          <div className="card2">
            <div className="inner">
              <h3>Beer fridge</h3>
              <div className="chamber-gauge">TODO</div>
              <div>Click gauge for details</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Home;
