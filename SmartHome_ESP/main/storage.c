#include "storage.h"
#include "device_config.h"
#include <inttypes.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include "nvs.h"

#define DEVICE_NS "device"
#define DEVICE_KEY "config"

static bool valid_record(const Device *d)
{
    if (!d)
        return false;

    if (d->type < DEVICE_TYPE_LIGHT || d->type > DEVICE_TYPE_WATER ||
        d->name[0] == '\0' ||
        !memchr(d->name, '\0', sizeof(d->name)) ||
        d->controller_id[0] == '\0' ||
        !memchr(d->controller_id, '\0', sizeof(d->controller_id)))
        return false;

    return true;
}

esp_err_t stor_save(const Device *device)
{
    if (!valid_record(device))
    {
        return ESP_ERR_INVALID_ARG;
    }

    nvs_handle_t handle;

    esp_err_t err = nvs_open(
        DEVICE_NS,
        NVS_READWRITE,
        &handle);

    if (err != ESP_OK)
    {
        return err;
    }

    err = nvs_set_blob(
        handle,
        DEVICE_KEY,
        device,
        sizeof(*device));

    if (err == ESP_OK && device->type == DEVICE_TYPE_WATER)
    {
        uint8_t threshold = device->data.water.close_threshold;
        if (threshold < 1 || threshold > 100)
            threshold = WATER_DEFAULT_THRESHOLD;
        err = nvs_set_blob(handle, "water_limit", &threshold, sizeof(threshold));
    }
    if (err == ESP_OK)
        err = nvs_commit(handle);

    nvs_close(handle);

    return err;
}

esp_err_t stor_load(Device *out)
{
    if (out == NULL)
    {
        return ESP_ERR_INVALID_ARG;
    }

    memset(out, 0, sizeof(*out));

    nvs_handle_t handle;

    esp_err_t err = nvs_open(
        DEVICE_NS,
        NVS_READONLY,
        &handle);

    if (err != ESP_OK)
    {
        return err;
    }

    size_t stored_size = 0;

    err = nvs_get_blob(
        handle,
        DEVICE_KEY,
        NULL,
        &stored_size);

    if (err == ESP_OK &&
        stored_size != sizeof(Device))
    {
        err = ESP_ERR_INVALID_SIZE;
    }

    Device temp = {0};

    if (err == ESP_OK)
    {
        err = nvs_get_blob(
            handle,
            DEVICE_KEY,
            &temp,
            &stored_size);
    }

    if (err == ESP_OK && temp.type == DEVICE_TYPE_WATER)
    {
        uint8_t threshold = WATER_DEFAULT_THRESHOLD;
        size_t size = sizeof(threshold);
        esp_err_t limit_err = nvs_get_blob(handle, "water_limit", &threshold, &size);
        if (limit_err == ESP_ERR_NVS_NOT_FOUND || limit_err == ESP_ERR_NVS_INVALID_LENGTH)
            threshold = WATER_DEFAULT_THRESHOLD;
        else if (limit_err != ESP_OK)
            err = limit_err;
        if (size != 1 || threshold < 1 || threshold > 100)
            threshold = WATER_DEFAULT_THRESHOLD;
        temp.data.water.close_threshold = threshold;
    }
    nvs_close(handle);

    if (err != ESP_OK)
    {
        return err;
    }

    if (!valid_record(&temp))
    {
        return ESP_ERR_INVALID_RESPONSE;
    }

    temp.online = false;

    *out = temp;

    return ESP_OK;
}
