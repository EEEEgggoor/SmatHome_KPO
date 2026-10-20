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

/** Связь с ESP32. Команды 0x02–0x04 требуют соответствующих обработчиков в прошивке. */
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
    // Чтение и команда одной платы публикуются в том же порядке, что выполняются.
    private final ConcurrentHashMap<String, Object> deviceLocks = new ConcurrentHashMap<>();
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
                        finished = true;
                        activeAttempt = null;
                        if (state.type == 0)
                        {
                            // Температуры света в оборудовании нет. Ноль нужен старой модели.
                            callback.onLightConnected(requestId, state.controllerId,
                                    state.poweredOn, state.value, 0);
                        }
                        else if (state.type == 1) {
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

    private Object deviceLock(String controllerId)
    {
        Object newLock = new Object();
        Object existingLock = deviceLocks.putIfAbsent(controllerId, newLock);
        if (existingLock != null)
        {
            return existingLock;
        }
        return newLock;
    }

    private InetSocketAddress addressFor(String controllerId) throws IOException
    {
        InetSocketAddress address = knownAddresses.get(controllerId);
        if (address == null)
        {
            Esp32NetworkClient.Result found = network.findAndRead(controllerId);
            address = found.address;
            knownAddresses.put(controllerId, address);
        }
        return address;
    }

    @Override
    public void readDeviceState(final long requestId, @NonNull final Device device,
                                @NonNull final Callback<Device> callback)
    {
        io.execute(new Runnable()
        {
            @Override
            public void run()
            {
                synchronized (deviceLock(device.getControllerId()))
                {
                    try
                    {
                        Esp32Wire.Snapshot state = readSnapshot(device);
                        final Device result = makeState(device, state);
                        main.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                callback.onSuccess(requestId, result);
                            }
                        });
                    }
                    catch (final Exception error)
                    {
                        postError(requestId, device, callback, error);
                    }
                }
            }
        });
    }

    private Esp32Wire.Snapshot readSnapshot(Device device) throws IOException
    {
        String controllerId = device.getControllerId();
        try
        {
            InetSocketAddress address = addressFor(controllerId);
            if (device instanceof WaterDevice)
            {
                // Отдельная команда обновляет показания воды, а не настройки устройства.
                return network.readWaterLevel(address, controllerId);
            }
            Esp32Wire.Snapshot state = network.read(address);
            if (!controllerId.equals(state.controllerId))
            {
                throw new IOException("Ответ получен от другого контроллера.");
            }
            return state;
        }
        catch (IOException error)
        {
            // Следующий запрос заново найдёт адрес. Текущую команду не повторяем.
            knownAddresses.remove(controllerId);
            throw error;
        }
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

    @Override
    public void setRelayState(final long requestId, @NonNull final RelayDevice device,
                              final boolean poweredOn, @NonNull final Callback<RelayDevice> callback)
    {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(9);
        io.execute(new Runnable()
        {
            @Override
            public void run()
            {
                synchronized (deviceLock(device.getControllerId()))
                {
                    try
                    {
                        InetSocketAddress address = addressFor(device.getControllerId());
                        Esp32Wire.Snapshot state = network.setRelayState(address,
                                device.getControllerId(), poweredOn, deadline);
                        final RelayDevice result = new RelayDevice(device.getId(), device.getName(),
                                device.getRoomId(), device.getControllerId(), state.online, state.poweredOn);
                        main.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                callback.onSuccess(requestId, result);
                            }
                        });
                    }
                    catch (final Exception error)
                    {
                        knownAddresses.remove(device.getControllerId());
                        postError(requestId, device, callback, error);
                    }
                }
            }
        });
    }

    @Override
    public void setLightState(final long requestId, @NonNull final LightDevice device,
                              final boolean poweredOn, final int brightness, final int lightTemperature,
                              @NonNull final Callback<LightDevice> callback)
    {
        if (lightTemperature < 0 || lightTemperature > 100)
        {
            postError(requestId, device, callback,
                    new IllegalArgumentException("Температура света должна быть от 0 до 100."));
            return;
        }
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(9);
        io.execute(new Runnable()
        {
            @Override
            public void run()
            {
                synchronized (deviceLock(device.getControllerId()))
                {
                    try
                    {
                        // Проверяем яркость до поиска и отправки.
                        Esp32Wire.setLightStateRequest(device.getControllerId(), poweredOn, brightness);
                        InetSocketAddress address = addressFor(device.getControllerId());
                        Esp32Wire.Snapshot state = network.setLightState(address,
                                device.getControllerId(), poweredOn, brightness, deadline);
                        // Температуру не отправляем: сохраняем параметр для совместимости сервиса.
                        final LightDevice result = new LightDevice(device.getId(), device.getName(),
                                device.getRoomId(), device.getControllerId(), state.online,
                                state.poweredOn, state.value, lightTemperature);
                        main.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                callback.onSuccess(requestId, result);
                            }
                        });
                    }
                    catch (final Exception error)
                    {
                        knownAddresses.remove(device.getControllerId());
                        postError(requestId, device, callback, error);
                    }
                }
            }
        });
    }

    @Override
    public void setValveOpen(long requestId, @NonNull WaterDevice device,
                             boolean valveOpen, @NonNull Callback<WaterDevice> callback)
    {
        // WaterData.power_on не описывает положение клапана. Не выдаём его за подтверждение.
        postError(requestId, device, callback,
                new UnsupportedOperationException("Протокол управления клапаном ещё не определён."));
    }

    private <T extends Device> void postError(final long requestId, final T device,
                                              final Callback<T> callback, final Exception error)
    {
        main.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onError(requestId, device.getId(), error);
            }
        });
    }

    @Override
    public Subscription subscribeToDevice(@NonNull final Device device,
                                           @NonNull final DeviceListener listener)
    {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final boolean[] lastOnline = new boolean[]{device.isOnline()};
        final ScheduledFuture<?> task = polling.scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                if (cancelled.get())
                {
                    return;
                }
                synchronized (deviceLock(device.getControllerId()))
                {
                    if (cancelled.get())
                    {
                        return;
                    }
                    try
                    {
                        final Device state = makeState(device, readSnapshot(device));
                        main.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                if (cancelled.get())
                                {
                                    return;
                                }
                                if (lastOnline[0] != state.isOnline())
                                {
                                    lastOnline[0] = state.isOnline();
                                    listener.onAvailabilityChanged(device.getId(), state.isOnline());
                                }
                                // После команды состояние меняется и вне этой подписки.
                                // Передаём каждый снимок, даже совпавший с прошлым опросом.
                                if (state.isOnline() && !cancelled.get())
                                {
                                    listener.onStateChanged(state);
                                }
                            }
                        });
                    }
                    catch (final Exception error)
                    {
                        main.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                if (cancelled.get())
                                {
                                    return;
                                }
                                if (lastOnline[0] && (error instanceof IOException
                                        || error instanceof SecurityException))
                                {
                                    lastOnline[0] = false;
                                    listener.onAvailabilityChanged(device.getId(), false);
                                }
                                if (!cancelled.get())
                                {
                                    listener.onError(device.getId(), error);
                                }
                            }
                        });
                    }
                }
            }
        }, 0, 5, TimeUnit.SECONDS);
        return new Subscription()
        {
            @Override
            public void cancel()
            {
                cancelled.set(true);
                task.cancel(true);
            }
        };
    }
}
