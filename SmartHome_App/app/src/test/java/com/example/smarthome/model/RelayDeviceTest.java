package com.example.smarthome.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RelayDeviceTest
{
    @Test
    public void constructor_allStateCombinations_savesBothFlags()
    {
        boolean[] states = {false, true};

        for (boolean online : states)
        {
            for (boolean poweredOn : states)
            {
                RelayDevice relay = new RelayDevice(
                        1, "Реле", 2, "esp32-relay", online, poweredOn
                );

                assertEquals(online, relay.isOnline());
                assertEquals(poweredOn, relay.isPoweredOn());
            }
        }
    }

    @Test
    public void setPoweredOn_changesPowerAndKeepsAvailability()
    {
        RelayDevice relay = new RelayDevice(
                1, "Реле", 2, "esp32-relay", true, false
        );

        relay.setPoweredOn(true);

        assertTrue(relay.isPoweredOn());
        assertTrue(relay.isOnline());

        relay.setPoweredOn(false);

        assertFalse(relay.isPoweredOn());
        assertTrue(relay.isOnline());
    }

    @Test
    public void setOnline_disconnected_keepsLastPowerState()
    {
        RelayDevice relay = new RelayDevice(
                1, "Реле", 2, "esp32-relay", true, true
        );

        relay.setOnline(false);

        assertFalse(relay.isOnline());
        assertTrue(relay.isPoweredOn());

        relay.setOnline(true);

        assertTrue(relay.isOnline());
        assertTrue(relay.isPoweredOn());
    }
}