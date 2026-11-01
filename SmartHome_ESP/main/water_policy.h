#pragma once
#include <stdbool.h>
#include <stdint.h>
uint8_t water_level_percent(int raw, int dry, int wet);
bool water_should_close(uint8_t level, uint8_t threshold);
bool water_can_open(uint8_t level, uint8_t threshold);
