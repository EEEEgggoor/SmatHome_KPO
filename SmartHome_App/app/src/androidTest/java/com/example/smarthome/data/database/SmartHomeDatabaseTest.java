package com.example.smarthome.data.database;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class SmartHomeDatabaseTest
{
    private SmartHomeDatabase database;
    private RoomDao roomDao;
    private DeviceDao deviceDao;

    @Before
    public void setUp()
    {
        Context context = InstrumentationRegistry
                .getInstrumentation()
                .getTargetContext();

        RoomDatabase.Builder<SmartHomeDatabase> builder =
                Room.inMemoryDatabaseBuilder(
                        context,
                        SmartHomeDatabase.class
                );

        database = builder.build();

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
    public void newDatabase_hasNoRoomsOrDevices()
    {
        assertTrue(roomDao.getAll().isEmpty());
        assertTrue(deviceDao.getAll().isEmpty());
    }

    @Test
    public void insertRoom_generatesDifferentIdsAndSavesTrimmedName()
    {
        RoomEntity firstRoom = new RoomEntity(0, "  Кухня  ");
        RoomEntity secondRoom = new RoomEntity(0, "Гостиная");

        int firstId = checkedId(roomDao.insert(firstRoom));
        int secondId = checkedId(roomDao.insert(secondRoom));

        assertTrue(firstId != secondId);

        RoomEntity savedRoom = roomDao.getById(firstId);

        assertNotNull(savedRoom);
        assertEquals(firstId, savedRoom.getId());
        assertEquals("Кухня", savedRoom.getName());
        assertEquals(2, roomDao.getAll().size());

        // Вставка не изменяет final id исходного объекта.
        assertEquals(0, firstRoom.getId());
    }

    @Test
    public void updateRoom_changesNameAndKeepsDevices()
    {
        int roomId = insertRoom("Кухня");

        DeviceEntity relay = createRelay(roomId, "Реле чайника");
        int deviceId = checkedId(deviceDao.insert(relay));

        RoomEntity room = roomDao.getById(roomId);
        assertNotNull(room);

        room.setName("  Кухонька  ");

        int updatedCount = roomDao.update(room);

        RoomEntity savedRoom = roomDao.getById(roomId);
        DeviceEntity savedDevice = deviceDao.getById(deviceId);

        assertEquals(1, updatedCount);
        assertNotNull(savedRoom);
        assertEquals("Кухонька", savedRoom.getName());

        assertNotNull(savedDevice);
        assertEquals(roomId, savedDevice.getRoomId());
    }

    @Test
    public void insertRoom_duplicateId_keepsOriginalRoomAndDevices()
    {
        int roomId = insertRoom("Кухня");

        DeviceEntity relay = createRelay(roomId, "Реле чайника");
        int deviceId = checkedId(deviceDao.insert(relay));

        RoomEntity duplicate = new RoomEntity(roomId, "Другая комната");

        try
        {
            roomDao.insert(duplicate);
            fail("Ожидалась ошибка из-за повторного идентификатора.");
        }
        catch (SQLiteConstraintException expected)
        {
            // База должна отклонить вставку с занятым id.
        }

        RoomEntity savedRoom = roomDao.getById(roomId);

        assertNotNull(savedRoom);
        assertEquals("Кухня", savedRoom.getName());
        assertEquals(1, roomDao.getAll().size());
        assertNotNull(deviceDao.getById(deviceId));
    }

    @Test
    public void insertLight_savesAllLightFields()
    {
        int roomId = insertRoom("Гостиная");

        DeviceEntity light = new DeviceEntity(
                0,
                "  Лента  ",
                roomId,
                "  ESP32-01  ",
                DeviceEntity.TYPE_LIGHT,
                true
        );

        light.setPoweredOn(true);
        light.setBrightness(77);
        light.setLightTemperature(40);

        int deviceId = checkedId(deviceDao.insert(light));

        DeviceEntity savedLight = deviceDao.getById(deviceId);

        assertNotNull(savedLight);
        assertEquals(deviceId, savedLight.getId());
        assertEquals("Лента", savedLight.getName());
        assertEquals(roomId, savedLight.getRoomId());
        assertEquals("ESP32-01", savedLight.getControllerId());
        assertEquals(DeviceEntity.TYPE_LIGHT, savedLight.getType());
        assertTrue(savedLight.isOnline());

        assertEquals(Boolean.TRUE, savedLight.getPoweredOn());
        assertEquals(Integer.valueOf(77), savedLight.getBrightness());
        assertEquals(Integer.valueOf(40), savedLight.getLightTemperature());

        assertNull(savedLight.getWaterLevel());
        assertNull(savedLight.getTemperature());
        assertNull(savedLight.getLastUpdate());
        assertNull(savedLight.getLevelStatus());
        assertNull(savedLight.getValveOpen());
    }

    @Test
    public void insertWater_unknownReadings_remainNull()
    {
        int roomId = insertRoom("Ванная");

        DeviceEntity water = new DeviceEntity(
                0,
                "Датчик воды",
                roomId,
                "ESP32-WATER",
                DeviceEntity.TYPE_WATER,
                false
        );

        int deviceId = checkedId(deviceDao.insert(water));

        DeviceEntity savedWater = deviceDao.getById(deviceId);

        assertNotNull(savedWater);
        assertEquals(DeviceEntity.TYPE_WATER, savedWater.getType());
        assertFalse(savedWater.isOnline());

        assertNull(savedWater.getWaterLevel());
        assertNull(savedWater.getTemperature());
        assertNull(savedWater.getLastUpdate());
        assertNull(savedWater.getLevelStatus());
        assertNull(savedWater.getValveOpen());

        assertNull(savedWater.getPoweredOn());
        assertNull(savedWater.getBrightness());
        assertNull(savedWater.getLightTemperature());
    }

    @Test
    public void updateWater_savesReadingsNameAndState()
    {
        int roomId = insertRoom("Ванная");

        DeviceEntity water = new DeviceEntity(
                0,
                "Датчик воды",
                roomId,
                "ESP32-WATER",
                DeviceEntity.TYPE_WATER,
                false
        );

        int deviceId = checkedId(deviceDao.insert(water));

        DeviceEntity savedWater = deviceDao.getById(deviceId);
        assertNotNull(savedWater);

        savedWater.setName("Бак");
        savedWater.setOnline(true);
        savedWater.setWaterLevel(64);
        savedWater.setTemperature(22.4);
        savedWater.setLastUpdate(1700000060000L);
        savedWater.setLevelStatus("  Норма  ");
        savedWater.setValveOpen(false);

        int updatedCount = deviceDao.update(savedWater);

        DeviceEntity updatedWater = deviceDao.getById(deviceId);

        assertEquals(1, updatedCount);
        assertNotNull(updatedWater);
        assertEquals("Бак", updatedWater.getName());
        assertTrue(updatedWater.isOnline());
        assertEquals(Integer.valueOf(64), updatedWater.getWaterLevel());

        assertNotNull(updatedWater.getTemperature());
        assertEquals(
                22.4,
                updatedWater.getTemperature().doubleValue(),
                0.0001
        );

        assertEquals(
                Long.valueOf(1700000060000L),
                updatedWater.getLastUpdate()
        );

        assertEquals("Норма", updatedWater.getLevelStatus());
        assertEquals(Boolean.FALSE, updatedWater.getValveOpen());
        assertEquals(1, deviceDao.getAll().size());
    }

    @Test
    public void getByRoomId_returnsOnlySelectedRoomDevicesInIdOrder()
    {
        int kitchenId = insertRoom("Кухня");
        int bedroomId = insertRoom("Спальня");

        // Вставляем не по порядку, чтобы проверить ORDER BY.
        DeviceEntity firstRelay = new DeviceEntity(
                20, "Реле 20", kitchenId,
                "ESP32-01", DeviceEntity.TYPE_RELAY, true
        );
        firstRelay.setPoweredOn(false);

        DeviceEntity secondRelay = new DeviceEntity(
                10, "Реле 10", kitchenId,
                "ESP32-01", DeviceEntity.TYPE_RELAY, true
        );
        secondRelay.setPoweredOn(true);

        DeviceEntity otherRelay = new DeviceEntity(
                30, "Реле 30", bedroomId,
                "ESP32-02", DeviceEntity.TYPE_RELAY, true
        );
        otherRelay.setPoweredOn(false);

        deviceDao.insert(firstRelay);
        deviceDao.insert(secondRelay);
        deviceDao.insert(otherRelay);

        List<DeviceEntity> kitchenDevices =
                deviceDao.getByRoomId(kitchenId);

        assertEquals(2, kitchenDevices.size());
        assertEquals(10, kitchenDevices.get(0).getId());
        assertEquals(20, kitchenDevices.get(1).getId());
        assertEquals(kitchenId, kitchenDevices.get(0).getRoomId());
        assertEquals(kitchenId, kitchenDevices.get(1).getRoomId());

        assertEquals(1, deviceDao.getByRoomId(bedroomId).size());
        assertTrue(deviceDao.getByRoomId(999).isEmpty());

        List<DeviceEntity> allDevices = deviceDao.getAll();

        assertEquals(3, allDevices.size());
        assertEquals(10, allDevices.get(0).getId());
        assertEquals(20, allDevices.get(1).getId());
        assertEquals(30, allDevices.get(2).getId());
    }

    @Test
    public void deleteDevice_keepsRoomAndOtherDevice()
    {
        int roomId = insertRoom("Кухня");

        int firstId = checkedId(
                deviceDao.insert(createRelay(roomId, "Первое реле"))
        );

        int secondId = checkedId(
                deviceDao.insert(createRelay(roomId, "Второе реле"))
        );

        int deletedCount = deviceDao.deleteById(firstId);

        assertEquals(1, deletedCount);
        assertNull(deviceDao.getById(firstId));
        assertNotNull(deviceDao.getById(secondId));
        assertNotNull(roomDao.getById(roomId));
        assertEquals(1, deviceDao.getByRoomId(roomId).size());
    }

    @Test
    public void deleteRoom_deletesItsDevicesAndKeepsOtherRoom()
    {
        int kitchenId = insertRoom("Кухня");
        int bedroomId = insertRoom("Спальня");

        int firstKitchenDeviceId = checkedId(
                deviceDao.insert(createRelay(kitchenId, "Чайник"))
        );

        int secondKitchenDeviceId = checkedId(
                deviceDao.insert(createRelay(kitchenId, "Подсветка"))
        );

        int bedroomDeviceId = checkedId(
                deviceDao.insert(createRelay(bedroomId, "Ночник"))
        );

        int deletedCount = roomDao.deleteById(kitchenId);

        // Считаются удалённые комнаты, а не устройства.
        assertEquals(1, deletedCount);

        assertNull(roomDao.getById(kitchenId));
        assertNull(deviceDao.getById(firstKitchenDeviceId));
        assertNull(deviceDao.getById(secondKitchenDeviceId));
        assertTrue(deviceDao.getByRoomId(kitchenId).isEmpty());

        assertNotNull(roomDao.getById(bedroomId));
        assertNotNull(deviceDao.getById(bedroomDeviceId));
        assertEquals(1, roomDao.getAll().size());
        assertEquals(1, deviceDao.getAll().size());
    }

    @Test
    public void insertDevice_missingRoom_rejectsInsert()
    {
        DeviceEntity device = createRelay(999, "Реле");

        try
        {
            deviceDao.insert(device);
            fail("Ожидалась ошибка: указанной комнаты нет в базе.");
        }
        catch (SQLiteConstraintException expected)
        {
            // Проверяем работу внешнего ключа.
        }

        assertTrue(deviceDao.getAll().isEmpty());
    }

    @Test
    public void missingRecords_returnNullAndZeroWithoutCreatingRows()
    {
        int roomId = insertRoom("Кухня");

        assertNull(roomDao.getById(999));
        assertNull(deviceDao.getById(999));

        RoomEntity missingRoom = new RoomEntity(999, "Нет такой комнаты");

        DeviceEntity missingDevice = new DeviceEntity(
                999,
                "Нет такого устройства",
                roomId,
                "ESP32-01",
                DeviceEntity.TYPE_RELAY,
                false
        );
        missingDevice.setPoweredOn(false);

        assertEquals(0, roomDao.update(missingRoom));
        assertEquals(0, deviceDao.update(missingDevice));

        assertEquals(0, roomDao.deleteById(999));
        assertEquals(0, deviceDao.deleteById(999));

        assertEquals(1, roomDao.getAll().size());
        assertTrue(deviceDao.getAll().isEmpty());
    }

    private int insertRoom(String name)
    {
        RoomEntity room = new RoomEntity(0, name);
        long savedId = roomDao.insert(room);

        return checkedId(savedId);
    }

    private DeviceEntity createRelay(int roomId, String name)
    {
        DeviceEntity relay = new DeviceEntity(
                0,
                name,
                roomId,
                "ESP32-TEST",
                DeviceEntity.TYPE_RELAY,
                true
        );

        relay.setPoweredOn(false);

        return relay;
    }

    private int checkedId(long id)
    {
        assertTrue("Идентификатор должен быть положительным.", id > 0);
        assertTrue("Идентификатор должен помещаться в int.", id <= Integer.MAX_VALUE);

        return (int) id;
    }
}