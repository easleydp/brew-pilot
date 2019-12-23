import './Home.scss';
import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { isMobile } from 'react-device-detect';

import Gauge from './Gauge';

//const Home: React.FC = () => {
const Home = () => {
  interface IChamberSummary {
    id: number;
    name: string;
    tTarget: number | null;
  }

  const [chamberSummaries, setChamberSummaries] = useState<IChamberSummary[]>([]);

  useEffect(() => {
    const fetchData = async () => {
      const response = await axios('/chamber-summaries');
      setChamberSummaries(response.data);
    };
    fetchData();
  }, []);

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
