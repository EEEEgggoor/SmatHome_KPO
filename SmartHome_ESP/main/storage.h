#ifndef STORAGE_H
#define STORAGE_H

#include "esp_err.h"
#include "device.h"

esp_err_t stor_save(const Device *device);
esp_err_t stor_load(Device *out);

#endif