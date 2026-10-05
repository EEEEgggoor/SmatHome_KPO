package com.example.smarthome.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class WaterDeviceTest
{
    @Test
    public void constructor_readingsAndValveAreUnknown()
    {
        WaterDevice water = new WaterDevice(
                1, "Бак", 2, "esp32-water", true
        );

        assertTrue(water.isOnline());
        assertNull(water.getWaterLevel());
        assertNull(water.getTemperature());
        assertNull(water.getLastUpdate());
        assertNull(water.getLevelStatus());
        assertNull(water.getValveOpen());
    }

    @Test
    public void constructor_offlineState_savesFalse()
    {
        WaterDevice water = new WaterDevice(
                1, "Бак", 2, "esp32-water", false
        );

        assertFalse(water.isOnline());
    }

    @Test
    public void updateReading_validData_savesReadingsAndTrimmedStatus()
    {
        WaterDevice water = new WaterDevice(
                1, "Бак", 2, "esp32-water", true
        );

        long measurementTime = 1700000000000L;

        water.updateReading(
                64, 22.456, "  Норма  ", measurementTime
        );

        assertEquals(Integer.valueOf(64), water.getWaterLevel());
        assertNotNull(water.getTemperature());
        assertEquals(22.456, water.getTemperature().doubleValue(), 0.000001);
        assertEquals("Норма", water.getLevelStatus());
        assertEquals(Long.valueOf(measurementTime), water.getLastUpdate());

        // Обновление показаний не придумывает положение клапана.
        assertNull(water.getValveOpen());
    }

    @Test
    public void updateReading_waterLevelBoundaries_areAccepted()
    {
        WaterDevice water = new WaterDevice(
                1, "Бак", 2, "esp32-water", true
        );

        water.updateReading(
                0, 22.4, "Пусто", 1700000000000L
        );

        assertEquals(Integer.valueOf(0), water.getWaterLevel());

        water.updateReading(
                100, 22.4, "Полный", 1700000001000L
        );

        assertEquals(Integer.valueOf(100), water.getWaterLevel());
    }

    @Test
    public void setValveOpen_changesValveWithoutChangingReadings()
    {
        WaterDevice water = new WaterDevice(
                1, "Бак", 2, "esp32-water", true
        );

        water.setValveOpen(true);
        assertEquals(Boolean.TRUE, water.getValveOpen());

        water.setValveOpen(false);
        assertEquals(Boolean.FALSE, water.getValveOpen());

        assertNull(water.getWaterLevel());
        assertNull(water.getTemperature());
        assertNull(water.getLastUpdate());
        assertNull(water.getLevelStatus());
    }

    @Test
    public void updateReading_invalidLevel_keepsPreviousReadings()
    {
        int[] invalidLevels = {-1, 101};

        for (int invalidLevel : invalidLevels)
        {
            assertReadingsRejectedAndUnchanged(
                    invalidLevel, 30.0, "Новый статус", 1700000001000L
            );
        }
    }

    @Test
    public void updateReading_nonFiniteTemperature_keepsPreviousReadings()
    {
        double[] invalidTemperatures =
                {
                        Double.NaN,
                        Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY
                };

        for (double invalidTemperature : invalidTemperatures)
        {
            assertReadingsRejectedAndUnchanged(
                    80, invalidTemperature, "Новый статус", 1700000001000L
            );
        }
    }

    @Test
    public void updateReading_invalidStatus_keepsPreviousReadings()
    {
        String[] invalidStatuses = {null, "", "   "};

        for (String invalidStatus : invalidStatuses)
        {
            assertReadingsRejectedAndUnchanged(
                    80, 30.0, invalidStatus, 1700000001000L
            );
        }
    }

    @Test
    public void updateReading_nonPositiveTime_keepsPreviousReadings()
    {
        long[] invalidTimes = {0L, -1L};

        for (long invalidTime : invalidTimes)
        {
            assertReadingsRejectedAndUnchanged(
                    80, 30.0, "Новый статус", invalidTime
            );
        }
    }

    @Test
    public void updateReading_newMeasurement_replacesPreviousReadings()
    {
        WaterDevice water = new WaterDevice(
                1, "Бак", 2, "esp32-water", true
        );

        water.updateReading(
                64, 22.4, "Норма", 1700000000000L
        );

        water.setValveOpen(true);

        water.updateReading(
                80, 25.5, "Новый статус", 1700000001000L
        );

        assertEquals(Integer.valueOf(80), water.getWaterLevel());
        assertEquals(25.5, water.getTemperature().doubleValue(), 0.000001);
        assertEquals("Новый статус", water.getLevelStatus());
        assertEquals(Long.valueOf(1700000001000L), water.getLastUpdate());
        assertEquals(Boolean.TRUE, water.getValveOpen());
    }

    @Test
    public void setOnline_connectionLost_keepsLastKnownData()
    {
        WaterDevice water = new WaterDevice(
                1, "Бак", 2, "esp32-water", true
        );

        water.updateReading(
                64, 22.4, "Норма", 1700000000000L
        );

        water.setValveOpen(true);
        water.setOnline(false);

        assertFalse(water.isOnline());
        assertEquals(Integer.valueOf(64), water.getWaterLevel());
        assertEquals(22.4, water.getTemperature().doubleValue(), 0.000001);
        assertEquals("Норма", water.getLevelStatus());
        assertEquals(Long.valueOf(1700000000000L), water.getLastUpdate());
        assertEquals(Boolean.TRUE, water.getValveOpen());
    }

    private void assertReadingsRejectedAndUnchanged(
            int waterLevel,
            double temperature,
            String levelStatus,
            long lastUpdate)
    {
        WaterDevice water = new WaterDevice(
                1, "Бак", 2, "esp32-water", true
        );

        long previousTime = 1700000000000L;

        water.updateReading(
                64, 22.4, "Норма", previousTime
        );

        water.setValveOpen(true);

        try
        {
            water.updateReading(
                    waterLevel, temperature, levelStatus, lastUpdate
            );

            fail("Некорректное измерение должно быть отклонено.");
        }
        catch (IllegalArgumentException exception)
        {
            // Ни одно прежнее показание не должно измениться.
            assertEquals(Integer.valueOf(64), water.getWaterLevel());
            assertEquals(22.4, water.getTemperature().doubleValue(), 0.000001);
            assertEquals("Норма", water.getLevelStatus());
            assertEquals(Long.valueOf(previousTime), water.getLastUpdate());
            assertEquals(Boolean.TRUE, water.getValveOpen());
        }
    }
}