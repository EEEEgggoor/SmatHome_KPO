
#include "_WiFi.h"

#include <string.h>
#include <stdint.h>
#include <stdbool.h>

#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"

#include "esp_log.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_netif_ip_addr.h"
#include "esp_wifi.h"
#include "esp_wifi_default.h"

#include "nvs.h"
#include "nvs_flash.h"


#define MAX_RETRY 10
#define WIFI_CONNBIT BIT0
#define WIFI_FAIL_BIT BIT1

#define WIFI_TIMEOUT_MS 60000

static const char *TAG_WIFI = "WIFI";

static int s_retry_num = 0;
static EventGroupHandle_t s_wifi_event_group = NULL;
static bool s_wifi_ready = false;
static bool s_should_connect = false;

static void event_handler(
    void *arg,
    esp_event_base_t event_base,
    int32_t event_id,
    void *event_data)
{
    (void)arg;

    if (event_base == WIFI_EVENT &&
        event_id == WIFI_EVENT_STA_START)
    {
        if (!s_should_connect)
            return;
        ESP_LOGI(TAG_WIFI, "Wi-Fi started");

        esp_err_t err = esp_wifi_connect();

        if (err != ESP_OK)
        {
            ESP_LOGE(TAG_WIFI, "Connect error: %s", esp_err_to_name(err));

            xEventGroupSetBits(
                s_wifi_event_group,
                WIFI_FAIL_BIT);
        }
    }

    else if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED)
    {
        xEventGroupClearBits(
            s_wifi_event_group,
            WIFI_CONNBIT);

        if (!s_should_connect)
            return;

        if (s_retry_num < MAX_RETRY)
        {
            s_retry_num++;

            ESP_LOGW(TAG_WIFI, "Reconnecting (%d/%d)", s_retry_num, MAX_RETRY);

            esp_err_t err = esp_wifi_connect();

            if (err != ESP_OK)
            {
                ESP_LOGE(TAG_WIFI, "Reconnect error: %s", esp_err_to_name(err));

                xEventGroupSetBits(
                    s_wifi_event_group,
                    WIFI_FAIL_BIT);
            }
        }
        else
        {
            ESP_LOGE(TAG_WIFI, "Maximum retries reached");

            xEventGroupSetBits(
                s_wifi_event_group,
                WIFI_FAIL_BIT);
        }

    }

    else if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP)
    {
        if (!s_should_connect)
            return;
        ip_event_got_ip_t *event =
            (ip_event_got_ip_t *)event_data;

        ESP_LOGI(TAG_WIFI, "Got IP: " IPSTR, IP2STR(&event->ip_info.ip));

        s_retry_num = 0;



        xEventGroupClearBits(
            s_wifi_event_group,
            WIFI_FAIL_BIT);

        xEventGroupSetBits(
            s_wifi_event_group,
            WIFI_CONNBIT);
    }
}

static esp_err_t save_credentials(const char *ssid, const char *password)
{
    nvs_handle_t handle;
    esp_err_t err = nvs_open("wifi_cfg", NVS_READWRITE, &handle);
    if (err != ESP_OK)
        return err;

    err = nvs_set_str(handle, "ssid", ssid);
    if (err == ESP_OK)
        err = nvs_set_str(handle, "password", password);
    if (err == ESP_OK)
        err = nvs_commit(handle);

    nvs_close(handle);
    return err;
}

esp_err_t wifi_init(
    const char *wifi_ssid,
    const char *wifi_pass)
{

    if (wifi_ssid == NULL || wifi_pass == NULL)
    {
        ESP_LOGE(TAG_WIFI, "NULL credentials");
        return ESP_ERR_INVALID_ARG;
    }

    size_t ssid_len = strlen(wifi_ssid);
    size_t pass_len = strlen(wifi_pass);

    if (ssid_len == 0 || ssid_len > 32 ||
        pass_len < 8 || pass_len > 63)
    {
        ESP_LOGE(TAG_WIFI, "Invalid SSID/password length");

        return ESP_ERR_INVALID_ARG;
    }

    if (!s_wifi_ready)
    {
        s_wifi_event_group = xEventGroupCreate();
        if (s_wifi_event_group == NULL)
            return ESP_ERR_NO_MEM;

        ESP_ERROR_CHECK(esp_netif_init());
        ESP_ERROR_CHECK(esp_event_loop_create_default());

        esp_netif_t *netif = esp_netif_create_default_wifi_sta();
        if (netif == NULL)
        {
            ESP_LOGE(TAG_WIFI, "Failed to create network interface");
            return ESP_FAIL;
        }

        wifi_init_config_t wifi_conf = WIFI_INIT_CONFIG_DEFAULT();
        ESP_ERROR_CHECK(esp_wifi_init(&wifi_conf));

        esp_event_handler_instance_t inst_any_id;
        esp_event_handler_instance_t inst_got_ip;
        ESP_ERROR_CHECK(esp_event_handler_instance_register(
            WIFI_EVENT, ESP_EVENT_ANY_ID, &event_handler, NULL, &inst_any_id));
        ESP_ERROR_CHECK(esp_event_handler_instance_register(
            IP_EVENT, IP_EVENT_STA_GOT_IP, &event_handler, NULL, &inst_got_ip));
        s_wifi_ready = true;
    }

    wifi_config_t wifi_config = {0};

    memcpy(
        wifi_config.sta.ssid,
        wifi_ssid,
        ssid_len);

    memcpy(
        wifi_config.sta.password,
        wifi_pass,
        pass_len);

    wifi_config.sta.threshold.authmode =
        WIFI_AUTH_WPA2_PSK;

    wifi_config.sta.pmf_cfg.capable = true;
    wifi_config.sta.pmf_cfg.required = false;

    ESP_ERROR_CHECK(
        esp_wifi_set_mode(WIFI_MODE_STA));

    ESP_ERROR_CHECK(
        esp_wifi_set_config(
            WIFI_IF_STA,
            &wifi_config));

    s_retry_num = 0;
    xEventGroupClearBits(s_wifi_event_group, WIFI_CONNBIT | WIFI_FAIL_BIT);
    s_should_connect = true;

    ESP_LOGI(TAG_WIFI, "Starting Wi-Fi...");

    ESP_ERROR_CHECK(esp_wifi_start());

    EventBits_t bits = xEventGroupWaitBits(
        s_wifi_event_group,
        WIFI_CONNBIT | WIFI_FAIL_BIT,
        pdTRUE,
        pdFALSE,
        pdMS_TO_TICKS(WIFI_TIMEOUT_MS));

    if (bits & WIFI_CONNBIT)
    {
        ESP_LOGI(TAG_WIFI, "Connected successfully");
        esp_err_t save_err = save_credentials(wifi_ssid, wifi_pass);
        if (save_err != ESP_OK)
        {
            ESP_LOGE(TAG_WIFI, "Failed to save Wi-Fi credentials: %s",
                     esp_err_to_name(save_err));
        }
        return ESP_OK;
    }

    s_should_connect = false;
    esp_err_t stop_err = esp_wifi_stop();
    if (stop_err != ESP_OK)
    {
        ESP_LOGE(TAG_WIFI, "Failed to stop Wi-Fi: %s",
                 esp_err_to_name(stop_err));
        return stop_err;
    }

    if (bits & WIFI_FAIL_BIT)
    {
        ESP_LOGE(TAG_WIFI, "Connection failed");

        return ESP_FAIL;
    }

    ESP_LOGW(TAG_WIFI, "Connection timeout");

    return ESP_ERR_TIMEOUT;
}
