import './Home.scss';
import React, { useEffect } from 'react';
import { useHistory } from 'react-router-dom';
import { useAppState, Auth } from './state';
import { Link } from 'react-router-dom';
import { isMobile } from 'react-device-detect';

import Gauge from './Gauge';
import IChamberSummary from '../api/IChamberSummary';

type HomeProps = {
  chamberSummaries: IChamberSummary[];
  chamberSummariesError: string | null;
};

//const Home: React.FC = () => {
const Home = ({ chamberSummaries, chamberSummariesError }: HomeProps) => {
  const history = useHistory();
  const { state } = useAppState();
  const isAuth = state && state.isAuth;

  useEffect(() => {
    console.info(
      Auth[isAuth],
      chamberSummaries,
      '=================== Home useEffect invoked ======================'
    );
    // If we know the user is definitely not logged in, go straight to signin form.
    if (isAuth === Auth.NotLoggedIn) {
      history.push('/signin', { from: '/' });
    }
  }, [chamberSummaries, history, isAuth]);

  function gaugeCard(cs: IChamberSummary) {
    const instruction = cs.tTarget ? `${isMobile ? 'Tap' : 'Click '} for details` : 'Inactive';
    return (
      <div className="gauge-card">
        <div className="inner">
          <h3>{cs.name}</h3>
          <Gauge chamberId={cs.id} tTarget={cs.tTarget} />
          <div className="instruction">{instruction}</div>
        </div>
      </div>
    );
  }

  return chamberSummaries && chamberSummaries.length ? (
    <div className="home container-fluid">
      <div className="row">
        {chamberSummaries.map(cs => {
          return (
            <div key={cs.id} className="col-sm-6">
              {cs.tTarget ? (
                <Link to={`/gyle-chart/${cs.id}`}>{gaugeCard(cs)}</Link>
              ) : (
                gaugeCard(cs)
              )}
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
