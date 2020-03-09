import React, { useState, useEffect } from 'react';
import { useHistory } from 'react-router-dom';
import axios from 'axios';
import { useAppState } from './state';

const Logout: React.FC = () => {
  const [helperText, setHelperText] = useState('Logging out...');
  const history = useHistory();

  const { dispatch } = useAppState();

  useEffect(() => {
    axios
      .get('/tempctrl/logout')
      .then(function(response) {
        console.log(response);
        dispatch({ type: 'LOGOUT' });
        history.push('/signin');
      })
      .catch(function(error) {
        console.log(error);
        setHelperText('Failed to logout: ' + error);
      });
  }, [dispatch, history]);

  return <div className="signout">{helperText}</div>;
};

export default Logout;
