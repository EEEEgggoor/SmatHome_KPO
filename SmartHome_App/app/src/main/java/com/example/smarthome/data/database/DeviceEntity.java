package com.example.smarthome.data.database;

import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "devices",
        foreignKeys = {
                @ForeignKey(
                entity = RoomEntity.class,
                parentColumns = "id",
                childColumns = "roomId",
                onDelete = ForeignKey.CASCADE
                )
        },
        indices = {@Index(value = "roomId")}
)
public class DeviceEntity {
    public static final String TYPE_LIGHT = "light";
    public static final String TYPE_RELAY = "relay";
    public static final String TYPE_WATER = "water";

    @PrimaryKey(autoGenerate = true)
    private final int id;

    @NonNull
    private String name;

    private final int roomId;

    @NonNull
    private final String controllerId;

    @NonNull
    private final String type;

    private boolean online;

    @Nullable
    private Boolean poweredOn;

    @Nullable
    private Integer brightness;

    @Nullable
    private Integer lightTemperature;

    @Nullable
    private Integer waterLevel;

    @Nullable
    private Double temperature;

    @Nullable
    private Long lastUpdate;

    @Nullable
    private String levelStatus;

    @Nullable
    private Boolean valveOpen;

    public DeviceEntity(
            int id,
            @NonNull String name,
            int roomId,
            @NonNull String controllerId,
            @NonNull String type,
            boolean online)
    {
        if (id < 0){
            throw new IllegalArgumentException("Идентификатор записи устройства не должен быть отрицательным.");
        }

        if (roomId <= 0){
            throw new IllegalArgumentException("Идентификатор записи устройства не должен быть отрицательным.");
        }

        if (controllerId == null)
        {
            throw new IllegalArgumentException(
                    "Идентификатор контроллера не должен быть null."
            );
        }

        String trimmedControllerId = controllerId.trim();

        if (trimmedControllerId.isEmpty())
        {
            throw new IllegalArgumentException(
                    "Идентификатор контроллера не должен быть пустым."
            );
        }

        if(!TYPE_LIGHT.equals(type) && !TYPE_RELAY.equals(type) && !TYPE_WATER.equals(type)){
            throw new IllegalArgumentException("Неизвестный тип устройства.");
        }

        this.id = id;
        this.roomId = roomId;
        this.controllerId = trimmedControllerId;
        this.type = type;
        this.online = online;

        setName(name);

        // Данные конкретного устройства заполняются отдельно.
        this.poweredOn = null;
        this.brightness = null;
        this.lightTemperature = null;
        this.waterLevel = null;
        this.temperature = null;
        this.lastUpdate = null;
        this.levelStatus = null;
        this.valveOpen = null;
    }

    public int getId(){
        return id;
    }

    @NonNull
    public String getName(){
        return name;
    }

    public void setName(@NonNull String name)
    {
        if (name == null)
        {
            throw new IllegalArgumentException(
                    "Название устройства не должно быть null."
            );
        }

        String trimmedName = name.trim();

        if (trimmedName.isEmpty())
        {
            throw new IllegalArgumentException(
                    "Название устройства не должно быть пустым."
            );
        }

        this.name = trimmedName;
    }

    public int getRoomId()
    {
        return roomId;
    }

    @NonNull
    public String getControllerId()
    {
        return controllerId;
    }

    @NonNull
    public String getType()
    {
        return type;
    }

    public boolean isOnline()
    {
        return online;
    }

    public void setOnline(boolean online)
    {
        this.online = online;
    }

    @Nullable
    public Boolean getPoweredOn()
    {
        return poweredOn;
    }

    public void setPoweredOn(@Nullable Boolean poweredOn)
    {
        this.poweredOn = poweredOn;
    }

    @Nullable
    public Integer getBrightness()
    {
        return brightness;
    }

    public void setBrightness(@Nullable Integer brightness){
        checkPercentage(brightness, "Яркость");
        this.brightness = brightness;
    }

    private void checkPercentage(@Nullable Integer value, String fieldName){
        if (value != null){
            if (value < 0 || value > 100){
                throw new IllegalArgumentException(fieldName + ": значение должно быть от 0 до 100.");
            }
        }
    }

    @Nullable
    public Integer getLightTemperature(){
        return lightTemperature;
    }

    public void setLightTemperature(@Nullable Integer lightTemperature)
    {
        checkPercentage(lightTemperature, "Температура света");
        this.lightTemperature = lightTemperature;
    }

    @Nullable
    public Integer getWaterLevel()
    {
        return waterLevel;
    }

    public void setWaterLevel(@Nullable Integer waterLevel)
    {
        checkPercentage(waterLevel, "Уровень воды");
        this.waterLevel = waterLevel;
    }

    @Nullable
    public Double getTemperature()
    {
        return temperature;
    }

    public void setTemperature(@Nullable Double temperature)
    {
        if (temperature != null)
        {
            if (Double.isNaN(temperature)
                    || Double.isInfinite(temperature))
            {
                throw new IllegalArgumentException(
                        "Температура должна быть конечным числом."
                );
            }
        }

        this.temperature = temperature;
    }

    @Nullable
    public Long getLastUpdate()
    {
        return lastUpdate;
    }

    public void setLastUpdate(@Nullable Long lastUpdate)
    {
        if (lastUpdate != null)
        {
            if (lastUpdate <= 0)
            {
                throw new IllegalArgumentException(
                        "Время измерения должно быть больше нуля."
                );
            }
        }

        this.lastUpdate = lastUpdate;
    }

    @Nullable
    public String getLevelStatus()
    {
        return levelStatus;
    }

    public void setLevelStatus(@Nullable String levelStatus)
    {
        if (levelStatus == null)
        {
            this.levelStatus = null;
        }
        else
        {
            String trimmedStatus = levelStatus.trim();

            if (trimmedStatus.isEmpty())
            {
                throw new IllegalArgumentException(
                        "Статус уровня воды не должен быть пустым."
                );
            }

            this.levelStatus = trimmedStatus;
        }
    }

    @Nullable
    public Boolean getValveOpen()
    {
        return valveOpen;
    }

    public void setValveOpen(@Nullable Boolean valveOpen)
    {
        this.valveOpen = valveOpen;
    }
}
