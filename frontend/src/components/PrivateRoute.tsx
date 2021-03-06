/* See
 * https://stackoverflow.com/questions/53104165/implement-react-router-privateroute-in-typescript-project
 * and
 * https://reacttraining.com/react-router/web/example/auth-workflow
 */

import * as React from 'react';
import { Route, Redirect, RouteProps } from 'react-router-dom';
import { useAppState } from './state';

interface PrivateRouteProps extends RouteProps {
  // tslint:disable-next-line:no-any
  component?: any;
  // tslint:disable-next-line:no-any
  children?: any;
}

const PrivateRoute = (props: PrivateRouteProps) => {
  const { component: Component, children, ...rest } = props;
  const { dispatch, state } = useAppState();

  return (
    <Route
      {...rest}
      render={routeProps =>
        state.isAuth ? (
          Component ? (
            <Component {...routeProps} />
          ) : (
            children
          )
        ) : (
          <Redirect
            to={{
              pathname: '/signin',
              state: { from: routeProps.location },
            }}
          />
        )
      }
    />
  );
};

export default PrivateRoute;
