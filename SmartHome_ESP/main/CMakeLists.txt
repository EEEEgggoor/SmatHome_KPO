
idf_component_register(
    SRCS
        "main.c"
        "_WiFi.c"
        "usb_line_parser.c"
        "storage.c"
        "SH_mDns.c"
        "tcp_service.c"
        "log_forwarder.c"
        "control_device.c"
        "device_io.c"
        "water_policy.c"

    INCLUDE_DIRS
        "."

    REQUIRES
        esp_wifi
        esp_netif
        esp_event
        nvs_flash
        esp_tinyusb
        lwip
        log

    PRIV_REQUIRES esp_driver_ledc esp_driver_gpio esp_adc
)
