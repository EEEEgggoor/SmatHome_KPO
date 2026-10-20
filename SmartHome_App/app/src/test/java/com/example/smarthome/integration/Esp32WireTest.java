package com.example.smarthome.integration;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class Esp32WireTest {
    @Test public void credentialsPreserveSpacesAndEndEachLine() {
        assertArrayEquals(" My WiFi \n password \n".getBytes(StandardCharsets.UTF_8),
                Esp32Wire.credentials(" My WiFi ", " password "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void credentialsRejectEmbeddedNewline() {
        Esp32Wire.credentials("home\nother", "password");
    }

    @Test(expected = IllegalArgumentException.class)
    public void credentialsRejectPasswordUnsupportedByCurrentFirmware() {
        Esp32Wire.credentials("home", "");
    }

    @Test public void readsWaterSnapshotWithoutInventingMeasurements() {
        byte[] raw = snapshot(2, "ESP32-S2-001", true, 63);
        Esp32Wire.Snapshot result = Esp32Wire.parseSnapshot(raw);
        assertEquals(2, result.type);
        assertEquals("ESP32-S2-001", result.controllerId);
        assertTrue(result.online);
        assertEquals(63, result.value);
        assertFalse(result.poweredOn);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTruncatedSnapshot() {
        Esp32Wire.parseSnapshot(new byte[79]);
    }

    @Test
    public void request_percentagesIncludeBothBoundaries()
    {
        byte[] minimum = Esp32Wire.setLightStateRequest("esp", false, 0);
        byte[] maximum = Esp32Wire.setLightStateRequest("esp", true, 100);
        assertEquals(35, maximum.length);
        assertEquals(0, minimum[33]);
        assertEquals(0, minimum[34]);
        assertEquals(1, maximum[33]);
        assertEquals(100, maximum[34]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void request_rejectsBrightnessAbove100()
    {
        Esp32Wire.setLightStateRequest("esp", true, 101);
    }

    @Test(expected = IllegalArgumentException.class)
    public void request_rejectsNullControllerId()
    {
        Esp32Wire.readWaterLevelRequest(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void request_rejectsBlankControllerId()
    {
        Esp32Wire.readWaterLevelRequest("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void request_rejectsEmbeddedZeroInControllerId()
    {
        Esp32Wire.setRelayStateRequest("esp\0another", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void request_reservesTerminatorIn32ByteId()
    {
        Esp32Wire.readWaterLevelRequest("12345678901234567890123456789012");
    }

    @Test
    public void request_accepts31ByteIdAndZeroTerminatesIt()
    {
        byte[] request = Esp32Wire.readWaterLevelRequest("1234567890123456789012345678901");
        assertEquals(33, request.length);
        assertEquals(2, request[0]);
        assertEquals(0, request[32]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void request_measuresIdInUtf8Bytes()
    {
        Esp32Wire.readWaterLevelRequest("абвгабвгабвгабвг");
    }

    private static byte[] snapshot(int type, String id, boolean online, int value) {
        byte[] raw = new byte[80];
        raw[0] = (byte) type;
        byte[] name = "Device".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(name, 0, raw, 12, name.length);
        byte[] controller = id.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(controller, 0, raw, 44, controller.length);
        raw[76] = (byte) (online ? 1 : 0);
        raw[78] = (byte) value;
        return raw;
    }
}
