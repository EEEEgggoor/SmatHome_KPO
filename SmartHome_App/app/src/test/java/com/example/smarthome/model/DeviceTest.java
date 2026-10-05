package com.example.smarthome.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DeviceTest
{
    @Test
    public void constructor_validData_savesFields()
    {
        Device device = new TestDevice(
                10, "  Устройство  ", 2, "  esp32-01  ", true
        );

        assertEquals(10, device.getId());
        assertEquals("Устройство", device.getName());
        assertEquals(2, device.getRoomId());
        assertEquals("esp32-01", device.getControllerId());
        assertTrue(device.isOnline());
    }

    @Test
    public void constructor_offlineState_savesFalse()
    {
        Device device = new TestDevice(
                10, "Устройство", 2, "esp32-01", false
        );

        assertFalse(device.isOnline());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_zeroId_throwsException()
    {
        new TestDevice(0, "Устройство", 2, "esp32-01", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_negativeId_throwsException()
    {
        new TestDevice(-1, "Устройство", 2, "esp32-01", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_zeroRoomId_throwsException()
    {
        new TestDevice(10, "Устройство", 0, "esp32-01", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_negativeRoomId_throwsException()
    {
        new TestDevice(10, "Устройство", -1, "esp32-01", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullControllerId_throwsException()
    {
        new TestDevice(10, "Устройство", 2, null, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_blankControllerId_throwsException()
    {
        new TestDevice(10, "Устройство", 2, "   ", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_nullName_throwsException()
    {
        new TestDevice(10, null, 2, "esp32-01", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_blankName_throwsException()
    {
        new TestDevice(10, "   ", 2, "esp32-01", true);
    }

    @Test
    public void setName_validName_changesOnlyName()
    {
        Device device = new TestDevice(
                10, "Устройство", 2, "esp32-01", true
        );

        device.setName("  Свет на кухне  ");

        assertEquals("Свет на кухне", device.getName());
        assertEquals(10, device.getId());
        assertEquals(2, device.getRoomId());
        assertEquals("esp32-01", device.getControllerId());
        assertTrue(device.isOnline());
    }

    @Test
    public void setName_invalidName_keepsPreviousName()
    {
        Device device = new TestDevice(
                10, "Устройство", 2, "esp32-01", true
        );

        String[] invalidNames = {null, "", "   "};

        for (String invalidName : invalidNames)
        {
            try
            {
                device.setName(invalidName);
                fail("Ожидалось исключение для некорректного имени.");
            }
            catch (IllegalArgumentException exception)
            {
                assertEquals("Устройство", device.getName());
            }
        }
    }

    @Test
    public void setOnline_changesAvailability()
    {
        Device device = new TestDevice(
                10, "Устройство", 2, "esp32-01", false
        );

        device.setOnline(true);
        assertTrue(device.isOnline());

        device.setOnline(false);
        assertFalse(device.isOnline());
    }

    private static class TestDevice extends Device
    {
        public TestDevice(
                int id,
                String name,
                int roomId,
                String controllerId,
                boolean online)
        {
            super(id, name, roomId, controllerId, online);
        }
    }
}