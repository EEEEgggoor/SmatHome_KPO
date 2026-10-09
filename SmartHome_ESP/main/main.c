#include <stdio.h>
#include <stdint.h>

#include "esp_err.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "tinyusb.h"
#include "tusb.h"

#include "usb_line_parser.h"

static const char *TAG = "usb_lines";

static esp_err_t usb_read_two_lines(char *first, size_t first_size,
                                    char *second, size_t second_size)
{
    if (first == NULL || second == NULL || first_size == 0 || second_size == 0) {
        return ESP_ERR_INVALID_ARG;
    }

    usb_line_parser_t parser;
    usb_line_parser_init(&parser, first, first_size, second, second_size);

    while (parser.current_line < 2) {
        if (!tud_cdc_connected() || tud_cdc_available() == 0) {
            vTaskDelay(pdMS_TO_TICKS(10));
            continue;
        }

        uint8_t data[64];
        const uint32_t count = tud_cdc_read(data, sizeof(data));
        for (uint32_t i = 0; i < count; ++i) {
            if (usb_line_parser_push(&parser, (char)data[i])) {
                break;
            }
        }
    }

    return parser.overflow ? ESP_ERR_INVALID_SIZE : ESP_OK;
}

void app_main(void)
{
    const tinyusb_config_t usb_config = TINYUSB_DEFAULT_CONFIG();
    ESP_ERROR_CHECK(tinyusb_driver_install(&usb_config));

    char first[128];
    char second[128];

    ESP_LOGI(TAG, "Waiting...");
    const esp_err_t result = usb_read_two_lines(first, sizeof(first),
                                                second, sizeof(second));
    if (result == ESP_ERR_INVALID_SIZE) {
        ESP_LOGW(TAG, "One of the lines was truncated");
    } else {
        ESP_ERROR_CHECK(result);
    }

    ESP_LOGI(TAG, "First: %s", first);
    ESP_LOGI(TAG, "Second: %s", second);
}
