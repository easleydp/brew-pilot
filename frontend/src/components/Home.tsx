import './Home.scss';
import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { isMobile } from 'react-device-detect';

import Gauge from './Gauge';
import { IChamberSummary } from '../api/IChamberSummary';

type HomeProps = {
  chamberSummaries: IChamberSummary[];
};

//const Home: React.FC = () => {
const Home = ({ chamberSummaries }: HomeProps) => {
  const instruction = `${isMobile ? 'Tap' : 'Click '} gauge for details`;
  return chamberSummaries.length ? (
    <div className="home container-fluid">
      <div className="row">
        {chamberSummaries.map(cs => {
          return (
            <div key={cs.id} className="col-sm-6">
              <div className="gauge-card">
                <div className="inner">
                  <h3>{cs.name}</h3>
                  <Gauge chamberId={cs.id} tTarget={cs.tTarget} />
                  <div className="instruction">{instruction}</div>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  ) : (
    <div>None found</div>
  );
};

export default Home;
