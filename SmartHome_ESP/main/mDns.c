#include "mDns.h"

void start_mdns_service(void)
{
    //Инициализация mDNS
    esp_err_t err = mdns_init();
    if (err != ESP_OK)
    {
        ESP_LOGE(TAG, "Ошибка инициализации mDNS: %s", esp_err_to_name(err));
        return;
    }

    //Установка имени хоста (устройство будет доступно по адресу esp32s2.local)
    ESP_ERROR_CHECK(mdns_hostname_set("esp32s2_smart_home"));

    //Установка понятного имени экземпляра (отображается в сканерах сети)
    ESP_ERROR_CHECK(mdns_instance_name_set("smart_home_device"));

    //Добавление сервиса (например, HTTP-сервер на порту 80)
    ESP_ERROR_CHECK(mdns_service_add(NULL, "_http", "_tcp", 80, NULL, 0));

    //(Опционально) Добавление TXT-записей с дополнительной информацией о сервисе
    mdns_txt_item_t serviceTxtData[2] = {
        {"board", "esp32s2_mini"},
        {"path", "/index.html"}};
    ESP_ERROR_CHECK(mdns_service_txt_set("_http", "_tcp", serviceTxtData, 2));

}