package com.example.smarthome.data.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface DeviceDao {
    @NonNull
    @Query("SELECT * FROM devices ORDER BY id ASC")
    List<DeviceEntity> getAll();


    @Nullable
    @Query("SELECT * FROM devices WHERE id = :deviceId")
    DeviceEntity getById(int deviceId);

    @NonNull
    @Query("SELECT * FROM devices WHERE roomId = :roomId ORDER BY id ASC")
    List<DeviceEntity> getByRoomId(int roomId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(@NonNull DeviceEntity device);

    @Update(onConflict = OnConflictStrategy.ABORT)
    int update(@NonNull DeviceEntity device);

    @Query("DELETE FROM devices WHERE id = :deviceId")
    int deleteById(int deviceId);
}
