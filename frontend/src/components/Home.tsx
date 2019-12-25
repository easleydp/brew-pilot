import './Home.scss';
import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';
import { isMobile } from 'react-device-detect';

import Gauge from './Gauge';
import { IChamberSummary } from '../api/IChamberSummary';

type HomeProps = {
  chamberSummaries: IChamberSummary[];
  chamberSummariesError: string | null;
};

//const Home: React.FC = () => {
const Home = ({ chamberSummaries, chamberSummariesError }: HomeProps) => {
  const instruction = `${isMobile ? 'Tap' : 'Click '} for details`;
  return chamberSummaries.length ? (
    <div className="home container-fluid">
      <div className="row">
        {chamberSummaries.map(cs => {
          return (
            <div key={cs.id} className="col-sm-6">
              <Link to={`/chamber/${cs.id}`}>
                <div className="gauge-card">
                  <div className="inner">
                    <h3>{cs.name}</h3>
                    <Gauge chamberId={cs.id} tTarget={cs.tTarget} />
                    <div className="instruction">{instruction}</div>
                  </div>
                </div>
              </Link>
            </div>
          );
        })}
      </div>
    </div>
  ) : (
    <div className="home none-found">
      <div>None found.</div>
      {chamberSummariesError && <div>{chamberSummariesError}</div>}
    </div>
  );
};

export default Home;
