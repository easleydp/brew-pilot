import './Status.scss';
import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { useAppState, Auth } from './state';
import { useHistory, useLocation } from 'react-router-dom';
import ILocationState from '../api/ILocationState';
import Loading from './Loading';

const JSON_INDENT = 4;

const dateToStr = (date: Date) => {
  const parts = new Intl.DateTimeFormat(['en'], {
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(date);
  const part = (type: string) => {
    return parts.find((part) => part.type === type)?.value;
  };
  return `${part('weekday')}, ${part('hour')}:${part('minute')}`;
};

const transformStatus = (status: any) => {
  // recentlyOffline array contains ISO8601 UTC date/times. Convert to local time and a more friendly format.
  const recentlyOffline = status.recentlyOffline;
  if (recentlyOffline) {
    status.recentlyOffline = recentlyOffline.map((utcDate: string) => {
      const localDate = new Date(utcDate);
      return dateToStr(localDate);
    });
  }
  // For quick date format test:
  // if (!recentlyOffline) {
  //   status.recentlyOffline = [dateToStr(new Date())];
  // }
  return status;
};

/**
 * For small screens, removes the outermost braces and decreases the indent of the remaining lines.
 *
 * We only do this for smaller screens because the effect doesn't look good on desktop.
 * Assumes the JSON string has been formatted with line-breaks and indentation.
 */
const removeOuterBraces = (json: string) => {
  if (window.screen.width <= 1280) {  // Bit of a hack but simple compared to the alternatives (https://stackoverflow.com/a/62695041/65555)
    if (json && json.length) {
      let lines = json.split('\n');
      // If first and last lines are opening and closing braces (as we expect),
      // remove them and decrease the indent of all the remaining lines.
      const lineCount = lines.length;
      if (lineCount > 2) {
        if (lines[0] === '{' && lines[lineCount - 1] === '}') {
          lines.length = lineCount - 1;
          lines.shift();
          lines = lines.map(line => line.substring(JSON_INDENT));
          json = lines.join('\n');
        }
      }
    }
  }
  return json;
};

const syntaxHighlight = (json: string) => {
  json = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const html = json.replace(
    /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+-]?\d+)?|,)/g,
    (match) => {
      if (match === ',') return ''; // Since the input is pretty-printed we can afford to drop the JSON commas
      let cls;
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
      } else {
        cls = 'number';
        match = (+match).toLocaleString();
      }
      return '<span class="' + cls + '">' + match + '</span>';
    }
  );

  return { __html: html };
};

const Status = () => {
  const history = useHistory<ILocationState>();
  const location = useLocation<ILocationState>();
  const [loading, setLoading] = useState<boolean>(true);
  const [status, setStatus] = useState<any | null>(null);

  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;

  useEffect(() => {
    const fetchData = async () => {
      const url = '/tempctrl/guest/log-chart/status';
      axios
        .get(url)
        .then((response) => {
          setLoading(false);
          setStatus(transformStatus(response.data));
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

    console.info(
      Auth[isAuth],
      '=================== Status useEffect invoked ======================'
    );

    // If we know the user is definitely not logged in, go straight to signin form.
    if (isAuth === Auth.NotLoggedIn) {
      history.push({ pathname: '/signin', state: { from: location.pathname } });
    } else if (isAuth === Auth.Unknown) {
      // We assume the user has hit F5 or hand entered the URL (thus reloading the app), so we don't
      // know whether they're logged in. The App component will be automatically be invoked when the
      // app is loaded (whatever the URL location). This will establish whether user is logged in
      // and update the isAuth state variable, which will cause this useEffect hook to re-execute.
      console.debug('user has hit F5?');
    } else {
      fetchData();
    }
  }, [dispatch, history, isAuth, location.pathname]);

  return loading ? (
    <Loading />
  ) : (
    <div className="status-page">
      {!status ? (
        <p>Status unknown</p>
      ) : (
        <pre dangerouslySetInnerHTML={syntaxHighlight(removeOuterBraces(JSON.stringify(status, undefined, JSON_INDENT)))}></pre>
      )}
    </div>
  );
};

export default Status;
