#pragma once

#define MODE_LIGHT 1
#define MODE_RELAY 2
#define MODE_WATER 3
#ifndef DEVICE_MODE
#define DEVICE_MODE MODE_WATER
#endif
#if DEVICE_MODE < MODE_LIGHT || DEVICE_MODE > MODE_WATER
#error "DEVICE_MODE must be 1 (light), 2 (relay), or 3 (water)"
#endif
#define CONFIGURED_DEVICE_TYPE ((DeviceType)(DEVICE_MODE - 1))

#define LIGHT_GPIO 5
#define RELAY_GPIO 7
#define RELAY_ACTIVE_LEVEL 0
#define WATER_ADC_GPIO 3 // ESP32-S2: ADC1 channel 2; does not conflict with Wi-Fi.
#define SERVO_GPIO 9

#define WATER_ADC_DRY 0
#define WATER_ADC_WET 5000
#define WATER_DEFAULT_THRESHOLD 80
#define WATER_SAMPLE_MS 200
#define WATER_LOG_RAW 0

#define SERVO_CLOSED_US 1000
#define SERVO_OPEN_US 2000
#define SERVO_PERIOD_US 20000
#if WATER_ADC_WET <= WATER_ADC_DRY
#error "WATER_ADC_WET must exceed WATER_ADC_DRY"
#endif
#if RELAY_ACTIVE_LEVEL != 0 && RELAY_ACTIVE_LEVEL != 1
#error "RELAY_ACTIVE_LEVEL must be 0 or 1"
#endif
