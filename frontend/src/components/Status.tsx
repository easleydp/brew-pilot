import './Status.scss';
import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useAppState, Auth } from './state';
import { useHistory } from 'react-router-dom';
import Cookies from 'universal-cookie';

const syntaxHighlight = (json: string) => {
  json = json
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
  const html = json.replace(
    /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?)/g,
    function(match) {
      var cls = 'number';
      if (/^"/.test(match)) {
        if (/:$/.test(match)) {
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

  const history = useHistory();
  const [status, setStatus] = useState<IStatusReport | null>(null);

  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await axios('/guest/log-chart/status');
        console.debug('/guest/log-chart/status', response);
        setStatus(response.data);
        if (isAuth === Auth.Unknown) {
          dispatch({
            type: 'LOGIN',
            isAdmin: new Cookies().get('isAdmin') === 'true',
          });
        }
      } catch (error) {
        console.debug('/admin/log-chart/status ERROR', error);
        const status = error.response && error.response.status;
        if (status === 403 || status === 401) {
          console.debug(status, 'Redirecting to signin');
          dispatch({ type: 'LOGOUT' });
          history.push('/signin', { from: '/status' });
        }
      }
    };

    console.info(
      Auth[isAuth],
      '=================== Status useEffect invoked ======================'
    );

    // If we know the user is definitely not logged in, go straight to signin form.
    if (isAuth === Auth.NotLoggedIn) {
      history.push('/signin', { from: '/status' });
    } else {
      fetchData();
    }
  }, [dispatch, history]);

  return (
    <div className="status-page">
      {!status ? (
        <p>Status unknown</p>
      ) : (
        <pre dangerouslySetInnerHTML={syntaxHighlight(JSON.stringify(status, undefined, 2))}></pre>
      )}
    </div>
  );
};

export default Status;
