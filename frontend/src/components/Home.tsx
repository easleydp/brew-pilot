import './Home.scss';
import axios from 'axios';
import React, { useEffect, useState } from 'react';
import { useHistory, useLocation } from 'react-router-dom';
import ILocationState from '../api/ILocationState';
import { useAppState, Auth } from './state';
import { Link } from 'react-router-dom';
import { isMobile } from 'react-device-detect';
import Loading from './Loading';

import Gauge from './Gauge';
import IChamberSummary from '../api/IChamberSummary';

const STATUS_REFRESH_INTERVAL_MILLIS = 180000;

type HomeProps = {
  errorMessage: string | null;
};

//const Home: React.FC = () => {
const Home = ({ errorMessage }: HomeProps) => {
  const history = useHistory<ILocationState>();
  const location = useLocation<ILocationState>();
  const [loading, setLoading] = useState<boolean>(true);
  const { state, dispatch } = useAppState();
  const [chamberSummaries, setChamberSummaries] = useState<IChamberSummary[] | null>(null);
  const [controlTimeEstablished, setControlTimeEstablished] = useState<boolean | null>(null);
  const isAuth = state && state.isAuth;

  const handleAuthError = () => {
    console.debug('Redirecting to signin');
    history.push({ pathname: '/signin', state: { from: '/' } });
    dispatch({ type: 'LOGOUT' });
  };

  useEffect(() => {
    console.info(Auth[isAuth], '=================== Home useEffect invoked ======================');

    let active = true;
    const getChamberSummaries = () => {
      const url = '/tempctrl/guest/chamber-summaries-and-user-type';
      axios
        .get(url)
        .then((response) => {
          if (active) {
            setLoading(false);
            setChamberSummaries(response.data.chamberSummaries);
            setControlTimeEstablished(response.data.controlTimeEstablished);
          }
        })
        .catch((error) => {
          console.debug(url + ' ERROR', error);
          const status = error?.response?.status;
          if (status === 403 || status === 401) {
            console.debug(`Redirecting to signin after ${status}`);
            history.push({ pathname: '/signin', state: { from: location.pathname } });
            dispatch({ type: 'LOGOUT' });
          }
        });
    };

    if (isAuth === Auth.NotLoggedIn) {
      // The user is definitely not logged in. Go straight to signin form.
      history.push({ pathname: '/signin', state: { from: '/' } });
    } else if (isAuth === Auth.Unknown) {
      // We assume the user has hit F5 or hand entered the URL (thus reloading the app), so we don't
      // know whether they're logged in. The App component will be automatically be invoked when the
      // app is loaded (whatever the URL location). This will establish whether user is logged in
      // and update the isAuth state variable, which will cause this useEffect hook to re-execute.
      console.debug('user has hit F5?');
    } else {
      getChamberSummaries();
      const interval = window.setInterval(getChamberSummaries, STATUS_REFRESH_INTERVAL_MILLIS);
      return () => {
        active = false;
        window.clearInterval(interval);
      };
    }
  }, [dispatch, history, isAuth, location.pathname]);

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

  if (errorMessage) {
    return <div className="error">{errorMessage}</div>;
  }
  if (loading) {
    return <Loading />;
  }
  return (
    <div className="home container-fluid">
      {controlTimeEstablished === false && (
        <div className="alert alert-warning" role="status">
          <strong>System clock not synchronised.</strong> Profile updates and readings are paused.
          The Arduino will continue using its last saved chamber settings. Normal operation will
          resume automatically when the system clock is synchronised.
        </div>
      )}
      {chamberSummaries && chamberSummaries.length ? (
        <div className="row">
          {chamberSummaries.map((cs) => {
            return (
              <div key={cs.id} className="col-sm-6">
                <Link to={`/gyle-chart/${cs.id}`}>{gaugeCard(cs)}</Link>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="error">No chamber summaries!</div>
      )}
    </div>
  );
};

export default Home;
