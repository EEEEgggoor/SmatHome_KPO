#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "control_device.h"
#include "device_io.h"
#include "storage.h"

static int locks, writes, replies, saves;
static bool send_ok;
static esp_err_t io_error, refresh_error, save_error;
static Device reply;
static LightData applied_light;
void device_io_lock(void)
{
    assert(locks == 0);
    locks++;
}
void device_io_unlock(void)
{
    assert(locks == 1);
    locks--;
}
esp_err_t device_io_refresh(Device *d)
{
    (void)d;
    assert(locks == 1);
    return refresh_error;
}
esp_err_t device_io_light(const LightData *s)
{
    writes++;
    applied_light = *s;
    return io_error;
}
esp_err_t device_io_relay(bool on)
{
    (void)on;
    writes++;
    return io_error;
}
esp_err_t device_io_valve(bool open)
{
    (void)open;
    writes++;
    return io_error;
}
esp_err_t stor_save(const Device *d)
{
    (void)d;
    saves++;
    return save_error;
}
static bool send_reply(int fd, const void *bytes, size_t length)
{
    assert(fd == 42 && locks == 0 && length == 80); // Network must never hold the hardware mutex.
    replies++;
    memcpy(&reply, bytes, length);
    return send_ok;
}
static Device reset(DeviceType type)
{
    assert(locks == 0);
    writes = replies = saves = 0;
    send_ok = true;
    io_error = refresh_error = save_error = ESP_OK;
    return (Device){.type = type, .name = "Test", .controller_id = "target", .online = true};
}
static void request_for(uint8_t bytes[35], uint8_t command, const Device *d)
{
    memset(bytes, 0, 35);
    bytes[0] = command;
    memcpy(bytes + 1, d->controller_id, 32);
}
static esp_err_t run(uint8_t *bytes, size_t size, Device *d)
{
    esp_err_t result = command_processing(bytes, size, d, 42, send_reply);
    assert(locks == 0);
    return result;
}
static void rejects_null_and_empty_requests(void)
{
    Device d = reset(DEVICE_TYPE_LIGHT);
    uint8_t request[35] = {CMD_READ_DEVICE};
    assert(run(NULL, 1, &d) == ESP_ERR_INVALID_ARG);
    assert(run(request, 1, NULL) == ESP_ERR_INVALID_ARG);
    assert(run(request, 0, &d) == ESP_ERR_INVALID_ARG);
    assert(command_processing(request, 1, &d, 42, NULL) == ESP_ERR_INVALID_ARG);
    assert(!writes && !replies);
}
static void every_command_checks_exact_length(void)
{
    const struct
    {
        uint8_t cmd;
        size_t size;
    } cases[] = {{1, 1}, {2, 33}, {3, 35}, {4, 34}, {5, 34}, {6, 34}};
    Device d = reset(DEVICE_TYPE_LIGHT);
    uint8_t bytes[36] = {0};
    for (size_t i = 0; i < sizeof(cases) / sizeof(cases[0]); ++i)
    {
        bytes[0] = cases[i].cmd;
        assert(run(bytes, cases[i].size + 1, &d) == ESP_ERR_INVALID_SIZE);
        if (cases[i].size > 1)
            assert(run(bytes, cases[i].size - 1, &d) == ESP_ERR_INVALID_SIZE);
    }
    bytes[0] = 0xff;
    assert(run(bytes, 1, &d) == ESP_ERR_NOT_SUPPORTED);
    assert(!writes && !replies);
}
static void address_and_device_type_are_checked_before_outputs(void)
{
    Device d = reset(DEVICE_TYPE_LIGHT);
    uint8_t bytes[35];
    request_for(bytes, CMD_LIGHT, &d);
    bytes[32] = 1; // Even trailing ID padding must match.
    assert(run(bytes, 35, &d) == ESP_ERR_INVALID_ARG);
    const uint8_t commands[] = {CMD_WATER, CMD_RELAY, CMD_VALVE, CMD_WATER_THRESHOLD};
    for (size_t i = 0; i < sizeof(commands); ++i)
    {
        request_for(bytes, commands[i], &d);
        bytes[33] = 50;
        assert(run(bytes, commands[i] == CMD_WATER ? 33 : 34, &d) == ESP_ERR_INVALID_STATE);
    }
    assert(!writes && !saves && !replies);
}
static void light_validation_and_failure_preserve_confirmed_state(void)
{
    Device d = reset(DEVICE_TYPE_LIGHT);
    uint8_t bytes[35];
    request_for(bytes, CMD_LIGHT, &d);
    bytes[33] = 2;
    assert(run(bytes, 35, &d) == ESP_ERR_INVALID_ARG);
    bytes[33] = 1;
    bytes[34] = 101;
    assert(run(bytes, 35, &d) == ESP_ERR_INVALID_ARG);
    assert(!writes);
    bytes[34] = 100;
    assert(run(bytes, 35, &d) == ESP_OK);
    assert(d.data.light.power_on && d.data.light.bright == 100 && applied_light.bright == 100);
    bytes[33] = 0;
    bytes[34] = 37;
    io_error = ESP_FAIL;
    assert(run(bytes, 35, &d) == ESP_FAIL);
    assert(d.data.light.power_on && d.data.light.bright == 100 && replies == 1);
    io_error = ESP_OK;
    assert(run(bytes, 35, &d) == ESP_OK);
    assert(!reply.data.light.power_on && reply.data.light.bright == 37);
}
static void send_failure_does_not_undo_applied_command(void)
{
    Device d = reset(DEVICE_TYPE_RELAY);
    uint8_t bytes[35];
    request_for(bytes, CMD_RELAY, &d);
    bytes[33] = 1;
    send_ok = false;
    assert(run(bytes, 34, &d) == ESP_FAIL);
    assert(d.data.relay.power_on && writes == 1 && replies == 1);
}
static void adc_failure_blocks_open_but_allows_manual_close(void)
{
    Device d = reset(DEVICE_TYPE_WATER);
    d.data.water = (WaterData){.power_on = true, .water_level = 10, .close_threshold = 80};
    uint8_t bytes[35];
    request_for(bytes, CMD_VALVE, &d);
    bytes[33] = 1;
    refresh_error = ESP_FAIL;
    assert(run(bytes, 34, &d) == ESP_FAIL && writes == 0);
    bytes[33] = 0;
    assert(run(bytes, 34, &d) == ESP_OK);
    assert(!d.data.water.power_on && writes == 1);
}
static void failed_threshold_save_keeps_old_limit_and_safe_closure(void)
{
    Device d = reset(DEVICE_TYPE_WATER);
    d.data.water = (WaterData){.power_on = true, .water_level = 70, .close_threshold = 80};
    uint8_t bytes[35];
    request_for(bytes, CMD_WATER_THRESHOLD, &d);
    bytes[33] = 60;
    save_error = ESP_FAIL;
    assert(run(bytes, 34, &d) == ESP_FAIL);
    assert(!d.data.water.power_on && d.data.water.close_threshold == 80 && !replies && saves == 1);
    d.data.water.power_on = true;
    save_error = ESP_OK;
    io_error = ESP_FAIL;
    assert(run(bytes, 34, &d) == ESP_FAIL && saves == 1 && d.data.water.close_threshold == 80);
}
static void threshold_boundaries_and_read_snapshot(void)
{
    Device d = reset(DEVICE_TYPE_WATER);
    d.data.water = (WaterData){.water_level = 0, .close_threshold = 80};
    uint8_t bytes[35];
    request_for(bytes, CMD_WATER_THRESHOLD, &d);
    bytes[33] = 101;
    assert(run(bytes, 34, &d) == ESP_ERR_INVALID_ARG);
    bytes[33] = 1;
    assert(run(bytes, 34, &d) == ESP_OK && reply.data.water.close_threshold == 1);
    bytes[33] = 100;
    assert(run(bytes, 34, &d) == ESP_OK && reply.data.water.close_threshold == 100);
    bytes[0] = CMD_READ_DEVICE;
    assert(run(bytes, 1, &d) == ESP_OK);
    assert(memcmp(reply.controller_id, "target", 6) == 0 && reply.type == DEVICE_TYPE_WATER);
}
int main(void)
{
    rejects_null_and_empty_requests();
    every_command_checks_exact_length();
    address_and_device_type_are_checked_before_outputs();
    light_validation_and_failure_preserve_confirmed_state();
    send_failure_does_not_undo_applied_command();
    adc_failure_blocks_open_but_allows_manual_close();
    failed_threshold_save_keeps_old_limit_and_safe_closure();
    threshold_boundaries_and_read_snapshot();
    puts("Command validation: 8 scenarios passed");
    return 0;
}
