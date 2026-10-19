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
