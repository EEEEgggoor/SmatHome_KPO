package com.example.smarthome.model;

public class Room {
    private final int id;
    private String name;

    public Room(int id, String name)
    {
        if (id <= 0)
        {
            throw new IllegalArgumentException("Идентификатор комнаты должен быть больше нуля.");
        }
        this.id = id;
        setName(name);
    }
    public int getId()
    {
        return id;
    }
    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        if (name == null){
            throw new IllegalArgumentException("Название комнаты не должно быть null.");
        }
        String trimmed_name = name.trim();
        if (trimmed_name.isEmpty())
        {
            throw new IllegalArgumentException("Название комнаты не должно быть пустым.");
        }
        if (trimmed_name.length() > 24){
            throw new IllegalArgumentException("Название комнаты не должно превышать 24 символа.");
        }

        this.name = trimmed_name;
    }


}
