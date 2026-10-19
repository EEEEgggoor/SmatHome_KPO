#include "tcp_service.h"

#include <errno.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/time.h>

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "lwip/inet.h"
#include "lwip/sockets.h"

static const char *TAG = "TCP_SERVICE";


static int server_socket = -1;
static const Device *current_device;

static bool send_all(int socket_fd, const void *data, size_t length)
{
    const uint8_t *bytes = data;
    size_t sent = 0;

    while (sent < length)
    {
        int count = send(socket_fd, bytes + sent, length - sent, 0);
        if (count <= 0)
            return false;
        sent += (size_t)count;
    }

    return true;
}

static void tcp_service_task(void *arg)
{
    (void)arg;

    for (;;)
    {
        int client = accept(server_socket, NULL, NULL);
        if (client < 0)
        {
            ESP_LOGW(TAG, "accept failed: %d", errno);
            vTaskDelay(pdMS_TO_TICKS(100));
            continue;
        }

        struct timeval timeout = { .tv_sec = 5, .tv_usec = 0 };
        setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        setsockopt(client, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

        uint8_t command;
        int count = recv(client, &command, sizeof(command), 0);
        if (count == 1 && command == TCP_CMD_READ_DEVICE)
        {
            Device snapshot = *current_device;
            if (!send_all(client, &snapshot, sizeof(snapshot)))
                ESP_LOGW(TAG, "send failed: %d", errno);
        }
        else if (count > 0)
        {
            ESP_LOGW(TAG, "Unknown command: 0x%02x", command);
        }

        close(client);
    }
}

esp_err_t tcp_service_start(const Device *device)
{
    if (device == NULL)
        return ESP_ERR_INVALID_ARG;
    if (server_socket >= 0)
        return ESP_ERR_INVALID_STATE;

    int socket_fd = socket(AF_INET, SOCK_STREAM, IPPROTO_IP);
    if (socket_fd < 0)
    {
        ESP_LOGE(TAG, "socket failed: %d", errno);
        return ESP_FAIL;
    }

    int reuse = 1;
    setsockopt(socket_fd, SOL_SOCKET, SO_REUSEADDR, &reuse, sizeof(reuse));

    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_port = htons(TCP_SERVICE_PORT),
        .sin_addr.s_addr = htonl(INADDR_ANY),
    };
    if (bind(socket_fd, (struct sockaddr *)&address, sizeof(address)) < 0 ||
        listen(socket_fd, 4) < 0)
    {
        ESP_LOGE(TAG, "bind/listen failed: %d", errno);
        close(socket_fd);
        return ESP_FAIL;
    }

    current_device = device;
    server_socket = socket_fd;
    if (xTaskCreate(tcp_service_task, "tcp_service", 4096, NULL, 5, NULL) != pdPASS)
    {
        close(socket_fd);
        server_socket = -1;
        current_device = NULL;
        return ESP_ERR_NO_MEM;
    }

    ESP_LOGI(TAG, "Listening on port %d", TCP_SERVICE_PORT);
    return ESP_OK;
}
