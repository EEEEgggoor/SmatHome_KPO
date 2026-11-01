#include <assert.h>
#include "device_io.h"
#include "device_config.h"
#include "hardware_stubs.h"

static int task_started, adc_configured, raw_value;
static esp_err_t read_error;
static uint32_t servo_duty;
static int output_pin, output_level;
static ledc_timer_config_t timer_config;
SemaphoreHandle_t xSemaphoreCreateMutex(void) { return &task_started; }
int xSemaphoreTake(SemaphoreHandle_t m, unsigned t)
{
    (void)m;
    (void)t;
    return 1;
}
int xSemaphoreGive(SemaphoreHandle_t m)
{
    (void)m;
    return 1;
}
void vTaskDelay(unsigned t) { (void)t; }
int xTaskCreate(void (*fn)(void *), const char *name, unsigned stack, void *arg, unsigned priority, void *handle)
{
    (void)fn;
    (void)name;
    (void)stack;
    (void)arg;
    (void)priority;
    (void)handle;
    task_started++;
    return pdPASS;
}
esp_err_t ledc_timer_config(const ledc_timer_config_t *c)
{
    timer_config = *c;
    return ESP_OK;
}
esp_err_t ledc_channel_config(const ledc_channel_config_t *c)
{
    servo_duty = c->duty;
    return ESP_OK;
}
esp_err_t ledc_set_duty(int mode, int channel, uint32_t duty)
{
    (void)mode;
    (void)channel;
    servo_duty = duty;
    return ESP_OK;
}
esp_err_t ledc_update_duty(int mode, int channel)
{
    (void)mode;
    (void)channel;
    return ESP_OK;
}
esp_err_t gpio_set_level(int pin, int value)
{
    output_pin = pin;
    output_level = value;
    return ESP_OK;
}
esp_err_t gpio_set_direction(int pin, int mode)
{
    (void)pin;
    (void)mode;
    return ESP_OK;
}
esp_err_t adc_oneshot_io_to_channel(int pin, adc_unit_t *unit, adc_channel_t *channel)
{
    assert(pin == 3);
    *unit = ADC_UNIT_1;
    *channel = 2;
    return ESP_OK;
}
esp_err_t adc_oneshot_new_unit(const adc_oneshot_unit_init_cfg_t *c, adc_oneshot_unit_handle_t *handle)
{
    assert(c->unit_id == ADC_UNIT_1);
    *handle = &adc_configured;
    return ESP_OK;
}
esp_err_t adc_oneshot_config_channel(adc_oneshot_unit_handle_t handle, adc_channel_t channel, const adc_oneshot_chan_cfg_t *c)
{
    assert(handle);
    assert(channel == 2);
    // ESP-IDF esp32s2 soc_caps.h: SOC_ADC_RTC_MIN/MAX_BITWIDTH are both 13.
    // Reproduce the real driver's invalid-bitwidth check, not a permissive mock.
    if (c->bitwidth != 13 && c->bitwidth != ADC_BITWIDTH_DEFAULT)
        return ESP_ERR_INVALID_ARG;
    adc_configured = 1;
    return ESP_OK;
}
esp_err_t adc_oneshot_read(adc_oneshot_unit_handle_t handle, adc_channel_t channel, int *raw)
{
    assert(handle);
    assert(channel == 2);
    *raw = raw_value;
    return read_error;
}
int main(void)
{
    Device light = {.type = DEVICE_TYPE_LIGHT, .data.light = {.power_on = true, .bright = 100}};
    assert(device_io_init(&light) == ESP_OK);
    assert(!light.data.light.power_on && light.data.light.bright == 0);
    assert(timer_config.freq_hz == 5000 && timer_config.duty_resolution == 13);
    assert(task_started == 0 && adc_configured == 0);
    LightData light_state = {.power_on = true, .bright = 100};
    assert(device_io_light(&light_state) == ESP_OK && servo_duty == 8191);
    light_state.power_on = false;
    assert(device_io_light(&light_state) == ESP_OK && servo_duty == 0);
    Device relay = {.type = DEVICE_TYPE_RELAY, .data.relay = {.power_on = true}};
    assert(device_io_init(&relay) == ESP_OK && !relay.data.relay.power_on);
    assert(output_pin == RELAY_GPIO && output_level == !RELAY_ACTIVE_LEVEL);
    assert(device_io_relay(true) == ESP_OK && output_level == RELAY_ACTIVE_LEVEL);
    assert(device_io_relay(false) == ESP_OK && output_level == !RELAY_ACTIVE_LEVEL);
    assert(task_started == 0 && adc_configured == 0);
    Device water = {.type = DEVICE_TYPE_WATER, .data.water = {.power_on = true, .close_threshold = 80}};
    // Regression: 12-bit config returned ESP_ERR_INVALID_ARG and app_main aborted,
    // repeatedly resetting USB before Windows could enumerate CDC.
    assert(device_io_init(&water) == ESP_OK);
    assert(adc_configured && task_started == 1);
    assert(timer_config.freq_hz == 50 && timer_config.duty_resolution == 14);
    assert(!water.data.water.power_on);
    assert(servo_duty == 819); // 1000 us / 20000 us * 16383
    raw_value = WATER_ADC_WET;
    water.data.water.power_on = true;
    assert(device_io_refresh(&water) == ESP_OK);
    assert(water.data.water.water_level == 100 && !water.data.water.power_on);
    raw_value = WATER_ADC_DRY;
    assert(device_io_refresh(&water) == ESP_OK);
    assert(water.data.water.water_level == 0 && !water.data.water.power_on);
    water.data.water.power_on = true;
    read_error = ESP_FAIL;
    assert(device_io_refresh(&water) == ESP_FAIL);
    assert(water.data.water.water_level == 255 && !water.data.water.power_on);
    return 0;
}
