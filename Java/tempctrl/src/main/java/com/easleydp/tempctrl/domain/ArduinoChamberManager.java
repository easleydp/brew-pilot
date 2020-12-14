package com.easleydp.tempctrl.domain;

import static java.lang.Double.*;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.fazecast.jSerialComm.SerialPortTimeoutException;
import com.google.common.base.Joiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interfaces to an Arduino microcontroller
 */
public class ArduinoChamberManager implements ChamberManager
{
    private static final Logger logger = LoggerFactory.getLogger(ArduinoChamberManager.class);
    private static final Logger arduinoLogger = LoggerFactory.getLogger("arduino");

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
    public ChamberManagerStatus getChamberManagerStatus() throws IOException
    {
        getMessenger().sendRequest("status");
        String response = getMessenger().getResponse("status:");
        String[] values = response.split(",");
        // Expecting:
        // uptimeMins,minFreeRam,badSensorCount,logDataEjected
        if (values.length != 4)
            throw new IOException("Unexpected 'status' response: " + response);
        int i = 0;
        return new ChamberManagerStatus(
                parseInt(values[i++]),
                parseInt(values[i++]),
                parseInt(values[i++]),
                parseBool(values[i++]));
    }

    @Override
    public void setParameters(int chamberId, ChamberParameters params) throws IOException
    {
        chamberParametersByChamberId.put(chamberId, params);

        getMessenger().sendRequest("setChParams:" + csv(chamberId, params.gyleAgeHours, params.tTarget, params.tTargetNext, params.tMin, params.tMax,
                params.hasHeater ? 1 : 0,
                params.fridgeMinOnTimeMins, params.fridgeMinOffTimeMins, params.fridgeSwitchOnLagMins,
                params.Kp, params.Ki, params.Kd, params.mode));
        // Examples for console test:
        //  ^setChParams:1,12,171,172,-10,400,1,10,10,5,2.1,0.01,20.5,A$
        //  ^setChParams:2,-1,100,100,-10,150,0,10,10,5,1.9,0.015,19.5,H$
        getMessenger().expectResponse("ack");
    }

    @Override
    public ChamberReadings getReadings(int chamberId, Date timeNow) throws IOException
    {
        getMessenger().sendRequest("getChRds:" + chamberId);
        String response = getMessenger().getResponse("chRds:");
        logger.debug("Raw chRds:" + response);
        String[] values = response.split(",");
        // Expecting:
        // gyleAgeHours,tTarget,tTargetNext,tMin,tMax,hasHeater,fridgeMinOnTimeMins,fridgeMinOffTimeMins,fridgeSwitchOnLagMins,Kp,Ki,Kd,mode,tBeer,tChamber,tExternal,tPi,heaterOutput,fridgeOn
        final int expectedValueCount = 19;
        if (values.length != expectedValueCount)
        {
            throw new IOException("Unexpected 'chRds' response (" + values.length + " values): " + response);
        }
        int i = 0;

        // Params (for consistency check)
        int gyleAgeHours = parseInt(values[i++]);
        int tTarget = parseInt(values[i++]);
        int tTargetNext = parseInt(values[i++]);
        int tMin = parseInt(values[i++]);
        int tMax = parseInt(values[i++]);
        boolean hasHeater = parseBool(values[i++]);
        int fridgeMinOnTimeMins = parseInt(values[i++]);
        int fridgeMinOffTimeMins = parseInt(values[i++]);
        int fridgeSwitchOnLagMins = parseInt(values[i++]);
        double Kp = parseDouble(values[i++]);
        double Ki = parseDouble(values[i++]);
        double Kd = parseDouble(values[i++]);
        Mode mode = Mode.get(values[i++]);

        // Readings
        int tBeer = parseInt(values[i++]);
        int tChamber = parseInt(values[i++]);
        int tExternal = parseInt(values[i++]);
        int tPi = parseInt(values[i++]);
        int heaterOutput = parseInt(values[i++]);
        boolean fridgeOn = parseBool(values[i++]);

        if (i != expectedValueCount)
        {
            throw new IllegalStateException("Should have read " + expectedValueCount + " values, actually read " + i);
        }

        // Check consistency of params
        {
            Gyle latestGyle = chamberRepository.getChamberById(chamberId).getLatestGyle();
            if (latestGyle == null  ||  !latestGyle.isActive())
                throw new IllegalStateException("No active gyle for chamberId " + chamberId);
            ChamberParameters params = latestGyle.getChamberParameters(timeNow);

            if (params.gyleAgeHours != gyleAgeHours)
                logChamberParamMismatchError(chamberId, "gyleAgeHours", params.gyleAgeHours, gyleAgeHours);
            if (params.tTarget != tTarget)
                logChamberParamMismatchError(chamberId, "tTarget", params.tTarget, tTarget);
            if (params.tTargetNext != tTargetNext)
                logChamberParamMismatchError(chamberId, "tTargetNext", params.tTargetNext, tTargetNext);
            if (params.tMin != tMin)
                logChamberParamMismatchError(chamberId, "tMin", params.tMin, tMin);
            if (params.tMax != tMax)
                logChamberParamMismatchError(chamberId, "tMax", params.tMax, tMax);
            if (params.hasHeater != hasHeater)
                logChamberParamMismatchError(chamberId, "hasHeater", params.hasHeater, hasHeater);
            if (params.fridgeMinOnTimeMins != fridgeMinOnTimeMins)
                logChamberParamMismatchError(chamberId, "fridgeMinOnTimeMins", params.fridgeMinOnTimeMins, fridgeMinOnTimeMins);
            if (params.fridgeMinOffTimeMins != fridgeMinOffTimeMins)
                logChamberParamMismatchError(chamberId, "fridgeMinOffTimeMins", params.fridgeMinOffTimeMins, fridgeMinOffTimeMins);
            if (params.fridgeSwitchOnLagMins != fridgeSwitchOnLagMins)
                logChamberParamMismatchError(chamberId, "fridgeSwitchOnLagMins", params.fridgeSwitchOnLagMins, fridgeSwitchOnLagMins);
              if (params.mode != mode)
                logChamberParamMismatchError(chamberId, "mode", params.mode, mode);
            // Deliberately not consistency checking the floating point values due to likelihood of rounding errors.
        }

        return new ChamberReadings(timeNow, tTarget, tBeer, tExternal, tChamber, tPi,
                heaterOutput, fridgeOn, mode,
                new ChamberParameters(gyleAgeHours, tTarget, tTargetNext, tMin, tMax, hasHeater,
                    fridgeMinOnTimeMins, fridgeMinOffTimeMins, fridgeSwitchOnLagMins, Kp, Ki, Kd, mode));
    }

    @Override
    public void slurpLogMessages() throws IOException
    {
        ArduinoMessenger messenger = getMessenger();
        try
        {
            messenger.sendRequest("getLogMsgs");
            while (true)
            {
                String logMessage = messenger.getResponse("logMsg:", "ack");
                if (logMessage == null)
                    break;
                logLogMessage(logMessage);
            }
        }
        catch (Throwable t)
        {
            if (messenger.getRequestCount() == 1  &&  t instanceof SerialPortTimeoutException)
            {
                // Read timeout seems to happen on first slurp. Allowing the exception to propagate would cause serial connection to MCU to
                // be closed. MCU would then restart when next opened, which would put us back to where we started (kind of an infinite loop).
                logger.debug("First read operation timed out in slurpLogMessages()");
            }
            else
            {
                throw t;
            }
        }
    }

    @Override
    public void handleIOException(IOException e)
    {
        if (messenger != null)
        {
            logger.info("Closing ArduinoMessenger after IOException");
            messenger.close();
            messenger = null;
        }
    }

    private static void logLogMessage(String logMessage) throws IOException
    {
        String[] values = logMessage.split(",");
        // Expecting:
        // sequenceNum,logLevel,prefix,id,chamberId,buffLen,b64Buffer
        // though buffLen and b64Buffer will be absent if no binary data.
        if (values.length != 5  &&  values.length != 7)
        {
            throw new IOException("Unexpected 'logMsg' response: " + logMessage);
        }
        int i = 0;
        int sequenceNum = parseInt(values[i++]);
        int logLevel = parseInt(values[i++]);
        String prefix = values[i++];
        char id = values[i++].charAt(0);
        int chamberId = parseInt(values[i++]);

        StringBuffer sb = new StringBuffer();
        sb.append("seqNum:" + sequenceNum + "; ");
        sb.append(prefix + ":" + id + "; ");
        sb.append("chamber:" + chamberId);

        if (values.length == 7)
        {
            int buffLen = parseInt(values[i++]);
            String b64Buffer = values[i++];
            byte[] buffer = Base64.getDecoder().decode(b64Buffer);
            if (buffer.length != buffLen)
            {
                throw new IOException("Bad 'logMsg' response: " + logMessage +
                        ". Actual buffer length was " + buffer.length + " rather than " + buffLen + ".");
            }
            sb.append("; buffer: ");
            sb.append(interpretBuffer(prefix, id, buffer));
        }
        switch (logLevel)
        {
            case 0: arduinoLogger.debug(sb.toString()); break;
            case 1: arduinoLogger.info(sb.toString()); break;
            case 2: arduinoLogger.warn(sb.toString()); break;
            case 3: arduinoLogger.error(sb.toString()); break;
            default:
                arduinoLogger.error("Unrecognise log level: " + logLevel);
                arduinoLogger.error(sb.toString());
                break;
        }
    }

    private static String interpretBuffer(String prefix, char id, byte[] buffer)
    {
        switch (prefix)
        {
            case "MN":
                switch (id)
                {
                    case '0':
                        // float, uint32, int16
                        if (buffer.length != 10)
                            return "setup {error: \"Expected 10 bytes\"}";

                        int i = 0;
                        return String.format("{pi: %f, uint32: 0x%x, int16: %d}",
                                bytesToFloat(buffer[i++], buffer[i++], buffer[i++], buffer[i++]),
                                bytesToUint32(buffer[i++], buffer[i++], buffer[i++], buffer[i++]),
                                bytesToInt16(buffer[i++], buffer[i++]));
                    default:
                        break;
                }
            case "PID":
                switch (id)
                {
	                case 'X':
	                {
	                    // tErrorAdjustedForSawtooth/* int16 */, tExternalBoost/* int16 */, T_EXTERNAL_BOOST_THRESHOLD/* int16 */
	                    if (buffer.length != 4)
	                        return "{error: \"Expected 4 bytes, got " + buffer.length + "\"}";

                        int i = 0;
                        int tErrorAdjustedForSawtooth = bytesToInt16(buffer[i++], buffer[i++]);
                        int tExternalBoost = bytesToInt16(buffer[i++], buffer[i++]);
                        return String.format("Temp  {tErrorAdjustedForSawtooth: %d, tExternalBoost: %d}",
                            tErrorAdjustedForSawtooth, tExternalBoost);
                    }
	                case 'W':
	                {
	                    // integralContrib/* float */
	                    if (buffer.length != 4)
	                        return "{error: \"Expected 4 bytes\"}";

	                    int i = 0;
	                    return String.format("Reject {integralContrib: %.3f}", bytesToFloat(buffer[i++], buffer[i++], buffer[i++], buffer[i++]));
	                }
                    case '~':
                    {
                        // tError/* int16 */, cd.integral/* float */, cd.priorError/* float */
                        if (buffer.length != 10)
                            return "{error: \"Expected 10 bytes\"}";

                        int i = 0;
                        int tError = bytesToInt16(buffer[i++], buffer[i++]);
                        float integral = bytesToFloat(buffer[i++], buffer[i++], buffer[i++], buffer[i++]);
                        float priorError = bytesToFloat(buffer[i++], buffer[i++], buffer[i++], buffer[i++]);
                        return String.format("PID state variables  {tError: %d, integral: %.3f, priorError: %.3f}", tError, integral, priorError);
                    }
                    case '+': case '-': case '!':
                        // pidOutput/* float */
                        if (buffer.length != 4)
                            return "{error: \"Expected 4 bytes\"}";

                        return String.format("%s {pidOutput: %.3f}",
                                id == '+' ? "pidOutput > 100" : id == '-' ? "pidOutput" : "pidOutput < 0 (we've screwed-up somehow)",
                                bytesToFloat(buffer[0], buffer[1], buffer[2], buffer[3]));
                    case 'C':
                    {
                        // PID output Components, 3 floats */
                        if (buffer.length != 12)
                            return "{error: \"Expected 12 bytes\"}";

                        int i = 0;
                        float c1 = bytesToFloat(buffer[i++], buffer[i++], buffer[i++], buffer[i++]);
                        float c2 = bytesToFloat(buffer[i++], buffer[i++], buffer[i++], buffer[i++]);
                        float c3 = bytesToFloat(buffer[i++], buffer[i++], buffer[i++], buffer[i++]);
                        return String.format("PID output components  {Kp*tError: %.3f, Ki*integral: %.3f, Kd*(tError-priorError): %.3f}", c1, c2, c3);
                    }
                    case 'd':
                        // tBeerLastDelta/* int8 */
                        if (buffer.length != 1)
                            return "{error: \"Expected 1 byte\"}";

                        return String.format("Decay {tBeerLastDelta: %d}", buffer[0]);
                    case 'D':
                    {
                        // integral/* float */
                        if (buffer.length != 4)
                            return "{error: \"Expected 4 bytes\"}";

                        int i = 0;
                        return String.format("Decay {integral: %.3f}", bytesToFloat(buffer[i++], buffer[i++], buffer[i++], buffer[i++]));
                    }
                    default:
                        break;
                }
            case "CD":
                switch (id)
                {
                    case 'p': case 'P':
                        if (buffer.length != 0)
                            return "{error: \"Expected 0 bytes\"}";

                        return "getEepromChamberParams " + (id == 'p' ? "good" : "bad");
                    case 't': case 'T':
                        if (buffer.length != 0)
                            return "{error: \"Expected 0 bytes\"}";

                        return "getEepromMovingChamberParams " + (id == 't' ? "good" : "bad");
                    case '0':
                        // tTarget/* int16_t */, mode/* char */
                        if (buffer.length != 3)
                            return "{error: \"Expected 3 bytes\"}";

                        return String.format("setChamberParams {tTarget: %d, mode: %c}",
                                bytesToInt16(buffer[0], buffer[1]), buffer[2]);
                    case '1': case '2':
                        return String.format("%s {tTarget: %d, gyleAgeHours: %d}",
                                id == '1' ? "saveMovingChamberParams" : "saveMovingChamberParamsOnceInAWhile");
                    default:
                        break;
                }
            case "CC":
                switch (id)
                {
                    case 'F': case 'f':
                        // fridgeStateChangeMins|level/* uint8_t */
                        if (buffer.length != 1)
                            return "{error: \"Expected 1 byte\"}";

                        return String.format("%s {fridgeStateChangeMins: %d}", id == 'F' ? "fridge ON" : "fridge OFF", buffer[0]);
                    case 'H':
                        // outputLevel/* uint8_t */
                        if (buffer.length != 1)
                            return "{error: \"Expected 1 byte\"}";

                        return String.format("{heater output: %d}", buffer[0]);
                    case 'C':
                        // fridgeStateChangeMins/* uint8_t */, hSetting/* byte */
                        if (buffer.length != 2)
                            return "{error: \"Expected 2 bytes\"}";

                        return String.format("Heating countermanded {fridgeStateChangeMins: %d, hSetting: %d}", buffer[0], buffer[1]);
                    case 'j': case 'k':
                        // time/* uint32_t */
                        if (buffer.length != 4)
                            return "{error: \"Expected 4 bytes\"}";

                        return String.format("%s {mS: %d}",
                                id == 'j' ? "readTemperatures duration" : "readTemperatures & control duration",
                                bytesToUint32(buffer[0], buffer[1], buffer[2], buffer[3]));
                    default:
                        break;
                }
            case "T":
                switch (id)
                {
                    case 'C':
                        // badSensorCount/* uint8_t */
                        if (buffer.length != 1)
                            return "{error: \"Expected 1 byte\"}";

                        return String.format("badSensorCount {%d}", buffer[0]);
                    case 'D':
                        // sensorIndex/* uint8_t */, reading/* float */
                        if (buffer.length != 5)
                            return "{error: \"Expected 5 bytes\"}";

                        return String.format("Sensor disconnected? {sensorIndex: %d, pidOutput: %.3f}", buffer[0], bytesToFloat(buffer[1], buffer[2], buffer[3], buffer[4]));
                    default:
                        break;
                }
        }

        // Catch all - format as byte array
        return byteArrayToString(buffer);
    }

    public static boolean isPrintableChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return (!Character.isISOControl(c)) &&
                c != KeyEvent.CHAR_UNDEFINED &&
                block != null &&
                block != Character.UnicodeBlock.SPECIALS;
    }

    private static String byteArrayToString(byte[] buffer)
    {
        StringBuffer sb = new StringBuffer("[");
        for (int i = 0; i < buffer.length; i++)
        {
            if (i > 0)
                sb.append(", ");
            sb.append(String.format("%02X", buffer[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    static float bytesToFloat(byte loLo, byte loHi, byte hiLo, byte hiHi)
    {
        int intBits = hiHi << 24 | (hiLo & 0xFF) << 16 | (loHi & 0xFF) << 8 | (loLo & 0xFF);
        return Float.intBitsToFloat(intBits);
    }

    static short bytesToInt16(byte lo, byte hi)
    {
        return (short) (((hi & 0xFF) << 8) | (lo & 0xFF));
    }

    // Returning as long because Java has no `unsigned int`
    static long bytesToUint32(byte loLo, byte loHi, byte hiLo, byte hiHi)
    {
        // <https://stackoverflow.com/a/27610608/65555>
        return ((long) bytesToInt16(hiLo, hiHi) & 0xffff) << 16 | ((long) bytesToInt16(loLo, loHi) & 0xffff);
    }

    private static void logChamberParamMismatchError(int chamberId, String paramName, Object expected, Object actual)
    {
        logger.error("Chamber params mismatch: Chamber " + chamberId + " " + paramName + " should be " + expected + " but is " + actual);
    }

    private ArduinoMessenger getMessenger() throws IOException
    {
        if (messenger == null)
            messenger = new ArduinoMessenger();  // can throw
        return messenger;
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
