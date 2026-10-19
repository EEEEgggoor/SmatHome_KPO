package com.example.smarthome.data;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.smarthome.data.database.DeviceDao;
import com.example.smarthome.data.database.DeviceEntity;
import com.example.smarthome.data.database.RoomDao;
import com.example.smarthome.data.database.RoomEntity;
import com.example.smarthome.data.database.SmartHomeDatabase;
import com.example.smarthome.model.Room;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.smarthome.model.Device;
import com.example.smarthome.model.LightDevice;
import com.example.smarthome.model.RelayDevice;
import com.example.smarthome.model.WaterDevice;

@RunWith(AndroidJUnit4.class)
public class HomeRepositoryTest
{
    private SmartHomeDatabase database;
    private HomeRepository repository;
    private RoomDao roomDao;
    private DeviceDao deviceDao;

    @Before
    public void setUp()
    {
        Context context = InstrumentationRegistry
                .getInstrumentation()
                .getTargetContext();

        database = androidx.room.Room.inMemoryDatabaseBuilder(
                context,
                SmartHomeDatabase.class
        ).build();

        repository = new HomeRepository(database);
        roomDao = database.roomDao();
        deviceDao = database.deviceDao();
    }

    @After
    public void tearDown()
    {
        if (database != null)
        {
            database.close();
        }
    }

    @Test
    public void getRooms_calledFromMainThread_returnsEmptyList()
            throws InterruptedException
    {
        final TestCallback<List<Room>> callback =
                new TestCallback<List<Room>>();

        // Имитируем вызов из интерфейса приложения.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        repository.getRooms(callback);
                    }
                }
        );

        // Ждём в тестовом потоке, не блокируя главный.
        List<Room> rooms = callback.awaitSuccess();

        assertTrue(rooms.isEmpty());
    }

    @Test
    public void getRooms_savedRooms_returnsModelsOrderedById()
            throws InterruptedException
    {
        roomDao.insert(new RoomEntity(20, "Спальня"));
        roomDao.insert(new RoomEntity(10, "Кухня"));

        TestCallback<List<Room>> callback =
                new TestCallback<List<Room>>();

        repository.getRooms(callback);

        List<Room> rooms = callback.awaitSuccess();

        assertEquals(2, rooms.size());

        assertEquals(10, rooms.get(0).getId());
        assertEquals("Кухня", rooms.get(0).getName());

        assertEquals(20, rooms.get(1).getId());
        assertEquals("Спальня", rooms.get(1).getName());
    }

    @Test
    public void addRoom_validName_savesRoomAndReturnsGeneratedId()
            throws InterruptedException
    {
        TestCallback<Room> callback = new TestCallback<Room>();

        repository.addRoom("  Кухня  ", callback);

        Room room = callback.awaitSuccess();

        assertTrue(room.getId() > 0);
        assertEquals("Кухня", room.getName());

        RoomEntity saved = roomDao.getById(room.getId());

        assertNotNull(saved);
        assertEquals(room.getId(), saved.getId());
        assertEquals("Кухня", saved.getName());
        assertEquals(1, roomDao.getAll().size());
    }

    @Test
    public void addRoom_invalidNames_reportsErrorAndSavesNothing()
            throws InterruptedException
    {
        String[] invalidNames = {
                null,
                "",
                "   ",
                "1234567890123456789012345"
        };

        for (String name : invalidNames)
        {
            TestCallback<Room> callback = new TestCallback<Room>();

            repository.addRoom(name, callback);

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);
            assertTrue(roomDao.getAll().isEmpty());
        }
    }

    @Test
    public void addRoom_twoRooms_generatesDifferentIds()
            throws InterruptedException
    {
        TestCallback<Room> firstCallback = new TestCallback<Room>();

        repository.addRoom("Кухня", firstCallback);

        Room firstRoom = firstCallback.awaitSuccess();

        TestCallback<Room> secondCallback = new TestCallback<Room>();

        repository.addRoom("Спальня", secondCallback);

        Room secondRoom = secondCallback.awaitSuccess();

        assertTrue(firstRoom.getId() > 0);
        assertTrue(secondRoom.getId() > 0);
        assertTrue(firstRoom.getId() != secondRoom.getId());
        assertEquals(2, roomDao.getAll().size());
    }

    @Test
    public void renameRoom_validName_updatesNameAndKeepsDevices()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int deviceId = insertRelay(roomId);

        TestCallback<Room> callback = new TestCallback<Room>();

        repository.renameRoom(roomId, "  Кухонька  ", callback);

        Room room = callback.awaitSuccess();

        assertEquals(roomId, room.getId());
        assertEquals("Кухонька", room.getName());

        RoomEntity savedRoom = roomDao.getById(roomId);

        assertNotNull(savedRoom);
        assertEquals("Кухонька", savedRoom.getName());
        assertEquals(1, roomDao.getAll().size());

        DeviceEntity savedDevice = deviceDao.getById(deviceId);

        assertNotNull(savedDevice);
        assertEquals(roomId, savedDevice.getRoomId());
        assertEquals("Реле", savedDevice.getName());
        assertEquals(Boolean.TRUE, savedDevice.getPoweredOn());
    }

    @Test
    public void renameRoom_invalidNames_keepsOriginalName()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");

        String[] invalidNames = {
                null,
                "",
                "   ",
                "1234567890123456789012345"
        };

        for (String name : invalidNames)
        {
            TestCallback<Room> callback = new TestCallback<Room>();

            repository.renameRoom(roomId, name, callback);

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);

            RoomEntity savedRoom = roomDao.getById(roomId);

            assertNotNull(savedRoom);
            assertEquals("Кухня", savedRoom.getName());
            assertEquals(1, roomDao.getAll().size());
        }
    }

    @Test
    public void renameRoom_missingRoom_reportsErrorAndCreatesNothing()
            throws InterruptedException
    {
        TestCallback<Room> callback = new TestCallback<Room>();

        repository.renameRoom(999, "Кухня", callback);

        Exception error = callback.awaitError();

        assertTrue(error instanceof IllegalArgumentException);
        assertTrue(roomDao.getAll().isEmpty());
    }

    @Test
    public void renameRoom_invalidIds_reportsErrorAndKeepsRoom()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int[] invalidIds = {0, -1};

        for (int invalidId : invalidIds)
        {
            TestCallback<Room> callback = new TestCallback<Room>();

            repository.renameRoom(invalidId, "Новое имя", callback);

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);

            RoomEntity savedRoom = roomDao.getById(roomId);

            assertNotNull(savedRoom);
            assertEquals("Кухня", savedRoom.getName());
            assertEquals(1, roomDao.getAll().size());
        }
    }

    @Test
    public void deleteRoom_existingRoom_deletesItsDevicesOnly()
            throws InterruptedException
    {
        int kitchenId = insertRoom("Кухня");
        int bedroomId = insertRoom("Спальня");

        int firstKitchenDeviceId = insertRelay(kitchenId);
        int secondKitchenDeviceId = insertRelay(kitchenId);
        int bedroomDeviceId = insertRelay(bedroomId);

        TestCallback<Boolean> callback = new TestCallback<Boolean>();

        repository.deleteRoom(kitchenId, callback);

        Boolean deleted = callback.awaitSuccess();

        assertEquals(Boolean.TRUE, deleted);

        assertNull(roomDao.getById(kitchenId));
        assertNull(deviceDao.getById(firstKitchenDeviceId));
        assertNull(deviceDao.getById(secondKitchenDeviceId));

        assertNotNull(roomDao.getById(bedroomId));

        DeviceEntity bedroomDevice = deviceDao.getById(bedroomDeviceId);

        assertNotNull(bedroomDevice);
        assertEquals(bedroomId, bedroomDevice.getRoomId());
        assertEquals(1, roomDao.getAll().size());
        assertEquals(1, deviceDao.getAll().size());
    }

    @Test
    public void deleteRoom_repeatedCall_returnsFalse()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");

        TestCallback<Boolean> firstCallback = new TestCallback<Boolean>();

        repository.deleteRoom(roomId, firstCallback);

        assertEquals(Boolean.TRUE, firstCallback.awaitSuccess());

        TestCallback<Boolean> secondCallback = new TestCallback<Boolean>();

        repository.deleteRoom(roomId, secondCallback);

        assertEquals(Boolean.FALSE, secondCallback.awaitSuccess());
        assertTrue(roomDao.getAll().isEmpty());
    }

    @Test
    public void deleteRoom_invalidIds_reportsErrorAndKeepsData()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int deviceId = insertRelay(roomId);

        int[] invalidIds = {0, -1};

        for (int invalidId : invalidIds)
        {
            TestCallback<Boolean> callback = new TestCallback<Boolean>();

            repository.deleteRoom(invalidId, callback);

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);
            assertNotNull(roomDao.getById(roomId));
            assertNotNull(deviceDao.getById(deviceId));
            assertEquals(1, roomDao.getAll().size());
            assertEquals(1, deviceDao.getAll().size());
        }
    }

    private int insertRoom(String name)
    {
        RoomEntity entity = new RoomEntity(0, name);

        long generatedId = roomDao.insert(entity);

        return checkedId(generatedId);
    }

    @Test
    public void getDevices_emptyDatabase_returnsEmptyList()
            throws InterruptedException
    {
        TestCallback<List<Device>> callback =
                new TestCallback<List<Device>>();

        repository.getDevices(callback);

        List<Device> devices = callback.awaitSuccess();

        assertTrue(devices.isEmpty());
    }

    @Test
    public void getDevices_allTypes_returnsCorrectModelsOrderedById()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        long measuredAt = 1700000060000L;

        DeviceEntity waterEntity = new DeviceEntity(
                30, "Бак", roomId, "water-controller",
                DeviceEntity.TYPE_WATER, false
        );

        waterEntity.setWaterLevel(64);
        waterEntity.setTemperature(22.4);
        waterEntity.setLevelStatus("Норма");
        waterEntity.setLastUpdate(measuredAt);
        waterEntity.setValveOpen(false);

        DeviceEntity relayEntity = new DeviceEntity(
                20, "Реле", roomId, "relay-controller",
                DeviceEntity.TYPE_RELAY, true
        );

        relayEntity.setPoweredOn(false);

        DeviceEntity lightEntity = new DeviceEntity(
                10, "Свет", roomId, "light-controller",
                DeviceEntity.TYPE_LIGHT, true
        );

        lightEntity.setPoweredOn(true);
        lightEntity.setBrightness(77);
        lightEntity.setLightTemperature(40);

        // Вставляем в обратном порядке, чтобы проверить сортировку.
        deviceDao.insert(waterEntity);
        deviceDao.insert(relayEntity);
        deviceDao.insert(lightEntity);

        TestCallback<List<Device>> callback =
                new TestCallback<List<Device>>();

        repository.getDevices(callback);

        List<Device> devices = callback.awaitSuccess();

        assertEquals(3, devices.size());

        assertTrue(devices.get(0) instanceof LightDevice);
        assertTrue(devices.get(1) instanceof RelayDevice);
        assertTrue(devices.get(2) instanceof WaterDevice);

        LightDevice light = (LightDevice) devices.get(0);
        RelayDevice relay = (RelayDevice) devices.get(1);
        WaterDevice water = (WaterDevice) devices.get(2);

        assertEquals(10, light.getId());
        assertEquals("Свет", light.getName());
        assertEquals(roomId, light.getRoomId());
        assertEquals("light-controller", light.getControllerId());
        assertTrue(light.isOnline());
        assertTrue(light.isPoweredOn());
        assertEquals(77, light.getBrightness());
        assertEquals(40, light.getLightTemperature());

        assertEquals(20, relay.getId());
        assertEquals("Реле", relay.getName());
        assertEquals(roomId, relay.getRoomId());
        assertEquals("relay-controller", relay.getControllerId());
        assertTrue(relay.isOnline());
        assertFalse(relay.isPoweredOn());

        assertEquals(30, water.getId());
        assertEquals("Бак", water.getName());
        assertEquals(roomId, water.getRoomId());
        assertEquals("water-controller", water.getControllerId());
        assertFalse(water.isOnline());
        assertEquals(Integer.valueOf(64), water.getWaterLevel());
        assertNotNull(water.getTemperature());
        assertEquals(22.4, water.getTemperature().doubleValue(), 0.001);
        assertEquals("Норма", water.getLevelStatus());
        assertEquals(Long.valueOf(measuredAt), water.getLastUpdate());
        assertEquals(Boolean.FALSE, water.getValveOpen());
    }

    @Test
    public void getDeviceByRoom_returnsOnlySelectedRoomDevices()
            throws InterruptedException
    {
        int kitchenId = insertRoom("Кухня");
        int bedroomId = insertRoom("Спальня");

        int firstKitchenId = insertRelay(kitchenId);
        insertRelay(bedroomId);
        int secondKitchenId = insertRelay(kitchenId);

        TestCallback<List<Device>> callback =
                new TestCallback<List<Device>>();

        repository.getDeviceByRoom(kitchenId, callback);

        List<Device> devices = callback.awaitSuccess();

        assertEquals(2, devices.size());
        assertEquals(firstKitchenId, devices.get(0).getId());
        assertEquals(secondKitchenId, devices.get(1).getId());

        for (Device device : devices)
        {
            assertEquals(kitchenId, device.getRoomId());
        }
    }

    @Test
    public void getDeviceByRoom_emptyOrMissingRoom_returnsEmptyList()
            throws InterruptedException
    {
        int emptyRoomId = insertRoom("Пустая комната");

        int occupiedRoomId = insertRoom("Кухня");
        insertRelay(occupiedRoomId);

        int[] roomIds = {emptyRoomId, 999};

        for (int roomId : roomIds)
        {
            TestCallback<List<Device>> callback =
                    new TestCallback<List<Device>>();

            repository.getDeviceByRoom(roomId, callback);

            List<Device> devices = callback.awaitSuccess();

            assertTrue(devices.isEmpty());
        }

        assertEquals(1, deviceDao.getAll().size());
    }

    @Test
    public void getDeviceByRoom_invalidIds_reportsError()
            throws InterruptedException
    {
        int[] invalidIds = {0, -1};

        for (int roomId : invalidIds)
        {
            TestCallback<List<Device>> callback =
                    new TestCallback<List<Device>>();

            repository.getDeviceByRoom(roomId, callback);

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);
        }
    }

    @Test
    public void getDevices_incompleteWaterRecord_reportsErrorInsteadOfPartialList()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");

        // Первая запись корректна.
        insertRelay(roomId);

        // Во второй записи есть только часть пакета показаний.
        DeviceEntity invalidWater = new DeviceEntity(
                0, "Бак", roomId, "water-controller",
                DeviceEntity.TYPE_WATER, true
        );

        invalidWater.setWaterLevel(50);
        invalidWater.setTemperature(22.0);
        deviceDao.insert(invalidWater);

        TestCallback<List<Device>> callback =
                new TestCallback<List<Device>>();

        repository.getDevices(callback);

        Exception error = callback.awaitError();

        assertTrue(error instanceof IllegalStateException);

        // Чтение не должно удалять или исправлять записи само.
        assertEquals(2, deviceDao.getAll().size());
    }

    @Test
    public void addRelayDevice_preservesAllOnlineAndPowerCombinations()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        boolean[] states = {false, true};

        for (boolean online : states)
        {
            for (boolean poweredOn : states)
            {
                TestCallback<RelayDevice> callback =
                        new TestCallback<RelayDevice>();

                repository.addRelayDevice(
                        "  Реле кухни  ",
                        roomId,
                        "  relay-controller  ",
                        online,
                        poweredOn,
                        callback
                );

                RelayDevice relay = callback.awaitSuccess();

                assertTrue(relay.getId() > 0);
                assertEquals("Реле кухни", relay.getName());
                assertEquals(roomId, relay.getRoomId());
                assertEquals("relay-controller", relay.getControllerId());
                assertEquals(online, relay.isOnline());
                assertEquals(poweredOn, relay.isPoweredOn());

                DeviceEntity saved = deviceDao.getById(relay.getId());

                assertNotNull(saved);
                assertEquals(DeviceEntity.TYPE_RELAY, saved.getType());
                assertEquals("Реле кухни", saved.getName());
                assertEquals(roomId, saved.getRoomId());
                assertEquals("relay-controller", saved.getControllerId());
                assertEquals(online, saved.isOnline());
                assertEquals(
                        Boolean.valueOf(poweredOn),
                        saved.getPoweredOn()
                );

                assertNull(saved.getBrightness());
                assertNull(saved.getLightTemperature());
                assertNull(saved.getWaterLevel());
                assertNull(saved.getValveOpen());
            }
        }

        assertEquals(4, deviceDao.getAll().size());
    }

    @Test
    public void addLightDevice_boundaryPercentages_savesSettingsWhilePoweredOff()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int[] percentages = {0, 100};

        for (int brightness : percentages)
        {
            for (int lightTemperature : percentages)
            {
                TestCallback<LightDevice> callback =
                        new TestCallback<LightDevice>();

                repository.addLightDevice(
                        "  Подсветка  ",
                        roomId,
                        "  light-controller  ",
                        true,
                        false,
                        brightness,
                        lightTemperature,
                        callback
                );

                LightDevice light = callback.awaitSuccess();

                assertTrue(light.getId() > 0);
                assertEquals("Подсветка", light.getName());
                assertEquals(roomId, light.getRoomId());
                assertEquals("light-controller", light.getControllerId());
                assertTrue(light.isOnline());
                assertFalse(light.isPoweredOn());
                assertEquals(brightness, light.getBrightness());
                assertEquals(lightTemperature, light.getLightTemperature());

                DeviceEntity saved = deviceDao.getById(light.getId());

                assertNotNull(saved);
                assertEquals(DeviceEntity.TYPE_LIGHT, saved.getType());
                assertEquals("Подсветка", saved.getName());
                assertEquals("light-controller", saved.getControllerId());
                assertEquals(Boolean.FALSE, saved.getPoweredOn());
                assertEquals(
                        Integer.valueOf(brightness),
                        saved.getBrightness()
                );
                assertEquals(
                        Integer.valueOf(lightTemperature),
                        saved.getLightTemperature()
                );
                assertNull(saved.getWaterLevel());
                assertNull(saved.getValveOpen());
            }
        }

        assertEquals(4, deviceDao.getAll().size());
    }

    @Test
    public void addLightDevice_invalidPercentages_reportsErrorAndSavesNothing()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int[] invalidValues = {-1, 101};

        for (int invalidValue : invalidValues)
        {
            TestCallback<LightDevice> brightnessCallback =
                    new TestCallback<LightDevice>();

            repository.addLightDevice(
                    "Свет", roomId, "light-controller",
                    true, true, invalidValue, 50,
                    brightnessCallback
            );

            Exception brightnessError = brightnessCallback.awaitError();

            assertTrue(brightnessError instanceof IllegalArgumentException);
            assertTrue(deviceDao.getAll().isEmpty());

            TestCallback<LightDevice> temperatureCallback =
                    new TestCallback<LightDevice>();

            repository.addLightDevice(
                    "Свет", roomId, "light-controller",
                    true, true, 50, invalidValue,
                    temperatureCallback
            );

            Exception temperatureError = temperatureCallback.awaitError();

            assertTrue(temperatureError instanceof IllegalArgumentException);
            assertTrue(deviceDao.getAll().isEmpty());
        }
    }

    @Test
    public void addWaterDevice_unknownReadings_preservesNullableValve()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        Boolean[] valveStates = {null, Boolean.FALSE, Boolean.TRUE};

        for (Boolean valveOpen : valveStates)
        {
            TestCallback<WaterDevice> callback =
                    new TestCallback<WaterDevice>();

            repository.addWaterDevice(
                    "Бак",
                    roomId,
                    "water-controller",
                    false,
                    null,
                    null,
                    null,
                    null,
                    valveOpen,
                    callback
            );

            WaterDevice water = callback.awaitSuccess();

            assertTrue(water.getId() > 0);
            assertFalse(water.isOnline());
            assertNull(water.getWaterLevel());
            assertNull(water.getTemperature());
            assertNull(water.getLevelStatus());
            assertNull(water.getLastUpdate());
            assertEquals(valveOpen, water.getValveOpen());

            DeviceEntity saved = deviceDao.getById(water.getId());

            assertNotNull(saved);
            assertEquals(DeviceEntity.TYPE_WATER, saved.getType());
            assertFalse(saved.isOnline());
            assertNull(saved.getWaterLevel());
            assertNull(saved.getTemperature());
            assertNull(saved.getLevelStatus());
            assertNull(saved.getLastUpdate());
            assertEquals(valveOpen, saved.getValveOpen());
            assertNull(saved.getPoweredOn());
            assertNull(saved.getBrightness());
            assertNull(saved.getLightTemperature());
        }

        assertEquals(3, deviceDao.getAll().size());
    }

    @Test
    public void addWaterDevice_levelOnlyFromEsp_persistsWithoutInventingOtherMeasurements()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        TestCallback<WaterDevice> callback = new TestCallback<WaterDevice>();
        repository.addWaterDevice("Бак", roomId, "ESP32-S2-001", true,
                63, null, null, null, null, callback);

        WaterDevice water = callback.awaitSuccess();
        assertEquals(Integer.valueOf(63), water.getWaterLevel());
        assertNull(water.getTemperature());
        assertNull(water.getLevelStatus());
        assertNull(water.getLastUpdate());
        DeviceEntity saved = deviceDao.getById(water.getId());
        assertEquals(Integer.valueOf(63), saved.getWaterLevel());
        assertNull(saved.getTemperature());
    }

    @Test
    public void addWaterDevice_completeReadings_savesValuesAndTrimmedText()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        long measuredAt = 1700000060000L;

        TestCallback<WaterDevice> callback =
                new TestCallback<WaterDevice>();

        repository.addWaterDevice(
                "  Бак  ",
                roomId,
                "  water-controller  ",
                true,
                64,
                22.4,
                "  Норма  ",
                measuredAt,
                false,
                callback
        );

        WaterDevice water = callback.awaitSuccess();

        assertTrue(water.getId() > 0);
        assertEquals("Бак", water.getName());
        assertEquals(roomId, water.getRoomId());
        assertEquals("water-controller", water.getControllerId());
        assertTrue(water.isOnline());
        assertEquals(Integer.valueOf(64), water.getWaterLevel());
        assertNotNull(water.getTemperature());
        assertEquals(22.4, water.getTemperature().doubleValue(), 0.001);
        assertEquals("Норма", water.getLevelStatus());
        assertEquals(Long.valueOf(measuredAt), water.getLastUpdate());
        assertEquals(Boolean.FALSE, water.getValveOpen());

        DeviceEntity saved = deviceDao.getById(water.getId());

        assertNotNull(saved);
        assertEquals(DeviceEntity.TYPE_WATER, saved.getType());
        assertEquals("Бак", saved.getName());
        assertEquals(roomId, saved.getRoomId());
        assertEquals("water-controller", saved.getControllerId());
        assertTrue(saved.isOnline());
        assertEquals(Integer.valueOf(64), saved.getWaterLevel());
        assertNotNull(saved.getTemperature());
        assertEquals(22.4, saved.getTemperature().doubleValue(), 0.001);
        assertEquals("Норма", saved.getLevelStatus());
        assertEquals(Long.valueOf(measuredAt), saved.getLastUpdate());
        assertEquals(Boolean.FALSE, saved.getValveOpen());
        assertEquals(1, deviceDao.getAll().size());
    }

    @Test
    public void addWaterDevice_incompleteReadings_reportsErrorAndSavesNothing()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");

        for (int missingField = 0; missingField < 4; missingField++)
        {
            Integer waterLevel = 64;
            Double temperature = 22.4;
            String levelStatus = "Норма";
            Long lastUpdate = 1700000060000L;

            if (missingField == 0)
            {
                waterLevel = null;
            }
            else if (missingField == 1)
            {
                temperature = null;
            }
            else if (missingField == 2)
            {
                levelStatus = null;
            }
            else
            {
                lastUpdate = null;
            }

            TestCallback<WaterDevice> callback =
                    new TestCallback<WaterDevice>();

            repository.addWaterDevice(
                    "Бак",
                    roomId,
                    "water-controller",
                    true,
                    waterLevel,
                    temperature,
                    levelStatus,
                    lastUpdate,
                    false,
                    callback
            );

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);
            assertTrue(deviceDao.getAll().isEmpty());
        }
    }

    @Test
    public void addDevices_missingRoom_reportsErrorForEveryType()
            throws InterruptedException
    {
        int missingRoomId = 999;

        TestCallback<RelayDevice> relayCallback =
                new TestCallback<RelayDevice>();

        repository.addRelayDevice(
                "Реле", missingRoomId, "relay-controller",
                true, false, relayCallback
        );

        Exception relayError = relayCallback.awaitError();

        assertTrue(relayError instanceof IllegalArgumentException);

        TestCallback<LightDevice> lightCallback =
                new TestCallback<LightDevice>();

        repository.addLightDevice(
                "Свет", missingRoomId, "light-controller",
                true, true, 77, 40, lightCallback
        );

        Exception lightError = lightCallback.awaitError();

        assertTrue(lightError instanceof IllegalArgumentException);

        TestCallback<WaterDevice> waterCallback =
                new TestCallback<WaterDevice>();

        repository.addWaterDevice(
                "Бак", missingRoomId, "water-controller",
                true, null, null, null, null, null,
                waterCallback
        );

        Exception waterError = waterCallback.awaitError();

        assertTrue(waterError instanceof IllegalArgumentException);
        assertTrue(deviceDao.getAll().isEmpty());
        assertTrue(roomDao.getAll().isEmpty());
    }

    @Test
    public void renameDevice_relay_changesNameAndKeepsState()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int deviceId = insertRelay(roomId);

        TestCallback<Device> callback = new TestCallback<Device>();

        repository.renameDevice(deviceId, "  Реле чайника  ", callback);

        Device device = callback.awaitSuccess();

        assertTrue(device instanceof RelayDevice);

        RelayDevice relay = (RelayDevice) device;

        assertEquals(deviceId, relay.getId());
        assertEquals("Реле чайника", relay.getName());
        assertEquals(roomId, relay.getRoomId());
        assertEquals("test-controller", relay.getControllerId());
        assertTrue(relay.isOnline());
        assertTrue(relay.isPoweredOn());

        DeviceEntity saved = deviceDao.getById(deviceId);

        assertNotNull(saved);
        assertEquals("Реле чайника", saved.getName());
        assertEquals(DeviceEntity.TYPE_RELAY, saved.getType());
        assertEquals(roomId, saved.getRoomId());
        assertEquals("test-controller", saved.getControllerId());
        assertTrue(saved.isOnline());
        assertEquals(Boolean.TRUE, saved.getPoweredOn());
        assertEquals(1, deviceDao.getAll().size());
    }

    @Test
    public void renameDevice_light_changesNameAndKeepsSettings()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");

        DeviceEntity entity = new DeviceEntity(
                0,
                "Свет",
                roomId,
                "light-controller",
                DeviceEntity.TYPE_LIGHT,
                false
        );

        entity.setPoweredOn(true);
        entity.setBrightness(77);
        entity.setLightTemperature(40);

        int deviceId = checkedId(deviceDao.insert(entity));

        TestCallback<Device> callback = new TestCallback<Device>();

        repository.renameDevice(deviceId, "  Подсветка кухни  ", callback);

        Device device = callback.awaitSuccess();

        assertTrue(device instanceof LightDevice);

        LightDevice light = (LightDevice) device;

        assertEquals(deviceId, light.getId());
        assertEquals("Подсветка кухни", light.getName());
        assertEquals(roomId, light.getRoomId());
        assertEquals("light-controller", light.getControllerId());
        assertFalse(light.isOnline());
        assertTrue(light.isPoweredOn());
        assertEquals(77, light.getBrightness());
        assertEquals(40, light.getLightTemperature());

        DeviceEntity saved = deviceDao.getById(deviceId);

        assertNotNull(saved);
        assertEquals("Подсветка кухни", saved.getName());
        assertEquals(DeviceEntity.TYPE_LIGHT, saved.getType());
        assertEquals(roomId, saved.getRoomId());
        assertEquals("light-controller", saved.getControllerId());
        assertFalse(saved.isOnline());
        assertEquals(Boolean.TRUE, saved.getPoweredOn());
        assertEquals(Integer.valueOf(77), saved.getBrightness());
        assertEquals(Integer.valueOf(40), saved.getLightTemperature());
        assertEquals(1, deviceDao.getAll().size());
    }

    @Test
    public void renameDevice_water_preservesKnownAndUnknownReadings()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        boolean[] readingStates = {false, true};

        for (boolean hasReadings : readingStates)
        {
            DeviceEntity entity = new DeviceEntity(
                    0,
                    "Бак",
                    roomId,
                    "water-controller",
                    DeviceEntity.TYPE_WATER,
                    true
            );

            if (hasReadings)
            {
                entity.setWaterLevel(64);
                entity.setTemperature(22.4);
                entity.setLevelStatus("Норма");
                entity.setLastUpdate(1700000060000L);
                entity.setValveOpen(false);
            }

            int deviceId = checkedId(deviceDao.insert(entity));

            TestCallback<Device> callback = new TestCallback<Device>();

            repository.renameDevice(deviceId, "  Бак кухни  ", callback);

            Device device = callback.awaitSuccess();

            assertTrue(device instanceof WaterDevice);

            WaterDevice water = (WaterDevice) device;

            assertEquals(deviceId, water.getId());
            assertEquals("Бак кухни", water.getName());
            assertEquals(roomId, water.getRoomId());
            assertEquals("water-controller", water.getControllerId());
            assertTrue(water.isOnline());

            // Ожидаемые значения берём из исходной записи.
            assertEquals(entity.getWaterLevel(), water.getWaterLevel());
            assertEquals(entity.getTemperature(), water.getTemperature());
            assertEquals(entity.getLevelStatus(), water.getLevelStatus());
            assertEquals(entity.getLastUpdate(), water.getLastUpdate());
            assertEquals(entity.getValveOpen(), water.getValveOpen());

            DeviceEntity saved = deviceDao.getById(deviceId);

            assertNotNull(saved);
            assertEquals("Бак кухни", saved.getName());
            assertEquals(DeviceEntity.TYPE_WATER, saved.getType());
            assertEquals(roomId, saved.getRoomId());
            assertEquals("water-controller", saved.getControllerId());
            assertTrue(saved.isOnline());
            assertEquals(entity.getWaterLevel(), saved.getWaterLevel());
            assertEquals(entity.getTemperature(), saved.getTemperature());
            assertEquals(entity.getLevelStatus(), saved.getLevelStatus());
            assertEquals(entity.getLastUpdate(), saved.getLastUpdate());
            assertEquals(entity.getValveOpen(), saved.getValveOpen());
        }

        assertEquals(2, deviceDao.getAll().size());
    }

    @Test
    public void renameDevice_invalidNames_keepsOriginalData()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int deviceId = insertRelay(roomId);

        String[] invalidNames = {null, "", "   "};

        for (String name : invalidNames)
        {
            TestCallback<Device> callback = new TestCallback<Device>();

            repository.renameDevice(deviceId, name, callback);

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);

            DeviceEntity saved = deviceDao.getById(deviceId);

            assertNotNull(saved);
            assertEquals("Реле", saved.getName());
            assertEquals(roomId, saved.getRoomId());
            assertEquals(DeviceEntity.TYPE_RELAY, saved.getType());
            assertEquals(Boolean.TRUE, saved.getPoweredOn());
            assertEquals(1, deviceDao.getAll().size());
        }
    }

    @Test
    public void renameDevice_invalidIds_reportsErrorAndKeepsDevice()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int deviceId = insertRelay(roomId);

        int[] invalidIds = {0, -1};

        for (int invalidId : invalidIds)
        {
            TestCallback<Device> callback = new TestCallback<Device>();

            repository.renameDevice(invalidId, "Новое имя", callback);

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);

            DeviceEntity saved = deviceDao.getById(deviceId);

            assertNotNull(saved);
            assertEquals("Реле", saved.getName());
            assertEquals(1, deviceDao.getAll().size());
        }
    }

    @Test
    public void renameDevice_missingDevice_reportsErrorAndCreatesNothing()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");

        TestCallback<Device> callback = new TestCallback<Device>();

        repository.renameDevice(999, "Новое имя", callback);

        Exception error = callback.awaitError();

        assertTrue(error instanceof IllegalArgumentException);
        assertTrue(deviceDao.getAll().isEmpty());
        assertNotNull(roomDao.getById(roomId));
    }

    @Test
    public void renameDevice_invalidStoredRecord_doesNotSaveNewName()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");

        DeviceEntity entity = new DeviceEntity(
                0,
                "Бак",
                roomId,
                "water-controller",
                DeviceEntity.TYPE_WATER,
                true
        );

        // Создаём неполный пакет напрямую через DAO.
        entity.setWaterLevel(64);
        entity.setTemperature(22.0);

        int deviceId = checkedId(deviceDao.insert(entity));

        TestCallback<Device> callback = new TestCallback<Device>();

        repository.renameDevice(deviceId, "Новое название", callback);

        Exception error = callback.awaitError();

        assertTrue(error instanceof IllegalStateException);

        DeviceEntity saved = deviceDao.getById(deviceId);

        assertNotNull(saved);
        assertEquals("Бак", saved.getName());
        assertEquals(Integer.valueOf(64), saved.getWaterLevel());
        assertEquals(Double.valueOf(22.0), saved.getTemperature());
        assertNull(saved.getLevelStatus());
        assertNull(saved.getLastUpdate());
        assertEquals(1, deviceDao.getAll().size());
    }

    @Test
    public void deleteDevice_existingDevice_keepsRoomsAndOtherDevices()
            throws InterruptedException
    {
        int kitchenId = insertRoom("Кухня");
        int bedroomId = insertRoom("Спальня");

        int deletedDeviceId = insertRelay(kitchenId);
        int kitchenNeighbourId = insertRelay(kitchenId);
        int bedroomDeviceId = insertRelay(bedroomId);

        TestCallback<Boolean> callback = new TestCallback<Boolean>();

        repository.deleteDevice(deletedDeviceId, callback);

        Boolean deleted = callback.awaitSuccess();

        assertEquals(Boolean.TRUE, deleted);
        assertNull(deviceDao.getById(deletedDeviceId));

        DeviceEntity kitchenNeighbour =
                deviceDao.getById(kitchenNeighbourId);

        DeviceEntity bedroomDevice =
                deviceDao.getById(bedroomDeviceId);

        assertNotNull(kitchenNeighbour);
        assertNotNull(bedroomDevice);

        assertEquals(kitchenId, kitchenNeighbour.getRoomId());
        assertEquals(bedroomId, bedroomDevice.getRoomId());

        assertNotNull(roomDao.getById(kitchenId));
        assertNotNull(roomDao.getById(bedroomId));

        assertEquals(2, roomDao.getAll().size());
        assertEquals(2, deviceDao.getAll().size());
    }

    @Test
    public void deleteDevice_repeatedCall_returnsFalseAndKeepsEmptyRoom()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int deviceId = insertRelay(roomId);

        TestCallback<Boolean> firstCallback =
                new TestCallback<Boolean>();

        repository.deleteDevice(deviceId, firstCallback);

        assertEquals(Boolean.TRUE, firstCallback.awaitSuccess());
        assertTrue(deviceDao.getAll().isEmpty());

        TestCallback<Boolean> secondCallback =
                new TestCallback<Boolean>();

        repository.deleteDevice(deviceId, secondCallback);

        assertEquals(Boolean.FALSE, secondCallback.awaitSuccess());

        assertTrue(deviceDao.getAll().isEmpty());
        assertNotNull(roomDao.getById(roomId));
        assertEquals(1, roomDao.getAll().size());
    }

    @Test
    public void deleteDevice_invalidIds_reportsErrorAndKeepsData()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int deviceId = insertRelay(roomId);

        int[] invalidIds = {0, -1};

        for (int invalidId : invalidIds)
        {
            TestCallback<Boolean> callback =
                    new TestCallback<Boolean>();

            repository.deleteDevice(invalidId, callback);

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);

            DeviceEntity saved = deviceDao.getById(deviceId);

            assertNotNull(saved);
            assertEquals("Реле", saved.getName());
            assertEquals(roomId, saved.getRoomId());
            assertEquals(Boolean.TRUE, saved.getPoweredOn());

            assertNotNull(roomDao.getById(roomId));
            assertEquals(1, roomDao.getAll().size());
            assertEquals(1, deviceDao.getAll().size());
        }
    }

    @Test
    public void updateRelayState_allCombinations_savesStateAndKeepsOtherData()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int deviceId = insertRelay(roomId);
        int neighbourId = insertRelay(roomId);

        boolean[] states = {false, true};

        for (boolean online : states)
        {
            for (boolean poweredOn : states)
            {
                TestCallback<RelayDevice> callback =
                        new TestCallback<RelayDevice>();

                repository.updateRelayState(
                        deviceId,
                        online,
                        poweredOn,
                        callback
                );

                RelayDevice relay = callback.awaitSuccess();

                assertEquals(deviceId, relay.getId());
                assertEquals("Реле", relay.getName());
                assertEquals(roomId, relay.getRoomId());
                assertEquals("test-controller", relay.getControllerId());
                assertEquals(online, relay.isOnline());
                assertEquals(poweredOn, relay.isPoweredOn());

                DeviceEntity saved = deviceDao.getById(deviceId);

                assertNotNull(saved);
                assertEquals(DeviceEntity.TYPE_RELAY, saved.getType());
                assertEquals("Реле", saved.getName());
                assertEquals(roomId, saved.getRoomId());
                assertEquals("test-controller", saved.getControllerId());
                assertEquals(online, saved.isOnline());
                assertEquals(
                        Boolean.valueOf(poweredOn),
                        saved.getPoweredOn()
                );

                DeviceEntity neighbour = deviceDao.getById(neighbourId);

                assertNotNull(neighbour);
                assertTrue(neighbour.isOnline());
                assertEquals(Boolean.TRUE, neighbour.getPoweredOn());
                assertEquals(2, deviceDao.getAll().size());
            }
        }
    }

    @Test
    public void updateRelayState_lightDevice_reportsErrorAndKeepsSettings()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int lightId = insertLight(roomId);

        TestCallback<RelayDevice> callback =
                new TestCallback<RelayDevice>();

        repository.updateRelayState(
                lightId,
                false,
                false,
                callback
        );

        Exception error = callback.awaitError();

        assertTrue(error instanceof IllegalArgumentException);
        assertOriginalLight(lightId);
        assertEquals(1, deviceDao.getAll().size());
    }

    @Test
    public void updateRelayState_invalidOrMissingId_reportsError()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int deviceId = insertRelay(roomId);

        int[] invalidOrMissingIds = {0, -1, 999};

        for (int id : invalidOrMissingIds)
        {
            TestCallback<RelayDevice> callback =
                    new TestCallback<RelayDevice>();

            repository.updateRelayState(id, false, false, callback);

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);

            DeviceEntity saved = deviceDao.getById(deviceId);

            assertNotNull(saved);
            assertTrue(saved.isOnline());
            assertEquals(Boolean.TRUE, saved.getPoweredOn());
            assertEquals(1, deviceDao.getAll().size());
        }
    }

    @Test
    public void updateRelayState_invalidStoredRecord_doesNotSaveNewState()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");

        DeviceEntity entity = new DeviceEntity(
                0,
                "Реле",
                roomId,
                "relay-controller",
                DeviceEntity.TYPE_RELAY,
                true
        );

        entity.setPoweredOn(true);

        // Яркость у реле недопустима: создаём некорректную запись.
        entity.setBrightness(50);

        int deviceId = checkedId(deviceDao.insert(entity));

        TestCallback<RelayDevice> callback =
                new TestCallback<RelayDevice>();

        repository.updateRelayState(
                deviceId,
                false,
                false,
                callback
        );

        Exception error = callback.awaitError();

        assertTrue(error instanceof IllegalStateException);

        DeviceEntity saved = deviceDao.getById(deviceId);

        assertNotNull(saved);

        // Новые false не должны попасть в базу после ошибки.
        assertTrue(saved.isOnline());
        assertEquals(Boolean.TRUE, saved.getPoweredOn());
        assertEquals(Integer.valueOf(50), saved.getBrightness());
        assertEquals("Реле", saved.getName());
        assertEquals(1, deviceDao.getAll().size());
    }

    @Test
    public void updateLightState_validData_savesSettingsAndKeepsOtherData()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int lightId = insertLight(roomId);
        int neighbourId = insertRelay(roomId);

        TestCallback<LightDevice> callback =
                new TestCallback<LightDevice>();

        repository.updateLightState(
                lightId,
                false,
                false,
                77,
                40,
                callback
        );

        LightDevice light = callback.awaitSuccess();

        assertEquals(lightId, light.getId());
        assertEquals("Свет", light.getName());
        assertEquals(roomId, light.getRoomId());
        assertEquals("light-controller", light.getControllerId());
        assertFalse(light.isOnline());
        assertFalse(light.isPoweredOn());
        assertEquals(77, light.getBrightness());
        assertEquals(40, light.getLightTemperature());

        DeviceEntity saved = deviceDao.getById(lightId);

        assertNotNull(saved);
        assertEquals(DeviceEntity.TYPE_LIGHT, saved.getType());
        assertEquals("Свет", saved.getName());
        assertEquals(roomId, saved.getRoomId());
        assertEquals("light-controller", saved.getControllerId());
        assertFalse(saved.isOnline());
        assertEquals(Boolean.FALSE, saved.getPoweredOn());
        assertEquals(Integer.valueOf(77), saved.getBrightness());
        assertEquals(Integer.valueOf(40), saved.getLightTemperature());

        DeviceEntity neighbour = deviceDao.getById(neighbourId);

        assertNotNull(neighbour);
        assertTrue(neighbour.isOnline());
        assertEquals(Boolean.TRUE, neighbour.getPoweredOn());
        assertEquals(2, deviceDao.getAll().size());
    }

    @Test
    public void updateLightState_boundaryPercentages_areAccepted()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int lightId = insertLight(roomId);

        int[] percentages = {0, 100};

        for (int brightness : percentages)
        {
            for (int lightTemperature : percentages)
            {
                TestCallback<LightDevice> callback =
                        new TestCallback<LightDevice>();

                repository.updateLightState(
                        lightId,
                        true,
                        true,
                        brightness,
                        lightTemperature,
                        callback
                );

                LightDevice light = callback.awaitSuccess();

                assertTrue(light.isOnline());
                assertTrue(light.isPoweredOn());
                assertEquals(brightness, light.getBrightness());
                assertEquals(lightTemperature, light.getLightTemperature());

                DeviceEntity saved = deviceDao.getById(lightId);

                assertNotNull(saved);
                assertEquals(
                        Integer.valueOf(brightness),
                        saved.getBrightness()
                );
                assertEquals(
                        Integer.valueOf(lightTemperature),
                        saved.getLightTemperature()
                );
                assertEquals(1, deviceDao.getAll().size());
            }
        }
    }

    @Test
    public void updateLightState_invalidPercentages_keepsEntirePreviousState()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int lightId = insertLight(roomId);

        int[] invalidValues = {-1, 101};

        for (int invalidValue : invalidValues)
        {
            TestCallback<LightDevice> brightnessCallback =
                    new TestCallback<LightDevice>();

            repository.updateLightState(
                    lightId,
                    false,
                    false,
                    invalidValue,
                    90,
                    brightnessCallback
            );

            Exception brightnessError = brightnessCallback.awaitError();

            assertTrue(brightnessError instanceof IllegalArgumentException);
            assertOriginalLight(lightId);

            TestCallback<LightDevice> temperatureCallback =
                    new TestCallback<LightDevice>();

            repository.updateLightState(
                    lightId,
                    false,
                    false,
                    80,
                    invalidValue,
                    temperatureCallback
            );

            Exception temperatureError = temperatureCallback.awaitError();

            assertTrue(temperatureError instanceof IllegalArgumentException);
            assertOriginalLight(lightId);
            assertEquals(1, deviceDao.getAll().size());
        }
    }

    @Test
    public void updateLightState_relayDevice_reportsErrorAndKeepsState()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int relayId = insertRelay(roomId);

        TestCallback<LightDevice> callback =
                new TestCallback<LightDevice>();

        repository.updateLightState(
                relayId,
                false,
                false,
                77,
                40,
                callback
        );

        Exception error = callback.awaitError();

        assertTrue(error instanceof IllegalArgumentException);

        DeviceEntity saved = deviceDao.getById(relayId);

        assertNotNull(saved);
        assertEquals(DeviceEntity.TYPE_RELAY, saved.getType());
        assertTrue(saved.isOnline());
        assertEquals(Boolean.TRUE, saved.getPoweredOn());
        assertNull(saved.getBrightness());
        assertNull(saved.getLightTemperature());
        assertEquals(1, deviceDao.getAll().size());
    }

    @Test
    public void updateLightState_invalidOrMissingId_reportsError()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int lightId = insertLight(roomId);

        int[] invalidOrMissingIds = {0, -1, 999};

        for (int id : invalidOrMissingIds)
        {
            TestCallback<LightDevice> callback =
                    new TestCallback<LightDevice>();

            repository.updateLightState(
                    id,
                    false,
                    false,
                    77,
                    40,
                    callback
            );

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);
            assertOriginalLight(lightId);
            assertEquals(1, deviceDao.getAll().size());
        }
    }

    private int insertLight(int roomId)
    {
        DeviceEntity entity = new DeviceEntity(
                0,
                "Свет",
                roomId,
                "light-controller",
                DeviceEntity.TYPE_LIGHT,
                true
        );

        entity.setPoweredOn(true);
        entity.setBrightness(20);
        entity.setLightTemperature(30);

        long generatedId = deviceDao.insert(entity);

        return checkedId(generatedId);
    }

    private void assertOriginalLight(int deviceId)
    {
        DeviceEntity saved = deviceDao.getById(deviceId);

        assertNotNull(saved);
        assertEquals(DeviceEntity.TYPE_LIGHT, saved.getType());
        assertEquals("Свет", saved.getName());
        assertEquals("light-controller", saved.getControllerId());
        assertTrue(saved.isOnline());
        assertEquals(Boolean.TRUE, saved.getPoweredOn());
        assertEquals(Integer.valueOf(20), saved.getBrightness());
        assertEquals(Integer.valueOf(30), saved.getLightTemperature());
    }

    @Test
    public void updateWaterState_completeData_updatesKnownAndUnknownState()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        boolean[] initialStates = {false, true};
        long newTime = 1700000120000L;

        for (boolean withReadings : initialStates)
        {
            int waterId = insertWater(roomId, withReadings);

            TestCallback<WaterDevice> callback =
                    new TestCallback<WaterDevice>();

            repository.updateWaterState(
                    waterId,
                    false,
                    80,
                    25.5,
                    "  Высокий  ",
                    newTime,
                    false,
                    callback
            );

            WaterDevice water = callback.awaitSuccess();

            assertEquals(waterId, water.getId());
            assertEquals("Бак", water.getName());
            assertEquals(roomId, water.getRoomId());
            assertEquals("water-controller", water.getControllerId());
            assertFalse(water.isOnline());
            assertEquals(Integer.valueOf(80), water.getWaterLevel());
            assertEquals(Double.valueOf(25.5), water.getTemperature());
            assertEquals("Высокий", water.getLevelStatus());
            assertEquals(Long.valueOf(newTime), water.getLastUpdate());
            assertEquals(Boolean.FALSE, water.getValveOpen());

            DeviceEntity saved = deviceDao.getById(waterId);

            assertNotNull(saved);
            assertEquals(DeviceEntity.TYPE_WATER, saved.getType());
            assertEquals("Бак", saved.getName());
            assertEquals(roomId, saved.getRoomId());
            assertEquals("water-controller", saved.getControllerId());
            assertFalse(saved.isOnline());
            assertEquals(Integer.valueOf(80), saved.getWaterLevel());
            assertEquals(Double.valueOf(25.5), saved.getTemperature());
            assertEquals("Высокий", saved.getLevelStatus());
            assertEquals(Long.valueOf(newTime), saved.getLastUpdate());
            assertEquals(Boolean.FALSE, saved.getValveOpen());
        }

        assertEquals(2, deviceDao.getAll().size());
    }

    @Test
    public void updateWaterState_readingsOnly_keepsValveAndAcceptsLevelBoundaries()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int waterId = insertWater(roomId, true);

        int[] levels = {0, 100};
        long measuredAt = 1700000120000L;

        for (int level : levels)
        {
            TestCallback<WaterDevice> callback =
                    new TestCallback<WaterDevice>();

            repository.updateWaterState(
                    waterId,
                    true,
                    level,
                    24.5,
                    "Новый статус",
                    measuredAt,
                    null,
                    callback
            );

            WaterDevice water = callback.awaitSuccess();

            assertEquals(Integer.valueOf(level), water.getWaterLevel());
            assertEquals(Double.valueOf(24.5), water.getTemperature());
            assertEquals("Новый статус", water.getLevelStatus());
            assertEquals(Long.valueOf(measuredAt), water.getLastUpdate());

            // null в аргументе клапана не должен стереть прежнее true.
            assertEquals(Boolean.TRUE, water.getValveOpen());

            DeviceEntity saved = deviceDao.getById(waterId);

            assertNotNull(saved);
            assertEquals(Integer.valueOf(level), saved.getWaterLevel());
            assertEquals(Double.valueOf(24.5), saved.getTemperature());
            assertEquals("Новый статус", saved.getLevelStatus());
            assertEquals(Long.valueOf(measuredAt), saved.getLastUpdate());
            assertEquals(Boolean.TRUE, saved.getValveOpen());
        }
    }

    @Test
    public void updateWaterState_valveOnly_keepsReadingsAndMeasurementTime()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int waterId = insertWater(roomId, true);

        boolean[] valveStates = {false, true};

        for (boolean valveOpen : valveStates)
        {
            TestCallback<WaterDevice> callback =
                    new TestCallback<WaterDevice>();

            repository.updateWaterState(
                    waterId,
                    true,
                    null,
                    null,
                    null,
                    null,
                    valveOpen,
                    callback
            );

            WaterDevice water = callback.awaitSuccess();

            assertEquals(Boolean.valueOf(valveOpen), water.getValveOpen());
            assertEquals(Integer.valueOf(64), water.getWaterLevel());
            assertEquals(Double.valueOf(22.4), water.getTemperature());
            assertEquals("Норма", water.getLevelStatus());
            assertEquals(
                    Long.valueOf(1700000060000L),
                    water.getLastUpdate()
            );

            DeviceEntity saved = deviceDao.getById(waterId);

            assertNotNull(saved);
            assertEquals(Boolean.valueOf(valveOpen), saved.getValveOpen());
            assertEquals(Integer.valueOf(64), saved.getWaterLevel());
            assertEquals(Double.valueOf(22.4), saved.getTemperature());
            assertEquals("Норма", saved.getLevelStatus());
            assertEquals(
                    Long.valueOf(1700000060000L),
                    saved.getLastUpdate()
            );
        }
    }

    @Test
    public void updateWaterState_allNull_changesOnlyOnline()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        boolean[] initialStates = {false, true};

        for (boolean withReadings : initialStates)
        {
            int waterId = insertWater(roomId, withReadings);

            DeviceEntity before = deviceDao.getById(waterId);
            assertNotNull(before);

            TestCallback<WaterDevice> callback =
                    new TestCallback<WaterDevice>();

            repository.updateWaterState(
                    waterId,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    callback
            );

            WaterDevice water = callback.awaitSuccess();

            assertFalse(water.isOnline());
            assertEquals(before.getWaterLevel(), water.getWaterLevel());
            assertEquals(before.getTemperature(), water.getTemperature());
            assertEquals(before.getLevelStatus(), water.getLevelStatus());
            assertEquals(before.getLastUpdate(), water.getLastUpdate());
            assertEquals(before.getValveOpen(), water.getValveOpen());

            DeviceEntity saved = deviceDao.getById(waterId);

            assertNotNull(saved);
            assertFalse(saved.isOnline());
            assertEquals(before.getWaterLevel(), saved.getWaterLevel());
            assertEquals(before.getTemperature(), saved.getTemperature());
            assertEquals(before.getLevelStatus(), saved.getLevelStatus());
            assertEquals(before.getLastUpdate(), saved.getLastUpdate());
            assertEquals(before.getValveOpen(), saved.getValveOpen());
        }
    }

    @Test
    public void updateWaterState_incompleteReadings_keepsEntirePreviousState()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int waterId = insertWater(roomId, true);

        for (int missingField = 0; missingField < 4; missingField++)
        {
            Integer waterLevel = 80;
            Double temperature = 25.5;
            String levelStatus = "Новый статус";
            Long lastUpdate = 1700000120000L;

            if (missingField == 0)
            {
                waterLevel = null;
            }
            else if (missingField == 1)
            {
                temperature = null;
            }
            else if (missingField == 2)
            {
                levelStatus = null;
            }
            else
            {
                lastUpdate = null;
            }

            TestCallback<WaterDevice> callback =
                    new TestCallback<WaterDevice>();

            repository.updateWaterState(
                    waterId,
                    false,
                    waterLevel,
                    temperature,
                    levelStatus,
                    lastUpdate,
                    false,
                    callback
            );

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);
            assertOriginalWater(waterId);
            assertEquals(1, deviceDao.getAll().size());
        }
    }

    @Test
    public void updateWaterState_invalidReadings_keepsEntirePreviousState()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int waterId = insertWater(roomId, true);

        for (int invalidCase = 0; invalidCase < 9; invalidCase++)
        {
            int waterLevel = 80;
            double temperature = 25.5;
            String levelStatus = "Новый статус";
            long lastUpdate = 1700000120000L;

            if (invalidCase == 0)
            {
                waterLevel = -1;
            }
            else if (invalidCase == 1)
            {
                waterLevel = 101;
            }
            else if (invalidCase == 2)
            {
                temperature = Double.NaN;
            }
            else if (invalidCase == 3)
            {
                temperature = Double.POSITIVE_INFINITY;
            }
            else if (invalidCase == 4)
            {
                temperature = Double.NEGATIVE_INFINITY;
            }
            else if (invalidCase == 5)
            {
                levelStatus = "";
            }
            else if (invalidCase == 6)
            {
                levelStatus = "   ";
            }
            else if (invalidCase == 7)
            {
                lastUpdate = 0;
            }
            else
            {
                lastUpdate = -1;
            }

            TestCallback<WaterDevice> callback =
                    new TestCallback<WaterDevice>();

            repository.updateWaterState(
                    waterId,
                    false,
                    waterLevel,
                    temperature,
                    levelStatus,
                    lastUpdate,
                    false,
                    callback
            );

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);
            assertOriginalWater(waterId);
            assertEquals(1, deviceDao.getAll().size());
        }
    }

    @Test
    public void updateWaterState_otherDeviceTypes_reportsErrorAndKeepsData()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int relayId = insertRelay(roomId);
        int lightId = insertLight(roomId);

        int[] deviceIds = {relayId, lightId};

        for (int deviceId : deviceIds)
        {
            TestCallback<WaterDevice> callback =
                    new TestCallback<WaterDevice>();

            repository.updateWaterState(
                    deviceId,
                    false,
                    80,
                    25.5,
                    "Норма",
                    1700000120000L,
                    false,
                    callback
            );

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);

            DeviceEntity saved = deviceDao.getById(deviceId);

            assertNotNull(saved);
            assertTrue(saved.isOnline());
            assertNull(saved.getWaterLevel());
            assertNull(saved.getTemperature());
            assertNull(saved.getLevelStatus());
            assertNull(saved.getLastUpdate());
            assertNull(saved.getValveOpen());
        }

        DeviceEntity relay = deviceDao.getById(relayId);

        assertNotNull(relay);
        assertEquals(DeviceEntity.TYPE_RELAY, relay.getType());
        assertEquals(Boolean.TRUE, relay.getPoweredOn());

        assertOriginalLight(lightId);
        assertEquals(2, deviceDao.getAll().size());
    }

    @Test
    public void updateWaterState_invalidOrMissingId_reportsError()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int waterId = insertWater(roomId, true);

        int[] invalidOrMissingIds = {0, -1, 999};

        for (int deviceId : invalidOrMissingIds)
        {
            TestCallback<WaterDevice> callback =
                    new TestCallback<WaterDevice>();

            repository.updateWaterState(
                    deviceId,
                    false,
                    80,
                    25.5,
                    "Новый статус",
                    1700000120000L,
                    false,
                    callback
            );

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);
            assertOriginalWater(waterId);
            assertEquals(1, deviceDao.getAll().size());
        }
    }

    private int insertWater(int roomId, boolean withReadings)
    {
        DeviceEntity entity = new DeviceEntity(
                0,
                "Бак",
                roomId,
                "water-controller",
                DeviceEntity.TYPE_WATER,
                true
        );

        if (withReadings)
        {
            entity.setWaterLevel(64);
            entity.setTemperature(22.4);
            entity.setLevelStatus("Норма");
            entity.setLastUpdate(1700000060000L);
            entity.setValveOpen(true);
        }

        long generatedId = deviceDao.insert(entity);

        return checkedId(generatedId);
    }

    private void assertOriginalWater(int deviceId)
    {
        DeviceEntity saved = deviceDao.getById(deviceId);

        assertNotNull(saved);
        assertEquals(DeviceEntity.TYPE_WATER, saved.getType());
        assertEquals("Бак", saved.getName());
        assertEquals("water-controller", saved.getControllerId());
        assertTrue(saved.isOnline());
        assertEquals(Integer.valueOf(64), saved.getWaterLevel());
        assertEquals(Double.valueOf(22.4), saved.getTemperature());
        assertEquals("Норма", saved.getLevelStatus());
        assertEquals(
                Long.valueOf(1700000060000L),
                saved.getLastUpdate()
        );
        assertEquals(Boolean.TRUE, saved.getValveOpen());
    }

    // 1. Изменение доступности реле не меняет его питание.
    @Test
    public void updateDeviceOnline_relay_keepsPowerAndCommonData()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");
        int deviceId = insertRelay(roomId);

        boolean[] onlineValues = {false, true};

        for (boolean online : onlineValues)
        {
            TestCallback<Device> callback = new TestCallback<Device>();

            repository.updateDeviceOnline(deviceId, online, callback);

            Device result = callback.awaitSuccess();

            assertTrue(result instanceof RelayDevice);

            RelayDevice relay = (RelayDevice) result;

            assertEquals(deviceId, relay.getId());
            assertEquals(roomId, relay.getRoomId());
            assertEquals("Реле", relay.getName());
            assertEquals("test-controller", relay.getControllerId());
            assertEquals(online, relay.isOnline());
            assertTrue(relay.isPoweredOn());

            DeviceEntity saved = deviceDao.getById(deviceId);

            assertNotNull(saved);
            assertEquals(DeviceEntity.TYPE_RELAY, saved.getType());
            assertEquals(roomId, saved.getRoomId());
            assertEquals("Реле", saved.getName());
            assertEquals("test-controller", saved.getControllerId());
            assertEquals(online, saved.isOnline());
            assertEquals(Boolean.TRUE, saved.getPoweredOn());
        }
    }

    // 2. Изменение доступности света сохраняет его настройки.
    @Test
    public void updateDeviceOnline_light_keepsPowerAndSettings()
            throws InterruptedException
    {
        int roomId = insertRoom("Гостиная");
        int deviceId = insertLight(roomId);

        boolean[] onlineValues = {false, true};

        for (boolean online : onlineValues)
        {
            TestCallback<Device> callback = new TestCallback<Device>();

            repository.updateDeviceOnline(deviceId, online, callback);

            Device result = callback.awaitSuccess();

            assertTrue(result instanceof LightDevice);

            LightDevice light = (LightDevice) result;

            assertEquals(deviceId, light.getId());
            assertEquals(roomId, light.getRoomId());
            assertEquals("Свет", light.getName());
            assertEquals("light-controller", light.getControllerId());
            assertEquals(online, light.isOnline());
            assertTrue(light.isPoweredOn());
            assertEquals(20, light.getBrightness());
            assertEquals(30, light.getLightTemperature());

            DeviceEntity saved = deviceDao.getById(deviceId);

            assertNotNull(saved);
            assertEquals(DeviceEntity.TYPE_LIGHT, saved.getType());
            assertEquals(roomId, saved.getRoomId());
            assertEquals("Свет", saved.getName());
            assertEquals("light-controller", saved.getControllerId());
            assertEquals(online, saved.isOnline());
            assertEquals(Boolean.TRUE, saved.getPoweredOn());
            assertEquals(Integer.valueOf(20), saved.getBrightness());
            assertEquals(Integer.valueOf(30), saved.getLightTemperature());
        }
    }

    // 3. Показания воды сохраняются, а неизвестные значения остаются null.
    @Test
    public void updateDeviceOnline_water_keepsKnownAndUnknownReadings()
            throws InterruptedException
    {
        int roomId = insertRoom("Ванная");

        boolean[] readingsVariants = {false, true};
        boolean[] onlineValues = {false, true};

        for (boolean withReadings : readingsVariants)
        {
            int deviceId = insertWater(roomId, withReadings);

            DeviceEntity original = deviceDao.getById(deviceId);

            assertNotNull(original);

            for (boolean online : onlineValues)
            {
                TestCallback<Device> callback = new TestCallback<Device>();

                repository.updateDeviceOnline(deviceId, online, callback);

                Device result = callback.awaitSuccess();

                assertTrue(result instanceof WaterDevice);

                WaterDevice water = (WaterDevice) result;

                assertEquals(deviceId, water.getId());
                assertEquals(roomId, water.getRoomId());
                assertEquals(original.getName(), water.getName());
                assertEquals(
                        original.getControllerId(),
                        water.getControllerId()
                );
                assertEquals(online, water.isOnline());

                assertEquals(original.getWaterLevel(), water.getWaterLevel());
                assertEquals(original.getTemperature(), water.getTemperature());
                assertEquals(original.getLevelStatus(), water.getLevelStatus());
                assertEquals(original.getLastUpdate(), water.getLastUpdate());
                assertEquals(original.getValveOpen(), water.getValveOpen());

                DeviceEntity saved = deviceDao.getById(deviceId);

                assertNotNull(saved);
                assertEquals(DeviceEntity.TYPE_WATER, saved.getType());
                assertEquals(roomId, saved.getRoomId());
                assertEquals(original.getName(), saved.getName());
                assertEquals(
                        original.getControllerId(),
                        saved.getControllerId()
                );
                assertEquals(online, saved.isOnline());

                assertEquals(original.getWaterLevel(), saved.getWaterLevel());
                assertEquals(original.getTemperature(), saved.getTemperature());
                assertEquals(original.getLevelStatus(), saved.getLevelStatus());
                assertEquals(original.getLastUpdate(), saved.getLastUpdate());
                assertEquals(original.getValveOpen(), saved.getValveOpen());

                assertNull(saved.getPoweredOn());
                assertNull(saved.getBrightness());
                assertNull(saved.getLightTemperature());
            }
        }
    }

    // 4. Обновляется только выбранное устройство, даже при общем контроллере.
    @Test
    public void updateDeviceOnline_sameController_keepsOtherDeviceUnchanged()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");

        int deviceId = insertRelay(roomId);
        int neighbourId = insertRelay(roomId);

        DeviceEntity first = deviceDao.getById(deviceId);
        DeviceEntity neighbourBefore = deviceDao.getById(neighbourId);

        assertNotNull(first);
        assertNotNull(neighbourBefore);
        assertEquals(
                first.getControllerId(),
                neighbourBefore.getControllerId()
        );

        TestCallback<Device> callback = new TestCallback<Device>();

        repository.updateDeviceOnline(deviceId, false, callback);

        Device result = callback.awaitSuccess();

        assertEquals(deviceId, result.getId());
        assertFalse(result.isOnline());

        DeviceEntity updated = deviceDao.getById(deviceId);
        DeviceEntity neighbourAfter = deviceDao.getById(neighbourId);

        assertNotNull(updated);
        assertNotNull(neighbourAfter);

        assertFalse(updated.isOnline());
        assertEquals(Boolean.TRUE, updated.getPoweredOn());

        assertTrue(neighbourAfter.isOnline());
        assertEquals(Boolean.TRUE, neighbourAfter.getPoweredOn());
        assertEquals(neighbourBefore.getName(), neighbourAfter.getName());
        assertEquals(
                neighbourBefore.getRoomId(),
                neighbourAfter.getRoomId()
        );
        assertEquals(
                neighbourBefore.getControllerId(),
                neighbourAfter.getControllerId()
        );

        assertEquals(2, deviceDao.getAll().size());
        assertEquals(1, roomDao.getAll().size());
    }

    // 5. Неверный или отсутствующий id приводит к ошибке без изменения базы.
    @Test
    public void updateDeviceOnline_invalidOrMissingId_reportsError()
            throws InterruptedException
    {
        int roomId = insertRoom("Гостиная");
        int deviceId = insertRelay(roomId);

        int[] invalidIds = {0, -1, 999};

        for (int invalidId : invalidIds)
        {
            TestCallback<Device> callback = new TestCallback<Device>();

            repository.updateDeviceOnline(invalidId, false, callback);

            Exception error = callback.awaitError();

            assertTrue(error instanceof IllegalArgumentException);

            DeviceEntity saved = deviceDao.getById(deviceId);

            assertNotNull(saved);
            assertTrue(saved.isOnline());
            assertEquals(Boolean.TRUE, saved.getPoweredOn());
            assertEquals("Реле", saved.getName());
            assertEquals(roomId, saved.getRoomId());
            assertEquals("test-controller", saved.getControllerId());

            assertEquals(1, deviceDao.getAll().size());
            assertEquals(1, roomDao.getAll().size());
        }
    }

    // 6. Некорректная запись не должна обновляться даже частично.
    @Test
    public void updateDeviceOnline_invalidStoredRecord_keepsPreviousState()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");

        DeviceEntity invalidRelay = new DeviceEntity(
                0,
                "Реле",
                roomId,
                "test-controller",
                DeviceEntity.TYPE_RELAY,
                true
        );

        invalidRelay.setPoweredOn(true);

        // Яркость относится к свету. У реле это поле должно быть null.
        invalidRelay.setBrightness(50);

        int deviceId = checkedId(deviceDao.insert(invalidRelay));

        TestCallback<Device> callback = new TestCallback<Device>();

        repository.updateDeviceOnline(deviceId, false, callback);

        Exception error = callback.awaitError();

        assertTrue(error instanceof IllegalStateException);

        DeviceEntity saved = deviceDao.getById(deviceId);

        assertNotNull(saved);

        // Операция завершилась ошибкой, поэтому online остался true.
        assertTrue(saved.isOnline());
        assertEquals(Boolean.TRUE, saved.getPoweredOn());
        assertEquals(Integer.valueOf(50), saved.getBrightness());

        assertEquals(DeviceEntity.TYPE_RELAY, saved.getType());
        assertEquals("Реле", saved.getName());
        assertEquals(roomId, saved.getRoomId());
        assertEquals("test-controller", saved.getControllerId());
        assertEquals(1, deviceDao.getAll().size());
    }

    // 1. Неверные общие параметры не позволяют добавить устройство.
    @Test
    public void addDevices_invalidCommonData_reportsErrorAndSavesNothing()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");

        String[] invalidStrings = {null, "", "   "};

        for (String invalidValue : invalidStrings)
        {
            // Проверяем неверное название.
            assertDeviceCreationRejected(
                    invalidValue,
                    roomId,
                    "test-controller"
            );

            // Проверяем неверный идентификатор контроллера.
            assertDeviceCreationRejected(
                    "Устройство",
                    roomId,
                    invalidValue
            );
        }

        // Проверяем неверный идентификатор комнаты.
        assertDeviceCreationRejected("Устройство", 0, "test-controller");
        assertDeviceCreationRejected("Устройство", -1, "test-controller");

        assertEquals(0, deviceDao.getAll().size());
        assertEquals(1, roomDao.getAll().size());

        RoomEntity savedRoom = roomDao.getById(roomId);

        assertNotNull(savedRoom);
        assertEquals("Кухня", savedRoom.getName());
    }

    // Один набор параметров проверяем для всех трёх типов устройств.
    private void assertDeviceCreationRejected(
            String name,
            int roomId,
            String controllerId)
            throws InterruptedException
    {
        TestCallback<RelayDevice> relayCallback =
                new TestCallback<RelayDevice>();

        repository.addRelayDevice(
                name,
                roomId,
                controllerId,
                true,
                true,
                relayCallback
        );

        Exception relayError = relayCallback.awaitError();

        assertTrue(relayError instanceof IllegalArgumentException);
        assertEquals(0, deviceDao.getAll().size());

        TestCallback<LightDevice> lightCallback =
                new TestCallback<LightDevice>();

        repository.addLightDevice(
                name,
                roomId,
                controllerId,
                true,
                true,
                50,
                50,
                lightCallback
        );

        Exception lightError = lightCallback.awaitError();

        assertTrue(lightError instanceof IllegalArgumentException);
        assertEquals(0, deviceDao.getAll().size());

        TestCallback<WaterDevice> waterCallback =
                new TestCallback<WaterDevice>();

        repository.addWaterDevice(
                name,
                roomId,
                controllerId,
                true,
                null,
                null,
                null,
                null,
                null,
                waterCallback
        );

        Exception waterError = waterCallback.awaitError();

        assertTrue(waterError instanceof IllegalArgumentException);
        assertEquals(0, deviceDao.getAll().size());
    }

    // 2. Неверные показания воды отклоняются уже при добавлении.
    @Test
    public void addWaterDevice_invalidReadings_reportsErrorAndSavesNothing()
            throws InterruptedException
    {
        int roomId = insertRoom("Ванная");
        long validTime = 1700000060000L;

        // Уровень за допустимыми границами.
        assertWaterCreationRejected(roomId, -1, 22.4, "Норма", validTime);
        assertWaterCreationRejected(roomId, 101, 22.4, "Норма", validTime);

        // Температура должна быть конечным числом.
        assertWaterCreationRejected(
                roomId, 64, Double.NaN, "Норма", validTime
        );
        assertWaterCreationRejected(
                roomId, 64, Double.POSITIVE_INFINITY, "Норма", validTime
        );
        assertWaterCreationRejected(
                roomId, 64, Double.NEGATIVE_INFINITY, "Норма", validTime
        );

        // Статус не должен быть пустым.
        assertWaterCreationRejected(roomId, 64, 22.4, "", validTime);
        assertWaterCreationRejected(roomId, 64, 22.4, "   ", validTime);

        // Время измерения должно быть положительным.
        assertWaterCreationRejected(roomId, 64, 22.4, "Норма", 0L);
        assertWaterCreationRejected(roomId, 64, 22.4, "Норма", -1L);

        assertEquals(0, deviceDao.getAll().size());
        assertEquals(1, roomDao.getAll().size());
    }

    private void assertWaterCreationRejected(
            int roomId,
            int waterLevel,
            double temperature,
            String levelStatus,
            long lastUpdate)
            throws InterruptedException
    {
        TestCallback<WaterDevice> callback =
                new TestCallback<WaterDevice>();

        repository.addWaterDevice(
                "Бак",
                roomId,
                "water-controller",
                true,
                waterLevel,
                temperature,
                levelStatus,
                lastUpdate,
                true,
                callback
        );

        Exception error = callback.awaitError();

        assertTrue(error instanceof IllegalArgumentException);
        assertEquals(0, deviceDao.getAll().size());
    }

    // 3. Ошибка после вставки комнаты должна отменить эту вставку.
    @Test
    public void addRoom_generatedIdExceedsInt_rollsBackInsert()
            throws InterruptedException
    {
        RoomEntity existingRoom = new RoomEntity(
                Integer.MAX_VALUE,
                "Существующая"
        );

        roomDao.insert(existingRoom);

        TestCallback<Room> callback = new TestCallback<Room>();

        repository.addRoom("Новая", callback);

        Exception error = callback.awaitError();

        assertTrue(error instanceof IllegalStateException);

        // В базе должна остаться только исходная комната.
        List<RoomEntity> savedRooms = roomDao.getAll();

        assertEquals(1, savedRooms.size());
        assertEquals(Integer.MAX_VALUE, savedRooms.get(0).getId());
        assertEquals("Существующая", savedRooms.get(0).getName());
        assertEquals(0, deviceDao.getAll().size());
    }

    // 4. Такая же ошибка должна отменить добавление любого типа устройства.
    @Test
    public void addDevices_generatedIdExceedsInt_rollsBackEveryInsert()
            throws InterruptedException
    {
        int roomId = insertRoom("Кухня");

        DeviceEntity existingDevice = new DeviceEntity(
                Integer.MAX_VALUE,
                "Существующее реле",
                roomId,
                "existing-controller",
                DeviceEntity.TYPE_RELAY,
                true
        );

        existingDevice.setPoweredOn(true);
        deviceDao.insert(existingDevice);

        TestCallback<RelayDevice> relayCallback =
                new TestCallback<RelayDevice>();

        repository.addRelayDevice(
                "Новое реле",
                roomId,
                "relay-controller",
                true,
                false,
                relayCallback
        );

        Exception relayError = relayCallback.awaitError();

        assertTrue(relayError instanceof IllegalStateException);
        assertEquals(1, deviceDao.getAll().size());

        TestCallback<LightDevice> lightCallback =
                new TestCallback<LightDevice>();

        repository.addLightDevice(
                "Новый свет",
                roomId,
                "light-controller",
                true,
                true,
                50,
                50,
                lightCallback
        );

        Exception lightError = lightCallback.awaitError();

        assertTrue(lightError instanceof IllegalStateException);
        assertEquals(1, deviceDao.getAll().size());

        TestCallback<WaterDevice> waterCallback =
                new TestCallback<WaterDevice>();

        repository.addWaterDevice(
                "Новый бак",
                roomId,
                "water-controller",
                true,
                null,
                null,
                null,
                null,
                null,
                waterCallback
        );

        Exception waterError = waterCallback.awaitError();

        assertTrue(waterError instanceof IllegalStateException);
        assertEquals(1, deviceDao.getAll().size());

        // Исходное устройство и его комната должны сохраниться.
        DeviceEntity saved = deviceDao.getById(Integer.MAX_VALUE);

        assertNotNull(saved);
        assertEquals("Существующее реле", saved.getName());
        assertEquals(roomId, saved.getRoomId());
        assertEquals("existing-controller", saved.getControllerId());
        assertEquals(DeviceEntity.TYPE_RELAY, saved.getType());
        assertTrue(saved.isOnline());
        assertEquals(Boolean.TRUE, saved.getPoweredOn());

        assertEquals(1, roomDao.getAll().size());
        assertNotNull(roomDao.getById(roomId));
    }

    private int insertRelay(int roomId)
    {
        DeviceEntity entity = new DeviceEntity(
                0,
                "Реле",
                roomId,
                "test-controller",
                DeviceEntity.TYPE_RELAY,
                true
        );

        entity.setPoweredOn(true);

        long generatedId = deviceDao.insert(entity);

        return checkedId(generatedId);
    }

    private int checkedId(long id)
    {
        assertTrue("Идентификатор должен быть положительным.", id > 0);
        assertTrue("Идентификатор должен помещаться в int.",
                id <= Integer.MAX_VALUE);

        return (int) id;
    }

    private static class TestCallback<T> implements RepositoryCallback<T>
    {
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicInteger callCount = new AtomicInteger(0);
        private final AtomicBoolean correctThread = new AtomicBoolean(true);

        private T result;
        private Exception error;

        @Override
        public void onSuccess(@NonNull T result)
        {
            this.result = result;
            recordCall();
        }

        @Override
        public void onError(@NonNull Exception error)
        {
            this.error = error;
            recordCall();
        }

        private void recordCall()
        {
            if (Looper.myLooper() != Looper.getMainLooper())
            {
                correctThread.set(false);
            }

            callCount.incrementAndGet();
            completed.countDown();
        }

        public T awaitSuccess() throws InterruptedException
        {
            awaitCompletion();

            assertNull("Ожидался успех, но получена ошибка: " + error, error);
            assertNotNull("Результат не должен быть null.", result);

            return result;
        }

        public Exception awaitError() throws InterruptedException
        {
            awaitCompletion();

            assertNull("При ошибке успешного результата быть не должно.", result);
            assertNotNull("Ожидалась ошибка.", error);

            return error;
        }

        private void awaitCompletion() throws InterruptedException
        {
            assertFalse(
                    "Нельзя ожидать callback в главном потоке.",
                    Looper.myLooper() == Looper.getMainLooper()
            );

            boolean received = completed.await(10, TimeUnit.SECONDS);

            assertTrue("Callback не получен за 10 секунд.", received);

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            assertTrue(
                    "Callback должен вызываться в главном потоке.",
                    correctThread.get()
            );

            assertEquals(
                    "Ожидался один вызов callback.",
                    1,
                    callCount.get()
            );
        }
    }
}
