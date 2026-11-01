#pragma once

#include "esp_err.h"

esp_err_t log_forwarder_start(void);
void log_forwarder_set_network_ready(void);
