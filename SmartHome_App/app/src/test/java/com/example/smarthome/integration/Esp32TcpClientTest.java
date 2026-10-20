package com.example.smarthome.integration;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class Esp32TcpClientTest
{
    private static final String CONTROLLER_ID = "ESP-test";

    @Test
    public void readDevice_keepsOriginalOneByteRequest() throws Exception
    {
        Esp32Wire.Snapshot state = exchange(1, false, 0, snapshot(1, true, false, 0));
        assertEquals(CONTROLLER_ID, state.controllerId);
        assertFalse(state.poweredOn);
    }

    @Test
    public void readWaterLevel_readsSeparateCommandAndCompleteFragmentedReply() throws Exception
    {
        Esp32Wire.Snapshot state = exchange(2, false, 0, snapshot(2, true, false, 63));
        assertEquals(63, state.value);
        assertEquals(2, state.type);
    }

    @Test
    public void setLightState_confirmsPowerAndBrightness() throws Exception
    {
        Esp32Wire.Snapshot state = exchange(3, true, 77, snapshot(0, true, true, 77));
        assertTrue(state.poweredOn);
        assertEquals(77, state.value);
    }

    @Test
    public void setLightState_powerOffDoesNotResetBrightness() throws Exception
    {
        Esp32Wire.Snapshot state = exchange(3, false, 77, snapshot(0, true, false, 77));
        assertFalse(state.poweredOn);
        assertEquals(77, state.value);
    }

    @Test
    public void setRelayState_acceptsOnAndOff() throws Exception
    {
        assertTrue(exchange(4, true, 0, snapshot(1, true, true, 0)).poweredOn);
        assertFalse(exchange(4, false, 0, snapshot(1, true, false, 0)).poweredOn);
    }

    @Test(expected = IOException.class)
    public void setLightState_wrongBrightnessFails() throws Exception
    {
        exchange(3, true, 77, snapshot(0, true, true, 76));
    }

    @Test(expected = IOException.class)
    public void setLightState_wrongPowerFails() throws Exception
    {
        exchange(3, true, 77, snapshot(0, true, false, 77));
    }

    @Test(expected = IOException.class)
    public void setRelayState_unconfirmedPowerFails() throws Exception
    {
        exchange(4, true, 0, snapshot(1, true, false, 0));
    }

    @Test(expected = IOException.class)
    public void response_wrongControllerFails() throws Exception
    {
        byte[] reply = snapshot(1, true, true, 0);
        reply[44] = 'X';
        exchange(4, true, 0, reply);
    }

    @Test(expected = IOException.class)
    public void response_wrongTypeFails() throws Exception
    {
        exchange(2, false, 0, snapshot(1, true, false, 0));
    }

    @Test(expected = IOException.class)
    public void response_offlineFails() throws Exception
    {
        exchange(4, true, 0, snapshot(1, false, true, 0));
    }

    @Test(expected = IOException.class)
    public void response_incompleteFails() throws Exception
    {
        exchange(2, false, 0, new byte[79]);
    }

    @Test(expected = IOException.class)
    public void response_oldFirmwareClosingUnknownCommandFails() throws Exception
    {
        exchange(4, true, 0, new byte[0]);
    }

    @Test(expected = IOException.class)
    public void response_invalidPercentageFails() throws Exception
    {
        exchange(2, false, 0, snapshot(2, true, false, 101));
    }

    @Test(expected = IOException.class)
    public void response_invalidBooleanFails() throws Exception
    {
        byte[] reply = snapshot(1, true, true, 0);
        reply[77] = 2;
        exchange(4, true, 0, reply);
    }

    @Test
    public void expiredCommand_doesNotOpenConnection() throws Exception
    {
        try (ServerSocket server = localServer())
        {
            Esp32TcpClient client = new Esp32TcpClient(200, 200, System.nanoTime() - 1);
            try
            {
                client.setRelayState(address(server), CONTROLLER_ID, true);
                fail("Истёкшая команда не должна отправляться.");
            }
            catch (SocketTimeoutException expected)
            {
                assertNoMoreConnections(server);
            }
        }
    }

    @Test
    public void invalidBrightness_doesNotOpenConnection() throws Exception
    {
        try (ServerSocket server = localServer())
        {
            Esp32TcpClient client = new Esp32TcpClient(200, 200);
            try
            {
                client.setLightState(address(server), CONTROLLER_ID, true, -1);
                fail("Некорректное значение не должно отправляться.");
            }
            catch (IllegalArgumentException expected)
            {
                assertNoMoreConnections(server);
            }
        }
    }

    @Test
    public void silentController_timesOutWithoutRetry() throws Exception
    {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = localServer())
        {
            Future<Void> peer = executor.submit(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    try (Socket socket = server.accept())
                    {
                        socket.setSoTimeout(2000);
                        byte[] request = new byte[34];
                        DataInputStream input = new DataInputStream(socket.getInputStream());
                        input.readFully(request);
                        assertEquals(4, request[0]);
                        // Не отвечаем. Клиент обязан закрыть соединение после тайм-аута.
                        assertEquals(-1, input.read());
                    }
                    assertNoMoreConnections(server);
                    return null;
                }
            });
            Esp32TcpClient client = new Esp32TcpClient(500, 150);
            try
            {
                client.setRelayState(address(server), CONTROLLER_ID, true);
                fail("Ожидался тайм-аут ответа.");
            }
            catch (SocketTimeoutException expected)
            {
                peer.get(3, TimeUnit.SECONDS);
            }
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    private Esp32Wire.Snapshot exchange(final int command, boolean poweredOn, int brightness,
                                        final byte[] reply) throws Exception
    {
        final byte[] expectedRequest = expectedRequest(command, poweredOn, brightness);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = localServer())
        {
            Future<Void> peer = executor.submit(new Callable<Void>()
            {
                @Override
                public Void call() throws Exception
                {
                    try (Socket socket = server.accept())
                    {
                        socket.setSoTimeout(2000);
                        byte[] request = new byte[expectedRequest.length];
                        new DataInputStream(socket.getInputStream()).readFully(request);
                        assertArrayEquals(expectedRequest, request);
                        // Несколько записей моделируют ответ, разделённый TCP на части.
                        for (int offset = 0; offset < reply.length; offset += 7)
                        {
                            int count = Math.min(7, reply.length - offset);
                            socket.getOutputStream().write(reply, offset, count);
                            socket.getOutputStream().flush();
                        }
                    }
                    return null;
                }
            });
            Esp32TcpClient client = new Esp32TcpClient(1000, 1000);
            try
            {
                if (command == 1)
                {
                    return client.read(address(server));
                }
                if (command == 2)
                {
                    return client.readWaterLevel(address(server), CONTROLLER_ID);
                }
                if (command == 3)
                {
                    return client.setLightState(address(server), CONTROLLER_ID, poweredOn, brightness);
                }
                return client.setRelayState(address(server), CONTROLLER_ID, poweredOn);
            }
            finally
            {
                peer.get(3, TimeUnit.SECONDS);
            }
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    private static ServerSocket localServer() throws IOException
    {
        ServerSocket server = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
        server.setSoTimeout(2000);
        return server;
    }

    private static InetSocketAddress address(ServerSocket server)
    {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort());
    }

    private static void assertNoMoreConnections(ServerSocket server) throws IOException
    {
        server.setSoTimeout(150);
        try (Socket unexpected = server.accept())
        {
            fail("Клиент не должен отправлять дополнительную команду.");
        }
        catch (SocketTimeoutException expected)
        {
            // Ни одной лишней отправки.
        }
    }

    private static byte[] expectedRequest(int command, boolean poweredOn, int brightness)
    {
        if (command == 1)
        {
            return new byte[]{1};
        }
        int size = 33;
        if (command == 3)
        {
            size = 35;
        }
        else if (command == 4)
        {
            size = 34;
        }
        byte[] request = new byte[size];
        request[0] = (byte) command;
        byte[] id = CONTROLLER_ID.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(id, 0, request, 1, id.length);
        if (command == 3 || command == 4)
        {
            if (poweredOn)
            {
                request[33] = 1;
            }
        }
        if (command == 3)
        {
            request[34] = (byte) brightness;
        }
        return request;
    }

    private static byte[] snapshot(int type, boolean online, boolean poweredOn, int value)
    {
        byte[] reply = new byte[80];
        reply[0] = (byte) type;
        byte[] id = CONTROLLER_ID.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(id, 0, reply, 44, id.length);
        if (online)
        {
            reply[76] = 1;
        }
        if (poweredOn)
        {
            reply[77] = 1;
        }
        reply[78] = (byte) value;
        return reply;
    }
}
