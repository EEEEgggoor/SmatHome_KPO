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

#include "control_device.h"

static const char *TAG = "TCP_SERVICE";

static int server_socket = -1;
static Device *current_device;

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

static bool recv_all(int sock, uint8_t *buff, size_t length)
{
    size_t got = 0;
    while (got < length)
    {
        int n = recv(sock, buff + got, length - got, 0);
        if (n <= 0)
        {
            return false;
        }
        got += (size_t)n;
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

        struct timeval timeout = {.tv_sec = 5, .tv_usec = 0};
        setsockopt(client, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
        setsockopt(client, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

        uint8_t request[REQUEST_SIZE] = {0};
        if (!recv_all(client, request, 1))
        {
            ESP_LOGW(TAG, "Failed to read command");
            close(client);
            continue;
        }
        size_t request_length;

        switch (request[0])
        {
        case 0x01: /* READ_DEVICE */
            request_length = 1;
            break;

        case 0x02: /* READ_WATER_LEVEL */
            request_length = 33;
            break;

        case 0x03: /* SET_LIGHT_STATE */
            request_length = 35;
            break;

        case 0x04: /* SET_RELAY_STATE */
            request_length = 34;
            break;

        default:
            ESP_LOGW(
                TAG, "Unknown command: 0x%02x",
                (unsigned int)request[0]
            );
            close(client);
            continue;
        }

        /* Для READ_DEVICE больше ничего читать не нужно. */
        if (request_length > 1 && !recv_all(client, request + 1, request_length - 1))
        {
            ESP_LOGW(TAG, "Incomplete request for command 0x%02x",(unsigned int)request[0]);
            close(client);
            continue;
        }

        esp_err_t err = command_processing(
            request,
            request_length,
            current_device,
            client,
            send_all
        );

        if (err != ESP_OK)
        {
            ESP_LOGW(
                TAG, "Command 0x%02x failed: %s",
                (unsigned int)request[0],
                esp_err_to_name(err)
            );
        }

        close(client);
    }
}

esp_err_t tcp_service_start(Device *device)
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
