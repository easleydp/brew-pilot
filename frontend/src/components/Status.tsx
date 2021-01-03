import './Status.scss';
import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useAppState, Auth } from './state';
import { useHistory } from 'react-router-dom';
import ILocationState from '../api/ILocationState';
import Cookies from 'universal-cookie';
import Loading from './Loading';

const syntaxHighlight = (json: string) => {
  json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const html = json.replace(
    /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g,
    function (match) {
      var cls = 'number';
      if (/^"/.test(match)) {
        if (/:$/.test(match)) {
          match = match.replace(/["]/g, ''); // remove the quotes surrounding the key
          cls = 'key';
        } else {
          cls = 'string';
        }
      } else if (/true|false/.test(match)) {
        cls = 'boolean';
      } else if (/null/.test(match)) {
        cls = 'null';
      }
      return '<span class="' + cls + '">' + match + '</span>';
    }
  );

  return { __html: html };
};

const Status: React.FC = () => {
  interface IStatusReport {
    code: number;
    desc: string;
  }

  const history = useHistory<ILocationState>();
  const [loading, setLoading] = useState<boolean>(true);
  const [status, setStatus] = useState<IStatusReport | null>(null);

  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;

  useEffect(() => {
    const fetchData = async () => {
      const url = '/tempctrl/guest/log-chart/status';
      try {
        const response = await axios(url);
        setLoading(false);
        setStatus(response.data);
        if (isAuth === Auth.Unknown) {
          dispatch({
            type: 'LOGIN',
            isAdmin: new Cookies().get('isAdmin') === 'true',
          });
        }
      } catch (error) {
        console.debug(url + ' ERROR', error);
        const status = error?.response?.status;
        if (status === 403 || status === 401) {
          console.debug(`Redirecting to signin after ${status}`);
          history.push({ pathname: '/signin', state: { from: '/status' } });
          dispatch({ type: 'LOGOUT' });
        }
      }
    };

    console.info(
      Auth[isAuth],
      '=================== Status useEffect invoked ======================'
    );

    // If we know the user is definitely not logged in, go straight to signin form.
    if (isAuth === Auth.NotLoggedIn) {
      history.push({ pathname: '/signin', state: { from: '/status' } });
    } else {
      fetchData();
    }
  }, [dispatch, history]);

  return loading ? (
    <Loading />
  ) : (
    <div className="status-page">
      {!status ? (
        <p>Status unknown</p>
      ) : (
        <pre dangerouslySetInnerHTML={syntaxHighlight(JSON.stringify(status, undefined, 4))}></pre>
      )}
    </div>
  );
};

export default Status;
