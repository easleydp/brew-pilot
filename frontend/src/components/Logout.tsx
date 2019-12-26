import React from 'react';
import { useHistory } from 'react-router-dom';
import { fakeAuth } from '../api/Auth';

const Logout: React.FC = () => {
  let history = useHistory();

  //   return fakeAuth.isAuthenticated ? (
  //     <p>
  //       Welcome!{' '}
  //       <button
  //         onClick={() => {
  //           fakeAuth.signOut(() => history.push('/login'));
  //         }}
  //       >
  //         Sign out
  //       </button>
  //     </p>
  //   ) : (
  //     <p>You are not logged in.</p>
  //   );

  fakeAuth.signOut(() => history.push('/'));
  return <div></div>;
};

export default Logout;
