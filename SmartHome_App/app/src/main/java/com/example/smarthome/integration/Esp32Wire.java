package com.example.smarthome.integration;

import java.nio.charset.StandardCharsets;

/** The wire layout used by SmartHome_ESP/main/device.h on ESP32-S2 (little endian). */
public final class Esp32Wire {
    public static final int TCP_PORT = 5000;
    public static final byte READ_DEVICE = 0x01;
    public static final byte READ_WATER_LEVEL = 0x02;
    public static final byte SET_LIGHT_STATE = 0x03;
    public static final byte SET_RELAY_STATE = 0x04;
    public static final int CONTROLLER_ID_SIZE = 32;
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

    public static byte[] readWaterLevelRequest(String controllerId)
    {
        return commandRequest(READ_WATER_LEVEL, controllerId, 0);
    }

    public static byte[] setLightStateRequest(String controllerId, boolean poweredOn, int brightness)
    {
        checkPercentage(brightness);
        byte[] request = commandRequest(SET_LIGHT_STATE, controllerId, 2);
        request[33] = booleanByte(poweredOn);
        request[34] = (byte) brightness;
        return request;
    }

    public static byte[] setRelayStateRequest(String controllerId, boolean poweredOn)
    {
        byte[] request = commandRequest(SET_RELAY_STATE, controllerId, 1);
        request[33] = booleanByte(poweredOn);
        return request;
    }

    private static byte[] commandRequest(byte command, String controllerId, int valueSize)
    {
        if (controllerId == null || controllerId.trim().isEmpty()
                || controllerId.indexOf('\0') >= 0)
        {
            throw new IllegalArgumentException("Идентификатор контроллера пуст или содержит нулевой символ.");
        }
        byte[] idBytes = controllerId.getBytes(StandardCharsets.UTF_8);
        if (idBytes.length >= CONTROLLER_ID_SIZE)
        {
            throw new IllegalArgumentException("Идентификатор контроллера должен занимать не более 31 байта UTF-8.");
        }
        // ESP проверяет адресата ДО изменения выхода: IP мог перейти другой плате.
        byte[] request = new byte[1 + CONTROLLER_ID_SIZE + valueSize];
        request[0] = command;
        System.arraycopy(idBytes, 0, request, 1, idBytes.length);
        return request;
    }

    private static byte booleanByte(boolean value)
    {
        if (value)
        {
            return 1;
        }
        else
        {
            return 0;
        }
    }

    private static void checkPercentage(int value)
    {
        if (value < 0 || value > 100)
        {
            throw new IllegalArgumentException("Значение должно быть от 0 до 100.");
        }
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
        int value = data[78] & 0xff;
        if (type == 0 || type == 2)
        {
            checkPercentage(value);
        }
        return new Snapshot(type, id, data[76] == 1, data[77] == 1, value);
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
