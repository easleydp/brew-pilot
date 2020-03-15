package com.easleydp.tempctrl.domain;

import static java.lang.Double.*;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

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
        // uptimeMins,minFreeRam,temperatureSensorsOk,logDataEjected
        if (values.length != 4)
            throw new IOException("Unexpected 'status' response: " + response);
        int i = 0;
        return new ChamberManagerStatus(
                parseInt(values[i++]),
                parseInt(values[i++]),
                parseBool(values[i++]),
                parseBool(values[i++]));
    }

    @Override
    public void setParameters(int chamberId, ChamberParameters params) throws IOException
    {
        chamberParametersByChamberId.put(chamberId, params);

        getMessenger().sendRequest("setParams:" + csv(chamberId, params.tTarget, params.tTargetNext, params.tMin, params.tMax,
                params.hasHeater ? 1 : 0, params.Kp, params.Ki, params.Kd, params.mode));
        // Examples for console test:
        //  ^setParams:1,171,172,-10,400,1,2.1,0.01,20.5,A$
        //  ^setParams:2,100,100,-10,150,0,1.9,0.015,19.5,O$
        getMessenger().expectResponse("ack");
    }

    @Override
    public ChamberReadings getReadings(int chamberId, Date timeNow) throws IOException
    {
        getMessenger().sendRequest("getChmbrRds:" + chamberId);
        String response = getMessenger().getResponse("chmbrRds:");
        logger.debug("Raw chmbrRds:" + response);
        String[] values = response.split(",");
        // Expecting:
        // tTarget,tTargetNext,tMin,tMax,hasHeater,Kp,Ki,Kd,tBeer,tChamber,tExternal,tPi,heaterOutput,fridgeOn,mode
        if (values.length != 16)
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
        Mode modeParam = Mode.get(values[i++]);

        // Readings
        int tBeer = parseInt(values[i++]);
        int tChamber = parseInt(values[i++]);
        int tExternal = parseInt(values[i++]);
        int tPi = parseInt(values[i++]);
        int heaterOutput = parseInt(values[i++]);
        boolean fridgeOn = parseBool(values[i++]);
        Mode modeActual = Mode.get(values[i++]); // may be null

        // Check consistency of params
        {
            Gyle activeGyle = chamberRepository.getChamberById(chamberId).getActiveGyle();
            if (activeGyle == null)
                throw new IllegalStateException("No active gyle for chamberId " + chamberId);
            ChamberParameters params = activeGyle.getChamberParameters(timeNow);

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
        }

        return new ChamberReadings(timeNow, tTarget, tBeer, tExternal, tChamber, tPi,
                heaterOutput, fridgeOn, modeActual, new ChamberParameters(tTarget, tTargetNext, tMin, tMax, hasHeater, Kp, Ki, Kd, modeParam));
    }

    @Override
    public void slurpLogMessages() throws IOException
    {
        try
        {
            getMessenger().sendRequest("getLogMsgs");
            while (true)
            {
                String logMessage = getMessenger().getResponse("logMsg:", "ack");
                if (logMessage == null)
                    break;
                logLogMessage(logMessage);
            }
        }
        catch (Throwable t)
        {
            if (firstSlurp && "The read operation timed out before any data was returned.".equals(t.getMessage()))
            {
                logger.debug("First read operation timed out in slurpLogMessages()");
            }
            throw t;
        }
        finally
        {
            firstSlurp = false;
        }
    }
    private static boolean firstSlurp = true;

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
                }
            case "PID":
                switch (id)
                {
                    case '~':
                        // tError/* int16 */, cd.integral/* float */, cd.priorError/* float */
                        if (buffer.length != 10)
                            return "PID state variables {error: \"Expected 10 bytes\"}";

                        int i = 0;
                        int tError = bytesToInt16(buffer[i++], buffer[i++]);
                        float integral = bytesToFloat(buffer[i++], buffer[i++], buffer[i++], buffer[i++]);
                        float priorError = bytesToFloat(buffer[i++], buffer[i++], buffer[i++], buffer[i++]);
                        return String.format("{tError: %d, integral: %.3f, priorError: %.3f}", tError, integral, priorError);
                    case '+': case '-': case '!':
                        // pidOutput/* float */
                        if (buffer.length != 4)
                            return "we've screwed-up somehow {error: \"Expected 4 bytes\"}";

                        return String.format("{pidOutput: %.3f}", bytesToFloat(buffer[0], buffer[1], buffer[2], buffer[3]));
                    case 'd':
                        // tBeerLastDelta/* int8 */
                        if (buffer.length != 1)
                            return "Decay {error: \"Expected 1 byte\"}";

                        return String.format("{tBeerLastDelta: %d}", buffer[0]);
                }
            case "CD":
                switch (id)
                {
                    case 'p': case 'P':
                        if (buffer.length != 0)
                            return "{error: \"Expected 0 bytes\"}";

                        return "getEepromChamberParams " + (id == 'p' ? "good" : "bad");
                    case '0':
                        // tTarget/* int16_t */, mode/* char */
                        if (buffer.length != 3)
                            return "{error: \"Expected 3 bytes\"}";

                        char ch = (char) buffer[2];
                        if (!isPrintableChar(ch))
                        {
                            logger.warn("CD:0 Arduino logMsg, bad mode char: " + buffer[2]);
                            ch = '!';
                        }
                        return String.format("updateChamberParamsAndTarget {tTarget: %d, mode: \"%c\"}",
                                bytesToInt16(buffer[0], buffer[1]), ch);
                    case '1': case '2': case 'T': case 't':
                        // tTarget/* int16_t */
                        if (buffer.length != 2)
                            return "{error: \"Expected 2 bytes\"}";

                        return String.format("%s {tTarget: %d}",
                                id == '1' ? "saveTTarget" : id == '2' ? "saveTTargetOnceInAWhile" : id == 'T' ? "getEepromTTargetWithChecksum error" : "getEepromTTargetWithChecksum good",
                                bytesToInt16(buffer[0], buffer[1]));
                    case 'Q': case 'U':
                        // chamberId/* uint8_t */
                        if (buffer.length != 1)
                            return "{error: \"Expected 1 byte\"}";

                        return String.format("%s {chamberId: %d}",
                                id == 'Q' ? "getEepromChamberParams bad chamberId: " : "getEepromTTargetWithChecksum bad chamberId",
                                buffer[0]);
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
                    case 'j': case 'k':
                        // time/* uint32_t */
                        if (buffer.length != 4)
                            return "{error: \"Expected 4 bytes\"}";

                        return String.format("%s {mS: %d}",
                                id == 'j' ? "readTemperatures duration" : "readTemperatures & control duration",
                                bytesToUint32(buffer[0], buffer[1], buffer[2], buffer[3]));
                }
            case "T":
                switch (id)
                {
                    case 'C':
                        // sensorCount/* uint8_t */
                        if (buffer.length != 1)
                            return "{error: \"Expected 1 byte\"}";

                        return String.format("Unexpected sensorCount {%d}", buffer[0]);
                    case 'D':
                        // sensorIndex/* uint8_t */, reading/* float */
                        if (buffer.length != 5)
                            return "{error: \"Expected 5 bytes\"}";

                        return String.format("Sensor disconnected? {sensorIndex: %d, pidOutput: %.3f}", buffer[0], bytesToFloat(buffer[1], buffer[2], buffer[3], buffer[4]));
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
