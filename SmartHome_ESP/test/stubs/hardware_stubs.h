#pragma once
#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"
#define ESP_ERR_NO_MEM 0x101
#define ESP_LOGW(...) ((void)0)
#define ESP_LOGI(...) ((void)0)
#define GPIO_MODE_OUTPUT 1
#define LEDC_LOW_SPEED_MODE 0
#define LEDC_TIMER_13_BIT 13
#define LEDC_TIMER_14_BIT 14
#define LEDC_TIMER_0 0
#define LEDC_TIMER_1 1
#define LEDC_CHANNEL_0 0
#define LEDC_CHANNEL_1 1
#define LEDC_AUTO_CLK 0
#define ADC_UNIT_1 0
#define ADC_BITWIDTH_DEFAULT 0
#define ADC_BITWIDTH_12 12
#define ADC_BITWIDTH_13 13
#define ADC_ATTEN_DB_12 3
#define portMAX_DELAY 0xffffffffu
#define pdPASS 1
#define pdMS_TO_TICKS(ms) (ms)
typedef void *SemaphoreHandle_t;
typedef void *adc_oneshot_unit_handle_t;
typedef int adc_channel_t;
typedef int adc_unit_t;
typedef struct
{
    int speed_mode, duty_resolution, timer_num, freq_hz, clk_cfg;
} ledc_timer_config_t;
typedef struct
{
    int gpio_num, speed_mode, channel, timer_sel;
    uint32_t duty;
} ledc_channel_config_t;
typedef struct
{
    int unit_id;
} adc_oneshot_unit_init_cfg_t;
typedef struct
{
    int bitwidth, atten;
} adc_oneshot_chan_cfg_t;
SemaphoreHandle_t xSemaphoreCreateMutex(void);
int xSemaphoreTake(SemaphoreHandle_t, unsigned);
int xSemaphoreGive(SemaphoreHandle_t);
int xTaskCreate(void (*fn)(void *), const char *, unsigned, void *, unsigned, void *);
void vTaskDelay(unsigned);
esp_err_t ledc_timer_config(const ledc_timer_config_t *);
esp_err_t ledc_channel_config(const ledc_channel_config_t *);
esp_err_t ledc_set_duty(int, int, uint32_t);
esp_err_t ledc_update_duty(int, int);
esp_err_t gpio_set_level(int, int);
esp_err_t gpio_set_direction(int, int);
esp_err_t adc_oneshot_io_to_channel(int, adc_unit_t *, adc_channel_t *);
esp_err_t adc_oneshot_new_unit(const adc_oneshot_unit_init_cfg_t *, adc_oneshot_unit_handle_t *);
esp_err_t adc_oneshot_config_channel(adc_oneshot_unit_handle_t, adc_channel_t, const adc_oneshot_chan_cfg_t *);
esp_err_t adc_oneshot_read(adc_oneshot_unit_handle_t, adc_channel_t, int *);
