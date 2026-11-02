package com.example.smarthome.data.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;


@Database(
        entities = {
                RoomEntity.class,
                DeviceEntity.class
        },
        version = 2,
        exportSchema = true
)
public abstract class SmartHomeDatabase extends  RoomDatabase{
    public static final androidx.room.migration.Migration MIGRATION_1_2 =
        new androidx.room.migration.Migration(1, 2) {
            @Override public void migrate(@NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
                db.execSQL("ALTER TABLE devices ADD COLUMN closeThreshold INTEGER");
            }
        };
    private static final String DATABASE_NAME = "smart_home.db";
    private static SmartHomeDatabase instance;
    public abstract RoomDao roomDao();
    public abstract DeviceDao deviceDao();
    public static synchronized SmartHomeDatabase getInstance(
            @NonNull Context context)
    {
        if (context == null){
            throw new IllegalArgumentException(
                    "Контекст приложения не должен быть null."
            );
        }

        if(instance == null){
            Context applicationContext = context.getApplicationContext();

            RoomDatabase.Builder<SmartHomeDatabase> builder =
                    Room.databaseBuilder(
                            applicationContext,
                            SmartHomeDatabase.class,
                            DATABASE_NAME
                    );
            instance = builder.addMigrations(MIGRATION_1_2).build();
        }
        return instance;
    }
}
