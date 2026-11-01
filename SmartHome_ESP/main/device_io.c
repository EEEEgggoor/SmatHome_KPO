#include "device_io.h"
#include "device_config.h"
#include "control_device.h"
#include "water_policy.h"
#include "driver/gpio.h"
#include "driver/ledc.h"
#include "esp_adc/adc_oneshot.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "freertos/task.h"

static SemaphoreHandle_t mutex;
static adc_oneshot_unit_handle_t adc;
static adc_channel_t water_channel;
static Device *active_device;

void device_io_lock(void) { xSemaphoreTake(mutex, portMAX_DELAY); }
void device_io_unlock(void) { xSemaphoreGive(mutex); }

esp_err_t light_pwm_init(void)
{
    const ledc_timer_config_t timer = {
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .duty_resolution = LEDC_TIMER_13_BIT,
        .timer_num = LEDC_TIMER_0,
        .freq_hz = 5000,
        .clk_cfg = LEDC_AUTO_CLK,
    };
    esp_err_t err = ledc_timer_config(&timer);
    if (err != ESP_OK)
        return err;
    const ledc_channel_config_t channel = {
        .gpio_num = LIGHT_GPIO,
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .channel = LEDC_CHANNEL_0,
        .timer_sel = LEDC_TIMER_0,
        .duty = 0,
    };
    return ledc_channel_config(&channel);
}

esp_err_t device_io_light(const LightData *state)
{
    uint32_t duty = state->power_on ? state->bright * 8191u / 100u : 0;
    esp_err_t err = ledc_set_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_0, duty);
    return err == ESP_OK ? ledc_update_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_0) : err;
}

esp_err_t device_io_relay(bool on)
{
    return gpio_set_level(RELAY_GPIO, on ? RELAY_ACTIVE_LEVEL : !RELAY_ACTIVE_LEVEL);
}

esp_err_t device_io_valve(bool open)
{
    uint32_t pulse = open ? SERVO_OPEN_US : SERVO_CLOSED_US;
    esp_err_t err = ledc_set_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_1,
                                  pulse * 16383u / SERVO_PERIOD_US);
    return err == ESP_OK ? ledc_update_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_1) : err;
}

esp_err_t device_io_refresh(Device *device)
{
    if (device->type != DEVICE_TYPE_WATER)
        return ESP_OK;
    int sum = 0;
    esp_err_t err = ESP_OK;
    for (int i = 0; i < 8; i++)
    {
        int raw;
        err = adc_oneshot_read(adc, water_channel, &raw);
        if (err != ESP_OK)
            break;
        sum += raw;
    }
    device->data.water.water_level = err == ESP_OK
                                         ? water_level_percent(sum / 8, WATER_ADC_DRY, WATER_ADC_WET)
                                         : 255;
#if WATER_LOG_RAW
    ESP_LOGI("WATER", "raw=%d level=%u%%", err == ESP_OK ? sum / 8 : -1,
             (unsigned)device->data.water.water_level);
#endif
    if (water_should_close(device->data.water.water_level, device->data.water.close_threshold))
    {
        esp_err_t close_err = device_io_valve(false);
        if (close_err == ESP_OK)
            device->data.water.power_on = false;
        else
            return close_err;
    }
    return err;
}

static void water_task(void *arg)
{
    (void)arg;
    for (;;)
    {
        device_io_lock();
        esp_err_t err = device_io_refresh(active_device);
        device_io_unlock();
        if (err != ESP_OK)
            ESP_LOGW("WATER", "Sampling/closure failed: %s", esp_err_to_name(err));
        vTaskDelay(pdMS_TO_TICKS(WATER_SAMPLE_MS));
    }
}

esp_err_t device_io_init(Device *device)
{
    mutex = xSemaphoreCreateMutex();
    if (!mutex)
        return ESP_ERR_NO_MEM;
    active_device = device;
    if (device->type == DEVICE_TYPE_LIGHT)
    {
        device->data.light = (LightData){0};
        return light_pwm_init();
    }
    if (device->type == DEVICE_TYPE_RELAY)
    {
        device->data.relay.power_on = false;
        esp_err_t err = device_io_relay(false);
        if (err != ESP_OK)
            return err;
        return gpio_set_direction(RELAY_GPIO, GPIO_MODE_OUTPUT);
    }
    device->data.water.power_on = false;
    device->data.water.water_level = 255;
    const ledc_timer_config_t timer = {
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .duty_resolution = LEDC_TIMER_14_BIT,
        .timer_num = LEDC_TIMER_1,
        .freq_hz = 50,
        .clk_cfg = LEDC_AUTO_CLK,
    };
    esp_err_t err = ledc_timer_config(&timer);
    if (err != ESP_OK)
        return err;
    const ledc_channel_config_t channel = {
        .gpio_num = SERVO_GPIO,
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .channel = LEDC_CHANNEL_1,
        .timer_sel = LEDC_TIMER_1,
        .duty = SERVO_CLOSED_US * 16383u / SERVO_PERIOD_US,
    };
    err = ledc_channel_config(&channel);
    if (err != ESP_OK)
        return err;
    adc_unit_t water_unit;
    err = adc_oneshot_io_to_channel(WATER_ADC_GPIO, &water_unit, &water_channel);
    if (err != ESP_OK)
        return err;
    if (water_unit != ADC_UNIT_1)
        return ESP_ERR_INVALID_ARG;
    const adc_oneshot_unit_init_cfg_t unit = {.unit_id = water_unit};
    err = adc_oneshot_new_unit(&unit, &adc);
    if (err != ESP_OK)
        return err;
    // ESP32-S2 oneshot uses the RTC ADC controller, which supports 13 bits only.
    // ADC_BITWIDTH_12 returns ESP_ERR_INVALID_ARG and aborts app_main/USB startup.
    const adc_oneshot_chan_cfg_t config = {.bitwidth = ADC_BITWIDTH_13, .atten = ADC_ATTEN_DB_12};
    err = adc_oneshot_config_channel(adc, water_channel, &config);
    if (err != ESP_OK)
        return err;
    return xTaskCreate(water_task, "water_monitor", 3072, NULL, 6, NULL) == pdPASS
               ? ESP_OK
               : ESP_ERR_NO_MEM;
}
