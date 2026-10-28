import './Login.scss';
import React, { useState, useEffect } from 'react';
import { useHistory, useLocation } from 'react-router-dom';
import ILocationState from '../api/ILocationState';
import axios from 'axios';
import { useAppState } from './state';

const getCookie = (name: string): string | undefined => {
  const prefix = `${name}=`;
  const cookie = document.cookie
    .split(';')
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.substring(prefix.length)) : undefined;
};

const Login: React.FC = () => {
  const history = useHistory<ILocationState>();
  const location = useLocation<ILocationState>();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isButtonDisabled, setIsButtonDisabled] = useState(true);
  const [helperText, setHelperText] = useState('');
  //const [error, setError] = useState(false);

  const { dispatch } = useAppState();

  useEffect(() => {
    console.info('=================== Login useEffect invoked ======================');

    if (username.trim() && password.trim()) {
      setIsButtonDisabled(false);
    } else {
      setIsButtonDisabled(true);
    }
  }, [username, password]);

  const handleLogin = () => {
    setIsButtonDisabled(true);
    setHelperText('');

    // axios would by default JSON encode, which Spring won't understand. Mimic regular form encoding.
    const params = new URLSearchParams();
    params.append('username', username);
    params.append('password', password);
    axios
      .get('/tempctrl/csrf')
      .then(() => {
        const csrfToken = getCookie('XSRF-TOKEN');
        if (!csrfToken) {
          throw new Error('CSRF token cookie was not set');
        }
        return axios.post('/tempctrl/login', params, {
          headers: {
            'X-XSRF-TOKEN': csrfToken,
            'Content-Type': 'application/x-www-form-urlencoded',
          },
        });
      })
      .then((response) => {
        console.debug(response);
        //setError(false);
        dispatch({
          type: 'LOGIN',
          isAdmin: response.data.isAdmin,
        });

        console.log(`After successful login, location.state.from is "${location?.state?.from}"`);
        history.replace(location?.state?.from || '/');
      })
      .catch((error) => {
        console.debug(error);
        //setError(true);
        setHelperText('Incorrect username or password');
        setIsButtonDisabled(false);
      });
  };

  const handleKeyPress = (e: any) => {
    if (e.keyCode === 13 || e.which === 13) {
      isButtonDisabled || handleLogin();
    }
    return false;
  };

  const handleSubmit = (event: React.FormEvent) => {
    isButtonDisabled || handleLogin();
    event.preventDefault();
    event.stopPropagation();
    return false;
  };

  return (
    <div className="signin wrapper fadeInDown">
      {/* <p>You must log in to view the page at {from.pathname}</p>
      <button onClick={signin}>Log in</button> */}
      <div id="formContent">
        {/* <div className="fadeIn first">
          <img src="http://danielzawadzki.com/codepen/01/icon.svg" id="icon" alt="User Icon" />
        </div> */}

        <form action="/signin" onSubmit={handleSubmit} autoComplete="on">
          <input
            type="text"
            id="signin"
            className="fadeIn second"
            name="signin"
            placeholder="username"
            autoComplete="username"
            onChange={(e) => setUsername(e.target.value)}
            onKeyPress={(e) => handleKeyPress(e)}
          />
          <input
            type="password"
            id="password"
            className="fadeIn third"
            name="password"
            placeholder="password"
            autoComplete="current-password"
            onChange={(e) => setPassword(e.target.value)}
            onKeyPress={(e) => handleKeyPress(e)}
          />
          <input
            type="submit"
            className="fadeIn fourth"
            value="Login"
            disabled={isButtonDisabled}
          />
          <div className="helper-text">{helperText}</div>
        </form>
      </div>
    </div>
  );
};

export default Login;
