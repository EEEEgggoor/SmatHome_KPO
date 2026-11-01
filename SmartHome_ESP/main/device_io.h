#pragma once
#include "device.h"
#include "esp_err.h"

esp_err_t device_io_init(Device *device);
void device_io_lock(void);
void device_io_unlock(void);
esp_err_t device_io_refresh(Device *device); // Caller holds lock.
esp_err_t device_io_light(const LightData *state);
esp_err_t device_io_relay(bool on);
esp_err_t device_io_valve(bool open);
