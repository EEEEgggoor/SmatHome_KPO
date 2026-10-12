package com.example.smarthome.data.database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "rooms")
public class RoomEntity {
    @PrimaryKey(autoGenerate = true)
    private final int id;

    @NonNull
    private String name;

    public RoomEntity(int id, @NonNull String name) {
        if (id < 0) {
            throw new IllegalArgumentException("Идентификатор записи комнаты не должен быть отрицательным.");
        }

        this.id = id;
        setName(name);
    }

    public int getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        if (name == null){
            throw new IllegalArgumentException("Название комнаты не должно быть null.");
        }

        String trimmedName = name.trim();

        if (trimmedName.isEmpty())
        {
            throw new IllegalArgumentException(
                    "Название комнаты не должно быть пустым."
            );
        }

        if (trimmedName.length() > 24)
        {
            throw new IllegalArgumentException(
                    "Название комнаты не должно превышать 24 символа."
            );
        }

        this.name = trimmedName;
    }
}