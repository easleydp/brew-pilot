import React, { useState, useEffect } from 'react';
import { StateProvider, Auth, useAppState } from './state';
import { useHistory, withRouter, RouteComponentProps } from 'react-router-dom';
//import logo from '../logo.svg';
import './App.scss';
import axios from 'axios';
import { BrowserRouter as Router, Switch, Route, NavLink } from 'react-router-dom';
import PropTypes from 'prop-types';
import PrivateRoute from './PrivateRoute';
// import { LinkContainer } from 'react-router-bootstrap';
import { Nav, Navbar, NavDropdown } from 'react-bootstrap';

import Home from './Home';
import GyleChart from './GyleChart';
import Status from './Status';
import Login from './Login';
import Logout from './Logout';
import IChamberSummary from '../api/IChamberSummary';

// Use a nested cmp just to work around this useHistory() issue: https://github.com/ReactTraining/react-router/issues/6939
const Nested = () => {
  const [chamberSummaries, setChamberSummaries] = useState<IChamberSummary[]>([]);
  const [chamberSummariesError, setChamberSummariesError] = useState<string | null>(null);

  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;
  const isLoggedIn = isAuth === Auth.LoggedIn;
  const isAdmin = isLoggedIn && state.isAdmin;

  const history = useHistory();
  useEffect(() => {
    console.info(Auth[isAuth], '=================== App useEffect invoked ======================');

    const fetchData = async () => {
      try {
        const response = await axios('/guest/chamber-summaries-and-user-type');
        setChamberSummaries(response.data.chamberSummaries);
        dispatch({
          type: 'LOGIN',
          isAdmin: response.data.isAdmin,
        });
      } catch (error) {
        console.debug(error);
        const status = error.response && error.response.status;
        if (status === 403 || status === 401) {
          console.debug(status, 'Redirecting to signin');
          dispatch({ type: 'LOGOUT' });
          setChamberSummaries([]);
          history.push('/signin', { from: '/' });
        } else {
          setChamberSummariesError('' + error);
        }
      }
    };

    // If we know the user is definitely not logged in, go straight to signin form.
    if (isAuth === Auth.NotLoggedIn) {
      setChamberSummaries([]);
      history.push('/signin', { from: '/' });
    } else {
      fetchData();
    }
  }, [isAuth, history]);

  // Approximation of https://github.com/react-bootstrap/react-bootstrap/issues/1301#issuecomment-251281488
  // NOTE: Not at all good that we're currently relying on `onMouseDown` on `Nav.Link`.
  const [navExpanded, setNavExpanded] = useState<boolean>(false);
  const setNavExpandedWrap = function(expanded: boolean) {
    setNavExpanded(expanded);
  };
  const closeNav = function() {
    setNavExpanded(false);
  };

  return (
    <div id="app-wrap">
      <Navbar bg="light" expand="lg" onToggle={setNavExpandedWrap} expanded={navExpanded}>
        <Navbar.Brand as={NavLink} to="/">
          <img
            src="/brew-pilot-logo.png"
            className="d-inline-block align-top"
            alt="BrewPilot logo"
          />
          Brew-Pilot
        </Navbar.Brand>
        <Navbar.Toggle aria-controls="basic-navbar-nav" />
        <Navbar.Collapse id="basic-navbar-nav">
          <Nav className="mr-auto" onSelect={closeNav}>
            {isLoggedIn && (
              <Nav.Link as={NavLink} to="/" onMouseDown={closeNav}>
                Home
              </Nav.Link>
            )}
            {isLoggedIn && (
              <NavDropdown title="Chambers" id="basic-nav-dropdown">
                {chamberSummaries.map((cs, index) => {
                  return (
                    <div key={cs.id}>
                      {index > 0 && <NavDropdown.Divider />}
                      <NavDropdown.Item
                        as={NavLink}
                        to={`/guest/chamber/${cs.id}`}
                        onSelect={closeNav}
                      >
                        {cs.name}
                      </NavDropdown.Item>
                    </div>
                  );
                })}
              </NavDropdown>
            )}
            {isLoggedIn && (
              <Nav.Link as={NavLink} to="/profiles" onMouseDown={closeNav}>
                Temperature profiles
              </Nav.Link>
            )}
            {isAdmin && (
              <Nav.Link as={NavLink} to="/status" onMouseDown={closeNav}>
                Backend status
              </Nav.Link>
            )}
            {isLoggedIn && (
              <Nav.Link as={NavLink} to="/signout" onMouseDown={closeNav}>
                Logout
              </Nav.Link>
            )}
          </Nav>
        </Navbar.Collapse>
      </Navbar>

      {/* A <Switch> looks through its child <Route>s and renders the first one that matches the current URL. */}
      <Switch>
        {/* "/signin" rather "/login" to avoid clash with SprintBoot URL. Likewise "/signout" */}
        <Route path="/signin" component={Login} />
        <Route path="/signout" component={Logout} />
        <Route path="/status" component={Status} />
        <Route path="/gyle-chart/:chamberId" component={GyleChart} />
        <Route path="/">
          <Home chamberSummaries={chamberSummaries} chamberSummariesError={chamberSummariesError} />
        </Route>
      </Switch>
    </div>
  );
};

const App: React.FC = () => {
  return (
    <StateProvider>
      <Router>
        {/* Use a nested cmp just to work around this useHistory() issue: https://github.com/ReactTraining/react-router/issues/6939 */}
        <Nested />
      </Router>
    </StateProvider>
  );
};

export default App;
