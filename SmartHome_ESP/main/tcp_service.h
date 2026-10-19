#ifndef TCP_SERVICE_H
#define TCP_SERVICE_H

#include "esp_err.h"
#include "device.h"

#define TCP_SERVICE_PORT 5000
#define TCP_CMD_READ_DEVICE 0x01


esp_err_t tcp_service_start(const Device *device);

#endif
