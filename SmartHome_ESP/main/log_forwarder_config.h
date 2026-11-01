#pragma once

// Update this to the computer's current Wi-Fi IPv4 address if DHCP changes it.
#define LOG_SERVER_IP "192.168.1.5"
#define LOG_SERVER_PORT 5001

#define LOG_FORWARD_QUEUE_LENGTH 32
#define LOG_FORWARD_LINE_MAX 256
#define LOG_FORWARD_RETRY_MS 2000
