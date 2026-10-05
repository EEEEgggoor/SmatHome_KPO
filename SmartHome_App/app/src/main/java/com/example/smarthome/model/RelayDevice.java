package com.example.smarthome.model;

public class RelayDevice extends Device
{
    private boolean poweredOn;

    public RelayDevice(int id, String name, int roomId, String controllerId, boolean online, boolean poweredOn){
        super(id, name, roomId, controllerId, online);

        this.poweredOn = poweredOn;
    }
    public boolean isPoweredOn(){
        return poweredOn;
    }

    public void setPoweredOn(boolean poweredOn){
        this.poweredOn = poweredOn;
    }
}
