package com.example.smarthome;

import android.app.Application;

import com.example.smarthome.data.HomeRepository;
import com.example.smarthome.data.database.SmartHomeDatabase;
import com.example.smarthome.integration.Esp32ControllerGateway;
import com.example.smarthome.service.DeviceControlService;

/** One repository and controller service for the app process. */
public final class SmartHomeApplication extends Application {
    private HomeRepository repository;
    private DeviceControlService controlService;

    @Override public void onCreate() {
        super.onCreate();
        repository = new HomeRepository(SmartHomeDatabase.getInstance(this));
        controlService = new DeviceControlService(repository, new Esp32ControllerGateway(this));
    }

    public HomeRepository repository() { return repository; }
    public DeviceControlService controlService() { return controlService; }
}
