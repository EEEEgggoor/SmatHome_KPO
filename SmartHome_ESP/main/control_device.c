#include "control_device.h"

#include <string.h>
#include <stdbool.h>
#include <stdint.h>
#include <errno.h>
#include <stddef.h>
#include <sys/time.h>

#include "device_io.h"
#include "water_policy.h"
#include "storage.h"
#include <stddef.h>
_Static_assert(sizeof(Device) == 80, "Android snapshot size changed");
_Static_assert(offsetof(Device, data.water.close_threshold) == 79, "Water threshold offset changed");

static esp_err_t process_locked(
    const uint8_t *request,
    size_t request_length,
    Device *current_device,
    int client,
    send_fn_t send_response)
{
    (void)client;
    if (request == NULL ||
        current_device == NULL ||
        send_response == NULL ||
        request_length == 0)
    {
        return ESP_ERR_INVALID_ARG;
    }

    const uint8_t command = request[0];
    size_t expected_length;

    switch (command)
    {
    case CMD_READ_DEVICE:
        expected_length = 1;
        break;

    case CMD_WATER:
        expected_length = 33;
        break;

    case CMD_LIGHT:
        expected_length = 35;
        break;

    case CMD_VALVE:
    case CMD_WATER_THRESHOLD:
    case CMD_RELAY:
        expected_length = 34;
        break;

    default:
        return ESP_ERR_NOT_SUPPORTED;
    }

    if (request_length != expected_length)
        return ESP_ERR_INVALID_SIZE;

    if (command != 0x01 && memcmp(request + 1, current_device->controller_id, 32) != 0)
    {
        return ESP_ERR_INVALID_ARG;
    }

    switch (command)
    {
    case CMD_READ_DEVICE:
        break;

    case CMD_WATER:
        if (current_device->type != DEVICE_TYPE_WATER)
            return ESP_ERR_INVALID_STATE;

        break;

    case CMD_LIGHT:
    {
        if (current_device->type != DEVICE_TYPE_LIGHT)
            return ESP_ERR_INVALID_STATE;

        const uint8_t powered = request[33];
        const uint8_t brightness = request[34];

        if (powered > 1 || brightness > 100)
            return ESP_ERR_INVALID_ARG;

        LightData next = {
            .power_on = (powered == 1),
            .bright = brightness};

        esp_err_t err = device_io_light(&next);

        if (err != ESP_OK)
            return err;

        current_device->data.light = next;
        break;
    }

    case CMD_RELAY:
        if (current_device->type != DEVICE_TYPE_RELAY)
            return ESP_ERR_INVALID_STATE;
        if (request[33] > 1)
            return ESP_ERR_INVALID_ARG;
        {
            esp_err_t err = device_io_relay(request[33] != 0);
            if (err != ESP_OK)
                return err;
            current_device->data.relay.power_on = request[33] != 0;
        }
        break;

    case CMD_VALVE:
        if (current_device->type != DEVICE_TYPE_WATER)
            return ESP_ERR_INVALID_STATE;
        if (request[33] > 1)
            return ESP_ERR_INVALID_ARG;
        if (request[33] && !water_can_open(current_device->data.water.water_level,
                                           current_device->data.water.close_threshold))
            return ESP_ERR_INVALID_STATE;
        {
            esp_err_t err = device_io_valve(request[33] != 0);
            if (err != ESP_OK)
                return err;
            current_device->data.water.power_on = request[33] != 0;
        }
        break;

    case CMD_WATER_THRESHOLD:
        if (current_device->type != DEVICE_TYPE_WATER)
            return ESP_ERR_INVALID_STATE;
        if (request[33] < 1 || request[33] > 100)
            return ESP_ERR_INVALID_ARG;
        {
            Device next = *current_device;
            next.data.water.close_threshold = request[33];
            if (water_should_close(next.data.water.water_level, request[33]))
            {
                esp_err_t err = device_io_valve(false);
                if (err != ESP_OK)
                    return err;
                next.data.water.power_on = false;
                current_device->data.water.power_on = false;
            }
            esp_err_t err = stor_save(&next);
            if (err != ESP_OK)
                return err;
            *current_device = next;
        }
        break;

    default:
        return ESP_ERR_NOT_SUPPORTED;
    }

    return ESP_OK;
}

esp_err_t command_processing(const uint8_t *request, size_t length, Device *device,
                             int client, send_fn_t send_response)
{
    if (!request || !device || !send_response || length == 0)
        return ESP_ERR_INVALID_ARG;
    Device snapshot;
    device_io_lock();
    esp_err_t refresh = device_io_refresh(device);
    // Closing is always allowed even when ADC sampling failed.
    esp_err_t err = ESP_OK;
    if (refresh != ESP_OK && request[0] == CMD_VALVE && length > 33 && request[33])
        err = refresh;
    if (err == ESP_OK)
        err = process_locked(request, length, device, client, send_response);
    snapshot = *device;
    device_io_unlock();
    if (err != ESP_OK)
        return err;
    return send_response(client, &snapshot, sizeof(snapshot)) ? ESP_OK : ESP_FAIL;
}
