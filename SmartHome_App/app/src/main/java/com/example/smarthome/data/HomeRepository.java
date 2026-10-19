package com.example.smarthome.data;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.example.smarthome.data.database.RoomDao;
import com.example.smarthome.data.database.RoomEntity;
import com.example.smarthome.data.database.SmartHomeDatabase;
import com.example.smarthome.model.Room;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;

import com.example.smarthome.data.database.DeviceEntity;
import com.example.smarthome.model.Device;
import com.example.smarthome.model.LightDevice;
import com.example.smarthome.model.RelayDevice;
import com.example.smarthome.model.WaterDevice;

import com.example.smarthome.data.database.DeviceDao;
import androidx.annotation.Nullable;

public class HomeRepository {

    private static final ExecutorService DATABASE_EXECUTOR = Executors.newSingleThreadExecutor();
    private final DeviceDao deviceDao;
    private final RoomDao roomDao;
    private final Handler mainHandler;
    private final SmartHomeDatabase database;

    public HomeRepository(@NonNull SmartHomeDatabase database){
        if (database == null){
            throw new IllegalArgumentException(
                    "База данных не должна быть null."
            );
        }

        this.database = database;
        this.roomDao = database.roomDao();
        this.deviceDao = database.deviceDao();
        this.mainHandler = new Handler(Looper.getMainLooper());

    }

    public void getRooms(@NonNull final RepositoryCallback<List<Room>> callback) {
        if (callback == null){
            throw new IllegalArgumentException("Обработчик результата не должен быть null.");
        }

        DATABASE_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                loadRooms(callback);
            }
        });
    }
    private void loadRooms(@NonNull final RepositoryCallback<List<Room>> callback) {
        final List<Room> rooms = new ArrayList<Room>();

        try {
            List<RoomEntity> roomEntities = roomDao.getAll();

            for (RoomEntity roomEntity : roomEntities) {
                Room room = new Room(roomEntity.getId(), roomEntity.getName());
                rooms.add(room);
            }
        } catch (final RuntimeException error) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(error);
                }
            });
            return;
        }

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(rooms);
            }
        });
    }

    public void  addRoom(@NonNull final String name, @NonNull final RepositoryCallback<Room> callback){
        if (callback == null){
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                saveRoom(name,callback);
            }
        });
    }
    private void saveRoom(@NonNull final String name, @NonNull final RepositoryCallback<Room> callback){
        final Room savedRoom;

        try{
            savedRoom = database.runInTransaction(new Callable<Room>() {
                @Override
                public Room call(){
                    RoomEntity roomEntity = new RoomEntity(0, name);

                    long generatedId = roomDao.insert(roomEntity);

                    if (generatedId <= 0){
                        throw new IllegalStateException(
                                "База не вернула корректный идентификатор комнаты."
                        );
                    }

                    if (generatedId > Integer.MAX_VALUE){
                        throw new IllegalStateException(
                                "Идентификатор комнаты превышает допустимый диапазон int."
                        );
                    }

                    int roomId = (int) generatedId;

                    Room room = new Room(roomId, roomEntity.getName());

                    return room;
                }
            });
        }
        catch (final RuntimeException error){
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(savedRoom);
            }
        });
    }

    public void renameRoom(final int roomId, @NonNull final String newName, @NonNull final RepositoryCallback<Room> callback){
        if (callback == null){
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                updateRoomName(roomId, newName, callback);
            }
        });
    }

    private void updateRoomName(final int roomId, @NonNull final String newName, @NonNull final RepositoryCallback<Room> callback){
        final Room updatedRoom;

        try {
            updatedRoom = database.runInTransaction(new Callable<Room>() {
                @Override
                public Room call() {
                    if (roomId <= 0)
                    {
                        throw new IllegalArgumentException(
                                "Идентификатор комнаты должен быть больше нуля."
                        );
                    }

                    RoomEntity roomEntity = roomDao.getById(roomId);

                    if (roomEntity == null)
                    {
                        throw new IllegalArgumentException(
                                "Комната с идентификатором " + roomId + " не найдена."
                        );
                    }

                    roomEntity.setName(newName);

                    int updatedCount = roomDao.update(roomEntity);

                    if (updatedCount != 1)
                    {
                        throw new IllegalStateException(
                                "Не удалось обновить комнату."
                        );
                    }

                    Room room = new Room(
                            roomEntity.getId(),
                            roomEntity.getName()
                    );

                    return room;

                }
            });
        }
        catch (final RuntimeException error){
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onSuccess(updatedRoom);
            }
        });
    }

    public void deleteRoom(final int roomId, @NonNull final RepositoryCallback<Boolean> callback) {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable()
        {
            @Override
            public void run()
            {
                removeRoom(roomId, callback);
            }
        });
    }
    private void removeRoom(final int roomId, @NonNull final RepositoryCallback<Boolean> callback) {
        final boolean deleted;

        try
        {
            if (roomId <= 0)
            {
                throw new IllegalArgumentException(
                        "Идентификатор комнаты должен быть больше нуля."
                );
            }

            int deletedCount = roomDao.deleteById(roomId);

            if (deletedCount == 1)
            {
                deleted = true;
            }
            else
            {
                deleted = false;
            }
        }
        catch (final RuntimeException error)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onSuccess(deleted);
            }
        });
    }
    @NonNull
    private Device toDevice(@NonNull DeviceEntity entity){
        if (entity == null){
            throw new IllegalArgumentException(
                    "Запись устройства не должна быть null."
            );
        }

        String type = entity.getType();

        Boolean poweredOn = entity.getPoweredOn();
        Integer brightness = entity.getBrightness();
        Integer lightTemperature = entity.getLightTemperature();

        Integer waterLevel = entity.getWaterLevel();
        Double temperature = entity.getTemperature();
        String levelStatus = entity.getLevelStatus();
        Long lastUpdate = entity.getLastUpdate();
        Boolean valveOpen = entity.getValveOpen();

        if (DeviceEntity.TYPE_LIGHT.equals(type)){
            if (poweredOn == null || brightness == null || lightTemperature == null){
                throw new IllegalStateException(
                        "В записи света отсутствуют обязательные данные."
                );
            }

            if (waterLevel != null || temperature != null  || levelStatus != null || lastUpdate != null || valveOpen != null){
                throw new IllegalStateException(
                        "Запись света содержит поля водяного устройства."
                );
            }

            LightDevice light = new LightDevice(
                    entity.getId(),
                    entity.getName(),
                    entity.getRoomId(),
                    entity.getControllerId(),
                    entity.isOnline(),
                    poweredOn,
                    brightness,
                    lightTemperature
            );
            return  light;

        }

        else if (DeviceEntity.TYPE_RELAY.equals(type)){
            if (poweredOn == null){
                throw new IllegalStateException(
                        "В записи реле отсутствует состояние питания."
                );
            }
            if (brightness != null
                    || lightTemperature != null
                    || waterLevel != null
                    || temperature != null
                    || levelStatus != null
                    || lastUpdate != null
                    || valveOpen != null)
            {
                throw new IllegalStateException(
                        "Запись реле содержит поля другого типа устройства."
                );
            }
            RelayDevice relay = new RelayDevice(
                    entity.getId(),
                    entity.getName(),
                    entity.getRoomId(),
                    entity.getControllerId(),
                    entity.isOnline(),
                    poweredOn
            );

            return relay;
        }

        else if (DeviceEntity.TYPE_WATER.equals(type)){
            if (poweredOn != null
                    || brightness != null
                    || lightTemperature != null)
            {
                throw new IllegalStateException(
                        "Запись водяного устройства содержит поля света или реле."
                );
            }

            WaterDevice water = new WaterDevice(
                    entity.getId(),
                    entity.getName(),
                    entity.getRoomId(),
                    entity.getControllerId(),
                    entity.isOnline()
            );

            boolean hasAnyReading = false;

            if (waterLevel != null
                    || temperature != null
                    || levelStatus != null
                    || lastUpdate != null)
            {
                hasAnyReading = true;
            }

            if (hasAnyReading){
                boolean levelOnly = waterLevel != null && temperature == null
                        && levelStatus == null && lastUpdate == null;
                boolean complete = waterLevel != null && temperature != null
                        && levelStatus != null && lastUpdate != null;
                if (!levelOnly && !complete)
                {
                    throw new IllegalStateException(
                            "В записи водяного устройства неполный набор показаний."
                    );
                }
                if (levelOnly) water.updateLevelOnly(waterLevel);
                else water.updateReading(waterLevel,temperature,levelStatus,lastUpdate);
            }

            if (valveOpen != null){
                water.setValveOpen(valveOpen);
            }

            return water;
        }
        else{
            throw new IllegalArgumentException(
                    "Неизвестный тип устройства: " + type
            );
        }
    }
    public void getDevices(@NonNull final RepositoryCallback<List<Device>> callback){
        if (callback == null){
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                loadDevices(callback);
            }
        });
    }
    private void loadDevices(@NonNull final RepositoryCallback<List<Device>> callback){
        final List<Device> devices = new ArrayList<Device>();

        try{
            List<DeviceEntity> entities = deviceDao.getAll();

            for (DeviceEntity entity : entities){
                Device device = toDevice(entity);
                devices.add(device);
            }
        }
        catch (final RuntimeException error){
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    callback.onError(error);
                }
            });

            return;
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(devices);
            }
        });
    }
    public void getDeviceByRoom(final int roomId, @NonNull final RepositoryCallback<List<Device>> callback){
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }
        DATABASE_EXECUTOR.execute(new Runnable()
        {
            @Override
            public void run()
            {
                loadDevicesByRoom(roomId, callback);
            }
        });
    }
    private void loadDevicesByRoom(final int roomId,@NonNull final RepositoryCallback<List<Device>> callback){
        final List<Device> devices = new ArrayList<Device>();

        try{
            if (roomId <= 0){
                throw new IllegalArgumentException(
                        "Идентификатор комнаты должен быть больше нуля."
                );
            }

            List<DeviceEntity> entities = deviceDao.getByRoomId(roomId);

            for (DeviceEntity entity : entities){
                Device device = toDevice(entity);
                devices.add(device);
            }
        }
        catch (final RuntimeException error){
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(devices);
            }
        });
    }

    public void addRelayDevice(
            @NonNull final String name,
            final int roomId,
            @NonNull final String controllerId,
            final boolean online,
            final boolean poweredOn,
            @NonNull final RepositoryCallback<RelayDevice> callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable()
        {
            @Override
            public void run()
            {
                saveRelayDevice(
                        name,
                        roomId,
                        controllerId,
                        online,
                        poweredOn,
                        callback
                );
            }
        });
    }

    private void saveRelayDevice(
            @NonNull final String name,
            final int roomId,
            @NonNull final String controllerId,
            final boolean online,
            final boolean poweredOn,
            @NonNull final RepositoryCallback<RelayDevice> callback)
    {
        final RelayDevice savedRelay;

        try
        {
            savedRelay = database.runInTransaction(
                    new Callable<RelayDevice>()
                    {
                        @Override
                        public RelayDevice call()
                        {
                            DeviceEntity entity = new DeviceEntity(
                                    0,
                                    name,
                                    roomId,
                                    controllerId,
                                    DeviceEntity.TYPE_RELAY,
                                    online
                            );

                            entity.setPoweredOn(poweredOn);

                            RoomEntity roomEntity = roomDao.getById(roomId);

                            if (roomEntity == null)
                            {
                                throw new IllegalArgumentException(
                                        "Комната с идентификатором "
                                                + roomId
                                                + " не найдена."
                                );
                            }

                            long generatedId = deviceDao.insert(entity);

                            if (generatedId <= 0)
                            {
                                throw new IllegalStateException(
                                        "База не вернула корректный идентификатор устройства."
                                );
                            }

                            if (generatedId > Integer.MAX_VALUE)
                            {
                                throw new IllegalStateException(
                                        "Идентификатор устройства превышает допустимый диапазон int."
                                );
                            }

                            int deviceId = (int) generatedId;

                            RelayDevice relay = new RelayDevice(
                                    deviceId,
                                    entity.getName(),
                                    entity.getRoomId(),
                                    entity.getControllerId(),
                                    entity.isOnline(),
                                    poweredOn
                            );

                            return relay;
                        }
                    }
            );
        }
        catch (final RuntimeException error)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onSuccess(savedRelay);
            }
        });
    }

    public void addLightDevice(
            @NonNull final String name,
            final int roomId,
            @NonNull final String controllerId,
            final boolean online,
            final boolean poweredOn,
            final int brightness,
            final int lightTemperature,
            @NonNull final RepositoryCallback<LightDevice> callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable()
        {
            @Override
            public void run()
            {
                saveLightDevice(
                        name,
                        roomId,
                        controllerId,
                        online,
                        poweredOn,
                        brightness,
                        lightTemperature,
                        callback
                );
            }
        });
    }

    private void saveLightDevice(
            @NonNull final String name,
            final int roomId,
            @NonNull final String controllerId,
            final boolean online,
            final boolean poweredOn,
            final int brightness,
            final int lightTemperature,
            @NonNull final RepositoryCallback<LightDevice> callback)
    {
        final LightDevice savedLight;

        try
        {
            savedLight = database.runInTransaction(
                    new Callable<LightDevice>()
                    {
                        @Override
                        public LightDevice call()
                        {
                            DeviceEntity entity = new DeviceEntity(
                                    0,
                                    name,
                                    roomId,
                                    controllerId,
                                    DeviceEntity.TYPE_LIGHT,
                                    online
                            );

                            entity.setPoweredOn(poweredOn);
                            entity.setBrightness(brightness);
                            entity.setLightTemperature(lightTemperature);

                            RoomEntity roomEntity = roomDao.getById(roomId);

                            if (roomEntity == null)
                            {
                                throw new IllegalArgumentException(
                                        "Комната с идентификатором "
                                                + roomId
                                                + " не найдена."
                                );
                            }

                            long generatedId = deviceDao.insert(entity);

                            if (generatedId <= 0)
                            {
                                throw new IllegalStateException(
                                        "База не вернула корректный идентификатор устройства."
                                );
                            }

                            if (generatedId > Integer.MAX_VALUE)
                            {
                                throw new IllegalStateException(
                                        "Идентификатор устройства превышает допустимый диапазон int."
                                );
                            }

                            int deviceId = (int) generatedId;

                            LightDevice light = new LightDevice(
                                    deviceId,
                                    entity.getName(),
                                    entity.getRoomId(),
                                    entity.getControllerId(),
                                    entity.isOnline(),
                                    poweredOn,
                                    brightness,
                                    lightTemperature
                            );

                            return light;
                        }
                    }
            );
        }
        catch (final RuntimeException error)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onSuccess(savedLight);
            }
        });
    }

    public void addWaterDevice(
            @NonNull final String name,
            final int roomId,
            @NonNull final String controllerId,
            final boolean online,
            @Nullable final Integer waterLevel,
            @Nullable final Double temperature,
            @Nullable final String levelStatus,
            @Nullable final Long lastUpdate,
            @Nullable final Boolean valveOpen,
            @NonNull final RepositoryCallback<WaterDevice> callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable()
        {
            @Override
            public void run()
            {
                saveWaterDevice(
                        name,
                        roomId,
                        controllerId,
                        online,
                        waterLevel,
                        temperature,
                        levelStatus,
                        lastUpdate,
                        valveOpen,
                        callback
                );
            }
        });
    }

    private void saveWaterDevice(
            @NonNull final String name,
            final int roomId,
            @NonNull final String controllerId,
            final boolean online,
            @Nullable final Integer waterLevel,
            @Nullable final Double temperature,
            @Nullable final String levelStatus,
            @Nullable final Long lastUpdate,
            @Nullable final Boolean valveOpen,
            @NonNull final RepositoryCallback<WaterDevice> callback)
    {
        final WaterDevice savedWater;

        try
        {
            savedWater = database.runInTransaction(
                    new Callable<WaterDevice>()
                    {
                        @Override
                        public WaterDevice call()
                        {
                            boolean hasAnyReading = false;

                            if (waterLevel != null
                                    || temperature != null
                                    || levelStatus != null
                                    || lastUpdate != null)
                            {
                                hasAnyReading = true;
                            }

                            if (hasAnyReading)
                            {
                                boolean levelOnly = waterLevel != null && temperature == null
                                        && levelStatus == null && lastUpdate == null;
                                boolean complete = waterLevel != null && temperature != null
                                        && levelStatus != null && lastUpdate != null;
                                if (!levelOnly && !complete)
                                {
                                    throw new IllegalArgumentException(
                                            "Передайте уровень либо полный пакет показаний воды."
                                    );
                                }
                            }

                            DeviceEntity entity = new DeviceEntity(
                                    0,
                                    name,
                                    roomId,
                                    controllerId,
                                    DeviceEntity.TYPE_WATER,
                                    online
                            );

                            entity.setWaterLevel(waterLevel);
                            entity.setTemperature(temperature);
                            entity.setLevelStatus(levelStatus);
                            entity.setLastUpdate(lastUpdate);
                            entity.setValveOpen(valveOpen);

                            RoomEntity roomEntity = roomDao.getById(roomId);

                            if (roomEntity == null)
                            {
                                throw new IllegalArgumentException(
                                        "Комната с идентификатором "
                                                + roomId
                                                + " не найдена."
                                );
                            }

                            long generatedId = deviceDao.insert(entity);

                            if (generatedId <= 0)
                            {
                                throw new IllegalStateException(
                                        "База не вернула корректный идентификатор устройства."
                                );
                            }

                            if (generatedId > Integer.MAX_VALUE)
                            {
                                throw new IllegalStateException(
                                        "Идентификатор устройства превышает допустимый диапазон int."
                                );
                            }

                            int deviceId = (int) generatedId;

                            WaterDevice water = new WaterDevice(
                                    deviceId,
                                    entity.getName(),
                                    entity.getRoomId(),
                                    entity.getControllerId(),
                                    entity.isOnline()
                            );

                            if (waterLevel != null && temperature == null)
                            {
                                water.updateLevelOnly(waterLevel);
                            }
                            else if (waterLevel != null
                                    && temperature != null
                                    && levelStatus != null
                                    && lastUpdate != null)
                            {
                                water.updateReading(
                                        waterLevel,
                                        temperature,
                                        levelStatus,
                                        lastUpdate
                                );
                            }

                            if (valveOpen != null)
                            {
                                water.setValveOpen(valveOpen);
                            }

                            return water;
                        }
                    }
            );
        }
        catch (final RuntimeException error)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onSuccess(savedWater);
            }
        });
    }

    public void renameDevice(
            final int deviceId,
            @NonNull final String newName,
            @NonNull final RepositoryCallback<Device> callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable()
        {
            @Override
            public void run()
            {
                updateDeviceName(deviceId, newName, callback);
            }
        });
    }

    private void updateDeviceName(
            final int deviceId,
            @NonNull final String newName,
            @NonNull final RepositoryCallback<Device> callback)
    {
        final Device updatedDevice;

        try
        {
            updatedDevice = database.runInTransaction(
                    new Callable<Device>()
                    {
                        @Override
                        public Device call()
                        {
                            if (deviceId <= 0)
                            {
                                throw new IllegalArgumentException(
                                        "Идентификатор устройства должен быть больше нуля."
                                );
                            }

                            DeviceEntity entity = deviceDao.getById(deviceId);

                            if (entity == null)
                            {
                                throw new IllegalArgumentException(
                                        "Устройство с идентификатором "
                                                + deviceId
                                                + " не найдено."
                                );
                            }

                            entity.setName(newName);

                            // Создаём модель с новым именем и прежними данными.
                            Device device = toDevice(entity);

                            int updatedCount = deviceDao.update(entity);

                            if (updatedCount != 1)
                            {
                                throw new IllegalStateException(
                                        "Не удалось обновить название устройства."
                                );
                            }

                            return device;
                        }
                    }
            );
        }
        catch (final RuntimeException error)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onSuccess(updatedDevice);
            }
        });
    }

    public void deleteDevice(
            final int deviceId,
            @NonNull final RepositoryCallback<Boolean> callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable()
        {
            @Override
            public void run()
            {
                removeDevice(deviceId, callback);
            }
        });
    }

    private void removeDevice(
            final int deviceId,
            @NonNull final RepositoryCallback<Boolean> callback)
    {
        final boolean deleted;

        try
        {
            if (deviceId <= 0)
            {
                throw new IllegalArgumentException(
                        "Идентификатор устройства должен быть больше нуля."
                );
            }

            int deletedCount = deviceDao.deleteById(deviceId);

            if (deletedCount == 1)
            {
                deleted = true;
            }
            else
            {
                deleted = false;
            }
        }
        catch (final RuntimeException error)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onSuccess(deleted);
            }
        });
    }

    public void updateRelayState(
            final int deviceId,
            final boolean online,
            final boolean poweredOn,
            @NonNull final RepositoryCallback<RelayDevice> callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable()
        {
            @Override
            public void run()
            {
                saveRelayState(
                        deviceId,
                        online,
                        poweredOn,
                        callback
                );
            }
        });
    }

    private void saveRelayState(
            final int deviceId,
            final boolean online,
            final boolean poweredOn,
            @NonNull final RepositoryCallback<RelayDevice> callback)
    {
        final RelayDevice updatedRelay;

        try
        {
            updatedRelay = database.runInTransaction(
                    new Callable<RelayDevice>()
                    {
                        @Override
                        public RelayDevice call()
                        {
                            if (deviceId <= 0)
                            {
                                throw new IllegalArgumentException(
                                        "Идентификатор устройства должен быть больше нуля."
                                );
                            }

                            DeviceEntity entity = deviceDao.getById(deviceId);

                            if (entity == null)
                            {
                                throw new IllegalArgumentException(
                                        "Устройство с идентификатором "
                                                + deviceId
                                                + " не найдено."
                                );
                            }

                            if (!DeviceEntity.TYPE_RELAY.equals(entity.getType()))
                            {
                                throw new IllegalArgumentException(
                                        "Устройство с идентификатором "
                                                + deviceId
                                                + " не является реле."
                                );
                            }

                            entity.setOnline(online);
                            entity.setPoweredOn(poweredOn);

                            Device device = toDevice(entity);
                            RelayDevice relay = (RelayDevice) device;

                            int updatedCount = deviceDao.update(entity);

                            if (updatedCount != 1)
                            {
                                throw new IllegalStateException(
                                        "Не удалось обновить состояние реле."
                                );
                            }

                            return relay;
                        }
                    }
            );
        }
        catch (final RuntimeException error)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onSuccess(updatedRelay);
            }
        });
    }

    public void updateLightState(
            final int deviceId,
            final boolean online,
            final boolean poweredOn,
            final int brightness,
            final int lightTemperature,
            @NonNull final RepositoryCallback<LightDevice> callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable()
        {
            @Override
            public void run()
            {
                saveLightState(
                        deviceId,
                        online,
                        poweredOn,
                        brightness,
                        lightTemperature,
                        callback
                );
            }
        });
    }

    private void saveLightState(
            final int deviceId,
            final boolean online,
            final boolean poweredOn,
            final int brightness,
            final int lightTemperature,
            @NonNull final RepositoryCallback<LightDevice> callback)
    {
        final LightDevice updatedLight;

        try
        {
            updatedLight = database.runInTransaction(
                    new Callable<LightDevice>()
                    {
                        @Override
                        public LightDevice call()
                        {
                            if (deviceId <= 0)
                            {
                                throw new IllegalArgumentException(
                                        "Идентификатор устройства должен быть больше нуля."
                                );
                            }

                            DeviceEntity entity = deviceDao.getById(deviceId);

                            if (entity == null)
                            {
                                throw new IllegalArgumentException(
                                        "Устройство с идентификатором "
                                                + deviceId
                                                + " не найдено."
                                );
                            }

                            if (!DeviceEntity.TYPE_LIGHT.equals(entity.getType()))
                            {
                                throw new IllegalArgumentException(
                                        "Устройство с идентификатором "
                                                + deviceId
                                                + " не является устройством света."
                                );
                            }

                            entity.setOnline(online);
                            entity.setPoweredOn(poweredOn);
                            entity.setBrightness(brightness);
                            entity.setLightTemperature(lightTemperature);

                            Device device = toDevice(entity);
                            LightDevice light = (LightDevice) device;

                            int updatedCount = deviceDao.update(entity);

                            if (updatedCount != 1)
                            {
                                throw new IllegalStateException(
                                        "Не удалось обновить состояние света."
                                );
                            }

                            return light;
                        }
                    }
            );
        }
        catch (final RuntimeException error)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onSuccess(updatedLight);
            }
        });
    }

    public void updateWaterState(
            final int deviceId,
            final boolean online,
            @Nullable final Integer waterLevel,
            @Nullable final Double temperature,
            @Nullable final String levelStatus,
            @Nullable final Long lastUpdate,
            @Nullable final Boolean valveOpen,
            @NonNull final RepositoryCallback<WaterDevice> callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable()
        {
            @Override
            public void run()
            {
                saveWaterState(
                        deviceId,
                        online,
                        waterLevel,
                        temperature,
                        levelStatus,
                        lastUpdate,
                        valveOpen,
                        callback
                );
            }
        });
    }

    private void saveWaterState(
            final int deviceId,
            final boolean online,
            @Nullable final Integer waterLevel,
            @Nullable final Double temperature,
            @Nullable final String levelStatus,
            @Nullable final Long lastUpdate,
            @Nullable final Boolean valveOpen,
            @NonNull final RepositoryCallback<WaterDevice> callback)
    {
        final WaterDevice updatedWater;

        try
        {
            updatedWater = database.runInTransaction(
                    new Callable<WaterDevice>()
                    {
                        @Override
                        public WaterDevice call()
                        {
                            if (deviceId <= 0)
                            {
                                throw new IllegalArgumentException(
                                        "Идентификатор устройства должен быть больше нуля."
                                );
                            }

                            DeviceEntity entity = deviceDao.getById(deviceId);

                            if (entity == null)
                            {
                                throw new IllegalArgumentException(
                                        "Устройство с идентификатором "
                                                + deviceId
                                                + " не найдено."
                                );
                            }

                            if (!DeviceEntity.TYPE_WATER.equals(entity.getType()))
                            {
                                throw new IllegalArgumentException(
                                        "Устройство с идентификатором "
                                                + deviceId
                                                + " не является водяным устройством."
                                );
                            }

                            boolean hasAnyReading = false;

                            if (waterLevel != null
                                    || temperature != null
                                    || levelStatus != null
                                    || lastUpdate != null)
                            {
                                hasAnyReading = true;
                            }

                            if (hasAnyReading)
                            {
                                boolean levelOnly = waterLevel != null && temperature == null
                                        && levelStatus == null && lastUpdate == null;
                                boolean complete = waterLevel != null && temperature != null
                                        && levelStatus != null && lastUpdate != null;
                                if (!levelOnly && !complete)
                                {
                                    throw new IllegalArgumentException(
                                            "Передайте уровень либо полный пакет показаний воды."
                                    );
                                }

                                entity.setWaterLevel(waterLevel);
                                entity.setTemperature(temperature);
                                entity.setLevelStatus(levelStatus);
                                entity.setLastUpdate(lastUpdate);
                            }

                            if (valveOpen != null)
                            {
                                entity.setValveOpen(valveOpen);
                            }

                            entity.setOnline(online);

                            Device device = toDevice(entity);
                            WaterDevice water = (WaterDevice) device;

                            int updatedCount = deviceDao.update(entity);

                            if (updatedCount != 1)
                            {
                                throw new IllegalStateException(
                                        "Не удалось обновить состояние водяного устройства."
                                );
                            }

                            return water;
                        }
                    }
            );
        }
        catch (final RuntimeException error)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onSuccess(updatedWater);
            }
        });
    }

    public void updateDeviceOnline(
            final int deviceId,
            final boolean online,
            @NonNull final RepositoryCallback<Device> callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Обработчик результата не должен быть null."
            );
        }

        DATABASE_EXECUTOR.execute(new Runnable()
        {
            @Override
            public void run()
            {
                saveDeviceOnline(deviceId, online, callback);
            }
        });
    }

    private void saveDeviceOnline(
            final int deviceId,
            final boolean online,
            @NonNull final RepositoryCallback<Device> callback)
    {
        final Device updatedDevice;

        try
        {
            updatedDevice = database.runInTransaction(
                    new Callable<Device>()
                    {
                        @Override
                        public Device call()
                        {
                            if (deviceId <= 0)
                            {
                                throw new IllegalArgumentException(
                                        "Идентификатор устройства должен быть больше нуля."
                                );
                            }

                            DeviceEntity entity = deviceDao.getById(deviceId);

                            if (entity == null)
                            {
                                throw new IllegalArgumentException(
                                        "Устройство с идентификатором "
                                                + deviceId
                                                + " не найдено."
                                );
                            }

                            entity.setOnline(online);

                            Device device = toDevice(entity);

                            int updatedCount = deviceDao.update(entity);

                            if (updatedCount != 1)
                            {
                                throw new IllegalStateException(
                                        "Не удалось обновить доступность устройства."
                                );
                            }

                            return device;
                        }
                    }
            );
        }
        catch (final RuntimeException error)
        {
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    callback.onError(error);
                }
            });

            return;
        }

        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                callback.onSuccess(updatedDevice);
            }
        });
    }
}
