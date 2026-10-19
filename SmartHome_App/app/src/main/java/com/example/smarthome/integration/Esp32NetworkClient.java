package com.example.smarthome.integration;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Discovery and the single TCP command currently implemented by tcp_service.c. */
final class Esp32NetworkClient {
    private static final String SERVICE_TYPE = "_smarthome._tcp.";
    private final NsdManager nsd;
    private final WifiManager wifi;

    Esp32NetworkClient(Context context) {
        nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    Result findAndRead(String expectedId) throws IOException {
        List<Result> found = discover(expectedId, true);
        if (found.isEmpty()) throw new IOException("ESP32-S2 не найден в Wi-Fi.");
        return found.get(0);
    }

    List<Result> findAll() throws IOException {
        return discover(null, false);
    }

    private List<Result> discover(String expectedId, boolean firstMatch) throws IOException {
        if (nsd == null) throw new IOException("Поиск контроллеров недоступен на этом телефоне.");
        WifiManager.MulticastLock lock = null;
        if (wifi != null) {
            lock = wifi.createMulticastLock("smarthome-esp-discovery");
            lock.setReferenceCounted(false);
            lock.acquire();
        }
        final LinkedBlockingQueue<Object> results = new LinkedBlockingQueue<>();
        NsdManager.DiscoveryListener discovery = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String serviceType) { }
            @Override public void onDiscoveryStopped(String serviceType) { }
            @Override public void onServiceLost(NsdServiceInfo serviceInfo) { }
            @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                results.offer(new IOException("Поиск ESP32-S2 не запустился: " + errorCode));
            }
            @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) { }
            @Override public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (!serviceInfo.getServiceName().startsWith("smart_home_device")) return;
                nsd.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                        results.offer(new IOException("Не удалось определить адрес ESP32-S2: " + errorCode));
                    }
                    @Override public void onServiceResolved(NsdServiceInfo info) {
                        if (info.getHost() != null && info.getPort() > 0) {
                            results.offer(new InetSocketAddress(info.getHost(), info.getPort()));
                        }
                    }
                });
            }
        };
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery);
            long deadline = System.currentTimeMillis() + (firstMatch ? 20000 : 8000);
            IOException lastError = null;
            LinkedHashMap<String, Result> found = new LinkedHashMap<>();
            while (System.currentTimeMillis() < deadline) {
                Object item;
                try {
                    item = results.poll(Math.max(1, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Поиск ESP32-S2 прерван.", error);
                }
                if (item instanceof IOException) {
                    lastError = (IOException) item;
                    if (lastError.getMessage() != null
                            && lastError.getMessage().startsWith("Поиск ESP32-S2 не запустился")) {
                        throw lastError;
                    }
                    continue;
                }
                if (item instanceof InetSocketAddress) {
                    InetSocketAddress address = (InetSocketAddress) item;
                    try {
                        Esp32Wire.Snapshot snapshot = read(address);
                        if (snapshot.online && (expectedId == null
                                || expectedId.equals(snapshot.controllerId))) {
                            found.put(snapshot.controllerId, new Result(address, snapshot));
                            if (firstMatch) return new ArrayList<>(found.values());
                        }
                    } catch (IOException | IllegalArgumentException error) {
                        lastError = new IOException("ESP32-S2 вернул некорректное состояние.", error);
                    }
                }
            }
            if (!found.isEmpty() || !firstMatch) return new ArrayList<>(found.values());
            if (lastError != null) throw lastError;
            throw new IOException("ESP32-S2 не найден в Wi-Fi. Телефон и контроллер должны быть в одной сети.");
        } finally {
            try { nsd.stopServiceDiscovery(discovery); } catch (RuntimeException ignored) { }
            if (lock != null && lock.isHeld()) lock.release();
        }
    }

    Esp32Wire.Snapshot read(InetSocketAddress address) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(address, 4000);
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(Esp32Wire.READ_DEVICE);
            socket.getOutputStream().flush();
            byte[] response = new byte[Esp32Wire.SNAPSHOT_SIZE];
            new DataInputStream(socket.getInputStream()).readFully(response);
            return Esp32Wire.parseSnapshot(response);
        }
    }

    static final class Result {
        final InetSocketAddress address;
        final Esp32Wire.Snapshot snapshot;
        Result(InetSocketAddress address, Esp32Wire.Snapshot snapshot) {
            this.address = address;
            this.snapshot = snapshot;
        }
    }
}
