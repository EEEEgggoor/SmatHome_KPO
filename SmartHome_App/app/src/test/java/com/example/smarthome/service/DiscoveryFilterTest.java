package com.example.smarthome.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.smarthome.integration.ControllerGateway;
import com.example.smarthome.model.Device;
import com.example.smarthome.model.RelayDevice;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class DiscoveryFilterTest {
    @Test public void scanExcludesSavedControllersAndDuplicateAnnouncements() {
        List<Device> saved = Arrays.asList(
                new RelayDevice(1, "Existing", 1, "paired-id", true, false));
        List<ControllerGateway.DiscoveredDevice> found = Arrays.asList(
                new ControllerGateway.DiscoveredDevice("paired-id", 1, "192.168.1.2"),
                new ControllerGateway.DiscoveredDevice("new-id", 2, "192.168.1.3"),
                new ControllerGateway.DiscoveredDevice("new-id", 2, "192.168.1.3"));

        List<ControllerGateway.DiscoveredDevice> result = DiscoveryFilter.unpaired(found, saved);

        assertEquals(1, result.size());
        assertEquals("new-id", result.get(0).controllerId);
        assertTrue(DiscoveryFilter.isPaired("paired-id", saved));
        assertFalse(DiscoveryFilter.isPaired("new-id", saved));
    }
}
