package com.easleydp.tempctrl.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ArduinoChamberManagerTests
{
    /*
     * bytesToInt16, etc. are for little endian microcontrollers with the parameters' order
     * reflecting increasing memory locations.
     */

    @Test
    public void testBytesToInt16()
    {
        // 0x0000
        assertEquals(0, ArduinoChamberManager.bytesToInt16((byte) 0x00, (byte) 0x00));
        // 0x0001
        assertEquals(1, ArduinoChamberManager.bytesToInt16((byte) 0x01, (byte) 0x00));
        // 0x00FF
        assertEquals(255, ArduinoChamberManager.bytesToInt16((byte) 0xFF, (byte) 0x00));
        // 0x0100
        assertEquals(256, ArduinoChamberManager.bytesToInt16((byte) 0x00, (byte) 0x01));

        // 0x0A0B
        assertEquals(0x0A0B, ArduinoChamberManager.bytesToInt16((byte) 0x0B, (byte) 0x0A));

        // 0xFFFF
        assertEquals(-1, ArduinoChamberManager.bytesToInt16((byte) 0xFF, (byte) 0xFF));
        // 0xFFFE
        assertEquals(-2, ArduinoChamberManager.bytesToInt16((byte) 0xFE, (byte) 0xFF));
    }

    @Test
    public void testBytesToUint32()
    {
        // Note: result is returned as long because Java has no `unsigned int`

        // 0x00000000
        assertEquals(0, ArduinoChamberManager.bytesToUint32((byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00));
        // 0x00000001
        assertEquals(1, ArduinoChamberManager.bytesToUint32((byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00));

        // 0x0A0B0C0D
        assertEquals(0x0A0B0C0DL, ArduinoChamberManager.bytesToUint32((byte) 0x0D, (byte) 0x0C, (byte) 0x0B, (byte) 0x0A));

        // 0xFFFFFFFE
        assertEquals(4294967294L, ArduinoChamberManager.bytesToUint32((byte) 0xFE, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF));
        // 0xFFFFFFFF
        assertEquals(4294967295L, ArduinoChamberManager.bytesToUint32((byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF));
    }

    @Test
    public void testBytesToFloat()
    {
        assertEquals(3.14159274101F, ArduinoChamberManager.bytesToFloat((byte) 0xDB, (byte) 0x0F, (byte) 0x49, (byte) 0x40));
    }

}
