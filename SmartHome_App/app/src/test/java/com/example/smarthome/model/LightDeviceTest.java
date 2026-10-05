package com.example.smarthome.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LightDeviceTest
{
    @Test
    public void constructor_validData_savesState()
    {
        LightDevice light = new LightDevice(
                1, "Лента", 2, "esp32-light", true, true, 70, 40
        );

        assertTrue(light.isOnline());
        assertTrue(light.isPoweredOn());
        assertEquals(70, light.getBrightness());
        assertEquals(40, light.getLightTemperature());
    }

    @Test
    public void constructor_offlineAndPoweredOff_savesState()
    {
        LightDevice light = new LightDevice(
                1, "Лента", 2, "esp32-light", false, false, 70, 40
        );

        assertFalse(light.isOnline());
        assertFalse(light.isPoweredOn());
    }

    @Test
    public void constructor_boundaryValues_areAllowed()
    {
        LightDevice firstLight = new LightDevice(
                1, "Лента 1", 2, "esp32-1", true, true, 0, 100
        );

        LightDevice secondLight = new LightDevice(
                2, "Лента 2", 2, "esp32-2", true, true, 100, 0
        );

        assertEquals(0, firstLight.getBrightness());
        assertEquals(100, firstLight.getLightTemperature());
        assertEquals(100, secondLight.getBrightness());
        assertEquals(0, secondLight.getLightTemperature());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_negativeBrightness_throwsException()
    {
        new LightDevice(
                1, "Лента", 2, "esp32-light", true, true, -1, 40
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_tooHighLightTemperature_throwsException()
    {
        new LightDevice(
                1, "Лента", 2, "esp32-light", true, true, 70, 101
        );
    }

    @Test
    public void setters_boundaryValues_areAllowed()
    {
        LightDevice light = new LightDevice(
                1, "Лента", 2, "esp32-light", true, true, 70, 40
        );

        light.setBrightness(0);
        light.setLightTemperature(100);

        assertEquals(0, light.getBrightness());
        assertEquals(100, light.getLightTemperature());

        light.setBrightness(100);
        light.setLightTemperature(0);

        assertEquals(100, light.getBrightness());
        assertEquals(0, light.getLightTemperature());
    }

    @Test
    public void setBrightness_invalidValue_keepsPreviousValue()
    {
        LightDevice light = new LightDevice(
                1, "Лента", 2, "esp32-light", true, true, 70, 40
        );

        int[] invalidValues = {-1, 101};

        for (int invalidValue : invalidValues)
        {
            try
            {
                light.setBrightness(invalidValue);
                fail("Ожидалось исключение для яркости вне диапазона.");
            }
            catch (IllegalArgumentException exception)
            {
                assertEquals(70, light.getBrightness());
            }
        }
    }

    @Test
    public void setLightTemperature_invalidValue_keepsPreviousValue()
    {
        LightDevice light = new LightDevice(
                1, "Лента", 2, "esp32-light", true, true, 70, 40
        );

        int[] invalidValues = {-1, 101};

        for (int invalidValue : invalidValues)
        {
            try
            {
                light.setLightTemperature(invalidValue);
                fail("Ожидалось исключение для температуры света.");
            }
            catch (IllegalArgumentException exception)
            {
                assertEquals(40, light.getLightTemperature());
            }
        }
    }

    @Test
    public void setPoweredOn_switchingPower_keepsSettings()
    {
        LightDevice light = new LightDevice(
                1, "Лента", 2, "esp32-light", true, true, 70, 40
        );

        light.setPoweredOn(false);

        assertFalse(light.isPoweredOn());
        assertTrue(light.isOnline());
        assertEquals(70, light.getBrightness());
        assertEquals(40, light.getLightTemperature());

        light.setPoweredOn(true);

        assertTrue(light.isPoweredOn());
        assertEquals(70, light.getBrightness());
        assertEquals(40, light.getLightTemperature());
    }

    @Test
    public void setOnline_disconnected_keepsLastPowerState()
    {
        LightDevice light = new LightDevice(
                1, "Лента", 2, "esp32-light", true, true, 70, 40
        );

        light.setOnline(false);

        assertFalse(light.isOnline());
        assertTrue(light.isPoweredOn());
    }

    @Test
    public void setBrightness_doesNotSwitchPower()
    {
        LightDevice light = new LightDevice(
                1, "Лента", 2, "esp32-light", true, true, 70, 40
        );

        light.setBrightness(0);
        assertTrue(light.isPoweredOn());

        light.setPoweredOn(false);
        light.setBrightness(80);

        assertFalse(light.isPoweredOn());
        assertEquals(80, light.getBrightness());
    }
}