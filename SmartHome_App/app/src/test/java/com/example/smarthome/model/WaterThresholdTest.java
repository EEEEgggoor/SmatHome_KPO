package com.example.smarthome.model;
import org.junit.Test;
import static org.junit.Assert.*;
public class WaterThresholdTest {
    @Test public void thresholdUnknownUntilConfirmedAndValidated() {
        WaterDevice water = new WaterDevice(1, "Water", 1, "id", true);
        assertNull(water.getCloseThreshold());
        water.setCloseThreshold(1); assertEquals(Integer.valueOf(1), water.getCloseThreshold());
        water.setCloseThreshold(100); assertEquals(Integer.valueOf(100), water.getCloseThreshold());
        water.updateLevelOnly(65); water.clearReading();
        assertNull(water.getWaterLevel());
        assertEquals(Integer.valueOf(100), water.getCloseThreshold());
    }
    @Test(expected=IllegalArgumentException.class) public void thresholdZeroRejected() {
        new WaterDevice(1, "Water", 1, "id", true).setCloseThreshold(0);
    }
    @Test(expected=IllegalArgumentException.class) public void thresholdAbove100Rejected() {
        new WaterDevice(1, "Water", 1, "id", true).setCloseThreshold(101);
    }
}
