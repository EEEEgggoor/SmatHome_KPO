#include <assert.h>
#include <string.h>
#include "storage.h"
#include "nvs.h"

static Device stored;
static int has_record;

esp_err_t nvs_open(const char *name, int mode, nvs_handle_t *handle)
{
    (void)name;
    (void)mode;
    *handle = 1;
    return ESP_OK;
}

esp_err_t nvs_set_blob(nvs_handle_t handle, const char *key, const void *value, size_t length)
{
    (void)handle;
    (void)key;
    assert(length == sizeof(stored));
    memcpy(&stored, value, length);
    has_record = 1;
    return ESP_OK;
}

esp_err_t nvs_get_blob(nvs_handle_t handle, const char *key, void *out, size_t *length)
{
    (void)handle;
    (void)key;
    assert(has_record);
    if (out) {
        assert(*length == sizeof(stored));
        memcpy(out, &stored, sizeof(stored));
    }
    *length = sizeof(stored);
    return ESP_OK;
}

esp_err_t nvs_commit(nvs_handle_t handle) { (void)handle; return ESP_OK; }
void nvs_close(nvs_handle_t handle) { (void)handle; }

int main(void)
{
    Device empty = {0};
    assert(stor_save(&empty) == ESP_ERR_INVALID_ARG);

    stored = empty;
    has_record = 1;
    Device loaded = {0};
    assert(stor_load(&loaded) == ESP_ERR_INVALID_RESPONSE);

    Device valid = {
        .type = DEVICE_TYPE_WATER,
        .name = "Water Controller",
        .controller_id = "ESP32-S2-001"
    };
    assert(stor_save(&valid) == ESP_OK);
    assert(stor_load(&loaded) == ESP_OK);
    assert(strcmp(loaded.name, valid.name) == 0);
    assert(loaded.type == DEVICE_TYPE_WATER);
    return 0;
}
