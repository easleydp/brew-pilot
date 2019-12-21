import React from 'react';
//import logo from '../logo.svg';
import './App.css';
import Home from './Home';
import Status from './Status';
import { BrowserRouter as Router, Switch, Route } from 'react-router-dom';
import Navbar from 'react-bootstrap/Navbar';
import Nav from 'react-bootstrap/Nav';
import NavDropdown from 'react-bootstrap/NavDropdown';

const App: React.FC = () => {
  return (
    <Router>
      <div>
        <Navbar bg="light" expand="lg">
          <Navbar.Brand href="/">
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
              <Nav.Link href="/">Home</Nav.Link>
              <NavDropdown title="Chambers" id="basic-nav-dropdown">
                <NavDropdown.Item href="/chamber/1">Fermenter</NavDropdown.Item>
                <NavDropdown.Divider />
                <NavDropdown.Item href="/chamber/2">Beer fridge</NavDropdown.Item>
              </NavDropdown>
              <Nav.Link href="/profiles">Temperature profiles</Nav.Link>
              <Nav.Link href="/status">Backend status</Nav.Link>
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
