import './Logout.scss';
import React, { useState, useEffect } from 'react';
import { useHistory } from 'react-router-dom';
import axios from 'axios';
import { useAppState } from './state';
import Loading from './Loading';

const Logout: React.FC = () => {
  const [errorText, setErrorText] = useState('');
  const history = useHistory();

  const { dispatch } = useAppState();

  useEffect(() => {
    axios
      .get('/tempctrl/logout')
      .then((response) => {
        dispatch({ type: 'LOGOUT' });
        history.push('/signin');
      })
      .catch((error) => {
        console.log(error);
        setErrorText('Failed to logout: ' + error);
      });
  }, [dispatch, history]);

  return errorText ? <div className="signout">{errorText}</div> : <Loading />;
};

export default Logout;
