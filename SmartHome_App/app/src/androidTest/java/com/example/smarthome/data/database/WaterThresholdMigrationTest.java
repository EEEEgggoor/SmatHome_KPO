package com.example.smarthome.data.database;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class WaterThresholdMigrationTest {
    @Test public void versionOneKeepsRoomsAndDevicesAndAddsNullableThreshold() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "water-migration-" + java.util.UUID.randomUUID() + ".db";
        try {
            try (SQLiteDatabase old = context.openOrCreateDatabase(name,0,null)) {
            old.execSQL("CREATE TABLE IF NOT EXISTS `rooms` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)");
            old.execSQL("CREATE TABLE IF NOT EXISTS `devices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `roomId` INTEGER NOT NULL, `controllerId` TEXT NOT NULL, `type` TEXT NOT NULL, `online` INTEGER NOT NULL, `poweredOn` INTEGER, `brightness` INTEGER, `lightTemperature` INTEGER, `waterLevel` INTEGER, `temperature` REAL, `lastUpdate` INTEGER, `levelStatus` TEXT, `valveOpen` INTEGER, FOREIGN KEY(`roomId`) REFERENCES `rooms`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            old.execSQL("CREATE INDEX IF NOT EXISTS `index_devices_roomId` ON `devices` (`roomId`)");
            old.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
            old.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '89cbe478a0bacc5e8e906ab884cc50f2')");
                old.execSQL("INSERT INTO rooms(id,name) VALUES(1,'Bathroom')");
                old.execSQL("INSERT INTO devices(id,name,roomId,controllerId,type,online,waterLevel) VALUES(1,'Water',1,'water-id','water',1,42)");
                old.setVersion(1);
            }
            SmartHomeDatabase db = Room.databaseBuilder(context,SmartHomeDatabase.class,name)
                .addMigrations(SmartHomeDatabase.MIGRATION_1_2).build();
            try {
                DeviceEntity water = db.deviceDao().getById(1);
                assertNotNull(water); assertEquals(Integer.valueOf(42),water.getWaterLevel());
                assertEquals("Bathroom",db.roomDao().getById(1).getName());
                assertNull(water.getCloseThreshold());
                water.setCloseThreshold(67); db.deviceDao().update(water);
                assertEquals(Integer.valueOf(67),db.deviceDao().getById(1).getCloseThreshold());
            } finally { db.close(); }
        } finally { context.deleteDatabase(name); }
    }
}
