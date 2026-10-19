#include <stdint.h>
#include <stdbool.h>

typedef enum {
    DEVICE_TYPE_LIGHT = 0,
    DEVICE_TYPE_RELAY,
    DEVICE_TYPE_WATER
} DeviceType;


typedef struct {
    bool power_on;
    uint8_t bright;
} LightData;

typedef struct { bool power_on; } RelayData;

typedef struct {
    bool power_on;
    uint8_t water_level;
} WaterData;


typedef struct {

    DeviceType type;
    
    int32_t id;                 // Идентификатор устройства в БД
    int32_t room_id;            // ID комнаты, к которой привязано устройство
    char name[32];              // Название устройства (null-terminated строка)
    char controller_id[32];     // Идентификатор контроллера (например, ESP32/MAC)
    bool online;                // Статус подключения устройства к сети

    union {
        LightData light;
        RelayData relay;
        WaterData water;
    } data;

} Device;