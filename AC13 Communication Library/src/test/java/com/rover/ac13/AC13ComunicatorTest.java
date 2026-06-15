package com.rover.ac13;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AC13ComunicatorTest {

    @Test
    void defaultsAreDisconnectedAndInfraredOff() {
        AC13Comunicator communicator = new AC13Comunicator();

        assertFalse(communicator.isConnected());
        assertEquals("Off", communicator.getInfraredState());
    }

    @Test
    void parameterDumpContainsExpectedKeys() {
        AC13Comunicator communicator = new AC13Comunicator();

        String dump = communicator.getParameters_List();

        assertTrue(dump.contains("id"));
        assertTrue(dump.contains("sys_ver"));
        assertTrue(dump.contains("wifi_key4_bits"));
        assertTrue(dump.contains("wifi_wpa_psk"));
    }
}
