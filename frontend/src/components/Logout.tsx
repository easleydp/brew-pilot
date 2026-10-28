import './Logout.scss';
import React, { useState, useEffect } from 'react';
import { useHistory /*, useLocation */ } from 'react-router-dom';
import ILocationState from '../api/ILocationState';
import axios from 'axios';
import { useAppState } from './state';
import Loading from './Loading';

const Logout: React.FC = () => {
  const [errorText, setErrorText] = useState('');
  const history = useHistory<ILocationState>();
  // const location = useLocation<ILocationState>();

  const { dispatch } = useAppState();

  useEffect(() => {
    console.info('=================== Logout useEffect invoked ======================');

    axios
      .get('/tempctrl/csrf')
      .then(() => {
        const csrfCookie = document.cookie
          .split(';')
          .map((value) => value.trim())
          .find((value) => value.startsWith('XSRF-TOKEN='));
        const csrfToken = csrfCookie ? decodeURIComponent(csrfCookie.substring('XSRF-TOKEN='.length)) : undefined;
        if (!csrfToken) {
          throw new Error('CSRF token cookie was not set');
        }

        return axios.post('/tempctrl/logout', null, {
          headers: {
            'X-XSRF-TOKEN': csrfToken,
          },
        });
      })
      .then(() => {
        history.push({ pathname: '/signin', state: { from: '/' } });
        dispatch({ type: 'LOGOUT' });
      })
      .catch((error) => {
        console.log(error);
        setErrorText('Failed to logout: ' + error);
      });
  }, [dispatch, history]);

  return errorText ? <div className="signout">{errorText}</div> : <Loading />;
};

export default Logout;
