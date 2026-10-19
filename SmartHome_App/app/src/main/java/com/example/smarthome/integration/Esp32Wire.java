package com.example.smarthome.integration;

import java.nio.charset.StandardCharsets;

/** The wire layout used by SmartHome_ESP/main/device.h on ESP32-S2 (little endian). */
public final class Esp32Wire {
    public static final int TCP_PORT = 5000;
    public static final byte READ_DEVICE = 0x01;
    public static final int SNAPSHOT_SIZE = 80;

    private Esp32Wire() { }

    public static byte[] credentials(String ssid, String password) {
        if (ssid == null || password == null || ssid.indexOf('\n') >= 0
                || ssid.indexOf('\r') >= 0 || ssid.indexOf('\0') >= 0
                || password.indexOf('\n') >= 0 || password.indexOf('\r') >= 0
                || password.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("SSID и пароль должны занимать по одной строке.");
        }
        byte[] ssidBytes = ssid.getBytes(StandardCharsets.UTF_8);
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        if (ssidBytes.length == 0 || ssidBytes.length > 32
                || passwordBytes.length < 8 || passwordBytes.length > 63) {
            throw new IllegalArgumentException("SSID: 1–32 байта; пароль: 8–63 байта UTF-8 для текущей прошивки.");
        }
        byte[] result = new byte[ssidBytes.length + passwordBytes.length + 2];
        System.arraycopy(ssidBytes, 0, result, 0, ssidBytes.length);
        result[ssidBytes.length] = '\n';
        System.arraycopy(passwordBytes, 0, result, ssidBytes.length + 1, passwordBytes.length);
        result[result.length - 1] = '\n';
        return result;
    }

    public static Snapshot parseSnapshot(byte[] data) {
        if (data == null || data.length != SNAPSHOT_SIZE) {
            throw new IllegalArgumentException("Неверный размер ответа ESP32-S2.");
        }
        int type = int32(data, 0);
        if (type < 0 || type > 2 || (data[76] != 0 && data[76] != 1)
                || (data[77] != 0 && data[77] != 1)) {
            throw new IllegalArgumentException("Неверный ответ ESP32-S2.");
        }
        String id = cString(data, 44, 32);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("ESP32-S2 не указал идентификатор.");
        }
        return new Snapshot(type, id, data[76] == 1, data[77] == 1,
                data[78] & 0xff);
    }

    private static int int32(byte[] data, int at) {
        return (data[at] & 0xff) | ((data[at + 1] & 0xff) << 8)
                | ((data[at + 2] & 0xff) << 16) | (data[at + 3] << 24);
    }

    private static String cString(byte[] data, int start, int length) {
        int end = start;
        while (end < start + length && data[end] != 0) end++;
        if (end == start + length) {
            throw new IllegalArgumentException("Строка ESP32-S2 не завершена нулём.");
        }
        return new String(data, start, end - start, StandardCharsets.UTF_8);
    }

    public static final class Snapshot {
        public final int type;
        public final String controllerId;
        public final boolean online;
        public final boolean poweredOn;
        /** Brightness for light, water level for water; raw firmware value. */
        public final int value;

        private Snapshot(int type, String controllerId, boolean online,
                         boolean poweredOn, int value) {
            this.type = type;
            this.controllerId = controllerId;
            this.online = online;
            this.poweredOn = poweredOn;
            this.value = value;
        }
    }
}
