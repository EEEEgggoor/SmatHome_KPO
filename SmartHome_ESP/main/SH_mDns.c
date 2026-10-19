#include "SH_mDns.h"
#include <stdbool.h>
#include "tcp_service.h"

void start_mdns_service(void)
{
    static bool service_started = false;
    if (service_started)
    {
        return;
    }
    //Инициализация mDNS
    esp_err_t err = mdns_init();
    if (err != ESP_OK)
    {
        ESP_LOGE("mDNS", "Ошибка инициализации mDNS: %s", esp_err_to_name(err));
        return;
    }

    ESP_ERROR_CHECK(mdns_hostname_set("esp32s2-smart-home"));

    ESP_ERROR_CHECK(mdns_instance_name_set("smart_home_device"));

    ESP_ERROR_CHECK(mdns_service_add(
        "smart_home_device", "_smarthome", "_tcp", TCP_SERVICE_PORT, NULL, 0));

    service_started = true;

}
