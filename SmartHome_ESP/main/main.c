
#include <stdio.h>
#include <stdint.h>

#include "esp_err.h"
#include "esp_log.h"

#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "tinyusb.h"
#include "tusb.h"
#include "tusb_cdc_acm.h"
#include "esp_check.h"
#include "usb_line_parser.h"
#include "_WiFi.h"
#include "storage.h"
#include "tcp_service.h"
#include "SH_mDns.h"
#include "nvs_flash.h"
#include "nvs.h"


#define WIFI_SSID_BUFFER_SIZE 33
#define WIFI_PASS_BUFFER_SIZE 64

static const char *TAG = "MAIN";
static char wifi_ssid[WIFI_SSID_BUFFER_SIZE] = {0};
static char wifi_pass[WIFI_PASS_BUFFER_SIZE] = {0};

static Device s_device = {0};

static esp_err_t init_nvs(void)
{
    esp_err_t err = nvs_flash_init();

    if (err == ESP_ERR_NVS_NO_FREE_PAGES ||
        err == ESP_ERR_NVS_NEW_VERSION_FOUND)
    {
        ESP_LOGW(TAG, "NVS requires reinitialization");

        ESP_RETURN_ON_ERROR(nvs_flash_erase(), TAG,
                            "Failed to erase NVS");

        err = nvs_flash_init();
    }

    if (err != ESP_OK)
    {
        return err;
    }

    nvs_handle_t handle;

    err = nvs_open("wifi_cfg", NVS_READONLY, &handle);

    if (err != ESP_OK)
    {
        return err;
    }

    // Читаем SSID
    size_t ssid_size = sizeof(wifi_ssid);

    err = nvs_get_str(
        handle,
        "ssid",
        wifi_ssid,
        &ssid_size);

    // Читаем пароль только при успешном чтении SSID
    if (err == ESP_OK)
    {
        size_t pass_size = sizeof(wifi_pass);

        err = nvs_get_str(
            handle,
            "password",
            wifi_pass,
            &pass_size);
    }

    nvs_close(handle);

    if (err != ESP_OK)
    {
        wifi_ssid[0] = '\0';
        wifi_pass[0] = '\0';
        return err;
    }

    ESP_LOGI(TAG, "WiFi credentials loaded from NVS");

    return ESP_OK;
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

    ESP_LOGI(TAG, "Initializing NVS");

    esp_err_t nvs_result = init_nvs();

    if (nvs_result == ESP_OK)
    {
        ESP_LOGI(TAG, "Using saved WiFi credentials");
    }
    else if (nvs_result == ESP_ERR_NVS_NOT_FOUND)
    {
        ESP_LOGW(TAG, "Credentials not found");
        ESP_LOGI(TAG, "Waiting for USB credentials");

        esp_err_t usb_result;
        do
        {
            usb_result = usb_read_two_lines(
                wifi_ssid, sizeof(wifi_ssid),
                wifi_pass, sizeof(wifi_pass));
            if (usb_result != ESP_OK)
                ESP_LOGW(TAG, "Invalid USB credentials: %s",
                         esp_err_to_name(usb_result));
        } while (usb_result != ESP_OK);
    }
    else
    {
        ESP_LOGE(TAG, "NVS error: %s", esp_err_to_name(nvs_result));
        return;
    }

    esp_err_t dev_result = stor_load(&s_device);

    if (dev_result == ESP_OK)
    {
        ESP_LOGI(TAG, "Device loaded successfully");

        ESP_LOGI(TAG, "Device name: %s", s_device.name);

        ESP_LOGI(TAG, "Controller ID: %s",
                 s_device.controller_id);
    }
    else if (dev_result == ESP_ERR_NVS_NOT_FOUND ||
             dev_result == ESP_ERR_INVALID_RESPONSE ||
             dev_result == ESP_ERR_INVALID_SIZE)
    {
        ESP_LOGW(TAG, "Device record missing or invalid, creating default");

        s_device = (Device){
            .type = DEVICE_TYPE_WATER,
            .id = 0,
            .room_id = 0,
            .name = "Water Controller",
            .controller_id = "ESP32-S2-001",
            .online = false,
            .data.water = {
                .power_on = false,
                .water_level = 0}};

        dev_result = stor_save(&s_device);
        if (dev_result != ESP_OK)
        {
            ESP_LOGE(TAG, "Failed to save default device: %s",
                     esp_err_to_name(dev_result));
            return;
        }

        ESP_LOGI(TAG, "Default Device saved");
    }
    else
    {
        ESP_LOGE(TAG, "Failed to load Device: %s",
                 esp_err_to_name(dev_result));
        return;
    }
    ESP_LOGI(TAG, "Connecting to WiFi");

    esp_err_t wifi_result = wifi_init(
        wifi_ssid,
        wifi_pass);

    while (wifi_result != ESP_OK)
    {
        ESP_LOGW(TAG, "WiFi failed: %s; waiting for new USB credentials",
                 esp_err_to_name(wifi_result));
        if (tud_cdc_connected())
        {
            tud_cdc_write_str("WIFI_FAILED\n");
            tud_cdc_write_flush();
        }

        esp_err_t usb_result = usb_read_two_lines(
            wifi_ssid, sizeof(wifi_ssid),
            wifi_pass, sizeof(wifi_pass));
        if (usb_result != ESP_OK)
        {
            ESP_LOGW(TAG, "Invalid USB credentials: %s",
                     esp_err_to_name(usb_result));
            continue;
        }

        wifi_result = wifi_init(wifi_ssid, wifi_pass);
    }

    s_device.online = true;
    esp_err_t tcp_result = tcp_service_start(&s_device);
    if (tcp_result != ESP_OK)
    {
        ESP_LOGE(TAG, "TCP service failed: %s", esp_err_to_name(tcp_result));
        if (tud_cdc_connected())
        {
            tud_cdc_write_str("SERVICE_FAILED\n");
            tud_cdc_write_flush();
        }
        return;
    }
    start_mdns_service();

    if (tud_cdc_connected())
    {
        tud_cdc_write_str("WIFI_CONNECTED\n");
        tud_cdc_write_flush();
    }

    ESP_LOGI(TAG, "WiFi result: %s", esp_err_to_name(wifi_result));

}
