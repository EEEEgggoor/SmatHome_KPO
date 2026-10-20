package com.example.smarthome.integration;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

/** Один запрос и один ответ на TCP-соединение. Не выполняет повтор команды. */
final class Esp32TcpClient
{
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final long deadlineNanos;

    Esp32TcpClient()
    {
        this(4000, 5000);
    }

    // Короткие тайм-ауты позволяют проверять ошибки соединения в локальных тестах.
    Esp32TcpClient(int connectTimeoutMillis, int readTimeoutMillis)
    {
        this(connectTimeoutMillis, readTimeoutMillis, Long.MAX_VALUE);
    }

    Esp32TcpClient(int connectTimeoutMillis, int readTimeoutMillis, long deadlineNanos)
    {
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0)
        {
            throw new IllegalArgumentException("Тайм-ауты должны быть положительными.");
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.deadlineNanos = deadlineNanos;
    }

    Esp32Wire.Snapshot read(InetSocketAddress address) throws IOException
    {
        byte[] request = new byte[]{Esp32Wire.READ_DEVICE};
        return exchange(address, request);
    }

    Esp32Wire.Snapshot readWaterLevel(InetSocketAddress address, String controllerId)
            throws IOException
    {
        byte[] request = Esp32Wire.readWaterLevelRequest(controllerId);
        Esp32Wire.Snapshot state = exchange(address, request);
        checkResponse(state, controllerId, 2);
        return state;
    }

    Esp32Wire.Snapshot setLightState(InetSocketAddress address, String controllerId,
                                    boolean poweredOn, int brightness) throws IOException
    {
        byte[] request = Esp32Wire.setLightStateRequest(controllerId, poweredOn, brightness);
        Esp32Wire.Snapshot state = exchange(address, request);
        checkResponse(state, controllerId, 0);
        if (state.poweredOn != poweredOn || state.value != brightness)
        {
            throw new IOException("Контроллер не подтвердил питание и яркость света.");
        }
        return state;
    }

    Esp32Wire.Snapshot setRelayState(InetSocketAddress address, String controllerId,
                                    boolean poweredOn) throws IOException
    {
        byte[] request = Esp32Wire.setRelayStateRequest(controllerId, poweredOn);
        Esp32Wire.Snapshot state = exchange(address, request);
        checkResponse(state, controllerId, 1);
        if (state.poweredOn != poweredOn)
        {
            throw new IOException("Контроллер не подтвердил состояние реле.");
        }
        return state;
    }

    private void checkResponse(Esp32Wire.Snapshot state, String controllerId, int type)
            throws IOException
    {
        if (!controllerId.equals(state.controllerId))
        {
            throw new IOException("Ответ получен от другого контроллера.");
        }
        if (state.type != type)
        {
            throw new IOException("Тип устройства не совпадает с командой.");
        }
        if (!state.online)
        {
            throw new IOException("Контроллер сообщил, что устройство недоступно.");
        }
    }

    private int remainingTimeout(int configuredTimeout) throws SocketTimeoutException
    {
        if (deadlineNanos == Long.MAX_VALUE)
        {
            return configuredTimeout;
        }
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0)
        {
            throw new SocketTimeoutException("Время выполнения команды истекло.");
        }
        long remainingMillis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
        return (int) Math.min(configuredTimeout, remainingMillis);
    }

    private Esp32Wire.Snapshot exchange(InetSocketAddress address, byte[] request)
            throws IOException
    {
        try (Socket socket = new Socket())
        {
            socket.connect(address, remainingTimeout(connectTimeoutMillis));
            socket.setSoTimeout(remainingTimeout(readTimeoutMillis));
            socket.getOutputStream().write(request);
            socket.getOutputStream().flush();

            // TCP может разделить ответ на несколько частей. Нужны все 80 байт.
            byte[] response = new byte[Esp32Wire.SNAPSHOT_SIZE];
            DataInputStream input = new DataInputStream(socket.getInputStream());
            int received = 0;
            while (received < response.length)
            {
                socket.setSoTimeout(remainingTimeout(readTimeoutMillis));
                int count = input.read(response, received, response.length - received);
                if (count < 0)
                {
                    throw new java.io.EOFException("ESP32-S2 закрыл соединение до полного ответа.");
                }
                received += count;
            }
            try
            {
                return Esp32Wire.parseSnapshot(response);
            }
            catch (IllegalArgumentException error)
            {
                throw new IOException("Некорректный ответ ESP32-S2.", error);
            }
        }
    }
}
