import './GyleManagement.scss';
import React, { useEffect, useState } from 'react';
import { Route, Router, useHistory, useLocation } from 'react-router-dom';
import ILocationState from '../api/ILocationState';
import { useAppState, Auth } from './state';
import { useParams } from 'react-router-dom';
import IGyle from '../api/IGyle';
import ITemperatureProfile from '../api/ITemperatureProfile';
import { Mode } from '../api/Mode';
import NowPattern from '../util/NowPattern';
import Utils from '../util/Utils';
import axios from 'axios';
import { Form, Button, Row, Col } from 'react-bootstrap';
import { useFormik /*, yupToFormErrors */ } from 'formik';
import * as Yup from 'yup';
import Toast from 'react-bootstrap/Toast';
import Loading from './Loading';

interface IErrors {
  formName?: string;
  // formDtStartedOld?: string;
  // formDtEndedOld?: string;
  formDtStarted?: string;
  formDtEnded?: string;
}
interface IValues {
  formName?: string;
  // formDtStartedOld?: string;
  // formDtEndedOld?: string;
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

const GyleManagement = () => {
  const history = useHistory<ILocationState>();
  const location = useLocation<ILocationState>();
  const { state, dispatch } = useAppState();
  const isAuth = state && state.isAuth;
  const isLoggedIn = isAuth === Auth.LoggedIn;
  const isAdmin = isLoggedIn && state.isAdmin;

  const { chamberId } = useParams<{ chamberId: string }>();

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

  const onClickCreateNextGyle = () => {
    if (gyle?.dtStarted && !gyle?.dtEnded) {
      setErrorMessage('A new gyle cannot be created while this gyle is still active.');
      formik.setSubmitting(true);
      setShowError(true);
      formik.setSubmitting(false);
    } else {
      history.push({ pathname: `/create-gyle/${chamberId}` });
    }
};

  const reDateTime = /^(\d{4}|\d{2})[-/]([01]?\d)[-/]([0123]?\d)\s([012]?\d)[:.]([012345]?\d)$/;

  const dateTimeStrIsValid = (dateTimeStr: string | null | undefined): dateTimeStr is string => {
    return !!dateTimeStr && reDateTime.test(dateTimeStr);
  };

  const dateTimeStrToMillis = (dateTimeStr: string) => {
    const match = dateTimeStr.match(reDateTime);
    if (!match) throw Error('Invalid date/time string: ' + dateTimeStr);
    const year = match[1].length === 2 ? parseInt(match[1]) + 2000 : parseInt(match[1]);
    const date = new Date(
      year,
      parseInt(match[2]) - 1, // month
      parseInt(match[3]), // day of month
      parseInt(match[4]), // hours
      parseInt(match[5]) // minutes
    );
    return date.getTime();
  };

  const millisToDateTimeStr = (ms: number) => {
    const parts = new Intl.DateTimeFormat(['en'], {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).formatToParts(new Date(ms));
    const part = (type: string) => {
      return parts.find((part) => part.type === type)?.value;
    };
    return `${part('year')}-${part('month')}-${part('day')} ${part('hour')}:${part('minute')}`;
  };

  const formik = useFormik({
    initialValues: {
      formName: '',
      // formDtStartedOld: '',
      // formDtEndedOld: '',
      formDtStarted: '',
      formDtEnded: '',
      formTemperatureProfile: '',
      formMode: Mode.Auto,
    },
    // validate,
    validationSchema: Yup.object({
      formName: Yup.string().required('Required'),
      // formDtStartedOld: Yup.number()
      //   .typeError('Must be an integer')
      //   .integer('Must be an integer')
      //   .min(0) // Jan 01 1970 00:00:00 UTC
      //   .max(2000000000000), // May 18 2033 03:33:20 UTC
      // formDtEndedOld: Yup.number()
      //   .typeError('Must be an integer')
      //   .integer('Must be an integer')
      //   .min(1580000000000) // Jan 26 2020 00:53:20 UTC
      //   .max(2000000000000) // May 18 2033 03:33:20 UTC
      //   .test('', 'EndedOld cannot be specified without StartedOld being set', function (val) {
      //     return !val || this.parent['formDtStartedOld'];
      //   })
      //   .test('', 'EndedOld must be later than StartedOld', function (val) {
      //     return !val || val > this.parent['formDtStartedOld'];
      //   }),
      formDtStarted: Yup.string().matches(reDateTime, 'Format must be: YYYY-MM-DD hh:mm'),
      formDtEnded: Yup.string()
        .matches(reDateTime, 'Format must be: YYYY-MM-DD hh:mm')
        .test('', 'End time cannot be specified without Start time being set', function (val) {
          return !val || this.parent['formDtStarted'];
        })
        .test('', 'End time must be later than Start time', function (val) {
          var millis = val && dateTimeStrIsValid(val) ? dateTimeStrToMillis(val) : undefined;
          return !millis || millis > dateTimeStrToMillis(this.parent['formDtStarted']);
        }),
      formTemperatureProfile: Yup.string()
        .test({
          test(val) {
            if (val) {
              const lines = val.split(/\r?\n|\r|\n/g);
              let lastHours = Number.MIN_SAFE_INTEGER;
              for (let i = 0; i < lines.length; i++) {
                const trimmedLine = lines[i].trim();
                // Ignore blank lines (while maintaining correct line numbering in eror messages)
                if (!trimmedLine.length) continue;
                const match = trimmedLine.match(/^(\d+)\s*,\s*(-?\d+)\s*,?$/); // note: we tolerate a trailing comma
                if (!match) {
                  return this.createError({ message: `Line ${i + 1} is invalid: ${trimmedLine}` });
                }
                const hours = parseInt(match[1], 10);
                if (lastHours === Number.MIN_SAFE_INTEGER && hours !== 0) {
                  return this.createError({ message: `First profile point's hoursSinceStart must be 0 (rather than ${hours})`});
                }
                if (hours <= lastHours) {
                  return this.createError({ message: `Line ${i + 1}'s hoursSinceStart (${hours}) must be greater than that of the previous line (${lastHours})` });
                }
                lastHours = hours;
              }
            }
            return true;
          }
        }),
    }),
    onSubmit: (values, { setSubmitting }) => {
      const temperatureProfileTextToJson = (text: string): ITemperatureProfile => {
        const wsStrippedLines = text.split(/\r?\n|\r|\n/g)
          // Strip whitespace
          .map(line => line.replace(/\s+/g, ''))
          // Ignore any blank lines
          .filter(line => line.length);
        return {points: wsStrippedLines.map(line => {
          const wsStrippedLine = line.replace(/\s+/g, '');
          const match = wsStrippedLine.match(/^(\d+),(-?\d+),?$/); // note: we tolerate a trailing comma
          // Should have been validated already so we expect it to match
          if (!match) throw Error('Invalid pre-validated temperature profile line: ' + line);
          return {hoursSinceStart: parseInt(match[1], 10), targetTemp: parseInt(match[2], 10)};
        })};
      };
      gyle!.name = values.formName;
      // gyle!.dtStartedOld = values.formDtStartedOld ? parseInt(values.formDtStartedOld) : undefined;
      // gyle!.dtEndedOld = values.formDtEndedOld ? parseInt(values.formDtEndedOld) : undefined;
      gyle!.dtStarted = values.formDtStarted
        ? dateTimeStrToMillis(values.formDtStarted)
        : undefined;
      gyle!.dtEnded = values.formDtEnded ? dateTimeStrToMillis(values.formDtEnded) : undefined;
      // gyle!.mode = values.formMode; Commented-out in sympathy with form.
      // gyle.mode is currently returned as we received it
      gyle!.temperatureProfile = temperatureProfileTextToJson(values.formTemperatureProfile);
      setSubmitting(true);
      const url = '/tempctrl/admin/chamber/' + chamberId + '/latest-gyle';
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

  // const truncMsToMinute = (ms: number) => {
  //   const minMillis = 1000 * 60;
  //   return Math.trunc(ms / minMillis) * minMillis;
  // };

  // const handleDtStartedOldNow = () => {
  //   setFieldOldNow('formDtStartedOld');
  // };
  // const handleDtEndedOldNow = () => {
  //   setFieldOldNow('formDtEndedOld');
  // };
  // const setFieldOldNow = (field: string) => {
  //   formik.setFieldValue(field, truncMsToMinute(Date.now()));
  // };

  const handleDtStartedNow = () => {
    setFieldNow('formDtStarted');
  };
  const handleDtEndedNow = () => {
    setFieldNow('formDtEnded');
  };
  const setFieldNow = (field: string) => {
    formik.setFieldValue(field, millisToDateTimeStr(Date.now()));
  };

  const handleFormBlur = () => {
    // // If either of the two old time input fields looks like a 'now' pattern, convert to ms
    // checkForOldNowPattern('formDtStartedOld');
    // checkForOldNowPattern('formDtEndedOld');

    // If either of the two time input fields looks like a 'now' pattern, convert to date/time string
    checkForNowPattern('formDtStarted');
    checkForNowPattern('formDtEnded');
  };

  // const checkForOldNowPattern = (field: string) => {
  //   let value = formik.getFieldProps(field).value;
  //   if (value) {
  //     value = '' + value; // Could be integer; normalise to string
  //     if (NowPattern.isNowPattern(value)) {
  //       const t = NowPattern.evaluateNowPattern(value).getTime();
  //       formik.setFieldValue(field, truncMsToMinute(t));
  //     }
  //   }
  // };
  const checkForNowPattern = (field: string) => {
    let value = formik.getFieldProps(field).value;
    if (value) {
      if (NowPattern.isNowPattern(value)) {
        const t = NowPattern.evaluateNowPattern(value).getTime();
        formik.setFieldValue(field, millisToDateTimeStr(t));
      }
    }
  };

  useEffect(() => {
    console.info(
      Auth[isAuth],
      '=================== GyleManagement useEffect invoked ======================'
    );

    // Returns promise for retrieving IGyle
    const getGyle = (): Promise<IGyle> => {
      const url = '/tempctrl/guest/chamber/' + chamberId + '/latest-gyle';
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

    const temperatureProfileJsonToText = (tp: ITemperatureProfile) => {
      return tp.points.map(pt => pt.hoursSinceStart + ', ' + pt.targetTemp).join('\n');
    };

    const buildForm = (gyle: IGyle) => {
      formik.setFieldValue('formName', gyle.name);
      // formik.setFieldValue('formDtStartedOld', gyle.dtStarted || '');
      // formik.setFieldValue('formDtEndedOld', gyle.dtEnded || '');

      formik.setFieldValue(
        'formDtStarted',
        gyle.dtStarted || gyle.dtStarted === 0 ? millisToDateTimeStr(gyle.dtStarted) : ''
      );

      formik.setFieldValue(
        'formDtEnded',
        gyle.dtEnded || gyle.dtEnded === 0 ? millisToDateTimeStr(gyle.dtEnded) : ''
      );

      formik.setFieldValue(
        'formTemperatureProfile',
        gyle.temperatureProfile ? temperatureProfileJsonToText(gyle.temperatureProfile) : ''
      );

      formik.setFieldValue('formMode', gyle.mode || Mode.Auto);
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
      getGyle().then((gyle) => {
        setLoading(false);
        setGyle(gyle);
        buildForm(gyle);
      });
    }
  }, [dispatch, history, isAuth, location.pathname, chamberId]); // formik deliberately not included

  return loading ? (
    <Loading />
  ) : (
    <Form className="gyle-management" onBlur={handleFormBlur} onSubmit={formik.handleSubmit}>
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
      <Form.Group controlId="formName" className="gyle-name">
        <Form.Label>Gyle name</Form.Label>
        <Form.Control type="text" {...formik.getFieldProps('formName')} />
        {formik.errors.formName ? (
          <Form.Text className="text-error">{formik.errors.formName}</Form.Text>
        ) : null}
      </Form.Group>

      {/* <Form.Group controlId="formDtStartedOld">
        <Form.Label>Started old</Form.Label>
        <Row>
          <Col>
            <Form.Control
              type="text"
              placeholder="ms since epoch"
              {...formik.getFieldProps('formDtStartedOld')}
            />
            {formik.errors.formDtStartedOld ? (
              <Form.Text className="text-error">{formik.errors.formDtStartedOld}</Form.Text>
            ) : null}
            <Form.Text className="text-muted">
              When temperature control should start.<br></br>Typically reset (to 'now') when yeast
              pitched or when signs of fermentation first detected.
            </Form.Text>
          </Col>
          <Col>
            <Button variant="secondary" type="button" onClick={handleDtStartedOldNow}>
              Now
            </Button>
          </Col>
        </Row>
      </Form.Group>

      <Form.Group controlId="formDtEndedOld">
        <Form.Label>Ended old</Form.Label>
        <Row>
          <Col>
            <Form.Control
              type="text"
              placeholder="ms since epoch"
              {...formik.getFieldProps('formDtEndedOld')}
            />
            {formik.errors.formDtEndedOld ? (
              <Form.Text className="text-error">{formik.errors.formDtEndedOld}</Form.Text>
            ) : null}
            <Form.Text className="text-muted">
              When temperature control no longer required.<br></br>Can be left blank until known.
            </Form.Text>
          </Col>
          <Col>
            <Button variant="secondary" type="button" onClick={handleDtEndedOldNow}>
              Now
            </Button>
          </Col>
        </Row>
      </Form.Group> */}

      <Form.Group controlId="formDtStarted" className="date-time">
        <Form.Label>Start time</Form.Label>
        <Row>
          <Col>
            <Form.Control
              type="text"
              placeholder="YYYY-MM-DD hh:mm"
              {...formik.getFieldProps('formDtStarted')}
            />
            <Button variant="secondary" type="button" onClick={handleDtStartedNow}>
              Now
            </Button>
            {formik.errors.formDtStarted ? (
              <Form.Text className="text-error">{formik.errors.formDtStarted}</Form.Text>
            ) : null}
            <Form.Text className="text-muted">
              {/* When temperature control should start. */}
              When fermentation started / is expected to start.<br></br>Note:
              <ul>
                <li>While blank the chamber will be inactive.</li>
                <li>
                  Can be set to a future date/time. In this case the chamber{' '}
                  <strong>will be activated forthwith</strong> and held at the profile's start
                  temperature until the start time.
                </li>
                <li>If setting in advance,
                  set it to your best guess initially (e.g. 24hrs after yeast pitch) then reset
                  once signs of fermentation are detected.</li>
              </ul>
            </Form.Text>
          </Col>
        </Row>
      </Form.Group>

      <Form.Group controlId="formDtEnded" className="date-time">
        <Form.Label>End time</Form.Label>
        <Row>
          <Col>
            <Form.Control
              type="text"
              placeholder="YYYY-MM-DD hh:mm"
              {...formik.getFieldProps('formDtEnded')}
            />
            <Button variant="secondary" type="button" onClick={handleDtEndedNow}>
              Now
            </Button>
            {formik.errors.formDtEnded ? (
              <Form.Text className="text-error">{formik.errors.formDtEnded}</Form.Text>
            ) : null}
            <Form.Text className="text-muted">
              When temperature control no longer required.<br></br>Note:
              <ul>
                <li>
                  Can be left blank until known; when the temperature profile has completed, the
                  chamber will be held at the last set temperature.
                </li>
                <li>The chamber will be deactivated when the specified date/time is reached.</li>
              </ul>
            </Form.Text>
          </Col>
        </Row>
      </Form.Group>

      <Form.Group controlId="formTemperatureProfile" className="temperature-profile">
        <Form.Label>Temperature profile</Form.Label>
        <Row>
          <Col>
            <Form.Control
              as="textarea"
              rows={9}
              {...formik.getFieldProps('formTemperatureProfile')}
            />
            {formik.errors.formTemperatureProfile ? (
              <Form.Text className="text-error">{formik.errors.formTemperatureProfile}</Form.Text>
            ) : null}
            <Form.Text className="text-muted">
              One line per profile point, each consisting of two integers separated by a comma:
              <ol>
                <li>Hours since start (must be zero for the first profile point),</li>
                <li>Target temperature x10 (e.g. 175 for 17.5°C).</li>
              </ol>
            </Form.Text>
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
        <Col>
          <Button variant="secondary" type="button" onClick={onClickCreateNextGyle} disabled={!isAdmin || formik.isSubmitting}>
            Create next gyle...
          </Button>
        </Col>
      </Row>
    </Form>
  );
};

export default GyleManagement;
