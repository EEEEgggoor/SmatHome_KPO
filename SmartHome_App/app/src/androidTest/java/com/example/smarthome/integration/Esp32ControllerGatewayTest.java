package com.example.smarthome.integration;

import android.content.Context;
import android.os.Looper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.example.smarthome.model.Device;
import com.example.smarthome.model.LightDevice;
import com.example.smarthome.model.RelayDevice;
import com.example.smarthome.model.WaterDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class Esp32ControllerGatewayTest
{
    private static final String ID = "gateway-test";
    private Esp32ControllerGateway gateway;
    private ServerSocket server;
    private ExecutorService peerExecutor;
    private Future<Void> peer;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception
    {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        gateway = new Esp32ControllerGateway(context);
        server = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
        server.setSoTimeout(3000);
        peerExecutor = Executors.newSingleThreadExecutor();
        // Подменяем только адрес в кэше: обмен идёт настоящими TCP-сокетами.
        Field addressesField = Esp32ControllerGateway.class.getDeclaredField("knownAddresses");
        addressesField.setAccessible(true);
        Map<String, InetSocketAddress> addresses =
                (Map<String, InetSocketAddress>) addressesField.get(gateway);
        addresses.put(ID, new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getLocalPort()));
    }

    @After
    public void tearDown() throws Exception
    {
        server.close();
        peerExecutor.shutdownNow();
        stopExecutor("io");
        stopExecutor("polling");
    }

    private void stopExecutor(String name) throws Exception
    {
        Field field = Esp32ControllerGateway.class.getDeclaredField(name);
        field.setAccessible(true);
        ExecutorService executor = (ExecutorService) field.get(gateway);
        executor.shutdownNow();
    }

    @Test
    public void lightCommand_returnsConfirmedCopyOnMainThread() throws Exception
    {
        final LightDevice original = new LightDevice(7, "Свет", 2, ID, true, false, 10, 25);
        final Probe<LightDevice> result = new Probe<LightDevice>();
        respond(3, 0, true, 77);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.setLightState(101, original, true, 77, 25, result);
            }
        });
        LightDevice confirmed = result.success();
        peer.get(4, TimeUnit.SECONDS);
        assertEquals(101, result.requestId);
        assertTrue(confirmed.isPoweredOn());
        assertEquals(77, confirmed.getBrightness());
        assertEquals(25, confirmed.getLightTemperature());
        assertEquals(7, confirmed.getId());
        assertEquals(2, confirmed.getRoomId());
        assertEquals("Свет", confirmed.getName());
        assertNotSame(original, confirmed);
        assertFalse(original.isPoweredOn());
        assertEquals(10, original.getBrightness());
    }

    @Test
    public void relayCommand_returnsConfirmedPower() throws Exception
    {
        final RelayDevice original = new RelayDevice(8, "Реле", 2, ID, true, false);
        final Probe<RelayDevice> result = new Probe<RelayDevice>();
        respond(4, 1, true, 0);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.setRelayState(102, original, true, result);
            }
        });
        assertTrue(result.success().isPoweredOn());
        peer.get(4, TimeUnit.SECONDS);
        assertFalse(original.isPoweredOn());
        assertEquals(102, result.requestId);
    }

    @Test
    public void relayCommand_unconfirmedPowerCallsErrorWithoutChangingModel() throws Exception
    {
        final RelayDevice original = new RelayDevice(8, "Реле", 2, ID, true, false);
        final Probe<RelayDevice> result = new Probe<RelayDevice>();
        respond(4, 1, false, 0);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.setRelayState(103, original, true, result);
            }
        });
        result.await();
        peer.get(4, TimeUnit.SECONDS);
        assertNotNull(result.error);
        assertNull(result.device);
        assertEquals(8, result.errorDeviceId);
        assertEquals(103, result.requestId);
        assertFalse(original.isPoweredOn());
    }

    @Test
    public void waterRead_uses02AndKeepsUnsupportedMeasurementsUnknown() throws Exception
    {
        final WaterDevice original = new WaterDevice(9, "Бак", 2, ID, true);
        final Probe<Device> result = new Probe<Device>();
        respond(2, 2, false, 63);
        main(new Runnable()
        {
            @Override
            public void run()
            {
                gateway.readDeviceState(104, original, result);
            }
        });
        WaterDevice water = (WaterDevice) result.success();
        peer.get(4, TimeUnit.SECONDS);
        assertEquals(Integer.valueOf(63), water.getWaterLevel());
        assertNull(water.getTemperature());
        assertNull(water.getLevelStatus());
        assertNull(water.getLastUpdate());
        assertNull(water.getValveOpen());
        assertNull(original.getWaterLevel());
    }

    private void respond(final int command, final int type, final boolean poweredOn,
                         final int value)
    {
        peer = peerExecutor.submit(new Callable<Void>()
        {
            @Override
            public Void call() throws Exception
            {
                try (Socket socket = server.accept())
                {
                    socket.setSoTimeout(3000);
                    int length = 33;
                    if (command == 3)
                    {
                        length = 35;
                    }
                    else if (command == 4)
                    {
                        length = 34;
                    }
                    byte[] request = new byte[length];
                    new DataInputStream(socket.getInputStream()).readFully(request);
                    assertEquals(command, request[0]);
                    byte[] id = ID.getBytes(StandardCharsets.UTF_8);
                    for (int index = 0; index < id.length; index++)
                    {
                        assertEquals(id[index], request[index + 1]);
                    }
                    if (command == 3 || command == 4)
                    {
                        assertEquals(1, request[33]);
                    }
                    if (command == 3)
                    {
                        assertEquals(77, request[34]);
                    }
                    byte[] response = new byte[80];
                    response[0] = (byte) type;
                    System.arraycopy(id, 0, response, 44, id.length);
                    response[76] = 1;
                    if (poweredOn)
                    {
                        response[77] = 1;
                    }
                    response[78] = (byte) value;
                    socket.getOutputStream().write(response);
                    socket.getOutputStream().flush();
                }
                return null;
            }
        });
    }

    private void main(Runnable action)
    {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static class Probe<T extends Device> implements ControllerGateway.Callback<T>
    {
        private final CountDownLatch done = new CountDownLatch(1);
        T device;
        Exception error;
        long requestId;
        int errorDeviceId;
        boolean mainThread;

        @Override
        public void onSuccess(long requestId, T device)
        {
            this.requestId = requestId;
            this.device = device;
            mainThread = Looper.myLooper() == Looper.getMainLooper();
            done.countDown();
        }

        @Override
        public void onError(long requestId, int deviceId, Exception error)
        {
            this.requestId = requestId;
            this.errorDeviceId = deviceId;
            this.error = error;
            mainThread = Looper.myLooper() == Looper.getMainLooper();
            done.countDown();
        }

        void await() throws InterruptedException
        {
            assertTrue("Нет callback gateway", done.await(4, TimeUnit.SECONDS));
            assertTrue("Callback должен приходить в главный поток", mainThread);
        }

        T success() throws InterruptedException
        {
            await();
            assertNull(error);
            assertNotNull(device);
            return device;
        }
    }
}
