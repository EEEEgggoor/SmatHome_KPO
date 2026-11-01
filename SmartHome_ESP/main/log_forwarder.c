#include "log_forwarder.h"
#include "log_forwarder_config.h"

#include <stdbool.h>
#include <stdint.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <sys/time.h>

#include "esp_log.h"
#include "esp_log_write.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include "lwip/inet.h"
#include "lwip/sockets.h"

#define NETWORK_READY_BIT BIT0

typedef struct
{
    uint16_t length;
    char text[LOG_FORWARD_LINE_MAX];
} log_packet_t;

static QueueHandle_t log_queue;
static EventGroupHandle_t network_events;
static vprintf_like_t console_vprintf;

static int log_vprintf(const char *format, va_list args)
{
    va_list console_args;
    va_copy(console_args, args);
    int result = console_vprintf != NULL
                     ? console_vprintf(format, console_args)
                     : vprintf(format, console_args);
    va_end(console_args);

    if (log_queue == NULL)
        return result;

    log_packet_t packet = {0};
    va_list network_args;
    va_copy(network_args, args);
    int formatted = vsnprintf(packet.text, sizeof(packet.text), format, network_args);
    va_end(network_args);
    if (formatted <= 0)
        return result;

    size_t length = (size_t)formatted;
    const size_t max_length = sizeof(packet.text) - 1;
    if (length > max_length)
    {
        length = max_length;
        packet.text[length - 1] = '\n';
    }
    else if (packet.text[length - 1] != '\n')
    {
        if (length < max_length)
            packet.text[length++] = '\n';
        else
            packet.text[length - 1] = '\n';
    }
    packet.text[length] = '\0';
    packet.length = (uint16_t)length;
    (void)xQueueSend(log_queue, &packet, 0);
    return result;
}

static int connect_log_server(void)
{
    int sock = socket(AF_INET, SOCK_STREAM, IPPROTO_IP);
    if (sock < 0)
        return -1;

    struct timeval timeout = {.tv_sec = 2, .tv_usec = 0};
    (void)setsockopt(sock, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));

    struct sockaddr_in address = {
        .sin_family = AF_INET,
        .sin_port = htons(LOG_SERVER_PORT),
    };
    if (inet_pton(AF_INET, LOG_SERVER_IP, &address.sin_addr) != 1 ||
        connect(sock, (struct sockaddr *)&address, sizeof(address)) < 0)
    {
        close(sock);
        return -1;
    }
    return sock;
}

static bool send_packet(int sock, const log_packet_t *packet)
{
    size_t sent = 0;
    while (sent < packet->length)
    {
        int count = send(sock, packet->text + sent, packet->length - sent, 0);
        if (count <= 0)
            return false;
        sent += (size_t)count;
    }
    return true;
}

static void log_sender_task(void *arg)
{
    (void)arg;
    int sock = -1;

    (void)xEventGroupWaitBits(network_events, NETWORK_READY_BIT, pdFALSE, pdTRUE, portMAX_DELAY);
    for (;;)
    {
        if (sock < 0)
        {
            sock = connect_log_server();
            if (sock < 0)
            {
                vTaskDelay(pdMS_TO_TICKS(LOG_FORWARD_RETRY_MS));
                continue;
            }
        }

        log_packet_t packet;
        if (xQueueReceive(log_queue, &packet, pdMS_TO_TICKS(1000)) == pdTRUE &&
            !send_packet(sock, &packet))
        {
            close(sock);
            sock = -1;
        }
    }
}

esp_err_t log_forwarder_start(void)
{
    if (log_queue != NULL)
        return ESP_ERR_INVALID_STATE;

    log_queue = xQueueCreate(LOG_FORWARD_QUEUE_LENGTH, sizeof(log_packet_t));
    if (log_queue == NULL)
        return ESP_ERR_NO_MEM;

    network_events = xEventGroupCreate();
    if (network_events == NULL)
    {
        vQueueDelete(log_queue);
        log_queue = NULL;
        return ESP_ERR_NO_MEM;
    }

    console_vprintf = esp_log_set_vprintf(log_vprintf);
    if (xTaskCreate(log_sender_task, "log_sender", 4096, NULL, 4, NULL) != pdPASS)
    {
        (void)esp_log_set_vprintf(console_vprintf);
        vEventGroupDelete(network_events);
        vQueueDelete(log_queue);
        network_events = NULL;
        log_queue = NULL;
        return ESP_ERR_NO_MEM;
    }
    return ESP_OK;
}

void log_forwarder_set_network_ready(void)
{
    if (network_events != NULL)
        (void)xEventGroupSetBits(network_events, NETWORK_READY_BIT);
}
