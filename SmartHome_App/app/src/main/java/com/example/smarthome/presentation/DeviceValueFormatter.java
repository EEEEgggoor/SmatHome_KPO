package com.example.smarthome.presentation;
import android.content.res.Resources;
import com.example.smarthome.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
public class DeviceValueFormatter {
    private final Resources resources;
    public DeviceValueFormatter(Resources resources){
        if (resources == null){
            throw new IllegalArgumentException("Ресурсы приложения не должны быть null.");
        }
        this.resources = resources;
    }
    // Уровень воды: "n %" или "Нет данных".
    public String formatWaterLevel(Integer waterLevel){
        if (waterLevel == null){
            return resources.getString(R.string.device_value_no_data);
        }
        else{
            return resources.getString(R.string.device_water_level_percent, waterLevel);
        }
    }
    // Температура с двумя знаками после запятой.
    public String formatTemperature(Double temperature){
        if(temperature == null){
            return resources.getString(R.string.device_value_no_data);
        }
        else{
            return resources.getString(R.string.device_temperature_celsius, temperature);
        }
    }
    // Дата и время измерения в часовом поясе телефона.
    public String formatLastUpdate(Long lastUpdate)
    {
        long currentTime = System.currentTimeMillis();

        return formatLastUpdate(lastUpdate, currentTime);
    }
    // Возвращает полученный статус без вычисления порогов.
    public String formatLevelStatus(String levelStatus){
        if(levelStatus == null){
            return resources.getString(R.string.device_value_no_data);
        }
        else{
            String trimmedStatus = levelStatus.trim();
            if(trimmedStatus.isEmpty()){
                return resources.getString(R.string.device_value_no_data);
            }
            else{
                return trimmedStatus;
            }
        }
    }
    // У клапана три состояния: открыт, закрыт, неизвестно.
    public String formatValveState(Boolean valveOpen)
    {
        if (valveOpen == null)
        {
            return resources.getString(
                    R.string.device_value_no_data
            );
        }
        else
        {
            if (valveOpen)
            {
                return resources.getString(
                        R.string.device_valve_open
                );
            }
            else
            {
                return resources.getString(
                        R.string.device_valve_closed
                );
            }
        }
    }
    // Доступность устройства для связи.
    public String formatOnlineState(boolean online)
    {
        if (online)
        {
            return resources.getString(
                    R.string.device_online
            );
        }
        else
        {
            return resources.getString(
                    R.string.device_offline
            );
        }
    }

    // Сохранённое состояние света или нагрузки реле.
    public String formatPowerState(boolean poweredOn)
    {
        if (poweredOn)
        {
            return resources.getString(
                    R.string.device_power_on
            );
        }
        else
        {
            return resources.getString(
                    R.string.device_power_off
            );
        }
    }

    // Яркость света в процентах.
    public String formatBrightness(int brightness)
    {
        return resources.getString(
                R.string.device_brightness_percent,
                brightness
        );
    }

    // Относительная температура света по шкале 0–100.
    public String formatLightTemperature(int lightTemperature)
    {
        return resources.getString(
                R.string.device_light_temperature_percent,
                lightTemperature
        );
    }
    //для тестов
    // Перегрузка с заданным временем для точной проверки поведения.
    String formatLastUpdate(Long lastUpdate, long currentTime)
    {
        if (lastUpdate == null)
        {
            return resources.getString(
                    R.string.device_value_no_data
            );
        }
        else
        {
            long elapsedTime = currentTime - lastUpdate;
            long oneMinute = 60_000L;

            if (elapsedTime >= 0 && elapsedTime < oneMinute)
            {
                return resources.getString(
                        R.string.device_updated_just_now
                );
            }
            else
            {
                Locale locale = resources
                        .getConfiguration()
                        .getLocales()
                        .get(0);

                SimpleDateFormat dateFormat = new SimpleDateFormat(
                        "dd.MM.yyyy HH:mm:ss",
                        locale
                );

                TimeZone timeZone = TimeZone.getDefault();
                dateFormat.setTimeZone(timeZone);

                Date measurementDate = new Date(lastUpdate);

                return dateFormat.format(measurementDate);
            }
        }
    }
}
