import React, { useState, useEffect } from 'react';
import axios from 'axios';

const Status: React.FC = () => {
  interface IStatusReport {
    code: number;
    desc: string;
  }

  const [status, setStatus] = useState<IStatusReport | null>(null);

  useEffect(() => {
    axios
      .get('/log-chart/status')
      .then(function(response) {
        console.log('/log-chart/status', response);
        setStatus(response.data);
      })
      .catch(function(error) {
        console.log('/log-chart/status ERROR', error);
      });
  }, []);

  return <h2>{status ? `Status is ${status.code} ${status.desc}` : 'Status unknown'}</h2>;
};

export default Status;
