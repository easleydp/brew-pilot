import './EmailTest.scss';
import React, { useEffect, useState } from 'react';
import { useHistory, useLocation } from 'react-router-dom';
import ILocationState from '../api/ILocationState';
import { useAppState, Auth } from './state';
import Utils from '../util/Utils';
import axios from 'axios';
import { Form, Button, Row, Col } from 'react-bootstrap';
import { useFormik /*, yupToFormErrors */} from 'formik';
import * as Yup from 'yup';
import Toast from 'react-bootstrap/Toast';
import Loading from './Loading';

// interface IErrors {
//   formName?: string;
//   formDtStarted?: string;
//   formDtEnded?: string;
// }
// interface IValues {
//   formSubject?: string;
//   formText?: string;
//   formNoRetry?: boolean;
// }

const EmailTest = () => {
  const history = useHistory<ILocationState>();
  const location = useLocation<ILocationState>();
  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;
  const isLoggedIn = isAuth === Auth.LoggedIn;
  const isAdmin = isLoggedIn && state.isAdmin;

  const [loading, setLoading] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [showSuccess, setShowSuccess] = useState(false);
  const [showError, setShowError] = useState(false);

  const formik = useFormik({
    initialValues: {
      formSubject: '',
      formText: '',
      formNoRetry: false,
    },
    // validate,
    validationSchema: Yup.object({
      formSubject: Yup.string().required('Required'),
    }),
    onSubmit: (values, { setSubmitting }) => {
      setSubmitting(true);
      const url = '/tempctrl/admin/email-test';
      axios
        .post(url, {
          subject: values.formSubject,
          text: values.formText,
          noRetry: values.formNoRetry,
        })
        .then((/*response*/) => {
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

  const onCloseErrorToast = () => {
    setShowError(false);
    setErrorMessage(null);
    formik.setSubmitting(false);
  };

  useEffect(() => {
    console.info(
      Auth[isAuth],
      '=================== EmailTest useEffect invoked ======================'
    );

    const buildForm = () => {
      formik.setFieldValue('formSubject', 'Test subject');
      formik.setFieldValue('formText', 'Test message text');
      formik.setFieldValue('formNoRetry', false);
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
      setLoading(false);
      buildForm();
    }
  }, [dispatch, history, isAuth, location.pathname]); // formik deliberately not included

  return loading ? (
    <Loading />
  ) : (
    <Form className="email-test" onSubmit={formik.handleSubmit}>
      <h1>Email test</h1>
      <Toast
        className="success"
        onClose={() => setShowSuccess(false)}
        show={showSuccess}
        delay={2000}
        autohide
      >
        <Toast.Header closeButton={false}>
          <strong className="mr-auto">Email sent</strong>
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
      <Form.Group controlId="formSubject">
        <Form.Label>Subject</Form.Label>
        <Form.Control type="text" {...formik.getFieldProps('formSubject')} />
        {formik.errors.formSubject ? (
          <Form.Text className="text-error">{formik.errors.formSubject}</Form.Text>
        ) : null}
      </Form.Group>
      <Form.Group controlId="formText">
        <Form.Label>Message text</Form.Label>
        <Form.Control as="textarea" rows={5} {...formik.getFieldProps('formText')} />
        {formik.errors.formText ? (
          <Form.Text className="text-error">{formik.errors.formText}</Form.Text>
        ) : null}
      </Form.Group>
      <Form.Group controlId="formNoRetry">
        <Form.Label>No retry</Form.Label>
        <Form.Control
          type="checkbox"
          className="formNoRetry"
          {...formik.getFieldProps('formNoRetry')}
        />
        {formik.errors.formNoRetry ? (
          <Form.Text className="text-error">{formik.errors.formNoRetry}</Form.Text>
        ) : null}
      </Form.Group>

      <Row>
        <Col>
          <Button variant="primary" type="submit" disabled={!isAdmin || formik.isSubmitting}>
            Send
          </Button>
        </Col>
      </Row>
    </Form>
  );
};

export default EmailTest;
