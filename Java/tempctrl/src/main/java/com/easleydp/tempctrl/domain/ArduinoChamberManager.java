package com.easleydp.tempctrl.domain;

import static java.lang.Double.*;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

public class ArduinoChamberManager implements ChamberManager
{
    private static final Logger logger = LoggerFactory.getLogger(ArduinoChamberManager.class);

    private static final char DELIM_CHAR = ',';
    private static final String DELIM = "" + DELIM_CHAR;

    private ArduinoMessenger messenger = null;  // Acquired lazily, so we can retry if initially not found

    private Map<Integer, ChamberParameters> chamberParametersByChamberId = new HashMap<>();
    private ChamberRepository chamberRepository;


    public ArduinoChamberManager(ChamberRepository chamberRepository)
    {
        this.chamberRepository = chamberRepository;
    }

    @Override
    public void setParameters(int chamberId, ChamberParameters params)
    {
        chamberParametersByChamberId.put(chamberId, params);
        try
        {
            getMessenger().sendRequest("setParams:" + csv(chamberId, params.tTarget, params.tTargetNext, params.tMin, params.tMax,
                    params.hasHeater ? 1 : 0, params.Kp, params.Ki, params.Kd));
            // Examples for console test:
            //  ^setParams:1,171,172,-10,400,1,2.1,0.01,20.5$
            //  ^setParams:2,100,100,-10,150,0,1.9,0.015,19.5$
            getMessenger().expectResponse("ack");
        }
        catch (IOException e)
        {
            handleIOException(e);
        }
    }

    @Override
    public ChamberReadings getReadings(int chamberId, Date timeNow)
    {
        // Arduino app currently doesn't cope with delivering readings unless ChamberParameters have
        // been set, so we start by sending a fresh set of parameters (including latest tTargetNext).
        Gyle activeGyle = chamberRepository.getChamberById(chamberId).getActiveGyle();
        if (activeGyle == null)
            throw new IllegalStateException("No active gyle for chamberId " + chamberId);
        ChamberParameters params = activeGyle.getChamberParameters(timeNow);
        setParameters(chamberId, params);

        try
        {
            getMessenger().sendRequest("getChmbrRds:" + chamberId);
            String response = getMessenger().getResponse("chmbrRds:");
            String[] values = response.split(",");
            // Expecting:
            // tTarget,tTargetNext,tMin,tMax,tBeer,tExternal,tChamber,tPi,heaterOutput,coolerOn(0|1),mode
            if (values.length != 15)
            {
                throw new IOException("Unexpected 'chmbrRds' response: " + response);
            }
            int i = 0;

            // Params (for consistency check)
            int tTarget = parseInt(values[i++]);
            int tTargetNext = parseInt(values[i++]);
            int tMin = parseInt(values[i++]);
            int tMax = parseInt(values[i++]);
            boolean hasHeater = parseBool(values[i++]);
            double Kp = parseDouble(values[i++]);
            double Ki = parseDouble(values[i++]);
            double Kd = parseDouble(values[i++]);

            // Readings
            int tBeer = parseInt(values[i++]);
            int tExternal = parseInt(values[i++]);
            int tChamber = parseInt(values[i++]);
            int tPi = parseInt(values[i++]);
            int heaterOutput = parseInt(values[i++]);
            boolean coolerOn = parseBool(values[i++]);
            Mode mode = Mode.get(values[i++]);

            // Check consistency of params
            if (params.tTarget != tTarget)
                logChamberParamMismatchError(chamberId, "tTarget", params.tTarget, tTarget);
            if (params.tTargetNext != tTargetNext)
                logChamberParamMismatchError(chamberId, "tTargetNext", params.tTargetNext, tTargetNext);
            if (params.tMin != tMin)
                logChamberParamMismatchError(chamberId, "tMin", params.tMin, tMin);
            if (params.tMax != tMax)
                logChamberParamMismatchError(chamberId, "tMax", params.tMax, tMax);
            if (params.hasHeater != hasHeater)
                logChamberParamMismatchError(chamberId, "hasHeater", params.hasHeater, tMax);
            // Deliberately not consistency checking the floating point values due to likelihood of rounding errors.

            return new ChamberReadings(timeNow, tTarget, tBeer, tExternal, tChamber, tPi,
                    heaterOutput, coolerOn, mode, new ChamberParameters(tTarget, tTargetNext, tMin, tMax, hasHeater, Kp, Ki, Kd));
        }
        catch (IOException e)
        {
            handleIOException(e);
            return null;
        }
    }

    private static void logChamberParamMismatchError(int chamberId, String paramName, Object expected, Object actual)
    {
        logger.error("Chamber params mismatch: Chamber " + chamberId + " " + paramName + " should be " + expected + " but is " + actual);
    }

    private ArduinoMessenger getMessenger()
    {
        if (messenger == null)
            messenger = new ArduinoMessenger();  // can throw
        return messenger;
    }

    private void handleIOException(IOException e)
    {
        if (messenger != null)
        {
            messenger.close();
            messenger = null;
        }
        logger.error(e.getMessage());
    }


    private static String csv(Object... values)
    {
        return Joiner.on(DELIM).join(values);
    }

    private static int parseInt(String str)
    {
        return Integer.parseInt(str, 10);
    }
    private static boolean parseBool(String str)
    {
        return "1".equals(str) || "T".equals(str);
    }

}
