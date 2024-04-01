import './CreateGyle.scss';
import React, { useEffect, useState } from 'react';
import { useHistory, useLocation } from 'react-router-dom';
import ILocationState from '../api/ILocationState';
import { useAppState, Auth } from './state';
import { useParams } from 'react-router-dom';
import Utils from '../util/Utils';
import axios from 'axios';
import { Form, Button, Row, Col } from 'react-bootstrap';
import { useFormik /*, yupToFormErrors */} from 'formik';
import * as Yup from 'yup';
import Toast from 'react-bootstrap/Toast';
import Loading from './Loading';

// interface IErrors {
//   formGyleId?: string;
// }
// interface IValues {
//   formGyleId?: string;
// }

interface IGyleNameIdDuration {
  id: number;
  name: string;
  durationHrs: number;
  startTemp: number;
  maxTemp: number;
}

const CreateGyle = () => {
  const history = useHistory<ILocationState>();
  const location = useLocation<ILocationState>();
  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;
  const isLoggedIn = isAuth === Auth.LoggedIn;
  const isAdmin = isLoggedIn && state.isAdmin;

  const { chamberId } = useParams<{ chamberId: string }>();

  const [recentGyles, setRecentGyles] = useState<IGyleNameIdDuration[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  //const [showSuccess, setShowSuccess] = useState(false);
  const [showError, setShowError] = useState(false);

  const formik = useFormik({
    initialValues: {
      formGyleId: ''
    },
    // validate,
    validationSchema: Yup.object({
      formGyleId: Yup.string().required('Required'),
    }),
    onSubmit: (values, { setSubmitting }) => {
      setSubmitting(true);
      const url = '/tempctrl/admin/chamber/' + chamberId + '/create-gyle';
      axios
        .post(url, {
          gyleToCopyId: values.formGyleId,
          newName: 'TODO **** Choose a name ****'
        })
        .then((/*response*/) => {
          //setShowSuccess(true);
          setSubmitting(false);
          history.push({ pathname: `/gyle-management/${chamberId}` });
        })
        .catch((error) => {
          console.debug(url + ' ERROR', error, error.response.data);
          const status = error?.response?.status;
          if (status === 403 || status === 401) {
            console.debug(`Redirecting to signin after ${status}`);
            history.push({ pathname: '/signin', state: { from: location.pathname } });
            dispatch({ type: 'LOGOUT' });
          } else {
            setErrorMessage(Utils.getErrorMessage(error));
            setShowError(true);
            // Note, we deliberately don't `setSubmitting(false)` here; that only happens when the user acknowledges the error.
          }
        });
    },
  });

  const onCloseErrorToast = () => {
    setShowError(false);
    setErrorMessage(null);
    formik.setSubmitting(false);
  };

  useEffect(() => {
    console.info(
      Auth[isAuth],
      '=================== CreateGyle useEffect invoked ======================'
    );

    // Returns promise for recent gyles (for chamber identified by chamberId)
    const getRecentGyles = (): Promise<[IGyleNameIdDuration]> => {
      const url = '/tempctrl/guest/chamber/' + chamberId + '/recent-gyles';
      return new Promise((resolve, reject) => {
        axios
          .get(url, {
            params: { max: 12 }
          })
          .then((response) => {
            return resolve(response.data);
          })
          .catch((error) => {
            console.debug(url + ' ERROR', error);
            const status = error?.response?.status;
            if (status === 403 || status === 401) {
              console.debug(`Redirecting to signin after ${status}`);
              history.push({ pathname: '/signin', state: { from: location.pathname } });
              dispatch({ type: 'LOGOUT' });
            }
            reject(error);
          });
      });
    };

    const buildForm = () => {
      formik.setFieldValue('formGyleId', '');
    };
  
    if (isAuth === Auth.NotLoggedIn) {
      // The user is definitely not logged in. Go straight to signin form.
      history.push({ pathname: '/signin', state: { from: location.pathname } });
    } else if (isAuth === Auth.Unknown) {
      // We assume the user has hit F5 or hand entered the URL (thus reloading the app), so we don't
      // know whether they're logged in. The App component will be automatically be invoked when the
      // app is loaded (whatever the URL location). This will establish whether user is logged in
      // and update the isAuth state variable, which will cause this useEffect hook to re-execute.
      console.debug('user has hit F5?');
    } else {
      getRecentGyles().then((recentGyles) => {
        setRecentGyles(recentGyles);
        buildForm();
        setLoading(false);
      });
    }
  }, [dispatch, history, isAuth, location.pathname]); // formik deliberately not included

  return loading ? (
    <Loading />
  ) : (
    <Form className="create-gyle" onSubmit={formik.handleSubmit}>
      {/* <h1>Create a new latest gyle</h1> */}
      <Toast className="error" onClose={onCloseErrorToast} show={showError}>
        <Toast.Header>
          <strong className="mr-auto">Error</strong>
          <small></small>
        </Toast.Header>
        <Toast.Body>{errorMessage}</Toast.Body>
      </Toast>
      <Form.Group controlId="formGyleId" className="gyle-id">
        <Form.Control
          as="select"
          {...formik.getFieldProps('formGyleId')}
        >
          <option className="d-none" value="">
                Select a recent gyle to copy ...
          </option>
          {recentGyles.map(gyle => (
                <option key={gyle.id} value={gyle.id}>{gyle.name}&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[start temp {gyle.startTemp / 10}°C, max temp {gyle.maxTemp / 10}°C, {gyle.durationHrs} hrs]</option>
          ))}
        </Form.Control>
      </Form.Group>

      <Row>
        <Col>
          <Button variant="primary" type="submit" disabled={!isAdmin || !formik.isValid || formik.isSubmitting}>
            Copy
          </Button>
        </Col>
      </Row>
    </Form>
  );
};

export default CreateGyle;
