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
public interface RoomDao {
    @NonNull
    @Query("SELECT * FROM rooms ORDER BY id ASC")
    List<RoomEntity> getAll();

    @Nullable
    @Query("SELECT * FROM rooms WHERE id = :roomId")
    RoomEntity getById(int roomId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(@NonNull RoomEntity room);

    @Update(onConflict = OnConflictStrategy.ABORT)
    int update(@NonNull RoomEntity room);

    @Query("DELETE FROM rooms WHERE id = :roomId")
    int deleteById(int roomId);
}
