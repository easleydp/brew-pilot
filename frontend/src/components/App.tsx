import React, { useState, useEffect } from 'react';
//import logo from '../logo.svg';
import './App.scss';
import axios from 'axios';
import { BrowserRouter as Router, Switch, Route, NavLink } from 'react-router-dom';
// import { LinkContainer } from 'react-router-bootstrap';
import { Nav, Navbar, NavDropdown } from 'react-bootstrap';

import Home from './Home';
import Status from './Status';
import { IChamberSummary } from '../api/IChamberSummary';

const App: React.FC = () => {
  const [chamberSummaries, setChamberSummaries] = useState<IChamberSummary[]>([]);
  const [chamberSummariesError, setChamberSummariesError] = useState<string | null>(null);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await axios('/chamber-summaries');
        setChamberSummaries(response.data);
      } catch (error) {
        setChamberSummariesError('' + error);
      }
    };
    fetchData();
  }, []);

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
    <Router>
      <div>
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
              <Nav.Link as={NavLink} to="/" onMouseDown={closeNav}>
                Home
              </Nav.Link>
              <NavDropdown title="Chambers" id="basic-nav-dropdown">
                {chamberSummaries.map((cs, index) => {
                  return (
                    <div key={cs.id}>
                      {index > 0 && <NavDropdown.Divider />}
                      <NavDropdown.Item as={NavLink} to={`/chamber/${cs.id}`} onSelect={closeNav}>
                        {cs.name}
                      </NavDropdown.Item>
                    </div>
                  );
                })}
              </NavDropdown>
              <Nav.Link as={NavLink} to="/profiles" onMouseDown={closeNav}>
                Temperature profiles
              </Nav.Link>
              <Nav.Link as={NavLink} to="/status" onMouseDown={closeNav}>
                Backend status
              </Nav.Link>
            </Nav>
          </Navbar.Collapse>
        </Navbar>

        {/* A <Switch> looks through its child <Route>s and renders the first one that matches the current URL. */}
        <Switch>
          <Route path="/status">
            <Status />
          </Route>
          <Route path="/">
            <Home
              chamberSummaries={chamberSummaries}
              chamberSummariesError={chamberSummariesError}
            />
          </Route>
        </Switch>
      </div>
    </Router>
  );
};

export default App;
