#ifndef TCP_SERVICE_H
#define TCP_SERVICE_H

#include "esp_err.h"
#include "device.h"
#include "control_device.h"

#define TCP_SERVICE_PORT 5000


esp_err_t tcp_service_start(Device *device);

#endif
