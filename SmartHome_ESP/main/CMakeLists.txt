
idf_component_register(
    SRCS
        "main.c"
        "_WiFi.c"
        "usb_line_parser.c"
        "storage.c"
        "SH_mDns.c"
        "tcp_service.c"

    INCLUDE_DIRS
        "."

    REQUIRES
        esp_wifi
        esp_netif
        esp_event
        nvs_flash
        esp_tinyusb
        lwip
)
