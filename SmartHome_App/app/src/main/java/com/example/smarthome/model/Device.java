package com.example.smarthome.model;

public abstract class Device {
    private final int id;
    private String name;
    private final int roomId;
    private final String controllerId;
    private boolean online;

    public Device(int id, String name, int roomId, String controllerId, boolean online)
    {
        if (id <= 0)
        {
            throw new IllegalArgumentException("Идентификатор устройства должен быть больше нуля.");
        }
        if (roomId <= 0){
            throw new IllegalArgumentException("Идентификатор комнаты должен быть больше нуля.");
        }
        if (controllerId == null){
            throw new IllegalArgumentException("Идентификатор контроллера не должен быть null.");
        }

        String trimmed_controllerId = controllerId.trim();

        if (trimmed_controllerId.isEmpty()){
            throw new IllegalArgumentException("Идентификатор контроллера не должен быть пустым.");
        }
        this.id = id;
        this.roomId = roomId;
        this.controllerId = trimmed_controllerId;
        this.online= online;
        setName(name);
    }

    public int getId(){
        return id;
    }

    public int getRoomId(){
        return roomId;
    }

    public String getControllerId(){
        return controllerId;
    }

    public boolean isOnline(){
        return online;
    }

    public void setOnline(boolean online){
        this.online = online;
    }

    public String getName(){
        return name;
    }
    public void setName(String name){
        if (name == null){
            throw new IllegalArgumentException("Название устройства не должно быть null.");
        }

        String trimmed_name = name.trim();

        if (trimmed_name.isEmpty()){
            throw new IllegalArgumentException("Название устройства не должно быть пустым.");
        }

        this.name = trimmed_name;
    }
}
