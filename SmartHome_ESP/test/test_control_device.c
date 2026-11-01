#include <assert.h>
#include <string.h>
#include "control_device.h"
#include "device_io.h"
#include "storage.h"

static bool relay_on, valve_open;
static int saves, replies, writes;
static esp_err_t output_error, save_error;
static Device response;
void device_io_lock(void) {}
void device_io_unlock(void) {}
esp_err_t device_io_refresh(Device *device)
{
    (void)device;
    return ESP_OK;
}
esp_err_t device_io_light(const LightData *state)
{
    (void)state;
    return output_error;
}
esp_err_t device_io_relay(bool on)
{
    writes++;
    if (!output_error)
        relay_on = on;
    return output_error;
}
esp_err_t device_io_valve(bool open)
{
    writes++;
    if (!output_error)
        valve_open = open;
    return output_error;
}
esp_err_t stor_save(const Device *device)
{
    (void)device;
    saves++;
    return save_error;
}
static bool send_reply(int fd, const void *data, size_t len)
{
    (void)fd;
    assert(len == 80);
    memcpy(&response, data, len);
    replies++;
    return true;
}
int main(void)
{
    Device d = {.type = DEVICE_TYPE_RELAY, .controller_id = "test"};
    uint8_t request[35] = {CMD_RELAY};
    memcpy(request + 1, d.controller_id, 32);
    request[33] = 1;
    assert(command_processing(request, 34, &d, 0, send_reply) == ESP_OK);
    assert(relay_on && d.data.relay.power_on && response.data.relay.power_on);
    request[33] = 2;
    assert(command_processing(request, 34, &d, 0, send_reply) == ESP_ERR_INVALID_ARG);
    assert(writes == 1 && replies == 1);
    request[33] = 0;
    request[1] = 'x';
    assert(command_processing(request, 34, &d, 0, send_reply) == ESP_ERR_INVALID_ARG);
    assert(writes == 1);
    request[1] = 't';
    output_error = ESP_FAIL;
    assert(command_processing(request, 34, &d, 0, send_reply) == ESP_FAIL);
    assert(d.data.relay.power_on);
    output_error = ESP_OK;
    d.type = DEVICE_TYPE_WATER;
    d.data.water = (WaterData){.water_level = 80, .close_threshold = 80};
    request[0] = CMD_VALVE;
    request[33] = 1;
    assert(command_processing(request, 34, &d, 0, send_reply) == ESP_ERR_INVALID_STATE);
    d.data.water.water_level = 75;
    assert(command_processing(request, 34, &d, 0, send_reply) == ESP_OK);
    assert(valve_open && d.data.water.power_on);
    request[0] = CMD_WATER_THRESHOLD;
    request[33] = 60;
    assert(command_processing(request, 34, &d, 0, send_reply) == ESP_OK);
    assert(!valve_open && !d.data.water.power_on && saves == 1);
    assert(d.data.water.close_threshold == 60);
    request[33] = 0;
    assert(command_processing(request, 34, &d, 0, send_reply) == ESP_ERR_INVALID_ARG);
    assert(saves == 1);
    request[33] = 90;
    save_error = ESP_FAIL;
    assert(command_processing(request, 34, &d, 0, send_reply) == ESP_FAIL);
    assert(d.data.water.close_threshold == 60);
    request[0] = CMD_VALVE;
    request[33] = 0;
    d.data.water.water_level = 255;
    assert(command_processing(request, 34, &d, 0, send_reply) == ESP_OK);
    request[0] = CMD_WATER;
    assert(command_processing(request, 33, &d, 0, send_reply) == ESP_OK);
    assert(response.data.water.water_level == 255);
    assert(command_processing(request, 32, &d, 0, send_reply) == ESP_ERR_INVALID_SIZE);
    return 0;
}
