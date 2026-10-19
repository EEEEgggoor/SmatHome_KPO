package com.example.smarthome.integration;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.smarthome.model.Device;
import com.example.smarthome.model.LightDevice;
import com.example.smarthome.model.RelayDevice;
import com.example.smarthome.model.WaterDevice;
import java.util.List;

/**
 * Контракт связи приложения с контроллерами.
 *
 * Обмен с оборудованием выполняется вне главного потока.
 * Результаты передаются обработчикам в главном потоке.
 *
 * Gateway не изменяет переданные модели и базу данных.
 * Подтверждённое состояние возвращается отдельной моделью.
 */
public interface ControllerGateway
{
    /** Find controllers already connected to the same local Wi-Fi network. */
    @MainThread
    @NonNull
    default ConnectionAttempt scanDevices(@NonNull ScanCallback callback)
    {
        callback.onError(new UnsupportedOperationException("Поиск в Wi-Fi не поддерживается."));
        return () -> { };
    }

    /** Read a selected controller before adding it, without sending Wi-Fi credentials. */
    @MainThread
    @NonNull
    default ConnectionAttempt connectDiscoveredDevice(long requestId,
            @NonNull String controllerId, @NonNull ConnectionCallback callback)
    {
        callback.onError(requestId, new UnsupportedOperationException("Подключение через Wi-Fi не поддерживается."));
        return () -> { };
    }

    final class DiscoveredDevice
    {
        public final String controllerId;
        public final int type;
        public final String address;

        public DiscoveredDevice(@NonNull String controllerId, int type, @NonNull String address)
        {
            this.controllerId = controllerId;
            this.type = type;
            this.address = address;
        }
    }

    interface ScanCallback
    {
        @MainThread void onSuccess(@NonNull List<DiscoveredDevice> devices);
        @MainThread void onError(@NonNull Exception error);
    }

    /**
     * Запросить состояние существующего устройства.
     * Результат должен иметь тот же тип и идентификаторы.
     */
    void readDeviceState(
            long requestId,
            @NonNull Device device,
            @NonNull Callback<Device> callback
    );

    /**
     * Установить питание и настройки света.
     * Яркость и температура света: от 0 до 100.
     */
    void setLightState(
            long requestId,
            @NonNull LightDevice device,
            boolean poweredOn,
            int brightness,
            int lightTemperature,
            @NonNull Callback<LightDevice> callback
    );

    /**
     * Установить питание реле.
     */
    void setRelayState(
            long requestId,
            @NonNull RelayDevice device,
            boolean poweredOn,
            @NonNull Callback<RelayDevice> callback
    );

    /**
     * Установить положение клапана.
     * true — открыть, false — закрыть.
     */
    void setValveOpen(
            long requestId,
            @NonNull WaterDevice device,
            boolean valveOpen,
            @NonNull Callback<WaterDevice> callback
    );

    /**
     * Начать получение событий существующего устройства.
     * Каждая подписка работает независимо от остальных.
     */
    @MainThread
    @NonNull
    Subscription subscribeToDevice(
            @NonNull Device device,
            @NonNull DeviceListener listener
    );

    /**
     * Подключить новое устройство к сети.
     *
     * Физическое устройство должно быть заранее выбрано
     * или однозначно определено реализацией.
     *
     * На экземпляр gateway допускается одно активное подключение.
     * Параллельная попытка получает ошибку через свой callback.
     *
     * Возвращение ConnectionAttempt ещё не означает успех.
     * SSID и пароль нельзя обрезать через trim().
     * Пароль нельзя записывать в журналы и сообщения ошибок.
     */
    @MainThread
    @NonNull
    ConnectionAttempt connectDevice(
            long requestId,
            @NonNull String ssid,
            @NonNull String password,
            @NonNull ConnectionCallback callback
    );

    /**
     * Результат одного запроса к существующему устройству.
     * Вызывается один метод: onSuccess либо onError.
     */
    interface Callback<T extends Device>
    {
        @MainThread
        void onSuccess(
                long requestId,
                @NonNull T device
        );

        @MainThread
        void onError(
                long requestId,
                int deviceId,
                @NonNull Exception error
        );
    }

    /**
     * Получатель событий существующего устройства.
     */
    interface DeviceListener
    {
        /**
         * Получена отдельная модель с подтверждённым состоянием.
         */
        @MainThread
        void onStateChanged(@NonNull Device device);

        /**
         * Изменилась доступность устройства.
         * Показания и время измерения этим событием не обновляются.
         */
        @MainThread
        void onAvailabilityChanged(
                int deviceId,
                boolean online
        );

        /**
         * Ошибка получения данных.
         * Не означает автоматически offline или отмену подписки.
         */
        @MainThread
        void onError(
                int deviceId,
                @NonNull Exception error
        );
    }

    /**
     * Управление одной подпиской.
     */
    interface Subscription
    {
        /**
         * Прекратить доставку событий и освободить обработчик.
         * Повторный вызов безопасен.
         *
         * После возврата события этой подписки не доставляются,
         * включая события, ранее поставленные в очередь.
         */
        @MainThread
        void cancel();
    }

    /**
     * Возможные этапы первоначального подключения.
     */
    enum ConnectionStage
    {
        SEARCHING_DEVICE,
        SENDING_WIFI_SETTINGS,
        CONNECTING_TO_NETWORK,
        READING_STATE
    }

    /**
     * Получатель этапов и результата подключения.
     *
     * До завершения может быть несколько вызовов onProgress.
     * Итог — один вызов успеха подходящего типа либо onError.
     * После отмены новые вызовы обработчика запрещены.
     *
     * Успех означает получение подтверждённого состояния
     * доступного устройства, но не сохранение его в базе.
     */
    interface ConnectionCallback
    {
        /**
         * Процент относится к текущему этапу.
         * null — процент неизвестен, иначе значение от 0 до 100.
         */
        @MainThread
        void onProgress(
                long requestId,
                @NonNull ConnectionStage stage,
                @Nullable Integer progressPercent
        );

        /**
         * Подключено устройство света.
         */
        @MainThread
        void onLightConnected(
                long requestId,
                @NonNull String controllerId,
                boolean poweredOn,
                int brightness,
                int lightTemperature
        );

        /**
         * Подключено реле.
         */
        @MainThread
        void onRelayConnected(
                long requestId,
                @NonNull String controllerId,
                boolean poweredOn
        );

        /**
         * Подключено устройство воды.
         *
         * Четыре показания: уровень, температура, статус и время —
         * передаются полным пакетом либо все остаются null.
         * Положение клапана может быть неизвестно независимо от них.
         */
        @MainThread
        void onWaterConnected(
                long requestId,
                @NonNull String controllerId,
                @Nullable Integer waterLevel,
                @Nullable Double temperature,
                @Nullable String levelStatus,
                @Nullable Long lastUpdate,
                @Nullable Boolean valveOpen
        );

        /**
         * Подключение завершилось ошибкой.
         * Идентификатор записи устройства в базе пока отсутствует.
         */
        @MainThread
        void onError(
                long requestId,
                @NonNull Exception error
        );
    }

    /**
     * Управление одной попыткой подключения.
     */
    interface ConnectionAttempt
    {
        /**
         * Прекратить попытку подключения и доставку её событий.
         * Повторный вызов безопасен.
         *
         * Уже переданные оборудованию настройки
         * автоматически не откатываются.
         */
        @MainThread
        void cancel();
    }
}
