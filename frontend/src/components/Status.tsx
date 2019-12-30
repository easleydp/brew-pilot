import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useAppState, Auth } from './state';
import { useHistory } from 'react-router-dom';

const Status: React.FC = () => {
  interface IStatusReport {
    code: number;
    desc: string;
  }

  const history = useHistory();
  const [status, setStatus] = useState<IStatusReport | null>(null);

  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;

  useEffect(() => {
    console.info(
      Auth[isAuth],
      '=================== Status useEffect invoked ======================'
    );

    const fetchData = async () => {
      try {
        const response = await axios('/admin/log-chart/status');
        console.log('/admin/log-chart/status', response);
        setStatus(response.data);
      } catch (error) {
        console.debug('/admin/log-chart/status ERROR', error);
        const status = error.response && error.response.status;
        if (status === 403 || status === 401) {
          console.debug(status, 'Redirecting to login');
          dispatch({ type: 'LOGOUT' });
          history.push('/login', { from: '/status' });
        }
      }
    };

    // If we know the user is definitely not logged in, go straight to login form.
    if (isAuth === Auth.NotLoggedIn) {
      history.push('/login', { from: '/status' });
    } else {
      fetchData();
    }
  }, []);

  return <h2>{status ? `Status is ${status.code} ${status.desc}` : 'Status unknown'}</h2>;
};

export default Status;
