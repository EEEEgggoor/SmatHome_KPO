#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "storage.h"
#include "nvs.h"

static Device record;
static size_t record_size;
static uint8_t threshold;
static esp_err_t open_error, get_error, set_error, limit_error, commit_error;
static int closes, commits, opens;
static void reset(void)
{
    record = (Device){.type = DEVICE_TYPE_WATER, .name = "Water", .controller_id = "water-id", .online = true};
    record_size = sizeof(record);
    threshold = 65;
    open_error = get_error = set_error = limit_error = commit_error = ESP_OK;
    closes = commits = opens = 0;
}
esp_err_t nvs_open(const char *name, int mode, nvs_handle_t *handle)
{
    assert(strcmp(name, "device") == 0);
    assert(mode == NVS_READONLY || mode == NVS_READWRITE);
    opens++;
    *handle = 1;
    return open_error;
}
esp_err_t nvs_get_blob(nvs_handle_t h, const char *key, void *out, size_t *size)
{
    assert(h == 1);
    if (strcmp(key, "water_limit") == 0)
    {
        if (limit_error)
            return limit_error;
        assert(out && *size == 1);
        *(uint8_t *)out = threshold;
        *size = 1;
        return ESP_OK;
    }
    assert(strcmp(key, "config") == 0);
    if (get_error)
        return get_error;
    if (out)
    {
        assert(*size == sizeof(record));
        memcpy(out, &record, sizeof(record));
    }
    *size = record_size;
    return ESP_OK;
}
esp_err_t nvs_set_blob(nvs_handle_t h, const char *key, const void *value, size_t size)
{
    assert(h == 1 && value);
    if (strcmp(key, "water_limit") == 0)
    {
        assert(size == 1);
        return limit_error;
    }
    assert(strcmp(key, "config") == 0 && size == sizeof(Device));
    return set_error;
}
esp_err_t nvs_commit(nvs_handle_t h)
{
    assert(h == 1);
    commits++;
    return commit_error;
}
void nvs_close(nvs_handle_t h)
{
    assert(h == 1);
    closes++;
}
static void assert_zero(const Device *d)
{
    Device zero = {0};
    assert(memcmp(d, &zero, sizeof(zero)) == 0);
}
static void invalid_input_never_opens_nvs(void)
{
    reset();
    assert(stor_save(NULL) == ESP_ERR_INVALID_ARG);
    assert(stor_load(NULL) == ESP_ERR_INVALID_ARG);
    Device invalid = record;
    invalid.type = (DeviceType)99;
    assert(stor_save(&invalid) == ESP_ERR_INVALID_ARG);
    invalid = record;
    memset(invalid.name, 'X', sizeof(invalid.name));
    assert(stor_save(&invalid) == ESP_ERR_INVALID_ARG);
    invalid = record;
    memset(invalid.controller_id, 'X', sizeof(invalid.controller_id));
    assert(stor_save(&invalid) == ESP_ERR_INVALID_ARG);
    assert(opens == 0);
}
static void open_failure_does_not_close_unopened_handle(void)
{
    reset();
    open_error = ESP_FAIL;
    Device out;
    memset(&out, 0xa5, sizeof(out));
    assert(stor_load(&out) == ESP_FAIL);
    assert_zero(&out);
    assert(stor_save(&record) == ESP_FAIL);
    assert(closes == 0 && commits == 0);
}
static void missing_or_wrong_sized_record_leaves_output_empty(void)
{
    reset();
    Device out;
    get_error = ESP_ERR_NVS_NOT_FOUND;
    assert(stor_load(&out) == ESP_ERR_NVS_NOT_FOUND);
    assert_zero(&out);
    assert(closes == 1);
    get_error = ESP_OK;
    record_size = 79;
    assert(stor_load(&out) == ESP_ERR_INVALID_SIZE);
    assert_zero(&out);
    assert(closes == 2);
    record_size = 81;
    assert(stor_load(&out) == ESP_ERR_INVALID_SIZE);
    assert_zero(&out);
}
static void corrupt_record_is_not_published(void)
{
    reset();
    Device out;
    record.name[0] = '\0';
    assert(stor_load(&out) == ESP_ERR_INVALID_RESPONSE);
    assert_zero(&out);
    assert(closes == 1);
}
static void fallback_threshold_does_not_swallow_storage_io_errors(void)
{
    reset();
    Device out;
    limit_error = ESP_ERR_NVS_NOT_FOUND;
    assert(stor_load(&out) == ESP_OK && out.data.water.close_threshold == 80);
    limit_error = ESP_ERR_NVS_INVALID_LENGTH;
    assert(stor_load(&out) == ESP_OK && out.data.water.close_threshold == 80);
    limit_error = ESP_OK;
    threshold = 0;
    assert(stor_load(&out) == ESP_OK && out.data.water.close_threshold == 80);
    threshold = 101;
    assert(stor_load(&out) == ESP_OK && out.data.water.close_threshold == 80);
    limit_error = ESP_FAIL;
    assert(stor_load(&out) == ESP_FAIL);
    assert_zero(&out);
}
static void write_and_commit_errors_propagate_and_close_handle(void)
{
    reset();
    set_error = ESP_FAIL;
    assert(stor_save(&record) == ESP_FAIL && commits == 0 && closes == 1);
    set_error = ESP_OK;
    limit_error = ESP_FAIL;
    assert(stor_save(&record) == ESP_FAIL && commits == 0 && closes == 2);
    limit_error = ESP_OK;
    commit_error = ESP_FAIL;
    assert(stor_save(&record) == ESP_FAIL && commits == 1 && closes == 3);
}
static void load_preserves_state_but_marks_device_offline(void)
{
    reset();
    record.data.water.water_level = 42;
    record.data.water.power_on = true;
    Device out;
    assert(stor_load(&out) == ESP_OK && !out.online);
    assert(out.data.water.water_level == 42 && out.data.water.power_on && out.data.water.close_threshold == 65);
    record.type = DEVICE_TYPE_RELAY;
    record.data.relay.power_on = true;
    limit_error = ESP_FAIL;
    assert(stor_load(&out) == ESP_OK && out.data.relay.power_on); // Relay must not read water keys.
}
int main(void)
{
    invalid_input_never_opens_nvs();
    open_failure_does_not_close_unopened_handle();
    missing_or_wrong_sized_record_leaves_output_empty();
    corrupt_record_is_not_published();
    fallback_threshold_does_not_swallow_storage_io_errors();
    write_and_commit_errors_propagate_and_close_handle();
    load_preserves_state_but_marks_device_offline();
    puts("Storage failures: 7 scenarios passed");
    return 0;
}
