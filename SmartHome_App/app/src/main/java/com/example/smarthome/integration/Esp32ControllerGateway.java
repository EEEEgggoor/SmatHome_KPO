package com.example.smarthome.integration;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.smarthome.model.Device;
import com.example.smarthome.model.LightDevice;
import com.example.smarthome.model.RelayDevice;
import com.example.smarthome.model.WaterDevice;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Integration with the commands actually present in the SmartHome_ESP branch. */
public final class Esp32ControllerGateway implements ControllerGateway {
    private static final String USB_PERMISSION = "com.example.smarthome.USB_PERMISSION";
    private static volatile Attempt pendingUsbPermission;

    private final Context context;
    private final UsbManager usb;
    private final Esp32NetworkClient network;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final ScheduledExecutorService polling = Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<String, InetSocketAddress> knownAddresses = new ConcurrentHashMap<>();
    private Attempt activeAttempt;

    public Esp32ControllerGateway(Context context) {
        this.context = context.getApplicationContext();
        usb = (UsbManager) this.context.getSystemService(Context.USB_SERVICE);
        network = new Esp32NetworkClient(this.context);
    }

    @Override public ConnectionAttempt scanDevices(@NonNull ScanCallback callback) {
        AtomicBoolean cancelled = new AtomicBoolean();
        Future<?> task = io.submit(() -> {
            try {
                List<DiscoveredDevice> devices = new ArrayList<>();
                for (Esp32NetworkClient.Result result : network.findAll()) {
                    knownAddresses.put(result.snapshot.controllerId, result.address);
                    devices.add(new DiscoveredDevice(result.snapshot.controllerId,
                            result.snapshot.type, result.address.getAddress().getHostAddress()));
                }
                main.post(() -> { if (!cancelled.get()) callback.onSuccess(devices); });
            } catch (Exception error) {
                main.post(() -> { if (!cancelled.get()) callback.onError(error); });
            }
        });
        return () -> { cancelled.set(true); task.cancel(true); };
    }

    @Override public ConnectionAttempt connectDiscoveredDevice(long requestId,
            @NonNull String controllerId, @NonNull ConnectionCallback callback) {
        Attempt attempt = new Attempt(requestId, callback);
        if (activeAttempt != null) {
            main.post(() -> callback.onError(requestId,
                    new IllegalStateException("Другое подключение ещё выполняется.")));
            return attempt;
        }
        if (controllerId.trim().isEmpty()) {
            main.post(() -> callback.onError(requestId,
                    new IllegalArgumentException("Идентификатор контроллера пуст.")));
            return attempt;
        }
        activeAttempt = attempt;
        attempt.expectedId = controllerId;
        attempt.progress(ConnectionStage.SEARCHING_DEVICE);
        attempt.start();
        return attempt;
    }

    @Override public ConnectionAttempt connectDevice(long requestId, @NonNull String ssid,
            @NonNull String password, @NonNull ConnectionCallback callback) {
        Attempt attempt = new Attempt(requestId, callback);
        if (activeAttempt != null) {
            main.post(() -> callback.onError(requestId,
                    new IllegalStateException("Другое подключение ещё выполняется.")));
            return attempt;
        }
        activeAttempt = attempt;
        try {
            attempt.credentials = Esp32Wire.credentials(ssid, password);
            if (usb == null) throw new IOException("USB host недоступен на телефоне.");
            attempt.device = Esp32UsbProvisioner.findSingleCdcDevice(usb);
            attempt.progress(ConnectionStage.SEARCHING_DEVICE);
            if (usb.hasPermission(attempt.device)) {
                attempt.start();
            } else {
                pendingUsbPermission = attempt;
                Intent intent = new Intent(context, Esp32UsbPermissionReceiver.class)
                        .setAction(USB_PERMISSION);
                PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                usb.requestPermission(attempt.device, pending);
            }
        } catch (Exception error) {
            attempt.fail(error);
        }
        return attempt;
    }

    static void onUsbPermission(Intent intent) {
        Attempt attempt = pendingUsbPermission;
        if (attempt == null || !USB_PERMISSION.equals(intent.getAction())) return;
        pendingUsbPermission = null;
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device == null || !device.equals(attempt.device)
                || !intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            attempt.fail(new IOException("Доступ к ESP32-S2 через USB не разрешён."));
        } else {
            attempt.start();
        }
    }

    private final class Attempt implements ConnectionAttempt {
        final long requestId;
        final ConnectionCallback callback;
        final AtomicBoolean cancelled = new AtomicBoolean();
        UsbDevice device;
        byte[] credentials;
        String expectedId;
        boolean finished;

        Attempt(long requestId, ConnectionCallback callback) {
            this.requestId = requestId;
            this.callback = callback;
        }

        void progress(ConnectionStage stage) {
            main.post(() -> { if (!cancelled.get() && !finished) callback.onProgress(requestId, stage, null); });
        }

        void start() {
            if (cancelled.get() || finished) return;
            io.execute(() -> {
                try {
                    if (expectedId == null) {
                        progress(ConnectionStage.SENDING_WIFI_SETTINGS);
                        Esp32UsbProvisioner.provision(usb, device, credentials, cancelled);
                        if (cancelled.get()) return;
                        progress(ConnectionStage.CONNECTING_TO_NETWORK);
                    }
                    progress(ConnectionStage.READING_STATE);
                    Esp32NetworkClient.Result result = expectedId == null
                            ? network.findAndRead(null) : readDiscovered(expectedId);
                    if (cancelled.get()) return;
                    knownAddresses.put(result.snapshot.controllerId, result.address);
                    main.post(() -> {
                        if (cancelled.get() || finished) return;
                        Esp32Wire.Snapshot state = result.snapshot;
                        if (!state.online) {
                            fail(new IOException("ESP32-S2 сообщил, что он не в сети."));
                            return;
                        }
                        // The C LightData has no light temperature, and WaterData has no
                        // timestamp/temperature/valve. Never invent missing measurements.
                        if (state.type == 0) {
                            fail(new IOException("Прошивка не передаёт температуру света."));
                            return;
                        }
                        finished = true;
                        activeAttempt = null;
                        if (state.type == 1) {
                            callback.onRelayConnected(requestId, state.controllerId, state.poweredOn);
                        } else {
                            if (state.value > 100) {
                                callback.onError(requestId, new IOException("ESP32-S2 вернул неверный уровень воды."));
                                return;
                            }
                            callback.onWaterConnected(requestId, state.controllerId,
                                    state.value, null, null, null, null);
                        }
                    });
                } catch (Exception error) {
                    fail(error);
                }
            });
        }

        void fail(Exception error) {
            main.post(() -> {
                if (cancelled.get() || finished) return;
                finished = true;
                if (activeAttempt == this) activeAttempt = null;
                if (pendingUsbPermission == this) pendingUsbPermission = null;
                callback.onError(requestId, error);
            });
        }

        @Override public void cancel() {
            cancelled.set(true);
            if (pendingUsbPermission == this) pendingUsbPermission = null;
            if (activeAttempt == this) activeAttempt = null;
        }
    }

    private Esp32NetworkClient.Result readDiscovered(String controllerId) throws IOException {
        InetSocketAddress address = knownAddresses.get(controllerId);
        if (address != null) {
            try {
                Esp32Wire.Snapshot snapshot = network.read(address);
                if (controllerId.equals(snapshot.controllerId) && snapshot.online) {
                    return new Esp32NetworkClient.Result(address, snapshot);
                }
            } catch (IOException | IllegalArgumentException ignored) { }
            knownAddresses.remove(controllerId);
        }
        return network.findAndRead(controllerId);
    }

    @Override public void readDeviceState(long requestId, @NonNull Device device,
            @NonNull Callback<Device> callback) {
        io.execute(() -> {
            try {
                Esp32Wire.Snapshot state = readSnapshot(device.getControllerId());
                Device result = makeState(device, state);
                main.post(() -> callback.onSuccess(requestId, result));
            } catch (Exception error) {
                main.post(() -> callback.onError(requestId, device.getId(), error));
            }
        });
    }

    private Esp32Wire.Snapshot readSnapshot(String controllerId) throws IOException {
        InetSocketAddress address = knownAddresses.get(controllerId);
        if (address != null) {
            try {
                Esp32Wire.Snapshot result = network.read(address);
                if (controllerId.equals(result.controllerId)) return result;
            } catch (IOException | IllegalArgumentException ignored) {
                knownAddresses.remove(controllerId);
            }
        }
        Esp32NetworkClient.Result found = network.findAndRead(controllerId);
        knownAddresses.put(controllerId, found.address);
        return found.snapshot;
    }

    private Device makeState(Device device, Esp32Wire.Snapshot state) throws IOException {
        if (!device.getControllerId().equals(state.controllerId)) {
            throw new IOException("Ответ получен от другого контроллера.");
        }
        if (device instanceof RelayDevice && state.type == 1) {
            RelayDevice old = (RelayDevice) device;
            return new RelayDevice(old.getId(), old.getName(), old.getRoomId(),
                    old.getControllerId(), state.online,
                    state.online ? state.poweredOn : old.isPoweredOn());
        }
        if (device instanceof LightDevice && state.type == 0) {
            LightDevice old = (LightDevice) device;
            int brightness = state.online ? state.value : old.getBrightness();
            if (brightness > 100) throw new IOException("ESP32-S2 вернул неверную яркость.");
            return new LightDevice(old.getId(), old.getName(), old.getRoomId(),
                    old.getControllerId(), state.online,
                    state.online ? state.poweredOn : old.isPoweredOn(), brightness,
                    old.getLightTemperature());
        }
        if (device instanceof WaterDevice && state.type == 2) {
            WaterDevice old = (WaterDevice) device;
            WaterDevice result = new WaterDevice(old.getId(), old.getName(), old.getRoomId(),
                    old.getControllerId(), state.online);
            if (state.online) {
                if (state.value > 100) throw new IOException("ESP32-S2 вернул неверный уровень воды.");
                result.updateLevelOnly(state.value);
            } else if (old.getWaterLevel() != null && old.getTemperature() == null) {
                result.updateLevelOnly(old.getWaterLevel());
            } else if (old.getWaterLevel() != null && old.getTemperature() != null
                    && old.getLevelStatus() != null && old.getLastUpdate() != null) {
                result.updateReading(old.getWaterLevel(), old.getTemperature(),
                        old.getLevelStatus(), old.getLastUpdate());
            }
            if (old.getValveOpen() != null) result.setValveOpen(old.getValveOpen());
            return result;
        }
        throw new IOException("Тип устройства не совпадает с ответом ESP32-S2.");
    }

    @Override public void setRelayState(long requestId, @NonNull RelayDevice device,
            boolean poweredOn, @NonNull Callback<RelayDevice> callback) {
        unsupported(requestId, device, callback);
    }

    @Override public void setLightState(long requestId, @NonNull LightDevice device,
            boolean poweredOn, int brightness, int lightTemperature,
            @NonNull Callback<LightDevice> callback) {
        unsupported(requestId, device, callback);
    }

    @Override public void setValveOpen(long requestId, @NonNull WaterDevice device,
            boolean valveOpen, @NonNull Callback<WaterDevice> callback) {
        unsupported(requestId, device, callback);
    }

    private <T extends Device> void unsupported(long requestId, T device, Callback<T> callback) {
        main.post(() -> callback.onError(requestId, device.getId(),
                new UnsupportedOperationException("Прошивка ESP32-S2 пока не принимает команды управления.")));
    }

    @Override public Subscription subscribeToDevice(@NonNull Device device,
            @NonNull DeviceListener listener) {
        AtomicBoolean cancelled = new AtomicBoolean();
        final boolean[] lastOnline = {device.isOnline()};
        final Device[] lastState = {device};
        ScheduledFuture<?> task = polling.scheduleWithFixedDelay(() -> {
            if (cancelled.get()) return;
            try {
                Device state = makeState(device, readSnapshot(device.getControllerId()));
                main.post(() -> {
                    if (cancelled.get()) return;
                    if (lastOnline[0] != state.isOnline()) {
                        lastOnline[0] = state.isOnline();
                        listener.onAvailabilityChanged(device.getId(), state.isOnline());
                    }
                    if (state.isOnline() && changed(lastState[0], state)) {
                        lastState[0] = state;
                        listener.onStateChanged(state);
                    }
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (cancelled.get()) return;
                    if (lastOnline[0] && (error instanceof IOException
                            || error instanceof SecurityException)) {
                        lastOnline[0] = false;
                        listener.onAvailabilityChanged(device.getId(), false);
                    }
                    listener.onError(device.getId(), error);
                });
            }
        }, 0, 5, TimeUnit.SECONDS);
        return () -> { cancelled.set(true); task.cancel(true); };
    }

    private static boolean changed(Device before, Device after) {
        if (before.isOnline() != after.isOnline()) return true;
        if (before instanceof RelayDevice && after instanceof RelayDevice) {
            return ((RelayDevice) before).isPoweredOn() != ((RelayDevice) after).isPoweredOn();
        }
        if (before instanceof LightDevice && after instanceof LightDevice) {
            LightDevice a = (LightDevice) before;
            LightDevice b = (LightDevice) after;
            return a.isPoweredOn() != b.isPoweredOn() || a.getBrightness() != b.getBrightness();
        }
        if (before instanceof WaterDevice && after instanceof WaterDevice) {
            Integer a = ((WaterDevice) before).getWaterLevel();
            Integer b = ((WaterDevice) after).getWaterLevel();
            return a == null ? b != null : !a.equals(b);
        }
        return true;
    }
}
