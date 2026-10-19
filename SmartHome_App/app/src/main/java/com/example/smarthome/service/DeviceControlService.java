package com.example.smarthome.service;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.smarthome.data.HomeRepository;
import com.example.smarthome.data.RepositoryCallback;
import com.example.smarthome.integration.ControllerGateway;
import com.example.smarthome.model.Device;
import com.example.smarthome.model.LightDevice;
import com.example.smarthome.model.RelayDevice;
import com.example.smarthome.model.Room;
import com.example.smarthome.model.WaterDevice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeoutException;

/**
 * Один общий сервис для приложения. Вызовы и callbacks выполняются в главном
 * потоке; обмен и SQL выполняют асинхронно gateway и repository.
 * Переданные модели не изменяются. USB/Wi-Fi здесь не реализуются.
 */
public class DeviceControlService
{
    private final HomeRepository repository;
    private final ControllerGateway gateway;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Integer> activeDeviceIds = new HashSet<Integer>();
    private final Map<Integer, DeviceWatch> watches = new HashMap<Integer, DeviceWatch>();
    private final long requestTimeoutMillis;
    private final long connectionTimeoutMillis;
    private static long nextRequestId = 1L;
    private ConnectRequest activeConnection;

    public DeviceControlService(@NonNull HomeRepository repository,
                                @NonNull ControllerGateway gateway)
    {
        this(repository, gateway, 10000L, 120000L);
    }

    // Тесты из того же пакета могут уменьшить время ожидания.
    DeviceControlService(HomeRepository repository, ControllerGateway gateway,
                         long requestTimeoutMillis, long connectionTimeoutMillis)
    {
        if (repository == null || gateway == null)
        {
            throw new IllegalArgumentException("Репозиторий и gateway не должны быть null.");
        }
        if (requestTimeoutMillis <= 0 || connectionTimeoutMillis <= 0)
        {
            throw new IllegalArgumentException("Время ожидания должно быть положительным.");
        }
        this.repository = repository;
        this.gateway = gateway;
        this.requestTimeoutMillis = requestTimeoutMillis;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
    }

    private void checkMainThread()
    {
        if (Looper.myLooper() != Looper.getMainLooper())
        {
            throw new IllegalStateException("Метод сервиса должен вызываться в главном потоке.");
        }
    }

    private long createRequestId()
    {
        checkMainThread();
        if (nextRequestId == Long.MAX_VALUE)
        {
            throw new IllegalStateException("Исчерпан диапазон номеров запросов.");
        }
        long result = nextRequestId;
        nextRequestId = nextRequestId + 1L;
        return result;
    }

    private void checkPercentage(int value, String fieldName)
    {
        if (value < 0 || value > 100)
        {
            throw new IllegalArgumentException(fieldName + ": значение должно быть от 0 до 100.");
        }
    }

    private String checkText(String value, String fieldName)
    {
        if (value == null || value.trim().isEmpty())
        {
            throw new IllegalArgumentException(fieldName + " не должно быть пустым.");
        }
        return value.trim();
    }

    private Exception checkedError(Exception error)
    {
        if (error == null)
        {
            return new IllegalStateException("Gateway не передал описание ошибки.");
        }
        return error;
    }

    private <T extends Device> void postError(final Callback<T> callback, final Exception error)
    {
        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onError(error);
            }
        });
    }

    private <T extends Device> boolean checkRequest(Device device, Callback<T> callback)
    {
        checkMainThread();
        if (callback == null)
        {
            throw new IllegalArgumentException("Обработчик результата не должен быть null.");
        }
        if (device == null)
        {
            postError(callback, new IllegalArgumentException("Устройство не должно быть null."));
            return false;
        }
        if (activeDeviceIds.contains(device.getId()))
        {
            postError(callback, new IllegalStateException("Операция для устройства ещё не завершена."));
            return false;
        }
        return true;
    }

    @MainThread
    public void setRelayState(@NonNull RelayDevice device, boolean poweredOn,
                              @NonNull Callback<RelayDevice> callback)
    {
        if (!checkRequest(device, callback))
        {
            return;
        }
        new RelayRequest(copyRelayDevice(device), poweredOn, callback).start();
    }

    @MainThread
    public void setLightState(@NonNull LightDevice device, boolean poweredOn,
                              int brightness, int lightTemperature,
                              @NonNull Callback<LightDevice> callback)
    {
        if (!checkRequest(device, callback))
        {
            return;
        }
        try
        {
            checkPercentage(brightness, "Яркость");
            checkPercentage(lightTemperature, "Температура света");
        }
        catch (IllegalArgumentException error)
        {
            postError(callback, error);
            return;
        }
        new LightRequest(copyLightDevice(device), poweredOn, brightness,
                lightTemperature, callback).start();
    }

    @MainThread
    public void setValveOpen(@NonNull WaterDevice device, boolean valveOpen,
                             @NonNull Callback<WaterDevice> callback)
    {
        if (!checkRequest(device, callback))
        {
            return;
        }
        final WaterDevice snapshot;
        try
        {
            snapshot = copyWaterDevice(device);
        }
        catch (IllegalArgumentException error)
        {
            postError(callback, error);
            return;
        }
        new WaterRequest(snapshot, valveOpen, callback).start();
    }

    /** Сохранённое offline не запрещает попытку опроса. */
    @MainThread
    public void readDeviceState(@NonNull Device device, @NonNull Callback<Device> callback)
    {
        if (!checkRequest(device, callback))
        {
            return;
        }
        final Device snapshot;
        try
        {
            snapshot = copyDevice(device);
        }
        catch (IllegalArgumentException error)
        {
            postError(callback, error);
            return;
        }
        new ReadRequest(snapshot, callback).start();
    }

    private RelayDevice copyRelayDevice(RelayDevice source)
    {
        return new RelayDevice(source.getId(), source.getName(), source.getRoomId(),
                source.getControllerId(), source.isOnline(), source.isPoweredOn());
    }

    private LightDevice copyLightDevice(LightDevice source)
    {
        return new LightDevice(source.getId(), source.getName(), source.getRoomId(),
                source.getControllerId(), source.isOnline(), source.isPoweredOn(),
                source.getBrightness(), source.getLightTemperature());
    }

    private WaterDevice copyWaterDevice(WaterDevice source)
    {
        WaterDevice result = new WaterDevice(source.getId(), source.getName(),
                source.getRoomId(), source.getControllerId(), source.isOnline());
        Integer level = source.getWaterLevel();
        Double temperature = source.getTemperature();
        String status = source.getLevelStatus();
        Long time = source.getLastUpdate();
        checkWaterReadings(level, temperature, status, time);
        if (level != null)
        {
            if (temperature == null) result.updateLevelOnly(level);
            else result.updateReading(level, temperature, status, time);
        }
        if (source.getValveOpen() != null)
        {
            result.setValveOpen(source.getValveOpen());
        }
        return result;
    }

    private void checkWaterReadings(Integer level, Double temperature, String status, Long time)
    {
        if (level == null && temperature == null && status == null && time == null)
        {
            return;
        }
        if (level != null && temperature == null && status == null && time == null)
        {
            checkPercentage(level, "Уровень воды");
            return;
        }
        if (level == null || temperature == null || status == null || time == null)
        {
            throw new IllegalArgumentException("Показания воды должны передаваться полным пакетом.");
        }
        checkPercentage(level, "Уровень воды");
        if (Double.isNaN(temperature) || Double.isInfinite(temperature))
        {
            throw new IllegalArgumentException("Температура должна быть конечным числом.");
        }
        checkText(status, "Статус уровня воды");
        if (time <= 0)
        {
            throw new IllegalArgumentException("Время измерения должно быть положительным.");
        }
    }

    private Device copyDevice(Device source)
    {
        if (source instanceof RelayDevice)
        {
            return copyRelayDevice((RelayDevice) source);
        }
        if (source instanceof LightDevice)
        {
            return copyLightDevice((LightDevice) source);
        }
        if (source instanceof WaterDevice)
        {
            return copyWaterDevice((WaterDevice) source);
        }
        throw new IllegalArgumentException("Тип устройства не поддерживается.");
    }

    private void checkIdentity(Device expected, Device actual)
    {
        if (actual == null || actual.getClass() != expected.getClass()
                || actual.getId() != expected.getId()
                || actual.getRoomId() != expected.getRoomId()
                || !actual.getControllerId().equals(expected.getControllerId()))
        {
            throw new IllegalStateException("Получено состояние другого устройства или типа.");
        }
    }

    /** Общая часть четырёх запросов: ожидание, проверка ответа и сохранение. */
    private abstract class DeviceRequest<T extends Device>
            implements ControllerGateway.Callback<T>, Runnable
    {
        final long requestId = createRequestId();
        final T device;
        final Callback<T> callback;
        private boolean waiting = true;
        private boolean finished;

        DeviceRequest(T device, Callback<T> callback)
        {
            this.device = device;
            this.callback = callback;
        }

        abstract void send();
        abstract T validate(T response);
        abstract T savedResult(Device result);

        final void start()
        {
            activeDeviceIds.add(device.getId());
            mainHandler.postDelayed(this, requestTimeoutMillis);
            try
            {
                send();
            }
            catch (RuntimeException error)
            {
                if (waiting && !finished)
                {
                    fail(error);
                }
            }
        }

        @Override
        public final void onSuccess(long responseRequestId, @NonNull T response)
        {
            checkMainThread();
            if (!waiting || finished)
            {
                return;
            }
            final T confirmed;
            try
            {
                if (responseRequestId != requestId)
                {
                    throw new IllegalStateException("Неверный номер запроса в ответе.");
                }
                checkIdentity(device, response);
                confirmed = validate(response);
            }
            catch (RuntimeException error)
            {
                fail(error);
                return;
            }
            waiting = false;
            mainHandler.removeCallbacks(this);
            DeviceWatch watch = watches.get(device.getId());
            if (watch != null)
            {
                // Ответ свежее событий, доставленных до него.
                watch.discardPending();
            }
            saveState(confirmed, new RepositoryCallback<Device>()
            {
                @Override
                public void onSuccess(@NonNull Device result)
                {
                    final T saved;
                    try
                    {
                        saved = savedResult(result);
                    }
                    catch (RuntimeException error)
                    {
                        saveFailed(confirmed, error);
                        return;
                    }
                    if (!finish())
                    {
                        return;
                    }
                    mainHandler.post(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            callback.onSuccess(saved);
                        }
                    });
                }

                @Override
                public void onError(@NonNull Exception error)
                {
                    saveFailed(confirmed, error);
                }
            });
        }

        @Override
        public final void onError(long responseRequestId, int deviceId, @NonNull Exception error)
        {
            checkMainThread();
            if (!waiting || finished)
            {
                return;
            }
            if (responseRequestId != requestId || deviceId != device.getId())
            {
                fail(new IllegalStateException("Ошибка другого запроса или устройства."));
                return;
            }
            fail(checkedError(error));
        }

        @Override
        public final void run()
        {
            if (waiting && !finished)
            {
                fail(new TimeoutException("Контроллер не ответил вовремя. Результат команды неизвестен."));
            }
        }

        private boolean finish()
        {
            checkMainThread();
            if (finished)
            {
                return false;
            }
            finished = true;
            waiting = false;
            mainHandler.removeCallbacks(this);
            releaseDevice(device.getId());
            return true;
        }

        private void fail(Exception error)
        {
            if (finish())
            {
                postError(callback, error);
            }
        }

        private void saveFailed(final T confirmed, final Exception error)
        {
            if (!finish())
            {
                return;
            }
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    callback.onStateSaveError(confirmed, error);
                }
            });
        }
    }

    private class RelayRequest extends DeviceRequest<RelayDevice>
    {
        private final boolean poweredOn;

        RelayRequest(RelayDevice device, boolean poweredOn, Callback<RelayDevice> callback)
        {
            super(device, callback);
            this.poweredOn = poweredOn;
        }

        @Override
        void send()
        {
            gateway.setRelayState(requestId, copyRelayDevice(device), poweredOn, this);
        }

        @Override
        RelayDevice validate(RelayDevice response)
        {
            if (!response.isOnline() || response.isPoweredOn() != poweredOn)
            {
                throw new IllegalStateException("Контроллер не подтвердил питание реле.");
            }
            return copyRelayDevice(response);
        }

        @Override
        RelayDevice savedResult(Device result)
        {
            return (RelayDevice) result;
        }
    }

    private class LightRequest extends DeviceRequest<LightDevice>
    {
        private final boolean poweredOn;
        private final int brightness;
        private final int lightTemperature;

        LightRequest(LightDevice device, boolean poweredOn, int brightness,
                     int lightTemperature, Callback<LightDevice> callback)
        {
            super(device, callback);
            this.poweredOn = poweredOn;
            this.brightness = brightness;
            this.lightTemperature = lightTemperature;
        }

        @Override
        void send()
        {
            gateway.setLightState(requestId, copyLightDevice(device), poweredOn,
                    brightness, lightTemperature, this);
        }

        @Override
        LightDevice validate(LightDevice response)
        {
            if (!response.isOnline() || response.isPoweredOn() != poweredOn
                    || response.getBrightness() != brightness
                    || response.getLightTemperature() != lightTemperature)
            {
                throw new IllegalStateException("Контроллер не подтвердил настройки света.");
            }
            return copyLightDevice(response);
        }

        @Override
        LightDevice savedResult(Device result)
        {
            return (LightDevice) result;
        }
    }

    private class WaterRequest extends DeviceRequest<WaterDevice>
    {
        private final boolean valveOpen;

        WaterRequest(WaterDevice device, boolean valveOpen, Callback<WaterDevice> callback)
        {
            super(device, callback);
            this.valveOpen = valveOpen;
        }

        @Override
        void send()
        {
            gateway.setValveOpen(requestId, copyWaterDevice(device), valveOpen, this);
        }

        @Override
        WaterDevice validate(WaterDevice response)
        {
            if (!response.isOnline() || response.getValveOpen() == null
                    || response.getValveOpen().booleanValue() != valveOpen)
            {
                throw new IllegalStateException("Контроллер не подтвердил положение клапана.");
            }
            return copyWaterDevice(response);
        }

        @Override
        WaterDevice savedResult(Device result)
        {
            return (WaterDevice) result;
        }
    }

    private class ReadRequest extends DeviceRequest<Device>
    {
        ReadRequest(Device device, Callback<Device> callback)
        {
            super(device, callback);
        }

        @Override
        void send()
        {
            gateway.readDeviceState(requestId, copyDevice(device), this);
        }

        @Override
        Device validate(Device response)
        {
            return copyDevice(response);
        }

        @Override
        Device savedResult(Device result)
        {
            return result;
        }
    }

    /**
     * Проверяем запись и не затираем более свежие показания воды.
     * Сервис сериализует свои записи; показания не следует одновременно
     * сохранять через репозиторий в обход сервиса.
     */
    private void saveState(final Device confirmed, final RepositoryCallback<Device> callback)
    {
        try
        {
            repository.getDevices(new RepositoryCallback<List<Device>>()
            {
                @Override
                public void onSuccess(@NonNull List<Device> devices)
                {
                    Device stored = null;
                    for (Device item : devices)
                    {
                        if (item.getId() == confirmed.getId())
                        {
                            stored = item;
                            break;
                        }
                    }
                    try
                    {
                        if (stored == null)
                        {
                            throw new IllegalStateException("Устройство уже удалено из базы.");
                        }
                        checkIdentity(stored, confirmed);
                        writeState(stored, confirmed, callback);
                    }
                    catch (RuntimeException error)
                    {
                        callback.onError(error);
                    }
                }

                @Override
                public void onError(@NonNull Exception error)
                {
                    callback.onError(error);
                }
            });
        }
        catch (RuntimeException error)
        {
            callback.onError(error);
        }
    }

    private <T extends Device> RepositoryCallback<T> forwardResult(
            final RepositoryCallback<Device> callback)
    {
        return new RepositoryCallback<T>()
        {
            @Override
            public void onSuccess(@NonNull T result)
            {
                callback.onSuccess(result);
            }

            @Override
            public void onError(@NonNull Exception error)
            {
                callback.onError(error);
            }
        };
    }

    private void writeState(Device stored, Device confirmed, RepositoryCallback<Device> callback)
    {
        if (!confirmed.isOnline())
        {
            // Offline не является новым измерением или подтверждением питания.
            repository.updateDeviceOnline(confirmed.getId(), false, callback);
        }
        else if (confirmed instanceof RelayDevice)
        {
            RelayDevice relay = (RelayDevice) confirmed;
            repository.updateRelayState(relay.getId(), true, relay.isPoweredOn(),
                    this.<RelayDevice>forwardResult(callback));
        }
        else if (confirmed instanceof LightDevice)
        {
            LightDevice light = (LightDevice) confirmed;
            repository.updateLightState(light.getId(), true, light.isPoweredOn(),
                    light.getBrightness(), light.getLightTemperature(),
                    this.<LightDevice>forwardResult(callback));
        }
        else if (confirmed instanceof WaterDevice)
        {
            WaterDevice water = (WaterDevice) confirmed;
            WaterDevice previous = (WaterDevice) stored;
            Integer level = water.getWaterLevel();
            Double temperature = water.getTemperature();
            String status = water.getLevelStatus();
            Long time = water.getLastUpdate();
            if (time != null && previous.getLastUpdate() != null
                    && time <= previous.getLastUpdate())
            {
                level = null;
                temperature = null;
                status = null;
                time = null;
            }
            repository.updateWaterState(water.getId(), true, level, temperature,
                    status, time, water.getValveOpen(), this.<WaterDevice>forwardResult(callback));
        }
        else
        {
            throw new IllegalArgumentException("Тип устройства не поддерживается.");
        }
    }

    private void releaseDevice(final int deviceId)
    {
        activeDeviceIds.remove(deviceId);
        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                DeviceWatch watch = watches.get(deviceId);
                if (watch != null)
                {
                    watch.drain();
                }
            }
        });
    }

    /**
     * onSuccess вызывается после каждого сохранённого обновления.
     * Отмена прекращает уведомления, но не откатывает начатую запись.
     */
    @MainThread
    @NonNull
    public ControllerGateway.Subscription subscribeToDevice(
            @NonNull Device device, @NonNull Callback<Device> callback)
    {
        checkMainThread();
        if (device == null || callback == null)
        {
            throw new IllegalArgumentException("Устройство и обработчик не должны быть null.");
        }
        Device snapshot = copyDevice(device);
        DeviceWatch watch = watches.get(device.getId());
        boolean mustStart = false;
        if (watch == null)
        {
            watch = new DeviceWatch(snapshot);
            watches.put(device.getId(), watch);
            mustStart = true;
        }
        else
        {
            checkIdentity(watch.device, snapshot);
        }
        Observer observer = new Observer(watch, callback);
        watch.observers.add(observer);
        if (mustStart)
        {
            watch.start();
        }
        return observer;
    }

    private class Observer implements ControllerGateway.Subscription
    {
        private final DeviceWatch watch;
        private Callback<Device> callback;

        Observer(DeviceWatch watch, Callback<Device> callback)
        {
            this.watch = watch;
            this.callback = callback;
        }

        @Override
        public void cancel()
        {
            checkMainThread();
            if (callback == null)
            {
                return;
            }
            callback = null;
            watch.observers.remove(this);
            if (watch.observers.isEmpty())
            {
                watch.stop();
            }
        }

        void deliver(final Device result, final Exception error, final boolean saveError)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    if (callback == null)
                    {
                        return;
                    }
                    if (error == null)
                    {
                        callback.onSuccess(copyDevice(result));
                    }
                    else if (saveError)
                    {
                        callback.onStateSaveError(copyDevice(result), error);
                    }
                    else
                    {
                        callback.onError(error);
                    }
                }
            });
        }
    }

    private class DeviceWatch implements ControllerGateway.DeviceListener
    {
        private final Device device;
        private final List<Observer> observers = new ArrayList<Observer>();
        private ControllerGateway.Subscription subscription;
        private Device pendingState;
        private Boolean pendingOnline;
        private long stateOrder;
        private long onlineOrder;
        private long eventOrder;
        private boolean stopped;

        DeviceWatch(Device device)
        {
            this.device = device;
        }

        void start()
        {
            try
            {
                subscription = gateway.subscribeToDevice(copyDevice(device), this);
                if (subscription == null)
                {
                    throw new IllegalStateException("Gateway не вернул подписку.");
                }
                if (stopped)
                {
                    subscription.cancel();
                    subscription = null;
                }
            }
            catch (RuntimeException error)
            {
                notifyObservers(null, error, false);
                stop();
            }
        }

        void stop()
        {
            stopped = true;
            discardPending();
            if (watches.get(device.getId()) == this)
            {
                watches.remove(device.getId());
            }
            if (subscription != null)
            {
                try
                {
                    subscription.cancel();
                }
                finally
                {
                    subscription = null;
                }
            }
        }

        void discardPending()
        {
            pendingState = null;
            pendingOnline = null;
        }

        @Override
        public void onStateChanged(@NonNull Device state)
        {
            checkMainThread();
            if (stopped)
            {
                return;
            }
            try
            {
                checkIdentity(device, state);
                pendingState = copyDevice(state);
                eventOrder = eventOrder + 1L;
                stateOrder = eventOrder;
            }
            catch (RuntimeException error)
            {
                notifyObservers(null, error, false);
                return;
            }
            drain();
        }

        @Override
        public void onAvailabilityChanged(int deviceId, boolean online)
        {
            checkMainThread();
            if (stopped)
            {
                return;
            }
            if (deviceId != device.getId())
            {
                notifyObservers(null, new IllegalStateException("Событие другого устройства."), false);
                return;
            }
            pendingOnline = online;
            eventOrder = eventOrder + 1L;
            onlineOrder = eventOrder;
            drain();
        }

        @Override
        public void onError(int deviceId, @NonNull Exception error)
        {
            checkMainThread();
            if (stopped)
            {
                return;
            }
            if (deviceId != device.getId())
            {
                notifyObservers(null, new IllegalStateException("Ошибка другого устройства."), false);
                return;
            }
            notifyObservers(null, checkedError(error), false);
        }

        void drain()
        {
            if (stopped || activeDeviceIds.contains(device.getId()))
            {
                return;
            }
            if (pendingState == null && pendingOnline == null)
            {
                return;
            }
            boolean availabilityOnly = false;
            final Device event;
            if (pendingOnline != null && (pendingState == null || onlineOrder < stateOrder))
            {
                event = copyDevice(device);
                event.setOnline(pendingOnline);
                pendingOnline = null;
                availabilityOnly = true;
            }
            else
            {
                event = pendingState;
                pendingState = null;
            }
            activeDeviceIds.add(device.getId());
            RepositoryCallback<Device> result = new RepositoryCallback<Device>()
            {
                private boolean delivered;

                @Override
                public void onSuccess(@NonNull Device saved)
                {
                    if (delivered)
                    {
                        return;
                    }
                    delivered = true;
                    releaseDevice(device.getId());
                    if (!stopped)
                    {
                        notifyObservers(saved, null, false);
                    }
                }

                @Override
                public void onError(@NonNull Exception error)
                {
                    if (delivered)
                    {
                        return;
                    }
                    delivered = true;
                    releaseDevice(device.getId());
                    if (!stopped)
                    {
                        notifyObservers(event, error, true);
                    }
                }
            };
            if (availabilityOnly)
            {
                // Доступность не является новым измерением.
                try
                {
                    repository.updateDeviceOnline(device.getId(), event.isOnline(), result);
                }
                catch (RuntimeException error)
                {
                    result.onError(error);
                }
            }
            else
            {
                saveState(event, result);
            }
        }

        void notifyObservers(Device result, Exception error, boolean saveError)
        {
            for (Observer observer : new ArrayList<Observer>(observers))
            {
                observer.deliver(result, error, saveError);
            }
        }
    }

    /** Имя и комнату выбирает пользователь; id назначается только базой. */
    @MainThread
    @NonNull
    public ControllerGateway.ConnectionAttempt connectDevice(
            @NonNull String name, int roomId, @NonNull String ssid,
            @NonNull String password, @NonNull ConnectionCallback callback)
    {
        checkMainThread();
        if (callback == null)
        {
            throw new IllegalArgumentException("Обработчик подключения не должен быть null.");
        }
        ConnectRequest request = new ConnectRequest(callback);
        if (activeConnection != null)
        {
            request.fail(new IllegalStateException("Другое подключение ещё не завершено."));
            return request;
        }
        try
        {
            request.name = checkText(name, "Название устройства");
            if (roomId <= 0)
            {
                throw new IllegalArgumentException("Идентификатор комнаты должен быть положительным.");
            }
            if (ssid == null || ssid.isEmpty() || password == null)
            {
                throw new IllegalArgumentException("Укажите SSID и пароль; для открытой сети пароль пустой.");
            }
        }
        catch (RuntimeException error)
        {
            request.fail(error);
            return request;
        }
        request.roomId = roomId;
        activeConnection = request;
        request.start(ssid, password, null);
        return request;
    }

    /** Finds ESP controllers on the current Wi-Fi network that are absent from the database. */
    @MainThread
    @NonNull
    public ControllerGateway.ConnectionAttempt scanUnpairedDevices(@NonNull ScanCallback callback)
    {
        checkMainThread();
        if (callback == null) throw new IllegalArgumentException("Обработчик поиска не должен быть null.");
        AtomicBoolean cancelled = new AtomicBoolean();
        final ControllerGateway.ConnectionAttempt transport;
        try
        {
            transport = gateway.scanDevices(new ControllerGateway.ScanCallback()
            {
                @Override public void onSuccess(@NonNull List<ControllerGateway.DiscoveredDevice> found)
                {
                    if (cancelled.get()) return;
                    repository.getDevices(new RepositoryCallback<List<Device>>()
                    {
                        @Override public void onSuccess(@NonNull List<Device> saved)
                        {
                            if (!cancelled.get()) callback.onSuccess(DiscoveryFilter.unpaired(found, saved));
                        }

                        @Override public void onError(@NonNull Exception error)
                        {
                            if (!cancelled.get()) callback.onError(error);
                        }
                    });
                }

                @Override public void onError(@NonNull Exception error)
                {
                    if (!cancelled.get()) callback.onError(error);
                }
            });
        }
        catch (RuntimeException error)
        {
            mainHandler.post(() -> { if (!cancelled.get()) callback.onError(error); });
            return () -> cancelled.set(true);
        }
        return () -> {
            cancelled.set(true);
            if (transport != null) transport.cancel();
        };
    }

    /** Adds an ESP that already has Wi-Fi credentials; no USB access is needed. */
    @MainThread
    @NonNull
    public ControllerGateway.ConnectionAttempt addDiscoveredDevice(@NonNull String name, int roomId,
            @NonNull String controllerId, @NonNull ConnectionCallback callback)
    {
        checkMainThread();
        if (callback == null) throw new IllegalArgumentException("Обработчик подключения не должен быть null.");
        ConnectRequest request = new ConnectRequest(callback);
        if (activeConnection != null)
        {
            request.fail(new IllegalStateException("Другое подключение ещё не завершено."));
            return request;
        }
        try
        {
            request.name = checkText(name, "Название устройства");
            if (roomId <= 0) throw new IllegalArgumentException("Идентификатор комнаты должен быть положительным.");
            controllerId = checkText(controllerId, "Идентификатор контроллера");
        }
        catch (RuntimeException error)
        {
            request.fail(error);
            return request;
        }
        request.roomId = roomId;
        activeConnection = request;
        request.start(null, null, controllerId);
        return request;
    }

    public interface ScanCallback
    {
        @MainThread void onSuccess(@NonNull List<ControllerGateway.DiscoveredDevice> devices);
        @MainThread void onError(@NonNull Exception error);
    }

    /** Подтверждённые данные до назначения id базой. SSID/пароль здесь не хранятся. */
    public static final class ConnectionInfo
    {
        private final String type;
        private final String controllerId;
        private final Boolean poweredOn;
        private final Integer brightness;
        private final Integer lightTemperature;
        private final Integer waterLevel;
        private final Double temperature;
        private final String levelStatus;
        private final Long lastUpdate;
        private final Boolean valveOpen;

        private ConnectionInfo(String type, String controllerId, Boolean poweredOn,
                               Integer brightness, Integer lightTemperature, Integer waterLevel,
                               Double temperature, String levelStatus, Long lastUpdate, Boolean valveOpen)
        {
            this.type = type;
            this.controllerId = controllerId;
            this.poweredOn = poweredOn;
            this.brightness = brightness;
            this.lightTemperature = lightTemperature;
            this.waterLevel = waterLevel;
            this.temperature = temperature;
            this.levelStatus = levelStatus;
            this.lastUpdate = lastUpdate;
            this.valveOpen = valveOpen;
        }

        public String getType()
        {
            return type;
        }
        public String getControllerId()
        {
            return controllerId;
        }
        @Nullable
        public Boolean getPoweredOn()
        {
            return poweredOn;
        }
        @Nullable
        public Integer getBrightness()
        {
            return brightness;
        }
        @Nullable
        public Integer getLightTemperature()
        {
            return lightTemperature;
        }
        @Nullable
        public Integer getWaterLevel()
        {
            return waterLevel;
        }
        @Nullable
        public Double getTemperature()
        {
            return temperature;
        }
        @Nullable
        public String getLevelStatus()
        {
            return levelStatus;
        }
        @Nullable
        public Long getLastUpdate()
        {
            return lastUpdate;
        }
        @Nullable
        public Boolean getValveOpen()
        {
            return valveOpen;
        }
    }

    private class ConnectRequest implements ControllerGateway.ConnectionCallback,
            ControllerGateway.ConnectionAttempt, Runnable
    {
        private final long requestId = createRequestId();
        private ConnectionCallback callback;
        private ControllerGateway.ConnectionAttempt attempt;
        private String name;
        private int roomId;
        private boolean waiting = true;
        private boolean checkingDuplicate;
        private boolean finished;
        private boolean cancelled;

        ConnectRequest(ConnectionCallback callback)
        {
            this.callback = callback;
        }

        void start(final String ssid, final String password, final String controllerId)
        {
            mainHandler.postDelayed(this, connectionTimeoutMillis);
            try
            {
                repository.getRooms(new RepositoryCallback<List<Room>>()
                {
                    @Override
                    public void onSuccess(@NonNull List<Room> rooms)
                    {
                        if (!waiting || finished || cancelled)
                        {
                            return;
                        }
                        boolean found = false;
                        for (Room room : rooms)
                        {
                            if (room.getId() == roomId)
                            {
                                found = true;
                                break;
                            }
                        }
                        if (!found)
                        {
                            fail(new IllegalArgumentException("Выбранная комната отсутствует."));
                            return;
                        }
                        try
                        {
                            attempt = controllerId == null
                                    ? gateway.connectDevice(requestId, ssid, password, ConnectRequest.this)
                                    : gateway.connectDiscoveredDevice(requestId, controllerId, ConnectRequest.this);
                            if (attempt == null && waiting && !finished)
                            {
                                fail(new IllegalStateException("Gateway не вернул объект подключения."));
                            }
                            if (cancelled || finished)
                            {
                                cancelTransport();
                            }
                        }
                        catch (RuntimeException error)
                        {
                            if (waiting && !finished)
                            {
                                fail(error);
                            }
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception error)
                    {
                        fail(error);
                    }
                });
            }
            catch (RuntimeException error)
            {
                fail(error);
            }
        }

        boolean accept(long responseId)
        {
            checkMainThread();
            if (!waiting || checkingDuplicate || finished || cancelled)
            {
                return false;
            }
            if (responseId != requestId)
            {
                fail(new IllegalStateException("Неверный номер запроса подключения."));
                return false;
            }
            return true;
        }

        @Override
        public void onProgress(long responseId, final ControllerGateway.ConnectionStage stage,
                               final Integer progressPercent)
        {
            if (!accept(responseId))
            {
                return;
            }
            if (stage == null || (progressPercent != null
                    && (progressPercent < 0 || progressPercent > 100)))
            {
                fail(new IllegalStateException("Некорректный этап или процент подключения."));
                return;
            }
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    if (callback != null && !cancelled && !finished)
                    {
                        callback.onProgress(stage, progressPercent);
                    }
                }
            });
        }

        @Override
        public void onLightConnected(long responseId, String controllerId, boolean poweredOn,
                                     int brightness, int lightTemperature)
        {
            if (!accept(responseId))
            {
                return;
            }
            try
            {
                String address = checkText(controllerId, "Идентификатор контроллера");
                checkPercentage(brightness, "Яркость");
                checkPercentage(lightTemperature, "Температура света");
                save(new ConnectionInfo("light", address, poweredOn, brightness,
                        lightTemperature, null, null, null, null, null));
            }
            catch (RuntimeException error)
            {
                fail(error);
            }
        }

        @Override
        public void onRelayConnected(long responseId, String controllerId, boolean poweredOn)
        {
            if (!accept(responseId))
            {
                return;
            }
            try
            {
                String address = checkText(controllerId, "Идентификатор контроллера");
                save(new ConnectionInfo("relay", address, poweredOn, null,
                        null, null, null, null, null, null));
            }
            catch (RuntimeException error)
            {
                fail(error);
            }
        }

        @Override
        public void onWaterConnected(long responseId, String controllerId, Integer waterLevel,
                                     Double temperature, String levelStatus, Long lastUpdate, Boolean valveOpen)
        {
            if (!accept(responseId))
            {
                return;
            }
            try
            {
                String address = checkText(controllerId, "Идентификатор контроллера");
                checkWaterReadings(waterLevel, temperature, levelStatus, lastUpdate);
                String status = levelStatus;
                if (status != null)
                {
                    status = status.trim();
                }
                save(new ConnectionInfo("water", address, null, null, null,
                        waterLevel, temperature, status, lastUpdate, valveOpen));
            }
            catch (RuntimeException error)
            {
                fail(error);
            }
        }

        void save(final ConnectionInfo info)
        {
            checkingDuplicate = true;
            try
            {
                repository.getDevices(new RepositoryCallback<List<Device>>()
                {
                    @Override public void onSuccess(@NonNull List<Device> saved)
                    {
                        if (!waiting || finished || cancelled) return;
                        if (DiscoveryFilter.isPaired(info.controllerId, saved))
                        {
                            fail(new IllegalStateException("Это устройство уже добавлено в приложение."));
                            return;
                        }
                        saveConfirmed(info);
                    }

                    @Override public void onError(@NonNull Exception error)
                    {
                        fail(error);
                    }
                });
            }
            catch (RuntimeException error)
            {
                fail(error);
            }
        }

        void saveConfirmed(final ConnectionInfo info)
        {
            waiting = false;
            mainHandler.removeCallbacks(this);
            RepositoryCallback<Device> result = new RepositoryCallback<Device>()
            {
                @Override
                public void onSuccess(@NonNull final Device device)
                {
                    if (!finish())
                    {
                        return;
                    }
                    deliverConnectionResult(device, info, null);
                }

                @Override
                public void onError(@NonNull final Exception error)
                {
                    if (!finish())
                    {
                        return;
                    }
                    deliverConnectionResult(null, info, error);
                }
            };
            try
            {
                if ("relay".equals(info.type))
                {
                    repository.addRelayDevice(name, roomId, info.controllerId, true,
                            info.poweredOn, DeviceControlService.this.<RelayDevice>forwardResult(result));
                }
                else if ("light".equals(info.type))
                {
                    repository.addLightDevice(name, roomId, info.controllerId, true,
                            info.poweredOn, info.brightness, info.lightTemperature,
                            DeviceControlService.this.<LightDevice>forwardResult(result));
                }
                else
                {
                    repository.addWaterDevice(name, roomId, info.controllerId, true,
                            info.waterLevel, info.temperature, info.levelStatus, info.lastUpdate,
                            info.valveOpen, DeviceControlService.this.<WaterDevice>forwardResult(result));
                }
            }
            catch (RuntimeException error)
            {
                result.onError(error);
            }
        }

        void deliverConnectionResult(final Device device, final ConnectionInfo info,
                                     final Exception error)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    ConnectionCallback target = callback;
                    callback = null;
                    if (cancelled || target == null)
                    {
                        return;
                    }
                    if (error == null)
                    {
                        target.onSuccess(device);
                    }
                    else
                    {
                        target.onStateSaveError(info, error);
                    }
                }
            });
        }

        @Override
        public void onError(long responseId, @NonNull Exception error)
        {
            if (accept(responseId))
            {
                fail(checkedError(error));
            }
        }

        @Override
        public void run()
        {
            if (waiting && !finished)
            {
                fail(new TimeoutException("Истекло время подключения. Проверьте состояние устройства."));
            }
        }

        boolean finish()
        {
            if (finished)
            {
                return false;
            }
            finished = true;
            waiting = false;
            mainHandler.removeCallbacks(this);
            if (activeConnection == this)
            {
                activeConnection = null;
            }
            attempt = null;
            return true;
        }

        void fail(final Exception error)
        {
            if (!waiting || finished)
            {
                return;
            }
            // Сначала закрываем приём событий: cancel транспорта может вызвать callback.
            ControllerGateway.ConnectionAttempt previous = attempt;
            if (!finish())
            {
                return;
            }
            cancelAttempt(previous);
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    ConnectionCallback target = callback;
                    callback = null;
                    if (!cancelled && target != null)
                    {
                        target.onError(checkedError(error));
                    }
                }
            });
        }

        void cancelAttempt(ControllerGateway.ConnectionAttempt previous)
        {
            if (previous == null)
            {
                return;
            }
            try
            {
                previous.cancel();
            }
            catch (RuntimeException ignored)
            {
                // Ошибка освобождения не заменяет основную ошибку операции.
            }
        }

        void cancelTransport()
        {
            ControllerGateway.ConnectionAttempt previous = attempt;
            attempt = null;
            cancelAttempt(previous);
        }

        @Override
        public void cancel()
        {
            checkMainThread();
            cancelled = true;
            callback = null;
            cancelTransport();
            if (waiting)
            {
                finish();
            }
            // Начатую запись не откатываем; освобождение activeConnection — после ответа БД.
        }
    }

    public interface Callback<T extends Device>
    {
        @MainThread
        void onSuccess(@NonNull T device);
        @MainThread
        void onError(@NonNull Exception error);
        @MainThread
        void onStateSaveError(@NonNull T confirmedDevice, @NonNull Exception error);
    }

    public interface ConnectionCallback
    {
        @MainThread
        void onProgress(@NonNull ControllerGateway.ConnectionStage stage, @Nullable Integer progressPercent);
        @MainThread
        void onSuccess(@NonNull Device device);
        @MainThread
        void onError(@NonNull Exception error);
        @MainThread
        void onStateSaveError(@NonNull ConnectionInfo confirmedDevice, @NonNull Exception error);
    }
}
