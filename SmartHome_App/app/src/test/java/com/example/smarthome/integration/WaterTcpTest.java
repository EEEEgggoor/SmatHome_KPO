package com.example.smarthome.integration;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
public class WaterTcpTest {
    private Esp32Wire.Snapshot exchange(boolean settingThreshold, int value, int replyValue, boolean open) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(2000);
            Future<?> peer = executor.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(2000);
                    byte[] request = new byte[34]; new DataInputStream(socket.getInputStream()).readFully(request);
                    assertEquals(settingThreshold ? 6 : 5, request[0]);
                    assertEquals('w', request[1]); assertEquals(0, request[32]); assertEquals(value, request[33]);
                    byte[] reply = new byte[80]; reply[0]=2; reply[44]='w'; reply[76]=1;
                    reply[77]=(byte)(open ? 1 : 0); reply[78]=45; reply[79]=(byte)replyValue;
                    socket.getOutputStream().write(reply,0,47); socket.getOutputStream().flush();
                    socket.getOutputStream().write(reply,47,33);
                } catch (IOException e) { throw new RuntimeException(e); }
            });
            Esp32TcpClient client = new Esp32TcpClient(1000,1000);
            InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(),server.getLocalPort());
            try {
                return settingThreshold ? client.setWaterThreshold(address,"w",value) : client.setValve(address,"w",value==1);
            } finally { peer.get(3,TimeUnit.SECONDS); }
        } finally { executor.shutdownNow(); }
    }
    @Test public void confirmsThresholdAndFragmentedSnapshot() throws Exception {
        assertEquals(65,exchange(true,65,65,false).closeThreshold);
    }
    @Test(expected=IOException.class) public void wrongThresholdFails() throws Exception { exchange(true,65,80,false); }
    @Test public void confirmsValveOpen() throws Exception { assertTrue(exchange(false,1,80,true).poweredOn); }
    @Test(expected=IOException.class) public void refusedValveOpenFails() throws Exception { exchange(false,1,80,false); }
}
