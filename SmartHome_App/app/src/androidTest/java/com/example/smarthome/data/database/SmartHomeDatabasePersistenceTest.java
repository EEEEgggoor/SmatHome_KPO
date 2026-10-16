package com.example.smarthome.data.database;

import android.content.Context;

import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class SmartHomeDatabasePersistenceTest
{
    private Context context;
    private String databaseName;
    private SmartHomeDatabase database;

    @Before
    public void setUp()
    {
        context = InstrumentationRegistry
                .getInstrumentation()
                .getTargetContext();

        // Каждый запуск использует собственный тестовый файл.
        databaseName =
                "smarthome_persistence_test_" + UUID.randomUUID() + ".db";

        database = openDatabase();
    }

    @After
    public void tearDown()
    {
        try
        {
            if (database != null)
            {
                database.close();
            }
        }
        finally
        {
            // Удаляем только файл, созданный этим тестом.
            if (context != null && databaseName != null)
            {
                context.deleteDatabase(databaseName);
            }
        }
    }

    @Test
    public void closeAndReopen_keepsRoomAndDeviceData()
    {
        RoomDao roomDao = database.roomDao();
        DeviceDao deviceDao = database.deviceDao();

        // 1. Сохраняем комнату.
        RoomEntity room = new RoomEntity(0, "Ванная");
        int roomId = checkedId(roomDao.insert(room));

        // 2. Сохраняем реле.
        DeviceEntity relay = new DeviceEntity(
                0,
                "Реле",
                roomId,
                "ESP32-RELAY",
                DeviceEntity.TYPE_RELAY,
                true
        );

        relay.setPoweredOn(false);

        int relayId = checkedId(deviceDao.insert(relay));

        // 3. Сохраняем датчик воды с показаниями.
        DeviceEntity water = new DeviceEntity(
                0,
                "Датчик бака",
                roomId,
                "ESP32-WATER",
                DeviceEntity.TYPE_WATER,
                true
        );

        water.setWaterLevel(64);
        water.setTemperature(22.4);
        water.setLastUpdate(1700000060000L);
        water.setLevelStatus("Норма");

        // Положение клапана пока неизвестно.
        water.setValveOpen(null);

        int waterId = checkedId(deviceDao.insert(water));

        // 4. Сохраняем новое название комнаты.
        RoomEntity savedRoom = roomDao.getById(roomId);
        assertNotNull(savedRoom);

        savedRoom.setName("Большая ванная");

        assertEquals(1, roomDao.update(savedRoom));

        // После вставки файл базы должен существовать.
        assertTrue(context.getDatabasePath(databaseName).exists());

        // 5. Полностью закрываем экземпляр базы.
        database.close();
        database = null;

        // 6. Открываем тот же файл через новый экземпляр.
        database = openDatabase();

        // Старые DAO больше не используем.
        RoomDao reopenedRoomDao = database.roomDao();
        DeviceDao reopenedDeviceDao = database.deviceDao();

        // 7. Проверяем комнату.
        RoomEntity reopenedRoom = reopenedRoomDao.getById(roomId);

        assertNotNull(reopenedRoom);
        assertEquals(roomId, reopenedRoom.getId());
        assertEquals("Большая ванная", reopenedRoom.getName());
        assertEquals(1, reopenedRoomDao.getAll().size());

        // 8. Проверяем реле.
        DeviceEntity reopenedRelay = reopenedDeviceDao.getById(relayId);

        assertNotNull(reopenedRelay);
        assertEquals("Реле", reopenedRelay.getName());
        assertEquals(roomId, reopenedRelay.getRoomId());
        assertEquals("ESP32-RELAY", reopenedRelay.getControllerId());
        assertEquals(DeviceEntity.TYPE_RELAY, reopenedRelay.getType());
        assertTrue(reopenedRelay.isOnline());
        assertEquals(Boolean.FALSE, reopenedRelay.getPoweredOn());

        // 9. Проверяем датчик и его показания.
        DeviceEntity reopenedWater = reopenedDeviceDao.getById(waterId);

        assertNotNull(reopenedWater);
        assertEquals("Датчик бака", reopenedWater.getName());
        assertEquals(roomId, reopenedWater.getRoomId());
        assertEquals("ESP32-WATER", reopenedWater.getControllerId());
        assertEquals(DeviceEntity.TYPE_WATER, reopenedWater.getType());
        assertTrue(reopenedWater.isOnline());

        assertEquals(
                Integer.valueOf(64),
                reopenedWater.getWaterLevel()
        );

        assertNotNull(reopenedWater.getTemperature());
        assertEquals(
                22.4,
                reopenedWater.getTemperature().doubleValue(),
                0.0001
        );

        assertEquals(
                Long.valueOf(1700000060000L),
                reopenedWater.getLastUpdate()
        );

        assertEquals("Норма", reopenedWater.getLevelStatus());
        assertNull(reopenedWater.getValveOpen());

        assertEquals(2, reopenedDeviceDao.getAll().size());
        assertEquals(2, reopenedDeviceDao.getByRoomId(roomId).size());
    }

    private SmartHomeDatabase openDatabase()
    {
        RoomDatabase.Builder<SmartHomeDatabase> builder =
                Room.databaseBuilder(
                        context,
                        SmartHomeDatabase.class,
                        databaseName
                );

        return builder.build();
    }

    private int checkedId(long id)
    {
        assertTrue(
                "Идентификатор должен быть положительным.",
                id > 0
        );

        assertTrue(
                "Идентификатор должен помещаться в int.",
                id <= Integer.MAX_VALUE
        );

        return (int) id;
    }
}