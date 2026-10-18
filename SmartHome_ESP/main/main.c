
#include <stdio.h>
#include <stdint.h>

#include "esp_err.h"
#include "esp_log.h"

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "nvs_flash.h"

#include "tinyusb.h"
#include "tusb.h"
#include "tusb_cdc_acm.h"

#include "usb_line_parser.h"
#include "_WiFi.h"

#define WIFI_SSID_BUFFER_SIZE 33
#define WIFI_PASS_BUFFER_SIZE 64

static const char *TAG = "MAIN";


static esp_err_t init_nvs(void)
{
    esp_err_t err = nvs_flash_init();

    if (err == ESP_ERR_NVS_NO_FREE_PAGES ||
        err == ESP_ERR_NVS_NEW_VERSION_FOUND)
    {
        ESP_LOGW(TAG, "NVS requires reinitialization");

        err = nvs_flash_erase();

        if (err != ESP_OK)
        {
            return err;
        }

        err = nvs_flash_init();
    }

    return err;
}


static esp_err_t usb_read_two_lines(char *first, size_t first_size, char *second, size_t second_size)
{
    if (first == NULL ||
        second == NULL ||
        first_size == 0 ||
        second_size == 0)
    {
        return ESP_ERR_INVALID_ARG;
    }

    usb_line_parser_t parser;

    usb_line_parser_init(
        &parser,
        first,
        first_size,
        second,
        second_size);

    ESP_LOGI(TAG, "Waiting for USB CDC connection");

    while (parser.current_line < 2)
    {
        if (!tud_cdc_connected())
        {
            vTaskDelay(pdMS_TO_TICKS(10));
            continue;
        }

        if (tud_cdc_available() == 0)
        {
            vTaskDelay(pdMS_TO_TICKS(10));
            continue;
        }

        uint8_t data[64];

        uint32_t count = tud_cdc_read(
            data,
            sizeof(data));

        for (uint32_t i = 0; i < count; ++i)
        {
            if (usb_line_parser_push(
                    &parser,
                    (char)data[i]))
            {
                break;
            }
        }
    }

    if (parser.overflow)
    {
        return ESP_ERR_INVALID_SIZE;
    }

    return ESP_OK;
}

void app_main(void)
{

    ESP_LOGI(TAG, "Initializing NVS");

    ESP_ERROR_CHECK(init_nvs());


    ESP_LOGI(TAG, "Initializing TinyUSB");

    const tinyusb_config_t usb_config = {0};

    ESP_ERROR_CHECK(
        tinyusb_driver_install(&usb_config));


    const tinyusb_config_cdcacm_t acm_config = {
        .usb_dev = TINYUSB_USBDEV_0,
        .cdc_port = TINYUSB_CDC_ACM_0,
        .rx_unread_buf_sz = 64,
        .callback_rx = NULL,
        .callback_rx_wanted_char = NULL,
        .callback_line_state_changed = NULL,
        .callback_line_coding_changed = NULL};

    ESP_ERROR_CHECK(
        tusb_cdc_acm_init(&acm_config));

    ESP_LOGI(TAG, "USB CDC initialized");


    char wifi_ssid[WIFI_SSID_BUFFER_SIZE] = {0};
    char wifi_pass[WIFI_PASS_BUFFER_SIZE] = {0};

    ESP_LOGI(TAG, "Waiting for SSID and password via USB");


    esp_err_t usb_result = usb_read_two_lines(
        wifi_ssid,
        sizeof(wifi_ssid),
        wifi_pass,
        sizeof(wifi_pass));

    if (usb_result != ESP_OK)
    {
        ESP_LOGE(TAG, "USB read error: %s", esp_err_to_name(usb_result));

        return;
    }

    ESP_LOGI(TAG, "Wi-Fi credentials received");


    ESP_LOGI(TAG, "Connecting to Wi-Fi");

    esp_err_t wifi_result = wifi_init(
        wifi_ssid,
        wifi_pass);
    if (wifi_result == ESP_OK) {
        tud_cdc_write_str("WIFI_CONNECTED\n");
    } else {
        tud_cdc_write_str("WIFI_FAILED\n");
    }

    tud_cdc_write_flush();

    if (wifi_result == ESP_OK)
    {
        ESP_LOGI(TAG, "Wi-Fi connection successful");
    }
    else if (wifi_result == ESP_ERR_TIMEOUT)
    {
        ESP_LOGW(TAG, "Wi-Fi connection timed out");
    }
    else
    {
        ESP_LOGE(TAG, "Wi-Fi connection error: %s", esp_err_to_name(wifi_result));
    }
}
