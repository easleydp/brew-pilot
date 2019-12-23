import './Home.scss';
import React, { useState, useEffect } from 'react';
import axios from 'axios';

import Gauge from './Gauge';

//const Home: React.FC = () => {
const Home = () => {
  return (
    <div className="home container-fluid">
      <div className="row">
        <div className="col-sm-6 bg-yellow">
          <div className="gauge-card card1">
            <div className="inner">
              <h3>Fermenter</h3>
              <Gauge id="container-1" targetTemp={17.5} />
              <div className="instruction">Click gauge for details</div>
            </div>
          </div>
        </div>
        <div className="col-sm-6 bg-pink">
          <div className="gauge-card card2">
            <div className="inner">
              <h3>Beer fridge</h3>
              <Gauge id="container-2" targetTemp={9} />
              <div className="instruction">Click gauge for details</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Home;
