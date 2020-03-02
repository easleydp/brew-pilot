import './Login.scss';
import React, { useState, useEffect } from 'react';
import { useHistory, useLocation } from 'react-router-dom';
import axios from 'axios';
import { useAppState } from './state';

const Login: React.FC = () => {
  let history = useHistory();
  let location = useLocation();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isButtonDisabled, setIsButtonDisabled] = useState(true);
  const [helperText, setHelperText] = useState('');
  //const [error, setError] = useState(false);

  const { dispatch } = useAppState();

  useEffect(() => {
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
      .post('/login', params, {
        headers: {
          'X-XSRF-TOKEN': '_csrf',
          //'Content-Type': 'application/json;charset=ISO-8859-1',
          'Content-Type': 'application/x-www-form-urlencoded',
          //multipart/form-data
        },
      })
      .then(response => {
        console.debug(response);
        //setError(false);
        dispatch({
          type: 'LOGIN',
          isAdmin: response.data.isAdmin,
        });

        console.log('After successful login, location.state is:', location.state);
        let { from } = location.state || { from: { pathname: '/' } };
        history.replace(from);
      })
      .catch(error => {
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

  // let { from } = location.state || { from: { pathname: '/' } };
  // let signin = () => {
  //   fakeAuth.authenticate(() => {
  //     history.replace(from);
  //   });
  // };

  return (
    <div className="signin wrapper fadeInDown">
      {/* <p>You must log in to view the page at {from.pathname}</p>
      <button onClick={signin}>Log in</button> */}
      <div id="formContent">
        {/* <div className="fadeIn first">
          <img src="http://danielzawadzki.com/codepen/01/icon.svg" id="icon" alt="User Icon" />
        </div> */}

        <form action="/signin" onSubmit={handleSubmit}>
          <input
            type="text"
            id="signin"
            className="fadeIn second"
            name="signin"
            placeholder="username"
            onChange={e => setUsername(e.target.value)}
            onKeyPress={e => handleKeyPress(e)}
          />
          <input
            type="password"
            id="password"
            className="fadeIn third"
            name="password"
            placeholder="password"
            onChange={e => setPassword(e.target.value)}
            onKeyPress={e => handleKeyPress(e)}
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
