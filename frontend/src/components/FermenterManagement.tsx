import './FermenterManagement.scss';
import React, { useEffect, useState } from 'react';
import { useHistory, useLocation } from 'react-router-dom';
import ILocationState from '../api/ILocationState';
import { useAppState, Auth } from './state';
import IGyle from '../api/IGyle';
import { Mode } from '../api/Mode';
import NowPattern from '../util/NowPattern';
import Utils from '../util/Utils';
import axios from 'axios';
import { Form, Button, Row, Col } from 'react-bootstrap';
import { useFormik, yupToFormErrors } from 'formik';
import * as Yup from 'yup';
import Toast from 'react-bootstrap/Toast';
import Loading from './Loading';

interface IErrors {
  formName?: string;
  formDtStarted?: string;
  formDtEnded?: string;
}
interface IValues {
  formName?: string;
  formDtStarted?: string;
  formDtEnded?: string;
  formMode: Mode;
}

// const validate = (values: IValues) => {
//   const errors: IErrors = {};
//   if (values.formDtStarted) {
//     if (!/^\d+$/.test(values.formDtStarted)) {
//       errors.formDtStarted = 'Started must be a positive integer';
//     } else if (values.formDtStarted.length !== 13) {
//       errors.formDtStarted = `Started must be 13 digits, not ${values.formDtStarted.length}`;
//     }
//   }
//   if (values.formDtEnded) {
//     if (!values.formDtStarted) {
//       errors.formDtEnded = 'Ended cannot be specified without Started being set';
//     } else if (!/^\d+$/.test(values.formDtEnded)) {
//       errors.formDtEnded = 'Ended must be all digits';
//     } else if (values.formDtEnded.length !== 13) {
//       errors.formDtEnded = `Ended must be 13 digits, not ${values.formDtEnded.length}`;
//     }
//   }
//   return errors;
// };

const FermenterManagement = () => {
  const history = useHistory<ILocationState>();
  const location = useLocation<ILocationState>();
  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;
  const isLoggedIn = isAuth === Auth.LoggedIn;
  const isAdmin = isLoggedIn && state.isAdmin;

  const [loading, setLoading] = useState<boolean>(true);
  const [gyle, setGyle] = useState<IGyle | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [showSuccess, setShowSuccess] = useState(false);
  const [showError, setShowError] = useState(false);

  const onCloseErrorToast = () => {
    setShowError(false);
    setErrorMessage(null);
    formik.setSubmitting(false);
  };

  useEffect(() => {
    console.info(
      Auth[isAuth],
      '=================== FermenterManagement useEffect invoked ======================'
    );

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
      getGyle().then((gyle) => {
        setLoading(false);
        setGyle(gyle);
        buildForm(gyle);
      });
    }
  }, [dispatch, history, isAuth]);

  // Returns promise for retrieving IGyle
  const getGyle = (): Promise<IGyle> => {
    const url = '/tempctrl/guest/chamber/1/latest-gyle';
    return new Promise((resolve, reject) => {
      axios
        .get(url)
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

  const buildForm = (gyle: IGyle) => {
    formik.setFieldValue('formName', gyle.name);
    formik.setFieldValue('formDtStarted', gyle.dtStarted || '');
    formik.setFieldValue('formDtEnded', gyle.dtEnded || '');
    formik.setFieldValue('formMode', gyle.mode || Mode.Auto);
  };

  const formik = useFormik({
    initialValues: {
      formName: '',
      formDtStarted: '',
      formDtEnded: '',
      formMode: Mode.Auto,
    },
    // validate,
    validationSchema: Yup.object({
      formName: Yup.string().required('Required'),
      formDtStarted: Yup.number()
        .typeError('Must be an integer')
        .integer('Must be an integer')
        .min(1580000000000)
        .max(2000000000000),
      formDtEnded: Yup.number()
        .typeError('Must be an integer')
        .integer('Must be an integer')
        .min(1580000000000)
        .max(2000000000000)
        .test('', 'Ended cannot be specified without Started being set', function (val) {
          return !val || this.parent['formDtStarted'];
        })
        .test('', 'Ended must be later than Started', function (val) {
          return !val || val > this.parent['formDtStarted'];
        }),
    }),
    onSubmit: (values, { setSubmitting }) => {
      gyle!.name = values.formName;
      gyle!.dtStarted = values.formDtStarted ? parseInt(values.formDtStarted) : undefined;
      gyle!.dtEnded = values.formDtEnded ? parseInt(values.formDtEnded) : undefined;
      // gyle!.mode = values.formMode; Commented-out in sympathy with form.
      // gyle.mode is currently returned as we received it
      // gyle.temperatureProfile is returned as we received it
      setSubmitting(true);
      const url = '/tempctrl/admin/chamber/1/latest-gyle';
      axios
        .post(url, gyle)
        .then((response) => {
          setShowSuccess(true);
          setSubmitting(false);
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
  const roundMsToNearestSecond = (ms: number) => {
    return Math.round(ms / 1000) * 1000;
  };
  const handleDtStartedNow = () => {
    setFieldNow('formDtStarted');
  };
  const handleDtEndedNow = () => {
    setFieldNow('formDtEnded');
  };
  const setFieldNow = (field: string) => {
    formik.setFieldValue(field, roundMsToNearestSecond(Date.now()));
  };
  const handleFormBlur = () => {
    // If either of the two time input fields looks like a 'now' pattern, convert to ms
    checkForNowPattern('formDtStarted');
    checkForNowPattern('formDtEnded');
  };
  const checkForNowPattern = (field: string) => {
    let value = formik.getFieldProps(field).value;
    if (value) {
      value = '' + value; // Could be integer; normalise to string
      if (NowPattern.isNowPattern(value)) {
        const t = NowPattern.evaluateNowPattern(value).getTime();
        formik.setFieldValue(field, roundMsToNearestSecond(t));
      }
    }
  };

  return loading ? (
    <Loading />
  ) : (
    <Form className="fermenter-management" onBlur={handleFormBlur} onSubmit={formik.handleSubmit}>
      <Toast
        className="success"
        onClose={() => setShowSuccess(false)}
        show={showSuccess}
        delay={2000}
        autohide
      >
        <Toast.Header closeButton={false}>
          <strong className="mr-auto">Changes saved</strong>
          <small></small>
        </Toast.Header>
      </Toast>
      <Toast className="error" onClose={onCloseErrorToast} show={showError}>
        <Toast.Header>
          <strong className="mr-auto">Error</strong>
          <small></small>
        </Toast.Header>
        <Toast.Body>{errorMessage}</Toast.Body>
      </Toast>
      <Form.Group controlId="formName">
        <Form.Label>Gyle name</Form.Label>
        <Form.Control type="text" {...formik.getFieldProps('formName')} />
        {formik.errors.formName ? (
          <Form.Text className="text-error">{formik.errors.formName}</Form.Text>
        ) : null}
      </Form.Group>

      <Form.Group controlId="formDtStarted">
        <Form.Label>Started</Form.Label>
        <Row>
          <Col>
            <Form.Control
              type="text"
              placeholder="ms since epoch"
              {...formik.getFieldProps('formDtStarted')}
            />
            {formik.errors.formDtStarted ? (
              <Form.Text className="text-error">{formik.errors.formDtStarted}</Form.Text>
            ) : null}
            <Form.Text className="text-muted">
              When temperature control should start.<br></br>Typically reset (to 'now') when yeast
              pitched or when signs of fermentation first detected.
            </Form.Text>
          </Col>
          <Col>
            <Button variant="secondary" type="button" onClick={handleDtStartedNow}>
              Now
            </Button>
          </Col>
        </Row>
      </Form.Group>

      <Form.Group controlId="formDtEnded">
        <Form.Label>Ended</Form.Label>
        <Row>
          <Col>
            <Form.Control
              type="text"
              placeholder="ms since epoch"
              {...formik.getFieldProps('formDtEnded')}
            />
            {formik.errors.formDtEnded ? (
              <Form.Text className="text-error">{formik.errors.formDtEnded}</Form.Text>
            ) : null}
            <Form.Text className="text-muted">
              When temperature control no longer required.<br></br>Can be left blank until known.
            </Form.Text>
          </Col>
          <Col>
            <Button variant="secondary" type="button" onClick={handleDtEndedNow}>
              Now
            </Button>
          </Col>
        </Row>
      </Form.Group>

      {/* For the time being, don't display the "Mode" field since this feature
          is unproven and of dubious usefulness. */}
      {/* <Form.Group controlId="formMode">
        <Form.Label>Mode</Form.Label>
        <Form.Control as="select" {...formik.getFieldProps('formMode')}>
          <option value={Mode.Auto}>Auto</option>
          <option value={Mode.Hold}>Hold</option>
          <option value={Mode.DisableHeater}>Disable heater</option>
          <option value={Mode.DisableFridge}>Disable fridge</option>
          <option value={Mode.MonitorOnly}>Monitor only</option>
        </Form.Control>
      </Form.Group> */}

      <Row>
        <Col>
          <Button variant="primary" type="submit" disabled={!isAdmin || formik.isSubmitting}>
            Update
          </Button>
        </Col>
      </Row>
    </Form>
  );
};

export default FermenterManagement;
