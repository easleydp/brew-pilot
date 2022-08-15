import './Home.scss';
import React, { useEffect } from 'react';
import { useHistory, useLocation } from 'react-router-dom';
import ILocationState from '../api/ILocationState';
import { useAppState, Auth } from './state';
import { Link } from 'react-router-dom';
import { isMobile } from 'react-device-detect';
import Loading from './Loading';

import Gauge from './Gauge';
import IChamberSummary from '../api/IChamberSummary';

type HomeProps = {
  chamberSummaries: IChamberSummary[];
  chamberSummariesError: string | null;
};

//const Home: React.FC = () => {
const Home = ({ chamberSummaries, chamberSummariesError }: HomeProps) => {
  const history = useHistory<ILocationState>();
  const location = useLocation<ILocationState>();
  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;

  const handleAuthError = () => {
    console.debug('Redirecting to signin');
    history.push({ pathname: '/signin', state: { from: location.pathname } });
    dispatch({ type: 'LOGOUT' });
  };

  useEffect(() => {
    console.info(
      Auth[isAuth],
      chamberSummaries,
      '=================== Home useEffect invoked ======================'
    );
    // If we know the user is definitely not logged in, go straight to signin form.
    if (isAuth === Auth.NotLoggedIn) {
      history.push({ pathname: '/signin', state: { from: '/' } });
    }
  }, [chamberSummaries, history, isAuth]);

  function gaugeCard(cs: IChamberSummary) {
    const instruction = `${isMobile ? 'Tap' : 'Click '} for details`;
    const title = cs.name + (cs.tTarget !== null ? '' : ' (inactive)');
    return (
      <div className="gauge-card">
        <div className="inner">
          <h3>{title}</h3>
          <Gauge chamberId={cs.id} tTarget={cs.tTarget} handleAuthError={handleAuthError} />
          <div className="instruction">{instruction}</div>
        </div>
      </div>
    );
  }

  if (chamberSummariesError) {
    return <div className="error">{chamberSummariesError}</div>;
  }
  if (chamberSummaries && chamberSummaries.length) {
    return (
      <div className="home container-fluid">
        <div className="row">
          {chamberSummaries.map((cs) => {
            return (
              <div key={cs.id} className="col-sm-6">
                <Link to={`/gyle-chart/${cs.id}`}>{gaugeCard(cs)}</Link>
              </div>
            );
          })}
        </div>
      </div>
    );
  }
  return <Loading />;
};

export default Home;
