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
        version = 1,
        exportSchema = true
)
public abstract class SmartHomeDatabase extends  RoomDatabase{
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
            instance = builder.build();
        }
        return instance;
    }
}
