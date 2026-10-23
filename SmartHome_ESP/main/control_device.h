#ifndef CONTROL_DEVICE_H
#define CONTROL_DEVICE_H

#include <stdint.h>
#include "esp_err.h"
#include "device.h"

#define REQUEST_SIZE 35
#define ID_OFFSET 1
#define ID_SIZE 32
#define POWER_OFFSET 33
#define BRIGHT_OFFSET 34

#define CMD_READ_DEVICE 0x01
#define CMD_WATER 0x02
#define CMD_LIGHT 0x03
#define CMD_RELAY 0x04

#define PWM_GPIO 5
#define LED_PWM_BITS LEDC_TIMER_13_BIT
#define LED_PWM_MAX ((1u << LED_PWM_BITS) - 1u)
#define LEDC_FREQ_HZ 5000
#define LEDC_SPEED_MODE LEDC_LOW_SPEED_MODE

typedef bool (*send_fn_t)(int fd, const void *data, size_t len);


esp_err_t light_pwm_init(void);

esp_err_t command_processing(
    const uint8_t *request,
    size_t request_length,
    Device *current_device,
    int client,
    send_fn_t send_response
);

#endif //CONTROL_DEVICE_H