// https://medium.com/simply/state-management-with-react-hooks-and-context-api-at-10-lines-of-code-baf6be8302c
// https://stackoverflow.com/a/54667477/65555
import React, { Dispatch, createContext, useContext, useReducer } from 'react';

export enum Auth {
  LoggedIn,
  NotLoggedIn,
  Unknown, // e.g. after the user hits F5, we don't know whether they're logged-in or not
}
export interface AppState {
  isAuth: Auth;
  isAdmin: boolean;
}
export const initialState: AppState = {
  isAuth: Auth.Unknown,
  isAdmin: false,
};

interface Action {
  type: string;
}
interface Logout extends Action {
  type: 'LOGOUT';
}
interface Login extends Action {
  type: 'LOGIN';
  isAdmin: boolean;
}
export type Actions = Login | Logout;

export const reducer = (state: AppState, action: Actions) => {
  switch (action.type) {
    case 'LOGOUT':
      return { ...state, isAuth: Auth.NotLoggedIn, isAdmin: false };
    case 'LOGIN':
      return { ...state, isAuth: Auth.LoggedIn, isAdmin: action.isAdmin };
  }
};

interface ContextProps {
  state: AppState;
  dispatch: Dispatch<Actions>;
}

export const StateContext = createContext({} as ContextProps);

export function StateProvider(props: any) {
  const [state, dispatch] = useReducer(reducer, initialState);

  const value = { state, dispatch };
  return <StateContext.Provider value={value}>{props.children}</StateContext.Provider>;
}

// Helper, returns exactly the same [state, dispatch] array, that is passed as a value to our Provider.
export const useAppState = () => useContext(StateContext);
