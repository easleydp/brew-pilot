import React from 'react';
//import logo from '../logo.svg';
import './App.css';
import Status from './Status';
import { BrowserRouter as Router, Switch, Route, Link } from 'react-router-dom';

const App: React.FC = () => {
  return (
    <Router>
      <div>
        <nav>
          <ul>
            <li>
              <Link to="/">Home</Link>
            </li>
            <li>
              <Link to="/status">Status</Link>
            </li>
          </ul>
        </nav>

        {/* A <Switch> looks through its children <Route>s and
            renders the first one that matches the current URL. */}
        <Switch>
          <Route path="/status">
            <Status />
          </Route>
          <Route path="/">
            <h1>Home</h1>
            <p>Welcome!</p>
          </Route>
        </Switch>
      </div>
    </Router>
  );
};

export default App;
