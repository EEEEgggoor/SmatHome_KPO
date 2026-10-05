package com.example.smarthome.model;

public class LightDevice extends Device
{
    private boolean poweredOn;
    private int brightness;
    private int lightTemperature;

    public LightDevice(int id, String name, int roomId, String controllerId,boolean online, boolean poweredOn, int brightness, int lightTemperature)
    {
        super(id, name, roomId, controllerId, online);

        setBrightness(brightness);
        setLightTemperature(lightTemperature);
        this.poweredOn = poweredOn;
    }

    public int getBrightness(){
        return brightness;
    }

    public int getLightTemperature(){
        return lightTemperature;
    }

    public boolean isPoweredOn(){
        return poweredOn;
    }

    public void setBrightness(int brightness){
        if (brightness < 0 || brightness > 100){
            throw new IllegalArgumentException("Яркость должна быть от 0 до 100.");
        }
        this.brightness = brightness;
    }

    public void setLightTemperature(int lightTemperature){
        if (lightTemperature < 0 || lightTemperature > 100){
            throw new IllegalArgumentException("Температура света должна быть от 0 до 100.");
        }
        this.lightTemperature = lightTemperature;
    }

    public void setPoweredOn(boolean poweredOn){
        this.poweredOn = poweredOn;
    }
}
