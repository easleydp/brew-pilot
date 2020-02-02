package com.easleydp.tempctrl.domain;

import static java.nio.charset.StandardCharsets.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortTimeoutException;

/**
 * NOTE: Although this class implements AutoCloseable this should not be taken as a hint to
 * wrap each message exchange with a 'try with resource' construct. Opening the port afresh
 * for each message exchange makes it about 100 times slower (e.g. 600 vs 6ms)! So, best to
 * use close() only when absolutely needed.
 */
public class ArduinoMessenger implements AutoCloseable
{
    private static final Logger logger = LoggerFactory.getLogger(ArduinoMessenger.class);

    private SerialPort comPort;

    private static final String preferredPortName = "ttyACM0";

    private static final char CHAR_START = '^';
    private static final char CHAR_END = '$';
    private static final byte BYTE_START = ("" + CHAR_START).getBytes(US_ASCII)[0];
    private static final byte BYTE_END = ("" + CHAR_END).getBytes(US_ASCII)[0];

    public ArduinoMessenger()
    {
        comPort = findUsbSerialPort();
        if (comPort == null)
            throw new RuntimeException("Failed to find USB serial port");
        logger.info("Found USB serial port " + comPort.getSystemPortName());
        comPort.openPort();
        comPort.setBaudRate(57600);
        // https://github.com/Fazecast/jSerialComm/wiki/Blocking-and-Semiblocking-Reading-Usage-Example
        comPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
    }

    /** Returns the most likely looking USB serial port or null if none. */
    private static SerialPort findUsbSerialPort()
    {
        SerialPort[] serialPorts = SerialPort.getCommPorts();
        if (serialPorts != null  &&  serialPorts.length > 0)
        {
            // If there's only one that mentions "USB", go for that.
            // If more than one, look for the preferred port name otherwise go with first.
            List<SerialPort> usbPorts = Arrays.asList(serialPorts).stream()
                    .filter(sp -> sp.getPortDescription().indexOf("USB") != -1)
                    .collect(Collectors.toList());
            if (usbPorts.size() > 0)
            {
                if (usbPorts.size() == 1)
                    return usbPorts.get(0);

                SerialPort preferredPort = usbPorts.stream()
                        .filter(sp -> preferredPortName.equals(sp.getSystemPortName()))
                        .findFirst()
                        .orElse(null);
                if (preferredPort != null)
                    return preferredPort;

                String usbsJoined = usbPorts.stream()
                        .map(SerialPort::getPortDescription)
                        .collect( Collectors.joining("; ") );
                logger.warn("Failed to choose between the following USB ports (going with first): " + usbsJoined);
                return usbPorts.get(0);
            }
        }
        return null;
    }

    /**
     * Sends the specified request.
     * Takes care of topping & tailing the request with `CHAR_START` and `CHAR_END`.
     */
    public void sendRequest(String request) throws IOException
    {
        byte[] bytes = (CHAR_START + request + CHAR_END).getBytes(US_ASCII);
        comPort.writeBytes(bytes, bytes.length);
    }

    /**
     * Returns the next response received, with `CHAR_START` and `CHAR_END` removed.
     * Any characters received before `CHAR_START` are discarded.
     * @return String, never null (though possibly empty).
     */
    public String getResponse() throws IOException
    {
        List<Byte> stringBytesList = null;
        byte[] byteBuffer = new byte[1];
        int consecutiveZeroCount = 0;
        while (true)
        {
            int numRead = comPort.readBytes(byteBuffer, 1L, 0);
            if (numRead == 0)
                throw new SerialPortTimeoutException("The read operation timed out before any data was returned.");
            byte b = byteBuffer[0];
            if (stringBytesList == null)  // not collecting
            {
                if (b == BYTE_START)
                {
                    stringBytesList = new ArrayList<Byte>();
                }
                else if (b != 10  &&  b != 13)  // Anything other than CR and LF is interesting...
                {
                    // Receiving a ton of zeros is symptomatic of the USB port being already in
                    // use. Worth detecting this and bailing.
                    if (b == 0)
                    {
                        if (++consecutiveZeroCount >= 1000)
                            throw new IOException("USB port appears to be already in use.");
                    }
                    else
                    {
                        consecutiveZeroCount = 0;
                        if (isPrintable(b))
                            logger.warn("Expected start byte but received " + b + " ('" + Character.toString ((char) b) + "')");
                        else
                            logger.warn("Expected start byte but received " + b);
                    }
                }
            }
            else  // collecting
            {
                if (b == BYTE_END)
                    break;
                if (isPrintable(b))
                    stringBytesList.add(b);
                else
                    logger.warn("Non-printable character received while collecting: " + b);
            }
        }

        byte[] stringBytesArray = new byte[stringBytesList.size()];
        int i = 0;
        for (Byte b : stringBytesList)
            stringBytesArray[i++] = b;
        return new String(stringBytesArray, US_ASCII);
    }

    private static boolean isPrintable(byte b)
    {
        return 32 <= b  &&  b <= 126;
    }

    /**
     * Convenience wrapper of `getResponse()` for use when a fixed response is expected.
     * @throws IllegalStateException, IOException
     */
    public void expectResponse(String expectedResponse) throws IOException
    {
        String response = getResponse();
        if (!expectedResponse.equals(response))
        {
            throw new IllegalStateException("Expected [" + expectedResponse + "] but received [" + response + "]");
        }
    }

    /**
     * @return the response minus the prefix.
     * @throws IllegalStateException, IOException
     */
    public String getResponse(String expectedPrefix) throws IOException
    {
        String response = getResponse();
        int i = response.indexOf(expectedPrefix);
        if (i != 0)
        {
            throw new IllegalStateException("Expected [" + expectedPrefix + "...] but received [" + response + "]");
        }
        return response.substring(expectedPrefix.length());
    }

    /**
     * As `String getResponse(String expectedPrefix)` but expects EITHER a message beginning with `expectedPrefix`
     * OR a message equal to `endResponse`.
     * @return the response minus the prefix or null if the endResponse was received.
     * @throws IllegalStateException, IOException
     */
    public String getResponse(String expectedPrefix, String endResponse) throws IOException
    {
        String response = getResponse();
        if (endResponse.equals(response))
            return null;
        int i = response.indexOf(expectedPrefix);
        if (i != 0)
        {
            throw new IllegalStateException("Expected [" + expectedPrefix + "...] but received [" + response + "]");
        }
        return response.substring(expectedPrefix.length());
    }

    @Override
    public void close()
    {
        comPort.closePort();
    }

}
