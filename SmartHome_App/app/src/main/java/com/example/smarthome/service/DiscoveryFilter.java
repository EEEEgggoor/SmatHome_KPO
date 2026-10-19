package com.example.smarthome.service;

import com.example.smarthome.integration.ControllerGateway;
import com.example.smarthome.model.Device;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Matches physical controller IDs against devices already saved in this app. */
final class DiscoveryFilter {
    private DiscoveryFilter() { }

    static boolean isPaired(String controllerId, List<Device> saved) {
        for (Device device : saved) {
            if (controllerId.equals(device.getControllerId())) return true;
        }
        return false;
    }

    static List<ControllerGateway.DiscoveredDevice> unpaired(
            List<ControllerGateway.DiscoveredDevice> discovered, List<Device> saved) {
        Set<String> seen = new HashSet<>();
        List<ControllerGateway.DiscoveredDevice> result = new ArrayList<>();
        for (ControllerGateway.DiscoveredDevice device : discovered) {
            if (seen.add(device.controllerId) && !isPaired(device.controllerId, saved)) {
                result.add(device);
            }
        }
        return result;
    }
}
