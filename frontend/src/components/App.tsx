import React from 'react';
//import logo from '../logo.svg';
import './App.scss';
import { BrowserRouter as Router, Switch, Route, NavLink } from 'react-router-dom';
// import { LinkContainer } from 'react-router-bootstrap';
import { Nav, Navbar, NavDropdown } from 'react-bootstrap';

import Home from './Home';
import Status from './Status';

const App: React.FC = () => {
  return (
    <Router>
      <div>
        <Navbar bg="light" expand="lg">
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
            <Nav className="mr-auto">
              <Nav.Link as={NavLink} to="/">
                Home
              </Nav.Link>
              <NavDropdown title="Chambers" id="basic-nav-dropdown">
                <NavDropdown.Item as={NavLink} to="/chamber/1">
                  Fermenter
                </NavDropdown.Item>
                <NavDropdown.Divider />
                <NavDropdown.Item as={NavLink} to="/chamber/2">
                  Beer fridge
                </NavDropdown.Item>
              </NavDropdown>
              <Nav.Link as={NavLink} to="/profiles">
                Temperature profiles
              </Nav.Link>
              <Nav.Link as={NavLink} to="/status">
                Backend status
              </Nav.Link>
            </Nav>
          </Navbar.Collapse>
        </Navbar>

        {/* A <Switch> looks through its children <Route>s and
            renders the first one that matches the current URL. */}
        <Switch>
          <Route path="/status">
            <Status />
          </Route>
          <Route path="/">
            <Home />
          </Route>
        </Switch>
      </div>
    </Router>
  );
};

export default App;
