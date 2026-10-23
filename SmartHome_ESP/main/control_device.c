#include "control_device.h"

#include <string.h>
#include <stdbool.h>
#include <stdint.h>
#include <errno.h>
#include <stddef.h>
#include <sys/time.h>

#include "driver/gpio.h"
#include "driver/ledc.h"
#include "hal/ledc_types.h"


esp_err_t light_pwm_init(void)
{
    const ledc_timer_config_t timer = {
        .speed_mode = LEDC_SPEED_MODE,
        .duty_resolution = LED_PWM_BITS,
        .timer_num = LEDC_TIMER_0,
        .freq_hz = LEDC_FREQ_HZ,
        .clk_cfg = LEDC_AUTO_CLK,
    };
    esp_err_t err = ledc_timer_config(&timer);
    if (err != ESP_OK)
        return err;

    const ledc_channel_config_t channel = {
        .gpio_num = PWM_GPIO,
        .speed_mode = LEDC_SPEED_MODE,
        .channel = LEDC_CHANNEL_0,
        .intr_type = LEDC_INTR_DISABLE,
        .timer_sel = LEDC_TIMER_0,
        .duty = 0,
        .hpoint = 0,
    };
    return ledc_channel_config(&channel);
}

static esp_err_t light_apply(const LightData *l)
{
    uint32_t duty = l->power_on ? (l->bright * LED_PWM_MAX / 100u) : 0u;

    esp_err_t err = ledc_set_duty(LEDC_SPEED_MODE, LEDC_CHANNEL_0, duty);
    if (err != ESP_OK)
    {
        return err;
    }
    return ledc_update_duty(LEDC_SPEED_MODE, LEDC_CHANNEL_0);
}

esp_err_t command_processing(
    const uint8_t *request,
    size_t request_length,
    Device *current_device,
    int client,
    send_fn_t send_response)
{
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

        if (current_device->data.water.water_level > 100)
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

        static bool pwm_initialized = false;

        if (!pwm_initialized)
        {
            esp_err_t err = light_pwm_init();

            if (err != ESP_OK)
                return err;

            pwm_initialized = true;
        }

        LightData next = {
            .power_on = (powered == 1),
            .bright = brightness
        };

        esp_err_t err = light_apply(&next);

        if (err != ESP_OK)
            return err;

        current_device->data.light = next;
        break;
    }

    case CMD_RELAY:

        return ESP_ERR_NOT_SUPPORTED;

    default:
        return ESP_ERR_NOT_SUPPORTED;
    }

    if (!send_response(
            client,
            current_device,
            sizeof(*current_device)))
    {
        return ESP_FAIL;
    }

    return ESP_OK;
}