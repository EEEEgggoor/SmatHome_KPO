package com.example.smarthome.ui;

import com.example.smarthome.model.Device;
import com.example.smarthome.model.LightDevice;
import com.example.smarthome.model.RelayDevice;
import com.example.smarthome.model.Room;
import com.example.smarthome.model.WaterDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * ВРЕМЕННЫЕ демонстрационные данные для экрана.
 * Это не реальные показания и не подключение к оборудованию.
 * Когда появятся HomeRepository и DemoDataFactory, этот класс нужно удалить.
 */
public class DemoHomeData
{
    public static List<Room> createRooms()
    {
        List<Room> rooms = new ArrayList<Room>();
        rooms.add(new Room(1, "Гостиная"));
        rooms.add(new Room(2, "Кухня"));
        rooms.add(new Room(3, "Ванная"));
        return rooms;
    }

    public static List<Device> createDevices()
    {
        List<Device> devices = new ArrayList<Device>();

        LightDevice light = new LightDevice(
                1, "Светодиодная лента", 1, "ESP32 #01",
                true, true, 72, 50);
        devices.add(light);

        RelayDevice relay = new RelayDevice(
                2, "Реле чайника", 2, "ESP32 #02",
                true, false);
        devices.add(relay);

        WaterDevice water = new WaterDevice(
                3, "Датчик воды", 3, "ESP32 #03", true);
        water.updateReading(64, 22.4, "Норма", System.currentTimeMillis());
        devices.add(water);

        return devices;
    }
}
