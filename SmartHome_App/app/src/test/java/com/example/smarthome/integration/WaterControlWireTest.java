package com.example.smarthome.integration;
import org.junit.Test;
import static org.junit.Assert.*;
public class WaterControlWireTest {
    @Test public void valveAndThresholdKeepControllerAddress() {
        byte[] valve = Esp32Wire.setValveRequest("water-1", true);
        assertEquals(34, valve.length); assertEquals(5, valve[0]); assertEquals(1, valve[33]);
        assertEquals('w', valve[1]); assertEquals(0, valve[32]);
        byte[] limit = Esp32Wire.setWaterThresholdRequest("water-1", 65);
        assertEquals(6, limit[0]); assertEquals(65, limit[33]);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectZeroThreshold() {
        Esp32Wire.setWaterThresholdRequest("water-1", 0);
    }
    @Test(expected=IllegalArgumentException.class) public void rejectHighThreshold() {
        Esp32Wire.setWaterThresholdRequest("water-1", 101);
    }
    @Test public void readsThresholdAndInvalidSensorWithoutInventingLevel() {
        byte[] bytes = new byte[80]; bytes[0]=2; bytes[44]='w'; bytes[76]=1;
        bytes[77]=0; bytes[78]=(byte)255; bytes[79]=80;
        Esp32Wire.Snapshot state = Esp32Wire.parseSnapshot(bytes);
        assertEquals(80, state.closeThreshold); assertEquals(255, state.value);
        assertFalse(state.poweredOn);
    }
}
