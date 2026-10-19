
#ifndef SMART_HOME_WIFI_H
#define SMART_HOME_WIFI_H

#include "esp_err.h"

/*
 * Initializes Wi-Fi in Station mode and waits for IP.
 *
 * ESP_OK             - Connected and received IP
 * ESP_ERR_INVALID_ARG - Invalid credentials
 * ESP_ERR_NO_MEM      - Event group allocation failed
 * ESP_ERR_TIMEOUT     - Connection timed out
 * A failed connection can be retried with new credentials.
 * ESP_FAIL            - Connection failed
 */
esp_err_t wifi_init(const char *wifi_ssid,
                    const char *wifi_pass);

#endif // SMART_HOME_WIFI_H
