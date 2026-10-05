package com.example.smarthome.model;

public class WaterDevice extends Device
{
    private Integer waterLevel;
    private Double temperature;
    private Long lastUpdate;
    private String levelStatus;
    private Boolean valveOpen;

    public WaterDevice(int id, String name, int roomId, String controllerId, boolean online){
        super(id, name,roomId,controllerId, online);

        this.waterLevel = null;
        this.temperature = null;
        this.lastUpdate = null;
        this.levelStatus = null;
        this.valveOpen = null;
    }

    public Integer getWaterLevel(){
        return waterLevel;
    }
    public Double getTemperature(){
        return temperature;
    }
    public Long getLastUpdate(){
        return lastUpdate;
    }
    public String getLevelStatus(){
        return levelStatus;
    }
    public Boolean getValveOpen(){
        return valveOpen;
    }
    public void setValveOpen(boolean valveOpen){
        this.valveOpen = valveOpen;
    }
    public void updateReading(int waterLevel, double temperature, String levelStatus, long lastUpdate) {
        if (waterLevel < 0 || waterLevel > 100) {
            throw new IllegalArgumentException("Уровень воды должен быть от 0 до 100.");
        }
        if (Double.isNaN(temperature) || Double.isInfinite(temperature)) {
            throw new IllegalArgumentException("Температура должна быть конечным числом.");
        }
        if (levelStatus == null) {
            throw new IllegalArgumentException("Статус уровня воды не должен быть null.");
        }
        String trimmed_levelStatus = levelStatus.trim();
        if (trimmed_levelStatus.isEmpty()){
            throw new IllegalArgumentException("Статус уровня воды не должен быть пустым.");
        }
        if(lastUpdate <= 0){
            throw new IllegalArgumentException("Время измерения должно быть больше нуля.");
        }

        this.waterLevel = waterLevel;
        this.temperature = temperature;
        this.levelStatus = trimmed_levelStatus;
        this.lastUpdate = lastUpdate;
    }
}
